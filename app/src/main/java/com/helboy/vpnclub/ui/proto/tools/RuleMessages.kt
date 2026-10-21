package com.helboy.vpnclub.ui.proto.tools

import dev.valid7996.narcicgeteway.domain.config.RuleValidation

/** Persian text for each [RuleValidation.Problem]. */
fun RuleValidation.Problem.message(): String = when (this) {
    RuleValidation.Problem.GeoIpInDomainField -> "geoip: به فیلد IP تعلق دارد. اینجا هرگز مطابقت پیدا نمی‌کند."
    RuleValidation.Problem.GeoSiteInIpField -> "geosite: به فیلد دامنه تعلق دارد. اینجا باعث می‌شود هسته کل کانفیگ را رد کند."
    RuleValidation.Problem.NegationInDomainField -> "دامنه‌ها را نمی‌توان با «!» نفی کرد. فقط ورودی‌های IP این را پشتیبانی می‌کنند."
    RuleValidation.Problem.DotlessWithDot -> "dotless: نمی‌تواند نقطه داشته باشد."
    RuleValidation.Problem.NotAnAddress -> "IP یا CIDR معتبر نیست. الگوهای دامنه به فیلد دامنه تعلق دارند."
    RuleValidation.Problem.BadRegex -> "عبارت باقاعدهٔ نامعتبر."
}

const val MSG_REMARKS_REQUIRED = "برای اینکه بعداً بشناسیدش یک نام بگذارید"
const val MSG_PORT_INVALID = "پورت نامعتبر است. یک عدد، یک بازه مثل 8000-9000، یا فهرستی جداشده با ویرگول بنویسید."
const val MSG_NO_CONDITION = "حداقل یک شرط (دامنه، IP، پورت، شبکه یا پروتکل) لازم است؛ قاعدهٔ بدون شرط هیچ معنایی ندارد."
const val MSG_PROTOCOL_SNIFFING = "تطبیق پروتکل به شنود ترافیک نیاز دارد که هم‌اکنون در تنظیمات هسته خاموش است. تا روشنش نکنید این قاعده اجرا نمی‌شود."

/** Protocols the sniffer can recognise. */
val KNOWN_PROTOCOLS = listOf("http", "tls", "quic", "bittorrent")

/** Result of checking one draft: one message per field, null when that field is fine. */
data class RuleErrors(
    val remarks: String? = null,
    val domain: String? = null,
    val ip: String? = null,
    val port: String? = null,
    val protocol: String? = null,
    val condition: String? = null,
) {
    val ok: Boolean get() = remarks == null && domain == null && ip == null && port == null && protocol == null && condition == null
}

/** Checks [d] with the engine's own [RuleValidation]; the message names the first offending entry. */
fun checkDraft(d: RuleDraft): RuleErrors {
    val domains = splitEntries(d.domain)
    val ips = splitEntries(d.ip)
    val protocols = splitEntries(d.protocol)

    val domainError = domains.firstNotNullOfOrNull { e -> RuleValidation.checkDomainEntry(e)?.let { "«$e»: ${it.message()}" } }
    val ipError = ips.firstNotNullOfOrNull { e -> RuleValidation.checkIpEntry(e)?.let { "«$e»: ${it.message()}" } }
    val unknown = protocols.firstOrNull { it.lowercase() !in KNOWN_PROTOCOLS }
    val noCondition = domains.isEmpty() && ips.isEmpty() && d.port.isBlank() && d.network.isBlank() && protocols.isEmpty()

    return RuleErrors(
        remarks = if (d.remarks.isBlank()) MSG_REMARKS_REQUIRED else null,
        domain = domainError,
        ip = ipError,
        port = if (RuleValidation.isValidPort(d.port)) null else MSG_PORT_INVALID,
        protocol = unknown?.let { "«$it» شناخته‌شده نیست. مقدارهای مجاز: ${KNOWN_PROTOCOLS.joinToString("، ")}" },
        condition = if (noCondition) MSG_NO_CONDITION else null,
    )
}

/** True when a protocol rule would silently never fire because sniffing is off. */
fun needsSniffingWarning(d: RuleDraft, sniffingEnabled: Boolean): Boolean =
    !sniffingEnabled && splitEntries(d.protocol).isNotEmpty()
