@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package dev.valid7996.narcicgeteway.ui.servers

import org.jetbrains.compose.resources.DrawableResource

import org.jetbrains.compose.resources.StringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.data.backup.BackupManager
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.data.net.GeoLookup
import dev.valid7996.narcicgeteway.data.net.PingCoordinator
import dev.valid7996.narcicgeteway.data.net.PingService
import dev.valid7996.narcicgeteway.core.AutoSelect
import dev.valid7996.narcicgeteway.domain.config.AutoSelectIds
import dev.valid7996.narcicgeteway.domain.config.ConfigParseException
import dev.valid7996.narcicgeteway.domain.config.ConfigParser
import dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import dev.valid7996.narcicgeteway.ui.format.formatBytes

import dev.valid7996.narcicgeteway.ui.components.MorphingBlob
import dev.valid7996.narcicgeteway.ui.components.PageHeader
import dev.valid7996.narcicgeteway.ui.theme.Personalization
import dev.valid7996.narcicgeteway.ui.theme.readableOn
import dev.valid7996.narcicgeteway.ui.theme.NrgGradients
import dev.valid7996.narcicgeteway.ui.theme.pingColor
import dev.valid7996.narcicgeteway.ui.theme.NrgLime
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.valid7996.narcicgeteway.ui.platform.mouseDragScroll
import dev.valid7996.narcicgeteway.ui.settings.IpScannerSheet
import dev.valid7996.narcicgeteway.ui.settings.TelegramProxySheet

@Composable
fun ServersScreen(
    repository: ConfigRepository,
    contentPadding: PaddingValues,
    onServerActivated: () -> Unit,
    modifier: Modifier = Modifier,
    twoColumns: Boolean = false,
    showAllGroup: Boolean = true,
    confirmRemove: Boolean = true,

    realPingConcurrency: Int = 16,
    delayTestUrl: String = "",
    autoTestAfterUpdate: Boolean = false,
    autoRemoveInvalidAfterTest: Boolean = false,
    autoSortAfterTest: Boolean = false,

    personalization: Personalization = Personalization.Default,
    settings: dev.valid7996.narcicgeteway.domain.model.AppSettings? = null,
    onUpdateSettings: (((dev.valid7996.narcicgeteway.domain.model.AppSettings) -> dev.valid7996.narcicgeteway.domain.model.AppSettings) -> Unit)? = null,
) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val lockedCannotShare = stringResource(Res.string.locked_cannot_share)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val columnsCount = (settings?.serverGridColumns ?: if (twoColumns) 2 else 1).coerceIn(1, 3)

    fun toastImportFailure(e: Throwable) {
        if (e.message?.contains("قفل شده است") == true) {
            scope.launch { platform.toast(e.message ?: "این کانفیگ اختصاصی برای دستگاه دیگری قفل شده است.") }
            return
        }
        if (e is dev.valid7996.narcicgeteway.crypto.ZsxKeyConsumedException) {
            scope.launch { platform.toast("این کلید اختصاصی قبلاً یک بار استفاده شده و باطل گردیده است (کلید یک‌بار مصرف).") }
            return
        }
        if (e is dev.valid7996.narcicgeteway.crypto.ZsxExpiredException) {
            scope.launch { platform.toast("این کانفیگ منقضی شده است و اعتبار آن پایان یافته است.") }
            return
        }
        if (e is ConfigRepository.SubscriptionNotFetchedException) {
            scope.launch {
                platform.toast(
                    if (e.savedForLater) getString(Res.string.subs_saved_unreachable, e.name)
                    else getString(Res.string.subs_failed),
                )
            }
            return
        }
        val ovpn = (e as? dev.valid7996.narcicgeteway.domain.config.OvpnConfig.UnsupportedException)?.reason
        val res = when {
            ovpn != null -> when (ovpn) {
                dev.valid7996.narcicgeteway.domain.config.OvpnConfig.Reason.NoRemote -> Res.string.import_err_ovpn_no_remote
                dev.valid7996.narcicgeteway.domain.config.OvpnConfig.Reason.Tap -> Res.string.import_err_ovpn_tap
                dev.valid7996.narcicgeteway.domain.config.OvpnConfig.Reason.ExternalFiles -> Res.string.import_err_ovpn_files
                dev.valid7996.narcicgeteway.domain.config.OvpnConfig.Reason.UnsupportedCredentials ->
                    Res.string.import_err_ovpn_creds
            }

            e is dev.valid7996.narcicgeteway.domain.config.AmneziaLink.UnsupportedException -> when (e.reason) {
                dev.valid7996.narcicgeteway.domain.config.AmneziaLink.Reason.Backup ->
                    Res.string.import_err_amnezia_backup
                dev.valid7996.narcicgeteway.domain.config.AmneziaLink.Reason.Subscription ->
                    Res.string.import_err_amnezia_subscription
                dev.valid7996.narcicgeteway.domain.config.AmneziaLink.Reason.NoRunnableContainer ->
                    Res.string.import_err_amnezia_unsupported
            }
            else -> when ((e as? ConfigParseException)?.reason) {
                ConfigParseException.Reason.UnsupportedSsCipher -> Res.string.import_err_ss_cipher
                ConfigParseException.Reason.MissingSsCipher -> Res.string.import_err_ss_no_cipher
                ConfigParseException.Reason.UnsupportedSsPlugin -> Res.string.import_err_ss_plugin
                else -> Res.string.config_invalid
            }
        }
        scope.launch { platform.toast(getString(res)) }
    }

    fun toastRes(res: StringResource, vararg args: Any) {
        scope.launch { platform.toast(getString(res, *args)) }
    }

    var pendingUnlock by remember { mutableStateOf<Pair<ByteArray, dev.valid7996.narcicgeteway.crypto.ZsxMetadata>?>(null) }

    val customJsonInvalid = stringResource(Res.string.custom_json_invalid)
    val singBoxJsonInvalid = stringResource(Res.string.singbox_json_invalid)

    var obfuscateCandidates by remember { mutableStateOf<List<String>>(emptyList()) }

    var ovpnPrompt by remember { mutableStateOf<OvpnPrompt?>(null) }

    suspend fun offerObfuscationAfter(block: suspend () -> Unit) {
        val before = repository.profiles.value.map { it.id }.toSet()
        block()
        val fresh = repository.profiles.value.map { it.id }.filterNot { it in before }
        if (fresh.isNotEmpty()) obfuscateCandidates = repository.plainWireguardAmong(fresh)
    }

    var relayKeyPrompt by remember { mutableStateOf<String?>(null) }

    fun relayFailure(text: String, err: Throwable) {
        when (err) {
            is dev.valid7996.narcicgeteway.data.config.RelayKeyRequiredException -> relayKeyPrompt = text
            is dev.valid7996.narcicgeteway.data.config.RelayAlreadyClaimedException ->
                platform.toast("این لینک قبلاً روی یک دستگاه دیگر فعال شده و دیگر قابل استفاده نیست.")
            is dev.valid7996.narcicgeteway.crypto.ZsxWrongPasswordException ->
                platform.toast("کلید اختصاصی وارد شده اشتباه است.")
            else -> platform.toast("دریافت کانفیگ از سرور لینک کوتاه ناموفق بود: ${err.message}")
        }
    }

    fun tryHandleLocked(text: String): Boolean {
        if (dev.valid7996.narcicgeteway.crypto.NrgLink.findRelay(text) != null) {
            scope.launch {
                val resolved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    repository.resolveNrgRelay(text)
                }
                resolved.fold(
                    onSuccess = { resolvedText ->
                        if (!tryHandleLocked(resolvedText)) toastRes(Res.string.config_invalid)
                    },
                    onFailure = { relayFailure(text, it) },
                )
            }
            return true
        }
        val link = LockedConfigExtractor.extractLink(text) ?: return false
        val bytes = runCatching { dev.valid7996.narcicgeteway.crypto.NrgLink.decode(link) }.getOrNull() ?: return false
        val meta = runCatching { repository.peekLocked(bytes) }.getOrNull() ?: return false
        if (meta.isExpired) {
            platform.toast("این کانفیگ منقضی شده است و اعتبار آن پایان یافته است.")
            return true
        }
        if (meta.deviceBound) {
            val autoImp = repository.importLocked(bytes, null)
            autoImp.fold(
                onSuccess = { toastRes(Res.string.servers_imported, 1) },
                onFailure = {
                    val key = LockedConfigExtractor.extractKey(text)
                    if (key != null) {
                        val keyImp = repository.importLocked(bytes, key)
                        keyImp.fold(
                            onSuccess = { toastRes(Res.string.servers_imported, 1) },
                            onFailure = {
                                platform.toast("این کانفیگ اختصاصی برای دستگاه دیگری قفل شده است و روی این دستگاه فعال نمی‌شود.")
                            },
                        )
                    } else {
                        platform.toast("این کانفیگ اختصاصی برای دستگاه دیگری قفل شده است و روی این دستگاه فعال نمی‌شود.")
                    }
                },
            )
            return true
        }
        val key = LockedConfigExtractor.extractKey(text)
        if (meta.passwordProtected) {
            if (key != null) {
                val imported = repository.importLocked(bytes, key)
                imported.fold(
                    onSuccess = { toastRes(Res.string.servers_imported, 1) },
                    onFailure = {
                        pendingUnlock = bytes to meta
                    },
                )
            } else {
                pendingUnlock = bytes to meta
            }
            return true
        }
        val imported = repository.importLocked(bytes, null)
        imported.fold(
            onSuccess = { toastRes(Res.string.servers_imported, 1) },
            onFailure = { toastImportFailure(it) },
        )
        return true
    }

    fun importFromFile() {
        scope.launch {
            val picked = platform.pickFileBytes() ?: return@launch
            val bytes = picked.bytes
            if (bytes.isEmpty()) {
                toastRes(Res.string.config_invalid)
                return@launch
            }

            // Check if it is a binary .nrg / .zsx sealed file
            val isNrgBinary = bytes.size >= 4 && (
                (bytes[0] == 'N'.code.toByte() && bytes[1] == 'R'.code.toByte() && bytes[2] == 'G'.code.toByte() && bytes[3] == '1'.code.toByte()) ||
                (bytes[0] == 'Z'.code.toByte() && bytes[1] == 'S'.code.toByte() && bytes[2] == 'X'.code.toByte() && bytes[3] == '1'.code.toByte())
            )
            if (isNrgBinary || picked.name.endsWith(".nrg", ignoreCase = true) || picked.name.endsWith(".zsx", ignoreCase = true)) {
                val link = if (isNrgBinary) {
                    dev.valid7996.narcicgeteway.crypto.NrgLink.encode(bytes)
                } else {
                    bytes.decodeToString().trim()
                }
                if (tryHandleLocked(link)) return@launch
            }

            val text = bytes.decodeToString()
            if (text.isBlank()) {
                toastRes(Res.string.config_invalid)
                return@launch
            }

            // Check if it is a JSON backup file
            val parsedBackup = BackupManager.parseBackup(text)
            if (parsedBackup.isSuccess) {
                val backup = parsedBackup.getOrNull()
                if (backup != null && (backup.profiles?.isNotEmpty() == true || backup.subscriptions?.isNotEmpty() == true)) {
                    val (restoredProfiles, restoredSubs) = repository.restoreBackupData(
                        newProfiles = backup.profiles,
                        newSubscriptions = backup.subscriptions,
                        replaceProfiles = false,
                        replaceSubscriptions = false,
                    )
                    platform.toast("نسخه پشتیبان بازیابی شد: $restoredProfiles سرور، $restoredSubs اشتراک")
                    return@launch
                }
            }

            if (tryHandleLocked(text)) return@launch

            val fileName = picked.name.substringBeforeLast('.').takeIf { it.isNotBlank() }

            val namedOvpn = picked.name.endsWith(".ovpn", ignoreCase = true)
            val info = runCatching {
                dev.valid7996.narcicgeteway.domain.config.OvpnConfig
                    .takeIf { namedOvpn || it.looksLikeOvpn(text) }?.inspect(text)
            }
            info.onFailure {
                toastImportFailure(it)

                if (namedOvpn) return@launch
            }
            val ovpn = info.getOrNull()
            if (ovpn != null && ovpn.needsCredentials && !ovpn.hasInlineCredentials) {
                ovpnPrompt = OvpnPrompt(text = text, name = fileName ?: ovpn.name)
                return@launch
            }
            if (ovpn != null) {
                repository.addOvpn(text, name = fileName ?: ovpn.name)
                    .onSuccess { platform.toast(getString(Res.string.servers_imported, 1)) }
                    .onFailure { toastImportFailure(it) }
                return@launch
            }
            withContext(kotlinx.coroutines.Dispatchers.Default) { repository.importPasted(text) }
                .onSuccess { r ->
                    platform.toast(
                        when {
                            r.subscription -> getString(Res.string.subs_imported, r.subscriptionName, r.count)
                            r.count > 0 -> getString(Res.string.servers_imported, r.count)
                            r.duplicates > 0 -> getString(Res.string.servers_already_added, r.duplicates)
                            else -> getString(Res.string.config_invalid)
                        },
                    )
                }
                .onFailure { toastImportFailure(it) }
        }
    }

    val profiles by repository.profiles.collectAsStateWithLifecycle()
    val subscriptions by repository.subscriptions.collectAsStateWithLifecycle()
    val activeId by repository.activeId.collectAsStateWithLifecycle()

    var showAddSheet by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showIpScanner by remember { mutableStateOf(false) }
    var showTelegramProxy by remember { mutableStateOf(false) }
    var showPatt by remember { mutableStateOf(false) }
    var showBackupRestore by remember { mutableStateOf(false) }
    var showDeviceCode by remember { mutableStateOf(false) }
    var lockShareTarget by remember { mutableStateOf<Pair<VpnProfile, String>?>(null) }

    var fanOpen by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var showAether by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VpnProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<VpnProfile?>(null) }
    var updating by remember { mutableStateOf(false) }
    var pingMenu by remember { mutableStateOf(false) }

    val pingProgress by PingCoordinator.progress.collectAsStateWithLifecycle()
    val testing = pingProgress != null

    var showRealDelayNote by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmDeleteSubscriptions by remember { mutableStateOf(false) }

    var confirmDeleteInvalid by remember { mutableStateOf<Int?>(null) }
    var editTarget by remember { mutableStateOf<VpnProfile?>(null) }

    var moveTarget by remember { mutableStateOf<VpnProfile?>(null) }

    var qrTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showCustomJson by remember { mutableStateOf(false) }
    var showPsiphon by remember { mutableStateOf(false) }
    var showDnsTunnel by remember { mutableStateOf(false) }
    var showMasterDns by remember { mutableStateOf(false) }
    var showOpenConnect by remember { mutableStateOf(false) }
    var showIkev2 by remember { mutableStateOf(false) }
    var showSsh by remember { mutableStateOf(false) }
    var showSniSpoof by remember { mutableStateOf(false) }
    var showSniSpoofNoRoot by remember { mutableStateOf(false) }
    var showProxyChain by remember { mutableStateOf(false) }
    var showCrossChain by remember { mutableStateOf(false) }

    val servers = profiles

    val chainCandidates = servers.filter {
        !it.isLocked && it.rawPayload() != null && (!it.isCustom || it.isServerless) && !it.isManagedTunnel && !it.isProxyChain &&
            !it.isSingBoxConfig
    }

    val spoofCandidates = servers.filter {
        !it.isLocked && it.rawPayload() != null && !it.isManagedTunnel && !it.isProxyChain &&
            !it.isCrossChain && !it.isSniSpoof
    }
    val crossCarriers = servers.filter { !it.isLocked && it.canCarryChain && !it.isCrossChain }
    val crossExits = servers.filter { !it.isLocked && it.canDialThroughProxy && !it.isCrossChain }

    val udpMismatchTemplate = stringResource(Res.string.crosschain_udp_unsupported)
    val crossPairError: (VpnProfile, VpnProfile) -> String? = { inner, outer ->
        repository.udpMismatch(inner, outer)?.let {
            udpMismatchTemplate.replace("%1\$s", it.protocol).replace("%2\$s", it.carrier)
        }
    }

    val activeGroupIndex: () -> Int = {
        val active = when {
            activeId != null && AutoSelectIds.isAuto(activeId) ->
                runCatching { AutoSelectIds.subscriptionOf(activeId!!) }.getOrNull().orEmpty()
            else -> profiles.firstOrNull { it.id == activeId }?.subscriptionId.orEmpty()
        }
        when {
            active.isEmpty() -> if (showAllGroup) 0 else 1
            else -> subscriptions.indexOfFirst { it.id == active }.takeIf { it >= 0 }?.plus(2)
                ?: if (showAllGroup) 0 else 1
        }
    }

    var groupIndex by rememberSaveable { mutableIntStateOf(activeGroupIndex()) }

    var pendingImportPayload by remember { mutableStateOf<String?>(null) }
    var rememberImportChoice by remember { mutableStateOf(false) }

    fun doExecuteImport(text: String, subId: String) {
        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            var outcome: Result<dev.valid7996.narcicgeteway.data.config.ConfigRepository.PastedImport>? = null
            offerObfuscationAfter { outcome = repository.importPasted(text, subId) }
            outcome?.onSuccess { r ->
                platform.toast(
                    when {
                        r.subscription -> getString(Res.string.subs_imported, r.subscriptionName, r.count)
                        r.count > 0 -> getString(Res.string.servers_imported, r.count)
                        r.duplicates > 0 -> getString(Res.string.servers_already_added, r.duplicates)
                        else -> getString(Res.string.config_invalid)
                    },
                )
            }?.onFailure { err ->
                if (err is dev.valid7996.narcicgeteway.crypto.ZsxPasswordRequiredException) {
                    val link = LockedConfigExtractor.extractLink(text) ?: text.trim()
                    val bytes = runCatching { dev.valid7996.narcicgeteway.crypto.NrgLink.decode(link) }.getOrNull()
                    val meta = bytes?.let { b -> runCatching { repository.peekLocked(b) }.getOrNull() }
                    if (bytes != null && meta != null) {
                        pendingUnlock = bytes to meta
                        return@onFailure
                    }
                }
                toastImportFailure(err)
            }
        }
    }

    fun requestImport(text: String) {
        if (text.isBlank()) {
            toastRes(Res.string.paste_empty)
            return
        }
        if (tryHandleLocked(text)) return
        if (repository.isSubscriptionUrl(text)) {
            doExecuteImport(text, "")
            return
        }

        val mode = settings?.importTargetMode ?: dev.valid7996.narcicgeteway.domain.model.ImportTargetMode.Ask
        when (mode) {
            dev.valid7996.narcicgeteway.domain.model.ImportTargetMode.Subscription -> {
                val subId = (if (groupIndex >= 2) subscriptions.getOrNull(groupIndex - 2) else null)?.id
                    ?: subscriptions.firstOrNull()?.id
                    ?: repository.addSubscription("سابسکرپشن شخصی", "").id
                doExecuteImport(text, subId)
            }
            dev.valid7996.narcicgeteway.domain.model.ImportTargetMode.Manual -> {
                doExecuteImport(text, "")
            }
            dev.valid7996.narcicgeteway.domain.model.ImportTargetMode.Ask -> {
                pendingImportPayload = text
            }
        }
    }

    fun importScanned(text: String) {
        requestImport(text)
    }

    fun importFromClipboard() {
        val text = platform.readClipboard()
        if (text.isNullOrBlank()) {
            toastRes(Res.string.paste_empty)
            return
        }
        requestImport(text)
    }

    androidx.compose.runtime.LaunchedEffect(activeId, subscriptions.size, showAllGroup) {
        groupIndex = activeGroupIndex()
    }

    val shown = when {
        subscriptions.isEmpty() || (showAllGroup && groupIndex == 0) -> servers
        groupIndex == 1 || (!showAllGroup && groupIndex == 0) -> servers.filter { it.subscriptionId.isEmpty() }
        else -> subscriptions.getOrNull(groupIndex - 2)?.let { sub ->
            servers.filter { it.subscriptionId == sub.id }
        } ?: servers
    }

    val headerCount = shown.size

    val autoScope: String? = when {
        subscriptions.isEmpty() || groupIndex == 0 -> null
        groupIndex == 1 -> ""
        else -> subscriptions.getOrNull(groupIndex - 2)?.id
    }
    val autoId = AutoSelectIds.of(autoScope)
    val autoMembers = remember(profiles, autoScope) { repository.autoSelectMembers(autoScope) }
    val autoSession by AutoSelect.session.collectAsStateWithLifecycle()
    val autoLive = autoSession?.takeIf { it.profileId == autoId }
    val autoLabel = when (autoScope) {
        null -> stringResource(Res.string.auto_group_all)
        "" -> stringResource(Res.string.group_manual)
        else -> subscriptions.firstOrNull { it.id == autoScope }?.name.orEmpty()
    }

    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    var moveSelectionTo by remember { mutableStateOf(false) }
    var confirmDeleteSelection by remember { mutableStateOf(false) }
    val shownIds = shown.map { it.id }

    androidx.compose.runtime.LaunchedEffect(shownIds) {
        if (selecting) selected = selected.intersect(shownIds.toSet())
    }
    var showAutoSheet by remember { mutableStateOf(false) }
    if (showAutoSheet) {
        AutoSelectSheet(
            groupLabel = autoLabel,
            members = autoMembers,
            live = autoLive,
            onDismiss = { showAutoSheet = false },
        )
    }

    if (showRealDelayNote) {
        AlertDialog(
            onDismissRequest = { showRealDelayNote = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            confirmButton = {
                TextButton(
                    onClick = { showRealDelayNote = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFF007AFF)),
                ) {
                    Text(stringResource(Res.string.action_ok), fontWeight = FontWeight.SemiBold)
                }
            },
            icon = { Icon(painterResource(Res.drawable.ic_speed), contentDescription = null, tint = Color(0xFF007AFF)) },
            title = {
                Text(
                    stringResource(Res.string.real_delay_note_title),
                    color = if (isDark) Color.White else Color.Black,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    stringResource(Res.string.real_delay_note_body),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    lineHeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontSize * 1.55f,
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    PageHeader(
                        title = stringResource(Res.string.nav_servers),
                        subtitle = if (servers.isEmpty()) null
                        else stringResource(Res.string.servers_count, headerCount),

                        singleLine = true,
                    )
                }
                Box {
                    IconButton(
                        enabled = !testing,
                        onClick = { pingMenu = true },
                        modifier = Modifier,
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_speed),
                            contentDescription = stringResource(Res.string.ping_test),
                        )
                    }
                    DropdownMenu(
                        expanded = pingMenu,
                        onDismissRequest = { pingMenu = false },
                        shape = MaterialTheme.shapes.largeIncreased,
                    ) {
                        val currentSub = if (groupIndex >= 2) subscriptions.getOrNull(groupIndex - 2) else null
                        val currentScopeName = when {
                            subscriptions.isEmpty() || (showAllGroup && groupIndex == 0) -> null
                            groupIndex == 1 || (!showAllGroup && groupIndex == 0) -> stringResource(Res.string.group_manual)
                            else -> currentSub?.name
                        }
                        val tcpPingLabel = if (currentScopeName != null) {
                            "پینگ TCP سرورهای $currentScopeName"
                        } else {
                            stringResource(Res.string.ping_tcp_all)
                        }
                        val realPingLabel = if (currentScopeName != null) {
                            "تأخیر واقعی سرورهای $currentScopeName"
                        } else {
                            stringResource(Res.string.ping_real_all)
                        }
                        if (ServersUiConfig.SHOW_TCP_PING) DropdownMenuItem(
                            text = { Text(tcpPingLabel) },
                            onClick = {
                                pingMenu = false

                                PingCoordinator.start(
                                    profiles = shown,
                                    useRealDelay = false,
                                    concurrency = realPingConcurrency,
                                    delayUrl = delayTestUrl,
                                    chainConfig = repository::chainProbeConfig,

                                    clearPings = { repository.clearPings(it, persist = false) },
                                    onResult = { id, ms, cc -> repository.setPing(id, ms, cc, persist = false) },
                                    flush = { repository.flushProfiles() },

                                    onFinished = { if (autoSortAfterTest) repository.sortByTestResults() },
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(realPingLabel) },
                            onClick = {
                                pingMenu = false
                                PingCoordinator.start(
                                    profiles = shown,
                                    useRealDelay = true,
                                    concurrency = realPingConcurrency,
                                    delayUrl = delayTestUrl,
                                    chainConfig = repository::chainProbeConfig,

                                    clearPings = { repository.clearPings(it, persist = false) },
                                    onResult = { id, ms, cc -> repository.setPing(id, ms, cc, persist = false) },
                                    flush = { repository.flushProfiles() },

                                    onFinished = { if (autoSortAfterTest) repository.sortByTestResults() },
                                )
                            },
                        )
                    }
                }

                if (testing) {
                    IconButton(onClick = { PingCoordinator.cancel() }) {
                        Icon(
                            painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.ping_stop),
                        )
                    }
                }

                IconButton(onClick = { showRealDelayNote = true }) {
                    Icon(
                        painterResource(Res.drawable.ic_info),
                        contentDescription = stringResource(Res.string.real_delay_note_title),
                    )
                }
                IconButton(
                    onClick = { showSubs = true },
                    modifier = Modifier,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_add_link),
                        contentDescription = stringResource(Res.string.subs_title),
                    )
                }
                Box {
                    IconButton(
                        onClick = { overflowOpen = true },
                        modifier = Modifier,
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_more_vert),
                            contentDescription = stringResource(Res.string.servers_actions),
                        )
                    }
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                        shape = MaterialTheme.shapes.largeIncreased,
                    ) {
                        val currentSub = if (groupIndex >= 2) subscriptions.getOrNull(groupIndex - 2) else null
                        if (currentSub != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.subs_update_this)) },
                                leadingIcon = { Icon(painterResource(Res.drawable.ic_sync), null) },
                                enabled = !updating,
                                onClick = {
                                    overflowOpen = false
                                    updating = true

                                    scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        val result = repository.updateSubscription(currentSub.id)
                                        val n = result.getOrElse { 0 }

                                        if (autoTestAfterUpdate && n > 0) {
                                            val targetServers = repository.profiles.value.filter { it.subscriptionId == currentSub.id }
                                            repository.clearPings(targetServers.map { it.id }, persist = false)
                                            PingService.measureAll(
                                                targetServers,
                                                useRealDelay = true,
                                                concurrency = realPingConcurrency,
                                                delayUrl = delayTestUrl,
                                                chainConfig = repository::chainProbeConfig,
                                            ) { id, ms, cc ->
                                                repository.setPing(id, ms, cc, persist = false)
                                            }
                                            repository.flushProfiles()
                                            if (autoRemoveInvalidAfterTest) repository.removeInvalid()
                                            if (autoSortAfterTest) repository.sortByTestResults()
                                        }
                                        updating = false
                                        if (result.isSuccess) {
                                            toastRes(Res.string.subs_updated, n)
                                        } else {
                                            toastRes(Res.string.subs_failed)
                                        }
                                    }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(if (currentSub != null) Res.string.subs_update_all else Res.string.subs_update_only)) },
                            leadingIcon = { Icon(painterResource(Res.drawable.ic_schedule), null) },
                            enabled = subscriptions.isNotEmpty() && !updating,
                            onClick = {
                                overflowOpen = false
                                updating = true

                                scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                    val n = repository.updateAllSubscriptions()

                                    if (autoTestAfterUpdate) {
                                        val all = repository.profiles.value
                                        repository.clearPings(all.map { it.id }, persist = false)
                                        PingService.measureAll(
                                            all,
                                            useRealDelay = true,
                                            concurrency = realPingConcurrency,
                                            delayUrl = delayTestUrl,
                                            chainConfig = repository::chainProbeConfig,
                                        ) { id, ms, cc ->
                                            repository.setPing(id, ms, cc, persist = false)
                                        }
                                        repository.flushProfiles()
                                        if (autoRemoveInvalidAfterTest) repository.removeInvalid()
                                        if (autoSortAfterTest) repository.sortByTestResults()
                                    }
                                    updating = false
                                    toastRes(Res.string.subs_updated, n)
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.select_multiple)) },
                            leadingIcon = { Icon(painterResource(Res.drawable.ic_check_circle), null) },
                            enabled = shown.isNotEmpty(),
                            onClick = {
                                overflowOpen = false
                                selecting = true
                                selected = emptySet()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.sort_by_results)) },
                            leadingIcon = { Icon(painterResource(Res.drawable.ic_tune), null) },
                            onClick = {
                                overflowOpen = false
                                repository.sortByTestResults()
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.del_duplicates)) },
                            leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null) },
                            onClick = {
                                overflowOpen = false
                                val n = repository.removeDuplicates()
                                toastRes(Res.string.removed_count, n)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.del_invalid)) },
                            leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null) },
                            onClick = {
                                overflowOpen = false

                                confirmDeleteInvalid = repository.invalidCount()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (currentSub != null) Res.string.del_this_subscription
                                        else Res.string.del_subscriptions
                                    )
                                )
                            },
                            leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null) },
                            enabled = if (currentSub != null) {
                                servers.any { it.subscriptionId == currentSub.id }
                            } else {
                                servers.any { it.subscriptionId.isNotBlank() }
                            },
                            onClick = {
                                overflowOpen = false
                                confirmDeleteSubscriptions = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.export_all)) },
                            leadingIcon = { Icon(painterResource(Res.drawable.ic_ios_share), null) },
                            onClick = {
                                overflowOpen = false
                                val payload = repository.exportAllPayloads()
                                if (payload.isBlank()) {
                                    toastRes(Res.string.nothing_to_export)
                                } else {
                                    platform.shareText(payload)
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.del_all)) },
                            leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null) },
                            onClick = {
                                overflowOpen = false
                                confirmDeleteAll = true
                            },
                        )
                    }
                }
            }

            pingProgress?.let { p ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(
                        stringResource(
                            if (p.real) Res.string.ping_progress_real else Res.string.ping_progress_tcp,
                            p.done,
                            p.total,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(

                        progress = { if (p.total <= 0) 0f else (p.done.toFloat() / p.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (servers.isNotEmpty() || subscriptions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (subscriptions.isNotEmpty()) {
                        val counts = remember(servers) { servers.groupingBy { it.subscriptionId }.eachCount() }

                        LaunchedEffect(showAllGroup) { if (!showAllGroup && groupIndex == 0) groupIndex = 1 }

                        val groupRow = androidx.compose.foundation.lazy.rememberLazyListState()
                        androidx.compose.foundation.lazy.LazyRow(
                            state = groupRow,
                            contentPadding = PaddingValues(end = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .mouseDragScroll(groupRow),
                        ) {
                            if (showAllGroup) {
                                item(key = "group-all") {
                                    GroupTab(
                                        label = stringResource(Res.string.group_all),
                                        count = servers.size,
                                        selected = groupIndex == 0,
                                        personalization = personalization,
                                        onClick = { groupIndex = 0 },
                                    )
                                }
                            }
                            item(key = "group-manual") {
                                GroupTab(
                                    label = stringResource(Res.string.group_manual),
                                    count = counts[""] ?: 0,
                                    selected = groupIndex == 1,
                                    personalization = personalization,
                                    onClick = { groupIndex = 1 },
                                )
                            }
                            itemsIndexed(subscriptions, key = { _, sub -> sub.id }) { index, sub ->
                                GroupTab(
                                    label = sub.name,
                                    count = counts[sub.id] ?: 0,
                                    selected = groupIndex == index + 2,
                                    personalization = personalization,
                                    onClick = { groupIndex = index + 2 },
                                )
                            }
                        }
                        if (servers.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    if (servers.isNotEmpty()) {
                        IosColumnSegmentedControl(
                            selectedColumns = columnsCount,
                            onColumnsSelected = { cols ->
                                onUpdateSettings?.invoke { it.copy(serverGridColumns = cols, doubleColumnDisplay = (cols == 2)) }
                            },
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(visible = selecting) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(Res.string.select_count, selected.size),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            selected = if (selected.size == shownIds.size) emptySet() else shownIds.toSet()
                        }) {
                            Text(
                                stringResource(
                                    if (selected.size == shownIds.size && shownIds.isNotEmpty()) {
                                        Res.string.select_none
                                    } else {
                                        Res.string.select_all
                                    },
                                ),
                            )
                        }
                        IconButton(
                            onClick = { moveSelectionTo = true },
                            enabled = selected.isNotEmpty() && subscriptions.isNotEmpty(),
                        ) {
                            Icon(
                                painterResource(Res.drawable.ic_add_link),
                                contentDescription = stringResource(Res.string.groups_move),
                            )
                        }
                        IconButton(
                            onClick = { confirmDeleteSelection = true },
                            enabled = selected.isNotEmpty(),
                        ) {
                            Icon(
                                painterResource(Res.drawable.ic_delete),
                                contentDescription = stringResource(Res.string.action_delete),
                            )
                        }
                        IconButton(onClick = { selecting = false; selected = emptySet() }) {
                            Icon(
                                painterResource(Res.drawable.ic_close),
                                contentDescription = stringResource(Res.string.action_cancel),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val swipeLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
            val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
            val groupSwipe = if (subscriptions.isEmpty()) {
                Modifier
            } else {
                Modifier.pointerInput(subscriptions.size, showAllGroup, swipeLtr) {
                    var travelled = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { _: androidx.compose.ui.geometry.Offset -> travelled = 0f },
                        onDragEnd = {
                            val lowest = if (showAllGroup) 0 else 1
                            val highest = subscriptions.size + 1
                            val forward = if (swipeLtr) travelled <= -swipeThreshold else travelled >= swipeThreshold
                            val back = if (swipeLtr) travelled >= swipeThreshold else travelled <= -swipeThreshold
                            when {
                                forward -> groupIndex = (groupIndex + 1).coerceAtMost(highest)
                                back -> groupIndex = (groupIndex - 1).coerceAtLeast(lowest)
                            }
                        },
                    ) { _: androidx.compose.ui.input.pointer.PointerInputChange, delta: Float ->
                        travelled += delta
                    }
                }
            }

            if (shown.isEmpty()) {
                EmptyState(
                    Modifier.fillMaxSize().then(groupSwipe)
                        .padding(bottom = contentPadding.calculateBottomPadding()),
                )
            } else {
                val gridPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = contentPadding.calculateBottomPadding() + 88.dp,
                )

                val card: @Composable (VpnProfile) -> Unit = { profile ->
                    ServerCard(
                        modifier = if (columnsCount > 1) Modifier.fillMaxHeight() else Modifier,
                        profile = profile,
                        active = profile.id == activeId,
                        personalization = personalization,
                        columns = columnsCount,
                        subscriptionName = subscriptions
                            .firstOrNull { it.id == profile.subscriptionId }?.name,
                        selecting = selecting,
                        isSelected = profile.id in selected,
                        onClick = {
                            if (selecting) {
                                selected = if (profile.id in selected) selected - profile.id
                                else selected + profile.id
                            } else {
                                repository.setActive(profile.id)
                                onServerActivated()
                            }
                        },

                        shareLink = runCatching { repository.shareLinkOf(profile) }.getOrNull(),
                        onShare = {
                            val pl = runCatching { repository.shareLinkOf(profile) }.getOrNull()
                            if (pl == null) platform.toast(lockedCannotShare) else platform.shareText(pl)
                        },
                        onShareLocked = {
                            val pl = runCatching { repository.shareLinkOf(profile) }.getOrNull()
                            if (pl == null) platform.toast(lockedCannotShare) else lockShareTarget = profile to pl
                        },
                        onShareQr = {
                            qrTarget = runCatching { repository.shareLinkOf(profile) }.getOrNull()
                                ?.let { profile.name to it }
                        },
                        onRename = { renameTarget = profile },
                        onEdit = { editTarget = profile },
                        onMoveGroup = { moveTarget = profile },
                        onPingTcp = {
                            scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                repository.clearPings(listOf(profile.id), persist = false)
                                val isSpoof = profile.isAether || profile.isNoRootSniSpoof || profile.isSniSpoof
                                val isSealed = profile.isLocked || profile.source is dev.valid7996.narcicgeteway.domain.config.ProfileSource.Sealed
                                val ms = if (isSpoof || isSealed) {
                                    PingService.realDelay(
                                        profile,
                                        url = delayTestUrl.ifBlank { dev.valid7996.narcicgeteway.data.net.NetworkInfoRepository.DELAY_TEST_URL },
                                    )
                                } else {
                                    PingService.tcpPing(profile.address, profile.port)
                                }
                                val cc = if (ms > 0 && !isSpoof && !isSealed) GeoLookup.countryOf(profile.address) else null
                                repository.setPing(profile.id, if (ms > 0) ms.toInt() else PingService.FAILED_PING, cc)
                            }
                        },
                        onPingReal = {
                            scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                repository.clearPings(listOf(profile.id), persist = false)
                                val isSpoof = profile.isAether || profile.isNoRootSniSpoof || profile.isSniSpoof
                                val isSealed = profile.isLocked || profile.source is dev.valid7996.narcicgeteway.domain.config.ProfileSource.Sealed
                                val ms = PingService.realDelay(
                                    profile,
                                    url = delayTestUrl.ifBlank { dev.valid7996.narcicgeteway.data.net.NetworkInfoRepository.DELAY_TEST_URL },
                                )
                                val cc = if (ms > 0 && !isSpoof && !isSealed) GeoLookup.countryOf(profile.address) else null
                                repository.setPing(profile.id, if (ms > 0) ms.toInt() else PingService.FAILED_PING, cc)
                            }
                        },
                        onDelete = {
                            if (confirmRemove) deleteTarget = profile else repository.remove(profile.id)
                        },
                    )
                }

                val gridState = rememberLazyGridState()
                var order by remember { mutableStateOf(shown) }
                LaunchedEffect(shown) { order = shown }
                val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->

                    val fromIndex = order.indexOfFirst { it.id == from.key }
                    val toIndex = order.indexOfFirst { it.id == to.key }
                    if (fromIndex >= 0 && toIndex >= 0) {
                        order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
                    }
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columnsCount),
                    modifier = Modifier.fillMaxSize().then(groupSwipe),
                    contentPadding = gridPadding,
                    horizontalArrangement = Arrangement.spacedBy(personalization.density.gapDp.dp),
                    verticalArrangement = Arrangement.spacedBy(personalization.density.gapDp.dp),
                ) {
                    if (autoMembers.size >= 2) {
                        item(key = "auto-select", span = { GridItemSpan(maxLineSpan) }) {
                            AutoSelectCard(
                                groupLabel = autoLabel,
                                memberCount = autoMembers.size,
                                active = activeId == autoId,
                                live = autoLive,
                                memberName = { id -> profiles.firstOrNull { it.id == id }?.name },
                                onSelect = {
                                    repository.setActive(autoId)
                                    onServerActivated()
                                },
                                onDetails = { showAutoSheet = true },
                                personalization = personalization,
                            )
                        }
                    }
                    gridItemsIndexed(order, key = { _, p -> p.id }) { position, profile ->
                        ReorderableItem(reorderState, key = profile.id) {
                            Box(

                                Modifier
                                    .then(if (columnsCount > 1) Modifier.fillMaxHeight() else Modifier)

                                    
                                    .then(

                                        if (selecting) Modifier
                                        else Modifier.longPressDraggableHandle(
                                            onDragStopped = { repository.reorder(order.map { it.id }) },
                                        ),
                                    ),
                            ) {
                                card(profile)
                            }
                        }
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = fanOpen,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { fanOpen = false },
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 14.dp),
        ) {
            SpeedDialAction(
                visible = fanOpen,
                delayMs = 90,
                icon = Res.drawable.ic_edit,
                label = stringResource(Res.string.servers_add_manual),
            ) {
                fanOpen = false
                showManual = true
            }
            SpeedDialAction(
                visible = fanOpen,
                delayMs = 45,
                icon = Res.drawable.ic_content_paste,
                label = stringResource(Res.string.servers_add_clipboard),
            ) {
                fanOpen = false
                importFromClipboard()
            }
            SpeedDialAction(
                visible = fanOpen,
                delayMs = 0,
                icon = Res.drawable.ic_more_vert,
                label = stringResource(Res.string.servers_add_more),
            ) {
                fanOpen = false
                showAddSheet = true
            }

            val rotation by androidx.compose.animation.core.animateFloatAsState(
                if (fanOpen) 45f else 0f, label = "fabRotation",
            )
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(56.dp)

                    .combinedClickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = androidx.compose.material3.ripple(),
                        onClick = { showAddSheet = true },
                        onLongClick = { fanOpen = true },
                    ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(Res.drawable.ic_add),
                        stringResource(Res.string.servers_add),
                        modifier = Modifier.graphicsLayer { rotationZ = rotation },
                    )
                }
            }
        }
    }

    ovpnPrompt?.let { prompt ->
        var username by remember(prompt) { mutableStateOf("") }
        var password by remember(prompt) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { ovpnPrompt = null },
            shape = RoundedCornerShape(14.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            title = {
                Text(
                    stringResource(Res.string.ovpn_credentials_title),
                    color = if (isDark) Color.White else Color.Black,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column {
                    Text(
                        stringResource(Res.string.ovpn_credentials_body, prompt.name),
                        color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        singleLine = true,
                        label = { Text(stringResource(Res.string.ovpn_username)) },
                        shape = RoundedCornerShape(10.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF007AFF),
                            unfocusedBorderColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA),
                            focusedContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                            unfocusedContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text(stringResource(Res.string.ovpn_password)) },
                        shape = RoundedCornerShape(10.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF007AFF),
                            unfocusedBorderColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA),
                            focusedContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                            unfocusedContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = prompt.text
                        val name = prompt.name
                        val user = username
                        val pass = password
                        ovpnPrompt = null
                        scope.launch {
                            repository.addOvpn(text, name = name, username = user, password = pass)
                                .onSuccess { platform.toast(getString(Res.string.servers_imported, 1)) }
                                .onFailure { toastImportFailure(it) }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF007AFF),
                    ),
                ) { Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { ovpnPrompt = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF007AFF),
                    ),
                ) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }

    if (showAddSheet) {
        AddServerSheet(
            onDismiss = { showAddSheet = false },
            onPasteLink = {
                showAddSheet = false
                importFromClipboard()
            },
            onImportFile = {
                showAddSheet = false
                importFromFile()
            },
            onScanQr = {
                showAddSheet = false
                platform.scanQrCode { text -> if (text != null) importScanned(text) }
            },
            onScanQrImage = {
                showAddSheet = false
                platform.scanQrFromImage { text -> if (text != null) importScanned(text) }
            },
            onManual = {
                showAddSheet = false
                showManual = true
            },
            onCustom = {
                showAddSheet = false
                showCustomJson = true
            },
            onPsiphon = {
                showAddSheet = false
                showPsiphon = true
            },
            onDnsTunnel = {
                showAddSheet = false
                showDnsTunnel = true
            },
            onMasterDns = {
                showAddSheet = false
                showMasterDns = true
            },
            onOpenConnect = {
                showAddSheet = false
                showOpenConnect = true
            },
            onIkev2 = {
                showAddSheet = false
                showIkev2 = true
            },
            onTor = {
                showAddSheet = false
                repository.addTor("Tor")

                toastRes(Res.string.tor_added)
            },
            onSsh = {
                showAddSheet = false
                showSsh = true
            },
            onSniSpoof = {
                showAddSheet = false
                showSniSpoof = true
            },
            onSniSpoofNoRoot = {
                showAddSheet = false
                showSniSpoofNoRoot = true
            },
            onAether = {
                showAddSheet = false
                showAether = true
            },
            onProxyChain = {
                showAddSheet = false
                showProxyChain = true
            },
            onCrossChain = {
                showAddSheet = false
                showCrossChain = true
            },
            onSubscription = {
                showAddSheet = false
                showSubs = true
            },
            onIpScanner = {
                showAddSheet = false
                showIpScanner = true
            },
            onTelegramProxy = {
                showAddSheet = false
                showTelegramProxy = true
            },
            onPatt = {
                showAddSheet = false
                showPatt = true
            },
            onBackupRestore = {
                showAddSheet = false
                showBackupRestore = true
            },
            onDeviceCode = {
                showAddSheet = false
                showDeviceCode = true
            },
        )
    }

    if (showDeviceCode) {
        DeviceCodeDialog(code = repository.nrgDeviceId(), onDismiss = { showDeviceCode = false })
    }

    if (showBackupRestore) {
        dev.valid7996.narcicgeteway.ui.settings.backup.BackupRestoreSheet(
            settings = dev.valid7996.narcicgeteway.domain.model.AppSettings(),
            repository = repository,
            onDismiss = { showBackupRestore = false },
            onUpdateSettings = {},
        )
    }

    lockShareTarget?.let { (prof, payload) ->
        LockShareSheet(
            profile = prof,
            rawPayload = payload,
            relayEndpoint = repository.nrgRelayEndpoint,
            relayToken = repository.nrgRelayToken,
            onRelayChanged = { endpoint, token ->
                repository.nrgRelayEndpoint = endpoint
                repository.nrgRelayToken = token
            },
            onDismiss = { lockShareTarget = null },
        )
    }

    relayKeyPrompt?.let { pendingText ->
        RelayKeyDialog(
            onDismiss = { relayKeyPrompt = null },
            onSubmit = { key ->
                relayKeyPrompt = null
                scope.launch {
                    val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        repository.resolveNrgRelay(pendingText, key)
                    }
                    r.fold(
                        onSuccess = { if (!tryHandleLocked(it)) toastRes(Res.string.config_invalid) },
                        onFailure = { relayFailure(pendingText, it) },
                    )
                }
            },
        )
    }

    pendingUnlock?.let { (bytes, meta) ->
        UnlockConfigDialog(
            zsxBytes = bytes,
            metadata = meta,
            repository = repository,
            onDismiss = { pendingUnlock = null },
            onSuccess = { pendingUnlock = null },
        )
    }

    if (showIpScanner) {
        IpScannerSheet(
            settings = settings,
            onUpdateSettings = onUpdateSettings,
            onDismiss = { showIpScanner = false },
        )
    }

    if (showTelegramProxy) {
        TelegramProxySheet(onDismiss = { showTelegramProxy = false })
    }

    if (showPatt) {
        dev.valid7996.narcicgeteway.ui.settings.patt.PattSheet(
            onDismiss = { showPatt = false },
            settings = settings,
            onUpdateSettings = onUpdateSettings,
            onImportConfig = { json ->
                showPatt = false
                repository.importText(json)
                    .onSuccess { toastRes(Res.string.servers_imported, it) }
                    .onFailure { toastImportFailure(it) }
            },
        )
    }

    if (showManual) {
        ManualConfigSheet(
            onDismiss = { showManual = false },
            onSave = { link ->
                showManual = false
                requestImport(link)
            },
        )
    }

    pendingImportPayload?.let { payload ->
        AlertDialog(
            onDismissRequest = { pendingImportPayload = null },
            shape = RoundedCornerShape(16.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            icon = { Icon(painterResource(Res.drawable.ic_subscription), null, tint = Color(0xFF007AFF)) },
            title = {
                Text(
                    "افزودن به سابسکرپشن شخصی",
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "آیا واقعاً می‌خواهید این کانفیگ به سابسکرپشن شخصی اضافه شود؟",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color(0xFFEBEBF5).copy(alpha = 0.85f) else Color(0xFF3C3C43).copy(alpha = 0.85f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { rememberImportChoice = !rememberImportChoice }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = rememberImportChoice,
                            onCheckedChange = { rememberImportChoice = it },
                        )
                        Text(
                            "عدم نمایش مجدد این پیام و ذخیره انتخاب",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDark) Color.White else Color.Black,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val p = payload
                        val remember = rememberImportChoice
                        pendingImportPayload = null
                        if (remember) {
                            onUpdateSettings?.invoke { it.copy(importTargetMode = dev.valid7996.narcicgeteway.domain.model.ImportTargetMode.Subscription) }
                        }
                        val subId = (if (groupIndex >= 2) subscriptions.getOrNull(groupIndex - 2) else null)?.id
                            ?: subscriptions.firstOrNull()?.id
                            ?: repository.addSubscription("سابسکرپشن شخصی", "").id
                        doExecuteImport(p, subId)
                    },
                ) {
                    Text("بله مطمئنم", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val p = payload
                        val remember = rememberImportChoice
                        pendingImportPayload = null
                        if (remember) {
                            onUpdateSettings?.invoke { it.copy(importTargetMode = dev.valid7996.narcicgeteway.domain.model.ImportTargetMode.Manual) }
                        }
                        doExecuteImport(p, "")
                    },
                ) {
                    Text("خیر، در قسمت دستی بماند")
                }
            },
        )
    }

    if (showDnsTunnel) {
        DnsTunnelSheet(
            onDismiss = { showDnsTunnel = false },
            onSave = { name, settings ->
                showDnsTunnel = false
                repository.addDnsTunnel(settings, name)
                onServerActivated()
            },
        )
    }

    if (showMasterDns) {
        MasterDnsSheet(
            onDismiss = { showMasterDns = false },
            onSave = { name, settings ->
                showMasterDns = false
                repository.addMasterDns(settings, name)
                onServerActivated()
            },
        )
    }

    if (showOpenConnect) {
        OpenConnectSheet(
            onDismiss = { showOpenConnect = false },
            onSave = { name, settings ->
                showOpenConnect = false
                repository.addOpenConnect(settings, name)
                onServerActivated()
            },
        )
    }

    if (showIkev2) {
        Ikev2Sheet(
            onDismiss = { showIkev2 = false },
            onSave = { name, settings ->
                showIkev2 = false
                repository.addIkev2(settings, name)
                onServerActivated()
            },
        )
    }

    if (showSsh) {
        SshSheet(
            onDismiss = { showSsh = false },
            onSave = { name, settings ->
                showSsh = false
                repository.addSsh(settings, name)
                onServerActivated()
            },
        )
    }

    if (showSniSpoof) {
        SniSpoofSheet(
            candidates = spoofCandidates,
            onDismiss = { showSniSpoof = false },
            onSave = { name, settings ->
                showSniSpoof = false
                repository.addSniSpoof(settings, name)
                onServerActivated()
            },
        )
    }

    if (showSniSpoofNoRoot) {
        SniSpoofSheet(
            candidates = spoofCandidates,
            noRoot = true,
            onDismiss = { showSniSpoofNoRoot = false },
            onSave = { name, settings ->
                showSniSpoofNoRoot = false
                repository.addSniSpoof(settings, name)
                onServerActivated()
            },
        )
    }

    if (showProxyChain) {
        ProxyChainSheet(
            candidates = chainCandidates,
            onDismiss = { showProxyChain = false },
            onSave = { name, memberIds ->
                showProxyChain = false
                repository.addProxyChain(memberIds, name)
                onServerActivated()
            },
        )
    }

    if (showCrossChain) {
        CrossChainSheet(
            exits = crossExits,
            carriers = crossCarriers,
            pairError = crossPairError,
            onDismiss = { showCrossChain = false },
            onSave = { name, innerId, outerId ->
                showCrossChain = false
                repository.addCrossChain(innerId = innerId, outerId = outerId, name = name)
                onServerActivated()
            },
        )
    }

    if (showPsiphon) {
        PsiphonSheet(
            onDismiss = { showPsiphon = false },
            onSave = { name, settings ->
                showPsiphon = false
                repository.addPsiphon(settings, name)
                onServerActivated()
            },
        )
    }

    if (showAether) {
        AetherSheet(
            onDismiss = { showAether = false },
            onSave = { name, settings ->
                showAether = false
                repository.addAether(settings, name)
                onServerActivated()
            },
        )
    }

    if (showSubs) {
        SubscriptionsSheet(
            repository = repository,
            onDismiss = { showSubs = false },
        )
    }

    if (showCustomJson) {
        RawJsonSheet(
            title = stringResource(Res.string.servers_add_custom),
            initial = "",
            onDismiss = { showCustomJson = false },
            onSave = { text ->
                repository.importText(text).fold(
                    onSuccess = { count ->
                        if (count > 0) {
                            toastRes(Res.string.servers_imported, count)
                            showCustomJson = false
                            null
                        } else {
                            customJsonInvalid
                        }
                    },
                    onFailure = { it.message?.take(160)?.ifBlank { null } ?: customJsonInvalid },
                )
            },
            allowList = true,
        )
    }

    if (obfuscateCandidates.isNotEmpty()) {
        val pending = obfuscateCandidates
        AlertDialog(
            onDismissRequest = { obfuscateCandidates = emptyList() },
            shape = RoundedCornerShape(14.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            title = {
                Text(
                    stringResource(Res.string.wg_obfuscate_title),
                    color = if (isDark) Color.White else Color.Black,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    stringResource(Res.string.wg_obfuscate_body, pending.size),
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = repository.enableWireguardObfuscation(pending)
                        obfuscateCandidates = emptyList()
                        toastRes(Res.string.wg_obfuscate_done, n)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFF007AFF)),
                ) { Text(stringResource(Res.string.wg_obfuscate_enable), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { obfuscateCandidates = emptyList() },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFF007AFF)),
                ) {
                    Text(stringResource(Res.string.wg_obfuscate_keep))
                }
            },
        )
    }

    qrTarget?.let { (qrTitle, qrText) ->
        dev.valid7996.narcicgeteway.ui.components.QrDialog(
            title = qrTitle,
            text = qrText,
            copyLabel = stringResource(Res.string.action_copy),
            shareLabel = stringResource(Res.string.action_share),
            closeLabel = stringResource(Res.string.action_close),
            unsupportedLabel = stringResource(Res.string.qr_too_large),
            onCopy = { platform.copyToClipboard(qrText); toastRes(Res.string.copied) },
            onShare = { platform.shareText(qrText) },
            onDismiss = { qrTarget = null },
        )
    }

    confirmDeleteInvalid?.let { count ->
        AlertDialog(
            onDismissRequest = { confirmDeleteInvalid = null },
            shape = RoundedCornerShape(14.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            icon = { Icon(painterResource(Res.drawable.ic_delete), null, tint = Color(0xFFFF3B30)) },
            title = { Text(stringResource(Res.string.del_invalid), color = if (isDark) Color.White else Color.Black, fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(Res.string.del_invalid_confirm, count), color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73)) },
            confirmButton = {
                TextButton(
                    enabled = count > 0,
                    onClick = {
                        confirmDeleteInvalid = null
                        toastRes(Res.string.removed_count, repository.removeInvalid())
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF3B30)),
                ) { Text(stringResource(Res.string.action_delete), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDeleteInvalid = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFF007AFF)),
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    if (confirmDeleteSubscriptions) {
        val currentSub = if (groupIndex >= 2) subscriptions.getOrNull(groupIndex - 2) else null
        val targetSubId = currentSub?.id
        val subCount = if (targetSubId != null) {
            servers.count { it.subscriptionId == targetSubId }
        } else {
            servers.count { it.subscriptionId.isNotBlank() }
        }
        AlertDialog(
            onDismissRequest = { confirmDeleteSubscriptions = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            icon = { Icon(painterResource(Res.drawable.ic_delete), null, tint = Color(0xFFFF3B30)) },
            title = {
                Text(
                    stringResource(
                        if (currentSub != null) Res.string.del_this_subscription
                        else Res.string.del_subscriptions
                    ),
                    color = if (isDark) Color.White else Color.Black,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    if (currentSub != null) {
                        stringResource(Res.string.del_this_subscription_confirm, currentSub.name, subCount)
                    } else {
                        stringResource(Res.string.del_subscriptions_confirm, subCount)
                    },
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteSubscriptions = false
                        val removed = repository.removeSubscriptionConfigs(targetSubId)
                        toastRes(Res.string.removed_count, removed)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF3B30)),
                ) { Text(stringResource(Res.string.action_delete), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDeleteSubscriptions = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFF007AFF)),
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            icon = { Icon(painterResource(Res.drawable.ic_delete), null, tint = Color(0xFFFF3B30)) },
            title = { Text(stringResource(Res.string.del_all), color = if (isDark) Color.White else Color.Black, fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(Res.string.del_all_confirm), color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAll = false
                        val removed = servers.size
                        repository.removeAll { !it.isLocked }
                        toastRes(Res.string.removed_count, removed)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF3B30)),
                ) { Text(stringResource(Res.string.action_delete), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDeleteAll = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFF007AFF)),
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    if (moveSelectionTo) {
        AlertDialog(
            onDismissRequest = { moveSelectionTo = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            title = { Text(stringResource(Res.string.groups_move), color = if (isDark) Color.White else Color.Black, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    listOf("" to stringResource(Res.string.groups_manual)).plus(
                        subscriptions.map { it.id to it.name },
                    ).forEach { (id, label) ->
                        TextButton(
                            onClick = {
                                val moved = selected.size
                                repository.moveToGroup(selected, id)
                                moveSelectionTo = false
                                selecting = false
                                selected = emptySet()
                                toastRes(Res.string.select_moved, moved)
                            },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = if (isDark) Color.White else Color.Black),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { moveSelectionTo = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFF007AFF)),
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    if (confirmDeleteSelection) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelection = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            title = { Text(stringResource(Res.string.action_delete), color = if (isDark) Color.White else Color.Black, fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(Res.string.select_delete_confirm, selected.size), color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = selected.size
                        repository.removeAll(selected)
                        confirmDeleteSelection = false
                        selecting = false
                        selected = emptySet()
                        toastRes(Res.string.removed_count, n)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF3B30)),
                ) { Text(stringResource(Res.string.action_delete), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDeleteSelection = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFF007AFF)),
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            shape = RoundedCornerShape(14.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            icon = { Icon(painterResource(Res.drawable.ic_delete), null, tint = Color(0xFFFF3B30)) },
            title = { Text(stringResource(Res.string.servers_delete_confirm_title), color = if (isDark) Color.White else Color.Black, fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(Res.string.servers_delete_confirm_body, target.name), color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.remove(target.id)
                        deleteTarget = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF3B30)),
                ) { Text(stringResource(Res.string.action_delete), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTarget = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFF007AFF)),
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    CardActionHosts(
        repository = repository,
        editTarget = editTarget,
        renameTarget = renameTarget,
        moveTarget = moveTarget,
        onDismissEdit = { editTarget = null },
        onDismissRename = { renameTarget = null },
        onDismissMove = { moveTarget = null },
    )
}

@Composable
private fun GroupTab(
    label: String,
    count: Int,
    selected: Boolean,
    personalization: Personalization = Personalization.Default,
    onClick: () -> Unit,
) {
    val isAzkaban = personalization.themeProfileId == "azkaban"
    val isNoir = personalization.themeProfileId == "noir"
    val isBlossom = personalization.themeProfileId == "blossom"
    val activeColor = if (personalization.dynamicColor) MaterialTheme.colorScheme.primary else (personalization.serverActiveColor ?: MaterialTheme.colorScheme.primary)
    val bg = when {
        selected -> activeColor
        personalization.amoledBlack -> Color(0xFF000000)
        isBlossom -> Color(0xFF260D1A)
        isNoir -> Color(0xFF161618)
        isAzkaban -> Color(0xFF091424)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when {
        selected -> if (isBlossom) Color(0xFFFFFFFF) else if (isNoir) Color(0xFF000000) else if (isAzkaban) Color(0xFF041220) else MaterialTheme.colorScheme.onPrimary
        isBlossom -> Color(0xFFFFD1DC)
        isNoir -> Color(0xFFE4E4E7)
        isAzkaban -> Color(0xFFBAE6FD)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderModifier = if (!selected && (isAzkaban || isNoir || isBlossom || personalization.amoledBlack || personalization.dynamicColor)) {
        val bc = when {
            personalization.dynamicColor -> MaterialTheme.colorScheme.primary.copy(alpha = if (personalization.amoledBlack) 0.55f else 0.35f)
            isBlossom -> Color(0xFFF472B6).copy(alpha = if (personalization.amoledBlack) 0.55f else 0.35f)
            isNoir -> Color(0xFFFFFFFF).copy(alpha = if (personalization.amoledBlack) 0.40f else 0.20f)
            isAzkaban -> Color(0xFF38BDF8).copy(alpha = if (personalization.amoledBlack) 0.50f else 0.30f)
            personalization.amoledBlack -> Color(0xFF10B981).copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.outlineVariant
        }
        Modifier.border(1.dp, bc, RoundedCornerShape(50))
    } else Modifier

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = borderModifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = contentColor.copy(alpha = 0.85f),
            )
        }
    }
}


@Composable
private fun SpeedDialAction(
    visible: Boolean,
    delayMs: Int,
    icon: DrawableResource,
    label: String,
    onClick: () -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, delayMs)) +
            androidx.compose.animation.scaleIn(
                androidx.compose.animation.core.tween(200, delayMs),
                initialScale = 0.6f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f),
            ),
        exit = androidx.compose.animation.fadeOut() +
            androidx.compose.animation.scaleOut(targetScale = 0.6f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = 2.dp,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shadowElevation = 4.dp,
                modifier = Modifier.size(44.dp).clickable(onClick = onClick),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
internal fun AddServerSheet(
    onDismiss: () -> Unit,
    onPasteLink: () -> Unit,
    onImportFile: () -> Unit,
    onScanQr: () -> Unit,
    onScanQrImage: () -> Unit,
    onManual: () -> Unit,
    onCustom: () -> Unit,
    onPsiphon: () -> Unit,
    onDnsTunnel: () -> Unit,
    onMasterDns: () -> Unit,
    onOpenConnect: () -> Unit,
    onIkev2: () -> Unit,
    onTor: () -> Unit,
    onSsh: () -> Unit,
    onSniSpoof: () -> Unit,
    onSniSpoofNoRoot: () -> Unit,
    onAether: () -> Unit,
    onProxyChain: () -> Unit,
    onCrossChain: () -> Unit,
    onSubscription: () -> Unit,
    onIpScanner: (() -> Unit)? = null,
    onTelegramProxy: (() -> Unit)? = null,
    onPatt: (() -> Unit)? = null,
    onBackupRestore: (() -> Unit)? = null,
    onDeviceCode: (() -> Unit)? = null,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B).copy(alpha = 0.4f)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 26.dp),
        ) {
            Text(
                stringResource(Res.string.servers_add),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A),
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            )

            OptionGroup(Res.string.add_group_import)
            GlassOptionCard(
                title = stringResource(Res.string.servers_add_clipboard),
                subtitle = stringResource(Res.string.servers_add_clipboard_sub),
                icon = Res.drawable.ic_content_paste,
                accentColor = Color(0xFF20D8C0),
                iconTint = Color(0xFF60E0B0),
                gradientStart = Color(0xFF0F2B38).copy(alpha = 0.90f),
                gradientEnd = Color(0xFF0B1B2C).copy(alpha = 0.75f),
                onClick = onPasteLink,
            )
            if (ServersUiConfig.SHOW_QR_SCAN && LocalPlatform.current.supportsQrScan) {
                GlassOptionCard(
                    title = stringResource(Res.string.servers_add_scan_qr),
                    subtitle = stringResource(Res.string.servers_add_scan_qr_sub),
                    icon = Res.drawable.ic_qr_code_2,
                    accentColor = Color(0xFF2088FF),
                    iconTint = Color(0xFF38BDF8),
                    gradientStart = Color(0xFF0C2442).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF0A1A30).copy(alpha = 0.75f),
                    onClick = onScanQr,
                )
            }
            if (ServersUiConfig.SHOW_ALL_ADD_OPTIONS) {
                GlassOptionCard(
                    title = stringResource(Res.string.servers_add_file),
                    subtitle = "",
                    icon = Res.drawable.ic_description,
                    accentColor = Color(0xFF2088FF),
                    iconTint = Color(0xFF38BDF8),
                    gradientStart = Color(0xFF0C2442).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF0A1A30).copy(alpha = 0.75f),
                    onClick = onImportFile,
                )
                GlassOptionCard(
                    title = stringResource(Res.string.servers_add_scan_image),
                    subtitle = "",
                    icon = Res.drawable.ic_add_link,
                    accentColor = Color(0xFF2088FF),
                    iconTint = Color(0xFF38BDF8),
                    gradientStart = Color(0xFF0C2442).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF0A1A30).copy(alpha = 0.75f),
                    onClick = onScanQrImage,
                )
                GlassOptionCard(
                    title = stringResource(Res.string.servers_add_manual),
                    subtitle = "",
                    icon = Res.drawable.ic_edit,
                    accentColor = Color(0xFF20D8C0),
                    iconTint = Color(0xFF60E0B0),
                    gradientStart = Color(0xFF0F2B38).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF0B1B2C).copy(alpha = 0.75f),
                    onClick = onManual,
                )
                GlassOptionCard(
                    title = stringResource(Res.string.servers_add_custom),
                    subtitle = "",
                    icon = Res.drawable.ic_description,
                    accentColor = Color(0xFF20D8C0),
                    iconTint = Color(0xFF60E0B0),
                    gradientStart = Color(0xFF0F2B38).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF0B1B2C).copy(alpha = 0.75f),
                    onClick = onCustom,
                )

                OptionGroup(Res.string.add_group_tunnels)
                GlassOptionCard(
                    title = stringResource(Res.string.psiphon_add_title),
                    subtitle = stringResource(Res.string.psiphon_add_title_sub),
                    icon = Res.drawable.ic_bolt,
                    accentColor = Color(0xFFB55FE6),
                    iconTint = Color(0xFFD8B4FE),
                    gradientStart = Color(0xFF271348).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF130E28).copy(alpha = 0.75f),
                    onClick = onPsiphon,
                )
                GlassOptionCard(
                    title = stringResource(Res.string.tor_add_title),
                    subtitle = "",
                    icon = Res.drawable.ic_lock,
                    accentColor = Color(0xFFB55FE6),
                    iconTint = Color(0xFFD8B4FE),
                    gradientStart = Color(0xFF271348).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF130E28).copy(alpha = 0.75f),
                    onClick = onTor,
                )
                GlassOptionCard(
                    title = stringResource(Res.string.ssh_add_title),
                    subtitle = "",
                    icon = Res.drawable.ic_bolt,
                    accentColor = Color(0xFFB55FE6),
                    iconTint = Color(0xFFD8B4FE),
                    gradientStart = Color(0xFF271348).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF130E28).copy(alpha = 0.75f),
                    onClick = onSsh,
                )
                // The no-root SNI spoof is a setting now (Settings > SNI spoof), as in RstaNG, not a profile to create.
                if (ServersUiConfig.SHOW_SNI_SPOOF_NO_ROOT) GlassOptionCard(
                    title = stringResource(Res.string.snispoof_noroot_add_title),
                    subtitle = "",
                    icon = Res.drawable.ic_bolt,
                    accentColor = Color(0xFF5FE6B0),
                    iconTint = Color(0xFFB4F4DC),
                    gradientStart = Color(0xFF123B2E).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF0B1E19).copy(alpha = 0.75f),
                    onClick = onSniSpoofNoRoot,
                )

                OptionGroup(Res.string.add_group_dns)
                GlassOptionCard(
                    title = stringResource(Res.string.dns_tunnel_add_title),
                    subtitle = "",
                    icon = Res.drawable.ic_public,
                    accentColor = Color(0xFF20D8C0),
                    iconTint = Color(0xFF60E0B0),
                    gradientStart = Color(0xFF0F2B38).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF0B1B2C).copy(alpha = 0.75f),
                    onClick = onDnsTunnel,
                )
                GlassOptionCard(
                    title = stringResource(Res.string.master_dns_add_title),
                    subtitle = "",
                    icon = Res.drawable.ic_public,
                    accentColor = Color(0xFF20D8C0),
                    iconTint = Color(0xFF60E0B0),
                    gradientStart = Color(0xFF0F2B38).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF0B1B2C).copy(alpha = 0.75f),
                    onClick = onMasterDns,
                )

                OptionGroup(Res.string.add_group_vpn)
                GlassOptionCard(
                    title = stringResource(Res.string.openconnect_add_title),
                    subtitle = "",
                    icon = Res.drawable.ic_lock,
                    accentColor = Color(0xFF2088FF),
                    iconTint = Color(0xFF38BDF8),
                    gradientStart = Color(0xFF0C2442).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF0A1A30).copy(alpha = 0.75f),
                    onClick = onOpenConnect,
                )
                if (ServersUiConfig.SHOW_AETHER) {
                    GlassOptionCard(
                        title = stringResource(Res.string.aether_add_title),
                        subtitle = stringResource(Res.string.aether_add_title_sub),
                        icon = Res.drawable.ic_bolt,
                        accentColor = Color(0xFF2088FF),
                        iconTint = Color(0xFF38BDF8),
                        gradientStart = Color(0xFF0C2442).copy(alpha = 0.90f),
                        gradientEnd = Color(0xFF0A1A30).copy(alpha = 0.75f),
                        onClick = onAether,
                    )
                }
                GlassOptionCard(
                    title = stringResource(Res.string.ikev2_add_title),
                    subtitle = "",
                    icon = Res.drawable.ic_lock,
                    accentColor = Color(0xFF2088FF),
                    iconTint = Color(0xFF38BDF8),
                    gradientStart = Color(0xFF0C2442).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF0A1A30).copy(alpha = 0.75f),
                    onClick = onIkev2,
                )

                OptionGroup(Res.string.add_group_chains)
                GlassOptionCard(
                    title = stringResource(Res.string.proxychain_add_title),
                    subtitle = stringResource(Res.string.proxychain_add_title_sub),
                    icon = Res.drawable.ic_add_link,
                    accentColor = Color(0xFF20D8C0),
                    iconTint = Color(0xFF60E0B0),
                    gradientStart = Color(0xFF0F2B38).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF0B1B2C).copy(alpha = 0.75f),
                    onClick = onProxyChain,
                )
                GlassOptionCard(
                    title = stringResource(Res.string.crosschain_add_title),
                    subtitle = stringResource(Res.string.crosschain_add_title_sub),
                    icon = Res.drawable.ic_add_link,
                    accentColor = Color(0xFF2088FF),
                    iconTint = Color(0xFF38BDF8),
                    gradientStart = Color(0xFF0C2442).copy(alpha = 0.90f),
                    gradientEnd = Color(0xFF0A1A30).copy(alpha = 0.75f),
                    onClick = onCrossChain,
                )
            } else {
                if (ServersUiConfig.SHOW_PSIPHON || ServersUiConfig.SHOW_TOR || ServersUiConfig.SHOW_SNI_SPOOF_NO_ROOT) {
                    OptionGroup(Res.string.add_group_tunnels)
                    if (ServersUiConfig.SHOW_PSIPHON) {
                        GlassOptionCard(
                            title = stringResource(Res.string.psiphon_add_title),
                            subtitle = stringResource(Res.string.psiphon_add_title_sub),
                            icon = Res.drawable.ic_bolt,
                            accentColor = Color(0xFFB55FE6),
                            iconTint = Color(0xFFD8B4FE),
                            gradientStart = Color(0xFF271348).copy(alpha = 0.90f),
                            gradientEnd = Color(0xFF130E28).copy(alpha = 0.75f),
                            onClick = onPsiphon,
                        )
                    }
                    if (ServersUiConfig.SHOW_SNI_SPOOF_NO_ROOT) {
                        GlassOptionCard(
                            title = stringResource(Res.string.snispoof_noroot_add_title),
                            subtitle = "",
                            icon = Res.drawable.ic_bolt,
                            accentColor = Color(0xFF5FE6B0),
                            iconTint = Color(0xFFB4F4DC),
                            gradientStart = Color(0xFF123B2E).copy(alpha = 0.90f),
                            gradientEnd = Color(0xFF0B1E19).copy(alpha = 0.75f),
                            onClick = onSniSpoofNoRoot,
                        )
                    }
                    if (ServersUiConfig.SHOW_TOR) {
                        GlassOptionCard(
                            title = stringResource(Res.string.tor_add_title),
                            subtitle = "",
                            icon = Res.drawable.ic_lock,
                            accentColor = Color(0xFFB55FE6),
                            iconTint = Color(0xFFD8B4FE),
                            gradientStart = Color(0xFF271348).copy(alpha = 0.90f),
                            gradientEnd = Color(0xFF130E28).copy(alpha = 0.75f),
                            onClick = onTor,
                        )
                    }
                }
                if (ServersUiConfig.SHOW_AETHER) {
                    OptionGroup(Res.string.add_group_vpn)
                    GlassOptionCard(
                        title = stringResource(Res.string.aether_add_title),
                        subtitle = stringResource(Res.string.aether_add_title_sub),
                        icon = Res.drawable.ic_bolt,
                        accentColor = Color(0xFF2088FF),
                        iconTint = Color(0xFF38BDF8),
                        gradientStart = Color(0xFF0C2442).copy(alpha = 0.90f),
                        gradientEnd = Color(0xFF0A1A30).copy(alpha = 0.75f),
                        onClick = onAether,
                    )
                }
                if (ServersUiConfig.SHOW_PROXY_CHAIN || ServersUiConfig.SHOW_CROSS_CHAIN) {
                    OptionGroup(Res.string.add_group_chains)
                    if (ServersUiConfig.SHOW_PROXY_CHAIN) {
                        GlassOptionCard(
                            title = stringResource(Res.string.proxychain_add_title),
                            subtitle = stringResource(Res.string.proxychain_add_title_sub),
                            icon = Res.drawable.ic_add_link,
                            accentColor = Color(0xFF20D8C0),
                            iconTint = Color(0xFF60E0B0),
                            gradientStart = Color(0xFF0F2B38).copy(alpha = 0.90f),
                            gradientEnd = Color(0xFF0B1B2C).copy(alpha = 0.75f),
                            onClick = onProxyChain,
                        )
                    }
                    if (ServersUiConfig.SHOW_CROSS_CHAIN) {
                        GlassOptionCard(
                            title = stringResource(Res.string.crosschain_add_title),
                            subtitle = stringResource(Res.string.crosschain_add_title_sub),
                            icon = Res.drawable.ic_add_link,
                            accentColor = Color(0xFF2088FF),
                            iconTint = Color(0xFF38BDF8),
                            gradientStart = Color(0xFF0C2442).copy(alpha = 0.90f),
                            gradientEnd = Color(0xFF0A1A30).copy(alpha = 0.75f),
                            onClick = onCrossChain,
                        )
                    }
                }
            }

            OptionGroup(Res.string.add_group_subscription)
            GlassOptionCard(
                title = stringResource(Res.string.subs_add),
                subtitle = stringResource(Res.string.subs_add_sub),
                icon = Res.drawable.ic_add_link,
                accentColor = Color(0xFF2088FF),
                iconTint = Color(0xFF38BDF8),
                gradientStart = Color(0xFF0C2442).copy(alpha = 0.90f),
                gradientEnd = Color(0xFF0A1A30).copy(alpha = 0.75f),
                onClick = onSubscription,
            )

            if (onTelegramProxy != null || onIpScanner != null) {
                OptionGroup("ابزارهای شبکه و اسکنر (Network Tools)")
                if (onTelegramProxy != null) {
                    GlassOptionCard(
                        title = "پروکسی‌های تلگرام (Telegram Proxies)",
                        subtitle = "دریافت، تست پینگ زنده و اتصال مستقیم به پروکسی‌های MTProto",
                        icon = Res.drawable.ic_public,
                        accentColor = Color(0xFF34C759),
                        iconTint = Color(0xFF4ADE80),
                        gradientStart = Color(0xFF0D3320).copy(alpha = 0.90f),
                        gradientEnd = Color(0xFF081C12).copy(alpha = 0.75f),
                        onClick = onTelegramProxy,
                    )
                }
                if (onIpScanner != null) {
                    GlassOptionCard(
                        title = "اسکنر Narcic Scanner",
                        subtitle = "اسکنر پیشرفته CDN و دامنه (Narcic Scanner)",
                        icon = Res.drawable.ic_speedometer,
                        accentColor = Color(0xFF007AFF),
                        iconTint = Color(0xFF60A5FA),
                        gradientStart = Color(0xFF0C2442).copy(alpha = 0.90f),
                        gradientEnd = Color(0xFF0A1A30).copy(alpha = 0.75f),
                        onClick = onIpScanner,
                    )
                }
                if (onPatt != null) {
                    GlassOptionCard(
                        title = "ابزارهای Patt (Patt's Tools)",
                        subtitle = "تست SNI Spoof، بهینه‌سازی Cloudflare Fix و تزریق ECH به کانفیگ‌ها",
                        icon = Res.drawable.ic_tune,
                        accentColor = Color(0xFFFF9500),
                        iconTint = Color(0xFFFBBF24),
                        gradientStart = Color(0xFF332005).copy(alpha = 0.90f),
                        gradientEnd = Color(0xFF1E1303).copy(alpha = 0.75f),
                        onClick = onPatt,
                    )
                }
                if (onDeviceCode != null) {
                    GlassOptionCard(
                        title = "کد دستگاه من (برای کانفیگ قفل‌شده)",
                        subtitle = "کد را برای فروشنده بفرستید تا کانفیگ فقط روی همین دستگاه باز شود",
                        icon = Res.drawable.ic_lock,
                        accentColor = Color(0xFFFF9500),
                        iconTint = Color(0xFFFFB340),
                        gradientStart = Color(0xFF2A1A05).copy(alpha = 0.90f),
                        gradientEnd = Color(0xFF170E03).copy(alpha = 0.75f),
                        onClick = onDeviceCode,
                    )
                }
                if (onBackupRestore != null) {
                    GlassOptionCard(
                        title = "پشتیبان‌گیری و بازیابی (Backup & Restore)",
                        subtitle = "خروجی گرفتن و بازیابی کامل سرورها، اشتراک‌ها و تنظیمات",
                        icon = Res.drawable.ic_sync,
                        accentColor = Color(0xFF34C759),
                        iconTint = Color(0xFF4ADE80),
                        gradientStart = Color(0xFF092913).copy(alpha = 0.90f),
                        gradientEnd = Color(0xFF05170B).copy(alpha = 0.75f),
                        onClick = onBackupRestore,
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionGroup(labelRes: StringResource) {
    OptionGroup(stringResource(labelRes))
}

@Composable
private fun OptionGroup(label: String) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val textColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73)
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun GlassOptionCard(
    title: String,
    subtitle: String,
    icon: DrawableResource,
    accentColor: Color,
    iconTint: Color,
    gradientStart: Color,
    gradientEnd: Color,
    onClick: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val shape = RoundedCornerShape(14.dp)
    val cardBg = if (isDark) Color(0xFF2C2C2E).copy(alpha = 0.45f) else Color(0xFFF2F2F7)
    val cardBorder = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)
    val titleColor = if (isDark) Color(0xFFF2F2F7) else Color(0xFF000000)
    val subtitleColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73)
    val chevronColor = if (isDark) Color(0xFF545458) else Color(0xFFC7C7CC)

    Surface(
        onClick = onClick,
        shape = shape,
        color = cardBg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .border(0.5.dp, cardBorder, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accentColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = chevronColor,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        if (direction == LayoutDirection.Rtl) {
                            rotationY = 180f
                        }
                    },
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MorphingBlob(progress = 0.35f, brush = NrgGradients.idle, modifier = Modifier.size(136.dp)) {
            Icon(
                painterResource(Res.drawable.ic_dns),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(50.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(Res.string.servers_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.servers_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun RawJsonSheet(
    title: String,
    initial: String,
    onDismiss: () -> Unit,

    onSave: (String) -> String?,

    flavour: JsonFlavour = JsonFlavour.Xray,

    allowList: Boolean = false,
) {
    val platform = LocalPlatform.current
    var text by remember { mutableStateOf(initial) }

    val parsed = remember(text) {
        runCatching { JsonEditor.json.parseToJsonElement(dev.valid7996.narcicgeteway.domain.config.Jsonc.strip(text)) }
    }
    val lines = remember(text) { text.count { it == '\n' } + 1 }

    var saveError by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(text) { saveError = null }

    val usable = remember(text, flavour) {
        parsed.isSuccess && when (flavour) {
            JsonFlavour.Xray -> dev.valid7996.narcicgeteway.domain.config.CustomConfig.looksLikeCustomJson(text) &&
                (allowList || parsed.getOrNull() is kotlinx.serialization.json.JsonObject)
            JsonFlavour.SingBox ->
                dev.valid7996.narcicgeteway.domain.config.SingBoxJson.shapeOf(text) !=
                    dev.valid7996.narcicgeteway.domain.config.SingBoxJson.Shape.None
        }
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B).copy(alpha = 0.4f)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color.Black,
            )

            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AssistChip(
                    onClick = { parsed.getOrNull()?.let { text = JsonEditor.pretty.encodeToString(it) } },
                    enabled = parsed.isSuccess,
                    label = { Text(stringResource(Res.string.json_format)) },
                )
                AssistChip(
                    onClick = { parsed.getOrNull()?.let { text = JsonEditor.json.encodeToString(it) } },
                    enabled = parsed.isSuccess,
                    label = { Text(stringResource(Res.string.json_minify)) },
                )
                AssistChip(
                    onClick = { platform.copyToClipboard(text) },
                    label = { Text(stringResource(Res.string.action_copy)) },
                )
                AssistChip(
                    onClick = { platform.readClipboard()?.takeIf { it.isNotBlank() }?.let { text = it } },
                    label = { Text(stringResource(Res.string.json_paste)) },
                )
            }

            Text(
                text = saveError ?: parsed.fold(
                    onSuccess = {
                        if (usable) {
                            stringResource(Res.string.json_valid, lines)
                        } else when (flavour) {
                            JsonFlavour.Xray -> stringResource(Res.string.custom_json_invalid)
                            JsonFlavour.SingBox -> stringResource(Res.string.singbox_json_invalid)
                        }
                    },
                    onFailure = {
                        stringResource(Res.string.json_broken, it.message?.take(120).orEmpty())
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (usable && saveError == null) Color(0xFF34C759)
                else MaterialTheme.colorScheme.error,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(Res.string.custom_json_hint)) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                isError = text.isNotBlank() && !usable,
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF007AFF),
                    unfocusedBorderColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA),
                    focusedContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                    unfocusedContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                ),
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            Button(
                onClick = { saveError = onSave(text.trim()) },
                enabled = usable,
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007AFF),
                    contentColor = Color.White,
                    disabledContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                    disabledContentColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93),
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

internal enum class JsonFlavour { Xray, SingBox }

private object JsonEditor {
    val json = kotlinx.serialization.json.Json { prettyPrint = false }
    val pretty = kotlinx.serialization.json.Json { prettyPrint = true }
}

private data class OvpnPrompt(val text: String, val name: String)

@Composable
private fun IosColumnSegmentedControl(
    selectedColumns: Int,
    onColumnsSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerBg = if (isDark) Color(0x33767680) else Color(0x1F767680)
    val selectedBg = if (isDark) Color(0xFF636366) else Color.White
    val selectedContent = if (isDark) Color.White else Color.Black
    val unselectedContent = if (isDark) Color(0xFFEBEBF5).copy(alpha = 0.65f) else Color(0xFF3C3C43).copy(alpha = 0.65f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(containerBg)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(1, 2, 3).forEach { cols ->
            val isSelected = selectedColumns == cols
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(selectedBg)
                                .shadow(elevation = 1.5.dp, shape = RoundedCornerShape(7.dp))
                        } else Modifier
                    )
                    .clickable { onColumnsSelected(cols) }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (cols) {
                        1 -> "۱"
                        2 -> "۲"
                        3 -> "۳"
                        else -> "$cols"
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) selectedContent else unselectedContent,
                )
            }
        }
    }
}
