@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.valid7996.narcicgeteway.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.valid7996.narcicgeteway.core.AutoSelect
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.data.net.GeoLookup
import dev.valid7996.narcicgeteway.data.net.PingCoordinator
import dev.valid7996.narcicgeteway.data.net.PingService
import dev.valid7996.narcicgeteway.domain.config.AutoSelectIds
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.servers.AddServerSheet
import dev.valid7996.narcicgeteway.ui.settings.IpScannerSheet
import dev.valid7996.narcicgeteway.ui.settings.TelegramProxySheet
import dev.valid7996.narcicgeteway.ui.servers.AetherSheet
import dev.valid7996.narcicgeteway.ui.servers.AutoSelectCard
import dev.valid7996.narcicgeteway.ui.servers.AutoSelectSheet
import dev.valid7996.narcicgeteway.ui.servers.PsiphonSheet
import dev.valid7996.narcicgeteway.ui.servers.ServersUiConfig
import dev.valid7996.narcicgeteway.ui.servers.SniSpoofSheet
import dev.valid7996.narcicgeteway.ui.servers.ServerCard
import dev.valid7996.narcicgeteway.ui.servers.ProxyChainSheet
import dev.valid7996.narcicgeteway.ui.servers.CrossChainSheet
import dev.valid7996.narcicgeteway.ui.components.QrDialog
import dev.valid7996.narcicgeteway.ui.servers.SubscriptionsSheet
import dev.valid7996.narcicgeteway.ui.servers.ManualConfigSheet
import dev.valid7996.narcicgeteway.ui.servers.RawJsonSheet
import dev.valid7996.narcicgeteway.ui.servers.DnsTunnelSheet
import dev.valid7996.narcicgeteway.ui.servers.MasterDnsSheet
import dev.valid7996.narcicgeteway.ui.servers.MhrvRelaySheet
import dev.valid7996.narcicgeteway.ui.servers.OpenConnectSheet
import dev.valid7996.narcicgeteway.ui.servers.Ikev2Sheet
import dev.valid7996.narcicgeteway.ui.servers.SshSheet
import dev.valid7996.narcicgeteway.ui.theme.Personalization
import dev.valid7996.narcicgeteway.ui.theme.NrgGreen
import dev.valid7996.narcicgeteway.ui.theme.readableOn
import dev.valid7996.narcicgeteway.crypto.ZsxMetadata
import dev.valid7996.narcicgeteway.ui.servers.UnlockConfigDialog
import dev.valid7996.narcicgeteway.ui.servers.RelayKeyDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The servers under the connect control of the home screen: the group tabs of the servers screen —
 * every one, the manual servers, and each subscription — then the header with add, ping-all and the
 * overflow actions, then the same cards the servers screen keeps.
 */
@Composable
internal fun HomeServerSection(
    repository: ConfigRepository,
    personalization: Personalization,
    realPingConcurrency: Int,
    delayTestUrl: String,
    autoSortAfterTest: Boolean,
    autoTestAfterUpdate: Boolean,
    autoRemoveInvalidAfterTest: Boolean,
    settings: dev.valid7996.narcicgeteway.domain.model.AppSettings? = null,
    onUpdateSettings: (((dev.valid7996.narcicgeteway.domain.model.AppSettings) -> dev.valid7996.narcicgeteway.domain.model.AppSettings) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val platform = dev.valid7996.narcicgeteway.ui.platform.LocalPlatform.current
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val profiles by repository.profiles.collectAsStateWithLifecycle()
    val activeId by repository.activeId.collectAsStateWithLifecycle()
    val subscriptions by repository.subscriptions.collectAsStateWithLifecycle()
    val pingProgress by PingCoordinator.progress.collectAsStateWithLifecycle()
    val testing = pingProgress != null

    val importedTemplate = stringResource(Res.string.servers_imported)
    val importFailed = stringResource(Res.string.config_invalid)
    val updatedTemplate = stringResource(Res.string.subs_updated)
    val removedTemplate = stringResource(Res.string.removed_count)
    val copiedToast = stringResource(Res.string.copied)
    val nothingToExport = stringResource(Res.string.nothing_to_export)

    // The group tabs: every server, the manual ones, then one per subscription.
    var group by remember { mutableStateOf("all") }
    var showAdd by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var pingMenu by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<VpnProfile?>(null) }
    var renameTarget by remember { mutableStateOf<VpnProfile?>(null) }
    var moveTarget by remember { mutableStateOf<VpnProfile?>(null) }
    var updating by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmDeleteSubscriptions by remember { mutableStateOf(false) }
    var showAutoSheet by remember { mutableStateOf(false) }
    var showPsiphon by remember { mutableStateOf(false) }
    var showSniSpoofNoRoot by remember { mutableStateOf(false) }
    var showAether by remember { mutableStateOf(false) }
    var showProxyChain by remember { mutableStateOf(false) }
    var showCrossChain by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showCustomJson by remember { mutableStateOf(false) }
    var showDnsTunnel by remember { mutableStateOf(false) }
    var showMasterDns by remember { mutableStateOf(false) }
    var showMhrv by remember { mutableStateOf(false) }
    var showOpenConnect by remember { mutableStateOf(false) }
    var showIkev2 by remember { mutableStateOf(false) }
    var showSsh by remember { mutableStateOf(false) }
    var showIpScanner by remember { mutableStateOf(false) }
    var showTelegramProxy by remember { mutableStateOf(false) }
    var showPatt by remember { mutableStateOf(false) }
    var ovpnPrompt by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingUnlock by remember { mutableStateOf<Pair<ByteArray, ZsxMetadata>?>(null) }
    var relayPrompt by remember { mutableStateOf<String?>(null) }
    val customJsonInvalid = stringResource(Res.string.custom_json_invalid)
    val torAddedToast = stringResource(Res.string.tor_added)

    fun tryHandleLocked(text: String): Boolean {
        if (dev.valid7996.narcicgeteway.crypto.NrgLink.findRelay(text) != null) {
            scope.launch {
                val resolved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    repository.resolveNrgRelay(text)
                }
                resolved.fold(
                    onSuccess = { resolvedText ->
                        if (!tryHandleLocked(resolvedText)) platform.toast(importFailed)
                    },
                    onFailure = { err ->
                        if (err is dev.valid7996.narcicgeteway.data.config.RelayKeyRequiredException) {
                            relayPrompt = text
                        } else if (err is dev.valid7996.narcicgeteway.data.config.RelayAlreadyClaimedException) {
                            platform.toast("این لینک قبلاً روی یک دستگاه دیگر فعال شده است.")
                        } else {
                            platform.toast(importFailed)
                        }
                    },
                )
            }
            return true
        }
        val link = dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor.extractLink(text) ?: return false
        val bytes = runCatching { dev.valid7996.narcicgeteway.crypto.NrgLink.decode(link) }.getOrNull() ?: return false
        val meta = runCatching { repository.peekLocked(bytes) }.getOrNull() ?: return false
        if (meta.isExpired) {
            platform.toast("این کانفیگ منقضی شده است و اعتبار آن پایان یافته است.")
            return true
        }
        if (meta.deviceBound) {
            val autoImp = repository.importLocked(bytes, null)
            autoImp.fold(
                onSuccess = { platform.toast(importedTemplate.format(1)) },
                onFailure = {
                    val key = dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor.extractKey(text)
                    if (key != null) {
                        val keyImp = repository.importLocked(bytes, key)
                        keyImp.fold(
                            onSuccess = { platform.toast(importedTemplate.format(1)) },
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
        val key = dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor.extractKey(text)
        if (meta.passwordProtected) {
            if (key != null) {
                val imported = repository.importLocked(bytes, key)
                imported.fold(
                    onSuccess = { platform.toast(importedTemplate.format(1)) },
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
            onSuccess = { platform.toast(importedTemplate.format(1)) },
            onFailure = { platform.toast(it.message ?: importFailed) },
        )
        return true
    }

    fun importFromFile() {
        scope.launch {
            val picked = platform.pickFileBytes() ?: return@launch
            val bytes = picked.bytes
            if (bytes.isEmpty()) {
                platform.toast(importFailed)
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
                platform.toast(importFailed)
                return@launch
            }
            val fileName = picked.name.substringBeforeLast('.').takeIf { it.isNotBlank() }
            val namedOvpn = picked.name.endsWith(".ovpn", ignoreCase = true)
            val info = runCatching {
                dev.valid7996.narcicgeteway.domain.config.OvpnConfig
                    .takeIf { namedOvpn || it.looksLikeOvpn(text) }?.inspect(text)
            }
            val ovpn = info.getOrNull()
            if (ovpn != null && ovpn.needsCredentials && !ovpn.hasInlineCredentials) {
                ovpnPrompt = Pair(text, fileName ?: ovpn.name)
                return@launch
            }
            if (ovpn != null) {
                repository.addOvpn(text, name = fileName ?: ovpn.name)
                    .onSuccess { platform.toast(importedTemplate.format(1)) }
                    .onFailure { platform.toast(it.message ?: importFailed) }
                return@launch
            }
            if (tryHandleLocked(text)) return@launch
            repository.importText(text)
                .onSuccess { count -> platform.toast(importedTemplate.format(count)) }
                .onFailure { platform.toast(it.message ?: importFailed) }
        }
    }
    var qrTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lockShareTarget by remember { mutableStateOf<Pair<VpnProfile, String>?>(null) }
    var showBackupRestore by remember { mutableStateOf(false) }
    val lockedCannotShare = stringResource(Res.string.locked_cannot_share)

    val allServers = profiles
    val manualCount = allServers.count { it.subscriptionId.isEmpty() }
    val servers = when (group) {
        "all" -> allServers
        "manual" -> allServers.filter { it.subscriptionId.isEmpty() }
        else -> allServers.filter { it.subscriptionId == group }
    }
    val subscriptionNames = subscriptions.associate { it.id to it.name }

    val chainCandidates = allServers.filter {
        !it.isLocked && it.rawPayload() != null && (!it.isCustom || it.isServerless) && !it.isManagedTunnel && !it.isProxyChain &&
            !it.isSingBoxConfig
    }
    val crossCarriers = allServers.filter { !it.isLocked && it.canCarryChain && !it.isCrossChain }
    val crossExits = allServers.filter { !it.isLocked && it.canDialThroughProxy && !it.isCrossChain }
    val udpMismatchTemplate = stringResource(Res.string.crosschain_udp_unsupported)
    val crossPairError: (VpnProfile, VpnProfile) -> String? = { inner, outer ->
        repository.udpMismatch(inner, outer)?.let {
            udpMismatchTemplate.replace("%1\$s", it.protocol).replace("%2\$s", it.carrier)
        }
    }

    // The auto card: the group's best config comes up on its own.
    val autoScope: String? = when (group) {
        "all" -> null
        "manual" -> ""
        else -> group
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

    fun pingAll(useRealDelay: Boolean) {
        PingCoordinator.start(
            profiles = servers,
            useRealDelay = useRealDelay,
            concurrency = realPingConcurrency,
            delayUrl = delayTestUrl,
            chainConfig = repository::chainProbeConfig,
            clearPings = { repository.clearPings(it, persist = false) },
            onResult = { id, ms, cc -> repository.setPing(id, ms, cc, persist = false) },
            flush = { repository.flushProfiles() },
            onFinished = { if (autoSortAfterTest) repository.sortByTestResults() },
        )
    }

    Column(modifier.fillMaxWidth()) {
        // Group tabs: همه · دستی · each subscription
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GroupTab(label = stringResource(Res.string.group_all), count = allServers.size, selected = group == "all", personalization = personalization) { group = "all" }
            GroupTab(label = stringResource(Res.string.group_manual), count = manualCount, selected = group == "manual", personalization = personalization) { group = "manual" }
            subscriptions.forEach { sub ->
                GroupTab(
                    label = sub.name,
                    count = allServers.count { it.subscriptionId == sub.id },
                    selected = group == sub.id,
                    personalization = personalization,
                ) { group = sub.id }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.nav_servers),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (testing) {
                GlassIconButton(onClick = { PingCoordinator.cancel() }) {
                    Icon(
                        painterResource(Res.drawable.ic_close),
                        contentDescription = stringResource(Res.string.ping_stop),
                    )
                }
            } else {
                Box {
                    GlassIconButton(onClick = { pingMenu = true }) {
                        Icon(
                            painterResource(Res.drawable.ic_speed),
                            contentDescription = stringResource(Res.string.ping_test),
                        )
                    }
                    DropdownMenu(
                        expanded = pingMenu,
                        onDismissRequest = { pingMenu = false },
                        shape = RoundedCornerShape(14.dp),
                        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA),
                        ),
                    ) {
                        val currentSub = subscriptions.firstOrNull { it.id == group }
                        val currentScopeName = when (group) {
                            "all" -> null
                            "manual" -> stringResource(Res.string.group_manual)
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
                            onClick = { pingMenu = false; pingAll(useRealDelay = false) },
                        )
                        DropdownMenuItem(
                            text = { Text(realPingLabel) },
                            onClick = { pingMenu = false; pingAll(useRealDelay = true) },
                        )
                    }
                }
            }
            Box {
                GlassIconButton(onClick = { overflowOpen = true }) {
                    Icon(
                        painterResource(Res.drawable.ic_more_vert),
                        contentDescription = stringResource(Res.string.servers_actions),
                    )
                }
                DropdownMenu(
                    expanded = overflowOpen,
                    onDismissRequest = { overflowOpen = false },
                    shape = RoundedCornerShape(14.dp),
                    containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA),
                    ),
                ) {
                    val currentSub = subscriptions.firstOrNull { it.id == group }
                    if (currentSub != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.subs_update_this)) },
                            leadingIcon = { Icon(painterResource(Res.drawable.ic_sync), null) },
                            enabled = !updating,
                            onClick = {
                                overflowOpen = false
                                updating = true
                                scope.launch(Dispatchers.Default) {
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
                                        platform.toast(updatedTemplate.format(n))
                                    } else {
                                        platform.toast(importFailed)
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
                            scope.launch(Dispatchers.Default) {
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
                                platform.toast(updatedTemplate.format(n))
                            }
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
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.export_all)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_ios_share), null) },
                        onClick = {
                            overflowOpen = false
                            val payload = repository.exportAllPayloads()
                            if (payload.isBlank()) {
                                platform.toast(nothingToExport)
                            } else {
                                platform.copyToClipboard(payload)
                                platform.toast(copiedToast)
                            }
                        },
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.del_duplicates)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null) },
                        onClick = {
                            overflowOpen = false
                            val n = repository.removeDuplicates()
                            platform.toast(removedTemplate.format(n))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.del_invalid)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null) },
                        onClick = {
                            overflowOpen = false
                            val n = repository.invalidCount()
                            repository.removeInvalid()
                            platform.toast(removedTemplate.format(n))
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
                            allServers.any { it.subscriptionId == currentSub.id }
                        } else {
                            allServers.any { it.subscriptionId.isNotBlank() }
                        },
                        onClick = {
                            overflowOpen = false
                            confirmDeleteSubscriptions = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.del_all), color = Color(0xFFFF3B30), fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null, tint = Color(0xFFFF3B30)) },
                        onClick = {
                            overflowOpen = false
                            confirmDeleteAll = true
                        },
                    )
                }
            }
            GlassIconButton(onClick = { showAdd = true }) {
                Icon(
                    painterResource(Res.drawable.ic_add),
                    contentDescription = stringResource(Res.string.servers_add),
                )
            }
        }

        if (autoMembers.size >= 2) {
            AutoSelectCard(
                groupLabel = autoLabel,
                memberCount = autoMembers.size,
                active = activeId == autoId,
                live = autoLive,
                memberName = { id -> profiles.firstOrNull { it.id == id }?.name },
                onSelect = { repository.setActive(autoId) },
                onDetails = { showAutoSheet = true },
                personalization = personalization,
            )
            Spacer(Modifier.height(10.dp))
        }

        if (servers.isNotEmpty()) {
            // A bounded, scrollable column: the cards are plain children, so the section keeps
            // the home layout's exact sizing while long lists scroll inside their own box.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                servers.forEach { profile ->
                    ServerCard(
                        profile = profile,
                        active = profile.id == activeId,
                        subscriptionName = subscriptionNames[profile.subscriptionId],
                        personalization = personalization,
                        onClick = { repository.setActive(profile.id) },
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
                        onPingTcp = {
                            scope.launch(Dispatchers.Default) {
                                pingOne(repository, profile, delayTestUrl, real = false)
                            }
                        },
                        onPingReal = {
                            scope.launch(Dispatchers.Default) {
                                pingOne(repository, profile, delayTestUrl, real = true)
                            }
                        },
                        onRename = { renameTarget = profile },
                        onEdit = { editTarget = profile },
                        onMoveGroup = { moveTarget = profile },
                        onDelete = { repository.remove(profile.id) },
                    )
                }
            }
        }
    }

    if (showAutoSheet) {
        AutoSelectSheet(
            groupLabel = autoLabel,
            members = autoMembers,
            live = autoLive,
            onDismiss = { showAutoSheet = false },
        )
    }

    if (showAether) {
        AetherSheet(
            onDismiss = { showAether = false },
            onSave = { name, settings ->
                showAether = false
                repository.addAether(settings, name)
            },
        )
    }

    if (showPsiphon) {
        PsiphonSheet(
            onDismiss = { showPsiphon = false },
            onSave = { name, settings ->
                showPsiphon = false
                repository.addPsiphon(settings, name)
            },
        )
    }

    if (showSniSpoofNoRoot) {
        SniSpoofSheet(
            candidates = profiles.filter { !it.isManagedTunnel && !it.isCrossChain && !it.isProxyChain },
            noRoot = true,
            onDismiss = { showSniSpoofNoRoot = false },
            onSave = { _, settings ->
                showSniSpoofNoRoot = false
                repository.saveSniSpoofDefaults(
                    settings.cleanIp,
                    settings.connectPort,
                    settings.fakeSni,
                    settings.method,
                )
            },
        )
    }

    if (showSubs) {
        SubscriptionsSheet(
            repository = repository,
            onDismiss = { showSubs = false },
        )
    }

    if (showAdd) {
        AddServerSheet(
            onDismiss = { showAdd = false },
            onPasteLink = {
                showAdd = false
                val text = platform.readClipboard()
                if (!text.isNullOrBlank()) {
                    if (!tryHandleLocked(text)) {
                        scope.launch {
                            repository.importText(text)
                                .onSuccess { platform.toast(importedTemplate.format(it)) }
                                .onFailure { platform.toast(importFailed) }
                        }
                    }
                }
            },
            onImportFile = {
                showAdd = false
                importFromFile()
            },
            onScanQr = {
                showAdd = false
                platform.scanQrCode { text ->
                    if (text.isNullOrBlank()) return@scanQrCode
                    if (!tryHandleLocked(text)) {
                        scope.launch {
                            repository.importText(text)
                                .onSuccess { platform.toast(importedTemplate.format(it)) }
                                .onFailure { platform.toast(importFailed) }
                        }
                    }
                }
            },
            onScanQrImage = {
                showAdd = false
                platform.scanQrFromImage { text ->
                    if (text.isNullOrBlank()) return@scanQrFromImage
                    if (!tryHandleLocked(text)) {
                        scope.launch {
                            repository.importText(text)
                                .onSuccess { platform.toast(importedTemplate.format(it)) }
                                .onFailure { platform.toast(importFailed) }
                        }
                    }
                }
            },
            onManual = {
                showAdd = false
                showManual = true
            },
            onCustom = {
                showAdd = false
                showCustomJson = true
            },
            onPsiphon = {
                showAdd = false
                showPsiphon = true
            },
            onDnsTunnel = {
                showAdd = false
                showDnsTunnel = true
            },
            onMasterDns = {
                showAdd = false
                showMasterDns = true
            },
            onMhrv = {
                showAdd = false
                showMhrv = true
            },
            onOpenConnect = {
                showAdd = false
                showOpenConnect = true
            },
            onIkev2 = {
                showAdd = false
                showIkev2 = true
            },
            onTor = {
                showAdd = false
                repository.addTor("Tor")
                platform.toast(torAddedToast)
            },
            onSsh = {
                showAdd = false
                showSsh = true
            },
            onSniSpoof = {},
            onSniSpoofNoRoot = { showAdd = false; showSniSpoofNoRoot = true },
            onProxyChain = {
                showAdd = false
                showProxyChain = true
            },
            onCrossChain = {
                showAdd = false
                showCrossChain = true
            },
            onAether = { showAdd = false; showAether = true },
            onSubscription = { showAdd = false; showSubs = true },
            onIpScanner = { showAdd = false; showIpScanner = true },
            onTelegramProxy = { showAdd = false; showTelegramProxy = true },
            onPatt = { showAdd = false; showPatt = true },
            onBackupRestore = { showAdd = false; showBackupRestore = true },
        )
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
        dev.valid7996.narcicgeteway.ui.servers.LockShareSheet(
            profile = prof,
            rawPayload = payload,
            onDismiss = { lockShareTarget = null },
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
                    .onSuccess { count -> platform.toast(importedTemplate.format(count)) }
                    .onFailure { platform.toast(it.message ?: importFailed) }
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
            },
        )
    }

    if (showManual) {
        ManualConfigSheet(
            onDismiss = { showManual = false },
            onSave = { link ->
                showManual = false
                repository.importText(link)
                    .onSuccess { platform.toast(importedTemplate.format(it)) }
                    .onFailure { platform.toast(it.message ?: importFailed) }
            },
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
                            platform.toast(importedTemplate.format(count))
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

    if (showDnsTunnel) {
        DnsTunnelSheet(
            onDismiss = { showDnsTunnel = false },
            onSave = { name, s ->
                showDnsTunnel = false
                repository.addDnsTunnel(s, name)
            },
        )
    }

    if (showMasterDns) {
        MasterDnsSheet(
            onDismiss = { showMasterDns = false },
            onSave = { name, s ->
                showMasterDns = false
                repository.addMasterDns(s, name)
            },
        )
    }

    if (showMhrv) {
        MhrvRelaySheet(
            onDismiss = { showMhrv = false },
            onSave = { name, s ->
                showMhrv = false
                repository.addMhrvRelay(s, name)
            },
        )
    }

    if (showOpenConnect) {
        OpenConnectSheet(
            onDismiss = { showOpenConnect = false },
            onSave = { name, s ->
                showOpenConnect = false
                repository.addOpenConnect(s, name)
            },
        )
    }

    if (showIkev2) {
        Ikev2Sheet(
            onDismiss = { showIkev2 = false },
            onSave = { name, s ->
                showIkev2 = false
                repository.addIkev2(s, name)
            },
        )
    }

    if (showSsh) {
        SshSheet(
            onDismiss = { showSsh = false },
            onSave = { name, s ->
                showSsh = false
                repository.addSsh(s, name)
            },
        )
    }

    ovpnPrompt?.let { (text, name) ->
        var username by remember(text) { mutableStateOf("") }
        var password by remember(text) { mutableStateOf("") }
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
                        stringResource(Res.string.ovpn_credentials_body, name),
                        color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                    )
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
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
                    androidx.compose.material3.OutlinedTextField(
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
                        val user = username
                        val pass = password
                        ovpnPrompt = null
                        scope.launch {
                            repository.addOvpn(text, name = name, username = user, password = pass)
                                .onSuccess { platform.toast(importedTemplate.format(1)) }
                                .onFailure { platform.toast(it.message ?: importFailed) }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFF007AFF)),
                ) { Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { ovpnPrompt = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFF007AFF)),
                ) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }

    qrTarget?.let { (qrTitle, qrText) ->
        QrDialog(
            title = qrTitle,
            text = qrText,
            copyLabel = stringResource(Res.string.action_copy),
            shareLabel = stringResource(Res.string.action_share),
            closeLabel = stringResource(Res.string.action_close),
            unsupportedLabel = stringResource(Res.string.qr_too_large),
            onCopy = { platform.copyToClipboard(qrText); platform.toast(copiedToast) },
            onShare = { platform.shareText(qrText) },
            onDismiss = { qrTarget = null },
        )
    }

    dev.valid7996.narcicgeteway.ui.servers.CardActionHosts(
        repository = repository,
        editTarget = editTarget,
        renameTarget = renameTarget,
        moveTarget = moveTarget,
        onDismissEdit = { editTarget = null },
        onDismissRename = { renameTarget = null },
        onDismissMove = { moveTarget = null },
    )

    pendingUnlock?.let { (bytes, meta) ->
        UnlockConfigDialog(
            zsxBytes = bytes,
            metadata = meta,
            repository = repository,
            onDismiss = { pendingUnlock = null },
            onSuccess = {
                pendingUnlock = null
                platform.toast(importedTemplate.format(1))
            },
        )
    }

    relayPrompt?.let { pendingText ->
        RelayKeyDialog(
            onDismiss = { relayPrompt = null },
            onSubmit = { key ->
                relayPrompt = null
                scope.launch(Dispatchers.Default) {
                    repository.resolveNrgRelay(pendingText, key)
                        .mapCatching { resolved -> repository.importPasted(resolved).getOrThrow() }
                        .onSuccess { r ->
                            platform.toast(importedTemplate.format(r.count))
                        }
                        .onFailure { err ->
                            when (err) {
                                is dev.valid7996.narcicgeteway.data.config.RelayAlreadyClaimedException ->
                                    platform.toast("این لینک قبلاً روی یک دستگاه دیگر فعال شده است.")
                                is dev.valid7996.narcicgeteway.crypto.ZsxWrongPasswordException ->
                                    platform.toast("کلید اختصاصی وارد شده اشتباه است.")
                                else -> platform.toast(importFailed)
                            }
                        }
                }
            },
        )
    }

    if (confirmDeleteSubscriptions) {
        val currentSub = subscriptions.firstOrNull { it.id == group }
        val targetSubId = currentSub?.id
        val subCount = if (targetSubId != null) {
            allServers.count { it.subscriptionId == targetSubId }
        } else {
            allServers.count { it.subscriptionId.isNotBlank() }
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
                        platform.toast(removedTemplate.format(removed))
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
                        val removed = allServers.size
                        repository.removeAll { !it.isLocked }
                        platform.toast(removedTemplate.format(removed))
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
}

/** A header action button sitting in a glass circle. */
@Composable
private fun GlassIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        modifier = Modifier.size(42.dp),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) { content() }
    }
}

/** The group chip of the servers screen, restyled as glass for the home header. */
@Composable
private fun GroupTab(
    label: String,
    count: Int,
    selected: Boolean,
    personalization: Personalization = Personalization.Default,
    onClick: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isAzkaban = personalization.themeProfileId == "azkaban"
    val isNoir = personalization.themeProfileId == "noir"
    val isBlossom = personalization.themeProfileId == "blossom"
    val activeColor = if (personalization.dynamicColor) MaterialTheme.colorScheme.primary else (personalization.serverActiveColor ?: NrgGreen)
    val tabShape = RoundedCornerShape(8.dp)
    val background = if (selected) {
        if (personalization.dynamicColor || personalization.serverActiveColor != null) activeColor else Color(0xFF007AFF)
    } else if (personalization.amoledBlack) {
        Color(0xFF000000)
    } else if (isBlossom) {
        Color(0xFF230D18)
    } else if (isNoir) {
        Color(0xFF141416)
    } else if (isAzkaban) {
        Color(0xFF0C192E)
    } else if (isDark) {
        Color(0xFF1C1C1E)
    } else {
        Color(0xFFE5E5EA)
    }
    val content = if (selected) {
        Color.White
    } else if (isBlossom) {
        Color(0xFFFFD1DC)
    } else if (isNoir) {
        Color(0xFFE4E4E7)
    } else if (isAzkaban) {
        Color(0xFFBAE6FD)
    } else if (isDark) {
        Color(0xFF8E8E93)
    } else {
        Color(0xFF6E6E73)
    }
    val borderColor = if (selected) Color.Transparent else (if (isDark) Color(0xFF2C2C2E) else Color(0xFFD1D1D6))

    Row(
        Modifier
            .clip(tabShape)
            .background(background)
            .border(
                0.5.dp,
                borderColor,
                tabShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            maxLines = 1,
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.75f),
            )
        }
    }
}

private suspend fun pingOne(
    repository: ConfigRepository,
    profile: VpnProfile,
    delayTestUrl: String,
    real: Boolean,
) {
    repository.clearPings(listOf(profile.id), persist = false)
    val isSpoof = profile.isAether || profile.isNoRootSniSpoof || profile.isSniSpoof
    val isSealed = profile.isLocked || profile.source is dev.valid7996.narcicgeteway.domain.config.ProfileSource.Sealed
    val isMhrv = profile.isMhrvRelay
    val ms = if (real || isSpoof || isSealed || isMhrv) {
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
