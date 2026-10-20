package dev.valid7996.narcicgeteway.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import kotlinx.coroutines.launch
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import dev.valid7996.narcicgeteway.core.VpnManager
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.core.LogBus
import dev.valid7996.narcicgeteway.domain.model.ConnectionState
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.ic_tray_connected
import dev.valid7996.narcicgeteway.shared.resources.ic_tray_connecting
import dev.valid7996.narcicgeteway.shared.resources.ic_tray_disconnected
import dev.valid7996.narcicgeteway.shared.resources.ic_nrg_mark
import dev.valid7996.narcicgeteway.domain.config.LocalProxy
import dev.valid7996.narcicgeteway.domain.model.RenderingMode
import dev.valid7996.narcicgeteway.domain.model.RunMode
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.painterResource
import dev.valid7996.narcicgeteway.desktop.core.Os
import dev.valid7996.narcicgeteway.desktop.core.TunMode
import dev.valid7996.narcicgeteway.desktop.platform.AdminPassword
import dev.valid7996.narcicgeteway.desktop.platform.DesktopKeyValueStore
import dev.valid7996.narcicgeteway.desktop.platform.DesktopProbe
import dev.valid7996.narcicgeteway.desktop.platform.DesktopPlatform
import dev.valid7996.narcicgeteway.desktop.platform.DesktopSettings
import dev.valid7996.narcicgeteway.desktop.platform.DesktopVpn
import dev.valid7996.narcicgeteway.domain.model.ThemeMode
import dev.valid7996.narcicgeteway.platform.AppInfo
import dev.valid7996.narcicgeteway.ui.MainScaffold
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import dev.valid7996.narcicgeteway.ui.theme.LocalMotionBudget
import dev.valid7996.narcicgeteway.ui.theme.MotionBudget
import dev.valid7996.narcicgeteway.ui.theme.NrgSecureTheme
import androidx.compose.ui.platform.LocalWindowInfo
import java.awt.GraphicsEnvironment

private val configRepository = ConfigRepository(DesktopKeyValueStore("configs"))
private val desktopSettings = DesktopSettings(DesktopKeyValueStore("settings"))

private val WIN_W = 420.dp
private val WIN_H = 860.dp
private val WIN_MIN_H = 480.dp
private val SCREEN_MARGIN = 16.dp

private fun fittedWindowHeight(): Dp {
    val usable = runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds.height }
        .getOrNull()?.takeIf { it > 0 } ?: return WIN_H
    return minOf(WIN_H, maxOf(WIN_MIN_H, usable.dp - SCREEN_MARGIN))
}

private const val TRAY_SERVERS = 30

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--version") {
        println("Narcic Getway $BUILD_VERSION")
        return
    }
    AppInfo.versionName = BUILD_VERSION
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { DesktopVpn.shutdown() } })
    configRepository.ensureDefaultSubscriptions(autoFetch = true)

    val rendering = DesktopRendering.apply(
        runCatching { desktopSettings.settings.value.renderingMode }.getOrDefault(RenderingMode.Auto),
        args,
    )
    LogBus.append(
        "I/Desktop rendering: ${if (rendering.software) "software" else "graphics card"} " +
            "(${rendering.source.name.lowercase()}, automatic would pick ${rendering.automatic.name})",
    )

    runCatching {
        desktopSettings.settings.value.language.tag?.let {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag(it))
        }
    }

    DesktopVpn.settingsProvider = { desktopSettings.settings.value }

    DesktopVpn.ruleOutboundsProvider = { configRepository.resolveRuleOutbounds(it) }
    DesktopVpn.chainConfigProvider = { profile, options ->
        runCatching { configRepository.buildChainConfig(profile, options) }.getOrNull()
    }

    dev.valid7996.narcicgeteway.core.CoreProbe.measureDelay = { url -> DesktopProbe.measureDelay(url) }

    dev.valid7996.narcicgeteway.core.AutoSelect.readStatus = {
        dev.valid7996.narcicgeteway.desktop.platform.DesktopStats.latestAutoSelect
    }

    VpnManager.deviceVpnProbe = { dev.valid7996.narcicgeteway.desktop.platform.DesktopVpnDetector.anyVpn() }
    dev.valid7996.narcicgeteway.core.CoreProbe.measureOutboundDelay = { cfg, url ->
        DesktopProbe.measureOutboundDelay(cfg, url)
    }

    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
        while (true) {
            val s = desktopSettings.settings.value
            if (s.autoUpdateSubscriptions) {
                runCatching { configRepository.updateDueSubscriptions(s.subscriptionUpdateIntervalHours) }
            }
            kotlinx.coroutines.delay(30 * 60 * 1000L)
        }
    }
    application {
        setSingletonImageLoaderFactory { ctx ->
            ImageLoader.Builder(ctx).components { add(SvgDecoder.Factory()) }.build()
        }

        val nativeTrayHost = remember {
            Os.current == Os.LINUX && LinuxDesktop.trayHostAvailable() && LinuxDesktop.nativeTrayLoads()
        }
        var nativeTrayFailed by remember { mutableStateOf(false) }
        val nativeTray = nativeTrayHost && !nativeTrayFailed
        val trayAvailable = remember(nativeTray) {
            nativeTray || java.awt.SystemTray.isSupported() && runCatching {
                val st = java.awt.SystemTray.getSystemTray()
                val img = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val probe = java.awt.TrayIcon(img)
                st.add(probe); st.remove(probe); true
            }.getOrDefault(false)
        }
        var windowVisible by remember { mutableStateOf(true) }
        var raiseWindow by remember { mutableStateOf(0) }
        val status by VpnManager.status.collectAsState()
        val connecting = status.state == ConnectionState.Connecting || status.state == ConnectionState.Disconnecting
        val connected = status.state == ConnectionState.Connected
        val trayScope = rememberCoroutineScope()

        fun reconnectIfRunning() {
            val state = VpnManager.status.value.state
            if (state != ConnectionState.Connected && state != ConnectionState.Connecting) return
            trayScope.launch {
                DesktopVpn.stop()
                kotlinx.coroutines.withTimeoutOrNull(15_000) {
                    VpnManager.status.first { it.state == ConnectionState.Idle }
                } ?: return@launch
                DesktopVpn.toggle(configRepository)
            }
        }

        if (trayAvailable) {
            val trayState = rememberTrayState()
            val profiles by configRepository.profiles.collectAsState()
            val activeId by configRepository.activeId.collectAsState()
            val settings by desktopSettings.settings.collectAsState()
            var previous by remember { mutableStateOf(status.state) }
            LaunchedEffect(status.state) {
                val was = previous
                previous = status.state
                val notice = when {
                    status.state == ConnectionState.Connected && was != ConnectionState.Connected ->
                        Notification("Narcic Getway", "Connected · ${status.serverName ?: "tunnel up"}", Notification.Type.Info)
                    status.state == ConnectionState.Error ->
                        Notification(
                            "Narcic Getway",
                            "Could not connect" + (status.error?.takeIf { it.isNotBlank() }?.let { ": ${it.take(140)}" } ?: ""),
                            Notification.Type.Error,
                        )
                    status.state == ConnectionState.Idle && was == ConnectionState.Disconnecting ->
                        Notification("Narcic Getway", "Disconnected", Notification.Type.None)
                    else -> null
                }
                notice?.let {
                    if (nativeTray) LinuxDesktop.notify(it.title, it.message) else trayState.sendNotification(it)
                }
            }
            val statusLine = when {
                connected -> "Connected · ${status.serverName ?: ""}".trimEnd(' ', '·')
                status.state == ConnectionState.Connecting -> "Connecting…"
                status.state == ConnectionState.Disconnecting -> "Disconnecting…"
                status.state == ConnectionState.Error -> "Connection failed"
                else -> "Not connected"
            }
            val tip = buildString {
                append("Narcic Getway · ").append(statusLine)
                if (connected) {
                    append("\n↓ ").append(humanBps(status.downloadBps)).append("   ↑ ").append(humanBps(status.uploadBps))
                }
            }
            val active = profiles.firstOrNull { it.id == activeId }
            val shown = (listOfNotNull(active) + profiles.filter { it.id != activeId }).take(TRAY_SERVERS)
            val proxyPort = VpnManager.activeSocksPort ?: LocalProxy.SOCKS_PORT
            val entries = buildList {
                add(TrayEntry.Action("Narcic Getway ${AppInfo.versionName}", enabled = false))
                add(TrayEntry.Action(statusLine, enabled = false))
                if (connected) {
                    add(TrayEntry.Action("↓ ${humanBps(status.downloadBps)}    ↑ ${humanBps(status.uploadBps)}", enabled = false))
                }
                add(TrayEntry.Separator)
                add(
                    TrayEntry.Action(
                        if (connected || connecting) "Disconnect" else "Connect",
                        enabled = status.state != ConnectionState.Disconnecting,
                    ) { DesktopVpn.toggle(configRepository) },
                )
                if (profiles.isNotEmpty()) {
                    add(
                        TrayEntry.Sub(
                            "Server",
                            shown.map { profile ->
                                TrayEntry.Check(profile.name.take(48), checked = profile.id == activeId) {
                                    if (profile.id != activeId) {
                                        configRepository.setActive(profile.id)
                                        reconnectIfRunning()
                                    }
                                }
                            } + if (profiles.size > shown.size) {
                                listOf(
                                    TrayEntry.Separator,
                                    TrayEntry.Action("All ${profiles.size} servers…") { windowVisible = true; raiseWindow++ },
                                )
                            } else {
                                emptyList()
                            },
                        ),
                    )
                }
                add(
                    TrayEntry.Sub(
                        "Mode",
                        listOfNotNull(
                            RunMode.SystemProxy to "System proxy",
                            RunMode.ProxyOnly to "SOCKS only",
                            (RunMode.Vpn to "TUN · all traffic").takeIf { TunMode.supported() },
                        ).map { (mode, label) ->
                            TrayEntry.Check(label, checked = settings.runMode == mode) {
                                if (settings.runMode != mode) {
                                    desktopSettings.update { it.copy(runMode = mode) }
                                    reconnectIfRunning()
                                }
                            }
                        },
                    ),
                )
                add(TrayEntry.Action("Copy proxy address · 127.0.0.1:$proxyPort") {
                    DesktopPlatform.copyToClipboard("127.0.0.1:$proxyPort")
                })
                add(TrayEntry.Separator)
                add(TrayEntry.Action(if (windowVisible) "Hide window" else "Show window") {
                    windowVisible = !windowVisible
                    if (windowVisible) raiseWindow++
                })
                add(TrayEntry.Action("Quit Narcic Getway") { DesktopVpn.shutdown(); exitApplication() })
            }
            val trayIconKey = when {
                connected -> "connected"
                connecting -> "connecting"
                else -> "disconnected"
            }
            val trayIcon = painterResource(
                when (trayIconKey) {
                    "connected" -> Res.drawable.ic_tray_connected
                    "connecting" -> Res.drawable.ic_tray_connecting
                    else -> Res.drawable.ic_tray_disconnected
                },
            )
            if (nativeTray) {
                LinuxNativeTray(
                    iconKey = trayIconKey,
                    icon = trayIcon,
                    tooltip = tip,
                    onOpen = { windowVisible = true; raiseWindow++ },
                    onFailure = { nativeTrayFailed = true },
                    entries = entries,
                )
            } else {
                Tray(
                    state = trayState,
                    icon = trayIcon,
                    tooltip = tip,
                    onAction = { windowVisible = true; raiseWindow++ },
                    menu = { awtEntries(entries) },
                )
            }
        }

        val windowState = rememberWindowState(width = WIN_W, height = fittedWindowHeight(), position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = { if (trayAvailable) windowVisible = false else { DesktopVpn.shutdown(); exitApplication() } },
            visible = windowVisible,
            title = "Narcic Getway",
            icon = painterResource(Res.drawable.ic_nrg_mark),
            resizable = false,
            state = windowState,
        ) {
            LaunchedEffect(raiseWindow) {
                if (raiseWindow > 0) {
                    window.isMinimized = false
                    window.toFront()
                    window.requestFocus()
                }
            }
            val settings by desktopSettings.settings.collectAsState()
            val dark = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            val langTag = settings.language.tag ?: "en"

            ProvideAppLanguage(langTag, dark) {
                NrgSecureTheme(
                    darkTheme = dark,
                    dynamicColor = false,
                    accentColor = settings.accentColor,
                    amoledBlack = settings.amoledBlack,
                    languageTag = langTag,
                    fontScale = settings.uiFontScale.scale,
                ) {
                    val focused = LocalWindowInfo.current.isWindowFocused
                    val motion = if (windowVisible && !windowState.isMinimized && focused) {
                        MotionBudget.Throttled
                    } else {
                        MotionBudget.Paused
                    }
                    CompositionLocalProvider(LocalPlatform provides DesktopPlatform, LocalMotionBudget provides motion) {
                        MainScaffold(
                            settings = settings,
                            configRepository = configRepository,
                            onToggleConnection = { DesktopVpn.toggle(configRepository) },
                            onActiveServerChanged = { reconnectIfRunning() },
                            onUpdateSettings = { transform -> desktopSettings.update(transform) },
                            onLanguage = { lang -> desktopSettings.update { it.copy(language = lang) } },
                            appVersion = BUILD_VERSION,
                        )
                        val passwordRequest by AdminPassword.request.collectAsState()
                        passwordRequest?.let { request ->
                            LaunchedEffect(request) {
                                windowVisible = true
                                raiseWindow++
                            }
                            AdminPasswordDialog(request.retry) { AdminPassword.answer(request, it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProvideAppLanguage(langTag: String, dark: Boolean, content: @Composable () -> Unit) {
    remember(langTag) {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(langTag)); langTag
    }
    content()
}

private fun humanBps(bps: Long): String {
    if (bps <= 0) return "0 B/s"
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var v = bps.toDouble(); var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return (if (v >= 100 || i == 0) "%.0f".format(v) else "%.1f".format(v)) + " " + units[i]
}
