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
import androidx.core.content.ContextCompat
import com.helboy.vpnclub.ui.screen.VPNClubScreen
import com.helboy.vpnclub.ui.theme.VPNClubTheme
import com.helboy.vpnclub.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleConnection()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+ for VPN foreground service status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            VPNClubTheme {
                VPNClubScreen(
                    viewModel = viewModel,
                    onConnectRequested = {
                        val prepareIntent = viewModel.vpnController.isVpnServicePrepared()
                        if (prepareIntent != null) {
                            vpnPrepareLauncher.launch(prepareIntent)
                        } else {
                            viewModel.toggleConnection()
                        }
                    }
                )
            }
        }
    }
}
