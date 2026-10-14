package com.helboy.vpnclub.corex

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** Bounded HTTPS traffic proves the tunnel can carry data; idle user counters are never a health signal. */
object HealthTester {
    fun tcpPing(host: String, port: Int, timeoutMs: Int = 2_000): Long = try {
        val start = System.nanoTime()
        java.net.Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
    } catch (_: Exception) { -1 }

    fun tunnelLatency(socksPort: Int, timeoutMs: Int = 4_000): Long {
        for (url in listOf("https://www.gstatic.com/generate_204", "https://cp.cloudflare.com/generate_204")) {
            val connection = open(socksPort, url, timeoutMs)
            try {
                val start = System.nanoTime()
                if (connection.responseCode == 204)
                    return ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
            } catch (_: Exception) { } finally { connection.disconnect() }
        }
        return -1
    }

    fun connectionQuality(socksPort: Int): ConnectionQuality {
        val latency = tunnelLatency(socksPort)
        if (latency <= 0) return ConnectionQuality(-1, 0.0)
        return ConnectionQuality(latency, downloadThroughProxy(socksPort))
    }

    fun downloadThroughProxy(socksPort: Int, bytes: Long = 131_072, timeoutMs: Int = 5_000): Double {
        // Some Workers cannot connect back to Cloudflare IPs. A fixed Google binary provides a separate fallback.
        val urls = listOf("https://speed.cloudflare.com/__down?bytes=$bytes", "https://www.gstatic.com/webp/gallery/1.webp")
        for (url in urls) {
            val connection = open(socksPort, url, timeoutMs)
            try {
                if (connection.responseCode != 200) continue
                val type = connection.contentType.orEmpty().lowercase()
                if (type.contains("text/") || type.contains("json")) continue
                val start = System.nanoTime()
                var read = 0L
                val buffer = ByteArray(16 * 1024)
                connection.inputStream.use { input ->
                    while (read < bytes) {
                        val n = input.read(buffer, 0, minOf(buffer.size.toLong(), bytes - read).toInt())
                        if (n < 0) break
                        read += n
                        if ((System.nanoTime() - start) / 1_000_000 >= timeoutMs) break
                    }
                }
                if (read < 16_384) continue
                val seconds = ((System.nanoTime() - start) / 1_000_000_000.0).coerceAtLeast(0.001)
                return read / seconds
            } catch (_: Exception) { } finally { connection.disconnect() }
        }
        return 0.0
    }

    private fun open(port: Int, url: String, timeoutMs: Int): HttpURLConnection =
        (URL(url).openConnection(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))) as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("User-Agent", "HellboyVPN/4.3")
            setRequestProperty("Accept-Encoding", "identity")
        }
}
