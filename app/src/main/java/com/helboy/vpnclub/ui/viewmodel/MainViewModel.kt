package com.helboy.vpnclub.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.helboy.vpnclub.corex.CurrentConfigHolder
import com.helboy.vpnclub.corex.HealthResult
import com.helboy.vpnclub.corex.Phase
import com.helboy.vpnclub.corex.ProfileStore
import com.helboy.vpnclub.corex.ProxyConfig
import com.helboy.vpnclub.corex.VpnClubEngine
import com.helboy.vpnclub.corex.VpnClubService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ویومدل تک‌دکمه‌ای.
 *
 * کل تجربه‌ی کاربر: یک دکمه. روشن = چرخه‌ی کامل موتور (واکشی مخازن → اسکن کلادفلر →
 * تزریق IP → تست پینگ/دانلود/آپلود → اتصال به بهترین). خاموش = قطع.
 *
 * نتیجه‌ی چرخه در [engineState] منعکس می‌شود تا UI فقط وضعیت را نشان دهد.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = VpnClubEngine(application)

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val message: String = "",
        val configCount: Int = 0,
        val cleanIpCount: Int = 0,
        val usableCount: Int = 0,
        val connected: Boolean = false,
        val best: ProxyConfig? = null,
        val lastHealth: Map<String, HealthResult> = emptyMap()
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var cycleJob: Job? = null

    /** یک دکمه: اگر متصل/در حال کار → خاموش؛ وگرنه چرخه‌ی کامل + اتصال. */
    fun toggle(onNeedVpnPermission: (Intent) -> Unit, onPermissionGranted: () -> Unit) {
        if (_ui.value.connected || cycleJob?.isActive == true) {
            disconnect()
            return
        }
        val prepare = VpnService.prepare(getApplication())
        if (prepare != null) {
            onNeedVpnPermission(prepare)
            return
        }
        onPermissionGranted()
        startCycle()
    }

    /** پس از گرفتن مجوز VPN از کاربر صدا زده می‌شود. */
    fun startCycle() {
        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            engine.autoConnect { state ->
                _ui.value = _ui.value.copy(
                    phase = state.phase,
                    message = state.message,
                    configCount = state.configCount,
                    cleanIpCount = state.cleanIpCount,
                    usableCount = state.usableCount
                )
            }.let { ok ->
                _ui.value = _ui.value.copy(
                    connected = ok,
                    best = CurrentConfigHolder.load(getApplication())
                )
            }
        }
    }

    fun disconnect() {
        cycleJob?.cancel()
        engine.disconnect(getApplication())
        _ui.value = UiState() // reset
    }

    /** پیش‌نمایش نتیجه‌ی آخرین سیکل (برای پنل جزئیات اختیاری). */
    fun loadLastSnapshot() {
        viewModelScope.launch {
            val snap = ProfileStore(getApplication()).load()
            _ui.value = _ui.value.copy(
                configCount = snap.configs.size,
                cleanIpCount = snap.cleanIps.size,
                lastHealth = snap.health
            )
        }
    }

    override fun onCleared() {
        cycleJob?.cancel()
        super.onCleared()
    }
}

/** نگهداری فاز برای اتصال به notification — ساده و بدون تزریق. */
object EnginePhaseBridge {
    @Volatile var lastPhase: Phase = Phase.IDLE
}
