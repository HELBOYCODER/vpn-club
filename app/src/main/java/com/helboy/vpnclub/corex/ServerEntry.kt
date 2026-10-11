package com.helboy.vpnclub.corex

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * یک «سرور منتخب» = کانفیگ + آدرس dial (IP تمیز تزریق‌شده) + پروفایل ایران + نتیجه‌ی تست تونل.
 *
 * همین مدل هم در تست (موتور) و هم در سرویس VPN استفاده می‌شود تا آنچه تست شد
 * دقیقاً همان چیزی باشد که وصل می‌شود (باگ قبلی: تست با FragmentProxy، سرویس بدون آن).
 */
data class ServerEntry(
    val config: ProxyConfig,
    val variantId: String? = null,
    val useFrag: Boolean = false,
    val latencyMs: Long = 0,
    val downBps: Double = 0.0,
    val upBps: Double = 0.0,
    val testedAt: Long = 0,
    /** سرور پشت کلادفلر است (قابل تزریق IP تمیز). */
    val cf: Boolean = false
) {
    val id: String get() =
        "${config.scheme}|${config.host}|${config.port}|${config.uuidOrUser}|${config.dialHost}|${variantId ?: "-"}"

    /** کلید یکتا فقط بر پایه‌ی سرور (بدون IP تزریقی/پروفایل) — برای جلوگیری از تکرار یک سرور. */
    val serverKey: String get() = "${config.scheme}|${config.host}|${config.port}|${config.uuidOrUser}"

    val country: String get() = countryOf(config.tag)

    val injected: Boolean get() = config.isInjected

    /** امتیاز: کمترین تاخیر واقعی تونل = بهترین (اگر سرعت دانلود هم اندازه‌گیری شده بود، وزن دارد). */
    val score: Double get() =
        if (latencyMs <= 0) 0.0
        else 1_000_000.0 / (latencyMs + 50) + (if (downBps > 0) downBps / 1_000_000.0 else 0.0)

    fun toJson(): JSONObject = JSONObject()
        .put("raw", config.raw)
        .put("dialHost", config.dialHost)
        .put("dialPort", config.dialPort)
        .put("variant", variantId ?: "")
        .put("frag", useFrag)
        .put("lat", latencyMs)
        .put("down", downBps)
        .put("up", upBps)
        .put("at", testedAt)
        .put("cf", cf)

    companion object {
        fun fromJson(o: JSONObject?): ServerEntry? {
            if (o == null) return null
            val cfg = LinkParser.parse(o.optString("raw")) ?: return null
            val dh = o.optString("dialHost")
            val dp = o.optInt("dialPort", cfg.port)
            val c = if (dh.isNotEmpty() && (dh != cfg.host || dp != cfg.port)) cfg.withDial(dh, dp) else cfg
            return ServerEntry(
                config = c,
                variantId = o.optString("variant").ifEmpty { null },
                useFrag = o.optBoolean("frag", false),
                latencyMs = o.optLong("lat"),
                downBps = o.optDouble("down", 0.0),
                upBps = o.optDouble("up", 0.0),
                testedAt = o.optLong("at"),
                cf = o.optBoolean("cf", false)
            )
        }

        /** کشور از روی برچسب (ایموجی پرچم یا کد دو حرفی). */
        fun countryOf(tag: String): String {
            val t = tag.trim()
            if (t.isEmpty()) return ""
            // ایموجی پرچم = دو Regional Indicator
            var i = 0
            while (i < t.length) {
                val cp = t.codePointAt(i)
                if (cp in 0x1F1E6..0x1F1FF && i + Character.charCount(cp) < t.length) {
                    val cp2 = t.codePointAt(i + Character.charCount(cp))
                    if (cp2 in 0x1F1E6..0x1F1FF) {
                        return "" + ('A' + (cp - 0x1F1E6)) + ('A' + (cp2 - 0x1F1E6))
                    }
                }
                i += Character.charCount(cp)
            }
            val m = Regex("(?<![A-Za-z])([A-Z]{2})(?![A-Za-z])").find(t)
            return m?.groupValues?.get(1) ?: ""
        }

        /** ایموجی پرچم از کد دو حرفی. */
        fun flagOf(cc: String): String {
            if (cc.length != 2 || !cc.all { it in 'A'..'Z' }) return "🌐"
            val a = 0x1F1E6 + (cc[0] - 'A')
            val b = 0x1F1E6 + (cc[1] - 'A')
            return String(Character.toChars(a)) + String(Character.toChars(b))
        }
    }
}

/**
 * ذخیره‌ی پایدار: لیست ۱۰ سرور منتخب + IPهای تمیز کلادفلر (کش برای شروع سریع‌تر دفعه‌ی بعد).
 */
class ServerStore(context: Context) {

    private val serversFile = File(context.filesDir, "top_servers.json")
    private val ipsFile = File(context.filesDir, "clean_ips.json")
    private val lock = Any()

    fun loadServers(): List<ServerEntry> = synchronized(lock) {
        try {
            if (!serversFile.exists()) return emptyList()
            val arr = JSONArray(serversFile.readText())
            (0 until arr.length()).mapNotNull { ServerEntry.fromJson(arr.optJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    fun saveServers(list: List<ServerEntry>) = synchronized(lock) {
        try {
            serversFile.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
        } catch (_: Exception) {}
    }

    fun loadCleanIps(): List<CloudflareScanner.CleanIp> = synchronized(lock) {
        try {
            if (!ipsFile.exists()) return emptyList()
            val arr = JSONArray(ipsFile.readText())
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let {
                    CloudflareScanner.CleanIp(it.optString("ip"), it.optInt("port", 443), it.optLong("ms"))
                }
            }.filter { it.ip.isNotEmpty() }
        } catch (_: Exception) { emptyList() }
    }

    fun saveCleanIps(list: List<CloudflareScanner.CleanIp>) = synchronized(lock) {
        try {
            ipsFile.writeText(JSONArray().apply {
                list.forEach { put(JSONObject().put("ip", it.ip).put("port", it.port).put("ms", it.tlsMs)) }
            }.toString())
        } catch (_: Exception) {}
    }
}
