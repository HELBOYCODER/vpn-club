package com.helboy.vpnclub.ui.proto.tools

import com.helboy.vpnclub.ui.fa
import dev.valid7996.narcicgeteway.data.backup.BackupPayload
import dev.valid7996.narcicgeteway.domain.config.Subscription
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What a backup file holds, compared with what is on the device now. */
data class RestorePreview(
    val app: String,
    val version: Int,
    val createdAt: Long,
    val hasSettings: Boolean,
    val profiles: Int,
    val newProfiles: Int,
    val subscriptions: Int,
    val newSubscriptions: Int,
    val currentProfiles: Int,
    val currentSubscriptions: Int,
) {
    val isEmpty: Boolean get() = !hasSettings && profiles == 0 && subscriptions == 0
}

/** What the person chose to bring back. */
data class RestoreChoice(
    val settings: Boolean = true,
    val profiles: Boolean = true,
    val subscriptions: Boolean = true,
    val replace: Boolean = false,
) {
    val anything: Boolean get() = settings || profiles || subscriptions
}

fun previewOf(payload: BackupPayload, currentProfiles: List<VpnProfile>, currentSubscriptions: List<Subscription>): RestorePreview {
    val profileIds = currentProfiles.map { it.id }.toSet()
    val subIds = currentSubscriptions.map { it.id }.toSet()
    val subUrls = currentSubscriptions.map { it.url.lowercase() }.toSet()
    return RestorePreview(
        app = payload.app,
        version = payload.version,
        createdAt = payload.createdAt,
        hasSettings = payload.settings != null,
        profiles = payload.profiles.size,
        // same rules as ConfigRepository.restoreBackupData in "add" mode
        newProfiles = payload.profiles.count { it.id !in profileIds },
        subscriptions = payload.subscriptions.size,
        newSubscriptions = payload.subscriptions.count { it.id !in subIds && it.url.lowercase() !in subUrls },
        currentProfiles = currentProfiles.size,
        currentSubscriptions = currentSubscriptions.size,
    )
}

/** Plain-language lines telling what restoring with [choice] will do. */
fun describeRestore(p: RestorePreview, choice: RestoreChoice): List<String> = buildList {
    if (choice.settings) add(if (p.hasSettings) "تنظیمات برنامه با تنظیمات فایل جایگزین می‌شود." else "فایل تنظیمات ندارد؛ تنظیمات دست نمی‌خورد.")
    if (choice.profiles) {
        add(
            if (choice.replace) "${p.currentProfiles.fa()} سرور فعلی حذف و با ${p.profiles.fa()} سرور فایل جایگزین می‌شود."
            else "${p.newProfiles.fa()} سرور تازه اضافه می‌شود (${(p.profiles - p.newProfiles).fa()} سرور تکراری رد می‌شود).",
        )
    }
    if (choice.subscriptions) {
        add(
            if (choice.replace) "${p.currentSubscriptions.fa()} اشتراک فعلی حذف و با ${p.subscriptions.fa()} اشتراک فایل جایگزین می‌شود."
            else "${p.newSubscriptions.fa()} اشتراک تازه اضافه می‌شود (${(p.subscriptions - p.newSubscriptions).fa()} مورد تکراری رد می‌شود).",
        )
    }
    if (!choice.anything) add("هیچ بخشی انتخاب نشده است.")
}

/** True when this restore deletes data that exists now. */
fun isDestructive(p: RestorePreview, choice: RestoreChoice): Boolean =
    choice.settings && p.hasSettings || choice.replace && (choice.profiles && p.currentProfiles > 0 || choice.subscriptions && p.currentSubscriptions > 0)

/** One-line outcome shown after a restore. */
fun restoreSummary(settingsApplied: Boolean, profilesAdded: Int, subscriptionsAdded: Int): String {
    val parts = buildList {
        if (settingsApplied) add("تنظیمات")
        if (profilesAdded > 0) add("${profilesAdded.fa()} سرور")
        if (subscriptionsAdded > 0) add("${subscriptionsAdded.fa()} اشتراک")
    }
    return if (parts.isEmpty()) "چیز تازه‌ای برای بازیابی نبود." else "بازیابی شد: ${parts.joinToString("، ")}"
}

/** Persian message for a failed parse; never leaks the exception text. */
fun parseFailureMessage(): String =
    "این فایل نسخهٔ پشتیبان معتبر نیست. فایل باید همان JSON ساخته‌شده توسط «ایجاد نسخهٔ پشتیبان» باشد."

fun backupFileName(nowMs: Long): String =
    "hellboy-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(nowMs)) + ".json"

/** "1403/07/29 14:05"-style stamp is not needed; a plain Gregorian date with Persian digits is enough. */
fun formatStamp(ms: Long): String =
    if (ms <= 0L) "—" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms)).fa()
