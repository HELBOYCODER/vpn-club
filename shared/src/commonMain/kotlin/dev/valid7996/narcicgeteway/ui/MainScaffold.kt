package dev.valid7996.narcicgeteway.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.auto_title_for
import dev.valid7996.narcicgeteway.domain.model.AppLanguage
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.ui.servers.ServersScreen
import dev.valid7996.narcicgeteway.ui.theme.toPersonalization
import dev.valid7996.narcicgeteway.ui.connection.ConnectionViewModel
import dev.valid7996.narcicgeteway.ui.easteregg.SpaceScreen
import dev.valid7996.narcicgeteway.ui.home.HomeScreen
import dev.valid7996.narcicgeteway.ui.motion.transitionDecoration
import dev.valid7996.narcicgeteway.ui.navigation.TopDestination
import dev.valid7996.narcicgeteway.ui.onboarding.OnboardingFlow
import dev.valid7996.narcicgeteway.ui.platform.BackHandler
import dev.valid7996.narcicgeteway.ui.settings.SettingsScreen
import dev.valid7996.narcicgeteway.ui.update.NudgeHost
import org.jetbrains.compose.resources.stringResource

@Composable
fun MainScaffold(
    settings: AppSettings,
    configRepository: ConfigRepository,
    onToggleConnection: () -> Unit,

    onActiveServerChanged: () -> Unit = {},
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onLanguage: (AppLanguage) -> Unit,

    aiBridge: dev.valid7996.narcicgeteway.domain.ai.AiAppBridge? = null,
    appVersion: String = "",
) {
    val platform = dev.valid7996.narcicgeteway.ui.platform.LocalPlatform.current
    var current by rememberSaveable { mutableStateOf(TopDestination.Home) }

    var spaceUnlocked by rememberSaveable { mutableStateOf(false) }

    val connectionVm: ConnectionViewModel = viewModel { ConnectionViewModel() }

    val profiles by configRepository.profiles.collectAsStateWithLifecycle()
    val subscriptions by configRepository.subscriptions.collectAsStateWithLifecycle()
    val activeId by configRepository.activeId.collectAsStateWithLifecycle()

    val activeProfile = remember(profiles, subscriptions, activeId) {
        activeId?.let { configRepository.profile(it) }
    }
    val autoSession by dev.valid7996.narcicgeteway.core.AutoSelect.session.collectAsStateWithLifecycle()

    val lockedNote = activeProfile?.takeIf { it.isLocked }?.note

    var activeCountryCode by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(activeProfile) {
        activeCountryCode = null
        val profile = activeProfile?.takeIf { !it.isLocked && !it.isAutoSelect } ?: return@LaunchedEffect
        val link = (profile.source as? dev.valid7996.narcicgeteway.domain.config.ProfileSource.Link)?.link
        val parsed = link?.let {
            runCatching { dev.valid7996.narcicgeteway.domain.config.ConfigParser.parse(it) }.getOrNull()
        }
        val host = parsed?.address?.takeIf { it.isNotBlank() }
            ?: profile.address.takeIf { it.isNotBlank() }
        if (host != null) {
            activeCountryCode = dev.valid7996.narcicgeteway.data.net.GeoLookup.countryOf(host)
        }
    }

    val connectionState by connectionVm.ui.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(connectionState.state, activeProfile?.id, activeProfile?.expiresAt) {
        if (!connectionState.state.isActive) return@LaunchedEffect
        val profile = activeProfile ?: return@LaunchedEffect
        if (!profile.isLocked) return@LaunchedEffect
        val exp = profile.expiresAt ?: return@LaunchedEffect
        val remaining = exp - dev.valid7996.narcicgeteway.platform.currentTimeMillis()
        if (remaining <= 0) {
            onToggleConnection()
        } else {
            kotlinx.coroutines.delay(remaining)
            onToggleConnection()
        }
    }

    val mtuHint = remember(activeProfile) {
        val link = (activeProfile?.source as? dev.valid7996.narcicgeteway.domain.config.ProfileSource.Link)?.link
        val parsed = link?.let {
            runCatching { dev.valid7996.narcicgeteway.domain.config.ConfigParser.parse(it) }.getOrNull()
        }
        val host = parsed?.address?.takeIf { it.isNotBlank() } ?: activeProfile?.address.orEmpty()
        dev.valid7996.narcicgeteway.data.net.MtuServerHint(
            host = host,
            overhead = parsed
                ?.let { dev.valid7996.narcicgeteway.data.net.MtuOverheads.ofServer(it) }
                ?: dev.valid7996.narcicgeteway.data.net.MtuOverheads.worstCase(),
        )
    }

    dev.valid7996.narcicgeteway.ui.servers.DeepLinkImportHost(
        repository = configRepository,
        onImported = {
            current = if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_SERVERS_TAB) {
                TopDestination.Servers
            } else {
                TopDestination.Home
            }
        },
    )

    NudgeHost(settings = settings, onUpdateSettings = onUpdateSettings)

    BackHandler(enabled = current != TopDestination.Home) { current = TopDestination.Home }

    if (spaceUnlocked) {
        SpaceScreen(onExit = { spaceUnlocked = false })
        return
    }

    val showOnboarding = settings.onboardingVersion == 0
    var setupDone by rememberSaveable(settings.onboardingVersion) { mutableStateOf(settings.onboardingVersion > 0) }

    if (!setupDone && showOnboarding) {
        var initialLang by remember { mutableStateOf(settings.language) }
        OnboardingFlow(
            settings = settings,
            onUpdate = onUpdateSettings,
            onLanguage = { lang ->
                onUpdateSettings { it.copy(language = lang) }
            },
            onFinish = {
                onUpdateSettings { it.copy(onboardingVersion = 1) }
                setupDone = true
                if (settings.language != initialLang) {
                    onLanguage(settings.language)
                }
            },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
    val effectiveReduceMotion = settings.reduceMotion || settings.animationMode == dev.valid7996.narcicgeteway.domain.model.AnimationMode.Off
    val motionBudget = when (settings.animationMode) {
        dev.valid7996.narcicgeteway.domain.model.AnimationMode.Off -> dev.valid7996.narcicgeteway.ui.theme.MotionBudget.Paused
        dev.valid7996.narcicgeteway.domain.model.AnimationMode.Slow -> dev.valid7996.narcicgeteway.ui.theme.MotionBudget.Throttled
        dev.valid7996.narcicgeteway.domain.model.AnimationMode.Normal -> if (settings.reduceMotion) dev.valid7996.narcicgeteway.ui.theme.MotionBudget.Paused else dev.valid7996.narcicgeteway.ui.theme.MotionBudget.Full
    }

    CompositionLocalProvider(
        dev.valid7996.narcicgeteway.ui.theme.LocalMotionBudget provides motionBudget,
    ) {
        Scaffold(
        bottomBar = {
            dev.valid7996.narcicgeteway.ui.navigation.NrgNavBar(
                style = settings.navBarStyle,
                current = current,
                onSelect = { current = it },
            )
        },
    ) { innerPadding ->
      Box(Modifier.fillMaxSize()) {
        val sign = dev.valid7996.narcicgeteway.ui.motion.layoutSign()
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                dev.valid7996.narcicgeteway.ui.motion.screenTransition(
                    style = settings.screenTransition,
                    forward = targetState.ordinal >= initialState.ordinal,
                    direction = sign,
                    reduceMotion = effectiveReduceMotion,
                ).using(SizeTransform(clip = false))
            },
            label = "top-destination",
            modifier = Modifier.fillMaxSize(),
        ) { dest ->

            val decoration = transitionDecoration(
                style = settings.screenTransition,
                forward = dest.ordinal >= current.ordinal,
                direction = sign,
                reduceMotion = effectiveReduceMotion,
            )
            Box(decoration) {
            when (dest) {
                TopDestination.Home -> HomeScreen(
                    connectionVm = connectionVm,
                    activeConfigName = activeProfile?.let { p ->
                        if (p.isAutoSelect) {
                            stringResource(
                                Res.string.auto_title_for,
                                dev.valid7996.narcicgeteway.ui.servers.autoSelectGroupLabel(p),
                            )
                        } else p.name
                    },
                    exitGeneration = autoSession?.takeIf { it.profileId == activeProfile?.id }?.exitGeneration ?: 0,
                    activeConfigDetail = activeProfile?.takeIf { it.isAutoSelect }?.let { p ->
                        autoSession?.takeIf { it.profileId == p.id }?.let { session ->
                            dev.valid7996.narcicgeteway.ui.servers.autoSelectLiveLine(session) { id ->
                                profiles.firstOrNull { it.id == id }?.name
                            }
                        }
                    },
                    lockedNote = lockedNote,
                    activeLocked = activeProfile?.isLocked == true,
                    activeExpiresAt = activeProfile?.takeIf { it.isLocked }?.expiresAt,
                    activeCountryCode = activeCountryCode,
                    reduceMotion = effectiveReduceMotion,

                    showConnectionInfo = activeProfile?.isDnsTunnel != true,
                    ipApiUrl = settings.ipApiUrl,
                    delayTestUrl = settings.delayTestUrl,

                    personalization = settings.toPersonalization(),
                    connectStyle = settings.connectButtonStyle,
                    repository = configRepository,
                    realPingConcurrency = settings.realPingConcurrency,
                    autoSortAfterTest = settings.autoSortAfterTest,
                    autoTestAfterUpdate = settings.autoTestAfterUpdate,
                    autoRemoveInvalidAfterTest = settings.autoRemoveInvalidAfterTest,
                    settings = settings,
                    onUpdateSettings = onUpdateSettings,
                    contentPadding = innerPadding,
                    onToggleConnection = onToggleConnection,

                    onBrowseConfigs = {
                        if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_SERVERS_TAB) {
                            current = TopDestination.Servers
                        }
                    },
                    onSecretUnlocked = { spaceUnlocked = true },
                )
                TopDestination.Servers -> ServersScreen(
                    repository = configRepository,
                    contentPadding = innerPadding,
                    twoColumns = settings.doubleColumnDisplay,
                    showAllGroup = settings.groupAllDisplay,
                    confirmRemove = settings.confirmRemove,
                    realPingConcurrency = settings.realPingConcurrency,
                    delayTestUrl = settings.delayTestUrl,
                    autoTestAfterUpdate = settings.autoTestAfterUpdate,
                    autoRemoveInvalidAfterTest = settings.autoRemoveInvalidAfterTest,
                    autoSortAfterTest = settings.autoSortAfterTest,
                    personalization = settings.toPersonalization(),
                    settings = settings,
                    onUpdateSettings = onUpdateSettings,
                    onServerActivated = {
                        if (settings.homeAfterSelect) current = TopDestination.Home

                        onActiveServerChanged()
                    },
                )
                TopDestination.Settings -> SettingsScreen(
                    settings = settings,
                    contentPadding = innerPadding,
                    onUpdate = onUpdateSettings,
                    onLanguage = onLanguage,
                    configRepository = configRepository,

                    serverTargets = profiles
                        .filter { it.source is dev.valid7996.narcicgeteway.domain.config.ProfileSource.Link }
                        .map { it.id to it.name },

                    mtuHint = mtuHint,
                    ai = aiBridge?.let { bridge ->
                        dev.valid7996.narcicgeteway.ui.settings.AiSection(
                            settings = settings.ai,
                            onUpdate = { next -> onUpdateSettings { it.copy(ai = next) } },
                            bridge = bridge,
                            onOpenUrl = { url -> platform.openUri(url) },

                            languageName = languageEnglishName(settings.language),
                            languageNative = languageNativeName(settings.language),
                            platform = "Android",
                            appVersion = appVersion,
                        )
                    },
                )
            }
            }
        }
      }
    }
    }
    }
}

private fun languageEnglishName(language: AppLanguage): String = when (language) {
    AppLanguage.Persian -> "Persian"
    AppLanguage.Chinese -> "Chinese"
    AppLanguage.Russian -> "Russian"
    AppLanguage.English -> "English"

    AppLanguage.System -> "the language the user writes in"
}

private fun languageNativeName(language: AppLanguage): String = when (language) {
    AppLanguage.Persian -> "فارسی"
    AppLanguage.Chinese -> "中文"
    AppLanguage.Russian -> "Русский"
    AppLanguage.English -> "English"
    AppLanguage.System -> ""
}
