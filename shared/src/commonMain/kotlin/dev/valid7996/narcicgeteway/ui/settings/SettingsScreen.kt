@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.valid7996.narcicgeteway.ui.settings

import dev.valid7996.narcicgeteway.ui.platform.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.valid7996.narcicgeteway.ui.motion.transitionDecoration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.intl.Locale
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.platform.AppInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import dev.valid7996.narcicgeteway.domain.model.AppLanguage
import dev.valid7996.narcicgeteway.data.update.Distribution
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.domain.model.FragmentPackets
import dev.valid7996.narcicgeteway.domain.model.HevLogLevel
import dev.valid7996.narcicgeteway.domain.model.LogLevel
import dev.valid7996.narcicgeteway.domain.model.OutboundDomainResolve
import dev.valid7996.narcicgeteway.domain.model.RunMode
import dev.valid7996.narcicgeteway.domain.model.ThemeMode
import dev.valid7996.narcicgeteway.domain.model.UiFontScale
import dev.valid7996.narcicgeteway.domain.model.VpnBypassLan
import dev.valid7996.narcicgeteway.domain.model.VpnInterfaceAddress
import dev.valid7996.narcicgeteway.domain.model.SingBoxMuxProtocol
import dev.valid7996.narcicgeteway.domain.model.SingBoxStack
import dev.valid7996.narcicgeteway.domain.model.XudpQuic
import dev.valid7996.narcicgeteway.ui.update.ManualUpdateCheckHost
import dev.valid7996.narcicgeteway.domain.config.LocalPorts
import dev.valid7996.narcicgeteway.domain.config.SingBoxTuning
import dev.valid7996.narcicgeteway.domain.config.effectiveLanSocksPort
import dev.valid7996.narcicgeteway.platform.secureRandomToken
import dev.valid7996.narcicgeteway.domain.model.ConnectButtonStyle
import dev.valid7996.narcicgeteway.ui.theme.applyThemeProfile
import dev.valid7996.narcicgeteway.ui.theme.resetPersonalization

enum class SettingsPage {
    Root, Ui, Vpn, Core, Routing, PerApp, Mux, Fragment, Observatory, Advanced, Mode, Assets, SingBox,
    DnsProtocols, Tor, TorBridges, Ssh, Support, Privacy, Monitor, Ai, AiChat, IpScanner, TelegramProxy, Patt, Backup,
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    contentPadding: PaddingValues,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,

    serverTargets: List<Pair<String, String>> = emptyList(),

    mtuHint: dev.valid7996.narcicgeteway.data.net.MtuServerHint? = null,

    ai: dev.valid7996.narcicgeteway.ui.settings.AiSection? = null,
    configRepository: dev.valid7996.narcicgeteway.data.config.ConfigRepository? = null,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.Root) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showDeviceCode by rememberSaveable { mutableStateOf(false) }

    val goUp: () -> Unit = {
        page = if (page == SettingsPage.TorBridges) SettingsPage.Tor else SettingsPage.Root
    }

    if (showAbout) AboutSheet(onDismiss = { showAbout = false })
    if (showDeviceCode && configRepository != null) {
        dev.valid7996.narcicgeteway.ui.servers.DeviceCodeDialog(
            code = configRepository.nrgDeviceId(),
            onDismiss = { showDeviceCode = false },
        )
    }

    BackHandler(enabled = page != SettingsPage.Root) { goUp() }

    val stateHolder = rememberSaveableStateHolder()

    val goBack: (() -> Unit)? = if (page == SettingsPage.Root) null else goUp
    dev.valid7996.narcicgeteway.ui.components.ProvidePageBack(goBack) {
    val sign = dev.valid7996.narcicgeteway.ui.motion.layoutSign()
    androidx.compose.animation.AnimatedContent(
        targetState = page,
        transitionSpec = {
            dev.valid7996.narcicgeteway.ui.motion.screenTransition(
                style = settings.screenTransition,
                forward = pageDepth(targetState) >= pageDepth(initialState),
                direction = sign,
                reduceMotion = settings.reduceMotion,
            ).using(androidx.compose.animation.SizeTransform(clip = false))
        },
        label = "settings-page",
    ) { shown ->
    val decoration = transitionDecoration(
        style = settings.screenTransition,
        forward = pageDepth(shown) >= pageDepth(page),
        direction = sign,
        reduceMotion = settings.reduceMotion,
    )
    androidx.compose.foundation.layout.Box(decoration) {
    stateHolder.SaveableStateProvider(shown) {
        when (shown) {
            SettingsPage.Root -> RootPage(
                settings = settings,
                contentPadding = contentPadding,
                modifier = modifier,
                onOpen = { page = it },
                onAbout = { showAbout = true },
                onDeviceCode = if (configRepository != null) { { showDeviceCode = true } } else null,
                onUpdate = onUpdate,
                hasAi = ai != null,
            )
            SettingsPage.Ui -> UiPage(settings, contentPadding, modifier, onUpdate, onLanguage)
            SettingsPage.Vpn -> VpnPage(settings, contentPadding, modifier, onUpdate, mtuHint)
            SettingsPage.Core -> CorePage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Mux -> MuxPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.SingBox -> SingBoxPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Fragment -> FragmentPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Observatory -> ObservatoryPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Advanced -> AdvancedPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Mode -> ModePage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Routing -> RoutingScreen(settings, contentPadding, onUpdate, modifier, serverTargets)
            SettingsPage.PerApp -> PerAppProxyScreen(settings, contentPadding, onUpdate, modifier)
            SettingsPage.Assets -> AssetsScreen(settings, contentPadding, modifier, onUpdate)
            SettingsPage.DnsProtocols -> DnsProtocolsPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Tor -> TorPage(settings, contentPadding, modifier, onUpdate) { page = it }
            SettingsPage.TorBridges -> TorBridgesScreen(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Ssh -> SshPage(settings, contentPadding, modifier, onUpdate)
            SettingsPage.Support -> SupportPage(contentPadding, modifier)
            SettingsPage.Privacy -> PrivacyPage(contentPadding, modifier)
            SettingsPage.Monitor -> LiveMonitorPage(contentPadding, modifier)
            SettingsPage.Ai -> ai?.let {
                SettingsPageScaffold(
                    title = stringResource(Res.string.ai_title),
                    subtitle = stringResource(Res.string.ai_subtitle),
                    contentPadding = contentPadding,
                    modifier = modifier,
                ) {
                    AiAssistantPage(
                        settings = it.settings,
                        onUpdate = it.onUpdate,
                        onOpenChat = { page = SettingsPage.AiChat },
                        onOpenKeyPage = it.onOpenUrl,
                    )
                }
            }

            SettingsPage.AiChat -> ai?.let {
                AiChatScreen(
                    settings = it.settings,
                    bridge = it.bridge,
                    languageName = it.languageName,
                    languageNative = it.languageNative,
                    platform = it.platform,
                    appVersion = it.appVersion,
                    contentPadding = contentPadding,
                    modifier = modifier,
                )
            }
            SettingsPage.IpScanner -> IpScannerScreen(
                settings = settings,
                onUpdateSettings = onUpdate,
                contentPadding = contentPadding,
                modifier = modifier,
            )
            SettingsPage.TelegramProxy -> TelegramProxyScreen(
                settings = settings,
                contentPadding = contentPadding,
                modifier = modifier,
            )
            SettingsPage.Patt -> dev.valid7996.narcicgeteway.ui.settings.patt.PattScreen(
                contentPadding = contentPadding,
                settings = settings,
                onUpdateSettings = onUpdate,
                modifier = modifier,
            )
            SettingsPage.Backup -> configRepository?.let { repo ->
                dev.valid7996.narcicgeteway.ui.settings.backup.BackupRestoreScreen(
                    settings = settings,
                    repository = repo,
                    contentPadding = contentPadding,
                    onUpdateSettings = onUpdate,
                    modifier = modifier,
                )
            }
        }
    }
    }
    }
}
}

private fun pageDepth(page: SettingsPage): Int = when (page) {
    SettingsPage.Root -> 0
    SettingsPage.TorBridges -> 2
    else -> 1
}

@Composable
private fun RootPage(
    settings: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onOpen: (SettingsPage) -> Unit,
    onAbout: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,

    hasAi: Boolean,
    onDeviceCode: (() -> Unit)? = null,
) {
    val platform = LocalPlatform.current

    var checkTrigger by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var checking by androidx.compose.runtime.remember { mutableStateOf(false) }
    var showLogs by androidx.compose.runtime.remember { mutableStateOf(false) }
    var showRstaSpoof by androidx.compose.runtime.remember { mutableStateOf(false) }

    SettingsPageScaffold(
        title = stringResource(Res.string.settings_title),
        contentPadding = contentPadding,
        modifier = modifier,
        accentLine = false,
    ) {
        if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS) SettingsGroup {
            SettingsMenuRow(
                stringResource(Res.string.support_title),
                stringResource(Res.string.support_subtitle),
                leadingIcon = Res.drawable.ic_favorite,
                iconTint = Color(0xFFFF2D55),
            ) { onOpen(SettingsPage.Support) }
        }

        SettingsGroup {
            SettingsMenuRow(
                stringResource(Res.string.title_ui_settings),
                stringResource(Res.string.summary_ui_settings),
                leadingIcon = Res.drawable.ic_palette,
                iconTint = Color(0xFF5856D6),
            ) { onOpen(SettingsPage.Ui) }
            if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS || dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_MODE) {
                SettingsDivider()
                SettingsMenuRow(
                    stringResource(Res.string.title_mode_settings),
                    stringResource(Res.string.summary_mode_settings),
                    leadingIcon = Res.drawable.ic_tune,
                    iconTint = Color(0xFF007AFF),
                ) { onOpen(SettingsPage.Mode) }
            }
            if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS || dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_MONITOR) {
                SettingsDivider()
                SettingsMenuRow(
                    stringResource(Res.string.monitor_title),
                    stringResource(Res.string.monitor_subtitle),
                    leadingIcon = Res.drawable.ic_speedometer,
                    iconTint = Color(0xFF34C759),
                ) { onOpen(SettingsPage.Monitor) }
            }
            if (hasAi && dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS) {
                SettingsDivider()
                SettingsMenuRow(
                    stringResource(Res.string.ai_title),
                    stringResource(Res.string.ai_subtitle),
                    leadingIcon = Res.drawable.ic_star,
                    iconTint = Color(0xFFAF52DE),
                ) { onOpen(SettingsPage.Ai) }
            }
        }

        SettingsGroup("ابزارهای شبکه (Network Tools)") {
            SettingsMenuRow(
                "اسکنر آی‌پی تمیز (CDN Scanner)",
                "یافتن آی‌پی‌های بدون فیلتر کلادفلر و CDNها بر اساس اپراتور",
                leadingIcon = Res.drawable.ic_speedometer,
                iconTint = Color(0xFF007AFF),
            ) { onOpen(SettingsPage.IpScanner) }
            SettingsDivider()
            SettingsMenuRow(
                "پروکسی‌های تلگرام (Telegram Proxies)",
                "دریافت، تست پینگ زنده و اتصال مستقیم به پروکسی‌های MTProto",
                leadingIcon = Res.drawable.ic_public,
                iconTint = Color(0xFF34C759),
            ) { onOpen(SettingsPage.TelegramProxy) }
            SettingsDivider()
            SettingsMenuRow(
                "ابزارهای Patt (Patt's Tools)",
                "تست SNI Spoof، بهینه‌سازی Cloudflare Fix و تزریق ECH به کانفیگ‌ها",
                leadingIcon = Res.drawable.ic_tune,
                iconTint = Color(0xFFFF9500),
            ) { onOpen(SettingsPage.Patt) }
        }

        SettingsGroup("پشتیبان‌گیری و انتقال داده (Backup & Data)") {
            SettingsMenuRow(
                "پشتیبان‌گیری و بازیابی (Backup & Restore)",
                "پشتیبان‌گیری کامل از تنظیمات و سرورها و بازیابی سریع اطلاعات",
                leadingIcon = Res.drawable.ic_sync,
                iconTint = Color(0xFF34C759),
            ) { onOpen(SettingsPage.Backup) }
        }

        SettingsGroup {
            SettingsMenuRow(
                stringResource(Res.string.logs_title),
                stringResource(Res.string.logs_settings_sub),
                leadingIcon = Res.drawable.ic_description,
                iconTint = Color(0xFF8E8E93),
            ) { showLogs = true }
            SettingsDivider()
            SettingsMenuRow(
                stringResource(Res.string.rsta_title),
                stringResource(Res.string.rsta_sub),
                leadingIcon = Res.drawable.ic_bolt,
                iconTint = Color(0xFFFF9500),
            ) { showRstaSpoof = true }
        }

        if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS || dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ROUTING) SettingsGroup(
            stringResource(Res.string.routing_title),
        ) {
            SettingsMenuRow(
                stringResource(Res.string.routing_title),
                stringResource(Res.string.routing_subtitle),
                leadingIcon = Res.drawable.ic_public,
                iconTint = Color(0xFF30B0C7),
            ) { onOpen(SettingsPage.Routing) }
            if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS || dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_PER_APP) {
                SettingsDivider()
                SettingsMenuRow(
                    stringResource(Res.string.per_app_title),
                    if (settings.perAppProxyEnabled) {
                        stringResource(Res.string.per_app_selected, settings.perAppPackages.size)
                    } else {
                        stringResource(Res.string.per_app_mode_off)
                    },
                    leadingIcon = Res.drawable.ic_map,
                    iconTint = Color(0xFF5E5CE6),
                ) { onOpen(SettingsPage.PerApp) }
            }
            if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS) {
                SettingsDivider()
                SettingsMenuRow(
                    stringResource(Res.string.assets_title),
                    stringResource(Res.string.assets_subtitle),
                    leadingIcon = Res.drawable.ic_download,
                    iconTint = Color(0xFF00C7BE),
                ) { onOpen(SettingsPage.Assets) }
            }
        }

        if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS || dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_TUNNELS) SettingsGroup(
            stringResource(Res.string.title_tunnels_settings),
        ) {
            SettingsMenuRow(
                stringResource(Res.string.title_dns_protocols),
                stringResource(Res.string.summary_dns_protocols),
                leadingIcon = Res.drawable.ic_dns,
                iconTint = Color(0xFF32ADE6),
            ) { onOpen(SettingsPage.DnsProtocols) }
            SettingsDivider()
            SettingsMenuRow(
                stringResource(Res.string.title_tor_settings),
                stringResource(Res.string.summary_tor_settings),
                leadingIcon = Res.drawable.ic_lock,
                iconTint = Color(0xFFAF52DE),
            ) { onOpen(SettingsPage.Tor) }
            SettingsDivider()
            SettingsMenuRow(
                stringResource(Res.string.title_ssh_settings),
                stringResource(Res.string.summary_ssh_settings),
                leadingIcon = Res.drawable.ic_encrypted,
                iconTint = Color(0xFF48484A),
            ) { onOpen(SettingsPage.Ssh) }
        }

        SettingsGroup(
            stringResource(Res.string.title_core_settings),
        ) {
            SettingsMenuRow(
                stringResource(Res.string.title_vpn_settings),
                stringResource(Res.string.summary_vpn_settings),
                leadingIcon = Res.drawable.ic_lock,
                iconTint = Color(0xFF28CD41),
            ) { onOpen(SettingsPage.Vpn) }
            SettingsDivider()
            SettingsMenuRow(
                stringResource(Res.string.title_core_settings),
                stringResource(Res.string.summary_core_settings),
                leadingIcon = Res.drawable.ic_settings,
                iconTint = Color(0xFF007AFF),
            ) { onOpen(SettingsPage.Core) }
            if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS || dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_CORE_FULL) {
                SettingsDivider()
                SettingsMenuRow(
                    stringResource(Res.string.title_mux_settings),
                    stringResource(Res.string.summary_mux_settings),
                    leadingIcon = Res.drawable.ic_speed,
                    iconTint = Color(0xFF5856D6),
                ) { onOpen(SettingsPage.Mux) }
            }
            if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS || dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_FRAGMENT || dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_CORE_FULL) {
                SettingsDivider()
                SettingsMenuRow(
                    stringResource(Res.string.title_fragment_settings),
                    stringResource(Res.string.summary_fragment_settings),
                    leadingIcon = Res.drawable.ic_contrast,
                    iconTint = Color(0xFFFF9500),
                ) { onOpen(SettingsPage.Fragment) }
            }
            if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS || dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_CORE_FULL) {
                SettingsDivider()
                SettingsMenuRow(
                    stringResource(Res.string.title_singbox_settings),
                    stringResource(Res.string.summary_singbox_settings),
                    leadingIcon = Res.drawable.ic_nrg_mark,
                    iconTint = Color(0xFFFF2D55),
                ) { onOpen(SettingsPage.SingBox) }
            }
            if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS || dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_AUTO_SELECT || dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_CORE_FULL) {
                SettingsDivider()
                SettingsMenuRow(
                    stringResource(Res.string.title_observatory_settings),
                    stringResource(Res.string.summary_observatory_settings),
                    leadingIcon = Res.drawable.ic_sync,
                    iconTint = Color(0xFF0A84FF),
                ) { onOpen(SettingsPage.Observatory) }
            }
            if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS || dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_CORE_FULL) {
                SettingsDivider()
                SettingsMenuRow(
                    stringResource(Res.string.title_advanced),
                    stringResource(Res.string.summary_advanced),
                    leadingIcon = Res.drawable.ic_tune,
                    iconTint = Color(0xFF8E8E93),
                ) { onOpen(SettingsPage.Advanced) }
            }
        }

        SettingsGroup(
            stringResource(Res.string.settings_about),
        ) {
            val fromPlay = platform.distribution == Distribution.PlayStore
            if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS) {
                SettingsMenuRow(
                    stringResource(Res.string.update_check_title),
                    when {
                        checking -> stringResource(Res.string.update_checking)
                        fromPlay -> stringResource(Res.string.update_check_summary)
                        else -> stringResource(Res.string.update_check_summary_github)
                    },
                    leadingIcon = Res.drawable.ic_sync,
                    iconTint = Color(0xFF34C759),
                ) { if (!checking) { checking = true; checkTrigger++ } }
                SettingsDivider()
                SettingsMenuRow(
                    stringResource(if (fromPlay) Res.string.rate_title else Res.string.rate_title_github),
                    stringResource(if (fromPlay) Res.string.rate_summary else Res.string.rate_summary_github),
                    leadingIcon = Res.drawable.ic_star,
                    iconTint = Color(0xFFFFCC00),
                ) { platform.openStorePage() }
                SettingsDivider()
                SettingsMenuRow(
                    stringResource(Res.string.privacy_title),
                    stringResource(Res.string.privacy_settings_sub),
                    leadingIcon = Res.drawable.ic_policy,
                    iconTint = Color(0xFFFF3B30),
                ) { onOpen(SettingsPage.Privacy) }
                SettingsDivider()
            }
            SettingsMenuRow(
                stringResource(Res.string.settings_about),
                "",
                leadingIcon = Res.drawable.ic_info,
                iconTint = Color(0xFF007AFF),
            ) {
                onAbout()
            }
            if (onDeviceCode != null) {
                SettingsDivider()
                SettingsMenuRow(
                    "کد دستگاه من (برای کانفیگ قفل‌شده)",
                    "مشاهده و کپی کد یکتای این دستگاه جهت دریافت کانفیگ قفل‌شده",
                    leadingIcon = Res.drawable.ic_lock,
                    iconTint = Color(0xFFFF9500),
                ) {
                    onDeviceCode()
                }
            }
        }

        ManualUpdateCheckHost(
            settings = settings,
            trigger = checkTrigger,
            onFinished = { checking = false },
        )

        if (showLogs) LogSheet(onDismiss = { showLogs = false })
        if (showRstaSpoof) RstaSpoofSheet(settings = settings, onUpdate = { updated -> onUpdate { updated } }, onDismiss = { showRstaSpoof = false })
    }
}

@Composable
private fun DnsProtocolsPage(
    s: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(Res.string.title_dns_protocols),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        SettingsGroup {
            SettingsInfoRow(
                stringResource(Res.string.dns_protocols_about_title),
                stringResource(Res.string.dns_protocols_about_body),
            )
        }

        SettingsGroup(stringResource(Res.string.dns_global_resolver_group)) {
            SettingsSwitchRow(
                title = stringResource(Res.string.dns_override),
                summary = stringResource(Res.string.dns_override_sum),
                checked = s.dnsGlobalResolverEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(dnsGlobalResolverEnabled = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.dns_resolver_ips),
                value = s.dnsGlobalResolvers,
                enabled = s.dnsGlobalResolverEnabled,
                placeholder = "8.8.8.8, 1.1.1.1",
                onValueChanged = { v -> onUpdate { it.copy(dnsGlobalResolvers = v) } },
            )
        }

        SettingsGroup(stringResource(Res.string.dns_pool_group)) {
            SettingsSwitchRow(
                title = stringResource(Res.string.dns_pool_enable),
                summary = stringResource(Res.string.dns_pool_enable_sum),
                checked = s.dnsPoolEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(dnsPoolEnabled = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.dns_pool_list),
                value = s.dnsPoolText,
                enabled = s.dnsPoolEnabled,
                placeholder = "8.8.8.8\n1.1.1.1",
                onValueChanged = { v -> onUpdate { it.copy(dnsPoolText = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.dns_pool_verify),
                summary = stringResource(
                    if (s.dnsPoolFullVerification) Res.string.dns_pool_verify_on else Res.string.dns_pool_verify_off,
                ),
                enabled = s.dnsPoolEnabled,
                checked = s.dnsPoolFullVerification,
                onCheckedChange = { v -> onUpdate { it.copy(dnsPoolFullVerification = v) } },
            )
        }

        SettingsGroup(stringResource(Res.string.dns_remote_group)) {
            SettingsListRow(
                title = stringResource(Res.string.dns_remote_server),
                options = listOf(
                    "default" to stringResource(Res.string.dns_remote_default),
                    "custom" to stringResource(Res.string.dns_remote_custom),
                ),
                selected = s.remoteDnsMode,
                onSelected = { v -> onUpdate { it.copy(remoteDnsMode = v) } },
            )
            if (s.remoteDnsMode == "custom") {
                SettingsDivider(hasIcon = false)
                SettingsEditRow(
                    title = stringResource(Res.string.dns_remote_primary), value = s.remoteDnsPrimary, placeholder = "9.9.9.9",
                    onValueChanged = { v -> onUpdate { it.copy(remoteDnsPrimary = v) } },
                )
                SettingsDivider(hasIcon = false)
                SettingsEditRow(
                    title = stringResource(Res.string.dns_remote_fallback), value = s.remoteDnsFallback, placeholder = "8.8.8.8",
                    onValueChanged = { v -> onUpdate { it.copy(remoteDnsFallback = v) } },
                )
            }
        }

        SettingsGroup(stringResource(Res.string.dns_behaviour_group)) {
            SettingsListRow(
                title = stringResource(Res.string.dns_workers),
                options = listOf(
                    "per_query" to stringResource(Res.string.dns_workers_per_query),
                    "two" to stringResource(Res.string.dns_workers_2),
                    "three" to stringResource(Res.string.dns_workers_3),
                    "five" to stringResource(Res.string.dns_workers_5),
                ),
                selected = s.dnsWorkerMode,
                onSelected = { v -> onUpdate { it.copy(dnsWorkerMode = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsInfoRow(
                title = stringResource(Res.string.dns_prevent_fallback),
                body = stringResource(Res.string.dns_prevent_fallback_always),
            )
        }
    }
}

@Composable
private fun TorPage(
    s: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onOpen: (SettingsPage) -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(Res.string.title_tor_settings),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        SettingsGroup(stringResource(Res.string.tor_bridges_group)) {
            SettingsListRow(
                title = stringResource(Res.string.tor_bridges_mode),
                options = listOf(
                    "none" to stringResource(Res.string.tor_bridges_none),
                    "default" to stringResource(Res.string.tor_bridges_default),
                    "own" to stringResource(Res.string.tor_bridges_own),
                ),
                selected = s.torBridgesMode,
                onSelected = { v -> onUpdate { it.copy(torBridgesMode = v) } },
            )
            if (s.torBridgesMode != "none") {
                val transports = if (s.torBridgesMode == "own") {
                    listOf("obfs4", "obfs3", "scramblesuit", "meek_lite", "snowflake", "conjure", "webtunnel", "vanilla")
                } else {
                    listOf("obfs4", "obfs3", "meek_lite", "snowflake", "conjure", "webtunnel", "vanilla")
                }
                SettingsDivider(hasIcon = false)
                SettingsListRow(
                    title = stringResource(Res.string.tor_bridge_transport),
                    options = transports.map { it to it },
                    selected = s.torBridgeTransport,
                    onSelected = { v -> onUpdate { it.copy(torBridgeTransport = v) } },
                )
                if (s.torBridgeTransport == "snowflake") {
                    SettingsDivider(hasIcon = false)
                    SettingsListRow(
                        title = stringResource(Res.string.tor_rendezvous),
                        options = listOf("amp" to "AMP", "cdn77" to "CDN77", "amazon" to "AMAZON"),
                        selected = s.torSnowflakeRendezvous,
                        onSelected = { v -> onUpdate { it.copy(torSnowflakeRendezvous = v) } },
                    )
                    SettingsDivider(hasIcon = false)
                    SettingsEditRow(
                        title = stringResource(Res.string.tor_snowflake_stun_hint),
                        value = s.torSnowflakeStun,
                        onValueChanged = { v -> onUpdate { it.copy(torSnowflakeStun = v) } },
                    )
                }
                if (s.torBridgesMode == "own") {
                    SettingsDivider(hasIcon = false)
                    SettingsEditRow(
                        title = stringResource(Res.string.tor_own_bridges),
                        value = s.torOwnBridges,
                        placeholder = "obfs4 1.2.3.4:443 CERT… iat-mode=0",
                        onValueChanged = { v -> onUpdate { it.copy(torOwnBridges = v) } },
                    )
                } else {
                    SettingsDivider(hasIcon = false)
                    SettingsMenuRow(
                        stringResource(Res.string.tor_bridges_title),
                        stringResource(Res.string.tor_bridges_browse_sum),
                    ) { onOpen(SettingsPage.TorBridges) }
                }
            }
        }

        SettingsGroup(stringResource(Res.string.tor_nodes_group)) {
            SettingsEditRow(
                title = stringResource(Res.string.tor_entry_nodes_t),
                value = s.torEntryNodes, placeholder = "{de},{nl}",
                onValueChanged = { v -> onUpdate { it.copy(torEntryNodes = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.tor_exit_nodes_t),
                value = s.torExitNodes, placeholder = "{us},{gb}",
                onValueChanged = { v -> onUpdate { it.copy(torExitNodes = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.tor_exclude_nodes_t),
                value = s.torExcludeNodes, placeholder = "{ru},{cn}",
                onValueChanged = { v -> onUpdate { it.copy(torExcludeNodes = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.tor_exclude_exit_nodes_t),
                value = s.torExcludeExitNodes, placeholder = "{ru},{cn}",
                onValueChanged = { v -> onUpdate { it.copy(torExcludeExitNodes = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.tor_strict_nodes_t),
                summary = stringResource(Res.string.tor_strict_nodes_t_sum),
                checked = s.torStrictNodes,
                onCheckedChange = { v -> onUpdate { it.copy(torStrictNodes = v) } },
            )
        }

        SettingsGroup(stringResource(Res.string.tor_common_group)) {
            SettingsEditRow(
                title = stringResource(Res.string.tor_virtual_addr), value = s.torVirtualAddrNetwork,
                onValueChanged = { v -> onUpdate { it.copy(torVirtualAddrNetwork = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.tor_hardware_accel),
                summary = stringResource(Res.string.tor_hardware_accel_sum),
                checked = s.torHardwareAccel,
                onCheckedChange = { v -> onUpdate { it.copy(torHardwareAccel = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.tor_avoid_disk),
                summary = stringResource(Res.string.tor_avoid_disk_sum),
                checked = s.torAvoidDiskWrites,
                onCheckedChange = { v -> onUpdate { it.copy(torAvoidDiskWrites = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.tor_conn_padding),
                summary = stringResource(Res.string.tor_conn_padding_sum),
                checked = s.torConnectionPadding,
                onCheckedChange = { v -> onUpdate { it.copy(torConnectionPadding = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.tor_reduced_padding),
                summary = stringResource(Res.string.tor_reduced_padding_sum),
                checked = s.torReducedConnectionPadding,
                onCheckedChange = { v -> onUpdate { it.copy(torReducedConnectionPadding = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.tor_fascist),
                summary = stringResource(Res.string.tor_fascist_sum),
                checked = s.torFascistFirewall,
                onCheckedChange = { v -> onUpdate { it.copy(torFascistFirewall = v) } },
            )
            SettingsDivider(hasIcon = false)
            IntEditRow(stringResource(Res.string.tor_new_circuit), s.torNewCircuitPeriod) { v -> onUpdate { it.copy(torNewCircuitPeriod = v) } }
            SettingsDivider(hasIcon = false)
            IntEditRow(stringResource(Res.string.tor_max_dirtiness), s.torMaxCircuitDirtiness) { v -> onUpdate { it.copy(torMaxCircuitDirtiness = v) } }
            SettingsDivider(hasIcon = false)
            IntEditRow(stringResource(Res.string.tor_dormant_timeout), s.torDormantClientTimeout) { v -> onUpdate { it.copy(torDormantClientTimeout = v) } }
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.tor_distinct_subnets),
                summary = stringResource(Res.string.tor_distinct_subnets_sum),
                checked = s.torEnforceDistinctSubnets,
                onCheckedChange = { v -> onUpdate { it.copy(torEnforceDistinctSubnets = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.tor_track_host),
                summary = stringResource(Res.string.tor_track_host_sum),
                checked = s.torTrackHostExits,
                onCheckedChange = { v -> onUpdate { it.copy(torTrackHostExits = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.tor_client_ipv4), checked = s.torClientUseIPv4,
                onCheckedChange = { v -> onUpdate { it.copy(torClientUseIPv4 = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.tor_client_ipv6), checked = s.torClientUseIPv6,
                onCheckedChange = { v -> onUpdate { it.copy(torClientUseIPv6 = v) } },
            )
        }

        SettingsGroup(stringResource(Res.string.tor_isolation_group)) {
            SettingsSwitchRow(
                title = stringResource(Res.string.tor_isolate_addr_t),
                summary = stringResource(Res.string.tor_isolate_addr_t_sum),
                checked = s.torIsolateDestAddr,
                onCheckedChange = { v -> onUpdate { it.copy(torIsolateDestAddr = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.tor_isolate_port_t),
                summary = stringResource(Res.string.tor_isolate_port_t_sum),
                checked = s.torIsolateDestPort,
                onCheckedChange = { v -> onUpdate { it.copy(torIsolateDestPort = v) } },
            )
        }

        SettingsGroup(stringResource(Res.string.tor_advanced_group)) {
            SettingsSwitchRow(
                title = stringResource(Res.string.tor_fake_sni),
                summary = stringResource(Res.string.tor_fake_sni_sum),
                checked = s.torFakeSniEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(torFakeSniEnabled = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.tor_fake_sni_hosts),
                value = s.torFakeSniHosts,
                enabled = s.torFakeSniEnabled,
                placeholder = "play.googleapis.com, drive.google.com (blank = defaults)",
                onValueChanged = { v -> onUpdate { it.copy(torFakeSniHosts = v) } },
            )
        }
    }
}

@Composable
private fun IntEditRow(title: String, value: Int, onChange: (Int) -> Unit) {
    SettingsEditRow(
        title = title,
        value = value.toString(),
        numeric = true,
        onValueChanged = { it.toIntOrNull()?.let(onChange) },
    )
}

@Composable
private fun SshPage(
    s: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(Res.string.title_ssh_settings),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        SettingsGroup {
            SettingsListRow(
                title = stringResource(Res.string.ssh_cipher),
                options = listOf(
                    "auto" to stringResource(Res.string.ssh_cipher_auto),
                    "aes128-gcm@openssh.com" to "AES-128-GCM",
                    "chacha20-poly1305@openssh.com" to "ChaCha20-Poly1305",
                    "aes256-gcm@openssh.com" to "AES-256-GCM",
                    "aes128-ctr" to stringResource(Res.string.ssh_cipher_legacy),
                ),
                selected = s.sshCipher,
                onSelected = { v -> onUpdate { it.copy(sshCipher = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.ssh_compression),
                summary = stringResource(Res.string.ssh_compression_sum),
                checked = s.sshCompression,
                onCheckedChange = { v -> onUpdate { it.copy(sshCompression = v) } },
            )
            SettingsDivider(hasIcon = false)
            IntEditRow(stringResource(Res.string.ssh_max_channels), s.sshMaxChannels) { v ->
                onUpdate { it.copy(sshMaxChannels = v.coerceIn(1, 64)) }
            }
        }
    }
}

@Composable
private fun UiPage(
    s: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(Res.string.title_ui_settings),
        subtitle = stringResource(Res.string.summary_ui_settings),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        SettingsGroup {
            SettingsListRow(
                title = stringResource(Res.string.title_pref_ui_mode_night),
                options = listOf(
                    ThemeMode.System to stringResource(Res.string.settings_theme_system),
                    ThemeMode.Light to stringResource(Res.string.settings_theme_light),
                    ThemeMode.Dark to stringResource(Res.string.settings_theme_dark),
                ),
                selected = s.themeMode,
                onSelected = { v -> onUpdate { it.copy(themeMode = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_dynamic_color),
                summary = stringResource(Res.string.summary_pref_dynamic_color),
                checked = s.dynamicColor,
                enabled = LocalPlatform.current.supportsDynamicColor,
                onCheckedChange = { v -> onUpdate { it.copy(dynamicColor = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_amoled),
                summary = stringResource(Res.string.summary_pref_amoled),
                checked = s.amoledBlack,
                onCheckedChange = { v -> onUpdate { it.copy(amoledBlack = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = stringResource(Res.string.settings_language),
                options = listOf(
                    AppLanguage.System to stringResource(Res.string.settings_language_system),
                    AppLanguage.English to "English",
                    AppLanguage.Persian to "فارسی",
                    AppLanguage.Chinese to "中文",
                    AppLanguage.Russian to "Русский",
                ),
                selected = s.language,
                onSelected = onLanguage,
            )
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = stringResource(Res.string.personalize_font_size),
                options = listOf(
                    UiFontScale.Smallest to stringResource(Res.string.personalize_font_smallest),
                    UiFontScale.Smaller to stringResource(Res.string.personalize_font_smaller),
                    UiFontScale.Normal to stringResource(Res.string.personalize_font_normal),
                    UiFontScale.Larger to stringResource(Res.string.personalize_font_larger),
                    UiFontScale.Largest to stringResource(Res.string.personalize_font_largest),
                ),
                selected = s.uiFontScale,
                onSelected = { v -> onUpdate { it.copy(uiFontScale = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = "حالت انیمیشن",
                options = listOf(
                    dev.valid7996.narcicgeteway.domain.model.AnimationMode.Normal to "حالت معمولی",
                    dev.valid7996.narcicgeteway.domain.model.AnimationMode.Slow to "حالت آهسته",
                    dev.valid7996.narcicgeteway.domain.model.AnimationMode.Off to "حالت خاموش (صرفه‌جویی باتری)",
                ),
                selected = s.animationMode,
                onSelected = { v -> onUpdate { it.copy(animationMode = v, reduceMotion = (v == dev.valid7996.narcicgeteway.domain.model.AnimationMode.Off)) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = "حالت ایمپورت",
                options = listOf(
                    dev.valid7996.narcicgeteway.domain.model.ImportTargetMode.Ask to "پرسش هنگام افزودن کانفیگ",
                    dev.valid7996.narcicgeteway.domain.model.ImportTargetMode.Subscription to "افزودن به سابسکرپشن شخصی",
                    dev.valid7996.narcicgeteway.domain.model.ImportTargetMode.Manual to "ذخیره در بخش دستی",
                ),
                selected = s.importTargetMode,
                onSelected = { v -> onUpdate { it.copy(importTargetMode = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsActionRow(
                title = "ویزارد خوش‌آمدگویی و راه‌اندازی اولیه",
                summary = "تنظیم مجدد زبان، قالب و حالت نمایش",
                onClick = { onUpdate { it.copy(onboardingVersion = 0) } },
            )
        }

        ThemeProfileRow(
            label = stringResource(Res.string.theme_profile_title),
            subtitle = stringResource(Res.string.theme_profile_sub),
            defaultLabel = stringResource(Res.string.theme_profile_none),
            selectedId = s.themeProfileId,
            onSelect = { profile ->
                onUpdate { settings ->
                    if (profile != null) {
                        settings.applyThemeProfile(profile)
                    } else {
                        settings.resetPersonalization().copy(themeProfileId = "narcis", dynamicColor = false)
                    }
                }
            },
        )

        ConnectStyleRow(
            label = stringResource(Res.string.connect_style_title),
            subtitle = stringResource(Res.string.connect_style_sub),
            selected = s.connectButtonStyle,
            nameOf = { style -> stringResource(style.labelRes()) },
            onSelect = { style -> onUpdate { it.copy(connectButtonStyle = style) } },
        )
    }
}

@Composable
private fun VpnPage(
    s: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,

    mtuHint: dev.valid7996.narcicgeteway.data.net.MtuServerHint?,
) {
    val isVpn = s.isVpnMode
    val hevOn = isVpn
    SettingsPageScaffold(
        title = stringResource(Res.string.title_vpn_settings),
        subtitle = stringResource(Res.string.summary_vpn_settings),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        SettingsGroup {
            val batteryPlatform = LocalPlatform.current
            if (batteryPlatform.supportsBatteryOptimization) {
                var exempt by remember { mutableStateOf(batteryPlatform.isIgnoringBatteryOptimizations) }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    exempt = batteryPlatform.isIgnoringBatteryOptimizations
                }
                SettingsActionRow(
                    title = stringResource(Res.string.battery_opt_title),
                    summary = stringResource(
                        if (exempt) Res.string.battery_opt_off else Res.string.battery_opt_on,
                    ),
                    onClick = { batteryPlatform.openBatteryOptimizationSettings() },
                )
                SettingsDivider(hasIcon = false)
            }
            SettingsSwitchRow(
                title = stringResource(Res.string.kill_switch),
                summary = stringResource(Res.string.kill_switch_sum),
                checked = s.killSwitch,
                onCheckedChange = { v -> onUpdate { it.copy(killSwitch = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_ipv6_enabled),
                summary = stringResource(Res.string.summary_pref_ipv6_enabled),
                checked = s.enableIpv6,
                onCheckedChange = { v -> onUpdate { it.copy(enableIpv6 = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_prefer_ipv6),
                summary = stringResource(Res.string.summary_pref_prefer_ipv6),
                checked = s.preferIpv6,
                onCheckedChange = { v -> onUpdate { it.copy(preferIpv6 = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = stringResource(Res.string.title_pref_vpn_interface_address),
                options = VpnInterfaceAddress.entries.map { it to it.label },
                selected = s.vpnInterfaceAddress,
                enabled = isVpn,
                onSelected = { v -> onUpdate { it.copy(vpnInterfaceAddress = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_vpn_mtu),
                value = s.vpnMtu.toString(),
                enabled = isVpn,
                numeric = true,
                onValueChanged = { v ->
                    v.toIntOrNull()?.coerceIn(1000, 9000)?.let { mtu ->
                        onUpdate { it.copy(vpnMtu = mtu) }
                    }
                },
            )
            SettingsDivider(hasIcon = false)
            MtuOptimizeRow(
                enabled = isVpn,
                serverHost = mtuHint?.host,
                overhead = mtuHint?.overhead ?: dev.valid7996.narcicgeteway.data.net.MtuOverheads.worstCase(),
            ) { mtu -> onUpdate { it.copy(vpnMtu = mtu) } }
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = stringResource(Res.string.title_pref_vpn_bypass_lan),
                options = listOf(
                    VpnBypassLan.FollowConfig to stringResource(Res.string.routing_mode_global),
                    VpnBypassLan.Bypass to stringResource(Res.string.routing_mode_bypass_lan),
                    VpnBypassLan.NotBypass to stringResource(Res.string.routing_mode_bypass_mainland),
                ),
                selected = s.vpnBypassLan,
                enabled = isVpn,
                onSelected = { v -> onUpdate { it.copy(vpnBypassLan = v) } },
            )
        }

        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_local_dns_enabled),
                summary = stringResource(Res.string.summary_pref_local_dns_enabled),
                checked = s.localDnsEnabled,
                enabled = isVpn,
                onCheckedChange = { v -> onUpdate { it.copy(localDnsEnabled = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_fake_dns_enabled),
                summary = stringResource(Res.string.summary_pref_fake_dns_enabled),
                checked = s.fakeDnsEnabled,
                enabled = isVpn && s.localDnsEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(fakeDnsEnabled = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_vpn_dns),
                value = s.vpnDns,
                enabled = isVpn && !s.localDnsEnabled,
                onValueChanged = { v -> onUpdate { it.copy(vpnDns = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_append_http_proxy),
                summary = stringResource(Res.string.summary_pref_append_http_proxy),
                checked = s.appendHttpProxy,
                enabled = s.effectiveLocalProxy,
                onCheckedChange = { v -> onUpdate { it.copy(appendHttpProxy = v) } },
            )
        }

        if (LocalPlatform.current.choosesTunEngine) {
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(Res.string.title_pref_use_hev_tunnel),
                    summary = stringResource(Res.string.summary_pref_use_hev_tunnel),
                    checked = s.useHevTun,
                    enabled = hevOn,
                    onCheckedChange = { v -> onUpdate { it.copy(useHevTun = v) } },
                )
                if (s.useHevTun) {
                    SettingsDivider(hasIcon = false)
                    SettingsListRow(
                        title = stringResource(Res.string.tun_engine_title),
                        options = listOf(
                            true to stringResource(Res.string.tun_engine_zeptun),
                            false to stringResource(Res.string.tun_engine_hev),
                        ),
                        selected = s.useZepTun,
                        enabled = hevOn,
                        onSelected = { v -> onUpdate { it.copy(useZepTun = v) } },
                    )
                    SettingsDivider(hasIcon = false)
                    SettingsInfoRow(
                        stringResource(Res.string.tun_engine_title),
                        stringResource(Res.string.tun_engine_sub),
                    )
                    SettingsDivider(hasIcon = false)
                    SettingsListRow(
                        title = stringResource(Res.string.title_pref_hev_tunnel_loglevel),
                        options = HevLogLevel.entries.map { it to it.value },
                        selected = s.hevTunLogLevel,
                        enabled = hevOn,
                        onSelected = { v -> onUpdate { it.copy(hevTunLogLevel = v) } },
                    )
                    SettingsDivider(hasIcon = false)
                    SettingsEditRow(
                        title = stringResource(Res.string.title_pref_hev_tunnel_rw_timeout),
                        value = s.hevTunRwTimeout,
                        enabled = hevOn,
                        placeholder = stringResource(Res.string.summary_pref_hev_tunnel_rw_timeout),
                        onValueChanged = { v -> onUpdate { it.copy(hevTunRwTimeout = v) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun CorePage(
    s: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    val localProxy = s.effectiveLocalProxy
    SettingsPageScaffold(
        title = stringResource(Res.string.title_core_settings),
        subtitle = stringResource(Res.string.summary_core_settings),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_sniffing_enabled),
                summary = stringResource(Res.string.summary_pref_sniffing_enabled),
                checked = s.sniffingEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(sniffingEnabled = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_route_only_enabled),
                summary = stringResource(Res.string.summary_pref_route_only_enabled),
                checked = s.routeOnly,
                onCheckedChange = { v -> onUpdate { it.copy(routeOnly = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = stringResource(Res.string.title_core_loglevel),
                options = LogLevel.entries.map { it to it.value },
                selected = s.logLevel,
                onSelected = { v -> onUpdate { it.copy(logLevel = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = stringResource(Res.string.title_outbound_domain_resolve_method),
                options = listOf(
                    OutboundDomainResolve.DoNotResolve to "Do not resolve",
                    OutboundDomainResolve.ResolveAndAddToHosts to "Resolve and add to DNS hosts",
                    OutboundDomainResolve.ResolveAndReplace to "Resolve and replace domain",
                ),
                selected = s.outboundDomainResolve,
                onSelected = { v -> onUpdate { it.copy(outboundDomainResolve = v) } },
            )
        }

        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_enable_local_proxy),
                summary = stringResource(Res.string.summary_pref_enable_local_proxy),
                checked = s.enableLocalProxy,

                enabled = !s.localProxyForced,
                onCheckedChange = { v ->
                    onUpdate {
                        it.copy(
                            enableLocalProxy = v,
                            appendHttpProxy = if (!v) false else it.appendHttpProxy,
                        )
                    }
                },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_proxy_sharing_enabled),
                summary = stringResource(Res.string.summary_pref_proxy_sharing_enabled),
                checked = s.proxySharing,
                enabled = localProxy,
                onCheckedChange = { v ->
                    onUpdate {
                        if (v && (it.socksUsername.isBlank() || it.socksPassword.isBlank())) {
                            it.copy(
                                proxySharing = true,
                                socksUsername = it.socksUsername.ifBlank { LAN_DEFAULT_USER },
                                socksPassword = it.socksPassword.ifBlank { secureRandomToken(LAN_PASSWORD_LENGTH) },
                            )
                        } else {
                            it.copy(proxySharing = v)
                        }
                    }
                },
            )
            if (s.proxySharing) {
                SettingsDivider(hasIcon = false)
                LanShareAddressRow(s)
            }
            val platform = LocalPlatform.current
            val invalidPort = stringResource(Res.string.toast_lan_port_invalid)
            val credentialRequired = stringResource(Res.string.toast_lan_credential_required)
            val sharing = localProxy && s.proxySharing
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_lan_port),
                value = s.effectiveLanSocksPort.toString(),
                enabled = sharing,
                numeric = true,
                onValueChanged = { v ->
                    val port = v.trim().toIntOrNull()

                    if (port != null && LocalPorts.lanSocksPort(port) == port) {
                        onUpdate { it.copy(socksPort = port) }
                    } else {
                        platform.toast(invalidPort)
                    }
                },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_lan_username),
                value = s.socksUsername,
                enabled = sharing,
                onValueChanged = { v ->
                    if (v.isBlank()) platform.toast(credentialRequired)
                    else onUpdate { it.copy(socksUsername = v.trim()) }
                },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_lan_password),
                value = s.socksPassword,
                enabled = sharing,
                isPassword = true,
                onValueChanged = { v ->
                    if (v.isBlank()) platform.toast(credentialRequired)
                    else onUpdate { it.copy(socksPassword = v) }
                },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_socks_enable_udp),
                summary = stringResource(Res.string.summary_pref_socks_enable_udp),
                checked = s.socksEnableUdp,
                enabled = sharing,
                onCheckedChange = { v -> onUpdate { it.copy(socksEnableUdp = v) } },
            )
        }

        SettingsGroup {
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_remote_dns),
                value = s.remoteDns,
                placeholder = stringResource(Res.string.summary_pref_remote_dns),
                onValueChanged = { v -> onUpdate { it.copy(remoteDns = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_domestic_dns),
                value = s.directDns,
                onValueChanged = { v -> onUpdate { it.copy(directDns = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_dns_hosts),
                value = s.dnsHosts,
                placeholder = stringResource(Res.string.summary_pref_dns_hosts),
                onValueChanged = { v -> onUpdate { it.copy(dnsHosts = v) } },
            )
        }
    }
}

private const val LAN_DEFAULT_USER = "nrg"

private const val LAN_PASSWORD_LENGTH = 16

@Composable
private fun LanShareAddressRow(s: AppSettings) {
    val platform = LocalPlatform.current
    var addresses by remember { mutableStateOf(platform.lanIpv4Addresses()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { addresses = platform.lanIpv4Addresses() }
    val port = s.effectiveLanSocksPort
    val userLine = stringResource(Res.string.lan_share_user, s.socksUsername)
    val none = stringResource(Res.string.lan_share_no_address)
    SettingsInfoRow(
        title = stringResource(Res.string.lan_share_connect_title),
        body = if (addresses.isEmpty()) {
            none
        } else {
            buildString {
                addresses.forEach { ip ->
                    appendLine("SOCKS5  $ip:$port")
                    if (s.appendHttpProxy) appendLine("HTTP  $ip:${port + 1}")
                }
                append(userLine)
            }
        },
    )
}

@Composable
private fun SingBoxPage(
    s: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(Res.string.title_singbox_settings),
        subtitle = stringResource(Res.string.summary_singbox_settings),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        SettingsGroup(title = stringResource(Res.string.singbox_section_servers)) {
            SettingsSwitchRow(
                title = stringResource(Res.string.title_singbox_mux),
                summary = stringResource(Res.string.summary_singbox_mux),
                checked = s.singBoxMuxEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(singBoxMuxEnabled = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = stringResource(Res.string.title_singbox_mux_protocol),
                options = SingBoxMuxProtocol.entries.map { it to it.value },
                selected = s.singBoxMuxProtocol,
                enabled = s.singBoxMuxEnabled,
                onSelected = { v -> onUpdate { it.copy(singBoxMuxProtocol = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_singbox_mux_connections),
                value = s.singBoxMuxMaxConnections.toString(),
                enabled = s.singBoxMuxEnabled,
                numeric = true,
                onValueChanged = { v ->
                    v.toIntOrNull()?.coerceIn(0, 64)?.let { n -> onUpdate { it.copy(singBoxMuxMaxConnections = n) } }
                },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_singbox_mux_padding),
                summary = stringResource(Res.string.summary_singbox_mux_padding),
                checked = s.singBoxMuxPadding,
                enabled = s.singBoxMuxEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(singBoxMuxPadding = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_singbox_brutal),
                summary = stringResource(Res.string.summary_singbox_brutal),
                checked = s.singBoxBrutalEnabled,
                enabled = s.singBoxMuxEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(singBoxBrutalEnabled = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_singbox_brutal_up),
                value = s.singBoxBrutalUpMbps.toString(),
                enabled = s.singBoxMuxEnabled && s.singBoxBrutalEnabled,
                numeric = true,
                onValueChanged = { v ->
                    v.toIntOrNull()?.coerceIn(1, 10_000)?.let { n -> onUpdate { it.copy(singBoxBrutalUpMbps = n) } }
                },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_singbox_brutal_down),
                value = s.singBoxBrutalDownMbps.toString(),
                enabled = s.singBoxMuxEnabled && s.singBoxBrutalEnabled,
                numeric = true,
                onValueChanged = { v ->
                    v.toIntOrNull()?.coerceIn(1, 10_000)?.let { n -> onUpdate { it.copy(singBoxBrutalDownMbps = n) } }
                },
            )
        }

        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(Res.string.title_singbox_tls_fragment),
                summary = stringResource(Res.string.summary_singbox_tls_fragment),
                checked = s.singBoxTlsFragment,
                onCheckedChange = { v -> onUpdate { it.copy(singBoxTlsFragment = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_singbox_record_fragment),
                summary = stringResource(Res.string.summary_singbox_record_fragment),
                checked = s.singBoxTlsRecordFragment,
                onCheckedChange = { v -> onUpdate { it.copy(singBoxTlsRecordFragment = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = stringResource(Res.string.title_singbox_utls),
                options = listOf("" to stringResource(Res.string.singbox_utls_off)) +
                    SingBoxTuning.FINGERPRINTS.map { it to it },
                selected = s.singBoxUtlsFingerprint,
                onSelected = { v -> onUpdate { it.copy(singBoxUtlsFingerprint = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_singbox_uot),
                summary = stringResource(Res.string.summary_singbox_uot),
                checked = s.singBoxUdpOverTcp,
                onCheckedChange = { v -> onUpdate { it.copy(singBoxUdpOverTcp = v) } },
            )
        }

        SettingsGroup(title = stringResource(Res.string.singbox_section_configs)) {
            SettingsListRow(
                title = stringResource(Res.string.title_singbox_stack),
                options = SingBoxStack.entries.map {
                    it to if (it == SingBoxStack.Auto) stringResource(Res.string.singbox_stack_auto) else it.value
                },
                selected = s.singBoxTunStack,
                onSelected = { v -> onUpdate { it.copy(singBoxTunStack = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_singbox_strict_route),
                summary = stringResource(Res.string.summary_singbox_strict_route),
                checked = s.singBoxStrictRoute,
                onCheckedChange = { v -> onUpdate { it.copy(singBoxStrictRoute = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_singbox_nat),
                summary = stringResource(Res.string.summary_singbox_nat),
                checked = s.singBoxEndpointIndependentNat,
                onCheckedChange = { v -> onUpdate { it.copy(singBoxEndpointIndependentNat = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_singbox_cache),
                summary = stringResource(Res.string.summary_singbox_cache),
                checked = s.singBoxStoreCache,
                onCheckedChange = { v -> onUpdate { it.copy(singBoxStoreCache = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_singbox_ntp),
                summary = stringResource(Res.string.summary_singbox_ntp),
                checked = s.singBoxNtpEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(singBoxNtpEnabled = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_singbox_ntp_server),
                value = s.singBoxNtpServer,
                enabled = s.singBoxNtpEnabled,
                onValueChanged = { v -> onUpdate { it.copy(singBoxNtpServer = v.trim()) } },
            )
        }
    }
}

@Composable
private fun MuxPage(
    s: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(Res.string.title_mux_settings),
        subtitle = stringResource(Res.string.summary_mux_settings),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_mux_enabled),
                summary = stringResource(Res.string.summary_pref_mux_enabled),
                checked = s.muxEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(muxEnabled = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_mux_concurrency),
                value = s.muxConcurrency.toString(),
                enabled = s.muxEnabled,
                numeric = true,
                placeholder = stringResource(Res.string.summary_pref_mux_concurrency),
                onValueChanged = { v ->
                    v.toIntOrNull()?.coerceIn(-1, 1024)?.let { n ->
                        onUpdate { it.copy(muxConcurrency = n) }
                    }
                },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_mux_xudp_concurrency),
                value = s.muxXudpConcurrency.toString(),
                enabled = s.muxEnabled,
                numeric = true,
                onValueChanged = { v ->
                    v.toIntOrNull()?.coerceIn(-1, 1024)?.let { n ->
                        onUpdate { it.copy(muxXudpConcurrency = n) }
                    }
                },
            )
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = stringResource(Res.string.title_pref_mux_xudp_quic),
                options = XudpQuic.entries.map { it to it.value },
                selected = s.muxXudpQuic,
                enabled = s.muxEnabled && s.muxXudpConcurrency >= 0,
                onSelected = { v -> onUpdate { it.copy(muxXudpQuic = v) } },
            )
        }
    }
}

@Composable
private fun FragmentPage(
    s: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(Res.string.title_fragment_settings),
        subtitle = stringResource(Res.string.summary_fragment_settings),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_fragment_enabled),
                checked = s.fragmentEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(fragmentEnabled = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = stringResource(Res.string.title_pref_fragment_packets),
                options = FragmentPackets.entries.map { it to it.value },
                selected = s.fragmentPackets,
                enabled = s.fragmentEnabled,
                onSelected = { v -> onUpdate { it.copy(fragmentPackets = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_fragment_length),
                value = s.fragmentLength,
                enabled = s.fragmentEnabled,
                placeholder = stringResource(Res.string.summary_pref_fragment_range),
                onValueChanged = { v -> onUpdate { it.copy(fragmentLength = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_fragment_interval),
                value = s.fragmentInterval,
                enabled = s.fragmentEnabled,
                placeholder = stringResource(Res.string.summary_pref_fragment_range),
                onValueChanged = { v -> onUpdate { it.copy(fragmentInterval = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_fragment_maxsplit),
                value = s.fragmentMaxSplit.toString(),
                enabled = s.fragmentEnabled,
                numeric = true,
                onValueChanged = { v ->
                    v.toIntOrNull()?.takeIf { it > 0 }?.let { n ->
                        onUpdate { it.copy(fragmentMaxSplit = n) }
                    }
                },
            )
        }
    }
}

@Composable
private fun ObservatoryPage(
    s: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    val seconds = stringResource(Res.string.auto_seconds, "%s")
    val minutes = stringResource(Res.string.auto_minutes, "%s")
    SettingsPageScaffold(
        title = stringResource(Res.string.title_observatory_settings),
        subtitle = stringResource(Res.string.summary_observatory_settings),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        SettingsGroup {
            SettingsInfoRow(
                stringResource(Res.string.auto_how_title),
                stringResource(Res.string.auto_how_body),
            )
        }
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(Res.string.auto_pref_retry),
                summary = stringResource(Res.string.auto_pref_retry_sum),
                checked = s.autoSelectRetry,
                onCheckedChange = { v -> onUpdate { it.copy(autoSelectRetry = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = stringResource(Res.string.auto_pref_margin),
                options = listOf(15, 30, 50).map { it to "$it%" },
                selected = s.autoSelectSwitchMargin,
                onSelected = { v -> onUpdate { it.copy(autoSelectSwitchMargin = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = stringResource(Res.string.auto_pref_check),
                options = listOf(15, 30, 60, 120).map { it to seconds.replace("%s", it.toString()) },
                selected = s.autoSelectCheckSeconds,
                onSelected = { v -> onUpdate { it.copy(autoSelectCheckSeconds = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsListRow(
                title = stringResource(Res.string.auto_pref_sweep),
                options = listOf(5, 10, 20, 30).map { it to minutes.replace("%s", it.toString()) },
                selected = s.autoSelectSweepMinutes,
                onSelected = { v -> onUpdate { it.copy(autoSelectSweepMinutes = v) } },
            )
        }
    }
}

@Composable
private fun AdvancedPage(
    s: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(Res.string.title_advanced),
        subtitle = stringResource(Res.string.summary_advanced),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_is_booted),
                summary = stringResource(Res.string.summary_pref_is_booted),
                checked = s.autoConnectOnBoot,
                onCheckedChange = { v -> onUpdate { it.copy(autoConnectOnBoot = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_delay_test_url),
                value = s.delayTestUrl,
                onValueChanged = { v -> onUpdate { it.copy(delayTestUrl = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_real_ping_concurrency),
                value = s.realPingConcurrency.toString(),
                numeric = true,
                onValueChanged = { v ->
                    v.toIntOrNull()?.coerceIn(1, 128)?.let { n ->
                        onUpdate { it.copy(realPingConcurrency = n) }
                    }
                },
            )
            SettingsDivider(hasIcon = false)
            SettingsEditRow(
                title = stringResource(Res.string.title_pref_ip_api_url),
                value = s.ipApiUrl,
                onValueChanged = { v -> onUpdate { it.copy(ipApiUrl = v) } },
            )
        }
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_auto_update_subs),
                summary = stringResource(Res.string.settings_auto_update_subs_sub),
                checked = s.autoUpdateSubscriptions,
                onCheckedChange = { v -> onUpdate { it.copy(autoUpdateSubscriptions = v) } },
            )
            SettingsDivider(hasIcon = false)
            val hourOptions = (SUB_INTERVAL_PRESETS + s.subscriptionUpdateIntervalHours.coerceIn(1, 168))
                .distinct().sorted()
            SettingsListRow(
                title = stringResource(Res.string.settings_sub_interval),
                options = hourOptions.map { h -> h to subIntervalLabel(h) },
                selected = s.subscriptionUpdateIntervalHours.coerceIn(1, 168),
                enabled = s.autoUpdateSubscriptions,
                onSelected = { h -> onUpdate { it.copy(subscriptionUpdateIntervalHours = h) } },
            )
            if (s.autoUpdateSubscriptions) {
                SettingsDivider(hasIcon = false)
                SettingsInfoRow(
                    title = stringResource(Res.string.settings_sub_interval_provider_title),
                    body = stringResource(Res.string.settings_sub_interval_provider_body),
                )
            }
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_auto_test_after_update),
                checked = s.autoTestAfterUpdate,
                onCheckedChange = { v -> onUpdate { it.copy(autoTestAfterUpdate = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_auto_remove_invalid),
                checked = s.autoRemoveInvalidAfterTest,
                onCheckedChange = { v -> onUpdate { it.copy(autoRemoveInvalidAfterTest = v) } },
            )
            SettingsDivider(hasIcon = false)
            SettingsSwitchRow(
                title = stringResource(Res.string.title_pref_auto_sort_after_test),
                checked = s.autoSortAfterTest,
                onCheckedChange = { v -> onUpdate { it.copy(autoSortAfterTest = v) } },
            )
        }
    }
}

private val SUB_INTERVAL_PRESETS = listOf(1, 3, 6, 12, 24, 48, 72, 168)

@Composable
private fun subIntervalLabel(hours: Int): String = when {
    hours == 1 -> stringResource(Res.string.settings_sub_interval_hour)
    hours == 24 -> stringResource(Res.string.settings_sub_interval_day)
    hours == 168 -> stringResource(Res.string.settings_sub_interval_week)
    hours % 24 == 0 -> stringResource(Res.string.settings_sub_interval_days, hours / 24)
    else -> stringResource(Res.string.settings_sub_interval_hours, hours)
}

@Composable
private fun ModePage(
    s: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(Res.string.title_mode_settings),
        subtitle = stringResource(Res.string.summary_mode_settings),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        val desktop = LocalPlatform.current.supportsSystemProxy
        val tun = LocalPlatform.current.supportsTun
        SettingsGroup {
            SettingsListRow(
                title = stringResource(Res.string.title_mode),
                options = if (desktop) {
                    listOfNotNull(
                        RunMode.SystemProxy to stringResource(Res.string.mode_system_proxy),
                        RunMode.ProxyOnly to stringResource(Res.string.mode_socks),
                        (RunMode.Vpn to stringResource(Res.string.mode_tun)).takeIf { tun },
                    )
                } else {
                    listOf(RunMode.Vpn, RunMode.ProxyOnly).map { it to it.value }
                },
                selected = if (desktop && !tun && s.runMode == RunMode.Vpn) RunMode.SystemProxy else s.runMode,
                onSelected = { v -> onUpdate { it.copy(runMode = v) } },
            )
            SettingsDivider(hasIcon = false)
            if (desktop) {
                SettingsInfoRow(
                    title = stringResource(Res.string.mode_system_proxy),
                    body = stringResource(Res.string.mode_system_proxy_body),
                )
                SettingsDivider(hasIcon = false)
                SettingsInfoRow(
                    title = stringResource(Res.string.mode_socks),
                    body = stringResource(Res.string.mode_socks_body),
                )
                SettingsDivider(hasIcon = false)
                SettingsInfoRow(
                    title = stringResource(Res.string.mode_tun),
                    body = stringResource(Res.string.mode_tun_body),
                )
            } else {
                SettingsInfoRow(
                    title = stringResource(Res.string.mode_vpn_explained),
                    body = stringResource(Res.string.mode_vpn_explained_body),
                )
                SettingsDivider(hasIcon = false)
                SettingsInfoRow(
                    title = stringResource(Res.string.mode_proxy_explained),
                    body = stringResource(Res.string.mode_proxy_explained_body),
                )
            }
        }
    }
}
