@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.valid7996.narcicgeteway.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.valid7996.narcicgeteway.ui.theme.AccentPresets
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.platform.draggableHorizontalScroll

private val DisabledAlpha = 0.38f

@Composable
private fun SettingsRow(
    title: String,
    description: String?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val titleColor = if (enabled) (if (isDark) Color(0xFFF2F2F7) else Color(0xFF000000))
    else MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha)
    val descColor = if (enabled) (if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73))
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!description.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = descColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    leadingIcon: org.jetbrains.compose.resources.DrawableResource? = null,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    SettingsRow(
        title = title,
        description = summary,
        enabled = enabled,
        onClick = onClick,
        leading = leadingIcon?.let {
            {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint ?: Color(0xFF007AFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(it),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        },
    )
}

@Composable
fun SettingsSwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    leadingIcon: org.jetbrains.compose.resources.DrawableResource? = null,
    iconTint: Color? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    SettingsRow(
        title = title,
        description = summary,
        enabled = enabled,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else null,
        leading = leadingIcon?.let {
            {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint ?: Color(0xFF34C759)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(it),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF34C759),
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = if (isDark) Color(0xFF39393D) else Color(0xFFE9E9EA),
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        },
    )
}

@Composable
fun SettingsEditRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    numeric: Boolean = false,
    placeholder: String? = null,
    leadingIcon: org.jetbrains.compose.resources.DrawableResource? = null,
    iconTint: Color? = null,
    onValueChanged: (String) -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var showDialog by remember { mutableStateOf(false) }
    val description = when {
        isPassword && value.isNotEmpty() -> "••••••"
        value.isNotEmpty() -> value
        else -> placeholder
    }

    SettingsRow(
        title = title,
        description = description,
        enabled = enabled,
        onClick = if (enabled) {
            { showDialog = true }
        } else null,
        leading = leadingIcon?.let {
            {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint ?: Color(0xFF007AFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(it),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        },
    )

    if (showDialog) {
        var text by remember { mutableStateOf(value) }
        var reveal by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else Color.Black,
                )
            },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                    ),
                    visualTransformation = if (isPassword && !reveal) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    trailingIcon = if (isPassword) {
                        {
                            Icon(
                                painterResource(
                                    if (reveal) Res.drawable.ic_visibility_off
                                    else Res.drawable.ic_visibility
                                ),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { reveal = !reveal },
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onValueChanged(text.trim())
                }) {
                    Text(
                        stringResource(Res.string.action_save),
                        color = Color(0xFF007AFF),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(
                        stringResource(Res.string.action_cancel),
                        color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                    )
                }
            },
        )
    }
}

@Composable
fun <T> SettingsListRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    leadingIcon: org.jetbrains.compose.resources.DrawableResource? = null,
    iconTint: Color? = null,
    onSelected: (T) -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val direction = LocalLayoutDirection.current
    var showDialog by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second
        ?: options.firstOrNull()?.second.orEmpty()

    SettingsRow(
        title = title,
        description = selectedLabel,
        enabled = enabled,
        onClick = if (enabled) {
            { showDialog = true }
        } else null,
        leading = leadingIcon?.let {
            {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint ?: Color(0xFF007AFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(it),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        },
        trailing = {
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
        },
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else Color.Black,
                )
            },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .selectable(
                                    selected = value == selected,
                                    onClick = {
                                        showDialog = false
                                        onSelected(value)
                                    },
                                )
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = value == selected,
                                onClick = {
                                    showDialog = false
                                    onSelected(value)
                                },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF007AFF),
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                label.ifBlank { stringResource(Res.string.option_none) },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isDark) Color(0xFFF2F2F7) else Color(0xFF000000),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(
                        stringResource(Res.string.action_cancel),
                        color = Color(0xFF007AFF),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }
}

@Composable
fun SettingsInfoRow(title: String, body: String) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) Color(0xFFF2F2F7) else Color(0xFF000000),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.25,
        )
    }
}

@Composable
fun SettingsMenuRow(
    title: String,
    subtitle: String? = null,
    leadingIcon: org.jetbrains.compose.resources.DrawableResource? = null,
    iconTint: Color? = null,
    iconTile: Boolean = true,
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val direction = LocalLayoutDirection.current
    SettingsRow(
        title = title,
        description = subtitle,
        enabled = true,
        onClick = onClick,
        leading = leadingIcon?.let {
            {
                val squircleBg = iconTint ?: Color(0xFF007AFF)
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(squircleBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(it),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!trailingText.isNullOrEmpty()) {
                    Text(
                        trailingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                        modifier = Modifier.padding(end = 4.dp),
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
        },
    )
}

@Composable
fun SettingsDivider(
    modifier: Modifier = Modifier,
    hasIcon: Boolean = true,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val direction = LocalLayoutDirection.current
    val indent = if (hasIcon) 62.dp else 16.dp
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier.then(
            if (direction == LayoutDirection.Rtl) {
                Modifier.padding(end = indent)
            } else {
                Modifier.padding(start = indent)
            }
        ),
        thickness = 0.5.dp,
        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
    )
}

@Composable
fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    sectionTitle: Boolean = false,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cardShape = RoundedCornerShape(16.dp)
    val cardBorder = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val headerColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73)

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = headerColor,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
            )
        }
        Surface(
            shape = cardShape,
            color = cardBg,
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, cardBorder, cardShape),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                content = content,
            )
        }
    }
}

typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

@Composable
fun SettingsPageScaffold(
    title: String,
    subtitle: String? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    modifier: Modifier = Modifier,
    titleIcon: org.jetbrains.compose.resources.DrawableResource? = null,
    accentLine: Boolean = false,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val pageBg = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7)

    Box(
        modifier
            .fillMaxSize()
            .background(pageBg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            dev.valid7996.narcicgeteway.ui.components.PageHeader(
                title = title,
                subtitle = subtitle,
                titleIcon = titleIcon,
                accentLine = false,
            )
            Spacer(Modifier.height(4.dp))
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun AccentColorRow(label: String, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().draggableHorizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AccentPresets.forEachIndexed { i, preset ->
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(preset.light)
                        .border(
                            width = if (selected == i) 3.dp else 1.dp,
                            color = if (selected == i) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(i) },
                )
            }
        }
    }
}
