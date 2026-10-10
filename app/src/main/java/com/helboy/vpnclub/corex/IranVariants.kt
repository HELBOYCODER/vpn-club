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
     * finalMask ی tlshello-0-len — پریست دقیق PattNG:
     * fragment اول: tlshello، lengths 0/104/1، delay 0؛ fragment دوم: packets 1-1، length 114، delay 1.
     */
    fun fmTlsHello(): JSONObject = JSONObject().put("tcp", JSONArray().put(
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
        val ipv6Prefix: String? = null
    )

    /** کل لیست — به ترتیب اولویت: MCI اول (ECH > IPv6)، بعد ایرانسل. */
    fun all(rng: java.security.SecureRandom = java.security.SecureRandom()): List<Variant> = listOf(
        // همراه اول ۱ — ECH
        Variant("mci-ech", "mci", echConfigList = ECH_CONFIG, cipherSuites = null,
            fingerprint = "chrome", finalMask = null, alpn = "h2,http/1.1"),
        // همراه اول ۲ — IPv6 + F&F (دامنه‌ی فیلترشده)
        Variant("mci-ipv6-ff", "mci", echConfigList = null, cipherSuites = null,
            fingerprint = "chrome", finalMask = fmTlsHello(), alpn = "h2,http/1.1",
            ipv6 = true, ipv6Prefix = IPV6_PREFIXES[rng.nextInt(IPV6_PREFIXES.size)]),
        // ایرانسل — F&F با semi-python و unsafe
        Variant("irancell-ff", "irancell", echConfigList = null, cipherSuites = CIPHER_SEMI_PYTHON,
            fingerprint = "unsafe", finalMask = fmTlsHello(), alpn = "http/1.1")
    )

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
