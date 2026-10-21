package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.domain.config.PsiphonConfigBuilder
import dev.valid7996.narcicgeteway.domain.config.PsiphonFronting
import dev.valid7996.narcicgeteway.domain.config.PsiphonProfile
import dev.valid7996.narcicgeteway.domain.config.PsiphonRegions
import dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig
import dev.valid7996.narcicgeteway.domain.config.SniSpoofProfile
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import java.util.Locale

/** The Psiphon form. Nothing is required: every field has a working default. */
data class PsiphonForm(
    val name: String = "",
    val country: String = "",
    val mode: String = PsiphonConfigBuilder.MODE_AUTO,
    val cdnIps: String = "",
    val cdnSni: String = "",
    val beast: Boolean = true,
) {
    fun check(): FormCheck = formCheck {
        val code = country.trim().uppercase()
        if (code.isNotEmpty() && code !in PsiphonRegions.CODES) warn(F_COUNTRY, "این کشور در فهرست سایفون نیست.")
        val ips = PsiphonFronting.ipCandidates(cdnIps)
        val ipTokens = FormText.tokens(cdnIps)
        if (ipTokens.isNotEmpty() && ips.isEmpty()) warn(F_IPS, "هیچ آدرس IPv4 معتبری پیدا نشد؛ این فهرست نادیده گرفته می‌شود.")
        else if (ips.size < ipTokens.distinct().size) warn(F_IPS, "بعضی ورودی‌ها IPv4 یا CIDR نیستند و نادیده گرفته می‌شوند.")
        val snis = PsiphonFronting.sniCandidates(cdnSni)
        val sniTokens = FormText.tokens(cdnSni)
        if (sniTokens.isNotEmpty() && snis.isEmpty()) warn(F_SNI, "هیچ نام دامنهٔ معتبری پیدا نشد؛ این فهرست نادیده گرفته می‌شود.")
        else if (snis.size < sniTokens.distinct().size) warn(F_SNI, "بعضی ورودی‌ها نام دامنهٔ معتبر نیستند و نادیده گرفته می‌شوند.")
    }

    fun toProfile(): PsiphonProfile = PsiphonProfile(
        country = country.trim().uppercase(),
        mode = PsiphonConfigBuilder.mode(mode),
        cdnIps = cdnIps.trim(),
        cdnSni = cdnSni.trim(),
        beast = beast,
    )

    companion object {
        const val F_COUNTRY = "country"
        const val F_IPS = "ips"
        const val F_SNI = "sni"

        val MODES = listOf(
            PsiphonConfigBuilder.MODE_AUTO to "خودکار",
            PsiphonConfigBuilder.MODE_CDN to "CDN",
            PsiphonConfigBuilder.MODE_DIRECT to "مستقیم",
        )

        fun from(p: PsiphonProfile?, name: String = ""): PsiphonForm =
            if (p == null) PsiphonForm(name = name) else PsiphonForm(
                name = name,
                country = p.country,
                mode = PsiphonConfigBuilder.mode(p.mode),
                cdnIps = p.cdnIps,
                cdnSni = p.cdnSni,
                beast = p.beast,
            )

        /** Country name in Persian when the device knows it, else the code. */
        fun countryName(code: String, locale: Locale = Locale("fa")): String =
            Locale("", code).getDisplayCountry(locale).ifBlank { code }

        /** The region codes sorted by their displayed name, filtered by what the person typed (name or code). */
        fun filterCountries(query: String, locale: Locale = Locale("fa")): List<String> {
            val q = query.trim().lowercase()
            val sorted = PsiphonRegions.CODES.sortedBy { countryName(it, locale).lowercase() }
            return if (q.isEmpty()) sorted
            else sorted.filter { countryName(it, locale).lowercase().contains(q) || it.lowercase().contains(q) }
        }
    }
}

/** The SNI spoof profile form (no-root engine). The profile's own [link] is not on screen and is kept as is. */
data class SniSpoofForm(
    val name: String = "SNI Spoof",
    val ip: String = SniSpoofConfig.DEFAULT_CONNECT_IP,
    val port: String = SniSpoofConfig.DEFAULT_CONNECT_PORT.toString(),
    val fakeSni: String = SniSpoofConfig.DEFAULT_FAKE_SNI,
    val method: String = SniSpoofConfig.DEFAULT_METHOD,
    val keptLink: String = "",
) {
    fun check(): FormCheck = formCheck {
        val i = ip.trim()
        if (i.isEmpty()) error(F_IP, "آدرس IP تمیز را وارد کنید.")
        else if (FormText.hasWhitespace(i)) error(F_IP, "آدرس نباید فاصله داشته باشد.")
        if (FormText.port(port) == null) error(F_PORT, "پورت باید عددی بین ۱ تا ۶۵۵۳۵ باشد.")
        val s = fakeSni.trim()
        if (s.isEmpty()) error(F_SNI, "SNI جعلی را وارد کنید.")
        else if (FormText.hasWhitespace(s)) error(F_SNI, "نام دامنه نباید فاصله داشته باشد.")
    }

    fun toProfile(): SniSpoofProfile = SniSpoofProfile(
        link = keptLink,
        fakeSni = fakeSni.trim().ifBlank { SniSpoofConfig.defaultFakeSni },
        cleanIp = ip.trim().ifBlank { SniSpoofConfig.defaultConnectIp },
        noRoot = true,
        method = SniSpoofConfig.method(method),
        connectPort = FormText.port(port) ?: SniSpoofConfig.defaultConnectPort,
    )

    companion object {
        const val F_IP = "ip"
        const val F_PORT = "port"
        const val F_SNI = "sni"

        /**
         * A profile's form. For an existing profile the stored values win; a blank value falls back to the
         * engine defaults, as the reference sheet does.
         */
        fun from(p: SniSpoofProfile?, name: String = "", defaults: SniSpoofProfile? = null): SniSpoofForm {
            val d = defaults
            val defIp = d?.cleanIp?.ifBlank { null } ?: SniSpoofConfig.defaultConnectIp
            val defPort = d?.connectPort?.takeIf { it > 0 } ?: SniSpoofConfig.defaultConnectPort
            val defSni = d?.fakeSni?.ifBlank { null } ?: SniSpoofConfig.defaultFakeSni
            val defMethod = d?.method?.ifBlank { null } ?: SniSpoofConfig.defaultMethod
            if (p == null) return SniSpoofForm(name = name.ifBlank { "SNI Spoof" }, ip = defIp, port = defPort.toString(), fakeSni = defSni, method = SniSpoofConfig.method(defMethod))
            return SniSpoofForm(
                name = name.ifBlank { "SNI Spoof" },
                ip = p.cleanIp.ifBlank { defIp },
                port = (p.connectPort.takeIf { it > 0 && it != SniSpoofConfig.RSTA_LOCAL_PORT } ?: defPort).toString(),
                fakeSni = p.fakeSni.ifBlank { defSni },
                method = SniSpoofConfig.method(p.method.ifBlank { defMethod }),
                keptLink = p.link,
            )
        }
    }
}

/** The global no-root engine settings (the same four values the reference "RstaSpoofSheet" edits). */
data class SpoofEngineForm(
    val ip: String,
    val port: String,
    val fakeSni: String,
    val method: String,
) {
    fun check(): FormCheck = formCheck {
        if (ip.isBlank()) error(F_IP, "آدرس IP را وارد کنید.")
        else if (FormText.hasWhitespace(ip)) error(F_IP, "آدرس نباید فاصله داشته باشد.")
        if (FormText.port(port) == null) error(F_PORT, "پورت باید عددی بین ۱ تا ۶۵۵۳۵ باشد.")
        if (fakeSni.isBlank()) error(F_SNI, "SNI جعلی را وارد کنید.")
    }

    /** [settings] with the four engine values replaced; blank or invalid values fall back to the engine defaults. */
    fun applyTo(settings: AppSettings): AppSettings = settings.copy(
        rstaSpoofConnectIp = ip.trim().ifEmpty { SniSpoofConfig.DEFAULT_CONNECT_IP },
        rstaSpoofConnectPort = FormText.port(port) ?: SniSpoofConfig.DEFAULT_CONNECT_PORT,
        rstaSpoofFakeSni = fakeSni.trim().ifEmpty { SniSpoofConfig.DEFAULT_FAKE_SNI },
        rstaSpoofMethod = SniSpoofConfig.method(method),
    )

    fun sameAs(settings: AppSettings): Boolean = applyTo(settings) == settings

    companion object {
        const val F_IP = "engineIp"
        const val F_PORT = "enginePort"
        const val F_SNI = "engineSni"

        fun from(s: AppSettings): SpoofEngineForm = SpoofEngineForm(
            ip = s.rstaSpoofConnectIp,
            port = s.rstaSpoofConnectPort.toString(),
            fakeSni = s.rstaSpoofFakeSni,
            method = SniSpoofConfig.method(s.rstaSpoofMethod),
        )
    }
}

/** Reads the engine's one-line status for the screen: a colour class and a Persian text. */
object SpoofStatusText {
    enum class Tone { Idle, Active, Failed }

    fun tone(raw: String): Tone {
        val s = raw.trim().lowercase()
        return when {
            s.startsWith("active") -> Tone.Active
            s.startsWith("failed") -> Tone.Failed
            else -> Tone.Idle
        }
    }

    fun describe(raw: String): String {
        val s = raw.trim()
        return when (tone(s)) {
            Tone.Active -> "فعال: " + s.substringAfter("active").trim().removePrefix("->").trim()
            Tone.Failed -> "ناموفق: " + s.substringAfter("failed").trim().removePrefix(":").trim()
            Tone.Idle -> if (s.isEmpty() || s.equals("inactive", ignoreCase = true)) "غیرفعال؛ با اتصال یا تست شروع می‌شود." else s
        }
    }
}
