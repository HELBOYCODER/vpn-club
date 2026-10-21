package com.helboy.vpnclub.ui.proto

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.components.ActionButton
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary

/*
 * Building blocks for every screen that fronts the protocol engine, so they all look and behave like the rest of
 * Hellboy: same cards, same accent, right-to-left, Persian copy, and the same three states (empty / loading / error).
 */

/** A full page with the Hellboy header: back arrow, title, optional actions, scrolling body. */
@Composable
fun ProtoPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("proto_back")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "بازگشت", tint = TextPrimary)
            }
            Text(
                title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.weight(1f),
            )
            actions()
        }
        val body = Modifier.weight(1f).fillMaxWidth()
        Column(
            (if (scroll) body.verticalScroll(rememberScrollState()) else body).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** A titled card holding related controls. */
@Composable
fun ProtoSection(
    title: String? = null,
    hint: String? = null,
    modifier: Modifier = Modifier,
    border: Color = com.helboy.vpnclub.ui.theme.BorderDark,
    content: @Composable ColumnScope.() -> Unit,
) {
    ClubCard(modifier.fillMaxWidth(), border = border) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            if (hint != null) Text(hint, color = TextSecondary, fontSize = 12.sp)
            content()
        }
    }
}

/** A single-line (or multi-line) text field with an optional error line under it. */
@Composable
fun ProtoField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    monospace: Boolean = false,
    enabled: Boolean = true,
    tag: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontSize = 12.sp) },
            placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder, fontSize = 12.sp, color = TextTertiary) }),
            isError = error != null,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            ),
            modifier = Modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it },
        )
        if (error != null) Text(error, color = StatusRed, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, start = 4.dp))
    }
}

/** A labelled on/off row. */
@Composable
fun ProtoSwitch(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    tag: String? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, color = TextTertiary, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier)
    }
}

/** One-of-N choice shown as scrolling chips. [options] are (value, label) pairs. */
@Composable
fun ProtoChoice(
    title: String?,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) Text(title, color = TextSecondary, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label, fontSize = 12.sp) })
            }
        }
        if (hint != null) Text(hint, color = TextTertiary, fontSize = 11.sp)
    }
}

/** Primary action button (accent colour, optional spinner while [running]). */
@Composable
fun ProtoButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    running: Boolean = false,
    accent: Color = NeonCyan,
    tag: String? = null,
) {
    ActionButton(text, icon, running = running, enabled = enabled, accent = accent, onClick = onClick,
        modifier = modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it })
}

/** Secondary (outlined) button. */
@Composable
fun ProtoOutlined(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tag: String? = null) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.let { if (tag != null) it.testTag(tag) else it }) { Text(text) }
}

/** Nothing here yet: icon, title, one line of help, optional action. */
@Composable
fun ProtoEmpty(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Inbox,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp).testTag("proto_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = TextTertiary)
        Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(hint, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) OutlinedButton(onClick = onAction) { Text(actionText) }
    }
}

/** Work in progress. */
@Composable
fun ProtoLoading(text: String = "در حال بارگذاری…", modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(16.dp).testTag("proto_loading"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.padding(end = 10.dp), strokeWidth = 2.dp, color = NeonCyan)
        Text(text, color = TextSecondary, fontSize = 13.sp)
    }
}

/** Something failed: say what, offer to retry. */
@Composable
fun ProtoError(text: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(16.dp).testTag("proto_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = StatusRed)
        Text(text, color = StatusRed, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (onRetry != null) OutlinedButton(onClick = onRetry) { Text("تلاش دوباره") }
    }
}

/** Yes/no question, for anything destructive. */
@Composable
fun ProtoConfirm(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, fontSize = 13.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("proto_confirm")) {
                Text(confirmLabel, color = if (danger) StatusRed else NeonCyan)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } },
    )
}

/** A dialog with a scrolling form body and save/cancel. */
@Composable
fun ProtoDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String = "ذخیره",
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        },
        confirmButton = {
            if (onConfirm != null) TextButton(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.testTag("proto_dialog_ok")) {
                Text(confirmLabel, color = NeonCyan)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (onConfirm == null) "بستن" else "لغو") } },
    )
}

/** A row in a list of destinations: title, one-line hint, whole row clickable. */
@Composable
fun ProtoNavRow(title: String, hint: String?, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, tag: String? = null) {
    ClubCard(modifier.fillMaxWidth().clickable(onClick = onClick).let { if (tag != null) it.testTag(tag) else it }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) Icon(icon, null, tint = NeonCyan)
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                if (hint != null) Text(hint, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

/** Convenience for screens that only show one of three states. */
sealed interface ProtoState<out T> {
    data object Loading : ProtoState<Nothing>
    data class Ready<T>(val value: T) : ProtoState<T>
    data class Failed(val message: String) : ProtoState<Nothing>
}

@Composable
fun <T> ProtoStateView(state: ProtoState<T>, onRetry: (() -> Unit)? = null, ready: @Composable (T) -> Unit) {
    when (state) {
        ProtoState.Loading -> ProtoLoading()
        is ProtoState.Failed -> ProtoError(state.message, onRetry = onRetry)
        is ProtoState.Ready -> ready(state.value)
    }
}
