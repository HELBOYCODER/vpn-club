package com.helboy.vpnclub.ui.proto.importing

import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.formatBytes
import dev.valid7996.narcicgeteway.crypto.NrgLink
import dev.valid7996.narcicgeteway.crypto.ZsxExpiredException
import dev.valid7996.narcicgeteway.crypto.ZsxKeyConsumedException
import dev.valid7996.narcicgeteway.crypto.ZsxLegacyException
import dev.valid7996.narcicgeteway.crypto.ZsxPasswordRequiredException
import dev.valid7996.narcicgeteway.crypto.ZsxTamperException
import dev.valid7996.narcicgeteway.crypto.ZsxVersionException
import dev.valid7996.narcicgeteway.crypto.ZsxWrongPasswordException
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.data.config.RelayAlreadyClaimedException
import dev.valid7996.narcicgeteway.data.config.RelayKeyRequiredException
import dev.valid7996.narcicgeteway.domain.config.AmneziaLink
import dev.valid7996.narcicgeteway.domain.config.ConfigParseException
import dev.valid7996.narcicgeteway.domain.config.ConfigParser
import dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor
import dev.valid7996.narcicgeteway.domain.config.OvpnConfig
import dev.valid7996.narcicgeteway.domain.config.Subscription

/*
 * Pure helpers for the import / subscriptions / deep-link / lock-share screens: nothing here touches a repository,
 * the clock (unless it is passed in) or Compose, so all of it is covered by plain JUnit tests.
 */

/** What a piece of pasted text most likely is. Decides how the import page treats it. */
enum class InputKind(val label: String) {
    Empty("خالی"),
    SubscriptionUrl("نشانی اشتراک"),
    Relay("لینک کوتاه قفل‌شده (NRG)"),
    Locked("کانفیگ قفل‌شده (NRG)"),
    Json("JSON"),
    Ovpn("فایل OpenVPN"),
    WireguardConf("فایل WireGuard"),
    Links("لینک کانفیگ"),
    Base64("متن رمز‌شده (Base64)"),
    Other("نامشخص"),
}

object ImportLogic {
    private val LINE_LINK = Regex("""^[A-Za-z][A-Za-z0-9+.\-]{1,19}://\S+""")
    private val BASE64_BODY = Regex("""^[A-Za-z0-9+/=_\-]+$""")
    private val URL_IN_TEXT = Regex("""https?://[^\s<>"'«»]+""", RegexOption.IGNORE_CASE)
    private val URL_TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '،', '؛')

    private fun String.withoutFormatChars(): String = filterNot { it.category == CharCategory.FORMAT }

    /** The text itself when it is one bare http(s) address (a subscription), else null. Same rule as the repository. */
    fun subscriptionUrlOf(text: String): String? {
        val t = text.withoutFormatChars().trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        val ok = (t.startsWith("http://", true) || t.startsWith("https://", true)) && t.length > "https://".length
        return t.takeIf { ok }
    }

    /** The one http(s) address inside a longer message, or null when there is none or more than one. */
    fun soleUrlIn(text: String): String? =
        URL_IN_TEXT.findAll(text.withoutFormatChars())
            .map { it.value.trimEnd(*URL_TRAILING) }
            .filter { it.length > "https://".length }
            .distinct()
            .toList()
            .singleOrNull()

    /** Non-blank trimmed lines. */
    fun lines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun isLinkLine(line: String): Boolean = LINE_LINK.containsMatchIn(line.trim())

    fun classify(text: String): InputKind {
        val t = text.withoutFormatChars().trim()
        if (t.isEmpty()) return InputKind.Empty
        if (subscriptionUrlOf(t) != null) return InputKind.SubscriptionUrl
        if (NrgLink.findRelay(t) != null) return InputKind.Relay
        if (LockedConfigExtractor.extractLink(t) != null) return InputKind.Locked
        if (t.startsWith("{") || t.startsWith("[") && !t.startsWith("[Interface]", ignoreCase = true)) return InputKind.Json
        if (runCatching { OvpnConfig.looksLikeOvpn(t) }.getOrDefault(false)) return InputKind.Ovpn
        if (runCatching { ConfigParser.looksLikeWireguardConf(t) }.getOrDefault(false)) return InputKind.WireguardConf
        val ls = lines(t)
        if (ls.isNotEmpty() && ls.all { isLinkLine(it) }) return InputKind.Links
        val compact = t.filterNot { it.isWhitespace() }
        if (compact.length >= 16 && BASE64_BODY.matches(compact)) return InputKind.Base64
        return InputKind.Other
    }

    // ----- files -----

    private fun hasHeader(bytes: ByteArray, a: Char, b: Char, c: Char, d: Char): Boolean =
        bytes.size >= 4 && bytes[0] == a.code.toByte() && bytes[1] == b.code.toByte() &&
            bytes[2] == c.code.toByte() && bytes[3] == d.code.toByte()

    /** True for the binary containers written by "share as file" (magic NRG1 / ZSX1). */
    fun hasSealedHeader(bytes: ByteArray): Boolean = hasHeader(bytes, 'N', 'R', 'G', '1') || hasHeader(bytes, 'Z', 'S', 'X', '1')

    /**
     * Turns a picked file into the text the importer understands: binary .nrg/.zsx become an `nrg://` link, a text
     * .nrg/.zsx is trimmed, anything else is decoded as UTF-8. Null when the file is empty or unreadable.
     */
    fun decodePickedFile(name: String, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        if (hasSealedHeader(bytes)) return NrgLink.encode(bytes)
        val sealedName = name.endsWith(".nrg", true) || name.endsWith(".zsx", true)
        val text = bytes.decodeToString().removePrefix("﻿")
        if (sealedName && '�' in text) return NrgLink.encode(bytes)
        if (text.isBlank()) return null
        return if (sealedName) text.trim() else text
    }

    /** "my config.ovpn" -> "my config"; null for a blank name. */
    fun baseName(fileName: String?): String? = fileName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }

    // ----- results -----

    fun formatSummary(s: ImportSummary): String {
        if (s.subscriptionName != null) {
            return if (s.savedOffline) {
                "اشتراک «${s.subscriptionName}» ذخیره شد؛ فعلاً به آن دسترسی نبود، بعداً به‌روزرسانی کنید"
            } else {
                "اشتراک «${s.subscriptionName}» اضافه شد و ${s.imported.fa()} سرور دریافت شد"
            }
        }
        val parts = buildList {
            if (s.imported > 0) add("${s.imported.fa()} کانفیگ اضافه شد")
            if (s.duplicates > 0) add(if (s.imported == 0 && s.failed == 0) "همه از قبل در لیست بودند (${s.duplicates.fa()} مورد)" else "${s.duplicates.fa()} تکراری")
            if (s.failed > 0) add("${s.failed.fa()} مورد ناموفق")
        }
        return if (parts.isEmpty()) "چیزی اضافه نشد" else parts.joinToString(" · ")
    }

    // ----- error wording -----

    const val MSG_EXPIRED = "این کانفیگ منقضی شده و اعتبارش تمام شده است."
    const val MSG_CONSUMED = "این کلید قبلاً یک بار استفاده و باطل شده است (کلید یک‌بار مصرف)."
    const val MSG_OTHER_DEVICE = "این کانفیگ برای دستگاه دیگری قفل شده و روی این دستگاه باز نمی‌شود. کد دستگاه خود را برای فرستندهٔ کانفیگ بفرستید."
    const val MSG_WRONG_KEY = "کلید وارد شده اشتباه است."
    const val MSG_NOTHING_FOUND = "در این متن کانفیگ پشتیبانی‌شده‌ای پیدا نشد."
    const val MSG_GENERIC = "ورود ناموفق بود؛ مطمئن شوید متن کامل و درست کپی شده است."

    /** A short Persian sentence for any import failure. Never contains a stack trace or a class name. */
    fun describeError(e: Throwable): String = when (e) {
        is ZsxExpiredException -> MSG_EXPIRED
        is ZsxKeyConsumedException -> MSG_CONSUMED
        is ZsxWrongPasswordException -> MSG_WRONG_KEY
        is ZsxPasswordRequiredException -> "این کانفیگ با کلید قفل شده است؛ کلید را وارد کنید."
        is ZsxTamperException -> "لینک یا فایل خراب است یا ناقص کپی شده است."
        is ZsxVersionException -> "این فایل با نسخهٔ دیگری ساخته شده و پشتیبانی نمی‌شود."
        is ZsxLegacyException -> "این کانفیگ قفل‌شده با نسخهٔ قدیمی ساخته شده و دیگر باز نمی‌شود."
        is RelayAlreadyClaimedException -> "این لینک قبلاً روی یک دستگاه دیگر فعال شده و دیگر قابل استفاده نیست."
        is RelayKeyRequiredException -> "برای این لینک کلید اختصاصی لازم است."
        is ConfigRepository.SubscriptionNotFetchedException ->
            if (e.savedForLater) "اشتراک «${e.name}» ذخیره شد ولی فعلاً در دسترس نیست."
            else "دریافت اشتراک ناموفق بود؛ نشانی و اینترنت را بررسی کنید."
        is ConfigParseException -> when (e.reason) {
            ConfigParseException.Reason.UnsupportedSsCipher -> "رمزنگاری این Shadowsocks پشتیبانی نمی‌شود."
            ConfigParseException.Reason.MissingSsCipher -> "در این لینک Shadowsocks روش رمزنگاری مشخص نشده است."
            ConfigParseException.Reason.UnsupportedSsPlugin -> "افزونهٔ این Shadowsocks پشتیبانی نمی‌شود."
            null -> "لینک کانفیگ نامعتبر است."
        }
        is AmneziaLink.UnsupportedException -> when (e.reason) {
            AmneziaLink.Reason.Backup -> "این یک پشتیبان Amnezia است؛ باید در خود Amnezia بازیابی شود."
            AmneziaLink.Reason.Subscription -> "اشتراک Amnezia فقط با سرورهای خود Amnezia کار می‌کند."
            AmneziaLink.Reason.NoRunnableContainer -> "در این کانفیگ Amnezia پروتکل قابل اجرایی نیست."
        }
        is OvpnConfig.UnsupportedException -> when (e.reason) {
            OvpnConfig.Reason.NoRemote -> "در این فایل OpenVPN آدرس سرور (remote) نیست."
            OvpnConfig.Reason.Tap -> "OpenVPN با حالت TAP پشتیبانی نمی‌شود."
            OvpnConfig.Reason.ExternalFiles -> "این فایل OpenVPN به فایل‌های بیرونی اشاره می‌کند؛ نسخهٔ یکپارچه (inline) را وارد کنید."
            OvpnConfig.Reason.UnsupportedCredentials -> "روش ورود این OpenVPN پشتیبانی نمی‌شود."
        }
        else -> {
            val m = e.message.orEmpty()
            when {
                // The engine's own Persian messages (device lock, relay) are already good for people.
                "قفل شده" in m && m.any { it in '؀'..'ۿ' } -> m
                m.equals("empty", true) -> "متنی برای ورود وجود ندارد."
                m.contains("no supported", true) || m.contains("no usable", true) || m.contains("no servers", true) -> MSG_NOTHING_FOUND
                else -> MSG_GENERIC
            }
        }
    }

    /** Wrong-key wording depends on whether the config was bound to a device. */
    fun describeUnlockError(e: Throwable, deviceBound: Boolean): String =
        if (e is ZsxWrongPasswordException) (if (deviceBound) MSG_OTHER_DEVICE else MSG_WRONG_KEY) else describeError(e)

    /** Why refreshing a subscription failed, in one line. */
    fun describeUpdateError(e: Throwable): String = when {
        e is ConfigRepository.SubscriptionUnreachableException -> "اتصال به سرور اشتراک برقرار نشد؛ اینترنت یا نشانی را بررسی کنید."
        e is IllegalArgumentException && e.message.orEmpty().contains("unknown", true) -> "این اشتراک دیگر وجود ندارد."
        e is IllegalArgumentException -> "پاسخ اشتراک کانفیگ قابل استفاده‌ای نداشت."
        else -> describeError(e).takeIf { it != MSG_GENERIC } ?: "به‌روزرسانی ناموفق بود."
    }

    // ----- subscriptions -----

    /** "https://user:pw@host.example/path/to/sub?token=abc" -> "host.example/path/to/…" (never shows userinfo or query). */
    fun shortUrl(url: String, max: Int = 36): String {
        val noScheme = url.trim().substringAfter("://")
        val noQuery = noScheme.substringBefore('?').substringBefore('#')
        val host = noQuery.substringBefore('/').substringAfterLast('@')
        val path = noQuery.substringAfter('/', "")
        val joined = if (path.isEmpty()) host else "$host/$path"
        return if (joined.length <= max) joined else joined.take(max - 1) + "…"
    }

    fun agoText(nowMs: Long, thenMs: Long): String {
        if (thenMs <= 0L) return "هنوز به‌روزرسانی نشده"
        val s = ((nowMs - thenMs) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "چند ثانیه پیش"
            s < 3600 -> "${(s / 60).fa()} دقیقه پیش"
            s < 86_400 -> "${(s / 3600).fa()} ساعت پیش"
            else -> "${(s / 86_400).fa()} روز پیش"
        }
    }

    fun usageText(sub: Subscription): String? {
        val used = sub.used
        val total = sub.total
        return when {
            used != null && total != null -> "${formatBytes(used)} از ${formatBytes(total)} مصرف شده"
            used != null -> "${formatBytes(used)} مصرف شده"
            total != null -> "سقف ${formatBytes(total)}"
            else -> null
        }
    }

    /** 0..1 of the quota used, or null when the quota is unknown. */
    fun usageFraction(sub: Subscription): Float? {
        val used = sub.used
        val total = sub.total
        return if (used != null && total != null && total > 0) (used.toDouble() / total).toFloat().coerceIn(0f, 1f) else null
    }

    fun expiryText(sub: Subscription, nowMs: Long): String? {
        val at = sub.expireAt ?: return null
        if (at <= nowMs) return "منقضی شده"
        val days = ((at - nowMs) / 86_400_000L).toInt()
        return when {
            days <= 0 -> "امروز تمام می‌شود"
            days == 1 -> "یک روز مانده"
            else -> "${days.fa()} روز مانده"
        }
    }

    fun autoUpdateText(enabled: Boolean, hours: Int): String =
        if (enabled) "به‌روزرسانی خودکار روشن است؛ هر ${hours.coerceIn(1, 168).fa()} ساعت (هر اشتراک می‌تواند بازهٔ خودش را داشته باشد)."
        else "به‌روزرسانی خودکار خاموش است؛ از تنظیمات می‌توانید روشنش کنید."

    fun isHttpUrl(text: String): Boolean = subscriptionUrlOf(text) != null

    // ----- locked export -----

    data class ExpiryOption(val code: Int, val label: String)

    val EXPIRY_OPTIONS: List<ExpiryOption> = listOf(
        ExpiryOption(0, "دائمی"),
        ExpiryOption(1, "۵ دقیقه (آزمایشی)"),
        ExpiryOption(2, "۱ ساعت"),
        ExpiryOption(3, "۶ ساعت"),
        ExpiryOption(4, "۲۴ ساعت"),
        ExpiryOption(5, "۳ روز"),
        ExpiryOption(6, "۷ روز"),
        ExpiryOption(7, "۳۰ روز"),
        ExpiryOption(8, "سفارشی"),
    )

    private const val MIN = 60_000L
    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L

    /** Absolute expiry for an option code (see [EXPIRY_OPTIONS]); null = never. [custom] is days/hours/minutes for code 8. */
    fun expiryFor(code: Int, nowMs: Long, customDays: Int = 0, customHours: Int = 0, customMinutes: Int = 0): Long? = when (code) {
        1 -> nowMs + 5 * MIN
        2 -> nowMs + HOUR
        3 -> nowMs + 6 * HOUR
        4 -> nowMs + DAY
        5 -> nowMs + 3 * DAY
        6 -> nowMs + 7 * DAY
        7 -> nowMs + 30 * DAY
        8 -> {
            val total = customDays.coerceAtLeast(0) * DAY + customHours.coerceAtLeast(0) * HOUR + customMinutes.coerceAtLeast(0) * MIN
            if (total > 0) nowMs + total else null
        }
        else -> null
    }

    fun remainingText(expiresAt: Long?, nowMs: Long): String {
        if (expiresAt == null) return "دائمی"
        val diff = expiresAt - nowMs
        if (diff <= 0) return "منقضی شده"
        val minutes = diff / MIN
        val days = minutes / (24 * 60)
        val hours = minutes % (24 * 60) / 60
        val mins = minutes % 60
        return when {
            days > 0 && hours > 0 -> "${days.fa()} روز و ${hours.fa()} ساعت دیگر"
            days > 0 -> "${days.fa()} روز دیگر"
            hours > 0 && mins > 0 -> "${hours.fa()} ساعت و ${mins.fa()} دقیقه دیگر"
            hours > 0 -> "${hours.fa()} ساعت دیگر"
            else -> "${mins.coerceAtLeast(1).fa()} دقیقه دیگر"
        }
    }

    private const val KEY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** "NG-XXXX-XXXX-XXXX" from 12 random bytes (no look-alike characters). */
    fun userKeyFrom(random: ByteArray): String {
        require(random.size >= 12) { "need 12 random bytes" }
        val chars = (0 until 12).map { KEY_ALPHABET[(random[it].toInt() and 0xFF) % KEY_ALPHABET.length] }
        return "NG-" + chars.chunked(4).joinToString("-") { it.joinToString("") }
    }

    /** Null when the choices can be sealed, otherwise what to fix. [mode] is "device", "key" or "public". */
    fun validateLockOptions(mode: String, deviceCode: String, key: String): String? = when {
        mode == "device" && deviceCode.isBlank() -> "کد دستگاه خریدار را وارد کنید."
        mode == "key" && key.isBlank() -> "کلید را وارد کنید یا یکی بسازید."
        mode == "key" && key.trim().length < 6 -> "کلید خیلی کوتاه است."
        else -> null
    }

    fun safeFileName(title: String, extension: String = "nrg"): String {
        val base = title.trim().ifEmpty { "config" }.replace(Regex("""[^A-Za-z0-9_.\-؀-ۿ]"""), "_").take(48)
        return "$base.$extension"
    }

    /** The message a seller pastes into a chat: name, validity, who it is for, the link, and (key mode) the key. */
    fun buildShareMessage(
        title: String,
        link: String,
        expiresAt: Long?,
        nowMs: Long,
        deviceCode: String? = null,
        key: String? = null,
        note: String = "",
    ): String = buildString {
        appendLine("کانفیگ قفل‌شده")
        appendLine("نام: ${title.ifBlank { "بدون نام" }}")
        appendLine("اعتبار: ${remainingText(expiresAt, nowMs)}")
        when {
            !deviceCode.isNullOrBlank() -> appendLine("فقط برای دستگاه با کد: $deviceCode")
            !key.isNullOrBlank() -> appendLine("کلید اختصاصی: $key")
            else -> appendLine("نوع قفل: عمومی")
        }
        if (note.isNotBlank()) appendLine("یادداشت: ${note.trim()}")
        appendLine()
        appendLine(link)
        appendLine()
        append("برای فعال‌سازی لینک بالا را در Hellboy در بخش «ورود کانفیگ» وارد کنید.")
    }
}

/** What an import produced. */
data class ImportSummary(
    val imported: Int,
    val duplicates: Int = 0,
    val failed: Int = 0,
    val firstFailure: String? = null,
    /** Set when a subscription was added. */
    val subscriptionName: String? = null,
    /** The subscription was kept but its address could not be reached right now. */
    val savedOffline: Boolean = false,
) {
    /** True when the list of servers changed (or a subscription was stored) so the host should jump to the list. */
    val changedList: Boolean get() = imported > 0 || savedOffline
}
