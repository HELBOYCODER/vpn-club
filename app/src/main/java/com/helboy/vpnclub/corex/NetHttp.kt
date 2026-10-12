package com.helboy.vpnclub.corex

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * HTTP برای کارهای خود اپ (دریافت ساب، API کلادفلر، دانلود کد پنل).
 *
 * سوکت‌های خود اپ عمداً از تونل VPN خارج‌اند (جلوگیری از حلقه). ولی گیت‌هاب / API کلادفلر /
 * workers.dev در ایران اغلب بدون VPN باز نمی‌شوند؛ پس وقتی VPN وصل است، اول از داخل تونل
 * (SOCKS هسته) و بعد مستقیم امتحان می‌شود. وقتی وصل نیست، فقط مستقیم.
 */
object NetHttp {

    private val base = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun viaTunnel(): OkHttpClient? {
        if (HellboyVpnService.status.value.state != SvcState.UP) return null
        val port = HellboyVpnService.activeSocksPort
        return base.newBuilder().proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))).build()
    }

    /** مسیرهای قابل امتحان به ترتیب: (برچسب، کلاینت). */
    private fun routes(): List<Pair<String, OkHttpClient>> =
        listOfNotNull(viaTunnel()?.let { "tunnel" to it }, "direct" to base)

    /**
     * اجرای درخواست با امتحان مسیرها. پاسخ HTTP (حتی 4xx) نتیجه است؛ فقط خطای شبکه باعث
     * امتحان مسیر بعدی می‌شود. [tag] برای لاگ.
     */
    fun execute(req: Request, tag: String): Response {
        var last: Exception? = null
        for ((route, client) in routes()) {
            try {
                val r = client.newCall(req).execute()
                DiagLogger.log("HTTP $tag ${req.method} ${req.url.host}${req.url.encodedPath.take(60)} via=$route -> ${r.code}")
                return r
            } catch (e: Exception) {
                last = e
                DiagLogger.log("HTTP $tag ${req.method} ${req.url.host} via=$route FAIL ${e.javaClass.simpleName}: ${e.message?.take(80)}")
            }
        }
        throw last ?: java.io.IOException("no route")
    }

    /** GET متن ساده؛ خطای HTTP → استثنا. */
    fun getText(url: String, tag: String, ua: String = "HellboyVPN/3 (Android)"): String {
        val req = Request.Builder().url(url).header("User-Agent", ua).build()
        execute(req, tag).use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return r.body?.string().orEmpty()
        }
    }
}
