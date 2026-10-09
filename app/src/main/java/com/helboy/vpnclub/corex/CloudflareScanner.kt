package com.helboy.vpnclub.corex

import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * اسکنر IPهای تمیز کلادفلر.
 *
 * روش: رنج‌های رسمی کلادفلر، نمونه‌گیری تصادفی IP، هندشیک واقعی TLS با SNI کلادفلر.
 * IP ای «تمیز» است که handshake کامل TLS را پاس کند.
 *
 * از SSLContext خود JVM استفاده می‌کنیم (stdlib) — ClientHello دستی شکننده بود و
 * در تست واقعی IPهای سالم را رد می‌کرد.
 */
object CloudflareScanner {

    /** پورت‌هایی که در ایران معمولاً بازترند و TLS کلادفلر روی آن‌ها فعال است. */
    val CF_PORTS = listOf(443, 2053, 2083, 2087, 2096, 8443)

    /** رنج‌های رسمی کلادفلر (cloudflare.com/ips-v4). */
    val CF_RANGES_V4 = listOf(
        "173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22",
        "141.101.64.0/18", "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20",
        "197.234.240.0/22", "198.41.128.0/17", "162.158.0.0/15", "104.16.0.0/13",
        "104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22"
    )

    /** SNI تست — دامنه‌ای پشت کلادفلر. */
    const val TEST_SNI = "speed.cloudflare.com"

    data class CleanIp(val ip: String, val port: Int, val tlsMs: Long)

    /**
     * اسکن: از هر رنج [perRange] IP می‌سازد و همه را موازی تست می‌کند.
     * @return IPهای تمیز، مرتب بر اساس زمان هندشیک (سریع‌ترین اول)
     */
    fun scan(
        perRange: Int = 16,
        ports: List<Int> = listOf(443),
        maxConcurrency: Int = 32,
        timeoutMs: Int = 2_000,
        onProgress: (tested: Int, cleanFound: Int) -> Unit = { _, _ -> }
    ): List<CleanIp> {
        val candidates = buildCandidates(perRange, ports)
        val pool = Executors.newFixedThreadPool(maxConcurrency)
        val results = ConcurrentHashMap<String, CleanIp>()
        val tested = AtomicInteger(0)
        val cleanCount = AtomicInteger(0)
        val total = candidates.size

        val futures = candidates.map { (ip, port) ->
            pool.submit {
                val ms = probeTls(ip, port, timeoutMs)
                val done = tested.incrementAndGet()
                if (ms > 0) {
                    results[ip] = CleanIp(ip, port, ms)
                    cleanCount.incrementAndGet()
                }
                if (done % 32 == 0 || done == total) onProgress(done, cleanCount.get())
            }
        }
        futures.forEach { runCatching { it.get() } }
        pool.shutdown()
        pool.awaitTermination(5, TimeUnit.SECONDS)

        return results.values.sortedBy { it.tlsMs }
    }

    /** تولید کاندید: هر رنج چند IP × پورت‌ها. */
    internal fun buildCandidates(perRange: Int, ports: List<Int>): List<Pair<String, Int>> {
        val out = mutableListOf<Pair<String, Int>>()
        for (cidr in CF_RANGES_V4) {
            for (ip in randomIpsInCidr(cidr, perRange)) for (p in ports) out.add(ip to p)
        }
        return out
    }

    /** چند IP تصادفی داخل یک CIDR v4 (بدون آدرس شبکه/برادکست). */
    internal fun randomIpsInCidr(cidr: String, count: Int): List<String> {
        val parts = cidr.split("/")
        val prefix = parts[1].toInt()
        val baseInt = ipToInt(parts[0])
        val usable = ((1L shl (32 - prefix)) - 2).coerceAtLeast(1)
        val out = LinkedHashSet<Long>()
        val rng = SecureRandom()
        while (out.size < count && out.size < usable) {
            val off = 1 + (Math.abs(rng.nextLong()) % usable)
            out.add(baseInt + off)
        }
        return out.map { intToIp(it) }
    }

    /**
     * هندشیک واقعی TLS با SNI کلادفلر.
     * @return میلی‌ثانیه یا -۱ در صورت شکست
     */
    internal fun probeTls(ip: String, port: Int, timeoutMs: Int): Long {
        var raw: Socket? = null
        var tls: SSLSocket? = null
        return try {
            raw = Socket()
            raw.tcpNoDelay = true
            raw.soTimeout = timeoutMs
            raw.connect(InetSocketAddress(ip, port), timeoutMs)

            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, TRUST_ALL, SecureRandom())
            tls = ctx.socketFactory.createSocket(raw, TEST_SNI, port, true) as SSLSocket
            tls.soTimeout = timeoutMs
            val params = tls.sslParameters
            params.serverNames = listOf(SNIHostName(TEST_SNI))
            tls.sslParameters = params

            val start = System.currentTimeMillis()
            tls.startHandshake()
            val elapsed = System.currentTimeMillis() - start
            if (tls.session != null) elapsed else -1L
        } catch (_: Exception) {
            -1L
        } finally {
            runCatching { tls?.close() }
            runCatching { raw?.close() }
        }
    }

    /** در اسکنر فقط «کلادفلر بودن» مهم است، نه اعتبار گواهی. */
    private val TRUST_ALL = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
    })

    private fun ipToInt(ip: String): Long =
        ip.split(".").fold(0L) { acc, p -> (acc shl 8) or (p.toLong() and 0xFF) }

    private fun intToIp(v: Long): String =
        listOf((v shr 24) and 0xFF, (v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
            .joinToString(".")
}
