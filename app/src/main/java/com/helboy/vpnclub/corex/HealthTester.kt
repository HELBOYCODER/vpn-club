package com.helboy.vpnclub.corex

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * سنجش سلامت واقعی یک کانفیگ، قبل از اتصال:
 *  ۱. TCP ping مستقیم به dial آدرس (IP تزریق‌شده یا دامنه اصلی)
 *  ۲. دانلود و آپلود واقعی از روی تونل پروکسی SOCKS5 که هسته‌ی Xray روی localhost باز می‌کند
 *
 * قانون کارفرما: کانفیگی که پینگ دارد ولی دانلود/آپلودش صفر است نباید وصل شود.
 */
object HealthTester {

    /**
     * تست پینگ TCP به آدرس و پورت کانفیگ.
     * خروجی: میلی‌ثانیه؛ -1 یعنی پاسخ نداد.
     */
    fun tcpPing(host: String, port: Int, timeoutMs: Int = 2_000): Long {
        var socket: java.net.Socket? = null
        return try {
            val start = System.currentTimeMillis()
            socket = java.net.Socket()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            System.currentTimeMillis() - start
        } catch (_: Exception) {
            -1L
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * تست دانلود واقعی از طریق SOCKS5 پروکسی (تونل).
     * @param socksPort پورت SOCKS5 که هسته روی 127.0.0.1 باز کرده
     * @param bytes تعداد بایت برای دانلود (پیش‌فرض ۲ مگابایت)
     * @return بایت بر ثانیه؛ ۰.۰ یعنی تونل مرده
     */
    fun downloadThroughProxy(socksPort: Int, bytes: Long = 2_000_000, timeoutMs: Int = 12_000): Double {
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            val conn = URL("https://speed.cloudflare.com/__down?bytes=$bytes")
                .openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "VPNClub/1.0")
            val start = System.currentTimeMillis()
            var read = 0L
            val buf = ByteArray(32 * 1024)
            conn.inputStream.use { input ->
                while (read < bytes) {
                    val n = input.read(buf)
                    if (n < 0) break
                    read += n
                    // سقف کل زمان: تونل کند هم با نرخ واقعی‌اش سنجیده می‌شود، نه گیر کردن
                    if (System.currentTimeMillis() - start > timeoutMs) break
                }
            }
            val sec = (System.currentTimeMillis() - start) / 1000.0
            if (sec <= 0 || read == 0L) 0.0 else read / sec
        } catch (_: Exception) {
            0.0
        }
    }

    /**
     * تست آپلود واقعی از طریق SOCKS5 پروکسی.
     * @return بایت بر ثانیه؛ ۰.۰ یعنی تونل مرده
     */
    fun uploadThroughProxy(socksPort: Int, bytes: Int = 400_000, timeoutMs: Int = 12_000): Double {
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            val conn = URL("https://speed.cloudflare.com/__up")
                .openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestMethod("POST")
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("User-Agent", "VPNClub/1.0")
            val payload = ByteArray(bytes)
            java.security.SecureRandom().nextBytes(payload)
            val start = System.currentTimeMillis()
            conn.outputStream.use { it.write(payload) }
            conn.responseCode
            val sec = (System.currentTimeMillis() - start) / 1000.0
            if (sec <= 0) 0.0 else bytes / sec
        } catch (_: Exception) {
            0.0
        }
    }

    /**
     * پروب سبک و بدون هسته: آیا این سرور واقعاً «زنده و از نوع خودش» است؟
     *  - TLS/Reality/Trojan: هندشیک TLS با SNI کانفیگ
     *  - ws/httpupgrade روی TLS: بعد از هندشیک، درخواست Upgrade و انتظار «101»
     *  - بدون TLS (ss/vmess-tcp): فقط اتصال TCP
     * [host]/[port] می‌تواند IP تمیز تزریقی باشد.
     * @return میلی‌ثانیه یا -۱
     */
    fun protocolProbe(c: ProxyConfig, host: String, port: Int, timeoutMs: Int = 2_500): Long {
        val q = c.query.toMap()
        val sec = q["security"] ?: ""
        val net = q["type"] ?: q["network"] ?: "tcp"
        val tls = sec == "tls" || sec == "reality" || q["tls"] == "tls" || c.scheme == "trojan"
        var raw: java.net.Socket? = null
        var ssl: javax.net.ssl.SSLSocket? = null
        return try {
            val start = System.currentTimeMillis()
            raw = java.net.Socket()
            raw.tcpNoDelay = true
            raw.soTimeout = timeoutMs
            raw.connect(InetSocketAddress(host, port), timeoutMs)
            if (!tls) {
                if (net == "ws" || net == "httpupgrade") {
                    val path = q["path"]?.takeIf { it.isNotEmpty() } ?: "/"
                    val hostHeader = q["host"]?.takeIf { it.isNotEmpty() } ?: c.sniHost
                    raw.getOutputStream().write(("GET $path HTTP/1.1\r\nHost: $hostHeader\r\n" +
                        "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray())
                    raw.getOutputStream().flush()
                    val buf = ByteArray(32)
                    var n = 0
                    while (n < 12) { val r = raw.getInputStream().read(buf, n, buf.size - n); if (r < 0) break; n += r }
                    if (!String(buf, 0, n).startsWith("HTTP/1.1 101")) return -1L
                }
                return System.currentTimeMillis() - start
            }
            val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
            ctx.init(null, CloudflareScanner.TRUST_ALL, java.security.SecureRandom())
            val sni = c.sniHost
            ssl = ctx.socketFactory.createSocket(raw, sni, port, true) as javax.net.ssl.SSLSocket
            ssl.soTimeout = timeoutMs
            val p = ssl.sslParameters
            p.serverNames = listOf(javax.net.ssl.SNIHostName(sni))
            if (net == "ws" || net == "httpupgrade") p.applicationProtocols = arrayOf("http/1.1")
            ssl.sslParameters = p
            ssl.startHandshake()
            if ((net == "ws" || net == "httpupgrade") && sec == "tls") {
                val hostHeader = q["host"]?.takeIf { it.isNotEmpty() } ?: sni
                val path = q["path"]?.takeIf { it.isNotEmpty() } ?: "/"
                ssl.outputStream.write(("GET $path HTTP/1.1\r\nHost: $hostHeader\r\n" +
                    "Upgrade: ${if (net == "ws") "websocket" else "websocket"}\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray())
                ssl.outputStream.flush()
                val buf = ByteArray(32)
                var n = 0
                while (n < 12) { val r = ssl.inputStream.read(buf, n, buf.size - n); if (r < 0) break; n += r }
                if (!String(buf, 0, n).startsWith("HTTP/1.1 101")) return -1L
            }
            System.currentTimeMillis() - start
        } catch (_: Exception) { -1L }
        finally {
            try { ssl?.close() } catch (_: Exception) {}
            try { raw?.close() } catch (_: Exception) {}
        }
    }

    /**
     * تاخیر واقعی از داخل تونل: زمان یک درخواست کوچک HTTPS (generate_204).
     * @return میلی‌ثانیه یا -۱
     */
    fun tunnelLatency(socksPort: Int, timeoutMs: Int = 6_000): Long {
        // اول گوگل (برای ورکرهای کلادفلر که به IP خود کلادفلر وصل نمی‌شوند)، بعد کلادفلر
        for (u in TUNNEL_TEST_URLS) {
            val ms = latencyVia(socksPort, u, timeoutMs)
            if (ms > 0) return ms
        }
        return -1L
    }

    private val TUNNEL_TEST_URLS = listOf("https://www.gstatic.com/generate_204", "https://cp.cloudflare.com/generate_204")

    private fun latencyVia(socksPort: Int, url: String, timeoutMs: Int): Long {
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            val conn = URL(url).openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "HellboyVPN/3")
            val start = System.currentTimeMillis()
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..399) System.currentTimeMillis() - start else -1L
        } catch (_: Exception) { -1L }
    }

    data class TunnelResult(val latencyMs: Long, val downBps: Double, val upBps: Double, val error: String?) {
        val ok: Boolean get() = error == null
    }

    /**
     * تست سریع تونل: تاخیر → دانلود ۱ مگ (سقف ۸ ثانیه) → آپلود کوچک.
     * کانفیگ فقط-پینگ (دانلود صفر) رد می‌شود.
     */
    fun testTunnel(socksPort: Int, withUpload: Boolean = true, quick: Boolean = false): TunnelResult {
        val lat = tunnelLatency(socksPort, if (quick) 5_000 else 6_000)
        if (lat < 0) return TunnelResult(-1, 0.0, 0.0, "no_response")
        val down = if (quick) downloadThroughProxy(socksPort, bytes = 500_000, timeoutMs = 5_000)
            else downloadThroughProxy(socksPort, bytes = 1_000_000, timeoutMs = 8_000)
        if (down < HealthResult.MIN_DOWNLOAD_BPS) return TunnelResult(lat, down, 0.0, "dead_tunnel")
        val up = if (withUpload) uploadThroughProxy(socksPort, bytes = 200_000, timeoutMs = 8_000) else 0.0
        return TunnelResult(lat, down, up, null)
    }

    /**
     * چرخه‌ی کامل سلامت یک کانفیگ، قبل از اینکه کاربر متصل شود.
     * tcp → (اگر پاسخ داد) دانلود → آپلود.
     */
    fun testConfig(config: ProxyConfig, socksPort: Int? = null): HealthResult {
        val tcpMs = tcpPing(config.dialHost, config.dialPort)
        if (tcpMs < 0) return HealthResult(config, -1, 0.0, 0.0, error = "tcp_fail")

        // اگر پروکسی محلی در دسترس نیست، فقط TCP می‌سنجیم (کانفیگ کاندید محسوب می‌شود، ولی usable نه)
        val port = socksPort ?: return HealthResult(config, tcpMs, 0.0, 0.0, error = "no_tunnel")

        val down = downloadThroughProxy(port)
        if (down < HealthResult.MIN_DOWNLOAD_BPS) {
            return HealthResult(config, tcpMs, down, 0.0, error = "dead_tunnel")
        }
        val up = uploadThroughProxy(port)
        return HealthResult(config, tcpMs, down, up)
    }
}
