package com.helboy.vpnclub.corex

import org.json.JSONArray
import org.json.JSONObject

/**
 * پروفایل‌های اتصال به کلادفلر روی فایروال‌های ایران (جمع‌بندی کارفرما):
 *
 * ── همراه اول (MCI) ──
 *  1. ECH        : آدرس IP تمیز CF (مثلاً 188.114.97.6)، cipherSuites خالی، fp=chrome،
 *                  echConfigList=cloudflare-ech.com+udp://1.1.1.1، finalMask خالی
 *  2. IPv6+F&F   : آدرس IPv6 (مثلاً 2a06:98c1:3121::7)، echConfigList خالی، fp=chrome،
 *                  finalMask=tlshello-0-len (اگر دامنه فیلتر است؛ وگرنه خالی)
 *
 * ── ایرانسل (Irancell) ──
 *  3. F&F        : آدرس IP تمیز CF، echConfigList خالی، finalMask=tlshello-0-len،
 *                  cipherSuites=semi-python، fp=unsafe، ALPN=http/1.1 برای ws (h2,http/1.1 برای xhttp)
 *
 * نکته: سیم‌کارت و فایروال ممکن است فرق داشته باشند — پس همه‌ی پروفایل‌ها به‌ترتیب امتحان می‌شوند.
 * هر پروفایل چند دامنه‌ی ECH/رنجه را رندوم امتحان می‌کند (خودِ اپ، Puarو).
 */
object IranVariants {

    /** کانفیگ ECH ثابت — dom+resolver، طبق راهنما. */
    const val ECH_CONFIG = "cloudflare-ech.com+udp://1.1.1.1"

    /** سرورهای ECH جایگزین — اگر اولی کار نکرد. */
    val ECH_DOMAINS = listOf("cloudflare-ech.com", "crypto.cloudflare.com", "ip.gs")

    /** ECH resolver ها. */
    val ECH_RESOLVERS = listOf("udp://1.1.1.1", "udp://8.8.8.8")

    /** رنج‌های IPv6 تمیز شناخته‌شده برای CF روی همراه اول (نمونه‌ی کارفرما + همسایه‌ها). */
    val IPV6_PREFIXES = listOf(
        "2a06:98c1:3121::", "2a06:98c1:3120::", "2a06:98c1:3122::", "2a06:98c1:3123::"
    )

    /** cipherSuites ی «semi-python» — دقیقاً مطابق پریست PattNG. */
    const val CIPHER_SEMI_PYTHON =
        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384:TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384:" +
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256:TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256:" +
        "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256:TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256:" +
        "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA:TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA:" +
        "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256:TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256"

    /**
     * cipherSuites ی پنل Netra (CSFM_DEFAULT_CS): اول سه سوئیت TLS 1.3، بعد ECDHE های TLS 1.2.
     * کانفیگ‌های Netra این را در پارامتر cs لینک می‌فرستند.
     */
    const val CIPHER_NETRA =
        "TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256:TLS_AES_128_GCM_SHA256:" + CIPHER_SEMI_PYTHON

    /**
     * finalMask پنل Netra (CSFM_DEFAULT_FM) — همان چیزی که کانفیگ‌های Netra را روی ایران خوب وصل می‌کند:
     *  ۱) ClientHello (tlshello) در سه تکه: 0 (فقط سرآیند رکورد)، 104، و بقیه با تکه‌های ۱ بایتی؛ بدون تاخیر
     *  ۲) بسته‌ی اول بعد از آن (1-1) در تکه‌های 114 و ۱ بایتی با ۱ms تاخیر، حداکثر ۱۱ تکه
     * هسته‌ی ما (patterniha v26.9.30) آرایه‌ی lengths/delays را می‌پذیرد؛ فقط آخرین عضو lengths نباید ۰ باشد.
     */
    fun fmNetra(): JSONObject = JSONObject().put("tcp", JSONArray().put(
        JSONObject().put("type", "fragment").put("settings", JSONObject()
            .put("packets", "tlshello")
            .put("lengths", JSONArray(listOf("0", "104", "1")))
            .put("delays", JSONArray(listOf("0")))
            .put("maxSplit", "0"))
    ).put(
        JSONObject().put("type", "fragment").put("settings", JSONObject()
            .put("packets", "1-1")
            .put("lengths", JSONArray(listOf("114", "1")))
            .put("delays", JSONArray(listOf("1")))
            .put("maxSplit", "11"))
    ))

    /**
     * finalMask ی tlshello-0-len (متد F&F) — فرمت فورک patterniha/Xray-core:
     * fragment اول: packets tlshello، length 104؛ fragment دوم: packets 1-1، length 114.
     * (فرمت تکی length/delay — هسته‌ی فعلی هر دو فرمت تکی و آرایه‌ای را می‌پذیرد.)
     */
    fun fmTlsHello(): JSONObject = JSONObject().put("tcp", JSONArray().put(
        JSONObject().put("type", "fragment").put("settings", JSONObject()
            .put("packets", "tlshello")
            .put("length", "104")
            .put("delay", "0")
            .put("maxSplit", "0"))
    ).put(
        JSONObject().put("type", "fragment").put("settings", JSONObject()
            .put("packets", "1-1")
            .put("length", "114")
            .put("delay", "1")
            .put("maxSplit", "11"))
    ))

    /**
     * یک پروفایل اتصال.
     * @param carrier        همراه اول یا ایرانسل — فقط برای برچسب/اولویت
     * @param echConfigList  اگر نال/خالی → ECH فعال نیست
     * @param cipherSuites   خالی = پیش‌فرض هسته
     * @param fingerprint    chrome / unsafe / ...
     * @param finalMask      JSON تکه‌بندی یا null
     * @param alpn           "http/1.1" برای ws، "h2,http/1.1" برای xhttp
     * @param ipv6           true = آدرس IPv6 رندوم از IPV6_PREFIXES بساز
     */
    data class Variant(
        val id: String,
        val carrier: String,           // "mci" | "irancell"
        val echConfigList: String? = null,
        val cipherSuites: String? = null,
        val fingerprint: String = "chrome",
        val finalMask: JSONObject? = null,
        val alpn: String = "h2,http/1.1",
        val ipv6: Boolean = false,
        val ipv6Prefix: String? = null,
        /** FragmentProxy محلی (تکه‌کردن ClientHello) — برای plain خاموش. */
        val useFrag: Boolean = true,
        /** فقط برای سرور پشت کلادفلر معنی دارد (ECH / IPv6 کلادفلر). */
        val needsCf: Boolean = false,
        val label: String = id
    )

    /** کل لیست — به ترتیب اولویت: MCI اول (ECH > IPv6)، بعد ایرانسل. */
    fun all(rng: java.security.SecureRandom = java.security.SecureRandom()): List<Variant> = listOf(
        // روش Netra — فرگمنت دومرحله‌ای داخل هسته + cipherSuites نترا (هر دو اپراتور).
        // فرگمنت داخل هسته است؛ FragmentProxy محلی خاموش تا ClientHello دوبار تکه نشود.
        Variant("netra-ff", "all", echConfigList = null, cipherSuites = CIPHER_NETRA,
            fingerprint = "chrome", finalMask = fmNetra(), alpn = "http/1.1",
            useFrag = false, label = "روش Netra · Fragment"),
        // همراه اول ۱ — ECH
        Variant("mci-ech", "mci", echConfigList = ECH_CONFIG, cipherSuites = null,
            fingerprint = "chrome", finalMask = null, alpn = "h2,http/1.1",
            needsCf = true, label = "همراه اول · ECH"),
        // همراه اول ۲ — IPv6 + F&F (دامنه‌ی فیلترشده)
        Variant("mci-ipv6-ff", "mci", echConfigList = null, cipherSuites = null,
            fingerprint = "chrome", finalMask = fmTlsHello(), alpn = "h2,http/1.1",
            ipv6 = true, ipv6Prefix = IPV6_PREFIXES[rng.nextInt(IPV6_PREFIXES.size)],
            needsCf = true, label = "همراه اول · IPv6"),
        // ایرانسل — F&F با semi-python و unsafe
        Variant("irancell-ff", "irancell", echConfigList = null, cipherSuites = CIPHER_SEMI_PYTHON,
            fingerprint = "unsafe", finalMask = fmTlsHello(), alpn = "http/1.1",
            label = "ایرانسل · Fragment"),
        // بدون هیچ ترفندی — برای وای‌فای/شبکه‌هایی که فیلتر شدید ندارند (سریع‌ترین مسیر)
        PLAIN
    )

    /** اتصال ساده بدون ترفند. alpn=null یعنی همان مقدار لینک. */
    val PLAIN = Variant("plain", "none", fingerprint = "chrome", alpn = "",
        useFrag = false, label = "مستقیم")

    fun byId(id: String?): Variant? = if (id.isNullOrBlank()) null else all().firstOrNull { it.id == id }

    /**
     * ترتیب امتحان پروفایل‌ها بر اساس انتخاب کاربر (auto / mci / irancell / plain).
     * [preferredFirst] = آخرین پروفایلی که کار کرده (یادگیری حین اجرا).
     */
    fun order(mode: String, preferredFirst: String? = null): List<Variant> {
        val all = all()
        val base = when (mode) {
            "mci" -> all.filter { it.carrier == "mci" } + all.filter { it.carrier != "mci" }
            "irancell" -> all.filter { it.carrier == "irancell" } + all.filter { it.carrier != "irancell" }
            "plain" -> listOf(PLAIN) + all.filter { it.id != "plain" }
            // خودکار: «مستقیم» اول (لاگ واقعی کاربر: همه‌ی موفقیت‌ها plain بودند و ECH/IPv6/Fragment
            // تقریباً همیشه no_response) و بقیه فقط به‌عنوان جایگزین
            else -> listOf(PLAIN) + all.filter { it.id != "plain" }
        }
        val pf = base.firstOrNull { it.id == preferredFirst } ?: return base
        return listOf(pf) + base.filter { it.id != pf.id }
    }

    /**
     * آدرس IPv6 رندوم در پیشوند — برای روش همراه‌اول ۲.
     */
    fun randomIpv6(prefix: String, rng: java.security.SecureRandom = java.security.SecureRandom()): String {
        val sb = StringBuilder(prefix)
        // 4 گروه ۱۶بیتی دیگر
        for (i in 0 until 4) {
            if (sb.isNotEmpty() && !sb.endsWith(":")) sb.append(":")
            sb.append(Integer.toHexString(rng.nextInt(0x10000)))
        }
        return sb.toString()
    }
}
