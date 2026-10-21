@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.valid7996.narcicgeteway.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.valid7996.narcicgeteway.core.LogBus
import dev.valid7996.narcicgeteway.core.VpnManager
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.valid7996.narcicgeteway.ui.platform.draggableHorizontalScroll

private enum class LogCategory(val tags: Set<String>) {
    App(setOf(
        "NarcicGeteway", "NrgVpnService", "MainActivity", "VpnManager", "AndroidVpn",
        "PingService", "PingCoordinator", "ConfigRepository", "GeoAssets", "UpdateChecker",
        "Ikev2Controller", "Ikev2EventReceiver", "OpenConnectController", "NrgWidgetProvider",
        "HevTunCore", "SocksShim", "SpeedTest",
    )),
    Xray(setOf("GoLog", "Go", "XrayController", "Libv2ray", "v2ray", "LibXray", "CoreProbe")),
    Tor(setOf("Tor", "TorController", "Snowflake", "Obfs4", "Conjure")),
    Dns(setOf("DnsTunnel", "DnsProbe", "DnsScanner", "DnsTunnelController", "Dnstt", "VayDns")),
    MasterDns(setOf("MasterDns", "MasterDnsController", "masterdns")),
    Mhrv(setOf("mhrv_rs", "MhrvRelay", "MhrvRelayController", "mhrv")),
    Psiphon(setOf("Psiphon")),
    Ssh(setOf("Ssh", "SshController", "JSch")),
}

private fun categoriesFor(kind: String?): Set<LogCategory> = when (kind) {
    VpnManager.KIND_XRAY, VpnManager.KIND_SNISPOOF -> setOf(LogCategory.Xray)
    VpnManager.KIND_PSIPHON -> setOf(LogCategory.Psiphon)
    VpnManager.KIND_TOR -> setOf(LogCategory.Tor)
    VpnManager.KIND_DNS_TUNNEL -> setOf(LogCategory.Dns)
    VpnManager.KIND_MASTERDNS -> setOf(LogCategory.MasterDns)
    VpnManager.KIND_MHRV -> setOf(LogCategory.Mhrv)
    VpnManager.KIND_SSH -> setOf(LogCategory.Ssh, LogCategory.Xray)

    VpnManager.KIND_CROSS_CHAIN -> LogCategory.entries.toSet()
    else -> emptySet()
}

private val TAG_RE = Regex("""\s([VDIWEF])/([A-Za-z0-9_.$-]+)""")

private data class LogLine(val raw: String, val level: Char, val tag: String?)

private fun parse(raw: String): LogLine {
    val m = TAG_RE.find(raw)
    return LogLine(raw, m?.groupValues?.get(1)?.first() ?: 'I', m?.groupValues?.get(2)?.trim())
}

private enum class LogLevel(val min: Set<Char>) {
    All(setOf('V', 'D', 'I', 'W', 'E', 'F')),
    Info(setOf('I', 'W', 'E', 'F')),
    Warn(setOf('W', 'E', 'F')),
    Error(setOf('E', 'F')),
}

@Composable
private fun levelLabel(level: LogLevel): String = when (level) {
    LogLevel.All -> stringResource(Res.string.logs_level_all)
    LogLevel.Info -> stringResource(Res.string.logs_level_info)
    LogLevel.Warn -> stringResource(Res.string.logs_level_warn)
    LogLevel.Error -> stringResource(Res.string.logs_level_error)
}

@Composable
private fun categoryLabel(category: LogCategory): String = when (category) {
    LogCategory.App -> stringResource(Res.string.logs_source_app)
    LogCategory.Xray -> "Xray"
    LogCategory.Tor -> "Tor"
    LogCategory.Dns -> "DNS"
    LogCategory.MasterDns -> "MasterDNS"
    LogCategory.Mhrv -> "MHRV Relay"
    LogCategory.Psiphon -> "Psiphon"
    LogCategory.Ssh -> "SSH"
}

@Composable
fun LogSheet(onDismiss: () -> Unit) {
    val platform = LocalPlatform.current
    val logsCopied = stringResource(Res.string.logs_copied)
    val allLines by LogBus.lines.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        LogBus.attach()
        onDispose { LogBus.detach() }
    }
    val activeKind by VpnManager.activeKind.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var picked by remember { mutableStateOf<LogCategory?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(LogLevel.All) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(activeKind) { showAll = activeKind == null; picked = null }

    val sessionCategories = remember(activeKind) { categoriesFor(activeKind) }

    val ownTags by LogBus.ownTags.collectAsStateWithLifecycle()
    val appTags = remember(ownTags) {
        ownTags.filterTo(HashSet()) { tag -> LogCategory.entries.none { it != LogCategory.App && tag in it.tags } }
    }
    val lines = remember(allLines, picked, showAll, level, query, sessionCategories, appTags) {
        val allowed: Set<LogCategory>? = when {
            showAll -> null
            picked != null -> setOf(picked!!, LogCategory.App)
            sessionCategories.isNotEmpty() -> sessionCategories + LogCategory.App
            else -> null
        }
        val needle = query.trim()
        allLines.asSequence()
            .map(::parse)
            .filter { it.level in level.min }
            .filter { line ->
                allowed == null || line.tag != null && allowed.any { cat ->
                    line.tag in cat.tags || cat == LogCategory.App && line.tag in appTags
                }
            }
            .filter { needle.isEmpty() || it.raw.contains(needle, ignoreCase = true) }
            .toList()
    }

    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty() && atBottom) listState.scrollToItem(lines.lastIndex)
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.2f)),
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(Res.string.logs_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${lines.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = {
                    platform.copyToClipboard(lines.joinToString("\n") { it.raw })
                    platform.toast(logsCopied)
                }) {
                    Icon(painterResource(Res.drawable.ic_content_paste), stringResource(Res.string.logs_copy), Modifier.height(18.dp))
                }
                FilledTonalButton(onClick = { platform.shareText(lines.joinToString("\n") { it.raw }) }) {
                    Icon(painterResource(Res.drawable.ic_ios_share), stringResource(Res.string.logs_share), Modifier.height(18.dp))
                }
                FilledTonalButton(onClick = { LogBus.clear() }) {
                    Icon(painterResource(Res.drawable.ic_delete), stringResource(Res.string.logs_clear), Modifier.height(18.dp))
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text(stringResource(Res.string.logs_search)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_search), null, Modifier.size(18.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        FilledTonalIconButton(onClick = { query = "" }) {
                            Icon(painterResource(Res.drawable.ic_close), stringResource(Res.string.action_cancel), Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            Row(
                Modifier.fillMaxWidth().draggableHorizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (sessionCategories.isNotEmpty()) {
                    FilterChip(
                        selected = !showAll && picked == null,
                        onClick = { showAll = false; picked = null },
                        label = { Text(stringResource(Res.string.logs_source_session)) },
                    )
                }
                FilterChip(
                    selected = showAll,
                    onClick = { showAll = true; picked = null },
                    label = { Text(stringResource(Res.string.logs_source_all)) },
                )
                LogCategory.entries.forEach { c ->
                    FilterChip(
                        selected = !showAll && picked == c,
                        onClick = { showAll = false; picked = c },
                        label = { Text(categoryLabel(c)) },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().draggableHorizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.logs_level),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LogLevel.entries.forEach { l ->
                    FilterChip(
                        selected = level == l,
                        onClick = { level = l },
                        label = { Text(levelLabel(l)) },
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(420.dp)) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    if (lines.isEmpty()) {
                        Text(
                            stringResource(
                                if (allLines.isEmpty()) Res.string.logs_empty else Res.string.logs_no_match,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    } else {
                        val logBoxShape = RoundedCornerShape(14.dp)
                        Surface(
                            color = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7),
                            shape = logBoxShape,
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp),
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                itemsIndexed(lines, key = { i, l -> "$i:${l.raw.hashCode()}" }) { _, line ->
                                    LogRow(line)
                                }
                            }
                        }
                    }
                }

                if (!atBottom && lines.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { scope.launch { listState.scrollToItem(lines.lastIndex) } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    ) {
                        Icon(painterResource(Res.drawable.ic_keyboard_arrow_down), null, Modifier.size(16.dp))
                        Spacer4()
                        Text(stringResource(Res.string.logs_jump_latest), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Spacer4() = Box(Modifier.width(6.dp))

@Composable
private fun LogRow(line: LogLine) {
    val colour = when (line.level) {
        'E', 'F' -> MaterialTheme.colorScheme.error
        'W' -> MaterialTheme.colorScheme.tertiary
        'D', 'V' -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .background(
                    if (line.level == 'E' || line.level == 'F' || line.level == 'W') colour
                    else Color.Transparent,
                    RoundedCornerShape(2.dp),
                ),
        )
        Spacer4()
        Text(
            text = line.raw,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = colour,
        )
    }
}
