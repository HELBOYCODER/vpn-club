package dev.valid7996.narcicgeteway.ui.settings.backup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valid7996.narcicgeteway.data.backup.BackupManager
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.ic_check
import dev.valid7996.narcicgeteway.shared.resources.ic_content_paste
import dev.valid7996.narcicgeteway.shared.resources.ic_description
import dev.valid7996.narcicgeteway.shared.resources.ic_download
import dev.valid7996.narcicgeteway.shared.resources.ic_ios_share
import dev.valid7996.narcicgeteway.shared.resources.ic_settings
import dev.valid7996.narcicgeteway.shared.resources.ic_sync
import dev.valid7996.narcicgeteway.shared.resources.ic_upload
import dev.valid7996.narcicgeteway.ui.components.PageHeader
import dev.valid7996.narcicgeteway.ui.components.ProvidePageBack
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreSheet(
    settings: AppSettings,
    repository: ConfigRepository,
    onDismiss: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.3f) else Color(0xFF3C3C43).copy(alpha = 0.3f)),
            )
        },
    ) {
        ProvidePageBack(onBack = onDismiss) {
            BackupRestoreScreen(
                settings = settings,
                repository = repository,
                contentPadding = PaddingValues(bottom = 32.dp),
                onUpdateSettings = onUpdateSettings,
            )
        }
    }
}

@Composable
fun BackupRestoreScreen(
    settings: AppSettings,
    repository: ConfigRepository,
    contentPadding: PaddingValues = PaddingValues(),
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val platform = LocalPlatform.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val scope = rememberCoroutineScope()
    val profiles = repository.profiles.value
    val subscriptions = repository.subscriptions.value

    var backupCopied by remember { mutableStateOf(false) }
    var restoreInput by remember { mutableStateOf("") }
    var restoreSettingsChecked by remember { mutableStateOf(true) }
    var restoreProfilesChecked by remember { mutableStateOf(true) }
    var restoreSubsChecked by remember { mutableStateOf(true) }
    var replaceExisting by remember { mutableStateOf(false) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    var restoreSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(
            title = "پشتیبان‌گیری و بازیابی",
            subtitle = "انتقال و ذخیره کامل تنظیمات، سرورها و اشتراک‌ها",
        )

        // ─── 1. Export Card ────────────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDark) Color(0xFF242426) else Color(0xFFF2F2F7),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_upload),
                        contentDescription = null,
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ایجاد نسخه پشتیبان (Export Backup)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }

                Text(
                    "یک خروجی استاندارد شامل تمام تنظیمات نرم‌افزار، ${profiles.size} سرور و ${subscriptions.size} لینک اشتراک جهت انتقال یا نگهداری ایجاد می‌شود.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 18.sp,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val backupText = BackupManager.exportBackup(
                                settings = settings,
                                profiles = profiles,
                                subscriptions = subscriptions,
                            )
                            platform.copyToClipboard(backupText)
                            backupCopied = true
                            platform.toast("متن پشتیبان در کلیپ‌بورد کپی شد")
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                    ) {
                        Icon(
                            painter = painterResource(if (backupCopied) Res.drawable.ic_check else Res.drawable.ic_content_paste),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (backupCopied) "کپی شد" else "کپی بکاپ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            val backupText = BackupManager.exportBackup(
                                settings = settings,
                                profiles = profiles,
                                subscriptions = subscriptions,
                            )
                            platform.shareFiles(listOf("narcic-backup.json" to backupText.encodeToByteArray()))
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_download),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("ذخیره فایل JSON", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            val backupText = BackupManager.exportBackup(
                                settings = settings,
                                profiles = profiles,
                                subscriptions = subscriptions,
                            )
                            platform.shareText(backupText)
                        },
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_ios_share),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // ─── 2. Import Card ────────────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDark) Color(0xFF242426) else Color(0xFFF2F2F7),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_download),
                            contentDescription = null,
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "بازیابی نسخه پشتیبان (Restore)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val picked = platform.pickFileBytes() ?: return@launch
                                    val text = picked.bytes.decodeToString().trim()
                                    if (text.isNotBlank()) {
                                        restoreInput = text
                                        restoreMessage = null
                                        platform.toast("فایل ${picked.name} انتخاب شد")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_description),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("انتخاب فایل JSON", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val clip = platform.readClipboard()
                                if (!clip.isNullOrBlank()) {
                                    restoreInput = clip.trim()
                                    platform.toast("متن از کلیپ‌بورد جای‌گذاری شد")
                                } else {
                                    platform.toast("کلیپ‌بورد خالی است")
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_content_paste),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("جای‌گذاری", fontSize = 11.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = restoreInput,
                    onValueChange = {
                        restoreInput = it
                        restoreMessage = null
                    },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    placeholder = { Text("متن پشتیبان JSON را در اینجا وارد یا جای‌گذاری کنید...") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF34C759),
                        unfocusedBorderColor = if (isDark) Color(0xFF3C3C3E) else Color(0xFFD1D1D6),
                    ),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                )

                // Selectable elements to restore
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("بخش‌های مورد نظر جهت بازیابی:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = restoreProfilesChecked,
                            onCheckedChange = { restoreProfilesChecked = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF34C759)),
                        )
                        Text("سرورها و کانفیگ‌ها (Servers)", fontSize = 13.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = restoreSubsChecked,
                            onCheckedChange = { restoreSubsChecked = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF34C759)),
                        )
                        Text("اشتراک‌ها (Subscriptions)", fontSize = 13.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = restoreSettingsChecked,
                            onCheckedChange = { restoreSettingsChecked = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF34C759)),
                        )
                        Text("تنظیمات ظاهری و هسته برنامه (Settings)", fontSize = 13.sp)
                    }
                }

                HorizontalDivider(color = if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("جایگزینی کامل (Replace All)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("حذف کانفیگ‌های قبلی و جایگزینی با فایل بکاپ", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = replaceExisting, onCheckedChange = { replaceExisting = it })
                }

                Button(
                    onClick = {
                        val parsed = BackupManager.parseBackup(restoreInput)
                        parsed.fold(
                            onSuccess = { backup ->
                                var details = mutableListOf<String>()

                                if (restoreSettingsChecked && backup.settings != null) {
                                    onUpdateSettings { backup.settings }
                                    details.add("تنظیمات برنامه")
                                }

                                val profilesToRestore = if (restoreProfilesChecked) backup.profiles else null
                                val subsToRestore = if (restoreSubsChecked) backup.subscriptions else null

                                val (restoredProfiles, restoredSubs) = repository.restoreBackupData(
                                    newProfiles = profilesToRestore,
                                    newSubscriptions = subsToRestore,
                                    replaceProfiles = replaceExisting,
                                    replaceSubscriptions = replaceExisting,
                                )

                                if (restoredProfiles > 0) details.add("$restoredProfiles سرور")
                                if (restoredSubs > 0) details.add("$restoredSubs اشتراک")

                                restoreSuccess = true
                                restoreMessage = "با موفقیت بازیابی شد: ${details.joinToString("، ")}"
                                platform.toast(restoreMessage!!)
                            },
                            onFailure = { err ->
                                restoreSuccess = false
                                restoreMessage = "خطا در خواندن فایل بکاپ: فرمت JSON نامعتبر است."
                                platform.toast(restoreMessage!!)
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = restoreInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                ) {
                    Icon(painter = painterResource(Res.drawable.ic_sync), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("شروع بازیابی اطلاعات", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // Status message
                AnimatedVisibility(visible = restoreMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (restoreSuccess) Color(0xFF34C759).copy(alpha = 0.15f) else Color(0xFFFF3B30).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (restoreSuccess) Color(0xFF34C759).copy(alpha = 0.4f) else Color(0xFFFF3B30).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = restoreMessage ?: "",
                            color = if (restoreSuccess) Color(0xFF34C759) else Color(0xFFFF3B30),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}
