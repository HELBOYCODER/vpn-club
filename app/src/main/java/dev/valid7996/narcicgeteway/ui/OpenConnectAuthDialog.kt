package dev.valid7996.narcicgeteway.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.valid7996.narcicgeteway.R
import dev.valid7996.narcicgeteway.core.AuthFormBus
import dev.valid7996.narcicgeteway.core.CertTrustBus
import org.infradead.libopenconnect.LibOpenConnect

@Composable
fun OpenConnectAuthDialog() {
    val prompt by AuthFormBus.pending.collectAsStateWithLifecycle()
    val form = prompt ?: return
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val values = remember(form) {
        mutableStateMapOf<String, String>().apply {
            form.opts.forEach { put(it.name ?: "", it.value ?: "") }
        }
    }

    AlertDialog(
        onDismissRequest = { AuthFormBus.cancel() },
        shape = RoundedCornerShape(14.dp),
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
        title = { Text(stringResource(R.string.oc_auth_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                form.banner?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                form.message?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                form.error?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                form.opts.forEach { opt ->
                    val name = opt.name ?: return@forEach

                    if (opt.flags and LibOpenConnect.OC_FORM_OPT_IGNORE.toLong() != 0L) return@forEach
                    if (opt.type == LibOpenConnect.OC_FORM_OPT_HIDDEN ||
                        opt.type == LibOpenConnect.OC_FORM_OPT_TOKEN ||
                        opt.type == LibOpenConnect.OC_FORM_OPT_SSO_TOKEN ||
                        opt.type == LibOpenConnect.OC_FORM_OPT_SSO_USER
                    ) return@forEach

                    val numeric = opt.flags and LibOpenConnect.OC_FORM_OPT_NUMERIC.toLong() != 0L
                    when (opt.type) {
                        LibOpenConnect.OC_FORM_OPT_SELECT -> SelectField(
                            label = opt.label ?: name,
                            choices = opt.choices,
                            selectedName = values[name].orEmpty(),
                            onSelect = { values[name] = it },
                        )
                        LibOpenConnect.OC_FORM_OPT_PASSWORD -> InputField(
                            label = opt.label ?: name,
                            value = values[name].orEmpty(),
                            password = true,
                            numeric = numeric,
                            onChange = { values[name] = it },
                        )
                        else -> InputField(
                            label = opt.label ?: name,
                            value = values[name].orEmpty(),
                            password = false,
                            numeric = numeric,
                            onChange = { values[name] = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { AuthFormBus.submit(values.toMap()) }) {
                Text(stringResource(R.string.oc_auth_connect), color = Color(0xFF007AFF), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = { AuthFormBus.cancel() }) {
                Text(stringResource(R.string.oc_auth_cancel), color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73))
            }
        },
    )
}

@Composable
private fun InputField(
    label: String,
    value: String,
    password: Boolean,
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                numeric && password -> KeyboardType.NumberPassword
                numeric -> KeyboardType.Number
                password -> KeyboardType.Password
                else -> KeyboardType.Text
            },
        ),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SelectField(
    label: String,
    choices: List<LibOpenConnect.FormChoice>,
    selectedName: String,
    onSelect: (String) -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.name == selectedName }?.label ?: selectedName
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
        )

        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA)),
        ) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label ?: choice.name ?: "") },
                    onClick = { onSelect(choice.name ?: ""); expanded = false },
                )
            }
        }
    }
}

@Composable
fun OpenConnectCertTrustDialog(onRemember: (hash: String) -> Unit = {}) {
    val prompt by CertTrustBus.pending.collectAsStateWithLifecycle()
    val pending = prompt ?: return
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    var remember by androidx.compose.runtime.remember(pending) { androidx.compose.runtime.mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { CertTrustBus.reject() },
        shape = RoundedCornerShape(14.dp),
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
        title = { Text(stringResource(R.string.oc_cert_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.oc_cert_body), style = MaterialTheme.typography.bodyMedium)
                pending.reason.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                SelectionContainer {
                    Text(
                        pending.hash,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { remember = !remember },
                ) {
                    Checkbox(
                        checked = remember,
                        onCheckedChange = { remember = it },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Color(0xFF007AFF)),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.oc_cert_remember),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (remember) onRemember(pending.hash)
                CertTrustBus.accept()
            }) {
                Text(stringResource(R.string.oc_cert_trust), color = Color(0xFF007AFF), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = { CertTrustBus.reject() }) {
                Text(stringResource(R.string.oc_auth_cancel), color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73))
            }
        },
    )
}
