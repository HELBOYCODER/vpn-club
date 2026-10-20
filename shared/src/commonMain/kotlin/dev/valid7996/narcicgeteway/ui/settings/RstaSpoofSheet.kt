@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.valid7996.narcicgeteway.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.components.PickerField
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * The no-root SNI spoof, laid out as RstaNG lays it out: ONE page with the connect IP, the connect port, the fake SNI
 * and the method, and the state of the engine with its log. Nothing is created per profile: every profile whose server
 * is 127.0.0.1:40443 goes through the engine with these settings, and the changes apply at once.
 */
@Composable
fun RstaSpoofSheet(settings: AppSettings, onUpdate: (AppSettings) -> Unit, onDismiss: () -> Unit) {
    val platform = LocalPlatform.current
    var ip by rememberSaveable(settings.rstaSpoofConnectIp) { mutableStateOf(settings.rstaSpoofConnectIp) }
    var port by rememberSaveable(settings.rstaSpoofConnectPort) { mutableStateOf(settings.rstaSpoofConnectPort.toString()) }
    var sni by rememberSaveable(settings.rstaSpoofFakeSni) { mutableStateOf(settings.rstaSpoofFakeSni) }
    var method by rememberSaveable(settings.rstaSpoofMethod) { mutableStateOf(SniSpoofConfig.method(settings.rstaSpoofMethod)) }
    var status by remember { mutableStateOf("") }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var showLog by remember { mutableStateOf(false) }

    // The engine runs in another process: what it says about itself is read from the files it writes.
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) { status = platform.rstaSpoofStatus(); log = platform.rstaSpoofLog() }
            delay(1_500)
        }
    }

    fun applySave() {
        val nextPort = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: 443
        val nextIp = ip.trim().ifEmpty { SniSpoofConfig.DEFAULT_CONNECT_IP }
        val nextSni = sni.trim().ifEmpty { SniSpoofConfig.DEFAULT_FAKE_SNI }
        val nextMethod = SniSpoofConfig.method(method)
        onUpdate(
            settings.copy(
                rstaSpoofConnectIp = nextIp,
                rstaSpoofConnectPort = nextPort,
                rstaSpoofFakeSni = nextSni,
                rstaSpoofMethod = nextMethod,
            ),
        )
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    ModalBottomSheet(
        onDismissRequest = {
            applySave()
            onDismiss()
        },
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
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.rsta_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(Res.string.rsta_note), style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text(stringResource(Res.string.rsta_connect_ip)) },
                supportingText = { Text(stringResource(Res.string.rsta_connect_ip_hint)) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = { Text(stringResource(Res.string.rsta_connect_port)) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = sni,
                onValueChange = { sni = it },
                label = { Text(stringResource(Res.string.rsta_fake_sni)) },
                supportingText = { Text(stringResource(Res.string.rsta_fake_sni_hint)) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PickerField(
                label = stringResource(Res.string.rsta_method),
                options = SniSpoofConfig.METHODS.map { it to it },
                selected = method,
                onSelect = { method = it },
            )

            Button(
                onClick = {
                    applySave()
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold)
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.rsta_status), style = MaterialTheme.typography.labelMedium)
                    Text(status.ifEmpty { "…" }, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.rsta_status_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(
                onClick = { showLog = !showLog },
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF007AFF)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.rsta_view_log), color = Color(0xFF007AFF), fontWeight = FontWeight.SemiBold)
            }
            if (showLog) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        log.takeLast(60).joinToString("\n").ifEmpty { stringResource(Res.string.rsta_log_empty) },
                        style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}
