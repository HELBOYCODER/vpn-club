package com.helboy.vpnclub.corex

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * مغز برنامه (singleton هم‌عمر با پردازه، مستقل از Activity):
 *
 *  - اتصال خودکار: ابتدا ۱۰ سرور منتخبِ ذخیره‌شده بازتست می‌شوند (اتصال در چند ثانیه)؛
 *    اگر سالمی نبود کشف کامل (ساب‌ها + IP تمیز) اجرا می‌شود.
 *  - به‌محض اولین سرور سالم وصل می‌شود؛ تست ادامه می‌یابد تا ۱۰ سرور منتخب کامل شود.
 *  - نگهبان: هر ۱۰ ثانیه سلامت تونل زنده سنجیده می‌شود؛ دو شکست پیاپی → سرور بعدی از لیست.
 *  - آمار زنده‌ی سرعت/حجم و زمان نشست.
 */
object VpnController {

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val message: String = "",
        val connected: Boolean = false,
        val servers: List<ServerEntry> = emptyList(),
        val current: ServerEntry? = null,
        val configCount: Int = 0,
        val cleanIpCount: Int = 0,
        val testing: Boolean = false,
        val connectedSince: Long = 0,
        val upBps: Long = 0,
        val downBps: Long = 0,
        val upTotal: Long = 0,
        val downTotal: Long = 0,
        val latencyMs: Long = -1,
        val cleanIps: List<CloudflareScanner.CleanIp> = emptyList(),
        val needsPermission: Boolean = false
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default +
        kotlinx.coroutines.CoroutineExceptionHandler { _, t ->
            DiagLogger.log("CONTROLLER_EXC ${t.javaClass.simpleName}: ${t.message}")
            _state.update { it.copy(testing = false) }
        })
    @Volatile private var switching = false
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private lateinit var app: Context
    private lateinit var engine: VpnClubEngine
    private lateinit var settings: AppSettings
    private var initialized = false

    private var mainJob: Job? = null
    private var watchJob: Job? = null
    private var statsJob: Job? = null
    @Volatile private var baseUp = 0L
    @Volatile private var baseDown = 0L

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            app = context.applicationContext
            engine = VpnClubEngine(app)
            settings = AppSettings.of(app)
            val saved = engine.savedServers().sortedByDescending { it.score }
            _state.update { it.copy(servers = saved) }
            initialized = true
        }
        // قطع از بیرون (تنظیمات سیستم / اپ VPN دیگر)
        scope.launch {
            VpnClubService.status.collect { s ->
                if (s.state == SvcState.STOPPED && _state.value.connected) markDisconnected("اتصال توسط سیستم قطع شد")
            }
        }
    }

    // ────────────────────────────── اقدام‌های کاربر ──────────────────────────────

    fun settings(): AppSettings = settings

    /** یک دکمه: روشن/خاموش. */
    fun toggle() {
        val s = _state.value
        if (s.connected || mainJob?.isActive == true) disconnect() else connectAuto()
    }

    fun connectAuto() {
        mainJob?.cancel()
        mainJob = scope.launch { runAuto() }
    }

    /** اتصال دستی به یکی از ۱۰ سرور منتخب. */
    fun connectTo(entry: ServerEntry) {
        mainJob?.cancel()
        mainJob = scope.launch {
            setState(Phase.CONNECTING, "اتصال به ${label(entry)}…")
            if (!activate(entry)) {
                if (_state.value.connected) setState(Phase.CONNECTED, "اتصال به این سرور ناموفق بود؛ سرور قبلی حفظ شد")
                else setState(Phase.FAILED, "اتصال به این سرور ناموفق بود")
            }
        }
    }

    fun disconnect() {
        mainJob?.cancel(); watchJob?.cancel(); statsJob?.cancel()
        VpnClubService.stop(app)
        CurrentConfigHolder.clear(app)
        _state.update {
            it.copy(phase = Phase.IDLE, message = "", connected = false, current = null,
                testing = false, upBps = 0, downBps = 0, upTotal = 0, downTotal = 0,
                connectedSince = 0, latencyMs = -1)
        }
    }

    /** بازتست و پر کردن دوباره‌ی لیست ۱۰ سرور (بدون قطع اتصال فعلی). */
    fun refreshServers() {
        if (mainJob?.isActive == true && !_state.value.connected) return
        scope.launch { fillServers(connectIfNone = false) }
    }

    // ────────────────────────────── چرخه‌ی اصلی ──────────────────────────────

    private suspend fun runAuto() {
        _state.update { it.copy(connected = false, current = null) }
        // ۱) مسیر سریع: سرورهای منتخب قبلی
        val saved = engine.savedServers().sortedByDescending { it.score }
        if (saved.isNotEmpty()) {
            setState(Phase.TESTING, "بازبینی سرورهای منتخب قبلی…")
            for (e in saved) {
                if (!currentCoroutineContext().isActive) return
                val fresh = engine.retest(e) ?: continue
                upsert(fresh)
                if (connectFirst(fresh)) { fillServers(connectIfNone = false); return }
            }
        }
        // ۲) کشف کامل
        fillServers(connectIfNone = true)
    }

    /** کشف + تست؛ هر سرور سالم به لیست اضافه می‌شود، و اگر لازم باشد اولین‌شان وصل می‌شود. */
    private suspend fun fillServers(connectIfNone: Boolean) {
        _state.update { it.copy(testing = true) }
        try {
            val disc = engine.discover { p -> report(p) }
            if (disc == null) {
                if (!_state.value.connected) _state.update { it.copy(phase = Phase.FAILED, connected = false) }
                return
            }
            _state.update { it.copy(configCount = disc.totalParsed, cleanIpCount = disc.cleanIps.size,
                cleanIps = disc.cleanIps) }
            var connectedHere = _state.value.connected
            val have = _state.value.servers.size
            val skip = _state.value.servers.map { it.serverKey }.toSet()
            val found = engine.findServers(
                disc, want = (VpnClubEngine.TARGET_SERVERS - have).coerceAtLeast(3), skipKeys = skip,
                onFound = { e ->
                    upsert(e)
                    if (!connectedHere && connectIfNone) {
                        connectedHere = connectFirst(e)
                    }
                },
                onState = { p -> if (!_state.value.connected) report(p) }
            )
            if (_state.value.connected) {
                _state.update { it.copy(message = "${_state.value.servers.size} سرور منتخب آماده است") }
            } else if (found == 0 && !_state.value.connected) {
                val last = DiagLogger.tail(1).firstOrNull().orEmpty()
                setState(Phase.FAILED, "سرور سالمی پیدا نشد. دوباره تلاش کنید. $last".trim())
            }
        } finally {
            _state.update { it.copy(testing = false) }
        }
    }

    private suspend fun connectFirst(e: ServerEntry): Boolean {
        setState(Phase.CONNECTING, "اتصال به ${label(e)}…")
        return activate(e)
    }

    /** سرویس را روی این سرور بالا می‌آورد و صحت جریان داده را می‌سنجد. */
    private suspend fun activate(e: ServerEntry): Boolean {
        val prev = if (_state.value.connected) _state.value.current else null
        switching = true
        try {
            if (tryActivate(e)) return true
        } finally { switching = false }
        // شکست: یا به سرور قبلی برگرد، یا تونل بی‌دیتا را کامل ببند
        if (prev != null) {
            CurrentConfigHolder.save(app, prev)
            VpnClubService.start(app)
            withTimeoutOrNull(20_000) {
                VpnClubService.status.first { (it.state == SvcState.UP && it.serverId == prev.id) || it.state == SvcState.FAILED }
            }
        } else {
            VpnClubService.stop(app)
            CurrentConfigHolder.clear(app)
        }
        return false
    }

    private suspend fun tryActivate(e: ServerEntry): Boolean {
        CurrentConfigHolder.save(app, e)
        VpnClubService.start(app)
        val st = withTimeoutOrNull(20_000) {
            VpnClubService.status.first {
                (it.state == SvcState.UP && it.serverId == e.id) || it.state == SvcState.FAILED
            }
        }
        if (st == null || st.state != SvcState.UP || st.serverId != e.id) {
            DiagLogger.log("ACTIVATE fail ${e.config.host} -> ${st?.state} ${st?.message}")
            return false
        }
        // همان‌جا یک درخواست واقعی از داخل تونل
        var lat = -1L
        for (i in 0 until 3) {
            lat = HealthTester.tunnelLatency(VpnClubService.SOCKS_PORT, 6_000)
            if (lat > 0) break
            delay(500)
        }
        if (lat <= 0) {
            DiagLogger.log("ACTIVATE no_data ${e.config.host}")
            return false
        }
        val now = System.currentTimeMillis()
        val b = Tun2Socks.bytes()
        baseUp = b?.first ?: 0; baseDown = b?.second ?: 0
        val sinceKeep = if (_state.value.connected && _state.value.connectedSince > 0) _state.value.connectedSince else now
        _state.update { it.copy(phase = Phase.CONNECTED, message = "", connected = true, current = e,
            connectedSince = sinceKeep, latencyMs = lat) }
        startStats()
        if (watchJob?.isActive != true) startWatchdog()
        return true
    }

    // ────────────────────────────── نگهبان و آمار ──────────────────────────────

    private fun startStats() {
        statsJob?.cancel()
        statsJob = scope.launch {
            var lastUp = baseUp; var lastDown = baseDown
            while (isActive) {
                delay(1_000)
                val b = Tun2Socks.bytes() ?: continue
                val up = b.first; val down = b.second
                _state.update {
                    it.copy(
                        upBps = (up - lastUp).coerceAtLeast(0),
                        downBps = (down - lastDown).coerceAtLeast(0),
                        upTotal = (up - baseUp).coerceAtLeast(0),
                        downTotal = (down - baseDown).coerceAtLeast(0)
                    )
                }
                lastUp = up; lastDown = down
            }
        }
    }

    private fun startWatchdog() {
        watchJob?.cancel()
        watchJob = scope.launch {
            var fails = 0
            while (isActive) {
                delay(10_000)
                if (!_state.value.connected || switching) continue
                val lat = HealthTester.tunnelLatency(VpnClubService.SOCKS_PORT, 6_000)
                if (lat > 0) { fails = 0; _state.update { it.copy(latencyMs = lat) }; continue }
                fails++
                if (fails >= 2 && settings.autoFailover) {
                    fails = 0
                    failover()
                }
            }
        }
    }

    /** تعویض خودکار: سرور بعدیِ لیست؛ اگر هیچ‌کدام نبود، کشف دوباره. */
    private suspend fun failover() {
        val cur = _state.value.current
        _state.update { it.copy(message = "قطعی تشخیص داده شد — تعویض سرور…") }
        DiagLogger.log("FAILOVER from ${cur?.config?.host}")
        // بدون اینترنت واقعی، سرورها را «ناسالم» حساب نکن و لیست را پاک نکن
        if (HealthTester.tcpPing("1.1.1.1", 443, 2_500) < 0 && HealthTester.tcpPing("8.8.8.8", 443, 2_500) < 0) {
            _state.update { it.copy(message = "اینترنت قطع است — منتظر بازگشت شبکه…") }
            return
        }
        val others = _state.value.servers.filter { it.id != cur?.id }
        for (e in others) {
            if (!currentCoroutineContext().isActive) return
            val fresh = engine.retest(e)
            if (fresh == null) { drop(e); continue }
            if (activate(fresh)) return
        }
        // لیست خالی/ناسالم → کشف تازه
        _state.update { it.copy(connected = false) }
        fillServers(connectIfNone = true)
    }

    // ────────────────────────────── کمکی ──────────────────────────────

    private fun markDisconnected(msg: String) {
        watchJob?.cancel(); statsJob?.cancel()
        _state.update { it.copy(phase = Phase.IDLE, message = msg, connected = false, current = null,
            upBps = 0, downBps = 0, connectedSince = 0) }
    }

    private fun report(p: VpnClubEngine.Progress) {
        _state.update {
            // وقتی وصلیم، تست پس‌زمینه نباید فاز/پیام اصلی را عوض کند
            it.copy(
                phase = if (it.connected) it.phase else p.phase,
                message = if (it.connected) it.message else p.message,
                configCount = if (p.configCount > 0) p.configCount else it.configCount,
                cleanIpCount = if (p.cleanIpCount > 0) p.cleanIpCount else it.cleanIpCount
            )
        }
    }

    private fun setState(ph: Phase, msg: String) {
        _state.update { it.copy(phase = ph, message = msg) }
    }

    private fun upsert(e: ServerEntry) {
        val list = (listOf(e) + _state.value.servers.filter { it.serverKey != e.serverKey })
            .sortedByDescending { it.score }
            .take(VpnClubEngine.TARGET_SERVERS)
        _state.update { it.copy(servers = list) }
        engine.saveServers(list)
    }

    private fun drop(e: ServerEntry) {
        val list = _state.value.servers.filter { it.id != e.id }
        _state.update { it.copy(servers = list) }
        engine.saveServers(list)
    }

    private fun label(e: ServerEntry) = e.config.tag.ifEmpty { e.config.host }.take(32)
}
