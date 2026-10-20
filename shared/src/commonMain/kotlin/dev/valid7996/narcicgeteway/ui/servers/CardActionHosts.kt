@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.domain.config.ConfigParser
import dev.valid7996.narcicgeteway.domain.config.ProfileSource
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The dialogs behind a card's three-dot menu: the editor of whichever engine the config runs on,
 * the rename dialog and the move-to-group dialog. The servers screen and the home list hand their
 * targets in; everything the editors need is computed here from the repository.
 */
@Composable
internal fun CardActionHosts(
    repository: ConfigRepository,
    editTarget: VpnProfile?,
    renameTarget: VpnProfile?,
    moveTarget: VpnProfile?,
    onDismissEdit: () -> Unit,
    onDismissRename: () -> Unit,
    onDismissMove: () -> Unit,
) {
    val platform = LocalPlatform.current
    val savedToast = stringResource(Res.string.saved)
    val editUnsupported = stringResource(Res.string.edit_unsupported)
    val configInvalid = stringResource(Res.string.config_invalid)
    val customJsonInvalid = stringResource(Res.string.custom_json_invalid)
    val singBoxJsonInvalid = stringResource(Res.string.singbox_json_invalid)

    fun toastSaved() = platform.toast(savedToast)

    val servers = repository.profiles.value.filterNot { it.isLocked }
    val chainCandidates = servers.filter {
        it.rawPayload() != null && (!it.isCustom || it.isServerless) && !it.isManagedTunnel && !it.isProxyChain &&
            !it.isSingBoxConfig
    }
    val spoofCandidates = servers.filter {
        it.rawPayload() != null && !it.isManagedTunnel && !it.isProxyChain &&
            !it.isCrossChain && !it.isSniSpoof
    }
    val crossCarriers = servers.filter { it.canCarryChain && !it.isCrossChain }
    val crossExits = servers.filter { it.canDialThroughProxy && !it.isCrossChain }
    val subscriptions = repository.subscriptions.value

    val udpMismatchTemplate = stringResource(Res.string.crosschain_udp_unsupported)
    val crossPairError: (VpnProfile, VpnProfile) -> String? = { inner, outer ->
        repository.udpMismatch(inner, outer)?.let {
            udpMismatchTemplate.replace("%1\$s", it.protocol).replace("%2\$s", it.carrier)
        }
    }

    editTarget?.let { target ->
        when {
            target.isSingBox || target.isSingBoxConfig -> {
                val source = target.source
                RawJsonSheet(
                    title = stringResource(Res.string.singbox_json_title),
                    initial = when (source) {
                        is ProfileSource.SingBox -> source.json
                        is ProfileSource.SingBoxConfig -> source.json
                        else -> ""
                    },
                    onDismiss = onDismissEdit,
                    onSave = { text ->
                        repository.updateSingBox(target.id, text).fold(
                            onSuccess = {
                                toastSaved()
                                onDismissEdit()
                                null
                            },
                            onFailure = { it.message?.take(160)?.ifBlank { null } ?: singBoxJsonInvalid },
                        )
                    },
                    flavour = JsonFlavour.SingBox,
                )
            }
            target.isCustom -> {
                RawJsonSheet(
                    title = stringResource(Res.string.custom_json_title),
                    initial = target.rawPayload().orEmpty(),
                    onDismiss = onDismissEdit,
                    onSave = { text ->
                        repository.updateRawJson(target.id, text).fold(
                            onSuccess = {
                                toastSaved()
                                onDismissEdit()
                                null
                            },
                            onFailure = { it.message?.take(160)?.ifBlank { null } ?: customJsonInvalid },
                        )
                    },
                )
            }

            target.isPsiphon -> {
                PsiphonSheet(
                    initial = target.psiphonSettings(),
                    initialName = target.name,
                    onDismiss = onDismissEdit,
                    onSave = { name, settings ->
                        repository.addPsiphon(settings, name, id = target.id)
                        toastSaved()
                        onDismissEdit()
                    },
                )
            }
            target.isDnsTunnel -> {
                DnsTunnelSheet(
                    initial = target.dnsTunnelSettings(),
                    initialName = target.name,
                    onDismiss = onDismissEdit,
                    onSave = { name, settings ->
                        repository.addDnsTunnel(settings, name, id = target.id)
                        toastSaved()
                        onDismissEdit()
                    },
                )
            }
            target.isMasterDns -> {
                MasterDnsSheet(
                    initial = target.masterDnsSettings(),
                    initialName = target.name,
                    onDismiss = onDismissEdit,
                    onSave = { name, settings ->
                        repository.addMasterDns(settings, name, id = target.id)
                        toastSaved()
                        onDismissEdit()
                    },
                )
            }
            target.isOpenConnect -> {
                OpenConnectSheet(
                    initial = target.openConnectSettings(),
                    initialName = target.name,
                    onDismiss = onDismissEdit,
                    onSave = { name, settings ->
                        repository.addOpenConnect(settings, name, id = target.id)
                        toastSaved()
                        onDismissEdit()
                    },
                )
            }
            target.isAether -> {
                AetherSheet(
                    initial = target.aetherSettings(),
                    initialName = target.name,
                    onDismiss = onDismissEdit,
                    onSave = { name, settings ->
                        repository.addAether(settings, name, id = target.id)
                        toastSaved()
                        onDismissEdit()
                    },
                )
            }
            target.isIkev2 -> {
                Ikev2Sheet(
                    initial = target.ikev2Settings(),
                    initialName = target.name,
                    onDismiss = onDismissEdit,
                    onSave = { name, settings ->
                        repository.addIkev2(settings, name, id = target.id)
                        toastSaved()
                        onDismissEdit()
                    },
                )
            }
            target.isSniSpoof -> {
                SniSpoofSheet(
                    candidates = spoofCandidates,
                    initial = target.sniSpoofSettings(),
                    initialName = target.name,
                    onDismiss = onDismissEdit,
                    onSave = { name, settings ->
                        repository.addSniSpoof(settings, name, id = target.id)
                        toastSaved()
                        onDismissEdit()
                    },
                )
            }
            target.isProxyChain -> {
                ProxyChainSheet(
                    candidates = chainCandidates,
                    initialName = target.name,
                    initialMemberIds = target.proxyChainSettings().orEmpty(),
                    onDismiss = onDismissEdit,
                    onSave = { name, memberIds ->
                        repository.addProxyChain(memberIds, name, id = target.id)
                        toastSaved()
                        onDismissEdit()
                    },
                )
            }
            target.isCrossChain -> {
                val (inId, outId) = target.crossChainSettings() ?: ("" to "")
                CrossChainSheet(
                    exits = crossExits,
                    carriers = crossCarriers,
                    pairError = crossPairError,
                    initialName = target.name,
                    initialInnerId = inId,
                    initialOuterId = outId,
                    onDismiss = onDismissEdit,
                    onSave = { name, innerId, outerId ->
                        repository.addCrossChain(innerId, outerId, name, id = target.id)
                        toastSaved()
                        onDismissEdit()
                    },
                )
            }
            target.isSsh -> {
                SshSheet(
                    initial = target.sshSettings(),
                    initialName = target.name,
                    onDismiss = onDismissEdit,
                    onSave = { name, settings ->
                        repository.update(
                            VpnProfile.fromSsh(settings = settings, id = target.id, addedAt = target.addedAt, name = name),
                        )
                        toastSaved()
                        onDismissEdit()
                    },
                )
            }
            else -> {
                val parsed = remember(target.id) {
                    target.rawPayload()?.let { payload ->
                        runCatching { ConfigParser.parse(payload) }.getOrNull()
                    }
                }
                if (parsed == null) {
                    LaunchedEffect(target.id) {
                        platform.toast(editUnsupported)
                        onDismissEdit()
                    }
                } else {
                    ManualConfigSheet(
                        initial = parsed,
                        onDismiss = onDismissEdit,
                        onSave = { link ->
                            val updated = runCatching {
                                VpnProfile.fromLink(
                                    link = link,
                                    id = target.id,
                                    addedAt = target.addedAt,
                                    subscriptionId = target.subscriptionId,
                                )
                            }.getOrNull()
                            if (updated == null) {
                                platform.toast(configInvalid)
                            } else {
                                repository.update(updated.copy(lastPingMs = target.lastPingMs))
                                toastSaved()
                            }
                            onDismissEdit()
                        },
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        var name by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = onDismissRename,
            shape = RoundedCornerShape(14.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            title = {
                Text(
                    stringResource(Res.string.servers_rename),
                    color = if (isDark) Color.White else Color.Black,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF007AFF),
                        unfocusedBorderColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA),
                        focusedContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                        unfocusedContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.rename(target.id, name.trim().ifBlank { target.name })
                        onDismissRename()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF007AFF),
                    ),
                ) {
                    Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissRename,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF007AFF),
                    ),
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    moveTarget?.let { target ->
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        AlertDialog(
            onDismissRequest = onDismissMove,
            shape = RoundedCornerShape(14.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            title = {
                Text(
                    stringResource(Res.string.groups_move),
                    color = if (isDark) Color.White else Color.Black,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                androidx.compose.foundation.layout.Column {
                    listOf("" to stringResource(Res.string.groups_manual)).plus(
                        subscriptions.map { it.id to it.name },
                    ).forEach { (id, label) ->
                        TextButton(
                            onClick = {
                                repository.moveToGroup(target.id, id)
                                onDismissMove()
                            },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = if (target.subscriptionId == id) Color(0xFF007AFF) else (if (isDark) Color.White else Color.Black),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                fontWeight = if (target.subscriptionId == id) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onDismissMove,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF007AFF),
                    ),
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}
