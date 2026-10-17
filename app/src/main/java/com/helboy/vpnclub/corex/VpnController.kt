package com.helboy.vpnclub.corex

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Discovery, activation and recovery belong to one user-controlled session. */
enum class Task { NONE, CONNECT }

object VpnController {
    data class UiState(
        val phase: Phase = Phase.IDLE,
        val message: String = "",
        val connected: Boolean = false,
        val sessionActive: Boolean = false,
        val servers: List<ServerEntry> = emptyList(),
        val current: ServerEntry? = null,
        val configCount: Int = 0,
        val task: Task = Task.NONE,
        val taskMsg: String = "",
        val connectedSince: Long = 0,
        val upBps: Long = 0,
        val downBps: Long = 0,
        val upTotal: Long = 0,
        val downTotal: Long = 0,
        val latencyMs: Long = -1,
        val testedDownloadBps: Double = 0.0,
        val needsPermission: Boolean = false,
        val connectionMode: ConnectionMode = ConnectionMode.AUTO,
        val dnsServer: String = "1.1.1.1",
        val directRules: String = "",
        val blockRules: String = "",
        val proxyRules: String = "",
    ) {
        val testing: Boolean get() = task != Task.NONE
        val busyConnecting: Boolean get() = sessionActive && !connected
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: Context
    private lateinit var engine: HellboyEngine
    private lateinit var prefs: AppSettings
    private lateinit var nativeBridge: NativeTransportBridge
    @Volatile private var activeMode: ConnectionMode = ConnectionMode.AUTO
    @Volatile private var initialized = false
    private var sessionJob: Job? = null
    private var statsJob: Job? = null
    @Volatile private var sessionId = 0L

    fun init(context: Context) {
        synchronized(this) {
            if (initialized) return
            app = context.applicationContext
            engine = HellboyEngine(app)
            prefs = AppSettings.of(app)
            prefs.clearRetiredSettings()
            nativeBridge = NativeTransportBridge(app, ::onNativeStatus, ::onNativeTraffic)
            _state.value = UiState(connectionMode = prefs.connectionMode, dnsServer = prefs.dnsServer, directRules = prefs.directRules,
                blockRules = prefs.blockRules, proxyRules = prefs.proxyRules)
            initialized = true
            DiagLogger.sessionHeader(app)
        }
    }
    fun settings(): AppSettings = prefs
    fun notify(message: String) { _state.update { it.copy(message = message) } }
    fun clearPermissionFlag() { _state.update { it.copy(needsPermission = false) } }

    fun setConnectionMode(mode: ConnectionMode) {
        if (prefs.connectionMode == mode) return
        // A mode switch must not leave an old native or Xray VPN running
        // while the new choice is displayed on Hellboy's main screen.
        if (_state.value.sessionActive) disconnect()
        prefs.connectionMode = mode
        _state.update { it.copy(connectionMode = mode, message = "روش ${mode.displayName} انتخاب شد؛ برای اتصال دکمه را بزنید") }
    }

    fun setDnsServer(value: String) {
        if (!DnsEndpoint.isValid(value)) { notify("DNS معتبر نیست"); return }
        prefs.dnsServer = value
        _state.update { it.copy(dnsServer = value, message = "DNS با اتصال بعدی اعمال می‌شود") }
    }
    fun setRoutingRules(direct: String, block: String, proxy: String) {
        prefs.directRules = direct; prefs.blockRules = block; prefs.proxyRules = proxy
        _state.update { it.copy(directRules = direct, blockRules = block, proxyRules = proxy,
            message = "قوانین با اتصال بعدی اعمال می‌شوند") }
    }

    fun toggle() {
        if (_state.value.sessionActive) disconnect() else connectFastest()
    }

    /** Every user connection begins fresh discovery. Old lists are never an activation shortcut. */
    @Synchronized fun connectFastest() {
        if (VpnService.prepare(app) != null) {
            _state.update { it.copy(needsPermission = true) }
            return
        }
        sessionJob?.cancel()
        statsJob?.cancel()
        val mode = prefs.connectionMode
        activeMode = mode
        val id = ++sessionId
        _state.update { it.copy(connectionMode = mode, sessionActive = true, connected = false, current = null, servers = emptyList(),
            task = Task.CONNECT, phase = Phase.FETCHING, message = "", taskMsg = "یافتن سرورهای تازه…",
            connectedSince = 0, latencyMs = -1, testedDownloadBps = 0.0) }
        if (mode.isNative) {
            try {
                nativeBridge.start(mode, prefs)
            } catch (error: Exception) {
                DiagLogger.log("HELLBOY_NATIVE_FAILURE ${error.javaClass.simpleName}: ${error.message}")
                _state.update { it.copy(sessionActive = false, connected = false, task = Task.NONE,
                    phase = Phase.FAILED, message = "موتور ${mode.displayName} راه‌اندازی نشد: ${error.message?.take(100)}") }
            }
            return
        }
        HellboyVpnService.search(app, id)
        sessionJob = scope.launch {
            try {
                val loop = SmartConnectionLoop(object : SmartConnectionLoop.Driver<ServerEntry> {
                    override fun key(candidate: ServerEntry) = candidate.serverKey
                    override fun networkAvailable() = underlyingNetworkAvailable()
                    override suspend fun discover(excluded: Set<String>): List<ServerEntry> {
                        val list = engine.scan(excluded, mode) { p -> if (id == sessionId) updateSession(id) {
                            it.copy(phase = p.phase, taskMsg = p.message, configCount = p.configCount)
                        } }
                        currentCoroutineContext().ensureActive()
                        if (id == sessionId) {
                            engine.saveServers(list)
                            updateSession(id) { it.copy(servers = list) }
                        }
                        return list
                    }
                    override suspend fun connect(candidate: ServerEntry): Boolean = activate(candidate, id)
                    override suspend fun quality(): ConnectionQuality {
                        if (HellboyVpnService.status.value.state != SvcState.UP) return ConnectionQuality(-1, 0.0)
                        val quality = HealthTester.connectionQuality(HellboyVpnService.SOCKS_PORT)
                        currentCoroutineContext().ensureActive()
                        if (id == sessionId) updateSession(id) { it.copy(latencyMs = quality.latencyMs, testedDownloadBps = quality.downloadBps) }
                        return quality
                    }
                    override suspend fun dropTunnel() {
                        if (id != sessionId) return
                        statsJob?.cancel()
                        HellboyVpnService.search(app, id)
                        updateSession(id) { it.copy(connected = false, current = null, connectedSince = 0, upBps = 0, downBps = 0) }
                    }
                    override fun status(status: SmartConnectionLoop.Status, candidate: ServerEntry?, retrySeconds: Long) {
                        if (id != sessionId) return
                        val message = when (status) {
                            SmartConnectionLoop.Status.SEARCHING -> "جست‌وجوی سرورهای تازه…"
                            SmartConnectionLoop.Status.CONNECTING -> "اتصال و بررسی سرعت ${candidate?.let { it.config.tag.ifEmpty { it.config.host } }.orEmpty()}…"
                            SmartConnectionLoop.Status.CONNECTED -> "اتصال هوشمند فعال است"
                            SmartConnectionLoop.Status.RECOVERING -> "سرور قطع یا ضعیف شده؛ انتخاب پشتیبان…"
                            SmartConnectionLoop.Status.WAITING_NETWORK -> "منتظر اینترنت گوشی…"
                            SmartConnectionLoop.Status.RETRYING -> "سرور مناسب پیدا نشد؛ جست‌وجوی تازه تا $retrySeconds ثانیهٔ دیگر"
                        }
                        val phase = when (status) {
                            SmartConnectionLoop.Status.CONNECTED -> Phase.CONNECTED
                            SmartConnectionLoop.Status.CONNECTING -> Phase.CONNECTING
                            else -> Phase.FETCHING
                        }
                        updateSession(id) { it.copy(phase = phase, message = message,
                            task = if (status == SmartConnectionLoop.Status.CONNECTED) Task.NONE else Task.CONNECT,
                            taskMsg = message) }
                        DiagLogger.log("SMART_SESSION $status")
                    }
                })
                loop.run()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                DiagLogger.log("SMART_SESSION_ERROR ${error.javaClass.simpleName}: ${error.message}")
                failSession(id)
            }
        }
    }

    /**
     * Native status comes from the actual embedded MSN VPN service, not a fake
     * connected flag. Only the selected active native session may paint Hellboy.
     */
    private fun onNativeStatus(status: String, detail: String?, progress: Int) {
        if (!_state.value.sessionActive || !activeMode.isNative) return
        val label = activeMode.displayName
        val suffix = if (progress in 0..99) " ($progress٪)" else ""
        val info = detail?.take(140).orEmpty()
        when (status) {
            com.msnguard.vpn.MsnGuardVpnService.STATUS_CONNECTING,
            com.msnguard.vpn.MsnGuardVpnService.STATUS_STARTING,
            com.msnguard.vpn.MsnGuardVpnService.STATUS_SCANNING -> {
                val scanning = status == com.msnguard.vpn.MsnGuardVpnService.STATUS_SCANNING
                _state.update { it.copy(phase = if (scanning) Phase.FETCHING else Phase.CONNECTING,
                    task = Task.CONNECT, connected = false,
                    taskMsg = "اتصال $label$suffix ${info.ifBlank { "در حال بررسی شبکه…" }}") }
            }
            com.msnguard.vpn.MsnGuardVpnService.STATUS_CONNECTED -> {
                val now = System.currentTimeMillis()
                _state.update { it.copy(phase = Phase.CONNECTED, task = Task.NONE, connected = true,
                    connectedSince = if (it.connectedSince == 0L) now else it.connectedSince,
                    message = "تونل $label برقرار شد", taskMsg = "") }
            }
            com.msnguard.vpn.MsnGuardVpnService.STATUS_FAILED -> {
                _state.update { it.copy(phase = Phase.FETCHING, task = Task.CONNECT, connected = false,
                    taskMsg = "$label: ${info.ifBlank { "اتصال ناموفق بود؛ تلاش مجدد…" }}") }
            }
            com.msnguard.vpn.MsnGuardVpnService.STATUS_DISCONNECTED -> {
                stopSession()
            }
        }
    }

    private fun onNativeTraffic(tx: Long, rx: Long, txSpeed: Long, rxSpeed: Long) {
        if (!_state.value.sessionActive || !activeMode.isNative) return
        _state.update { it.copy(upTotal = tx.coerceAtLeast(0L),
            downTotal = rx.coerceAtLeast(0L), upBps = txSpeed.coerceAtLeast(0L),
            downBps = rxSpeed.coerceAtLeast(0L)) }
    }

    private suspend fun activate(entry: ServerEntry, id: Long): Boolean {
        currentCoroutineContext().ensureActive()
        if (id != sessionId) return false
        CurrentConfigHolder.save(app, entry)
        HellboyVpnService.start(app, id)
        val status = withTimeoutOrNull(15_000L) {
            HellboyVpnService.status.first { (it.state == SvcState.UP && it.serverId == entry.id) || it.state == SvcState.FAILED }
        }
        currentCoroutineContext().ensureActive()
        if (status?.state != SvcState.UP || id != sessionId) return false
        val quality = HealthTester.connectionQuality(HellboyVpnService.SOCKS_PORT)
        currentCoroutineContext().ensureActive()
        if (id != sessionId || !quality.usable) {
            DiagLogger.log("SMART_REJECT ${entry.config.host} latency=${quality.latencyMs} down=${quality.downloadBps}")
            return false
        }
        val verified = entry.copy(latencyMs = quality.latencyMs, downBps = quality.downloadBps, testedAt = System.currentTimeMillis())
        updateSession(id) { it.copy(connected = true, current = verified, connectedSince = System.currentTimeMillis(),
            latencyMs = quality.latencyMs, testedDownloadBps = quality.downloadBps, upTotal = 0, downTotal = 0,
            servers = it.servers.map { e -> if (e.serverKey == entry.serverKey) verified else e }) }
        startStats(id)
        return true
    }

    private fun startStats(id: Long) {
        statsJob?.cancel()
        statsJob = scope.launch {
            val base = Tun2Socks.bytes() ?: (0L to 0L)
            var last = base
            while (isActive && id == sessionId) {
                delay(1_000)
                val bytes = Tun2Socks.bytes() ?: continue
                updateSession(id) { it.copy(upBps = (bytes.first - last.first).coerceAtLeast(0),
                    downBps = (bytes.second - last.second).coerceAtLeast(0),
                    upTotal = (bytes.first - base.first).coerceAtLeast(0), downTotal = (bytes.second - base.second).coerceAtLeast(0)) }
                last = bytes
            }
        }
    }

    /** The lease check is inside StateFlow's CAS retry, so a late result cannot overwrite Stop/new-session state. */
    private fun updateSession(id: Long, change: (UiState) -> UiState) {
        _state.update { current -> if (id == sessionId && current.sessionActive) change(current) else current }
    }

    @Synchronized private fun failSession(id: Long) {
        if (id != sessionId) return
        disconnect()
        _state.update { it.copy(phase = Phase.FAILED, message = "اتصال متوقف شد؛ دوباره تلاش کنید") }
    }

    private fun underlyingNetworkAvailable(): Boolean = runCatching {
        val manager = app.getSystemService(ConnectivityManager::class.java)
        manager.allNetworks.any { network -> manager.getNetworkCapabilities(network)?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        } == true }
    }.getOrDefault(false)

    @Synchronized fun disconnect() {
        val oldSession = sessionId
        val wasNative = activeMode.isNative
        stopSession()
        if (wasNative) nativeBridge.stop() else HellboyVpnService.stop(app, oldSession)
    }
    /** Notification/system stop ends retries as well as the active tunnel. */
    @Synchronized fun stopSession() {
        ++sessionId
        sessionJob?.cancel(); sessionJob = null
        statsJob?.cancel(); statsJob = null
        CurrentConfigHolder.clear(app)
        _state.update { it.copy(sessionActive = false, connected = false, current = null, task = Task.NONE,
            phase = Phase.IDLE, message = "", taskMsg = "", connectedSince = 0, upBps = 0, downBps = 0) }
    }
    fun isSessionCurrent(id: Long) = id == sessionId && _state.value.sessionActive
}
