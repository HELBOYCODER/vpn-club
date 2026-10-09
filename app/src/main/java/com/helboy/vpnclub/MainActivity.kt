package com.helboy.vpnclub

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.helboy.vpnclub.ui.screen.OneButtonScreen
import com.helboy.vpnclub.ui.theme.VPNClubTheme
import com.helboy.vpnclub.ui.viewmodel.MainViewModel

/**
 * تک‌دکمه: هر ضربه = روشن/خاموش. مجوز VPN فقط بار اول از سیستم گرفته می‌شود.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startCycle()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            VPNClubTheme {
                val ui by viewModel.ui.collectAsState()
                OneButtonScreen(
                    ui = ui,
                    onToggle = { viewModel.toggle({ vpnPermissionLauncher.launch(it) }, {}) }
                )
            }
        }
    }
}
