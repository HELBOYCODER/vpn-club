package com.helboy.vpnclub.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.bridge.EngineBridge
import com.helboy.vpnclub.corex.ServerEntry
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.components.ActionButton
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.theme.CardBg
import com.helboy.vpnclub.ui.theme.DarkBg
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.Palette
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.core.VpnManager

/**
 * Pages that come from the protocol engine (profiles, protocol editors, advanced settings). The activity owns
 * them because they need its launchers (file picker, QR scanner, certificate install, deep links); this root
 * only decides where they sit inside the Hellboy shell.
 */
class ProtocolPages(
    /** The profile list with every import and protocol editor ("پروتکل‌ها"). */
    val profiles: @Composable (padding: PaddingValues, goHome: () -> Unit) -> Unit,
    /** Routing, per-app proxy, Tor, monitor, MTU, Telegram proxy, geo files, backup, assistant, logs. */
    val advanced: @Composable (padding: PaddingValues) -> Unit,
    /** Hosts that must stay composed for the whole session (deep-link import, update nudge). */
    val hosts: @Composable (onImported: () -> Unit) -> Unit,
)

/** ریشهٔ UI: پنج تب — خانه، سرورها، پروتکل‌ها، اسکنر IP، تنظیمات. راست‌چین. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AppRoot(
    onToggle: () -> Unit,
    onConnect: (ServerEntry) -> Unit,
    pages: ProtocolPages,
    showOnboarding: Boolean,
    onOnboardingDone: () -> Unit,
) {
    val hellboy by VpnController.state.collectAsState()
    val narcic by VpnManager.status.collectAsState()
    val ui = EngineBridge.overlay(hellboy, narcic)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var advanced by rememberSaveable { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(Modifier.fillMaxSize().concreteWall()) {
            // قطره‌های خون و اخگرهای آتش روی دیوار (قابل خاموش‌کردن در تنظیمات)
            if (com.helboy.vpnclub.ui.theme.ThemeState.effects)
                com.helboy.vpnclub.ui.components.BloodAndFire(Modifier.fillMaxSize(), dark = Palette.isDark)
        }
        pages.hosts { tab = TAB_PROTOCOLS; advanced = false }

        if (showOnboarding) {
            Onboarding(onOnboardingDone)
            return@CompositionLocalProvider
        }

        BackHandler(enabled = advanced) { advanced = false }
        BackHandler(enabled = !advanced && tab != 0) { tab = 0 }

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
                    NavItem(tab == TAB_HOME && !advanced, "nav_home", Icons.Rounded.Home, "خانه", colors) { advanced = false; tab = TAB_HOME }
                    NavItem(tab == TAB_SERVERS && !advanced, "nav_servers", Icons.Rounded.Dns, "سرورها", colors) { advanced = false; tab = TAB_SERVERS }
                    NavItem(tab == TAB_PROTOCOLS && !advanced, "nav_protocols", Icons.Rounded.Hub, "پروتکل‌ها", colors) { advanced = false; tab = TAB_PROTOCOLS }
                    NavItem(tab == TAB_SCANNER && !advanced, "nav_scanner", Icons.Rounded.Radar, "اسکنر IP", colors) { advanced = false; tab = TAB_SCANNER }
                    NavItem(tab == TAB_SETTINGS || advanced, "nav_settings", Icons.Rounded.Settings, "تنظیمات", colors) { advanced = false; tab = TAB_SETTINGS }
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                if (advanced) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { advanced = false }, modifier = Modifier.testTag("advanced_back")) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "بازگشت", tint = TextPrimary)
                            }
                            Text("ابزارها و تنظیمات پیشرفته", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) { pages.advanced(PaddingValues(0.dp)) }
                    }
                } else when (tab) {
                    TAB_HOME -> HomeScreen(ui, onToggle, EngineBridge.externalOf(narcic))
                    TAB_SERVERS -> ServersScreen(ui, onConnect,
                        onRescan = { VpnController.rescanServers() },
                        onInject = { VpnController.injectIps() })
                    TAB_PROTOCOLS -> pages.profiles(PaddingValues(0.dp)) { tab = TAB_HOME }
                    TAB_SCANNER -> ScannerScreen(ui, onScan = { VpnController.scanIps() },
                        onInject = { VpnController.injectIps() })
                    else -> SettingsScreen(VpnController.settings(), onOpenAdvanced = { advanced = true })
                }
            }
        }
    }
}

private const val TAB_HOME = 0
private const val TAB_SERVERS = 1
private const val TAB_PROTOCOLS = 2
private const val TAB_SCANNER = 3
private const val TAB_SETTINGS = 4

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    selected: Boolean,
    tag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    colors: androidx.compose.material3.NavigationBarItemColors,
    onClick: () -> Unit,
) {
    NavigationBarItem(selected = selected, onClick = onClick, colors = colors, modifier = Modifier.testTag(tag),
        icon = { Icon(icon, null) }, label = { Text(label, fontSize = 11.sp, maxLines = 1) })
}

/** خوش‌آمدگویی اولین اجرا: سه نکتهٔ لازم، با همان چهرهٔ هلبوی. */
@Composable
private fun Onboarding(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("onboarding"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("HELLBOY VPN", color = NeonCyan, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
        Text("به هلبوی خوش آمدید", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        listOf(
            "اتصال هوشمند" to "دکمهٔ بزرگ خانه بهترین سرور را پیدا و وصل می‌کند: اسکن IP تمیز کلادفلر، سایفون و WARP رایگان، یا حالت بدون سرور.",
            "همهٔ پروتکل‌ها در یک برنامه" to "از تب «پروتکل‌ها» کانفیگ، اشتراک، QR یا فایل وارد کنید؛ Xray، sing-box، WireGuard، IKEv2، SSH، تونل DNS و Tor پشتیبانی می‌شوند.",
            "مجوزها" to "اندروید یک بار اجازهٔ ساخت VPN و نمایش اعلان را می‌پرسد. ترافیک شما فقط از سرورهایی که خودتان انتخاب کرده‌اید عبور می‌کند.",
        ).forEach { (t, d) ->
            ClubCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(d, color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
        ActionButton("شروع", Icons.Rounded.RocketLaunch, running = false, enabled = true, accent = NeonCyan,
            onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("onboarding_start"))
    }
}

/**
 * پس‌زمینه‌ی «دیوار بتنی» (همان فضای پشت عکس هلبوی): بافت بی‌درز کاشی‌شده + سایه‌ی لبه‌ها.
 */
@Composable
private fun Modifier.concreteWall(): Modifier {
    val p = Palette
    val tex = androidx.compose.ui.graphics.ImageBitmap.imageResource(
        if (p.isDark) dev.valid7996.narcicgeteway.R.drawable.bg_concrete_dark else dev.valid7996.narcicgeteway.R.drawable.bg_concrete_light)
    val brush = remember(tex) {
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
