package com.helboy.vpnclub.shortcuts

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.helboy.vpnclub.MainActivity
import com.helboy.vpnclub.corex.VpnController
import kotlinx.coroutines.*

class QuickTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null
    override fun onStartListening() {
        super.onStartListening()
        VpnController.init(applicationContext)
        listening?.cancel()
        listening = scope.launch {
            VpnController.state.collect { st ->
                qsTile?.let { t ->
                    t.state = if (st.sessionActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    t.label = "Hellboy VPN"
                    t.subtitle = if (st.connected) "متصل" else if (st.busyConnecting) "در حال اتصال" else "قطع"
                    t.updateTile()
                }
            }
        }
    }
    override fun onStopListening() { listening?.cancel(); super.onStopListening() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    // The Intent overload is required below API 34; the new overload is guarded below.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val intent = Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_TOGGLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            else { @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
        }
    }
}
