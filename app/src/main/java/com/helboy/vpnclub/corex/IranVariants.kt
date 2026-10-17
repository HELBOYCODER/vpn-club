package com.helboy.vpnclub.corex

import org.json.JSONArray
import org.json.JSONObject

/**
 * پروفایل‌های اتصال به کلادفلر روی فایروال‌های ایران (جمع‌بندی کارفرما):
 *
 * ── همراه اول (MCI) ──
 *  1. ECH        : آدرس IP تمیز CF (مثلاً 188.114.97.6)، cipherSuites خالی، fp=chrome،
 *                  echConfigList=<دامنه ECH>+<resolver> از فهرست [ECH_LIST]، finalMask خالی
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

    /**
     * فهرست ECH — هر خط «دامنه+resolver» (فرمت echConfigList هسته).
     * resolver خط‌ها در [ECH_RESOLVERS] به‌صورت رندوم جایگزین می‌شود (۱.۱.۱.۱ یا ۸.۸.۸.۸).
     */
    private val ECH_LIST = """
        iranva.ir+udp://1.1.1.1
        moj.ir+udp://1.1.1.1
        dolts.ir+udp://1.1.1.1
        geedo.com+udp://1.1.1.1
        ip.gs+udp://1.1.1.1
        gitlab.io+udp://1.1.1.1
        discordapp.com+udp://1.1.1.1
        ifconfig.io+udp://1.1.1.1
        test.defo.ie+udp://1.1.1.1
        red.jaydjc.ccwu.cc+udp://1.1.1.1
        osmium.chat+udp://1.1.1.1
        niyaniya.moe+udp://1.1.1.1
        miraheze.org+udp://1.1.1.1
        padidehkhodro.com+udp://1.1.1.1
        tgju.org+udp://1.1.1.1
        tempmaili.com+udp://1.1.1.1
        dns.vinnyp.xyz+udp://1.1.1.1
        cloudflare-ech.com+udp://1.1.1.1
    """.trimIndent().lines().map { it.trim() }.filter { it.contains('+') }

    /** دامنه‌های ECH (بخش قبل از «+») — از [ECH_LIST] ساخته می‌شود. */
    val ECH_DOMAINS: List<String> = ECH_LIST.map { it.substringBefore('+') }.distinct()

    /** ECH resolver ها — گوگل و کلادفلر. */
    val ECH_RESOLVERS = listOf("udp://1.1.1.1", "udp://8.8.8.8")

    /** چند دامنه‌ی ECH در هر چرخه‌ی تست امتحان شود. */
    const val ECH_TRIES_PER_CYCLE = 3

    /** پیشوند id پروفایل ECH — بقیه‌ی id ها در [all] ثابت‌اند. */
    private const val ECH_ID_PREFIX = "mci-ech:"

    private const val LEGACY_ECH_ID = "mci-ech"

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
     * finalMask ی tlshello-0-len (متد F&F) — فرمت فورک patterniha/Xray-core:
     * fragment اول: packets tlshello، length 104؛ fragment دوم: packets 1-1، length 114.
     * توجه: length/delay/maxSplit رشته‌ی تکی هستند — آرایه‌ی "lengths" توسط هسته رد می‌شود
     * (خطای "LengthMin can't be 0" — اثبات‌شده روی redroid با باینری واقعی).
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
        val ipv6Prefix: String? = null
    )

    /** پروفایل ECH برای یک خط «دامنه+resolver». id خودِ خط را حمل می‌کند تا بعد از ری‌استارت هم برگردد. */
    private fun echVariant(echLine: String) = Variant(
        id = ECH_ID_PREFIX + echLine, carrier = "mci", echConfigList = echLine,
        cipherSuites = null, fingerprint = "chrome", finalMask = null, alpn = "h2,http/1.1"
    )

    /**
     * کل لیست — به ترتیب اولویت: MCI اول (ECH ها > IPv6)، بعد ایرانسل.
     * از [ECH_LIST] چند دامنه‌ی رندوم با resolver رندوم انتخاب می‌شود.
     */
    fun all(rng: java.security.SecureRandom = java.security.SecureRandom()): List<Variant> {
        val echLines = ECH_DOMAINS.shuffled(rng).take(ECH_TRIES_PER_CYCLE).map { domain ->
            "$domain+${ECH_RESOLVERS[rng.nextInt(ECH_RESOLVERS.size)]}"
        }
        return echLines.map { echVariant(it) } + listOf(
            // همراه اول ۲ — IPv6 + F&F (دامنه‌ی فیلترشده)
            Variant("mci-ipv6-ff", "mci", echConfigList = null, cipherSuites = null,
                fingerprint = "chrome", finalMask = fmTlsHello(), alpn = "h2,http/1.1",
                ipv6 = true, ipv6Prefix = IPV6_PREFIXES[rng.nextInt(IPV6_PREFIXES.size)]),
            // ایرانسل — F&F با semi-python و unsafe
            Variant("irancell-ff", "irancell", echConfigList = null, cipherSuites = CIPHER_SEMI_PYTHON,
                fingerprint = "unsafe", finalMask = fmTlsHello(), alpn = "http/1.1")
        )
    }

    /**
     * پروفایل را از id ذخیره‌شده برمی‌گرداند (برای بازیابی بعد از ری‌استارت).
     * id های ECH خودشان خط echConfigList را دارند، پس نیاز به فهرست رندوم نیست.
     */
    fun byId(id: String?): Variant? {
        if (id.isNullOrBlank()) return null
        if (id.startsWith(ECH_ID_PREFIX)) return echVariant(id.removePrefix(ECH_ID_PREFIX))
        // id قدیمی نسخه‌ی ۱.۶ — همان ECH تک‌دامنه‌ی قبلی؛ بعد از آپدیت هم بتواند بازیابی شود
        if (id == LEGACY_ECH_ID) return echVariant(ECH_LIST.first())
        return all().firstOrNull { it.id == id }
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
