package com.helboy.vpnclub.corex

import android.content.Context
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * غربال کشور برای اتصال هوشمند.
 *
 * کاربر یک کشور انتخاب می‌کند (مثلاً US)؛ موتور فقط کانفیگ‌هایی را نگه می‌دارد که
 * IP مقصدشان واقعاً در همان کشور است. کشورِ نامشخص (هیچ سرویسی جواب نداد) حذف می‌شود:
 * در حالت فیلتر، «احتمالاً درست» کافی نیست.
 *
 * نکته: برای کانفیگ‌هایی که IP تمیز کلادفلر به آن‌ها تزریق شده، کشور همان IP کلادفلر است
 * (نقطه‌ی ورود تونل)، نه کشور سرور اصلی.
 */
object CountryFilter {

    private const val PREFS = "vpnclub_prefs"
    private const val KEY_COUNTRY = "preferred_country"
    private const val TIMEOUT_MS = 4_000
    private const val LOOKUP_THREADS = 12

    /** گزینه‌های کشور در UI — کد ISO و برچسب فارسی. کد خالی = بدون فیلتر. */
    val CHOICES: List<Pair<String, String>> = listOf(
        "" to "همه",
        "US" to "آمریکا",
        "DE" to "آلمان",
        "NL" to "هلند",
        "GB" to "انگلیس",
        "FR" to "فرانسه",
        "CA" to "کانادا",
        "JP" to "ژاپن",
        "SG" to "سنگاپور",
        "KR" to "کره جنوبی",
    )

    /** سرویس‌های رایگان و بدون کلید؛ `%s` = IP. */
    private val LOOKUP_URLS = listOf(
        "https://get.geojs.io/v1/ip/country/%s.json",
        "https://ipwho.is/%s?fields=country_code",
    )

    private val CODE_JSON = Regex("\"country(?:_code)?\"\\s*:\\s*\"([A-Za-z]{2})\"")

    /** کش IP → کشور (فقط پاسخ‌های موفق). */
    private val cache = ConcurrentHashMap<String, String>()

    fun preferred(context: Context): String = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_COUNTRY, "") ?: ""
    } catch (_: Exception) { "" }

    fun setPreferred(context: Context, code: String) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_COUNTRY, code).apply()
        } catch (_: Exception) {}
    }

    /** کشور یک IP به‌صورت دو حرفی بزرگ؛ null اگر هیچ سرویسی جواب نداد. */
    fun countryOf(ip: String): String? {
        if (ip.isBlank()) return null
        cache[ip]?.let { return it }
        for (template in LOOKUP_URLS) {
            try {
                val connection = (URL(template.format(ip)).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                }
                try {
                    if (connection.responseCode !in 200..299) continue
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    CODE_JSON.find(body)?.groupValues?.get(1)?.uppercase()?.let {
                        cache[ip] = it
                        return it
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                // سرویس بعدی
            }
        }
        return null
    }

    /**
     * فقط کانفیگ‌هایی که IP مقصدشان در [country] است. موازی و با سقف زمان.
     */
    fun filter(configs: List<ProxyConfig>, country: String): List<ProxyConfig> {
        val pool = Executors.newFixedThreadPool(LOOKUP_THREADS)
        try {
            val futures = configs.map { c ->
                c to pool.submit(Callable { countryOfHost(c.dialHost) })
            }
            return futures.mapNotNull { (c, future) ->
                val code = runCatching { future.get(TIMEOUT_MS * 3L, TimeUnit.MILLISECONDS) }.getOrNull()
                if (code == country) c else null
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun countryOfHost(host: String): String? {
        val ip = if (host.all { it.isDigit() || it == '.' || it == ':' }) host
        else runCatching { InetAddress.getByName(host).hostAddress }.getOrNull()
        return ip?.let { countryOf(it) }
    }
}
