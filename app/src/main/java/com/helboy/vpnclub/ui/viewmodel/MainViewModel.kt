package com.helboy.vpnclub.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.helboy.vpnclub.data.model.VpnServer
import com.helboy.vpnclub.data.repository.VpnServerRepository
import com.helboy.vpnclub.network.TcpReachabilityScanner
import com.helboy.vpnclub.vpn.OpenVpnController
import com.helboy.vpnclub.vpn.VpnConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOption {
    IRAN_COMPATIBLE,
    PING,
    SPEED,
    SESSIONS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VpnServerRepository(application)
    val vpnController = OpenVpnController(application)

    private val _servers = MutableStateFlow<List<VpnServer>>(emptyList())
    val servers: StateFlow<List<VpnServer>> = _servers.asStateFlow()

    private val _selectedServer = MutableStateFlow<VpnServer?>(null)
    val selectedServer: StateFlow<VpnServer?> = _selectedServer.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isProbing = MutableStateFlow(false)
    val isProbing: StateFlow<Boolean> = _isProbing.asStateFlow()

    private val _probeStatus = MutableStateFlow("")
    val probeStatus: StateFlow<String> = _probeStatus.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _countryFilter = MutableStateFlow<String?>(null)
    val countryFilter: StateFlow<String?> = _countryFilter.asStateFlow()

    private val _onlyIranCompatible = MutableStateFlow(true)
    val onlyIranCompatible: StateFlow<Boolean> = _onlyIranCompatible.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.IRAN_COMPATIBLE)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _sessionDurationSeconds = MutableStateFlow(0L)
    val sessionDurationSeconds: StateFlow<Long> = _sessionDurationSeconds.asStateFlow()

    private var timerJob: Job? = null
    private var connectionWatchdogJob: Job? = null

    val connectionState = vpnController.connectionState
    val statusMessage = vpnController.statusMessage
    val downloadSpeed = vpnController.downloadSpeed
    val uploadSpeed = vpnController.uploadSpeed
    val totalDownloaded = vpnController.totalDownloaded
    val totalUploaded = vpnController.totalUploaded
    val logEntries = vpnController.logEntries

    val filteredServers: StateFlow<List<VpnServer>> = combine(
        _servers,
        _searchQuery,
        _countryFilter,
        _onlyIranCompatible,
        _sortOption
    ) { list, query, country, iranOnly, sort ->
        var res = list

        if (iranOnly) {
            res = res.filter { it.isIranCompatible }
        }

        if (country != null) {
            res = res.filter {
                it.countryLong.equals(country, ignoreCase = true) ||
                it.countryShort.equals(country, ignoreCase = true)
            }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            res = res.filter {
                it.countryLong.lowercase().contains(q) ||
                it.countryShort.lowercase().contains(q) ||
                it.ip.contains(q) ||
                it.hostName.lowercase().contains(q) ||
                it.port.toString().contains(q)
            }
        }

        when (sort) {
            SortOption.IRAN_COMPATIBLE -> res.sortedWith(
                compareByDescending<VpnServer> { if (it.isIranCompatible) 100 else 0 }
                    .thenByDescending { if (it.port == 995) 50 else 0 }
                    .thenBy { if (it.ping <= 0) 9999 else it.ping }
                    .thenByDescending { it.speed }
            )
            SortOption.PING -> res.sortedWith(
                compareBy<VpnServer> {
                    if (it.probedLatencyMs != null && it.probedLatencyMs!! > 0) it.probedLatencyMs!!
                    else if (it.ping <= 0) 9999 else it.ping
                }.thenByDescending { it.speed }
            )
            SortOption.SPEED -> res.sortedByDescending { it.speed }
            SortOption.SESSIONS -> res.sortedByDescending { it.numVpnSessions }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val availableCountries: StateFlow<List<Pair<String, String>>> = _servers.combine(_onlyIranCompatible) { list, iranOnly ->
        val effectiveList = if (iranOnly) list.filter { it.isIranCompatible } else list
        effectiveList.groupBy { it.countryLong }
            .map { (country, items) ->
                val flag = items.firstOrNull()?.countryFlag ?: "🌐"
                Pair(country, flag)
            }
            .sortedBy { it.first }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadServers(forceRefresh = false)
        observeConnectionState()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state == VpnConnectionState.CONNECTED) {
                    connectionWatchdogJob?.cancel()
                    _probeStatus.value = "اتصال پایدار برقرار شد"
                    startTimer()
                } else if (state == VpnConnectionState.DISCONNECTED || state == VpnConnectionState.ERROR) {
                    stopTimer()
                }
            }
        }
    }

    private fun startTimer() {
        stopTimer()
        _sessionDurationSeconds.value = 0L
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _sessionDurationSeconds.value += 1
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun loadServers(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val list = repository.getServers(forceRefresh)
                _servers.value = list

                if (_selectedServer.value == null && list.isNotEmpty()) {
                    // Pick the fastest IRAN-COMPATIBLE server by default
                    val iranCompatibles = list.filter { it.isIranCompatible }
                    _selectedServer.value = iranCompatibles.firstOrNull { it.ping > 0 }
                        ?: iranCompatibles.firstOrNull()
                        ?: list.firstOrNull()
                }
            } catch (e: Exception) {
                // Handled gracefully
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun selectServer(server: VpnServer) {
        _selectedServer.value = server
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCountryFilter(country: String?) {
        _countryFilter.value = country
    }

    fun setOnlyIranCompatible(value: Boolean) {
        _onlyIranCompatible.value = value
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun toggleConnection(): Boolean {
        return if (connectionState.value == VpnConnectionState.CONNECTED ||
            connectionState.value == VpnConnectionState.CONNECTING ||
            connectionState.value == VpnConnectionState.AUTHENTICATING) {
            connectionWatchdogJob?.cancel()
            vpnController.disconnect()
            true
        } else {
            val target = _selectedServer.value ?: _servers.value.firstOrNull { it.isIranCompatible } ?: _servers.value.firstOrNull()
            if (target != null) {
                vpnController.connect(target, target.probedPort)
            } else {
                false
            }
        }
    }

    /**
     * Smart Iran Auto-Connect:
     * 1. Filters non-blocked Iran-compatible servers (ports 995, non-standard high ports, non-Tsukuba subnets).
     * 2. Concurrently probes TCP socket reachability on candidate servers directly through the user's cellular/WiFi connection.
     * 3. Selects the verified reachable server with the lowest latency.
     * 4. Starts OpenVPN with anti-throttling & MTU tuning.
     * 5. Runs an automatic failover watchdog (switches to the next candidate if not connected within 9 seconds).
     */
    fun smartConnectIran() {
        if (connectionState.value == VpnConnectionState.CONNECTED ||
            connectionState.value == VpnConnectionState.CONNECTING ||
            connectionState.value == VpnConnectionState.AUTHENTICATING) {
            connectionWatchdogJob?.cancel()
            vpnController.disconnect()
            return
        }

        viewModelScope.launch {
            _isProbing.value = true
            _probeStatus.value = "🔍 غربالگری سرورهای سازگار با اینترنت ایران..."

            val candidates = _servers.value.filter { it.isIranCompatible }
                .sortedWith(
                    compareByDescending<VpnServer> { if (it.port == 995) 50 else 0 }
                        .thenBy { if (it.ping <= 0) 9999 else it.ping }
                        .thenByDescending { it.speed }
                )

            if (candidates.isEmpty()) {
                _isProbing.value = false
                _probeStatus.value = "سرور مناسبی در لیست یافت نشد"
                return@launch
            }

            _probeStatus.value = "⚡ در حال سنجش زنده سوکت از اینترنت دستگاه شما..."
            val probeResults = TcpReachabilityScanner.probeBatch(candidates, timeoutMs = 1800, maxConcurrency = 10)
            val responsive = probeResults.filter { it.isReachable }.sortedBy { it.latencyMs }

            _isProbing.value = false

            val queue = if (responsive.isNotEmpty()) {
                responsive.map { Pair(it.server, it.responsivePort) }
            } else {
                // If probes timed out, take the top 5 candidates directly
                candidates.take(5).map { Pair(it, it.port) }
            }

            attemptConnectWithFailover(queue, attemptIndex = 0)
        }
    }

    private fun attemptConnectWithFailover(queue: List<Pair<VpnServer, Int>>, attemptIndex: Int) {
        if (attemptIndex >= queue.size) {
            _probeStatus.value = "پاسخی از سرورهای انتخابی دریافت نشد"
            return
        }

        val (targetServer, targetPort) = queue[attemptIndex]
        _selectedServer.value = targetServer

        val attemptNum = attemptIndex + 1
        _probeStatus.value = "اتصال به ${targetServer.countryLong} (پورت $targetPort)... [تلاش $attemptNum از ${queue.size}]"

        vpnController.connect(targetServer, targetPort)

        connectionWatchdogJob?.cancel()
        connectionWatchdogJob = viewModelScope.launch {
            delay(9000L)
            val currentState = vpnController.connectionState.value
            if (currentState != VpnConnectionState.CONNECTED) {
                Log.w("MainViewModel", "Server ${targetServer.ip}:$targetPort timed out after 9s, switching...")
                vpnController.disconnect()
                delay(600L)
                attemptConnectWithFailover(queue, attemptIndex + 1)
            }
        }
    }

    /**
     * Probes all visible servers in the current list to display real live latency badges.
     */
    fun probeVisibleServers() {
        viewModelScope.launch {
            _isProbing.value = true
            _probeStatus.value = "در حال سنجش زنده تاخیر..."
            val visible = filteredServers.value.take(15)
            TcpReachabilityScanner.probeBatch(visible, timeoutMs = 1800, maxConcurrency = 10)
            _isProbing.value = false
            _probeStatus.value = "سنجش زنده پایان یافت"
        }
    }

    fun formatDuration(seconds: Long): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectionWatchdogJob?.cancel()
        stopTimer()
        vpnController.release()
    }
}
