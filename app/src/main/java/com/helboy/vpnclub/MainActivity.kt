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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.helboy.vpnclub.corex.ServerEntry
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.screen.AppRoot
import com.helboy.vpnclub.ui.theme.HellboyTheme

/**
 * ورودی برنامه. منطق در [VpnController] (مستقل از Activity) است؛ اینجا فقط مجوز VPN و UI.
 */
class MainActivity : ComponentActivity() {

    companion object { const val ACTION_TOGGLE = "com.helboy.vpnclub.TOGGLE" }

    /** کاری که بعد از گرفتن مجوز VPN باید انجام شود. */
    private var pending: (() -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) pending?.invoke()
        if (result.resultCode != Activity.RESULT_OK) VpnController.notify("مجوز VPN داده نشد؛ اتصال آغاز نشد")
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
        com.helboy.vpnclub.ui.theme.ThemeState.mode = VpnController.settings().themeMode
        com.helboy.vpnclub.ui.theme.ThemeState.buttonStyle = VpnController.settings().buttonStyle
        com.helboy.vpnclub.ui.theme.ThemeState.effects = VpnController.settings().skinEffects

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // اتصال خودکار هنگام باز شدن (فقط اگر مجوز VPN از قبل داده شده)
        if (intent.action != ACTION_TOGGLE && intent.action != Intent.ACTION_SEND && savedInstanceState == null && VpnController.settings().autoConnectOnLaunch &&
            !VpnController.state.value.connected && VpnService.prepare(this) == null) {
            VpnController.connectFastest()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                VpnController.state.collect { st ->
                    if (st.needsPermission) {
                        VpnController.clearPermissionFlag()
                        withVpnPermission { VpnController.connectFastest() }
                    }
                }
            }
        }

        handleShortcut(intent)

        setContent {
            HellboyTheme {
                AppRoot(
                    onToggle = {
                        val s = VpnController.state.value
                        if (s.connected || s.busyConnecting) VpnController.toggle()
                        else withVpnPermission { VpnController.toggle() }
                    },
                    onConnect = { e: ServerEntry -> withVpnPermission { VpnController.connectTo(e) } },
                )
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcut(intent)
    }

    private fun handleShortcut(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.take(1_048_576).orEmpty().trim()
            intent.action = null
            if (text.startsWith("https://") && !text.contains("\n")) {
                val error = VpnController.addSub("اشتراک‌گذاری", text)
                if (error != null) VpnController.notify(error)
            } else if (text.isNotBlank()) VpnController.importConfigs(text)
            return
        }
        if (intent.action != ACTION_TOGGLE) return
        intent.action = null // rotation / recreation must not toggle a second time
        val st = VpnController.state.value
        if (st.connected || st.busyConnecting) VpnController.toggle()
        else withVpnPermission { VpnController.toggle() }
    }

}
