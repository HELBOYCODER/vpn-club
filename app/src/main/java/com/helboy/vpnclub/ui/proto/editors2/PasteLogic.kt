package com.helboy.vpnclub.ui.proto.editors2

import com.helboy.vpnclub.ui.fa
import dev.valid7996.narcicgeteway.crypto.ZsxExpiredException
import dev.valid7996.narcicgeteway.crypto.ZsxKeyConsumedException
import dev.valid7996.narcicgeteway.crypto.ZsxPasswordRequiredException
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.domain.config.AetherLink
import dev.valid7996.narcicgeteway.domain.config.AmneziaLink
import dev.valid7996.narcicgeteway.domain.config.ConfigParseException
import dev.valid7996.narcicgeteway.domain.config.ConfigParser
import dev.valid7996.narcicgeteway.domain.config.CustomConfig
import dev.valid7996.narcicgeteway.domain.config.Jsonc
import dev.valid7996.narcicgeteway.domain.config.NrgLink
import dev.valid7996.narcicgeteway.domain.config.OvpnConfig
import dev.valid7996.narcicgeteway.domain.config.SingBoxJson
import dev.valid7996.narcicgeteway.domain.config.SniSpoofLink
import dev.valid7996.narcicgeteway.domain.config.SshLink
import dev.valid7996.narcicgeteway.domain.config.SsdLink
import dev.valid7996.narcicgeteway.domain.config.SubscriptionParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * What the manual editor does with text that is pasted or read from a file: recognising what it is, the JSON
 * checks (the same two the reference editor makes: an Xray config or a sing-box one), and the wording of why an
 * import failed. All pure, so plain JUnit covers it.
 */

/** What a pasted text is, as far as the importer will treat it. */
sealed interface PasteKind {
    data object Empty : PasteKind

    /** One or more share links (vless://, vmess://, ss://, wireguard:// ...). [single] is the link when there is exactly one. */
    data class Links(val count: Int, val single: String?) : PasteKind

    data object SubscriptionUrl : PasteKind

    data class XrayJson(val isList: Boolean) : PasteKind

    data class SingBox(val shape: SingBoxJson.Shape) : PasteKind

    data object Ovpn : PasteKind

    data object WireguardConf : PasteKind

    /** A link of a managed protocol (aether://, ssh://, narcicgeteway://, snispoof://, vpn:// ...): the importer handles it. */
    data class ManagedLink(val word: String) : PasteKind

    data class BrokenJson(val reason: String) : PasteKind

    data object UnknownJson : PasteKind

    data object Unknown : PasteKind

    /** True when the importer has something to do with the text. */
    val importable: Boolean
        get() = when (this) {
            Empty, is BrokenJson, UnknownJson, Unknown -> false
            else -> true
        }
}

object PasteDetector {

    fun detect(text: String): PasteKind {
        val t = text.trim().trimStart('﻿').trim()
        if (t.isEmpty()) return PasteKind.Empty
        val firstLine = t.lineSequence().first().trim()

        if (SshLink.isSshLink(firstLine)) return PasteKind.ManagedLink("ssh://")
        if (AetherLink.isAetherLink(firstLine)) return PasteKind.ManagedLink("aether://")
        if (NrgLink.isNrgLink(t)) return PasteKind.ManagedLink("narcicgeteway://")
        if (t.startsWith("mhrv-rs://", ignoreCase = true) ||
            (t.startsWith("{") && "\"script_id" in t && "\"auth_key\"" in t)
        ) {
            return PasteKind.ManagedLink("MHRV")
        }
        if (SniSpoofLink.isSniSpoofLink(t)) return PasteKind.ManagedLink("snispoof://")
        if (SsdLink.isSsdLink(t)) return PasteKind.ManagedLink("ssd://")
        if (AmneziaLink.isAmneziaLink(t) || AmneziaLink.isAmneziaJson(t) || AmneziaLink.isAmneziaBlob(t)) {
            return PasteKind.ManagedLink("Amnezia")
        }
        if (OvpnConfig.looksLikeOvpn(t)) return PasteKind.Ovpn
        if (ConfigParser.looksLikeWireguardConf(t)) return PasteKind.WireguardConf

        if (t.startsWith("{") || t.startsWith("[")) {
            val shape = SingBoxJson.shapeOf(t)
            if (shape != SingBoxJson.Shape.None) return PasteKind.SingBox(shape)
            if (CustomConfig.looksLikeCustomJson(t)) {
                return PasteKind.XrayJson(isList = Jsonc.strip(t).trim().startsWith("["))
            }
            val parsed = JsonRules.parse(t)
            return if (parsed.isFailure) PasteKind.BrokenJson(JsonRules.brokenText(parsed.exceptionOrNull())) else PasteKind.UnknownJson
        }

        if ((t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) &&
            t.none { it.isWhitespace() }
        ) {
            return PasteKind.SubscriptionUrl
        }

        val links = SubscriptionParser.extractLinks(t)
        if (links.isNotEmpty()) return PasteKind.Links(links.size, links.singleOrNull())
        return PasteKind.Unknown
    }

    /** One line saying what was recognised, in Persian. */
    fun label(kind: PasteKind): String = when (kind) {
        PasteKind.Empty -> "متنی وارد نشده است."
        is PasteKind.Links ->
            if (kind.count == 1) "یک لینک سرور شناسایی شد." else "${kind.count.fa()} لینک سرور شناسایی شد."
        PasteKind.SubscriptionUrl -> "نشانی اشتراک؛ فهرست سرورها از اینترنت گرفته می‌شود."
        is PasteKind.XrayJson -> if (kind.isList) "فهرستی از کانفیگ‌های JSON هستهٔ Xray" else "کانفیگ JSON هستهٔ Xray"
        is PasteKind.SingBox -> when (kind.shape) {
            SingBoxJson.Shape.FullConfig -> "کانفیگ کامل sing-box"
            SingBoxJson.Shape.Outbound -> "یک سرور sing-box"
            SingBoxJson.Shape.OutboundList -> "فهرست سرورهای sing-box"
            SingBoxJson.Shape.Fragment -> "تکه‌ای از کانفیگ sing-box (سرورهایش جدا اضافه می‌شوند)"
            SingBoxJson.Shape.None -> "JSON"
        }
        PasteKind.Ovpn -> "پروفایل OpenVPN"
        PasteKind.WireguardConf -> "کانفیگ WireGuard / AmneziaWG (فایل .conf)"
        is PasteKind.ManagedLink -> "لینک ${kind.word}؛ با ویرایشگر مخصوص خودش اضافه می‌شود."
        is PasteKind.BrokenJson -> kind.reason
        PasteKind.UnknownJson -> "این JSON نه کانفیگ Xray است و نه sing-box."
        PasteKind.Unknown -> "این متن لینک یا کانفیگ شناخته‌شده‌ای نیست؛ آن را بررسی کنید."
    }
}

enum class JsonFlavour { Xray, SingBox }

/** The result of checking an edited JSON: [ok] means it can be saved. */
data class JsonCheck(val ok: Boolean, val parsed: Boolean, val message: String)

object JsonRules {
    private val reader = Json { prettyPrint = false }
    private val prettyWriter = Json { prettyPrint = true }

    const val XRAY_INVALID = "کانفیگ JSON معتبر نیست"
    const val SINGBOX_INVALID = "JSON معتبر sing-box نیست"

    /** [text] parsed the way the importer reads it: comments and trailing commas are allowed. */
    fun parse(text: String): Result<JsonElement> = runCatching { reader.parseToJsonElement(Jsonc.strip(text)) }

    fun brokenText(error: Throwable?): String = "خطای JSON: ${error?.message?.take(120).orEmpty()}"

    fun check(text: String, flavour: JsonFlavour, allowList: Boolean = false): JsonCheck {
        if (text.isBlank()) return JsonCheck(ok = false, parsed = false, message = "متنی وارد نشده است.")
        val parsed = parse(text)
        if (parsed.isFailure) return JsonCheck(ok = false, parsed = false, message = brokenText(parsed.exceptionOrNull()))
        val usable = when (flavour) {
            JsonFlavour.Xray -> CustomConfig.looksLikeCustomJson(text) &&
                (allowList || parsed.getOrNull() is JsonObject)
            JsonFlavour.SingBox -> SingBoxJson.shapeOf(text) != SingBoxJson.Shape.None
        }
        if (!usable) {
            return JsonCheck(
                ok = false,
                parsed = true,
                message = if (flavour == JsonFlavour.Xray) XRAY_INVALID else SINGBOX_INVALID,
            )
        }
        val lines = text.count { it == '\n' } + 1
        return JsonCheck(ok = true, parsed = true, message = "JSON درست است · ${lines.fa()} خط")
    }

    /** [text] re-indented, or null when it is not valid JSON. */
    fun pretty(text: String): String? =
        parse(text).getOrNull()?.let { prettyWriter.encodeToString(JsonElement.serializer(), it) }

    /** [text] on as few characters as possible, or null when it is not valid JSON. */
    fun minify(text: String): String? =
        parse(text).getOrNull()?.let { reader.encodeToString(JsonElement.serializer(), it) }
}

/** Why an import failed, in words that say what to do. */
object ImportErrors {
    const val GENERIC = "این کانفیگ خوانده نشد؛ متن را بررسی کنید و دوباره امتحان کنید."

    fun text(e: Throwable): String {
        e.message?.takeIf { it.contains("قفل شده است") }?.let { return it }
        return when {
            e is ZsxKeyConsumedException ->
                "این کلید اختصاصی قبلاً یک بار استفاده شده و باطل گردیده است (کلید یک‌بار مصرف)."
            e is ZsxExpiredException -> "این کانفیگ منقضی شده است و اعتبار آن پایان یافته است."
            e is ZsxPasswordRequiredException ->
                "این کانفیگ قفل‌شده رمز می‌خواهد؛ آن را از بخش گاوصندوق و با رمزش باز کنید."
            e is ConfigRepository.SubscriptionNotFetchedException ->
                if (e.savedForLater) {
                    "اشتراک «${e.name}» ذخیره شد، اما الان دانلود نشد. وقتی وصل شدید آن را به‌روز کنید."
                } else {
                    "دریافت اشتراک ناموفق بود؛ نشانی و اتصال اینترنت را بررسی کنید."
                }
            e is OvpnConfig.UnsupportedException -> when (e.reason) {
                OvpnConfig.Reason.NoRemote -> "این فایل OpenVPN هیچ سروری ندارد (خط \"remote\" ندارد)."
                OvpnConfig.Reason.Tap -> "فایل‌های OpenVPN در حالت TAP روی اندروید کار نمی‌کنند."
                OvpnConfig.Reason.ExternalFiles ->
                    "گواهی‌های این فایل جدا هستند. از ارائه‌دهنده نسخهٔ تک‌فایلی (inline) را بخواه."
                OvpnConfig.Reason.UnsupportedCredentials ->
                    "این فایل با گواهی pkcs12 یا اسمارت‌کارت وارد می‌شود که اپ نمی‌تواند استفاده کند."
            }
            e is AmneziaLink.UnsupportedException -> when (e.reason) {
                AmneziaLink.Reason.Backup ->
                    "این فایل پشتیبان Amnezia است. اول در خود Amnezia بازیابی‌اش کنید و بعد سرور را از آنجا به اشتراک بگذارید."
                AmneziaLink.Reason.Subscription ->
                    "این کلید Amnezia Free/Premium است. آن سرورها فقط از اپ خود Amnezia در دسترس‌اند."
                AmneziaLink.Reason.NoRunnableContainer ->
                    "این کانفیگ Amnezia فقط OpenVPN روی Cloak یا Shadowsocks دارد که خود Amnezia هم دیگر پشتیبانی‌اش نمی‌کند."
            }
            e is ConfigParseException -> when (e.reason) {
                ConfigParseException.Reason.UnsupportedSsCipher ->
                    "این روش رمزنگاری در هستهٔ برنامه پشتیبانی نمی‌شود. از ارائه‌دهنده یک کانفیگ AES-GCM یا ChaCha20 بگیرید."
                ConfigParseException.Reason.MissingSsCipher ->
                    "این لینک شادوساکس هیچ روش رمزنگاری ندارد، پس قابل استفاده نیست."
                ConfigParseException.Reason.UnsupportedSsPlugin -> "این پلاگین شادوساکس با این هسته کار نمی‌کند."
                null -> GENERIC
            }
            else -> GENERIC
        }
    }
}
