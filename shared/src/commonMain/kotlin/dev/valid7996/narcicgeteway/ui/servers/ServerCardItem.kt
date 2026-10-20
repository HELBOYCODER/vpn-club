@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.TextUnit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valid7996.narcicgeteway.platform.currentTimeMillis
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.ui.components.FlagBadge
import dev.valid7996.narcicgeteway.ui.format.formatBytes
import dev.valid7996.narcicgeteway.ui.theme.Personalization
import dev.valid7996.narcicgeteway.ui.theme.NrgGreen
import dev.valid7996.narcicgeteway.ui.theme.pingColor
import dev.valid7996.narcicgeteway.ui.theme.readableOn
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal fun extractCountryCode(text: String): String? {
    var i = 0
    while (i <= text.length - 4) {
        if (text[i] == '\uD83C' && text[i + 1] in '\uDDE6'..'\uDDFF' &&
            text[i + 2] == '\uD83C' && text[i + 3] in '\uDDE6'..'\uDDFF') {
            val c1 = ('A'.code + (text[i + 1].code - 0xDDE6)).toChar()
            val c2 = ('A'.code + (text[i + 3].code - 0xDDE6)).toChar()
            return "$c1$c2"
        }
        i++
    }
    return null
}

internal fun cleanProfileName(name: String): String {
    val sb = StringBuilder(name.length)
    var i = 0
    while (i < name.length) {
        if (i <= name.length - 4 &&
            name[i] == '\uD83C' && name[i + 1] in '\uDDE6'..'\uDDFF' &&
            name[i + 2] == '\uD83C' && name[i + 3] in '\uDDE6'..'\uDDFF') {
            i += 4
        } else {
            sb.append(name[i])
            i++
        }
    }
    return sb.toString().replace(Regex("""\s+"""), " ").trim().ifBlank { name }
}

/**
 * The server card of the servers list and the home list alike: the side rail, the active dot, the
 * ping chip and the actions menu. The share actions stay behind [ServersUiConfig.SHOW_SHARE_ACTIONS]
 * so they can come back untouched.
 */
@Composable
internal fun ServerCard(
    profile: VpnProfile,
    active: Boolean,
    subscriptionName: String?,
    onClick: () -> Unit,
    onShare: () -> Unit = {},
    onShareLocked: (() -> Unit)? = null,
    onShareQr: () -> Unit = {},

    shareLink: String? = null,
    onRename: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,

    onMoveGroup: (() -> Unit)? = null,

    selecting: Boolean = false,
    isSelected: Boolean = false,
    onPingTcp: () -> Unit = {},
    onPingReal: () -> Unit = {},
    onDelete: () -> Unit,
    personalization: Personalization = Personalization.Default,
    columns: Int = 1,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isSelectedCard = selecting && isSelected
    val isAzkaban = personalization.themeProfileId == "azkaban"
    val isNoir = personalization.themeProfileId == "noir"
    val isBlossom = personalization.themeProfileId == "blossom"
    val activeColor = if (personalization.dynamicColor) MaterialTheme.colorScheme.primary else (personalization.serverActiveColor ?: NrgGreen)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val container = if (isSelectedCard) {
        MaterialTheme.colorScheme.secondaryContainer
    } else if (active) {
        activeColor
    } else if (personalization.amoledBlack) {
        Color(0xFF000000)
    } else if (isBlossom) {
        Color(0xFF180A12)
    } else if (isNoir) {
        Color(0xFF121214)
    } else if (isAzkaban) {
        Color(0xFF091322)
    } else {
        personalization.serverCardColor
            ?: if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    }
    val frame = when {
        isSelectedCard -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
        active -> activeColor.copy(alpha = 0.95f)
        personalization.dynamicColor -> MaterialTheme.colorScheme.primary.copy(alpha = if (personalization.amoledBlack) 0.55f else 0.35f)
        isBlossom -> Color(0xFFF472B6).copy(alpha = if (personalization.amoledBlack) 0.55f else 0.32f)
        isNoir -> Color(0xFFFFFFFF).copy(alpha = if (personalization.amoledBlack) 0.40f else 0.22f)
        isAzkaban -> Color(0xFF38BDF8).copy(alpha = if (personalization.amoledBlack) 0.55f else 0.35f)
        personalization.amoledBlack -> Color(0xFF10B981).copy(alpha = 0.40f)
        else -> if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    }

    val onContainer = when {
        isSelectedCard -> MaterialTheme.colorScheme.onSecondaryContainer
        active && personalization.serverActiveColor != null -> personalization.serverActiveColor.readableOn()
        !active && personalization.serverCardColor != null -> personalization.serverCardColor.readableOn()
        isBlossom -> Color(0xFFFFF1F5)
        isNoir -> Color(0xFFF4F4F5)
        isAzkaban -> Color(0xFFF0F9FF)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val vPad = personalization.density.cardVerticalDp.dp
    val primaryAccent = activeColor
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(personalization.cornerStyle.serverCardDp.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(personalization.cornerStyle.serverCardDp.dp))
                .background(container)
                .border(if (active) 1.dp else 0.5.dp, frame, RoundedCornerShape(personalization.cornerStyle.serverCardDp.dp))
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(if (columns >= 3) 3.dp else 4.dp)
                    .fillMaxHeight()
                    .background(
                        when {
                            active -> primaryAccent
                            isSelectedCard -> MaterialTheme.colorScheme.secondary
                            else -> Color.Transparent
                        },
                    ),
            )
            Row(
                Modifier
                    .weight(1f)
                    .padding(
                        start = if (columns >= 3) 8.dp else 14.dp,
                        end = if (columns >= 3) 2.dp else 4.dp,
                        top = if (columns >= 3) (vPad * 0.75f).coerceAtLeast(4.dp) else vPad,
                        bottom = if (columns >= 3) (vPad * 0.75f).coerceAtLeast(4.dp) else vPad,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (active) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(primaryAccent),
                            )
                            Spacer(Modifier.width(7.dp))
                        }
                        val flagCountry = extractCountryCode(profile.name) ?: profile.countryCode
                        if (!flagCountry.isNullOrBlank()) {
                            FlagBadge(countryCode = flagCountry, size = if (columns >= 3) 13.dp else 16.dp)
                            Spacer(Modifier.width(if (columns >= 3) 4.dp else 6.dp))
                        }
                        Text(
                            text = cleanProfileName(profile.name),
                            style = (if (columns >= 3) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium).copy(
                                textDirection = TextDirection.ContentOrRtl,
                            ),
                            fontWeight = FontWeight.SemiBold,
                            color = onContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val subtle = onContainer.copy(alpha = 0.72f)

                    val typeLabel = if (profile.isCustom) {
                        stringResource(Res.string.servers_custom_config)
                    } else {
                        profile.transportLabel
                    }

                    Text(
                        text = buildString {
                            append(typeLabel)
                            if (profile.address.isNotBlank() && profile.address != "-") {
                                append("  •  ")
                                append(profile.address)
                                if (profile.port > 0) append(":${profile.port}")
                            }
                            if (subscriptionName != null) append("  •  $subscriptionName")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (personalization.monospaceAddress) FontFamily.Monospace else null,
                        color = subtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (personalization.showServerUsage && (profile.bytesDown > 0 || profile.bytesUp > 0)) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(
                                Res.string.servers_usage,
                                formatBytes(profile.bytesDown),
                                formatBytes(profile.bytesUp),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = subtle.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    profile.lastPingMs?.takeIf { it >= 0 }?.takeIf { personalization.showServerPing }?.let { ping ->
                        Surface(
                            shape = CircleShape,
                            color = pingColor(ping, container).copy(alpha = 0.16f),
                            contentColor = pingColor(ping, container),
                        ) {
                            Text(
                                text = if (ping < 0) "$ping" else "$ping ms",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    profile.expiresAt?.let { exp ->
                        Spacer(Modifier.height(2.dp))
                        ExpiryCountdownBadge(
                            expiresAt = exp,
                            isDark = isDark,
                        )
                    }
                }
                if (active) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painterResource(Res.drawable.ic_check_circle),
                        contentDescription = stringResource(Res.string.config_active),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (ServersUiConfig.SHOW_CARD_MENU) Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = if (columns >= 3) Modifier.size(28.dp) else Modifier,
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_more_vert),
                            contentDescription = stringResource(Res.string.servers_actions),
                            tint = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                            modifier = if (columns >= 3) Modifier.size(16.dp) else Modifier,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        shape = RoundedCornerShape(14.dp),
                        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA),
                        ),
                    ) {
                        val isDefaultConfig = ConfigRepository.isDefaultSubscription(profile.subscriptionId)
                        if (ServersUiConfig.SHOW_SHARE_ACTIONS && !isDefaultConfig) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.action_share)) },
                                leadingIcon = { Icon(painterResource(Res.drawable.ic_ios_share), null) },

                                enabled = shareLink != null,
                                onClick = { menuOpen = false; onShare() },
                            )
                            onShareLocked?.let { shareLocked ->
                                DropdownMenuItem(
                                    text = { Text("قفل و اشتراک امن (NRG)") },
                                    leadingIcon = { Icon(painterResource(Res.drawable.ic_lock), null, tint = Color(0xFFFF9500)) },
                                    enabled = shareLink != null,
                                    onClick = { menuOpen = false; shareLocked() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.action_share_qr)) },
                                leadingIcon = { Icon(painterResource(Res.drawable.ic_qr_code_2), null) },
                                enabled = shareLink != null,
                                onClick = { menuOpen = false; onShareQr() },
                            )
                        }
                        if (!isDefaultConfig) {
                            onEdit?.let { edit ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.action_edit)) },
                                    leadingIcon = { Icon(painterResource(Res.drawable.ic_edit), null) },
                                    enabled = profile.isEditable,
                                    onClick = { menuOpen = false; edit() },
                                )
                            }
                            onRename?.let { rename ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.servers_rename)) },
                                    leadingIcon = { Icon(painterResource(Res.drawable.ic_description), null) },
                                    onClick = { menuOpen = false; rename() },
                                )
                            }
                        }
                        onMoveGroup?.let { move ->
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.groups_move)) },
                                leadingIcon = { Icon(painterResource(Res.drawable.ic_add_link), null) },
                                onClick = { menuOpen = false; move() },
                            )
                        }

                        if ((!profile.isManagedTunnel || profile.isSingBoxConfig || profile.isAether || profile.isNoRootSniSpoof || profile.isSniSpoof) && !profile.isDnsBasedTunnel) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.ping_tcp)) },
                                leadingIcon = { Icon(painterResource(Res.drawable.ic_speed), null) },
                                onClick = { menuOpen = false; onPingTcp() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.ping_real)) },
                                leadingIcon = { Icon(painterResource(Res.drawable.ic_bolt), null) },
                                onClick = { menuOpen = false; onPingReal() },
                            )
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.action_delete), color = Color(0xFFFF3B30), fontWeight = FontWeight.Medium) },
                            leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null, tint = Color(0xFFFF3B30)) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpiryCountdownBadge(
    expiresAt: Long,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 9.sp,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    var now by remember(expiresAt) { mutableLongStateOf(currentTimeMillis()) }

    LaunchedEffect(expiresAt) {
        while (true) {
            now = currentTimeMillis()
            val remainingMs = expiresAt - now
            if (remainingMs <= 0) break
            delay(1000L)
        }
    }

    val remainingMs = expiresAt - now
    val (expText, expColor) = if (remainingMs <= 0) {
        "منقضی شده" to Color(0xFFFF3B30)
    } else {
        val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0)
        val days = totalSeconds / (24 * 3600L)
        val hours = (totalSeconds % (24 * 3600L)) / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        val formatted = when {
            days > 0 -> {
                if (hours > 0) "$days روز $hours س $minutes د" else "$days روز $minutes د"
            }
            hours > 0 -> {
                "$hours س $minutes د $seconds ث"
            }
            minutes > 0 -> {
                "$minutes د $seconds ث"
            }
            else -> {
                "$seconds ثانیه"
            }
        }
        val color = when {
            remainingMs < 10 * 60_000L -> Color(0xFFFF3B30)
            remainingMs < 60 * 60_000L -> Color(0xFFFF9500)
            else -> if (isDark) Color(0xFFAAAAAA) else Color(0xFF666666)
        }
        formatted to color
    }

    Text(
        text = expText,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = expColor,
        maxLines = 1,
        modifier = modifier,
    )
}


