package com.helboy.vpnclub.ui.proto.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoConfirm
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.data.backup.BackupManager
import dev.valid7996.narcicgeteway.data.backup.BackupPayload
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface RestoreOutcome {
    data class Done(val text: String) : RestoreOutcome
    data class Failed(val text: String) : RestoreOutcome
}

/**
 * Backup and restore of settings, servers and subscriptions in the engine's own backup format
 * ([BackupManager]). The reference has no password, so the file is plain JSON and the page says so.
 */
@Composable
fun BackupPage(onBack: () -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val settings by ProtoEnv.settings.settings.collectAsState()
    val profiles by ProtoEnv.repo.profiles.collectAsState()
    val subscriptions by ProtoEnv.repo.subscriptions.collectAsState()

    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    var text by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<BackupPayload?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var choice by remember { mutableStateOf(RestoreChoice()) }
    var confirming by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<RestoreOutcome?>(null) }

    fun inspect(newText: String) {
        text = newText
        outcome = null
        parsed = null
        parseError = null
        if (newText.isBlank()) return
        scope.launch {
            val result = withContext(Dispatchers.Default) { BackupManager.parseBackup(newText) }
            // ignore a result for text the person has already replaced
            if (text != newText) return@launch
            result.fold(
                onSuccess = { parsed = it },
                onFailure = { parseError = parseFailureMessage() },
            )
        }
    }

    suspend fun buildBackup(): String = withContext(Dispatchers.Default) {
        BackupManager.exportBackup(settings, profiles, subscriptions)
    }

    fun create(action: suspend (String) -> Unit) {
        if (creating) return
        creating = true
        createError = null
        scope.launch {
            try {
                action(buildBackup())
            } catch (e: Exception) {
                createError = "نسخهٔ پشتیبان ساخته نشد. دوباره امتحان کنید."
            } finally {
                creating = false
            }
        }
    }

    val preview = remember(parsed, profiles, subscriptions) { parsed?.let { previewOf(it, profiles, subscriptions) } }

    if (confirming && preview != null) {
        ProtoConfirm(
            title = "بازیابی انجام شود؟",
            text = describeRestore(preview, choice).joinToString("\n"),
            confirmLabel = "بازیابی",
            danger = isDestructive(preview, choice),
            onConfirm = {
                confirming = false
                val payload = parsed ?: return@ProtoConfirm
                restoring = true
                scope.launch {
                    outcome = try {
                        val c = choice
                        val settingsApplied = c.settings && payload.settings != null
                        if (settingsApplied) ProtoEnv.settings.update { payload.settings!! }
                        val (p, s) = withContext(Dispatchers.Default) {
                            ProtoEnv.repo.restoreBackupData(
                                newProfiles = if (c.profiles) payload.profiles else null,
                                newSubscriptions = if (c.subscriptions) payload.subscriptions else null,
                                replaceProfiles = c.replace,
                                replaceSubscriptions = c.replace,
                            )
                        }
                        val summary = restoreSummary(settingsApplied, p, s)
                        platform.toast(summary)
                        RestoreOutcome.Done(summary)
                    } catch (e: Exception) {
                        RestoreOutcome.Failed("بازیابی کامل نشد. داده‌های فعلی ممکن است بخشی تغییر کرده باشند؛ فایل را بررسی و دوباره امتحان کنید.")
                    } finally {
                        restoring = false
                    }
                }
            },
            onDismiss = { confirming = false },
        )
    }

    ProtoPage(title = "پشتیبان‌گیری و بازیابی", onBack = onBack) {
        ProtoSection(
            title = "ساخت نسخهٔ پشتیبان",
            hint = "یک فایل JSON با همهٔ تنظیمات، سرورها و اشتراک‌ها.",
        ) {
            Text("شامل:", color = TextSecondary, fontSize = 12.sp)
            Text("• تنظیمات برنامه و هسته (از جمله قاعده‌های مسیریابی و برنامه‌های per-app)", color = TextPrimary, fontSize = 13.sp)
            Text("• ${profiles.size.fa()} سرور / کانفیگ", color = TextPrimary, fontSize = 13.sp, modifier = Modifier.testTag("proto_backup_profiles"))
            Text("• ${subscriptions.size.fa()} اشتراک", color = TextPrimary, fontSize = 13.sp)
            Text(
                "هشدار: فایل رمزنگاری نمی‌شود و اطلاعات اتصال سرورهای شما (کلیدها و رمزها) را دارد. آن را فقط جای امن نگه دارید یا بفرستید.",
                color = StatusAmber, fontSize = 12.sp,
            )
            ProtoButton(
                "اشتراک‌گذاری به‌صورت فایل", Icons.Rounded.Backup, running = creating, tag = "proto_backup_share",
                onClick = {
                    create { body ->
                        platform.shareFiles(listOf(backupFileName(System.currentTimeMillis()) to body.encodeToByteArray()))
                    }
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtoOutlined("کپی متن", modifier = Modifier.weight(1f), enabled = !creating, tag = "proto_backup_copy", onClick = {
                    create { body ->
                        platform.copyToClipboard(body)
                        platform.toast("متن پشتیبان کپی شد")
                    }
                })
                ProtoOutlined("اشتراک متن", modifier = Modifier.weight(1f), enabled = !creating, onClick = {
                    create { body -> platform.shareText(body) }
                })
            }
            createError?.let { Text(it, color = StatusRed, fontSize = 12.sp, modifier = Modifier.testTag("proto_backup_error")) }
        }

        ProtoSection(title = "بازیابی از نسخهٔ پشتیبان", hint = "فایل را انتخاب کنید یا متن را جای‌گذاری کنید؛ پیش از هر تغییری خلاصهٔ آن را می‌بینید.") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtoOutlined("انتخاب فایل", modifier = Modifier.weight(1f), tag = "proto_restore_pick", onClick = {
                    scope.launch {
                        val pick = platform.pickFileBytes() ?: return@launch
                        val decoded = withContext(Dispatchers.Default) { runCatching { pick.bytes.decodeToString().trim() }.getOrNull() }
                        if (decoded == null) { parseError = parseFailureMessage(); parsed = null } else inspect(decoded)
                    }
                })
                ProtoOutlined("جای‌گذاری", modifier = Modifier.weight(1f), onClick = {
                    val clip = platform.readClipboard()
                    if (clip.isNullOrBlank()) platform.toast("کلیپ‌بورد خالی است") else inspect(clip.trim())
                })
            }
            ProtoField(
                "متن پشتیبان", text, { inspect(it) },
                placeholder = "{ \"app\": \"…\", \"settings\": {…}, \"profiles\": […] }",
                singleLine = false, minLines = 3, monospace = true, tag = "proto_restore_text",
                error = parseError,
            )

            val p = preview
            if (p != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("proto_restore_preview")) {
                    Text("پیش‌نمایش فایل", color = TextPrimary, fontSize = 13.sp)
                    Text("ساخته‌شده: ${formatStamp(p.createdAt)} · نسخهٔ قالب ${p.version.fa()}", color = TextTertiary, fontSize = 11.sp)
                    Text(
                        (if (p.hasSettings) "تنظیمات، " else "") +
                            "${p.profiles.fa()} سرور، ${p.subscriptions.fa()} اشتراک",
                        color = TextSecondary, fontSize = 12.sp,
                    )
                    if (p.isEmpty) Text("این فایل داده‌ای برای بازیابی ندارد.", color = StatusRed, fontSize = 12.sp)
                }
                if (!p.isEmpty) {
                    Column {
                        Text("بخش‌های بازیابی", color = TextSecondary, fontSize = 12.sp)
                        CheckRow("تنظیمات برنامه", choice.settings && p.hasSettings, p.hasSettings) { choice = choice.copy(settings = it) }
                        CheckRow("سرورها (${p.profiles.fa()})", choice.profiles && p.profiles > 0, p.profiles > 0) { choice = choice.copy(profiles = it) }
                        CheckRow("اشتراک‌ها (${p.subscriptions.fa()})", choice.subscriptions && p.subscriptions > 0, p.subscriptions > 0) { choice = choice.copy(subscriptions = it) }
                    }
                    ProtoSwitch(
                        "جایگزینی کامل",
                        choice.replace,
                        { choice = choice.copy(replace = it) },
                        subtitle = "سرورها و اشتراک‌های فعلی حذف و با فایل جایگزین شوند (به‌جای افزودن موارد تازه)",
                        tag = "proto_restore_replace",
                    )
                    val effective = choice.copy(
                        settings = choice.settings && p.hasSettings,
                        profiles = choice.profiles && p.profiles > 0,
                        subscriptions = choice.subscriptions && p.subscriptions > 0,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.testTag("proto_restore_effects")) {
                        Text("با بازیابی:", color = TextSecondary, fontSize = 12.sp)
                        describeRestore(p, effective).forEach { Text("• $it", color = TextPrimary, fontSize = 12.sp) }
                    }
                    ProtoButton(
                        "بازیابی", Icons.Rounded.Restore, running = restoring, enabled = effective.anything, tag = "proto_restore_go",
                        onClick = { choice = effective; confirming = true },
                    )
                }
            }

            when (val o = outcome) {
                is RestoreOutcome.Done -> Text(o.text, color = StatusGreen, fontSize = 13.sp, modifier = Modifier.testTag("proto_restore_done"))
                is RestoreOutcome.Failed -> Text(o.text, color = StatusRed, fontSize = 13.sp, modifier = Modifier.testTag("proto_restore_failed"))
                null -> {}
            }
        }
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(title, color = if (enabled) TextPrimary else TextTertiary, fontSize = 13.sp)
    }
}
