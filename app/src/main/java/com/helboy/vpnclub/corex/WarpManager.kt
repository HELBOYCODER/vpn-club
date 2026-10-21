package com.helboy.vpnclub.corex

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * WARP رایگان کلادفلر (WireGuard) — بدون اپ 1.1.1.1:
 *  ۱) کلید X25519 روی خود گوشی ساخته می‌شود (کلید خصوصی هیچ‌وقت از گوشی خارج نمی‌شود)
 *  ۲) فقط کلید عمومی در API عمومی WARP ثبت می‌شود → کلید عمومی peer، آدرس‌های داخلی، reserved
 *  ۳) اندپوینت‌های WARP (رنج‌ها × پورت‌ها) با هسته‌ی Xray تست می‌شوند؛ سریع‌ترین وصل می‌شود
 *  ۴) «نویز» (بسته‌های تصادفی UDP قبل از هندشیک، مثل WARP-in-PattNG) برای عبور از فیلتر WireGuard ایران
 */
object WarpManager {

    private const val API = "https://api.cloudflareclient.com/v0a2158/reg"

    data class Account(
        val privateKey: String, val peerPublicKey: String, val v4: String, val v6: String,
        val reserved: List<Int>, val id: String, val token: String,
    ) {
        fun toJson() = JSONObject().put("priv", privateKey).put("peer", peerPublicKey).put("v4", v4).put("v6", v6)
            .put("reserved", org.json.JSONArray(reserved)).put("id", id).put("token", token)

        companion object {
            fun fromJson(o: JSONObject) = Account(o.getString("priv"), o.getString("peer"), o.getString("v4"),
                o.optString("v6"), (0 until (o.optJSONArray("reserved")?.length() ?: 0)).map { o.getJSONArray("reserved").getInt(it) },
                o.optString("id"), o.optString("token"))
        }
    }

    private fun file(ctx: Context) = File(ctx.filesDir, "warp_account.json")

    fun account(ctx: Context): Account? = try { Account.fromJson(JSONObject(file(ctx).readText())) } catch (_: Exception) { null }

    fun clear(ctx: Context) { file(ctx).delete() }

    /** ثبت دستگاه تازه در WARP. خطای شبکه → استثنا. */
    fun register(ctx: Context): Account {
        val priv = X25519.newPrivateKey()
        val pub = X25519.publicKey(priv)
        val tos = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val body = JSONObject().put("key", Base64.getEncoder().encodeToString(pub)).put("install_id", "").put("fcm_token", "")
            .put("tos", tos).put("model", "PC").put("serial_number", "").put("locale", "en_US")
        val req = Request.Builder().url(API)
            .header("User-Agent", "okhttp/3.12.1").header("CF-Client-Version", "a-6.10-2158")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val text = NetHttp.execute(req, "WARP_REG").use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw java.io.IOException("WARP API HTTP ${r.code}: ${t.take(120)}")
            t
        }
        val o = JSONObject(text).let { it.optJSONObject("result") ?: it }
        val cfg = o.getJSONObject("config")
        val peer = cfg.getJSONArray("peers").getJSONObject(0).getString("public_key")
        val addr = cfg.getJSONObject("interface").getJSONObject("addresses")
        val reserved = Base64.getDecoder().decode(cfg.optString("client_id", "AAAA")).take(3).map { it.toInt() and 0xFF }
        val acc = Account(Base64.getEncoder().encodeToString(priv), peer, addr.getString("v4"), addr.optString("v6"),
            reserved, o.optString("id"), o.optString("token"))
        file(ctx).writeText(acc.toJson().toString())
        DiagLogger.log("WARP registered id=${acc.id.take(8)}… v4=${acc.v4} reserved=${acc.reserved}")
        return acc
    }

    /** رنج‌ها و پورت‌های شناخته‌شده‌ی اندپوینت WARP. */
    val RANGES = listOf("162.159.192", "162.159.193", "162.159.195", "188.114.96", "188.114.97", "188.114.98", "188.114.99")
    val PORTS = listOf(2408, 500, 854, 859, 864, 878, 880, 890, 891, 894, 903, 908, 928, 934, 939, 942, 943, 945, 946, 955,
        968, 987, 988, 1002, 1010, 1014, 1018, 1070, 1074, 1180, 1387, 1701, 1843, 2371, 2506, 3138, 3476, 3581, 3854,
        4177, 4198, 4233, 4500, 5279, 5956, 7103, 7152, 7156, 7281, 7559, 8319, 8742, 8854, 8886)

    /** [n] اندپوینت تصادفی (همیشه شامل پیش‌فرض رسمی 162.159.192.1:2408). */
    fun candidates(n: Int, rng: SecureRandom = SecureRandom()): List<Pair<String, Int>> {
        val out = linkedSetOf("162.159.192.1" to 2408)
        while (out.size < n) out += "${RANGES[rng.nextInt(RANGES.size)]}.${1 + rng.nextInt(254)}" to PORTS[rng.nextInt(PORTS.size)]
        return out.toList()
    }

    /** کانفیگ WireGuard به‌صورت لینک wireguard:// (قابل کپی/QR در v2rayNG/PattNG/Hiddify). */
    fun toConfig(acc: Account, host: String, port: Int, tag: String = "WARP رایگان"): ProxyConfig? {
        val addresses = listOfNotNull("${acc.v4}/32", acc.v6.takeIf { it.isNotBlank() }?.let { "$it/128" }).joinToString(",")
        val draft = ProxyConfig("wireguard", host, port, acc.privateKey, listOf(
            "publickey" to acc.peerPublicKey, "address" to addresses,
            "reserved" to acc.reserved.joinToString(","), "mtu" to "1280"), tag = tag)
        return LinkParser.parse(ShareLinkBuilder.build(draft))
    }
}
