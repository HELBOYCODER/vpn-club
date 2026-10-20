package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform

/** The buyer sends this code to the seller, who locks the config to this device only. */
@Composable
fun DeviceCodeDialog(code: String, onDismiss: () -> Unit) {
    val platform = LocalPlatform.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کد دستگاه من") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("این کد را برای فروشنده بفرستید تا کانفیگ فقط برای همین دستگاه ساخته شود.")
                Text(code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                platform.copyToClipboard(code)
                platform.toast("کد دستگاه کپی شد")
            }) { Text("کپی کد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}
