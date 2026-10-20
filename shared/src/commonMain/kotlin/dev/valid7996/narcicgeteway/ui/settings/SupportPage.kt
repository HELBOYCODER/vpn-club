@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.valid7996.narcicgeteway.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SupportPage(
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    val platform = LocalPlatform.current
    val copied = stringResource(Res.string.support_copied)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    SettingsPageScaffold(
        title = stringResource(Res.string.support_title),
        subtitle = stringResource(Res.string.support_subtitle),
        contentPadding = contentPadding,
        modifier = modifier,
        titleIcon = Res.drawable.ic_favorite,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .border(0.5.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), RoundedCornerShape(16.dp)),
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF2D55)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_favorite),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    stringResource(Res.string.support_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFFF2F2F7) else Color(0xFF000000),
                )
            }
        }

        SettingsGroup {
            WalletRow(
                icon = Res.drawable.ic_coin_tether,
                label = stringResource(Res.string.support_usdt),
                address = "TRxVSHcoADZnBfztFmFb2TQopusAwWYEVR",
                onCopy = { platform.copyToClipboard(it); platform.toast(copied) },
            )
            SettingsDivider()
            WalletRow(
                icon = Res.drawable.ic_coin_ton,
                label = stringResource(Res.string.support_ton),
                address = "UQAH75bXaaRUhZMwiF0ZujOXFDDmvLSPASKoOsWF0HNasiaM",
                onCopy = { platform.copyToClipboard(it); platform.toast(copied) },
            )
            SettingsDivider()
            WalletRow(
                icon = Res.drawable.ic_coin_tron,
                label = stringResource(Res.string.support_tron),
                address = "TRxVSHcoADZnBfztFmFb2TQopusAwWYEVR",
                onCopy = { platform.copyToClipboard(it); platform.toast(copied) },
            )
        }

        Text(
            stringResource(Res.string.support_usdt_note),
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WalletRow(
    icon: DrawableResource,
    label: String,
    address: String,
    onCopy: (String) -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCopy(address) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color(0xFFF2F2F7) else Color(0xFF000000),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                address,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painterResource(Res.drawable.ic_content_paste),
            contentDescription = stringResource(Res.string.action_copy),
            tint = Color(0xFF007AFF),
            modifier = Modifier.size(18.dp),
        )
    }
}
