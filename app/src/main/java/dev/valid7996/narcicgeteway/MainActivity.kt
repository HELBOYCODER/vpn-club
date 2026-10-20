package dev.valid7996.narcicgeteway

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.valid7996.narcicgeteway.core.AndroidVpn
import dev.valid7996.narcicgeteway.core.StartPlanner
import dev.valid7996.narcicgeteway.core.VpnManager
import dev.valid7996.narcicgeteway.core.platform.LocaleManager
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.data.settings.SettingsRepository
import dev.valid7996.narcicgeteway.domain.model.ThemeMode
import dev.valid7996.narcicgeteway.platform.AndroidPlatform
import dev.valid7996.narcicgeteway.ui.MainScaffold
import dev.valid7996.narcicgeteway.ui.platform.FilePick
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import dev.valid7996.narcicgeteway.ui.theme.NrgSecureTheme
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private val configRepository: ConfigRepository
        get() = (application as NrgSecureApp).container.configRepository

    private val settingsRepository: SettingsRepository
        get() = (application as NrgSecureApp).container.settingsRepository

    private var pendingStart: StartPlanner.Plan.Start? = null

    private val planner by lazy { StartPlanner(this) }

    private var planning = false

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val start = pendingStart
            pendingStart = null
            if (result.resultCode == RESULT_OK && start != null) {
                AndroidVpn.start(this, start.configJson, start.remark, start.socksPort, start.kind)
            } else if (result.resultCode != RESULT_OK) {
                toast(getString(R.string.vpn_permission_denied))
            }
        }

    private var pendingIkev2Remark: String? = null
    private val ikev2ConsentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val remark = pendingIkev2Remark
            pendingIkev2Remark = null
            if (result.resultCode == RESULT_OK && remark != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    dev.valid7996.narcicgeteway.core.Ikev2Controller.startProvisioned(this@MainActivity, remark)
                }
            } else {
                dev.valid7996.narcicgeteway.core.Ikev2Controller.forgetProvisionedProfile(this)
                VpnManager.onDisconnected()
                if (result.resultCode != RESULT_OK) toast(getString(R.string.vpn_permission_denied))
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var filePickPending: CompletableDeferred<FilePick?>? = null
    private val filePickLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val pending = filePickPending
            filePickPending = null
            if (uri == null) { pending?.complete(null); return@registerForActivityResult }
            val bytes = runCatching {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            pending?.complete(if (bytes != null) FilePick(name, bytes) else null)
        }

    private var qrScanPending: ((String?) -> Unit)? = null
    private val qrScanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val pending = qrScanPending
            qrScanPending = null
            pending?.invoke(
                result.data?.getStringExtra(dev.valid7996.narcicgeteway.qr.QrScanActivity.EXTRA_RESULT)
                    ?.takeIf { result.resultCode == RESULT_OK },
            )
        }

    private var imagePickPending: CompletableDeferred<ByteArray?>? = null
    private val imagePickLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val pending = imagePickPending
            imagePickPending = null
            pending?.complete(
                uri?.let {
                    runCatching { contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }
                        .getOrNull()
                },
            )
        }

    private suspend fun pickImage(): ByteArray? {
        imagePickPending?.complete(null)
        val deferred = CompletableDeferred<ByteArray?>()
        imagePickPending = deferred
        imagePickLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly,
            ),
        )
        return deferred.await()
    }

    private fun scanQr(onResult: (String?) -> Unit) {
        qrScanPending?.invoke(null)
        qrScanPending = onResult
        qrScanLauncher.launch(Intent(this, dev.valid7996.narcicgeteway.qr.QrScanActivity::class.java))
    }

    private suspend fun pickFile(): FilePick? {
        filePickPending?.complete(null)
        val deferred = CompletableDeferred<FilePick?>()
        filePickPending = deferred
        filePickLauncher.launch(arrayOf("*/*"))
        return deferred.await()
    }

    private var certInstallPending: CompletableDeferred<dev.valid7996.narcicgeteway.ui.servers.CertInstallResult>? = null
    private val certInstallLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val pending = certInstallPending
            certInstallPending = null
            pending?.complete(
                if (result.resultCode == RESULT_OK) {
                    dev.valid7996.narcicgeteway.ui.servers.CertInstallResult.Installed
                } else {
                    dev.valid7996.narcicgeteway.ui.servers.CertInstallResult.Cancelled
                }
            )
        }

    private suspend fun installCredential(
        bytes: ByteArray,
        suggestedName: String,
    ): dev.valid7996.narcicgeteway.ui.servers.CertInstallResult {
        certInstallPending?.complete(dev.valid7996.narcicgeteway.ui.servers.CertInstallResult.Cancelled)
        val deferred = CompletableDeferred<dev.valid7996.narcicgeteway.ui.servers.CertInstallResult>()
        certInstallPending = deferred
        val intent = android.security.KeyChain.createInstallIntent().apply {
            putExtra(android.security.KeyChain.EXTRA_PKCS12, bytes)
            if (suggestedName.isNotBlank()) {
                putExtra(android.security.KeyChain.EXTRA_NAME, suggestedName)
            }
        }
        return runCatching { certInstallLauncher.launch(intent); deferred.await() }
            .getOrElse {
                certInstallPending = null
                dev.valid7996.narcicgeteway.ui.servers.CertInstallResult.Unsupported
            }
    }

    private val androidPlatform by lazy { AndroidPlatform(this, ::pickFile, ::scanQr, ::pickImage) }

    override fun attachBaseContext(newBase: Context) {
        val tag = SettingsRepository.readLanguageTag(newBase)
        super.attachBaseContext(LocaleManager.wrap(newBase, tag))
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.reassert(SettingsRepository.readLanguageTag(this))
    }

    override fun onResume() {
        super.onResume()
        runCatching { dev.valid7996.narcicgeteway.core.Ikev2Controller.resync(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
        handleConnectRequest(intent)
    }

    private fun handleDeepLink(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        val data = intent.data ?: return false
        val scheme = data.scheme?.lowercase() ?: return false
        if (scheme == "content" || scheme == "file") {
            try {
                val stream = contentResolver.openInputStream(data)
                val bytes = stream?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val isNrgBinary = bytes.size >= 4 && (
                        (bytes[0] == 'N'.code.toByte() && bytes[1] == 'R'.code.toByte() && bytes[2] == 'G'.code.toByte() && bytes[3] == '1'.code.toByte()) ||
                        (bytes[0] == 'Z'.code.toByte() && bytes[1] == 'S'.code.toByte() && bytes[2] == 'X'.code.toByte() && bytes[3] == '1'.code.toByte())
                    )
                    val textToImport = if (isNrgBinary) {
                        dev.valid7996.narcicgeteway.crypto.NrgLink.encode(bytes)
                    } else {
                        bytes.decodeToString()
                    }
                    val req = dev.valid7996.narcicgeteway.domain.config.DeepLinkParser.parse(textToImport)
                        ?: dev.valid7996.narcicgeteway.domain.config.DeepLinkRequest.ConfigText(textToImport)
                    dev.valid7996.narcicgeteway.core.DeepLinkBus.request(req)
                    return true
                }
            } catch (_: Throwable) {
                // Ignore and fall through
            }
            return false
        }
        if (scheme !in dev.valid7996.narcicgeteway.domain.config.DeepLinkParser.SCHEMES) return false
        val request = dev.valid7996.narcicgeteway.domain.config.DeepLinkParser.parse(intent.dataString.orEmpty())
        if (request == null) {
            toast(getString(R.string.config_invalid))
        } else {
            dev.valid7996.narcicgeteway.core.DeepLinkBus.request(request)
        }
        return true
    }

    private fun handleConnectRequest(intent: Intent?) {
        if (intent?.action != dev.valid7996.narcicgeteway.core.VpnNotifications.ACTION_CONNECT) return

        intent.action = null
        if (VpnManager.status.value.state.isActive) return
        toggleConnection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dev.valid7996.narcicgeteway.core.Ikev2CertBridgeAndroid.install(this)
        dev.valid7996.narcicgeteway.core.CertImportAndroid.installer = ::installCredential
        dev.valid7996.narcicgeteway.core.CertImportAndroid.install()
        maybeRequestNotificationPermission()

        if (savedInstanceState == null) {
            handleDeepLink(intent)
        }
        handleConnectRequest(intent)
        val repo = (application as NrgSecureApp).container.settingsRepository
        enableEdgeToEdge()
        setContent {
            val settings by repo.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            val langTag = settings.language.tag ?: resources.configuration.locales[0].language
            NrgSecureTheme(
                darkTheme = dark,
                dynamicColor = settings.dynamicColor,
                accentColor = settings.accentColor,
                amoledBlack = settings.amoledBlack,
                themeProfileId = settings.themeProfileId,
                languageTag = langTag,
                fontScale = settings.uiFontScale.scale,
            ) {
                val aetherActions = androidx.compose.runtime.remember { dev.valid7996.narcicgeteway.ui.AetherActionsAndroid(applicationContext) }
                CompositionLocalProvider(
                    LocalPlatform provides androidPlatform,
                    dev.valid7996.narcicgeteway.ui.servers.LocalAetherActions provides aetherActions,
                ) {
                    val aiBridge = androidx.compose.runtime.remember {
                        dev.valid7996.narcicgeteway.ai.AndroidAiBridge.create(
                            repository = configRepository,
                            settings = repo,
                            toggleConnection = ::toggleConnection,
                            mtuHint = {
                                val active = configRepository.activeProfile()
                                val link = (active?.source as? dev.valid7996.narcicgeteway.domain.config.ProfileSource.Link)?.link
                                val parsed = link?.let {
                                    runCatching { dev.valid7996.narcicgeteway.domain.config.ConfigParser.parse(it) }.getOrNull()
                                }
                                val host = parsed?.address?.takeIf { it.isNotBlank() }
                                    ?: active?.address.orEmpty()
                                if (host.isBlank()) {
                                    null
                                } else {
                                    dev.valid7996.narcicgeteway.data.net.MtuServerHint(
                                        host = host,
                                        overhead = parsed
                                            ?.let { dev.valid7996.narcicgeteway.data.net.MtuOverheads.ofServer(it) }
                                            ?: dev.valid7996.narcicgeteway.data.net.MtuOverheads.worstCase(),
                                    )
                                }
                            },
                        )
                    }
                    dev.valid7996.narcicgeteway.skin.HellboySkinProviders {
                    MainScaffold(
                        settings = settings,
                        configRepository = configRepository,
                        onToggleConnection = ::toggleConnection,
                        onActiveServerChanged = ::switchActiveServer,
                        onUpdateSettings = repo::update,
                        onLanguage = { lang ->
                            repo.update { it.copy(language = lang) }
                            recreate()
                        },
                        aiBridge = aiBridge,
                        appVersion = dev.valid7996.narcicgeteway.BuildConfig.VERSION_NAME,
                    )
                    }

                    dev.valid7996.narcicgeteway.ui.OpenConnectAuthDialog()
                    dev.valid7996.narcicgeteway.ui.OpenConnectCertTrustDialog(
                        onRemember = { hash ->
                            configRepository.activeProfile()?.let {
                                configRepository.rememberOpenConnectCert(it.id, hash)
                            }
                        },
                    )
                    dev.valid7996.narcicgeteway.ui.OpenConnectSsoDialog()
                }
            }
        }
    }

    private val switchStopTimeoutMs = 8_000L

    private val switchSettleMs = 350L

    private fun switchActiveServer() {
        if (!VpnManager.status.value.state.isActive) return
        lifecycleScope.launch {
            toggleConnection()
            val stopped = withTimeoutOrNull(switchStopTimeoutMs) {
                VpnManager.status.first { !it.state.isActive && !it.state.isTransitioning }
                true
            } != null
            if (!stopped) {
                toast(getString(R.string.switch_server_timeout))
                return@launch
            }

            delay(switchSettleMs)
            toggleConnection()
        }
    }

    private fun toggleConnection() {
        val state = VpnManager.status.value.state
        if (state.isActive || state.isTransitioning) {
            if (dev.valid7996.narcicgeteway.core.Ikev2Controller.isActive) {
                dev.valid7996.narcicgeteway.core.Ikev2Controller.stop(this)
                return
            }
            AndroidVpn.stop(this)
            return
        }
        if (planning) return
        val profile = configRepository.activeProfile()
        if (profile == null) {
            toast(getString(R.string.select_config_first))
            return
        }
        planning = true
        lifecycleScope.launch {
            val plan = try {
                withContext(Dispatchers.IO) { planner.plan(profile) }
            } finally {
                planning = false
            }
            when (plan) {
                is StartPlanner.Plan.Ikev2 -> startIkev2(plan.remark, plan.profile)
                is StartPlanner.Plan.Failure -> toast(plan.message)
                is StartPlanner.Plan.Start -> {
                    if (plan.proxyOnly) {
                        AndroidVpn.start(this@MainActivity, plan.configJson, plan.remark, plan.socksPort, plan.kind, proxyOnly = true)
                        return@launch
                    }
                    val consent = VpnService.prepare(this@MainActivity)
                    if (consent != null) {
                        pendingStart = plan
                        vpnPermissionLauncher.launch(consent)
                    } else {
                        AndroidVpn.start(this@MainActivity, plan.configJson, plan.remark, plan.socksPort, plan.kind)
                    }
                }
            }
        }
    }

    private fun startIkev2(remark: String, ikev2: dev.valid7996.narcicgeteway.domain.config.Ikev2Profile) {
        val controller = dev.valid7996.narcicgeteway.core.Ikev2Controller
        if (!controller.isSupportedSdk) {
            toast(getString(R.string.ikev2_requires_android11))
            return
        }
        if (!controller.isSupported(this)) {
            toast(getString(R.string.ikev2_requires_ipsec))
            return
        }

        if (VpnManager.status.value.state.isActive) AndroidVpn.stop(this)
        VpnManager.onStarting(remark)

        controller.note("connect requested for $remark (server=${ikev2.server}, auth=${ikev2.effectiveAuth})")
        lifecycleScope.launch(Dispatchers.IO) {
            val consent = try {
                controller.provision(this@MainActivity, ikev2)
            } catch (e: dev.valid7996.narcicgeteway.core.Ikev2Exception) {
                controller.note("provision refused", e.cause ?: e)
                withContext(Dispatchers.Main) { VpnManager.onError(getString(e.messageRes)) }
                return@launch
            } catch (e: Exception) {
                controller.note("provision threw", e)
                withContext(Dispatchers.Main) {
                    VpnManager.onError(e.message ?: getString(R.string.ikev2_failed_generic))
                }
                return@launch
            }
            if (consent != null) {
                withContext(Dispatchers.Main) {
                    pendingIkev2Remark = remark
                    ikev2ConsentLauncher.launch(consent)
                }
            } else {
                controller.startProvisioned(this@MainActivity, remark)
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
