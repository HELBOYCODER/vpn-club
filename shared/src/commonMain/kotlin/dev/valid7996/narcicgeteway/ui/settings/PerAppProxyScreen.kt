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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import dev.valid7996.narcicgeteway.ui.components.CyberRadarIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.ui.components.PageHeader
import dev.valid7996.narcicgeteway.ui.components.PickerField
import dev.valid7996.narcicgeteway.ui.platform.InstalledApp
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PerAppProxyScreen(
    settings: AppSettings,
    contentPadding: PaddingValues,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val platform = LocalPlatform.current
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }

    if (!platform.supportsPerAppProxy) {
        Column(modifier.fillMaxSize().padding(contentPadding)) {
            Spacer(Modifier.height(8.dp))
            PageHeader(
                title = stringResource(Res.string.per_app_title),
                subtitle = stringResource(Res.string.per_app_subtitle),
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                Text(
                    stringResource(Res.string.per_app_unsupported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        return
    }

    val importFailMsg = stringResource(Res.string.per_app_import_failed)
    val exportOkMsg = stringResource(Res.string.per_app_export_ok)

    var imported by remember { mutableStateOf<Int?>(null) }
    imported?.let { count ->
        val msg = stringResource(Res.string.per_app_import_ok, count)
        LaunchedEffect(count) {
            platform.toast(msg)
            imported = null
        }
    }

    val apps by produceState<List<InstalledApp>?>(initialValue = null, showSystem) {
        value = withContext(Dispatchers.IO) {
            platform.installedApps().filter { showSystem || !it.isSystem }
        }
    }

    val mode = when {
        !settings.perAppProxyEnabled -> PerAppMode.Off
        settings.perAppBypassMode -> PerAppMode.Exclude
        else -> PerAppMode.Include
    }

    val visible = remember(apps, query, settings.perAppPackages) {
        val list = apps.orEmpty()
        val filtered = if (query.isBlank()) list else list.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        filtered.sortedWith(
            compareByDescending<InstalledApp> { it.packageName in settings.perAppPackages }
                .thenBy { it.label.lowercase() },
        )
    }

    fun setSelection(next: Set<String>) {
        onUpdate { it.copy(perAppPackages = next) }
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Box(
        modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7)),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            Spacer(Modifier.height(8.dp))
            PageHeader(
                title = stringResource(Res.string.per_app_title),
                subtitle = stringResource(Res.string.per_app_subtitle),
                accentLine = true,
            )
            Spacer(Modifier.height(12.dp))

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PickerField(
                label = stringResource(Res.string.per_app_title),
                options = listOf(
                    PerAppMode.Off to stringResource(Res.string.per_app_mode_off),
                    PerAppMode.Include to stringResource(Res.string.per_app_mode_include),
                    PerAppMode.Exclude to stringResource(Res.string.per_app_mode_exclude),
                ),
                selected = mode,
                onSelect = { selected ->
                    onUpdate {
                        it.copy(
                            perAppProxyEnabled = selected != PerAppMode.Off,
                            perAppBypassMode = selected == PerAppMode.Exclude,
                        )
                    }
                },
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(Res.string.per_app_search)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.per_app_show_system),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = showSystem,
                    onCheckedChange = { showSystem = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF34C759),
                    ),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (apps == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                CyberRadarIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "per-app-actions") {
                    Text(
                        stringResource(Res.string.per_app_selected, settings.perAppPackages.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { setSelection(settings.perAppPackages + visible.map { it.packageName }) },
                            label = { Text(stringResource(Res.string.per_app_select_all)) },
                        )
                        AssistChip(
                            onClick = { setSelection(settings.perAppPackages - visible.map { it.packageName }.toSet()) },
                            label = { Text(stringResource(Res.string.per_app_select_none)) },
                        )
                        AssistChip(
                            onClick = {
                                val shown = visible.map { it.packageName }.toSet()
                                val kept = settings.perAppPackages - shown
                                setSelection(kept + shown.filterNot { it in settings.perAppPackages })
                            },
                            label = { Text(stringResource(Res.string.per_app_invert)) },
                        )
                        AssistChip(
                            onClick = {
                                val parsed = platform.readClipboard().orEmpty()
                                    .split('\n', ',')
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() && it.none(Char::isWhitespace) }
                                if (parsed.isEmpty()) platform.toast(importFailMsg)
                                else {
                                    setSelection(parsed.toSet())
                                    imported = parsed.size
                                }
                            },
                            label = { Text(stringResource(Res.string.per_app_import)) },
                        )
                        AssistChip(
                            onClick = {
                                platform.copyToClipboard(settings.perAppPackages.sorted().joinToString("\n"))
                                platform.toast(exportOkMsg)
                            },
                            label = { Text(stringResource(Res.string.per_app_export)) },
                        )
                    }

                    Text(
                        stringResource(Res.string.per_app_reconnect_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                items(visible, key = { it.packageName }) { app ->
                    val checked = app.packageName in settings.perAppPackages
                    AppRow(
                        app = app,
                        checked = checked,
                        enabled = settings.perAppProxyEnabled,
                        onToggle = {
                            onUpdate { current ->
                                val next = current.perAppPackages.toMutableSet()
                                if (checked) next.remove(app.packageName)
                                else next.add(app.packageName)
                                current.copy(perAppPackages = next)
                            }
                        },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    }
}

internal enum class PerAppMode { Off, Include, Exclude }

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val shape = RoundedCornerShape(12.dp)
    val cardBorder = if (checked) Color(0xFF007AFF) else (if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
    val cardBg = if (checked) {
        if (isDark) Color(0xFF007AFF).copy(alpha = 0.12f) else Color(0xFF007AFF).copy(alpha = 0.08f)
    } else {
        if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    }

    Surface(
        shape = shape,
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(if (checked) 1.dp else 0.5.dp, cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val platform = LocalPlatform.current
            val icon by produceState<ByteArray?>(app.iconPng, app.packageName) {
                if (value == null) value = withContext(Dispatchers.IO) { platform.appIcon(app.packageName) }
            }
            AppIcon(iconPng = icon, label = app.label)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color.White else Color(0xFF000000),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                enabled = enabled,
                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Color(0xFF007AFF)),
            )
        }
    }
}

@Composable
private fun AppIcon(iconPng: ByteArray?, label: String) {
    if (iconPng != null) {
        val platformContext = LocalPlatformContext.current
        AsyncImage(
            model = remember(iconPng) {
                ImageRequest.Builder(platformContext).data(iconPng).build()
            },
            contentDescription = null,
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)),
        )
    } else {
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    label.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
