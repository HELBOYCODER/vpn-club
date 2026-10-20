@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.valid7996.narcicgeteway.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.LayoutDirection

private const val POLICY_URL = "https://valid7996.github.io/Narcic-Getway/"

@Composable
fun PrivacyPage(
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    val platform = LocalPlatform.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current

    SettingsPageScaffold(
        title = stringResource(Res.string.privacy_title),
        subtitle = stringResource(Res.string.privacy_subtitle),
        contentPadding = contentPadding,
        modifier = modifier,
        titleIcon = Res.drawable.ic_policy,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            border = BorderStroke(0.5.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF34C759)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_policy),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    stringResource(Res.string.privacy_headline),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else Color(0xFF000000),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Section(
                stringResource(Res.string.privacy_client_title),
                stringResource(Res.string.privacy_client_body),
                isDark,
            )
            Section(
                stringResource(Res.string.privacy_device_title),
                stringResource(Res.string.privacy_device_body),
                isDark,
            )
            Section(
                stringResource(Res.string.privacy_nocollect_title),
                stringResource(Res.string.privacy_nocollect_body),
                isDark,
            )
            Section(
                stringResource(Res.string.privacy_third_title),
                stringResource(Res.string.privacy_third_body),
                isDark,
            )
            Section(
                stringResource(Res.string.privacy_perm_title),
                stringResource(Res.string.privacy_perm_body),
                isDark,
            )
            Section(
                stringResource(Res.string.privacy_deviceid_title),
                stringResource(Res.string.privacy_deviceid_body),
                isDark,
            )
            Section(
                stringResource(Res.string.privacy_limits_title),
                stringResource(Res.string.privacy_limits_body),
                isDark,
            )
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            border = BorderStroke(0.5.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { platform.openUri(POLICY_URL) },
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF007AFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_description),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.privacy_read_full),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color.White else Color(0xFF000000),
                    )
                    Text(
                        stringResource(Res.string.privacy_read_full_sub),
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

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Section(title: String, body: String, isDark: Boolean) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
        border = BorderStroke(0.5.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else Color(0xFF000000),
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
            )
        }
    }
}
