@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valid7996.narcicgeteway.domain.config.MhrvRelayProfile
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.components.PickerField
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Add / edit sheet of the MasterHttpRelayVPN (Apps Script relay) core. */
@Composable
fun MhrvRelaySheet(
    initial: MhrvRelayProfile? = null,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, MhrvRelayProfile) -> Unit,
) {
    val platform = LocalPlatform.current
    val pastedOk = stringResource(Res.string.mhrv_pasted_ok)
    val pasteBad = stringResource(Res.string.mhrv_paste_bad)

    var name by remember { mutableStateOf(initialName) }
    var mode by remember { mutableStateOf(initial?.mode ?: MhrvRelayProfile.MODE_APPS_SCRIPT) }
    var scriptIdsList by remember { mutableStateOf(initial?.scriptIdList() ?: emptyList()) }
    var authKey by remember { mutableStateOf(initial?.authKey ?: "") }
    var googleIp by remember { mutableStateOf(initial?.googleIp ?: MhrvRelayProfile.DEFAULT_GOOGLE_IP) }
    var frontDomain by remember { mutableStateOf(initial?.frontDomain ?: MhrvRelayProfile.DEFAULT_FRONT_DOMAIN) }
    var sniHosts by remember { mutableStateOf(initial?.sniHosts ?: "") }
    var verifySsl by remember { mutableStateOf(initial?.verifySsl ?: true) }
    var parallel by remember { mutableIntStateOf(initial?.parallelRelay ?: 1) }
    var blockQuic by remember { mutableStateOf(initial?.blockQuic ?: true) }
    var blockStun by remember { mutableStateOf(initial?.blockStun ?: true) }
    var blockDoh by remember { mutableStateOf(initial?.blockDoh ?: true) }
    var tunnelDoh by remember { mutableStateOf(initial?.tunnelDoh ?: true) }
    var coalesceStep by remember { mutableIntStateOf(initial?.coalesceStepMs ?: 10) }
    var coalesceMax by remember { mutableIntStateOf(initial?.coalesceMaxMs ?: 1000) }
    var youtubeRelay by remember { mutableStateOf(initial?.youtubeViaRelay ?: false) }
    var upstream by remember { mutableStateOf(initial?.upstreamSocks5 ?: "") }
    var advancedOpen by remember { mutableStateOf(false) }

    fun current() = MhrvRelayProfile(
        mode = mode,
        scriptIds = scriptIdsList.joinToString("\n"),
        authKey = authKey.trim(),
        googleIp = googleIp.trim(),
        frontDomain = frontDomain.trim(),
        sniHosts = sniHosts.trim(),
        verifySsl = verifySsl,
        parallelRelay = parallel,
        blockQuic = blockQuic,
        blockStun = blockStun,
        blockDoh = blockDoh,
        tunnelDoh = tunnelDoh,
        coalesceStepMs = coalesceStep,
        coalesceMaxMs = coalesceMax,
        youtubeViaRelay = youtubeRelay,
        upstreamSocks5 = upstream.trim(),
        logLevel = initial?.logLevel ?: "info",
    )

    val draft = current()
    val needsCreds = draft.needsCredentials
    val valid = draft.isValid

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(Res.string.mhrv_add_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(Res.string.mhrv_add_title_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = {
                    val parsed = platform.readClipboard()?.let(MhrvRelayProfile::fromText)
                    if (parsed == null) {
                        platform.toast(pasteBad)
                    } else {
                        mode = parsed.mode
                        scriptIdsList = parsed.scriptIdList()
                        authKey = parsed.authKey.ifBlank { authKey }
                        googleIp = parsed.googleIp
                        frontDomain = parsed.frontDomain
                        sniHosts = parsed.sniHosts
                        verifySsl = parsed.verifySsl
                        parallel = parsed.parallelRelay.coerceIn(1, 8)
                        blockQuic = parsed.blockQuic
                        blockStun = parsed.blockStun
                        blockDoh = parsed.blockDoh
                        tunnelDoh = parsed.tunnelDoh
                        coalesceStep = parsed.coalesceStepMs
                        coalesceMax = parsed.coalesceMaxMs
                        youtubeRelay = parsed.youtubeViaRelay
                        upstream = parsed.upstreamSocks5
                        platform.toast(pastedOk)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.mhrv_paste_config)) }

            Field(name, Res.string.manual_remark) { name = it }
            PickerField(
                label = stringResource(Res.string.mhrv_mode),
                options = listOf(
                    MhrvRelayProfile.MODE_APPS_SCRIPT to stringResource(Res.string.mhrv_mode_apps_script),
                    MhrvRelayProfile.MODE_FULL to stringResource(Res.string.mhrv_mode_full),
                    MhrvRelayProfile.MODE_DIRECT to stringResource(Res.string.mhrv_mode_direct),
                ),
                selected = mode,
                onSelect = { mode = it },
            )
            if (needsCreds) {
                DeploymentIdsEditor(
                    ids = scriptIdsList,
                    onChange = { scriptIdsList = it },
                )
                Field(authKey, Res.string.mhrv_auth_key) { authKey = it }
            }
            Field(googleIp, Res.string.mhrv_google_ip, placeholder = MhrvRelayProfile.DEFAULT_GOOGLE_IP) { googleIp = it }
            Field(frontDomain, Res.string.mhrv_front_domain, placeholder = MhrvRelayProfile.DEFAULT_FRONT_DOMAIN) { frontDomain = it }

            // SNI Rotation Pool with Test & Checkboxes
            SniPoolEditor(
                currentSniHosts = sniHosts,
                googleIp = googleIp,
                onChange = { sniHosts = it },
            )

            if (draft.needsCa && platform.supportsMhrvCa) {
                Text(
                    stringResource(Res.string.mhrv_ca_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { platform.installMhrvCa().takeIf { it.isNotBlank() }?.let(platform::toast) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(Res.string.mhrv_install_ca)) }
            }

            TextButton(onClick = { advancedOpen = !advancedOpen }) {
                Text(stringResource(Res.string.mhrv_advanced) + if (advancedOpen) " ▲" else " ▼")
            }
            AnimatedVisibility(advancedOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(upstream, Res.string.mhrv_upstream_socks, placeholder = "127.0.0.1:10808") { upstream = it }
                    Stepper(Res.string.mhrv_parallel, parallel, min = 1, max = 8) { parallel = it }
                    SwitchRow(Res.string.mhrv_verify_ssl, verifySsl, isDark) { verifySsl = it }
                    SwitchRow(Res.string.mhrv_block_quic, blockQuic, isDark) { blockQuic = it }
                    SwitchRow(Res.string.mhrv_block_stun, blockStun, isDark) { blockStun = it }
                    SwitchRow(Res.string.mhrv_block_doh, blockDoh, isDark) { blockDoh = it }
                    SwitchRow(Res.string.mhrv_tunnel_doh, tunnelDoh, isDark) { tunnelDoh = it }
                    SwitchRow(Res.string.mhrv_youtube_relay, youtubeRelay, isDark) { youtubeRelay = it }
                    Stepper(Res.string.mhrv_coalesce_step, coalesceStep, min = 5, max = 500, step = 5) { coalesceStep = it }
                    Stepper(Res.string.mhrv_coalesce_max, coalesceMax, min = 100, max = 5000, step = 100) { coalesceMax = it }
                }
            }

            Button(
                onClick = { onSave(name, current()) },
                enabled = valid,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007AFF),
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DeploymentIdsEditor(
    ids: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var newIdInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.mhrv_deployment_ids_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (ids.isNotEmpty()) {
                Text(
                    "${ids.size} شناسه",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
        Text(
            stringResource(Res.string.mhrv_deployment_ids_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Existing IDs list
        ids.forEachIndexed { index, id ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { edited ->
                        val parts = edited.split(',', '\n', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
                        val updated = ids.toMutableList()
                        if (parts.size > 1) {
                            updated.removeAt(index)
                            updated.addAll(index, parts)
                        } else {
                            updated[index] = edited
                        }
                        onChange(updated.distinct())
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("#${index + 1}") },
                    shape = RoundedCornerShape(10.dp),
                )
                IconButton(
                    onClick = {
                        onChange(ids.filterIndexed { i, _ -> i != index })
                    },
                ) {
                    Text("✕", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Add Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newIdInput,
                onValueChange = { newIdInput = it },
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 3,
                placeholder = { Text(stringResource(Res.string.mhrv_deployment_id_placeholder), fontSize = 12.sp) },
                shape = RoundedCornerShape(10.dp),
            )
            Button(
                onClick = {
                    val parts = newIdInput.split(',', '\n', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
                    if (parts.isNotEmpty()) {
                        onChange((ids + parts).distinct())
                        newIdInput = ""
                    }
                },
                enabled = newIdInput.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(stringResource(Res.string.mhrv_add_deployment_id), fontWeight = FontWeight.Bold)
            }
        }
    }
}

private sealed interface SniProbeStatus {
    object Idle : SniProbeStatus
    object InFlight : SniProbeStatus
    data class Ok(val ms: Long) : SniProbeStatus
    data class Err(val message: String) : SniProbeStatus
}

@Composable
private fun SniPoolEditor(
    currentSniHosts: String,
    googleIp: String,
    onChange: (String) -> Unit,
) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val defaultPool = MhrvRelayProfile.DEFAULT_SNI_POOL

    val activeList = remember(currentSniHosts) {
        val list = currentSniHosts.split(',', '\n', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (list.isEmpty()) defaultPool else list
    }
    val enabledSet = remember(activeList) { activeList.toSet() }

    var allSniList by remember {
        mutableStateOf((defaultPool + activeList).distinct())
    }
    var customSniInput by remember { mutableStateOf("") }
    val probeMap = remember { mutableStateMapOf<String, SniProbeStatus>() }

    fun probeOne(sni: String) {
        probeMap[sni] = SniProbeStatus.InFlight
        scope.launch {
            val ip = googleIp.trim().ifEmpty { MhrvRelayProfile.DEFAULT_GOOGLE_IP }
            val raw = platform.testSni(ip, sni)
            if (raw.contains("\"ok\":true")) {
                val latency = runCatching {
                    val m = Regex(""""latencyMs"\s*:\s*(\d+)""").find(raw)
                    m?.groupValues?.get(1)?.toLongOrNull() ?: 100L
                }.getOrDefault(100L)
                probeMap[sni] = SniProbeStatus.Ok(latency)
            } else {
                val err = runCatching {
                    val m = Regex(""""error"\s*:\s*"([^"]+)"""").find(raw)
                    m?.groupValues?.get(1) ?: "خطا در اتصال"
                }.getOrDefault("Failed")
                probeMap[sni] = SniProbeStatus.Err(err)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.mhrv_sni_pool),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = { allSniList.forEach { probeOne(it) } }) {
                Text(stringResource(Res.string.mhrv_test_all), fontWeight = FontWeight.Bold, color = Color(0xFF007AFF))
            }
        }

        allSniList.forEach { sni ->
            val isEnabled = sni in enabledSet
            val status = probeMap[sni] ?: SniProbeStatus.Idle

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        val updated = if (checked) {
                            (activeList + sni).distinct()
                        } else {
                            activeList.filter { it != sni }
                        }
                        onChange(updated.joinToString("\n"))
                    },
                )
                Text(
                    sni,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Probe result badge
                when (status) {
                    is SniProbeStatus.InFlight -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF007AFF),
                        )
                    }
                    is SniProbeStatus.Ok -> {
                        Text(
                            "✔ ${status.ms} ms",
                            color = Color(0xFF34C759),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    is SniProbeStatus.Err -> {
                        Text(
                            status.message.take(15),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 10.sp,
                        )
                    }
                    SniProbeStatus.Idle -> {}
                }

                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = { probeOne(sni) },
                    enabled = status !is SniProbeStatus.InFlight,
                ) {
                    Text(stringResource(Res.string.mhrv_test), fontSize = 11.sp)
                }
            }
        }

        // Custom Add Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = customSniInput,
                onValueChange = { customSniInput = it },
                label = { Text(stringResource(Res.string.mhrv_sni_add_custom)) },
                placeholder = { Text("maps.google.com") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            )
            Button(
                onClick = {
                    val tokens = customSniInput.split(Regex("[\\s,;]+")).map { it.trim() }.filter { it.isNotEmpty() }
                    if (tokens.isNotEmpty()) {
                        val nextAll = (allSniList + tokens).distinct()
                        val nextActive = (activeList + tokens).distinct()
                        allSniList = nextAll
                        onChange(nextActive.joinToString("\n"))
                        customSniInput = ""
                    }
                },
                enabled = customSniInput.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(stringResource(Res.string.mhrv_add_deployment_id))
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    labelRes: StringResource,
    placeholder: String? = null,
    minLines: Int = 1,
    error: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = minLines == 1,
        shape = RoundedCornerShape(12.dp),
        minLines = minLines,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(labelRes: StringResource, checked: Boolean, isDark: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF34C759),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = if (isDark) Color(0xFF39393D) else Color(0xFFE9E9EA),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun Stepper(labelRes: StringResource, value: Int, min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        androidx.compose.material3.IconButton(
            onClick = { if (value - step >= min) onChange(value - step) else onChange(min) },
            enabled = value > min,
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        androidx.compose.material3.IconButton(
            onClick = { if (value + step <= max) onChange(value + step) else onChange(max) },
            enabled = value < max,
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
