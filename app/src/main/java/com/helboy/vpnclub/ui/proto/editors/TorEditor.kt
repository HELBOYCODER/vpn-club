package com.helboy.vpnclub.ui.proto.editors

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoNavRow
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import dev.valid7996.narcicgeteway.data.settings.SettingsRepository
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.domain.config.VpnProfile

/**
 * Add or edit a Tor profile. The profile is only a name; the bridges are app-wide Tor settings, edited here and on the
 * bridges page, and they apply as soon as they are changed.
 */
@Composable
fun TorEditor(existingId: String?, onBack: () -> Unit) {
    var showBridges by remember { mutableStateOf(false) }
    if (showBridges) {
        TorBridgesPage(onBack = { showBridges = false })
    } else {
        EditorFrame("Tor", existingId, onBack, owns = { it.isTor }) { existing -> TorBody(existing, onBack, openBridges = { showBridges = true }) }
    }
}

@Composable
private fun TorBody(existing: VpnProfile?, onBack: () -> Unit, openBridges: () -> Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    val touched = rememberTouched(existing?.id)
    var saveError by remember { mutableStateOf<String?>(null) }
    val settingsRepo = remember { runCatching { ProtoEnv.settings }.getOrNull() }

    ProtoSection(title = "پروفایل") {
        NameField(name, { name = it }, FormCheck(), touched)
    }

    if (settingsRepo == null) {
        ProtoSection(title = "پل‌ها") { HintText("تنظیمات برنامه در دسترس نیست؛ پل‌ها را بعداً از صفحهٔ تنظیمات تغییر دهید.", error = true) }
    } else {
        BridgeSection(settingsRepo, openBridges)
    }

    SaveSection(
        valid = true,
        onSave = {
            saveGuarded({ saveError = it }) {
                EditorSave.tor(ProtoEnv.repo, existing, name)
                onBack()
            }
        },
        onCancel = onBack,
        saveError = saveError,
    )
}

@Composable
private fun BridgeSection(settingsRepo: SettingsRepository, openBridges: () -> Unit) {
    val s by settingsRepo.settings.collectAsState()
    fun change(f: (AppSettings) -> AppSettings) = settingsRepo.update(f)

    ProtoSection(
        title = "پل‌ها",
        hint = "این تنظیمات برای همهٔ پروفایل‌های Tor مشترک است و همان لحظه اعمال می‌شود.",
    ) {
        Text(TorBridgeSettings.summary(s), color = TextPrimary, fontSize = 13.sp, modifier = Modifier.testTag("proto_tor_summary"))
        TorBridgeSettings.problem(s)?.let { HintText(it, error = true) }
        ProtoChoice(
            title = "حالت پل‌ها",
            options = TorBridgeSettings.MODES,
            selected = s.torBridgesMode,
            onSelect = { m -> change { it.copy(torBridgesMode = m) } },
        )
        if (s.torBridgesMode != TorBridgeSettings.MODE_NONE) {
            val transports = TorBridgeSettings.transports(s.torBridgesMode)
            ProtoChoice(
                title = "نوع پل",
                options = transports.map { it to TorBridgeSettings.transportLabel(it) },
                selected = s.torBridgeTransport,
                onSelect = { t -> change { it.copy(torBridgeTransport = t) } },
            )
            if (s.torBridgeTransport == "snowflake") {
                ProtoChoice(
                    title = "نقطهٔ دیدار (rendezvous)",
                    options = TorBridgeSettings.RENDEZVOUS,
                    selected = s.torSnowflakeRendezvous,
                    onSelect = { r -> change { it.copy(torSnowflakeRendezvous = r) } },
                )
                com.helboy.vpnclub.ui.proto.ProtoField(
                    label = "سرورهای STUN برای Snowflake (خالی = پیش‌فرض)",
                    value = s.torSnowflakeStun,
                    onValueChange = { v -> change { it.copy(torSnowflakeStun = v) } },
                    tag = "proto_tor_stun",
                )
            }
            if (s.torBridgesMode == TorBridgeSettings.MODE_OWN) {
                com.helboy.vpnclub.ui.proto.ProtoField(
                    label = "خطوط پل شما (هر خط یکی)",
                    value = s.torOwnBridges,
                    onValueChange = { v -> change { it.copy(torOwnBridges = v) } },
                    placeholder = "obfs4 1.2.3.4:443 CERT… iat-mode=0",
                    singleLine = false,
                    minLines = 3,
                    monospace = true,
                    tag = "proto_tor_own_bridges",
                )
            } else {
                ProtoNavRow(
                    title = "مرور و انتخاب پل‌ها",
                    hint = "مرور، پینگ و انتخاب پل‌های داخلی",
                    onClick = openBridges,
                    icon = Icons.Rounded.Hub,
                    tag = "proto_tor_open_bridges",
                )
            }
        }
        Text("بدون انتخاب پل، اتصال مستقیم به شبکهٔ تور برقرار می‌شود؛ در شبکه‌های فیلترشده معمولاً پل لازم است.", color = TextSecondary, fontSize = 11.sp)
    }
}
