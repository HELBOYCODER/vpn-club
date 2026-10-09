package com.helboy.vpnclub.corex

/**
 * یک کانفیگ پروکسی نرمال‌شده.
 *
 * همه‌ی فرمت‌های شناخته‌شده (VLESS / VMess / Trojan / Shadowsocks / Hysteria2 / SOCKS)
 * به این مدل واحد تبدیل می‌شوند تا لایه‌ی بازنویسی IP و لایه‌ی سنجش سرعت
 * مجبور نباشند هر پروتکل را جداگانه بشناسند.
 *
 * @param scheme  پیشوند لینک اشتراک (vless, vmess, trojan, ss, hysteria2, socks)
 * @param host    آدرس اصلی (دامنه یا IP) — برای حفظ SNI/Host نگه داشته می‌شود
 * @param port    پورت اصلی
 * @param query   پارامترهای پرس‌وجوی لینک، به ترتیب ظهور (برای بازسازی عین به عین)
 * @param payload بدنه‌ی base64 برای vmess؛ برای بقیه خالی است
 * @param tag     برچسب خوانا (کشور/نام) برای نمایش در لیست
 */
data class ProxyConfig(
    val scheme: String,
    val host: String,
    val port: Int,
    val uuidOrUser: String = "",
    val query: List<Pair<String, String>> = emptyList(),
    val payload: String = "",
    val tag: String = "",
    val raw: String = ""
) {
    /** آدرسی که واقعاً به آن وصل می‌شویم؛ پس از تزریق IP فلر عوض می‌شود. */
    var dialHost: String = host

    /** پورتی که واقعاً به آن وصل می‌شویم. */
    var dialPort: Int = port

    /** مقدار SNI/Host که باید دست‌نخورده بماند تا TLS معتبر بماند. */
    val sniHost: String get() = query.firstOrNull { it.first.equals("sni", true) }?.second
        ?: query.firstOrNull { it.first.equals("host", true) }?.second
        ?: host

    val isInjected: Boolean get() = dialHost != host || dialPort != port

    /**
     * لینک اشتراک با آدرس تزریق‌شده.
     * اگر IP تزریق نشده باشد، لینک اصلی برگردانده می‌شود.
     */
    fun toShareLink(): String {
        if (!isInjected && raw.isNotEmpty()) return raw
        return ShareLinkBuilder.build(this)
    }

    /** یک نسخه‌ی تازه با آدرس/پورت تزریق‌شده (بدون تغییر روی نمونه‌ی اصلی). */
    fun withDial(host: String, port: Int): ProxyConfig =
        copy().also { it.dialHost = host; it.dialPort = port }
}

/** نتیجه‌ی سنجش سلامت یک کانفیگ. */
data class HealthResult(
    val config: ProxyConfig,
    val tcpMs: Long,
    val downloadBps: Double,
    val uploadBps: Double,
    val error: String? = null
) {
    /**
     * کانفیگی قابل اتصال است که هم پینگ داشته باشد و هم ترافیک واقعی رد کند.
     * قانون کارفرما: کانفیگ‌هایی که فقط پینگ دارند و دانلود/آپلودشان صفر است نباید وصل شوند.
     */
    val isUsable: Boolean
        get() = error == null && tcpMs in 1..MAX_TCP_MS && downloadBps >= MIN_DOWNLOAD_BPS

    /** امتیاز ترکیبی: سرعت غالب است، ولی پینگ هم وزن دارد. */
    val score: Double
        get() = if (!isUsable) -1.0
        else downloadBps * 0.7 + uploadBps * 0.1 + (1_000_000.0 / (tcpMs + 50))

    companion object {
        const val MAX_TCP_MS = 2_500L
        const val MIN_DOWNLOAD_BPS = 20_000.0 // ۲۰ کیلوبایت بر ثانیه = مرز «صفر نبودن»
    }
}
