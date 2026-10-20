@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.domain.config.AetherCommands
import dev.valid7996.narcicgeteway.domain.config.AetherProfile
import dev.valid7996.narcicgeteway.domain.config.LocalPorts
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.components.PickerField
import dev.valid7996.narcicgeteway.ui.components.SectionTitle
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import dev.valid7996.narcicgeteway.ui.theme.NrgGreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Full manual Aether editor, crafted faithfully after PattNG's ServerAetherActivity.
 * Features:
 * - Complete options suite (WARP over MASQUE, ECH, Fragmentation, Obfuscation, Psiphon CDN sets, Tor bridges).
 * - Live log panel with color-coded streaming output from the Rust core.
 * - Real-time endpoint scanning with cancel capability and output relay.
 * - WARP keys renewal dialog with Cloudflare API.
 * - Dual-theme support (Dark neon glassmorphism & soft light mode).
 */
@Composable
fun AetherSheet(
    initial: AetherProfile? = null,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (String, AetherProfile) -> Unit,
) {
    val platform = LocalPlatform.current
    val aether = LocalAetherActions.current
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Form state
    var name by remember { mutableStateOf(initialName) }
    var protocol by remember { mutableStateOf(initial?.protocol ?: AetherProfile.PROTO_WG) }
    var server by remember { mutableStateOf(initial?.server.orEmpty()) }
    var port by remember { mutableStateOf(initial?.serverPort?.takeIf { it > 0 }?.toString().orEmpty()) }
    var scanMode by remember { mutableStateOf(initial?.scanMode ?: AetherProfile.SCAN_BALANCED) }
    var transport by remember { mutableStateOf(initial?.transport ?: AetherProfile.TRANSPORT_H3) }
    var obfuscation by remember { mutableStateOf(initial?.obfuscation ?: AetherProfile.OBF_AUTO) }
    var ipVersion by remember { mutableStateOf(initial?.ipVersion ?: AetherProfile.IP_V4) }
    var dns by remember { mutableStateOf(initial?.dns.orEmpty()) }
    var exitLoc by remember { mutableStateOf(initial?.exitLoc.orEmpty()) }
    var targetStrategy by remember { mutableStateOf(initial?.targetStrategy.orEmpty()) }
    var fingerprint by remember { mutableStateOf(initial?.fingerprint ?: AetherProfile.FINGERPRINT_CHROME) }
    var fragment by remember { mutableStateOf(initial?.fragment ?: false) }
    var fragmentSize by remember { mutableStateOf(initial?.fragmentSize.orEmpty()) }
    var fragmentDelay by remember { mutableStateOf(initial?.fragmentDelay.orEmpty()) }
    var ech by remember { mutableStateOf(initial?.ech ?: false) }
    var echDns by remember { mutableStateOf(initial?.echDns.orEmpty()) }
    var echDomain by remember { mutableStateOf(initial?.echDomain.orEmpty()) }
    var wiwOuter by remember { mutableStateOf(initial?.wiwOuter.orEmpty()) }
    var wiwInner by remember { mutableStateOf(initial?.wiwInner.orEmpty()) }
    var tor by remember { mutableStateOf(initial?.tor ?: AetherProfile.CARRIER_OFF) }
    var torBridges by remember { mutableStateOf(initial?.torBridges ?: AetherProfile.TOR_BRIDGES_AUTO) }
    var torBridgeLines by remember { mutableStateOf(initial?.torBridgeLines.orEmpty()) }
    var torRelays by remember { mutableStateOf(initial?.torRelays ?: AetherProfile.TOR_RELAYS_AUTO) }
    var psiphon by remember { mutableStateOf(initial?.psiphon ?: AetherProfile.CARRIER_OFF) }
    var psiphonMode by remember { mutableStateOf(initial?.psiphonMode ?: AetherProfile.PSIPHON_MODE_AUTO) }
    var psiphonRegion by remember { mutableStateOf(initial?.psiphonRegion.orEmpty()) }
    var psiphonCdnIps by remember { mutableStateOf(initial?.psiphonCdnIps.orEmpty()) }
    var psiphonCdnSni by remember { mutableStateOf(initial?.psiphonCdnSni.orEmpty()) }
    var psiphonCdnSets by remember { mutableStateOf(initial?.psiphonCdnSets.orEmpty()) }
    var psiphonBeast by remember { mutableStateOf(initial?.psiphonBeast ?: true) }
    var psiphonBundledList by remember { mutableStateOf(initial?.psiphonBundledList ?: true) }
    var finalMask by remember { mutableStateOf(initial?.finalMask.orEmpty()) }
    var dialMode by remember { mutableStateOf(initial?.dialMode.orEmpty()) }
    var masqueSni by remember { mutableStateOf(initial?.masqueSni ?: AetherProfile.DEFAULT_MASQUE_SNI) }
    var command by remember { mutableStateOf(initial?.command.orEmpty()) }
    var showOther by remember { mutableStateOf(dns.isNotBlank() || exitLoc.isNotBlank() || targetStrategy.isNotBlank()) }
    var showCdnSets by remember { mutableStateOf(psiphonCdnSets.isNotBlank()) }
    var showFinalMaskPresets by remember { mutableStateOf(false) }

    // Scanner state
    var isScanning by remember { mutableStateOf(false) }
    var scanStatusText by remember { mutableStateOf<String?>(null) }
    var scanSuccess by remember { mutableStateOf<Boolean?>(null) }

    // Live log entries
    val logEntries = remember { mutableStateListOf<AetherLogUiEntry>() }
    var nextLogId by remember { mutableStateOf(1L) }

    fun addLog(priority: Int, text: String) {
        logEntries.add(AetherLogUiEntry(nextLogId++, priority, text))
        if (logEntries.size > 500) logEntries.removeAt(0)
    }

    // Keys dialog
    var showKeysDialog by remember { mutableStateOf(false) }

    val logCopiedText = stringResource(Res.string.aether_log_copied)

    // What the bundled core understands: an old one takes neither MASQUE SNI nor WireGuard over MASQUE.
    var coreInfo by remember { mutableStateOf<AetherCoreInfo?>(null) }
    LaunchedEffect(aether) { coreInfo = aether?.coreInfo() }

    val twoHops = AetherProfile.isTwoHops(protocol)
    val overMasque = protocol == AetherProfile.PROTO_MASQUE || protocol == AetherProfile.PROTO_MIM || protocol == AetherProfile.PROTO_WG_OVER_MASQUE
    val warpUsed = psiphon != AetherProfile.CARRIER_ONLY && tor != AetherProfile.CARRIER_ONLY
    val overHttp2 = overMasque && transport == AetherProfile.TRANSPORT_H2

    fun baseProfile(): AetherProfile = AetherProfile(
        protocol = protocol,
        server = server.trim(),
        serverPort = port.trim().toIntOrNull() ?: 0,
        scanMode = scanMode,
        transport = transport,
        obfuscation = obfuscation,
        ipVersion = ipVersion,
        dns = dns.trim(),
        exitLoc = exitLoc.trim(),
        fingerprint = fingerprint,
        fragment = fragment,
        fragmentSize = fragmentSize.trim(),
        fragmentDelay = fragmentDelay.trim(),
        ech = ech,
        echDns = echDns.trim(),
        echDomain = echDomain.trim(),
        wiwOuter = wiwOuter.trim(),
        wiwInner = wiwInner.trim(),
        tor = tor,
        torBridges = torBridges,
        torBridgeLines = torBridgeLines.trim(),
        torRelays = torRelays,
        psiphon = psiphon,
        psiphonMode = psiphonMode,
        psiphonRegion = psiphonRegion.trim(),
        psiphonCdnIps = psiphonCdnIps.trim(),
        psiphonCdnSni = psiphonCdnSni.trim(),
        psiphonCdnSets = psiphonCdnSets.trim(),
        psiphonBeast = psiphonBeast,
        psiphonBundledList = psiphonBundledList,
        finalMask = finalMask.trim(),
        dialMode = dialMode.trim(),
        targetStrategy = targetStrategy.trim(),
        masqueSni = masqueSni.trim(),
    )

    // The command the settings give, as the editor shows it. It is worked out on every recomposition: it used to be
    // remembered on a handful of fields, so a changed fragment, ECH or Psiphon setting left it (and the "custom"
    // flag) stale. A command equal to the one the settings give is no custom command and is not stored as one.
    val builtCmd = AetherCommands.buildArguments(baseProfile(), LocalPorts.AETHER_SOCKS).joinToString(" ")
    val isCustom = command.isNotBlank() && command.trim() != builtCmd
    fun buildProfile(): AetherProfile = baseProfile().copy(command = if (isCustom) command.trim() else "")

    // PattNG's editor refuses what the core would refuse; so does this one, with the reason.
    val problem = AetherCommands.problemOf(buildProfile())
    val problemText = when (problem) {
        AetherCommands.Problem.CARRIER_NEEDS_MASQUE -> stringResource(Res.string.aether_problem_carrier_masque)
        AetherCommands.Problem.TOR_PSIPHON_CONFLICT -> stringResource(Res.string.aether_problem_tor_psiphon)
        AetherCommands.Problem.TOR_BRIDGES_MISSING -> stringResource(Res.string.aether_problem_bridges_missing)
        null -> null
    }

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
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.aether_add_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                    )
                    Text(
                        stringResource(Res.string.aether_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color(0xFFA0B0C4) else Color(0xFF64748B),
                    )
                }
            }

            // Renew Keys button (PattNG top action)
            OutlinedButton(
                onClick = { showKeysDialog = true },
                enabled = !isScanning,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_bolt), null, Modifier.size(18.dp), tint = if (isDark) NrgGreen else Color(0xFF0D9488))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.aether_action_renew_key), fontWeight = FontWeight.SemiBold)
            }

            coreInfo?.takeIf { !it.masqueSni || !it.wgOverMasque }?.let { info ->
                Text(
                    stringResource(Res.string.aether_core_old_note, info.version.ifEmpty { "?" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (protocol == AetherProfile.PROTO_WG_OVER_MASQUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            // Remarks field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.manual_remark)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // Protocol & Tunnel shape
            if (warpUsed) {
                SectionTitle(stringResource(Res.string.aether_lab_protocol))

                PickerField(
                    label = stringResource(Res.string.aether_lab_protocol),
                    options = AetherProfile.protocols,
                    selected = protocol,
                    onSelect = { protocol = it },
                )

                if (overMasque) {
                    PickerField(
                        label = stringResource(Res.string.aether_lab_transport),
                        options = AetherProfile.transports,
                        selected = transport,
                        onSelect = { transport = it },
                    )
                }

                // MASQUE SNI: the server name the MASQUE handshakes put in their ClientHello, on either carrier and
                // both hops. Cores older than 2.3 do not take it; the note above says so.
                if (overMasque) {
                    OutlinedTextField(
                        value = masqueSni,
                        onValueChange = { masqueSni = it },
                        label = { Text(stringResource(Res.string.aether_masque_sni)) },
                        placeholder = { Text(AetherProfile.DEFAULT_MASQUE_SNI) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (overHttp2) {
                    SettingToggleRow(
                        title = stringResource(Res.string.aether_lab_fragment),
                        checked = fragment,
                        onCheckedChange = { fragment = it },
                    )
                    if (fragment) {
                        OutlinedTextField(
                            value = fragmentSize,
                            onValueChange = { fragmentSize = it },
                            label = { Text(stringResource(Res.string.aether_lab_fragment_size)) },
                            placeholder = { Text("8-16") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = fragmentDelay,
                            onValueChange = { fragmentDelay = it },
                            label = { Text(stringResource(Res.string.aether_lab_fragment_delay)) },
                            placeholder = { Text("2-10") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (overMasque) {
                    SettingToggleRow(
                        title = stringResource(Res.string.aether_lab_ech),
                        
                        checked = ech,
                        onCheckedChange = { ech = it },
                    )
                    if (ech) {
                        PickerField(
                            label = stringResource(Res.string.aether_lab_ech_dns),
                            options = AetherProfile.defaultEchDnsOptions.map { it to it },
                            selected = echDns.ifBlank { AetherProfile.DEFAULT_ECH_DNS },
                            onSelect = { echDns = it },
                        )
                        PickerField(
                            label = stringResource(Res.string.aether_lab_ech_domain),
                            options = AetherProfile.defaultEchDomainOptions.map { it to it },
                            selected = echDomain.ifBlank { AetherProfile.DEFAULT_ECH_DOMAIN },
                            onSelect = { echDomain = it },
                        )
                    }
                }

                PickerField(
                    label = stringResource(Res.string.aether_lab_scan_mode),
                    options = AetherProfile.scanModes,
                    selected = scanMode,
                    onSelect = { scanMode = it },
                )

                if (!overHttp2) {
                    PickerField(
                        label = stringResource(Res.string.aether_lab_obfuscation),
                        options = AetherProfile.obfuscations,
                        selected = obfuscation,
                        onSelect = { obfuscation = it },
                    )
                }

                if (overMasque) {
                    PickerField(
                        label = stringResource(Res.string.aether_lab_fingerprint),
                        options = AetherProfile.fingerprints,
                        selected = fingerprint,
                        onSelect = { fingerprint = it },
                    )
                }
            }

            // Exit-Node Section
            SectionTitle(stringResource(Res.string.aether_final_mask))

            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = finalMask,
                    onValueChange = { finalMask = it },
                    label = { Text(stringResource(Res.string.aether_final_mask)) },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        IconButton(onClick = { showFinalMaskPresets = true }) {
                            Icon(painterResource(Res.drawable.ic_keyboard_arrow_down), null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = showFinalMaskPresets,
                    onDismissRequest = { showFinalMaskPresets = false },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    AetherProfile.finalMaskPresets.forEach { (label, value) ->
                        DropdownMenuItem(
                            text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                finalMask = value
                                showFinalMaskPresets = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = dialMode,
                onValueChange = { dialMode = it },
                label = { Text(stringResource(Res.string.aether_dial_mode)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // Psiphon Carrier Section
            SectionTitle(stringResource(Res.string.aether_psiphon))
            PickerField(
                label = stringResource(Res.string.aether_psiphon),
                options = AetherProfile.carrierModes,
                selected = psiphon,
                onSelect = { psiphon = it },
            )
            if (psiphon != AetherProfile.CARRIER_OFF) {
                PickerField(
                    label = stringResource(Res.string.aether_psiphon_connection),
                    options = AetherProfile.psiphonModes,
                    selected = psiphonMode,
                    onSelect = { psiphonMode = it },
                )
                if (psiphonMode != AetherProfile.PSIPHON_MODE_DIRECT) {
                    OutlinedTextField(
                        value = psiphonCdnIps,
                        onValueChange = { psiphonCdnIps = it },
                        label = { Text(stringResource(Res.string.aether_psiphon_cdn_ips)) },
                        supportingText = { Text(stringResource(Res.string.psiphon_cdn_hint)) },
                        minLines = 1,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = psiphonCdnSni,
                        onValueChange = { psiphonCdnSni = it },
                        label = { Text(stringResource(Res.string.aether_psiphon_cdn_sni)) },
                        minLines = 1,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // CDN Sets collapsible section
                    SettingToggleRow(
                        title = stringResource(Res.string.aether_psiphon_cdn_sets),
                        
                        checked = showCdnSets,
                        onCheckedChange = { showCdnSets = it },
                    )
                    if (showCdnSets) {
                        val activeSets = psiphonCdnSets.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AetherProfile.psiphonCdnSets.forEach { (key, label) ->
                                    val isSet = key in activeSets
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            val next = if (isSet) activeSets - key else activeSets + key
                                            psiphonCdnSets = next.joinToString(",")
                                        }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(checked = isSet, onCheckedChange = null)
                                        Spacer(Modifier.width(10.dp))
                                        Text(label, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                PsiphonCountryField(
                    selected = psiphonRegion,
                    onSelect = { psiphonRegion = it },
                )

                // Beast mode: every protocol on every server at once; Psiphon also remembers the route that worked.
                SettingToggleRow(
                    title = stringResource(Res.string.psiphon_beast_title),
                    summary = stringResource(Res.string.psiphon_beast_summary),
                    checked = psiphonBeast,
                    onCheckedChange = { psiphonBeast = it },
                )

                SettingToggleRow(
                    title = stringResource(Res.string.aether_psiphon_bundled_list),
                    summary = stringResource(Res.string.aether_psiphon_bundled_list_summary),
                    checked = psiphonBundledList,
                    onCheckedChange = { psiphonBundledList = it },
                )

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val ok = aether?.clearPsiphonData() ?: false
                            addLog(
                                if (ok) 4 else 6,
                                getString(if (ok) Res.string.aether_psiphon_cache_cleared else Res.string.aether_psiphon_cache_clear_failed),
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.aether_psiphon_clear_cache))
                }
            }

            // Tor Carrier Section
            SectionTitle(stringResource(Res.string.aether_tor))
            PickerField(
                label = stringResource(Res.string.aether_tor),
                options = AetherProfile.carrierModes,
                selected = tor,
                onSelect = { tor = it },
            )
            if (tor != AetherProfile.CARRIER_OFF) {
                PickerField(
                    label = stringResource(Res.string.aether_tor_bridges),
                    options = AetherProfile.torBridgeModes,
                    selected = torBridges,
                    onSelect = { torBridges = it },
                )
                if (torBridges == AetherProfile.TOR_BRIDGES_AUTO || torBridges == AetherProfile.TOR_BRIDGES_FIRST) {
                    PickerField(
                        label = stringResource(Res.string.aether_tor_relays),
                        options = AetherProfile.torRelayModes,
                        selected = torRelays,
                        onSelect = { torRelays = it },
                    )
                }
                if (torBridges == AetherProfile.TOR_BRIDGES_OWN) {
                    OutlinedTextField(
                        value = torBridgeLines,
                        onValueChange = { torBridgeLines = it },
                        label = { Text(stringResource(Res.string.aether_tor_bridge_lines)) },
                        placeholder = { Text(stringResource(Res.string.aether_tor_bridge_lines_hint)) },
                        minLines = 2,
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Endpoint & Scanning Section
            if (warpUsed) {
                SectionTitle(stringResource(Res.string.aether_lab_endpoint))

                if (twoHops) {
                    OutlinedTextField(
                        value = wiwOuter,
                        onValueChange = { wiwOuter = it },
                        label = { Text(stringResource(Res.string.aether_lab_wiw_outer)) },
                        placeholder = { Text(stringResource(Res.string.aether_hint_endpoint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = wiwInner,
                        onValueChange = { wiwInner = it },
                        label = { Text(stringResource(Res.string.aether_lab_wiw_inner)) },
                        placeholder = { Text(stringResource(Res.string.aether_hint_endpoint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = { Text(stringResource(Res.string.aether_lab_endpoint)) },
                        placeholder = { Text(stringResource(Res.string.aether_hint_endpoint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(Res.string.aether_lab_port)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                PickerField(
                    label = stringResource(Res.string.aether_lab_ip_version),
                    options = AetherProfile.ipVersions,
                    selected = ipVersion,
                    onSelect = { ipVersion = it },
                )

                // Scan Action Row
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            val current = buildProfile()
                            isScanning = true
                            scanSuccess = null
                            scanStatusText = null
                            scope.launch {
                                addLog(4, getString(Res.string.aether_scan_started, current.protocol.uppercase()))
                                val result = aether?.scan(current) { line ->
                                    val prio = when {
                                        line.startsWith("E/") || line.startsWith("Error:") -> 6
                                        line.startsWith("W/") || line.startsWith("Warn:") -> 5
                                        else -> 4
                                    }
                                    addLog(prio, line)
                                }
                                isScanning = false
                                if (result != null) {
                                    scanSuccess = true
                                    val found = getString(Res.string.aether_scan_found, result.endpoint)
                                    scanStatusText = found
                                    addLog(4, found)
                                    if (twoHops) {
                                        // WireGuard over MASQUE names its gateway alone: the WireGuard endpoint inside
                                        // it is the profile's own, so only the outer field is filled then.
                                        wiwOuter = result.endpoint
                                        result.innerHop?.let { wiwInner = it }
                                    } else {
                                        val text = result.endpoint
                                        val sep = text.lastIndexOf(':')
                                        if (sep > 0) {
                                            server = text.substring(0, sep).removeSurrounding("[", "]")
                                            port = text.substring(sep + 1)
                                        }
                                    }
                                } else {
                                    scanSuccess = false
                                    val none = getString(Res.string.aether_scan_none)
                                    scanStatusText = none
                                    addLog(5, none)
                                }
                            }
                        },
                        enabled = !isScanning,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) NrgGreen else Color(0xFF0D9488),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.aether_action_scanning))
                        } else {
                            Icon(painterResource(Res.drawable.ic_bolt), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.aether_action_scan), fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isScanning) {
                        OutlinedButton(
                            onClick = {
                                aether?.cancelScan()
                                isScanning = false
                                scope.launch { addLog(5, getString(Res.string.aether_scan_cancelled)) }
                            },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.height(48.dp),
                        ) {
                            Text(stringResource(Res.string.action_cancel))
                        }
                    }
                }

                scanStatusText?.let { msg ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (scanSuccess == true) (if (isDark) Color(0xFF60E0B0) else Color(0xFF059669)) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            // Other Settings Collapsible
            SettingToggleRow(
                title = stringResource(Res.string.aether_other_settings),
                checked = showOther,
                onCheckedChange = { showOther = it },
            )
            if (showOther) {
                if (warpUsed) {
                    OutlinedTextField(
                        value = dns,
                        onValueChange = { dns = it },
                        label = { Text(stringResource(Res.string.aether_lab_dns)) },
                        placeholder = { Text("e.g. udp://1.1.1.1") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = exitLoc,
                        onValueChange = { exitLoc = it },
                        label = { Text(stringResource(Res.string.aether_lab_exit_location)) },
                        placeholder = { Text("e.g. DE") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PickerField(
                    label = stringResource(Res.string.aether_target_strategy),
                    options = AetherProfile.targetStrategies.map { it to it },
                    selected = targetStrategy.ifBlank { "AsIs" },
                    onSelect = { targetStrategy = it },
                )
            }

            // Custom command (Expert mode)
            OutlinedTextField(
                value = command.ifBlank { builtCmd },
                onValueChange = { command = it },
                label = { Text(stringResource(Res.string.aether_lab_command)) },
                supportingText = if (isCustom) {
                    { Text(stringResource(Res.string.aether_command_custom), color = MaterialTheme.colorScheme.primary) }
                } else null,
                minLines = 2,
                maxLines = 6,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (isCustom) {
                TextButton(
                    onClick = { command = "" },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(Res.string.aether_action_use_settings))
                }
            }

            // The panel carries its own title and copy button.
            AetherLiveLogPanel(
                entries = logEntries,
                onCopy = {
                    val full = logEntries.joinToString("\n") { it.text }
                    platform.copyToClipboard(full)
                    platform.toast(logCopiedText)
                },
                isDark = isDark,
            )

            Spacer(Modifier.height(8.dp))

            problemText?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            // Save button
            Button(
                onClick = {
                    onSave(name.trim(), buildProfile())
                },
                enabled = problem == null,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007AFF),
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_bolt), null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(Res.string.action_save), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    // WARP Keys Page (PattNG-faithful full page)
    if (showKeysDialog && aether != null) {
        AetherKeysPage(
            actions = aether,
            onDismiss = { showKeysDialog = false },
            onLog = { prio, line -> addLog(prio, line) },
            isDark = isDark,
        )
    }
}

/**
 * Live Log Panel matching PattNG: monospace, colored priority text, scrollable surface with copy button.
 */
@Composable
private fun AetherLiveLogPanel(
    entries: List<AetherLogUiEntry>,
    onCopy: () -> Unit,
    isDark: Boolean,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
    }

    val panelBorder = if (isDark) {
        Brush.horizontalGradient(listOf(Color(0xFF20D8C0).copy(alpha = 0.40f), Color(0xFF2088FF).copy(alpha = 0.25f)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF0D9488).copy(alpha = 0.25f), Color(0xFF3B82F6).copy(alpha = 0.15f)))
    }
    val panelBg = if (isDark) Color(0xFF06101E) else Color(0xFFF1F5F9)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.aether_log_title),
                style = MaterialTheme.typography.titleSmall,
                color = if (isDark) Color(0xFFC0C0D0) else Color(0xFF475569),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCopy, enabled = entries.isNotEmpty()) {
                Icon(painterResource(Res.drawable.ic_content_paste), null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.logs_copy), style = MaterialTheme.typography.labelSmall)
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = panelBg,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .border(1.dp, panelBorder, RoundedCornerShape(18.dp)),
        ) {
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.aether_log_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        val textColor = when {
                            entry.priority >= 6 -> MaterialTheme.colorScheme.error
                            entry.priority == 5 -> Color(0xFFF59E0B)
                            entry.priority == 4 -> if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                            else -> if (isDark) Color(0xFFA0B0C4) else Color(0xFF475569)
                        }
                        Text(
                            text = entry.text,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = textColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingToggleRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!summary.isNullOrBlank()) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * The WARP keys page, faithful to PattNG's ServerAetherKeysActivity: a radio group for the key
 * kind, the enroll address, fragment / ECH / fingerprint settings, the exit-node fields, a live
 * log panel and the renew button. Its settings are its own: no profile's settings reach them.
 */
@Composable
private fun AetherKeysPage(
    actions: AetherActions,
    onDismiss: () -> Unit,
    onLog: (Int, String) -> Unit,
    isDark: Boolean,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Key settings (PattNG AetherKeysSettings)
    var keyKind by remember { mutableStateOf("all") }
    var enrollAddress by remember { mutableStateOf(AetherProfile.DEFAULT_ENROLL_ADDRESS) }
    var keyFragment by remember { mutableStateOf(false) }
    var keyFragmentSize by remember { mutableStateOf("") }
    var keyFragmentDelay by remember { mutableStateOf("") }
    var keyEch by remember { mutableStateOf(false) }
    var keyEchDns by remember { mutableStateOf(AetherProfile.DEFAULT_ECH_DNS) }
    var keyEchDomain by remember { mutableStateOf(AetherProfile.DEFAULT_ECH_DOMAIN) }
    var keyFingerprint by remember { mutableStateOf(AetherProfile.FINGERPRINT_CHROME) }
    var keyFinalMask by remember { mutableStateOf("") }
    var keyDialMode by remember { mutableStateOf("") }
    var showKeyFinalMaskPresets by remember { mutableStateOf(false) }

    var isRenewing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var keysList by remember { mutableStateOf<List<AetherKeyUiEntry>>(emptyList()) }

    val platform = LocalPlatform.current
    val logEntries = remember { mutableStateListOf<AetherLogUiEntry>() }
    var nextLogId by remember { mutableStateOf(1L) }
    fun keyLog(priority: Int, text: String) {
        logEntries.add(AetherLogUiEntry(nextLogId++, priority, text))
        if (logEntries.size > 500) logEntries.removeAt(0)
    }

    LaunchedEffect(Unit) {
        keysList = actions.keys()
    }

    val kindLabels = AetherProfile.keyKinds.map { (kind, label) ->
        kind to if (kind == "all") stringResource(Res.string.aether_key_kind_all) else label
    }
    val statusReady = stringResource(Res.string.aether_key_ready)
    val statusMissing = stringResource(Res.string.aether_key_missing)

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
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(Res.string.aether_action_renew_key),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A),
                modifier = Modifier.padding(vertical = 4.dp),
            )

            // Current key status
            if (keysList.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        keysList.forEach { entry ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(entry.file, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (entry.deviceId != null) statusReady else statusMissing,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (entry.deviceId != null) (if (isDark) Color(0xFF60E0B0) else Color(0xFF059669)) else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            // Key kind radio group
            SectionTitle(stringResource(Res.string.aether_key_kind))
            Column {
                kindLabels.forEach { (kind, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isRenewing) { keyKind = kind }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = keyKind == kind,
                            onClick = null,
                            enabled = !isRenewing,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (isDark) Color(0xFFE0E0F0) else Color(0xFF0F172A))
                    }
                }
            }

            // Enroll address
            OutlinedTextField(
                value = enrollAddress,
                onValueChange = { enrollAddress = it },
                label = { Text(stringResource(Res.string.aether_enroll_address)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            // Fragment
            SettingToggleRow(
                title = stringResource(Res.string.aether_lab_fragment),
                checked = keyFragment,
                onCheckedChange = { keyFragment = it },
            )
            if (keyFragment) {
                OutlinedTextField(
                    value = keyFragmentSize,
                    onValueChange = { keyFragmentSize = it },
                    label = { Text(stringResource(Res.string.aether_lab_fragment_size)) },
                    placeholder = { Text(stringResource(Res.string.aether_hint_fragment_size)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = keyFragmentDelay,
                    onValueChange = { keyFragmentDelay = it },
                    label = { Text(stringResource(Res.string.aether_lab_fragment_delay)) },
                    placeholder = { Text(stringResource(Res.string.aether_hint_fragment_delay)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ECH
            SettingToggleRow(
                title = stringResource(Res.string.aether_lab_ech),
                summary = stringResource(Res.string.aether_keys_hint_ech),
                checked = keyEch,
                onCheckedChange = { keyEch = it },
            )
            if (keyEch) {
                PickerField(
                    label = stringResource(Res.string.aether_lab_ech_dns),
                    options = AetherProfile.defaultEchDnsOptions.map { it to it },
                    selected = keyEchDns,
                    onSelect = { keyEchDns = it },
                )
                PickerField(
                    label = stringResource(Res.string.aether_lab_ech_domain),
                    options = AetherProfile.defaultEchDomainOptions.map { it to it },
                    selected = keyEchDomain,
                    onSelect = { keyEchDomain = it },
                )
            }

            // Fingerprint
            PickerField(
                label = stringResource(Res.string.aether_lab_fingerprint),
                options = AetherProfile.fingerprints,
                selected = keyFingerprint,
                onSelect = { keyFingerprint = it },
            )

            // Exit-node
            SectionTitle(stringResource(Res.string.aether_exit_node))
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = keyFinalMask,
                    onValueChange = { keyFinalMask = it },
                    label = { Text(stringResource(Res.string.aether_final_mask)) },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        IconButton(onClick = { showKeyFinalMaskPresets = true }) {
                            Icon(painterResource(Res.drawable.ic_keyboard_arrow_down), null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = showKeyFinalMaskPresets,
                    onDismissRequest = { showKeyFinalMaskPresets = false },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    AetherProfile.finalMaskPresets.forEach { (label, value) ->
                        DropdownMenuItem(
                            text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                keyFinalMask = value
                                showKeyFinalMaskPresets = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = keyDialMode,
                onValueChange = { keyDialMode = it },
                label = { Text(stringResource(Res.string.aether_dial_mode)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // Renew button
            Button(
                onClick = {
                    isRenewing = true
                    statusMessage = null
                    scope.launch {
                        keyLog(4, getString(Res.string.aether_key_renewing, keyKind))
                        // Every setting of the page reaches the run: they were collected here and never sent.
                        val request = AetherCommands.KeysRequest(
                            kind = keyKind,
                            enrollAddress = enrollAddress,
                            fragment = keyFragment,
                            fragmentSize = keyFragmentSize,
                            fragmentDelay = keyFragmentDelay,
                            ech = keyEch,
                            echDns = keyEchDns,
                            echDomain = keyEchDomain,
                            fingerprint = keyFingerprint,
                            finalMask = keyFinalMask,
                            dialMode = keyDialMode,
                        )
                        val ok = actions.registerKeys(request) { line ->
                            keyLog(4, line)
                            onLog(4, line)
                        }
                        isRenewing = false
                        keysList = actions.keys()
                        if (ok) {
                            val ready = getString(Res.string.aether_keys_new_ready)
                            keyLog(4, ready)
                            onLog(4, ready)
                            statusMessage = getString(Res.string.aether_key_ok)
                        } else {
                            val unfinished = getString(Res.string.aether_keys_not_finished)
                            keyLog(6, unfinished)
                            onLog(6, unfinished)
                            statusMessage = getString(Res.string.aether_key_failed)
                        }
                    }
                },
                enabled = !isRenewing,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007AFF),
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (isRenewing) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.aether_registering))
                } else {
                    Text(stringResource(Res.string.aether_action_renew_key), fontWeight = FontWeight.Bold)
                }
            }

            statusMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color(0xFF60E0B0) else Color(0xFF059669),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            // Log panel (PattNG-faithful)
            AetherLiveLogPanel(
                entries = logEntries,
                onCopy = {
                    val full = logEntries.joinToString("\n") { it.text }
                    platform.copyToClipboard(full)
                },
                isDark = isDark,
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}
