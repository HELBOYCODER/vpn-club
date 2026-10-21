package com.helboy.vpnclub.ui.proto.editors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoSection
import dev.valid7996.narcicgeteway.domain.config.SshProfile
import dev.valid7996.narcicgeteway.domain.config.VpnProfile

/** Add or edit an SSH tunnel. [existingId] null creates a new profile; [onBack] runs after saving or cancelling. */
@Composable
fun SshEditor(existingId: String?, onBack: () -> Unit) {
    EditorFrame("تونل SSH", existingId, onBack, owns = { it.isSsh }) { existing -> SshEditorBody(existing, onBack) }
}

@Composable
private fun SshEditorBody(existing: VpnProfile?, onBack: () -> Unit) {
    var form by remember(existing?.id) { mutableStateOf(SshForm.from(existing?.sshSettings(), existing?.name.orEmpty())) }
    val touched = rememberTouched(existing?.id)
    var saveError by remember { mutableStateOf<String?>(null) }
    val keyImport = rememberPemImport("PRIVATE KEY", "این فایل کلید خصوصی PEM نیست.") { form = form.copy(privateKey = it) }
    val check = form.check()

    ProtoSection(title = "سرور", hint = "آدرس و پورت سرور SSH که ترافیک از آن رد می‌شود.") {
        NameField(form.name, { form = form.copy(name = it) }, check, touched)
        EField("آدرس سرور", form.host, { form = form.copy(host = it) }, SshForm.F_HOST, check, touched, "proto_ssh_host",
            placeholder = "example.com", keyboard = KeyboardType.Uri)
        NumberField("پورت", form.port, { form = form.copy(port = it) }, SshForm.F_PORT, check, touched, "proto_ssh_port", "22", maxDigits = 5)
        EField("نام کاربری", form.username, { form = form.copy(username = it) }, "username", check, touched, "proto_ssh_user")
    }

    ProtoSection(title = "احراز هویت") {
        ProtoChoice(
            title = null,
            options = listOf(SshProfile.AUTH_PASSWORD to "رمز عبور", SshProfile.AUTH_KEY to "کلید خصوصی"),
            selected = form.authType,
            onSelect = { form = form.copy(authType = it) },
        )
        if (form.usesKey) {
            SecretField("کلید خصوصی (PEM)", form.privateKey, { form = form.copy(privateKey = it) }, SshForm.F_KEY, check, touched,
                "proto_ssh_key", minLines = 4, monospace = true)
            FileImportRow(
                title = "کلید از فایل",
                status = if (form.privateKey.isBlank()) null else "کلید بارگذاری شد",
                emptyText = "فایلی انتخاب نشده",
                onPick = keyImport::pick,
                onClear = { form = form.copy(privateKey = "") },
                busy = keyImport.busy,
                error = keyImport.error,
                tag = "proto_ssh_key_pick",
            )
            SecretField("عبارت عبور کلید (اختیاری)", form.passphrase, { form = form.copy(passphrase = it) }, "passphrase", check, touched,
                "proto_ssh_passphrase")
        } else {
            SecretField("رمز عبور", form.password, { form = form.copy(password = it) }, SshForm.F_PASSWORD, check, touched, "proto_ssh_password")
        }
    }

    SaveSection(
        valid = check.ok,
        onSave = {
            if (check.ok) saveGuarded({ saveError = it }) {
                EditorSave.ssh(ProtoEnv.repo, existing, form.name, form.toProfile())
                onBack()
            }
        },
        onCancel = onBack,
        saveError = saveError,
    )
}
