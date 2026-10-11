package com.helboy.vpnclub.corex

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** کار در حال اجرا — هر دکمه یک کار جدا. */
enum class Task { NONE, SCAN_IP, RESCAN, INJECT, CONNECT }

/**
 * مغز برنامه (singleton هم‌عمر با پردازه، مستقل از Activity).
 *
 * امکانات جدا، هرکدام با دکمه‌ی خودش:
 *  - [scanIps]        اسکنر: پیدا کردن IPهای تمیز کلادفلر
 *  - [rescanServers]  ری‌اسکن: لیست تازه‌ی ۱۰ سرور سالم تصادفی از کل ساب‌ها
 *  - [injectIps]      اینجکت: تزریق تمیزترین IP به سرورهای پشت کلادفلر در همان ۱۰ سرور
 *  - [connectFastest] سریع‌ترین اتصال: همه‌ی ۱۰ سرور هم‌زمان تست، اتصال به کم‌تاخیرترین
 *  - [connectTo]      اتصال به سرور انتخابی
 * همه با موتور PattNG و پروفایل‌های فایروال ایران ([IranVariants]).
 *
 * خودکار در پس‌زمینه: نگهبان اتصال (تعویض سرور هنگام قطعی)، بررسی سلامت لیست و تغییر شبکه.
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
        val task: Task = Task.NONE,
        val taskMsg: String = "",
        val connectedSince: Long = 0,
        val upBps: Long = 0,
        val downBps: Long = 0,
        val upTotal: Long = 0,
        val downTotal: Long = 0,
        val latencyMs: Long = -1,
        val cleanIps: List<CloudflareScanner.CleanIp> = emptyList(),
        val needsPermission: Boolean = false
    ) {
        val testing: Boolean get() = task != Task.NONE
        val busyConnecting: Boolean get() = task == Task.CONNECT
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default +
        CoroutineExceptionHandler { _, t ->
            DiagLogger.log("CONTROLLER_EXC ${t.javaClass.simpleName}: ${t.message}")
            _state.update { it.copy(task = Task.NONE, taskMsg = "") }
        })

    private lateinit var app: Context
    private lateinit var engine: VpnClubEngine
    private lateinit var settings: AppSettings
    @Volatile private var initialized = false

    /** فقط یک «کار» (اسکن/ری‌اسکن/اینجکت/اتصال) در هر لحظه. */
    private val opMutex = Mutex()
    private var opJob: Job? = null
    private var watchJob: Job? = null
    private var statsJob: Job? = null
    private var poolJob: Job? = null
    @Volatile private var switching = false
    @Volatile private var baseUp = 0L
    @Volatile private var baseDown = 0L

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            app = context.applicationContext
            engine = VpnClubEngine(app)
            settings = AppSettings.of(app)
            _state.update {
                it.copy(servers = engine.savedServers().sortedByDescending { e -> e.score },
                    cleanIps = engine.savedCleanIps())
            }
            initialized = true
        }
        registerNetworkKick()
        scope.launch {
            VpnClubService.status.collect { s ->
                if (s.state == SvcState.STOPPED && _state.value.connected) markDisconnected("اتصال توسط سیستم قطع شد")
            }
        }
    }

    fun settings(): AppSettings = settings
    fun clearPermissionFlag() { _state.update { it.copy(needsPermission = false) } }

    // ════════════════════════════ دکمه‌ها ════════════════════════════

    /** دکمه‌ی اصلی: وصل/قطع. وصل = سریع‌ترین (یا سرور انتخابی اگر گزینه‌ی سریع‌ترین خاموش است). */
    fun toggle() {
        val s = _state.value
        if (s.connected || s.task == Task.CONNECT) { disconnect(); return }
        if (settings.fastestMode || s.servers.isEmpty()) connectFastest()
        else connectTo(s.servers.first())
    }

    /** اسکنر IP تمیز کلادفلر. */
    fun scanIps() = runTask(Task.SCAN_IP) { doScanIps() }

    /** لیست تازه‌ی ۱۰ سرور سالم تصادفی از کل ساب‌ها (جایگزین لیست قبلی). */
    fun rescanServers() = runTask(Task.RESCAN) { doRescan() }

    /** تزریق IPهای اسکن‌شده به سرورهای لیست. */
    fun injectIps() = runTask(Task.INJECT) { doInject() }

    /** سریع‌ترین اتصال: همه‌ی سرورها هم‌زمان تست و اتصال به کم‌تاخیرترین. */
    fun connectFastest() = runTask(Task.CONNECT) { doConnectFastest() }

    /** اتصال به سرور مشخص از لیست. */
    fun connectTo(entry: ServerEntry) = runTask(Task.CONNECT) {
        setTask(Task.CONNECT, "اتصال به ${label(entry)}…")
        setState(Phase.CONNECTING, "اتصال به ${label(entry)}…")
        if (!activate(entry)) {
            if (_state.value.connected) setState(Phase.CONNECTED, "این سرور وصل نشد؛ سرور قبلی حفظ شد")
            else setState(Phase.FAILED, "این سرور وصل نشد. «سریع‌ترین اتصال» را امتحان کنید.")
        }
    }

    fun disconnect() {
        opJob?.cancel(); watchJob?.cancel(); statsJob?.cancel(); poolJob?.cancel()
        VpnClubService.stop(app)
        CurrentConfigHolder.clear(app)
        _state.update {
            it.copy(phase = Phase.IDLE, message = "", connected = false, current = null,
                task = Task.NONE, taskMsg = "", upBps = 0, downBps = 0, upTotal = 0, downTotal = 0,
                connectedSince = 0, latencyMs = -1)
        }
    }

    /** اجرای یک کار به‌صورت انحصاری. کار دیگری در حال اجرا باشد، نادیده گرفته می‌شود. */
    private fun runTask(t: Task, block: suspend () -> Unit) {
        if (opMutex.isLocked) return
        opJob = scope.launch {
            opMutex.withLock {
                setTask(t, "")
                try { block() } finally { setTask(Task.NONE, "") }
            }
        }
    }

    // ════════════════════════════ کارها ════════════════════════════

    private suspend fun doScanIps(): List<CloudflareScanner.CleanIp> {
        setTask(Task.SCAN_IP, "اسکن IP تمیز کلادفلر…")
        val ips = engine.freshCleanIps { tested, found ->
            setTask(Task.SCAN_IP, "اسکن IP تمیز… $tested بررسی · $found تمیز")
        }
        _state.update { it.copy(cleanIps = ips, cleanIpCount = ips.size) }
        toast(if (ips.isEmpty()) "IP تمیزی پیدا نشد" else "${ips.size} IP تمیز پیدا شد · بهترین: ${ips.first().ip}")
        return ips
    }

    private suspend fun doRescan(): List<ServerEntry> {
        setTask(Task.RESCAN, "دریافت ساب‌ها…")
        val disc = engine.discover { p -> setTask(Task.RESCAN, p.message) } ?: run {
            toast("دریافت ساب‌ها ناموفق بود. اینترنت را بررسی کنید.")
            return _state.value.servers
        }
        _state.update { it.copy(configCount = disc.totalParsed, cleanIpCount = disc.cleanIps.size,
            cleanIps = if (it.cleanIps.isEmpty()) disc.cleanIps else it.cleanIps) }
        // سرور فعلی (اگر وصلیم) در لیست می‌ماند؛ بقیه کاملاً تازه
        val keep = listOfNotNull(_state.value.current.takeIf { _state.value.connected })
        var fresh = keep
        val want = VpnClubEngine.TARGET_SERVERS - keep.size
        engine.findServers(disc, want, skipKeys = keep.map { it.serverKey }.toSet(),
            onFound = { e ->
                fresh = (fresh + e).distinctBy { it.serverKey }
                setTask(Task.RESCAN, "سرور سالم: ${fresh.size}/${VpnClubEngine.TARGET_SERVERS}")
                publish(fresh)
            },
            onState = { p -> setTask(Task.RESCAN, "${p.message} · سالم: ${fresh.size}/${VpnClubEngine.TARGET_SERVERS}") })
        if (fresh.size <= keep.size) toast("سرور سالم جدیدی پیدا نشد")
        else toast("${fresh.size} سرور سالم آماده است")
        return _state.value.servers
    }

    private suspend fun doInject(): Int {
        if (_state.value.servers.isEmpty()) { toast("اول «ری‌اسکن» بزنید تا سرورها پیدا شوند"); return 0 }
        var ips = _state.value.cleanIps
        if (ips.isEmpty()) ips = doScanIps()
        if (ips.isEmpty()) return 0
        setTask(Task.INJECT, "تزریق ${ips.first().ip} به سرورها…")
        val (list, changed) = engine.injectCleanIps(_state.value.servers, ips) { d, t ->
            setTask(Task.INJECT, "تزریق IP تمیز ($d/$t)")
        }
        publish(list)
        val eligible = list.count { it.cf }
        toast(when {
            eligible == 0 -> "هیچ‌کدام از این سرورها پشت کلادفلر نیستند (تزریق لازم ندارند)"
            changed == 0 -> "IP قبلی‌ها بهتر بودند؛ تغییری لازم نبود"
            else -> "IP تمیز به $changed سرور تزریق شد"
        })
        return changed
    }

    private suspend fun doConnectFastest() {
        _state.update { it.copy(needsPermission = false) }
        // ۱) لیست خالی → ساخت: ری‌اسکن ← اسکن IP ← اینجکت
        if (_state.value.servers.isEmpty()) {
            setState(Phase.TESTING, "یافتن سرورهای سالم…")
            doRescan()
            if (_state.value.servers.isEmpty()) { setState(Phase.FAILED, "سرور سالمی پیدا نشد. دوباره تلاش کنید."); return }
            doInject()
        }
        // ۲) همه هم‌زمان تست، سریع‌ترین اول
        setTask(Task.CONNECT, "یافتن سریع‌ترین سرور…")
        setState(Phase.TESTING, "یافتن سریع‌ترین سرور…")
        var alive = engine.retestAll(_state.value.servers) { d, a ->
            setTask(Task.CONNECT, "تست سرورها $d/${_state.value.servers.size} · سالم: $a")
        }
        if (alive.isEmpty()) {
            // لیست مرده → ساخت دوباره
            setState(Phase.TESTING, "سرورهای قبلی ضعیف شده‌اند؛ لیست تازه…")
            doRescan(); doInject()
            alive = _state.value.servers
        } else publish(alive)
        // ۳) اتصال به ترتیب سرعت
        for (e in alive.sortedBy { it.latencyMs }) {
            if (!currentCoroutineContext().isActive) return
            setTask(Task.CONNECT, "اتصال به ${label(e)} (${e.latencyMs}ms)…")
            setState(Phase.CONNECTING, "اتصال به ${label(e)}…")
            if (activate(e)) return
            if (_state.value.needsPermission) return
        }
        setState(Phase.FAILED, "اتصال ممکن نشد. «ری‌اسکن» را بزنید و دوباره وصل شوید.")
    }

    // ════════════════════════════ اتصال ════════════════════════════

    /** سرویس را روی این سرور بالا می‌آورد و صحت جریان داده را می‌سنجد. */
    private suspend fun activate(e: ServerEntry): Boolean {
        val prev = if (_state.value.connected) _state.value.current else null
        switching = true
        try {
            if (tryActivate(e)) return true
        } finally { switching = false }
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
            if (st?.state == SvcState.FAILED && st.message.contains("تونل") &&
                android.net.VpnService.prepare(app) != null) {
                _state.update { it.copy(needsPermission = true, message = "مجوز VPN لازم است") }
            }
            return false
        }
        var lat = -1L
        for (i in 0 until 3) {
            lat = HealthTester.tunnelLatency(VpnClubService.SOCKS_PORT, 6_000)
            if (lat > 0) break
            delay(500)
        }
        if (lat <= 0) { DiagLogger.log("ACTIVATE no_data ${e.config.host}"); return false }
        val b = Tun2Socks.bytes()
        baseUp = b?.first ?: 0; baseDown = b?.second ?: 0
        val now = System.currentTimeMillis()
        val since = if (_state.value.connected && _state.value.connectedSince > 0) _state.value.connectedSince else now
        _state.update { it.copy(phase = Phase.CONNECTED, message = "", connected = true, current = e,
            connectedSince = since, latencyMs = lat) }
        startStats()
        if (watchJob?.isActive != true) startWatchdog()
        startPoolMonitor()
        return true
    }

    // ════════════════════════════ خودکار ════════════════════════════

    private fun startStats() {
        statsJob?.cancel()
        statsJob = scope.launch {
            var lastUp = baseUp; var lastDown = baseDown
            while (isActive) {
                delay(1_000)
                val b = Tun2Socks.bytes() ?: continue
                _state.update {
                    it.copy(upBps = (b.first - lastUp).coerceAtLeast(0), downBps = (b.second - lastDown).coerceAtLeast(0),
                        upTotal = (b.first - baseUp).coerceAtLeast(0), downTotal = (b.second - baseDown).coerceAtLeast(0))
                }
                lastUp = b.first; lastDown = b.second
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
                if (++fails >= 2 && settings.autoFailover) { fails = 0; failover() }
            }
        }
    }

    /** هر چند دقیقه: سرورهای مرده از لیست حذف؛ اگر ≤ WEAK_POOL ماند، ری‌اسکن + اینجکت خودکار. */
    private fun startPoolMonitor() {
        if (poolJob?.isActive == true) return
        poolJob = scope.launch {
            while (isActive) {
                delay(POOL_CHECK_MS)
                if (!_state.value.connected || switching || opMutex.isLocked) continue
                val current = _state.value.servers
                if (current.isEmpty()) continue
                opMutex.withLock {
                    val alive = engine.retestAll(current)
                    val keepCur = _state.value.current?.let { c -> current.firstOrNull { it.serverKey == c.serverKey } }
                    publish((alive + listOfNotNull(keepCur)).distinctBy { it.serverKey })
                    DiagLogger.log("POOL_CHECK alive=${alive.size}/${current.size}")
                    if (alive.size <= WEAK_POOL) {
                        setTask(Task.RESCAN, "تازه‌سازی خودکار لیست…")
                        try { doRescan(); doInject() } finally { setTask(Task.NONE, "") }
                    }
                }
            }
        }
    }

    /** قطعی: سریع‌ترین سرور سالم دیگر؛ اگر هیچ نبود ری‌اسکن + اینجکت و دوباره. */
    private suspend fun failover() {
        val cur = _state.value.current
        _state.update { it.copy(message = "قطعی تشخیص داده شد — تعویض سرور…") }
        DiagLogger.log("FAILOVER from ${cur?.config?.host}")
        if (HealthTester.tcpPing("1.1.1.1", 443, 2_500) < 0 && HealthTester.tcpPing("8.8.8.8", 443, 2_500) < 0) {
            _state.update { it.copy(message = "اینترنت قطع است — منتظر بازگشت شبکه…") }
            return
        }
        val others = _state.value.servers.filter { it.serverKey != cur?.serverKey }
        val alive = engine.retestAll(others).sortedBy { it.latencyMs }
        for (e in alive) {
            if (!currentCoroutineContext().isActive) return
            if (activate(e)) { _state.update { it.copy(message = "") }; return }
        }
        if (opMutex.isLocked) return
        opMutex.withLock {
            setTask(Task.RESCAN, "همه‌ی سرورها ضعیف شدند؛ لیست تازه…")
            try { doRescan(); doInject() } finally { setTask(Task.NONE, "") }
        }
        for (e in _state.value.servers.sortedBy { it.latencyMs }) { if (activate(e)) return }
        markDisconnected("سرور سالمی پیدا نشد")
        setState(Phase.FAILED, "سرور سالمی پیدا نشد")
    }

    /** تغییر شبکه‌ی زیرین (وای‌فای ↔ سیم‌کارت): سلامت تونل فوراً سنجیده می‌شود. */
    private fun registerNetworkKick() {
        try {
            val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
            val req = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            cm.registerNetworkCallback(req, object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) { kickCheck() }
            })
        } catch (_: Exception) {}
    }

    @Volatile private var kickBusy = false
    private fun kickCheck() {
        if (kickBusy || !_state.value.connected) return
        kickBusy = true
        scope.launch {
            try {
                delay(2_000)
                if (!_state.value.connected || switching) return@launch
                var lat = HealthTester.tunnelLatency(VpnClubService.SOCKS_PORT, 5_000)
                if (lat <= 0) { delay(1_500); lat = HealthTester.tunnelLatency(VpnClubService.SOCKS_PORT, 5_000) }
                if (lat <= 0 && settings.autoFailover) failover()
            } finally { kickBusy = false }
        }
    }

    // ════════════════════════════ کمکی ════════════════════════════

    private fun markDisconnected(msg: String) {
        watchJob?.cancel(); statsJob?.cancel(); poolJob?.cancel()
        _state.update { it.copy(phase = Phase.IDLE, message = msg, connected = false, current = null,
            upBps = 0, downBps = 0, connectedSince = 0) }
    }

    /** لیست را مرتب (سریع‌ترین اول)، به ۱۰ محدود، در state و دیسک می‌نویسد. */
    private fun publish(list: List<ServerEntry>) {
        val sorted = list.distinctBy { it.serverKey }.sortedByDescending { it.score }
            .take(VpnClubEngine.TARGET_SERVERS)
        _state.update { it.copy(servers = sorted) }
        engine.saveServers(sorted)
    }

    private fun setTask(t: Task, msg: String) { _state.update { it.copy(task = t, taskMsg = msg) } }

    /** پیام نتیجه‌ی یک کار (وقتی وصلیم فاز اصلی را عوض نمی‌کند). */
    private fun toast(msg: String) { _state.update { it.copy(message = msg) } }

    private fun setState(ph: Phase, msg: String) {
        _state.update { if (it.connected && ph != Phase.CONNECTED) it.copy(message = msg) else it.copy(phase = ph, message = msg) }
    }

    private fun label(e: ServerEntry) = e.config.tag.ifEmpty { e.config.host }.take(32)

    private const val WEAK_POOL = 5
    private const val POOL_CHECK_MS = 4 * 60_000L
}
