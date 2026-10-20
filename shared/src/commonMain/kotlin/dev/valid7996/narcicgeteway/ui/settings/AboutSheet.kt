@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.valid7996.narcicgeteway.ui.settings

import org.jetbrains.compose.resources.DrawableResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.platform.AppInfo
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import dev.valid7996.narcicgeteway.ui.components.LiquidCircle
import dev.valid7996.narcicgeteway.ui.theme.NrgCyan
import dev.valid7996.narcicgeteway.ui.theme.NrgLime
import dev.valid7996.narcicgeteway.ui.theme.NrgViolet

private const val TELEGRAM_URL = "https://t.me/Narcic_team"
private const val GITHUB_URL = "https://github.com/valid7996/Narcic-Getway"

private const val XRAY_SOURCE_URL = "https://github.com/CluvexStudio/Xray-core"
private const val LIB_SOURCE_URL = "https://github.com/CluvexStudio/AndroidLibXrayLite"

@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val platform = LocalPlatform.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiquidCircle(
                colors = listOf(NrgViolet, NrgCyan, NrgLime),
                modifier = Modifier.size(96.dp),
            )
            Text(
                stringResource(Res.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A),
            )
            Text(
                stringResource(Res.string.about_version, AppInfo.versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Color(0xFFA0B0C4) else Color(0xFF64748B),
            )
            Spacer(Modifier.size(4.dp))

            LinkRow(
                iconRes = Res.drawable.ic_add_link,
                title = stringResource(Res.string.about_telegram),
                subtitle = "t.me/Narcic_team",
                isDark = isDark,
                onClick = { platform.openUri(TELEGRAM_URL) },
            )
            LinkRow(
                iconRes = Res.drawable.ic_description,
                title = stringResource(Res.string.about_github),
                subtitle = "github.com/valid7996/Narcic-Getway",
                isDark = isDark,
                onClick = { platform.openUri(GITHUB_URL) },
            )

            Spacer(Modifier.size(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (isDark) Color(0xFF2C2C2E).copy(alpha = 0.45f) else Color(0xFFF2F2F7),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(Res.string.about_app_description_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF60E0B0) else Color(0xFF0D9488),
                    )
                    Text(
                        stringResource(Res.string.about_app_description_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color(0xFFA0B0C4) else Color(0xFF475569),
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.25,
                    )
                }
            }

            Spacer(Modifier.size(8.dp))

            if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ALL_SETTINGS) {
                Text(
                    stringResource(Res.string.about_source_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color(0xFF60E0B0) else Color(0xFF0D9488),
                )
                Text(
                    stringResource(Res.string.about_source_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color(0xFFA0B0C4) else Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                )
                LinkRow(
                    iconRes = Res.drawable.ic_description,
                    title = stringResource(Res.string.about_source_xray),
                    subtitle = "github.com/valid7996/Xray-core",
                    isDark = isDark,
                    onClick = { platform.openUri(XRAY_SOURCE_URL) },
                )
                LinkRow(
                    iconRes = Res.drawable.ic_description,
                    title = stringResource(Res.string.about_source_lib),
                    subtitle = "github.com/valid7996/AndroidLibXrayLite",
                    isDark = isDark,
                    onClick = { platform.openUri(LIB_SOURCE_URL) },
                )
            }

            Spacer(Modifier.size(4.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (isDark) Color(0xFF2C2C2E).copy(alpha = 0.45f) else Color(0xFFF2F2F7),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "تشکر خاص از:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFFFF9500) else Color(0xFFD97706),
                    )
                    Text(
                        "cluvex • patterniha • anonvector • mahsaaNG • pashmforsh",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color(0xFFE5E5EA) else Color(0xFF1C1C1E),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Text(
                stringResource(Res.string.about_thanks_valid7996),
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Color(0xFF8899A6) else Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LinkRow(iconRes: DrawableResource, title: String, subtitle: String, isDark: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val cardBorder = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)
    val cardBg = if (isDark) Color(0xFF2C2C2E).copy(alpha = 0.45f) else Color(0xFFF2F2F7)
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    Surface(
        onClick = onClick,
        shape = shape,
        color = cardBg,
        modifier = Modifier
            .fillMaxWidth()
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
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF007AFF)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color(0xFFF2F2F7) else Color(0xFF000000),
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                )
            }
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = if (isDark) Color(0xFF545458) else Color(0xFFC7C7CC),
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
