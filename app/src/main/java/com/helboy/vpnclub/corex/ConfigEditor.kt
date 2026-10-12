package com.helboy.vpnclub.corex

/**
 * ویرایش، کپی و وارد کردن کانفیگ‌ها.
 *
 * ویرایش فیلدی: نام، آدرس، پورت، SNI، Host، Path → لینک تازه ساخته و دوباره پارس می‌شود؛
 * اگر لینک حاصل پارس نشود یا هسته پشتیبانی نکند، تغییر رد می‌شود (کانفیگ خراب ذخیره نمی‌شود).
 */
object ConfigEditor {

    data class Fields(
        val name: String,
        val address: String,
        val port: String,
        val sni: String,
        val host: String,
        val path: String,
    )

    /** نتیجه‌ی ویرایش: کانفیگ تازه یا دلیل رد. */
    sealed interface Result {
        data class Ok(val config: ProxyConfig) : Result
        data class Invalid(val reason: String) : Result
    }

    fun fieldsOf(c: ProxyConfig): Fields {
        val q = c.query.toMap()
        return Fields(c.tag, c.host, c.port.toString(), q["sni"].orEmpty(), q["host"].orEmpty(), q["path"].orEmpty())
    }

    /** آیا این پروتکل فیلدهای SNI/Host/Path دارد؟ (ss و socks ندارند) */
    fun hasTransportFields(c: ProxyConfig) = c.scheme !in setOf("ss", "socks", "socks5")

    fun apply(c: ProxyConfig, f: Fields): Result {
        val address = f.address.trim().trim('[', ']')
        if (address.isEmpty() || address.any { it.isWhitespace() }) return Result.Invalid("آدرس نامعتبر است")
        val port = f.port.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return Result.Invalid("پورت باید بین ۱ تا ۶۵۵۳۵ باشد")
        var q = c.query
        if (hasTransportFields(c)) {
            q = setKey(q, "sni", f.sni.trim())
            q = setKey(q, "host", f.host.trim())
            q = setKey(q, "path", f.path.trim())
        }
        val draft = ProxyConfig(scheme = c.scheme, host = address, port = port, uuidOrUser = c.uuidOrUser,
            query = q, tag = f.name.trim())
        return fromLink(ShareLinkBuilder.build(draft))
    }

    /** ویرایش متن خام لینک. */
    fun fromLink(link: String): Result {
        val cfg = LinkParser.parse(link.trim()) ?: return Result.Invalid("لینک قابل خواندن نیست")
        if (!XrayCore.isSupported(cfg)) return Result.Invalid("این نوع کانفیگ پشتیبانی نمی‌شود (مثلاً allowInsecure یا ss قدیمی)")
        return Result.Ok(cfg)
    }

    /** وارد کردن چند لینک (هر خط یک لینک، یا ساب base64). */
    fun parseMany(text: String): List<ProxyConfig> {
        val body = text.trim()
        val lines = if (body.contains("://")) body.lines()
        else runCatching { String(java.util.Base64.getMimeDecoder().decode(body)).lines() }.getOrDefault(emptyList())
        return lines.mapNotNull { LinkParser.parse(it.trim()) }.distinctBy { it.raw }
    }

    /** لینک قابل اشتراک: با IP تمیز تزریق‌شده (اگر هست) تا دیگران هم مستقیم وصل شوند. */
    fun shareLink(e: ServerEntry): String = e.config.toShareLink()

    /** تنظیم/حذف یک کلید پرس‌وجو با حفظ ترتیب؛ مقدار خالی = حذف. */
    internal fun setKey(q: List<Pair<String, String>>, key: String, value: String): List<Pair<String, String>> {
        val has = q.any { it.first.equals(key, true) }
        return when {
            value.isEmpty() -> q.filterNot { it.first.equals(key, true) }
            has -> q.map { if (it.first.equals(key, true)) it.first to value else it }
            else -> q + (key to value)
        }
    }
}
