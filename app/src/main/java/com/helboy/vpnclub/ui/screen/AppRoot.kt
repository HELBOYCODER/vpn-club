package com.helboy.vpnclub.ui.screen

import com.helboy.vpnclub.ui.theme.Palette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.helboy.vpnclub.corex.ServerEntry
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.theme.CardBg
import com.helboy.vpnclub.ui.theme.DarkBg
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.TextTertiary

/** ریشهٔ UI: چهار تب جدا — خانه، سرورها، اسکنر IP، تنظیمات. راست‌چین. */
@Composable
fun AppRoot(
    onToggle: () -> Unit,
    onConnect: (ServerEntry) -> Unit,
) {
    val ui by VpnController.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = DarkBg,
            bottomBar = {
                NavigationBar(containerColor = CardBg) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBg, selectedTextColor = NeonCyan,
                        indicatorColor = NeonCyan, unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary,
                    )
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, colors = colors,
                        icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("خانه") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, colors = colors,
                        icon = { Icon(Icons.Rounded.Dns, null) }, label = { Text("سرورها") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, colors = colors,
                        icon = { Icon(Icons.Rounded.Radar, null) }, label = { Text("اسکنر IP") })
                    NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, colors = colors,
                        icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text("تنظیمات") })
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Palette.bgTop, DarkBg, Palette.bgBottom)))
                .padding(pad)) {
                when (tab) {
                    0 -> HomeScreen(ui, onToggle)
                    1 -> ServersScreen(ui, onConnect,
                        onRescan = { VpnController.rescanServers() },
                        onInject = { VpnController.injectIps() })
                    2 -> ScannerScreen(ui, onScan = { VpnController.scanIps() },
                        onInject = { VpnController.injectIps() })
                    else -> SettingsScreen(VpnController.settings())
                }
            }
        }
    }
}
