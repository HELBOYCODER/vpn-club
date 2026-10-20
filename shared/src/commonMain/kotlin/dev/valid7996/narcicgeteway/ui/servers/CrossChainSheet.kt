@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.ButtonDefaults

private enum class CrossSlot { CARRIER, EXIT }

@Composable
fun CrossChainSheet(
    exits: List<VpnProfile>,
    carriers: List<VpnProfile>,

    pairError: (inner: VpnProfile, outer: VpnProfile) -> String?,
    onDismiss: () -> Unit,
    onSave: (name: String, innerId: String, outerId: String) -> Unit,
    initialName: String = "",
    initialInnerId: String = "",
    initialOuterId: String = "",
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var name by remember { mutableStateOf(initialName) }
    var innerId by remember { mutableStateOf(initialInnerId) }
    var outerId by remember { mutableStateOf(initialOuterId) }
    var query by remember { mutableStateOf("") }

    var slot by remember {
        mutableStateOf(if (initialOuterId.isEmpty()) CrossSlot.CARRIER else CrossSlot.EXIT)
    }

    val inner = exits.firstOrNull { it.id == innerId }
    val outer = carriers.firstOrNull { it.id == outerId }

    val sameProfile = innerId.isNotEmpty() && innerId == outerId
    val incompatible = if (inner != null && outer != null && !sameProfile) pairError(inner, outer) else null
    val valid = inner != null && outer != null && !sameProfile && incompatible == null

    val options = (if (slot == CrossSlot.CARRIER) carriers else exits).filterByChainQuery(query)
    val emptyText = stringResource(
        if (slot == CrossSlot.CARRIER) Res.string.crosschain_no_carriers else Res.string.crosschain_no_exits,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 10.dp),
                color = if (isDark) Color(0xFF38383A) else Color(0xFFD1D1D6),
                shape = RoundedCornerShape(2.5.dp),
            ) {
                Box(Modifier.size(width = 36.dp, height = 5.dp))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 8.dp),
            ) {
                item(key = "head") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(Res.string.crosschain_add_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(Res.string.crosschain_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item(key = "name") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(Res.string.manual_remark)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item(key = "slots") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SlotCard(
                            label = stringResource(Res.string.crosschain_carrier),
                            picked = outer,
                            active = slot == CrossSlot.CARRIER,
                            onClick = { slot = CrossSlot.CARRIER },
                            modifier = Modifier.weight(1f),
                        )
                        SlotCard(
                            label = stringResource(Res.string.crosschain_exit),
                            picked = inner,
                            active = slot == CrossSlot.EXIT,
                            onClick = { slot = CrossSlot.EXIT },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item(key = "hint") {
                    Text(
                        stringResource(
                            if (slot == CrossSlot.CARRIER) Res.string.crosschain_carrier_hint
                            else Res.string.crosschain_exit_hint,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item(key = "search") { ChainSearchField(value = query, onValueChange = { query = it }) }

                chainCandidateItems(
                    options = options,
                    leading = { p ->
                        val id = if (slot == CrossSlot.CARRIER) outerId else innerId
                        if (p.id == id) "●" else "○"
                    },
                    picked = { p -> p.id == (if (slot == CrossSlot.CARRIER) outerId else innerId) },
                    onSelect = { p ->
                        if (slot == CrossSlot.CARRIER) {
                            outerId = p.id

                            if (innerId.isEmpty()) { slot = CrossSlot.EXIT; query = "" }
                        } else {
                            innerId = p.id
                            if (outerId.isEmpty()) { slot = CrossSlot.CARRIER; query = "" }
                        }
                    },
                )

                if (options.isEmpty()) {
                    item(key = "none") {
                        Text(
                            if (query.isBlank()) emptyText
                            else stringResource(Res.string.chain_no_results, query),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (query.isBlank()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider()
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (valid) {
                    Surface(
                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(Res.string.crosschain_summary, inner.name, outer.name),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                if (incompatible != null) {
                    Text(
                        incompatible,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (sameProfile) {
                    Text(
                        stringResource(Res.string.crosschain_same_profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (inner?.isPsiphon == true) {
                    Text(
                        stringResource(Res.string.crosschain_psiphon_quic_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (inner?.isTor == true) {
                    Text(
                        stringResource(Res.string.crosschain_tor_bridge_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (inner != null && !inner.isManagedTunnel) {
                    Text(
                        stringResource(Res.string.crosschain_fragment_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(
                    onClick = { onSave(name.trim(), innerId, outerId) },
                    enabled = valid,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun SlotCard(
    label: String,
    picked: VpnProfile?,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val shape = RoundedCornerShape(12.dp)
    val cardBorder = if (active) Color(0xFF007AFF) else (if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA))
    val cardBg = if (active) Color(0xFF007AFF).copy(alpha = 0.12f)
        else (if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))

    Surface(
        onClick = onClick,
        color = cardBg,
        shape = shape,
        border = androidx.compose.foundation.BorderStroke(if (active) 1.5.dp else 0.5.dp, cardBorder),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (active) Color(0xFF007AFF) else (if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73)),
            )
            Text(
                picked?.name ?: stringResource(Res.string.crosschain_not_set),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (picked == null) MaterialTheme.colorScheme.onSurfaceVariant
                else (if (isDark) Color.White else Color.Black),
            )
            if (picked != null) {
                Text(
                    picked.transportLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
