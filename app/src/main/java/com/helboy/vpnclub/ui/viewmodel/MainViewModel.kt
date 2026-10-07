package com.helboy.vpnclub.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.helboy.vpnclub.data.model.VpnServer
import com.helboy.vpnclub.data.repository.VpnServerRepository
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _countryFilter = MutableStateFlow<String?>(null)
    val countryFilter: StateFlow<String?> = _countryFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.PING)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _sessionDurationSeconds = MutableStateFlow(0L)
    val sessionDurationSeconds: StateFlow<Long> = _sessionDurationSeconds.asStateFlow()

    private var timerJob: Job? = null

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
        _sortOption
    ) { list, query, country, sort ->
        var res = list

        if (country != null) {
            res = res.filter { it.countryLong.equals(country, ignoreCase = true) || it.countryShort.equals(country, ignoreCase = true) }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            res = res.filter {
                it.countryLong.lowercase().contains(q) ||
                it.countryShort.lowercase().contains(q) ||
                it.ip.contains(q) ||
                it.hostName.lowercase().contains(q)
            }
        }

        when (sort) {
            SortOption.PING -> res.sortedWith(
                compareBy<VpnServer> { if (it.ping <= 0) 9999 else it.ping }
                    .thenByDescending { it.speed }
            )
            SortOption.SPEED -> res.sortedByDescending { it.speed }
            SortOption.SESSIONS -> res.sortedByDescending { it.numVpnSessions }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val availableCountries: StateFlow<List<Pair<String, String>>> = _servers.combine(_servers) { list, _ ->
        list.groupBy { it.countryLong }
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
                    // Pick the fastest server by default
                    _selectedServer.value = list.firstOrNull { it.ping > 0 } ?: list.first()
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

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun toggleConnection(): Boolean {
        return if (connectionState.value == VpnConnectionState.CONNECTED ||
            connectionState.value == VpnConnectionState.CONNECTING ||
            connectionState.value == VpnConnectionState.AUTHENTICATING) {
            vpnController.disconnect()
            true
        } else {
            val target = _selectedServer.value ?: _servers.value.firstOrNull()
            if (target != null) {
                vpnController.connect(target)
            } else {
                false
            }
        }
    }

    fun autoConnectFastest(): Boolean {
        val fastest = _servers.value.filter { it.ping > 0 }.minByOrNull { it.ping }
            ?: _servers.value.firstOrNull()
        if (fastest != null) {
            _selectedServer.value = fastest
            return vpnController.connect(fastest)
        }
        return false
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
        stopTimer()
        vpnController.release()
    }
}
