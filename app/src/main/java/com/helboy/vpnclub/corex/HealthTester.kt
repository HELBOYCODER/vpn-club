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
