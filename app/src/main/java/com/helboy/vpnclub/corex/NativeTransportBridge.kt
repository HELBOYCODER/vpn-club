package com.helboy.vpnclub.corex

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.msnguard.vpn.CoreConfig
import com.msnguard.vpn.MsnGuardVpnService
import com.msnguard.vpn.SplitTunnelSettings
import com.msnguard.vpn.profiled

/**
 * A headless adapter to the embedded MSN-GUARD VPN service. There is ONE
 * user-facing app (Hellboy). No MSN Activity, settings screen or second launcher.
 *
 * The original MSN native transport lifecycle is preserved; we pass its actual
 * CoreConfig JSON and forward native status/traffic broadcasts to Hellboy state.
 */
internal class NativeTransportBridge(
    private val context: Context,
    private val status: (state: String, detail: String?, progress: Int) -> Unit,
    private val traffic: (tx: Long, rx: Long, speedTx: Long, speedRx: Long) -> Unit,
) {
    private val app = context.applicationContext

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.hasExtra(MsnGuardVpnService.EXTRA_TRAFFIC_TX)) {
                traffic(
                    intent.getLongExtra(MsnGuardVpnService.EXTRA_TRAFFIC_TX, 0L),
                    intent.getLongExtra(MsnGuardVpnService.EXTRA_TRAFFIC_RX, 0L),
                    intent.getLongExtra(MsnGuardVpnService.EXTRA_TRAFFIC_SPEED_TX, 0L),
                    intent.getLongExtra(MsnGuardVpnService.EXTRA_TRAFFIC_SPEED_RX, 0L),
                )
            }
            intent.getStringExtra(MsnGuardVpnService.EXTRA_STATUS)?.let {
                status(
                    it,
                    intent.getStringExtra(MsnGuardVpnService.EXTRA_DETAIL),
                    intent.getIntExtra(MsnGuardVpnService.EXTRA_PROGRESS, -1),
                )
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(MsnGuardVpnService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun start(mode: ConnectionMode, prefs: AppSettings) {
        require(mode.isNative && mode.nativeProtocol != null) { "Only native transports" }
        // Settings remain controlled from Hellboy. Synchronize them into the
        // native engine's real profiled preferences before each connection.
        val source = app.profiled()
        source.edit()
            .putString("dns_servers", prefs.dnsServer)
            .putBoolean(MsnGuardVpnService.IRAN_BYPASS_PREF, prefs.bypassIran)
            .putBoolean(MsnGuardVpnService.AUTO_RECONNECT_PREF, true)
            .putBoolean("kill_switch", prefs.nativeKillSwitch)
            .apply()
        val bypass = prefs.bypassApps
        SplitTunnelSettings(app).save(
            if (bypass.isEmpty()) SplitTunnelSettings.Mode.ALL else SplitTunnelSettings.Mode.EXCLUDE,
            bypass,
        )

        val config = CoreConfig.json(app, mode.nativeProtocol)
        DiagLogger.log("HELLBOY_NATIVE_START ${mode.id}")
        app.startForegroundService(
            Intent(app, MsnGuardVpnService::class.java)
                .setAction(MsnGuardVpnService.ACTION_CONNECT)
                .putExtra(MsnGuardVpnService.EXTRA_CONFIG, config)
        )
    }

    fun stop() {
        DiagLogger.log("HELLBOY_NATIVE_STOP")
        app.startService(
            Intent(app, MsnGuardVpnService::class.java)
                .setAction(MsnGuardVpnService.ACTION_DISCONNECT)
        )
    }
}
