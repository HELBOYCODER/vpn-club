package dev.valid7996.narcicgeteway.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.core.tor.TorBridges
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun TorBridgesScreen(
    s: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val scope = rememberCoroutineScope()
    val transport = s.torBridgeTransport
    var bridges by remember(transport) { mutableStateOf<List<TorBridges.Bridge>>(emptyList()) }
    LaunchedEffect(transport) { bridges = TorBridges.load(transport) }
    val pings = remember(transport) { SnapshotStateMap<String, Long>() }
    var pingingAll by remember { mutableStateOf(false) }

    val enabled = remember(s.torEnabledBridges) {
        s.torEnabledBridges.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun toggle(line: String) {
        val next = enabled.toMutableSet()
        if (!next.remove(line)) next.add(line)
        onUpdate { it.copy(torEnabledBridges = next.joinToString("\n")) }
    }

    fun pingOne(b: TorBridges.Bridge) {
        pings[b.line] = -2L
        scope.launch(Dispatchers.Default) { pings[b.line] = TorBridges.ping(b, s.torSnowflakeRendezvous) }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7))
            .padding(contentPadding),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Spacer(Modifier.height(8.dp))
            dev.valid7996.narcicgeteway.ui.components.PageHeader(
                title = stringResource(Res.string.tor_bridges_title),
                subtitle = transport,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.tor_bridges_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = !pingingAll && bridges.isNotEmpty(),
                    onClick = {
                        pingingAll = true
                        scope.launch {
                            bridges.map { b ->
                                pings[b.line] = -2L
                                launch(Dispatchers.Default) {
                                    pings[b.line] = TorBridges.ping(b, s.torSnowflakeRendezvous)
                                }
                            }.joinAll()
                            pingingAll = false
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF007AFF),
                        contentColor = Color.White,
                        disabledContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                        disabledContentColor = if (isDark) Color(0xFF8E8E93) else Color(0xFFAFAFB4),
                    ),
                ) {
                    Text(
                        stringResource(Res.string.tor_bridges_ping_all),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (bridges.isEmpty()) {
            Text(
                stringResource(Res.string.tor_bridges_empty),
                Modifier.padding(24.dp),
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                        border = BorderStroke(0.5.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            bridges.forEachIndexed { index, b ->
                                BridgeRow(
                                    bridge = b,
                                    ping = pings[b.line],
                                    checked = enabled.isEmpty() || b.line in enabled,
                                    allUsedByDefault = enabled.isEmpty(),
                                    isDark = isDark,
                                    onToggle = { toggle(b.line) },
                                    onPing = { pingOne(b) },
                                )
                                if (index < bridges.lastIndex) {
                                    HorizontalDivider(
                                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BridgeRow(
    bridge: TorBridges.Bridge,
    ping: Long?,
    checked: Boolean,
    allUsedByDefault: Boolean,
    isDark: Boolean,
    onToggle: () -> Unit,
    onPing: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF007AFF),
                checkmarkColor = Color.White,
                uncheckedColor = if (isDark) Color(0xFF8E8E93) else Color(0xFFC7C7CC),
            ),
        )
        Column(Modifier.weight(1f)) {
            Text(
                bridge.address,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                bridge.line,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (ping) {
            -2L -> CircularProgressIndicator(
                Modifier.padding(4.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF007AFF),
            )
            null -> Button(
                onClick = onPing,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                    contentColor = Color(0xFF007AFF),
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    stringResource(Res.string.tor_bridges_ping),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            -1L -> Text(
                stringResource(Res.string.tor_bridges_unreachable),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFFF3B30),
            )
            else -> Text(
                "$ping ms",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    ping < 400 -> Color(0xFF34C759)
                    ping < 1200 -> Color(0xFFFF9500)
                    else -> Color(0xFFFF3B30)
                },
            )
        }
    }
}
