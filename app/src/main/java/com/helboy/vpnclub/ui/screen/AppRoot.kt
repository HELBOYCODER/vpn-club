package com.helboy.vpnclub.ui.screen

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.helboy.vpnclub.corex.ServerEntry
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.theme.CardBg
import com.helboy.vpnclub.ui.theme.DarkBg
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.TextTertiary
import com.helboy.vpnclub.ui.theme.Palette

/** ریشهٔ UI: چهار تب جدا — خانه، سرورها، اسکنر IP، تنظیمات. راست‌چین. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AppRoot(
    onToggle: () -> Unit,
    onConnect: (ServerEntry) -> Unit,
) {
    val ui by VpnController.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = CardBg) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBg, selectedTextColor = NeonCyan,
                        indicatorColor = NeonCyan, unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary,
                    )
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, colors = colors, modifier = Modifier.testTag("nav_home"),
                        icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("خانه") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, colors = colors, modifier = Modifier.testTag("nav_servers"),
                        icon = { Icon(Icons.Rounded.Dns, null) }, label = { Text("سرورها") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, colors = colors, modifier = Modifier.testTag("nav_scanner"),
                        icon = { Icon(Icons.Rounded.Radar, null) }, label = { Text("اسکنر IP") })
                    NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, colors = colors, modifier = Modifier.testTag("nav_settings"),
                        icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text("تنظیمات") })
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().concreteWall()) {
                // قطره‌های خون و اخگرهای آتش روی دیوار (قابل خاموش‌کردن در تنظیمات)
                if (com.helboy.vpnclub.ui.theme.ThemeState.effects)
                    com.helboy.vpnclub.ui.components.BloodAndFire(Modifier.fillMaxSize(), dark = Palette.isDark)
            }
            Box(Modifier.fillMaxSize().padding(pad)) {
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

/**
 * پس‌زمینه‌ی «دیوار بتنی» (همان فضای پشت عکس هلبوی): بافت بی‌درز کاشی‌شده + سایه‌ی لبه‌ها.
 */
@Composable
private fun Modifier.concreteWall(): Modifier {
    val p = Palette
    val tex = androidx.compose.ui.graphics.ImageBitmap.imageResource(
        if (p.isDark) com.helboy.vpnclub.R.drawable.bg_concrete_dark else com.helboy.vpnclub.R.drawable.bg_concrete_light)
    val brush = androidx.compose.runtime.remember(tex) {
        androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(
            tex, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated))
    }
    return this.drawBehind {
        drawRect(p.bg)
        drawRect(brush)
        // نور از بالا، سایه در پایین و لبه‌ها (مثل نورپردازی عکس)
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(p.bgTop.copy(alpha = 0.10f), androidx.compose.ui.graphics.Color.Transparent, p.bgBottom)))
        drawRect(androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = if (p.isDark) 0.55f else 0.18f)),
            center = center, radius = size.maxDimension * 0.75f))
    }
}
