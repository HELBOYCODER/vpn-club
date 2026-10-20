@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.valid7996.narcicgeteway.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import dev.valid7996.narcicgeteway.ui.components.CyberRadarIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.data.assets.GeoAsset
import dev.valid7996.narcicgeteway.data.assets.GeoAssets
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.domain.model.GeoFilesSource
import dev.valid7996.narcicgeteway.ui.components.PageHeader
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.launch

@Composable
fun AssetsScreen(
    settings: AppSettings,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit = {},
) {
    val platform = LocalPlatform.current
    val geo = platform.geoAssets
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Box(
        modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Spacer(Modifier.height(8.dp))
            PageHeader(
                title = stringResource(Res.string.assets_title),
                subtitle = stringResource(Res.string.assets_subtitle),
                accentLine = true,
            )
            Spacer(Modifier.height(14.dp))

        if (geo == null) {
            Text(
                stringResource(Res.string.assets_subtitle),
                Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        var assets by remember { mutableStateOf(geo.state()) }
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            assets.forEach { asset ->
                AssetCard(
                    asset = asset,
                    onImport = {
                        scope.launch {
                            val pick = platform.pickFileBytes() ?: return@launch
                            busy = true
                            val ok = runCatching { geo.importBytes(pick.bytes, asset.name) }.getOrDefault(false)
                            assets = geo.state()
                            busy = false
                            message = getString(if (ok) Res.string.assets_done else Res.string.assets_failed)
                        }
                    },
                )
            }

            Spacer(Modifier.height(4.dp))

            SettingsGroup {
                SettingsListRow(
                    title = stringResource(Res.string.assets_source),
                    options = GeoFilesSource.entries.map { it to it.repo },
                    selected = settings.geoFilesSource,
                    onSelected = { v -> onUpdate { it.copy(geoFilesSource = v) } },
                )
            }
            Text(
                stringResource(Res.string.assets_source_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Res.string.assets_bundled_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            if (busy) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CyberRadarIndicator()
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.assets_downloading))
                }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            val ok = geo.downloadAll(settings.geoFilesSource)
                            assets = geo.state()
                            busy = false
                            message = getString(if (ok) Res.string.assets_done else Res.string.assets_failed)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF007AFF),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(stringResource(Res.string.assets_download), fontWeight = FontWeight.SemiBold)
                }
            }

            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFF34C759) else Color(0xFF28CD41),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024
    var i = 0
    while (value >= 1024 && i < units.size - 1) { value /= 1024; i++ }
    val rounded = (value * 10).toLong() / 10.0
    return "$rounded ${units[i]}"
}

@Composable
private fun AssetCard(asset: GeoAsset, onImport: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val shape = RoundedCornerShape(14.dp)
    val cardBorder = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)

    Surface(
        shape = shape,
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (asset.present) Color(0xFF34C759).copy(alpha = 0.15f)
                        else Color(0xFF8E8E93).copy(alpha = 0.15f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_description),
                    contentDescription = null,
                    tint = if (asset.present) Color(0xFF34C759) else (if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73)),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    asset.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else Color(0xFF000000),
                )
                Text(
                    if (asset.present) {
                        stringResource(Res.string.assets_size, formatBytes(asset.sizeBytes), "")
                    } else {
                        stringResource(Res.string.assets_missing)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                )
            }
            OutlinedButton(
                onClick = onImport,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF007AFF)),
            ) {
                Text(
                    stringResource(Res.string.assets_import),
                    color = Color(0xFF007AFF),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
