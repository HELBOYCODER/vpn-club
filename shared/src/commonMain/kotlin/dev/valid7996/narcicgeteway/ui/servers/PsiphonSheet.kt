@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.domain.config.PsiphonConfigBuilder
import dev.valid7996.narcicgeteway.domain.config.PsiphonProfile
import dev.valid7996.narcicgeteway.ui.components.PickerField

@Composable
fun PsiphonSheet(
    initial: PsiphonProfile? = null,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, PsiphonProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var country by remember { mutableStateOf(initial?.country ?: "") }
    var mode by remember { mutableStateOf(initial?.mode ?: PsiphonConfigBuilder.MODE_AUTO) }
    var cdnIps by remember { mutableStateOf(initial?.cdnIps ?: "") }
    var cdnSni by remember { mutableStateOf(initial?.cdnSni ?: "") }
    var beast by remember { mutableStateOf(initial?.beast ?: true) }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.2f)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(Res.string.psiphon_add_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A),
                modifier = Modifier.padding(vertical = 4.dp),
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.manual_remark)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            PsiphonCountryField(
                selected = country,
                onSelect = { country = it },
            )
            PickerField(
                label = stringResource(Res.string.psiphon_mode),
                options = PsiphonConfigBuilder.MODES.map { it to it },
                selected = mode,
                onSelect = { mode = it },
            )
            // The edge addresses and names are used in every mode that fronts (auto, direct and CDN), as in
            // Shirokhorshid; they used to show, and count, in CDN mode alone.
            OutlinedTextField(
                value = cdnIps,
                onValueChange = { cdnIps = it },
                label = { Text(stringResource(Res.string.psiphon_cdn_ips)) },
                supportingText = { Text(stringResource(Res.string.psiphon_cdn_hint)) },
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cdnSni,
                onValueChange = { cdnSni = it },
                label = { Text(stringResource(Res.string.psiphon_cdn_sni)) },
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            // Beast mode: every protocol on every server at once; Psiphon also remembers the route that worked.
            SettingToggleRow(
                title = stringResource(Res.string.psiphon_beast_title),
                summary = stringResource(Res.string.psiphon_beast_summary),
                checked = beast,
                onCheckedChange = { beast = it },
            )

            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        PsiphonProfile(
                            country = country.trim(),
                            mode = mode,
                            cdnIps = cdnIps.trim(),
                            cdnSni = cdnSni.trim(),
                            beast = beast,
                        ),
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007AFF),
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(stringResource(Res.string.action_save), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
