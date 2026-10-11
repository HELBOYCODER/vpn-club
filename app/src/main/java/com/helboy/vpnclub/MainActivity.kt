package com.helboy.vpnclub

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.helboy.vpnclub.corex.ServerEntry
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.screen.AppRoot
import com.helboy.vpnclub.ui.theme.VPNClubTheme

/**
 * ورودی برنامه. منطق در [VpnController] (مستقل از Activity) است؛ اینجا فقط مجوز VPN و UI.
 */
class MainActivity : ComponentActivity() {

    /** کاری که بعد از گرفتن مجوز VPN باید انجام شود. */
    private var pending: (() -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) pending?.invoke()
        pending = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private fun withVpnPermission(action: () -> Unit) {
        val prepare: Intent? = VpnService.prepare(this)
        if (prepare == null) action()
        else { pending = action; vpnPermissionLauncher.launch(prepare) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VpnController.init(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // اتصال خودکار هنگام باز شدن (فقط اگر مجوز VPN از قبل داده شده)
        if (savedInstanceState == null && VpnController.settings().autoConnectOnLaunch &&
            !VpnController.state.value.connected && VpnService.prepare(this) == null) {
            VpnController.connectAuto()
        }

        setContent {
            VPNClubTheme {
                AppRoot(
                    onToggle = {
                        val s = VpnController.state.value
                        if (s.connected || s.testing || s.phase.ordinal in 1..4) VpnController.toggle()
                        else withVpnPermission { VpnController.connectAuto() }
                    },
                    onConnect = { e: ServerEntry -> withVpnPermission { VpnController.connectTo(e) } },
                )
            }
        }
    }
}
