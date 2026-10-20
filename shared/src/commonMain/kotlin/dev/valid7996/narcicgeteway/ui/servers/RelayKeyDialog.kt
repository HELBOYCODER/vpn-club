package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Asks for the private key of a short (relay) link that was pasted without its key. */
@Composable
fun RelayKeyDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کلید اختصاصی") },
        text = {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("کلید (مثلاً NG-XXXX-XXXX-XXXX)") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (key.isNotBlank()) onSubmit(key.trim()) }) { Text("فعال‌سازی") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
