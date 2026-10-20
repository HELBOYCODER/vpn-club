@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.valid7996.narcicgeteway.ui.servers

import org.jetbrains.compose.resources.StringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import dev.valid7996.narcicgeteway.ui.components.PickerField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig
import dev.valid7996.narcicgeteway.domain.config.SniSpoofProfile

/**
 * Dedicated SNI Spoof configuration sheet:
 *  • Connect IP (clean IP, default: 104.21.33.58)
 *  • Connect Port (real server port, default: 443)
 *  • Fake SNI (DPI disguise domain, default: www.sciencedirect.com)
 *  • Spoof Method (combined, fragment, random_split, etc.)
 *  • Engine Status
 *  • View Logs dialog
 *  • Profile name
 */
@Composable
fun SniSpoofSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, SniSpoofProfile) -> Unit,
    initial: SniSpoofProfile? = null,
    initialName: String = "",
    @Suppress("UNUSED_PARAMETER") candidates: List<dev.valid7996.narcicgeteway.domain.config.VpnProfile> = emptyList(),
    @Suppress("UNUSED_PARAMETER") noRoot: Boolean = true,
) {
    val initParsed = remember(initial?.link) {
        initial?.link?.trim()?.let { lnk ->
            runCatching { dev.valid7996.narcicgeteway.domain.config.ConfigParser.parse(lnk) }.getOrNull()
        }
    }

    var name by remember(initialName) { mutableStateOf(initialName.ifBlank { "SNI Spoof" }) }
    var ip by remember(initial) {
        mutableStateOf(
            initial?.cleanIp?.ifBlank { null }
                ?: initParsed?.address?.takeIf { it != "sni-spoof" && it != "127.0.0.1" }
                ?: SniSpoofConfig.defaultConnectIp
        )
    }
    var port by remember(initial) {
        mutableStateOf(
            initParsed?.port?.takeIf { it > 0 && it != SniSpoofConfig.RSTA_LOCAL_PORT }?.toString()
                ?: initial?.connectPort?.takeIf { it > 0 }?.toString()
                ?: SniSpoofConfig.defaultConnectPort.toString()
        )
    }
    var fakeSni by remember(initial) { mutableStateOf(initial?.fakeSni?.ifBlank { null } ?: SniSpoofConfig.defaultFakeSni) }
    var method by remember(initial) { mutableStateOf(SniSpoofConfig.method(initial?.method.orEmpty().ifBlank { SniSpoofConfig.defaultMethod })) }

    var showLogsDialog by remember { mutableStateOf(false) }

    val portInt = port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
    val ipOk = ip.trim().isNotBlank()
    val portOk = portInt != null
    val sniOk = fakeSni.trim().isNotBlank()
    val canSave = ipOk && portOk && sniOk

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── Title & Icon ───────────────────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF10B981).copy(alpha = 0.16f),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(Res.drawable.ic_bolt),
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(Res.string.snispoof_noroot_add_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // ── Explanation note ───────────────────────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(Res.string.snispoof_noroot_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(14.dp),
                )
            }

            // ── Connect IP (Real Server / Clean IP) ────────────────────────────────────────────
            SniField(
                value = ip,
                labelRes = Res.string.snispoof_clean_ip,
                placeholder = SniSpoofConfig.DEFAULT_CONNECT_IP,
                isError = ip.isNotBlank() && ip.trim().isBlank(),
            ) { ip = normalizeDigits(it).trim() }

            // ── Connect Port (Real Server Port) ────────────────────────────────────────────────
            SniField(
                value = port,
                labelRes = Res.string.snispoof_connect_port,
                placeholder = SniSpoofConfig.DEFAULT_CONNECT_PORT.toString(),
                numeric = true,
                isError = port.isNotEmpty() && portInt == null,
            ) { port = normalizeDigits(it).filter(Char::isDigit).take(5) }

            // ── Fake SNI (DPI Disguise Domain) ─────────────────────────────────────────────────
            SniField(
                value = fakeSni,
                labelRes = Res.string.snispoof_fake_sni,
                placeholder = SniSpoofConfig.DEFAULT_FAKE_SNI,
                isError = fakeSni.isNotBlank() && fakeSni.trim().isBlank(),
            ) { fakeSni = normalizeDigits(it).trim() }

            // ── Bypass Method Picker ───────────────────────────────────────────────────────────
            PickerField(
                label = stringResource(Res.string.snispoof_method),
                options = SniSpoofConfig.METHODS.map { it to it },
                selected = method,
                onSelect = { method = it },
            )

            // ── Remark / Profile Name (only shown when editing an existing profile) ─────────
            if (initial != null) {
                SniField(
                    value = name,
                    labelRes = Res.string.manual_remark,
                    placeholder = "SNI Spoof",
                ) { name = it }
            }

            // ── Engine Status & Log Actions ────────────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            stringResource(Res.string.snispoof_engine_status),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(Res.string.snispoof_status_ready),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981),
                        )
                    }
                    OutlinedButton(
                        onClick = { showLogsDialog = true },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(stringResource(Res.string.snispoof_view_logs), fontSize = 12.sp)
                    }
                }
            }

            // ── Save Button ────────────────────────────────────────────────────────────────────
            Button(
                enabled = canSave,
                onClick = {
                    val resolvedPort = portInt ?: SniSpoofConfig.defaultConnectPort
                    val resolvedIp = ip.trim().ifBlank { SniSpoofConfig.defaultConnectIp }
                    val resolvedSni = fakeSni.trim().ifBlank { SniSpoofConfig.defaultFakeSni }
                    val profileName = name.trim().ifBlank { "SNI Spoof" }

                    val link = initial?.link.orEmpty()

                    onSave(
                        profileName,
                        SniSpoofProfile(
                            link = link,
                            fakeSni = resolvedSni,
                            cleanIp = resolvedIp,
                            noRoot = true,
                            method = SniSpoofConfig.method(method),
                            connectPort = resolvedPort,
                        ),
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007AFF),
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(stringResource(Res.string.action_save), fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            title = {
                Text(
                    stringResource(Res.string.snispoof_view_logs),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else Color.Black,
                )
            },
            text = {
                Surface(
                    color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)
                    ) {
                        Text(
                            text = "127.0.0.1:${SniSpoofConfig.RSTA_LOCAL_PORT} -> ${ip.trim()}:${port.trim()} [sni=${fakeSni.trim()}, method=$method]\nReady to spoof TLS handshakes.",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showLogsDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF007AFF)),
                ) {
                    Text(stringResource(Res.string.action_close), fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

@Composable
private fun SniField(
    value: String,
    labelRes: StringResource,
    placeholder: String? = null,
    minLines: Int = 1,
    numeric: Boolean = false,
    isError: Boolean = false,
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
        isError = isError,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numeric) {
                androidx.compose.ui.text.input.KeyboardType.Number
            } else {
                androidx.compose.ui.text.input.KeyboardType.Ascii
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun normalizeDigits(input: String): String =
    input.map {
        when (it) {
            in '۰'..'۹' -> '0' + (it - '۰')
            in '٠'..'٩' -> '0' + (it - '٠')
            else -> it
        }
    }.joinToString("")
