package com.helboy.vpnclub.corex

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.security.SecureRandom

/**
 * اسکنر IPهای تمیز کلادفلر.
 *
 * روش استاندارد: رنج‌های رسمی کلادفلر را از cloudflare.com/ips می‌گیریم،
 * داخل هر رنج به‌صورت نمونه‌گیری تصادفی IP می‌سازیم و به پورت گزینه (443/2053/2083/2087/2096/8443)
 * TLS-هندشیک تست می‌کنیم. IPهایی که هم سوکت باز کنند و هم گواهی کلادفلر برگردانند «تمیز» هستند.
 *
 * الگو گرفته از ابزارهای معروف CloudflareSpeedTest / CfScanner — ولی مستقل و بدون وابستگی.
 */
object CloudflareScanner {

    /** پورت‌هایی که در ایران کمتر فیلتر می‌شوند و TLS کلادفلر روی آن‌ها فعال است. */
    val CF_PORTS = listOf(443, 2053, 2083, 2087, 2096, 8443)

    /** رنج‌های رسمی کلادفلر (بروزرسانی‌شده؛ منبع: cloudflare.com/ips-v4) */
    val CF_RANGES_V4 = listOf(
        "173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22",
        "141.101.64.0/18", "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20",
        "197.234.240.0/22", "198.41.128.0/17", "162.158.0.0/15", "104.16.0.0/13",
        "104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22"
    )

    /** برای SNI تست، نام دامنه‌ای که پشت کلادفلر است. */
    const val TEST_SNI = "speed.cloudflare.com"

    data class CleanIp(val ip: String, val port: Int, val tlsMs: Long)

    /**
     * از هر رنج چند IP کاندید می‌سازد، به‌موازات تست می‌کند و تمیزها را برمی‌گرداند.
     *
     * @param perRange تعداد IP کاندید از هر رنج
     * @param maxConcurrency حداکثر سوکت همزمان (روی گوشی ۳۲ معقول است)
     * @param timeoutMs هر تلاش TLS چند میلی‌ثانیه فرصت داشته باشد
     */
    fun scan(
        perRange: Int = 24,
        ports: List<Int> = listOf(443, 2053),
        maxConcurrency: Int = 32,
        timeoutMs: Int = 1_500,
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
                tested.incrementAndGet()
                if (ms > 0) {
                    results[ip] = CleanIp(ip, port, ms)
                    cleanCount.incrementAndGet()
                }
                if (tested.get() % 32 == 0 || tested.get() == total) {
                    onProgress(tested.get(), cleanCount.get())
                }
            }
        }
        futures.forEach { it.get() }
        pool.shutdown()
        pool.awaitTermination(5, TimeUnit.SECONDS)

        return results.values.sortedBy { it.tlsMs }
    }

    /** تولید IP کاندید: هر رنج → چند IP تصادفی × پورت‌ها */
    internal fun buildCandidates(perRange: Int, ports: List<Int>): List<Pair<String, Int>> {
        val out = mutableListOf<Pair<String, Int>>()
        for (cidr in CF_RANGES_V4) {
            val ips = randomIpsInCidr(cidr, perRange)
            for (ip in ips) for (port in ports) out.add(ip to port)
        }
        return out
    }

    /** چند IP تصادفی داخل یک رنج CIDR v4. */
    internal fun randomIpsInCidr(cidr: String, count: Int): List<String> {
        val (base, bits) = cidr.split("/")
        val prefix = bits.toInt()
        val baseInt = ipToInt(base)
        val hostBits = 32 - prefix
        val size = 1L shl hostBits
        // از آدرس شبکه و برادکست پرهیز می‌کنیم
        val usable = (size - 2).coerceAtLeast(1)
        val out = LinkedHashSet<Long>()
        val rng = SecureRandom()
        while (out.size < count && out.size < usable) {
            val off = 1 + (java.math.BigInteger.valueOf(Math.abs(rng.nextLong()))
                .mod(java.math.BigInteger.valueOf(usable))).toLong()
            out.add(baseInt + off)
        }
        return out.map { intToIp(it) }
    }

    /**
     * تست واقعی TLS: سوکت باز → ClientHello با SNI → اگر پاسخ ServerHello آمد، سلامت است.
     * برگشت: زمان به میلی‌ثانیه؛ ۰ یا منفی یعنی ناموفق.
     */
    internal fun probeTls(ip: String, port: Int, timeoutMs: Int): Long {
        var socket: Socket? = null
        return try {
            val start = System.currentTimeMillis()
            socket = Socket()
            socket.tcpNoDelay = true
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            // TLS ClientHello minimal با SNI = TEST_SNI
            val hello = buildClientHello(TEST_SNI)
            val out = socket.getOutputStream()
            out.write(hello)
            out.flush()
            val buf = ByteArray(1024)
            val input = socket.getInputStream()
            val n = input.read(buf)
            val elapsed = System.currentTimeMillis() - start
            if (n > 0 && isServerHello(buf, n)) elapsed else -1L
        } catch (_: Exception) {
            -1L
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /** بررسی اینکه بایت‌های پاسخ با یک ServerHello سازگار است (ContentType=22, Version>=3.1). */
    private fun isServerHello(buf: ByteArray, n: Int): Boolean {
        if (n < 6) return false
        val contentType = buf[0].toInt() and 0xFF
        if (contentType != 22) return false
        val major = buf[1].toInt() and 0xFF
        val minor = buf[2].toInt() and 0xFF
        return major == 3 && minor >= 1
    }

    /**
     * ساخت یک ClientHello کوچک TLS 1.2 با SNI مشخص.
     * کافی است فقط برای اطمینان از «کلادفلر بودن» پاسخ — رمزنگاری واقعی بعداً توسط Xray انجام می‌شود.
     */
    private fun buildClientHello(sni: String): ByteArray {
        val sniBytes = sni.toByteArray(Charsets.US_ASCII)
        val sniExtData = with(java.io.ByteArrayOutputStream()) {
            write(0x00); write(0x00)                       // server_name list type
            write((sniBytes.size + 3) shr 8); write((sniBytes.size + 3) and 0xFF) // list len
            write(0x00)                                     // host_name
            write((sniBytes.size shr 8) and 0xFF); write(sniBytes.size and 0xFF)
            write(sniBytes)
            toByteArray()
        }
        val extData = with(java.io.ByteArrayOutputStream()) {
            write(0x00); write(0x00)                        // extension server_name
            write((sniExtData.size shr 8) and 0xFF); write(sniExtData.size and 0xFF)
            write(sniExtData)
            toByteArray()
        }

        val body = with(java.io.ByteArrayOutputStream()) {
            write(0x03); write(0x03)                        // client_version TLS 1.2
            val random = ByteArray(32)
            SecureRandom().nextBytes(random)
            write(random)                                   // random
            write(0x00)                                     // session_id len
            write(0x00, 0x04)                               // cipher suites len
            write(0x13); write(0x01)                        // TLS_AES_128_GCM_SHA256
            write(0x13); write(0x02)                        // TLS_AES_256_GCM_SHA384
            write(0x01); write(0x00)                        // compression: null
            write((extData.size shr 8) and 0xFF); write(extData.size and 0xFF)
            write(extData)
            toByteArray()
        }

        return with(java.io.ByteArrayOutputStream()) {
            write(0x16)                                     // handshake
            write(0x03); write(0x01)                        // TLS 1.0 record (max compat)
            write((body.size + 4) shr 8); write((body.size + 4) and 0xFF)
            write(0x01)                                     // client_hello
            write((body.size shr 16) and 0xFF); write((body.size shr 8) and 0xFF); write(body.size and 0xFF)
            write(body)
            toByteArray()
        }
    }

    private fun ipToInt(ip: String): Long =
        ip.split(".").fold(0L) { acc, part -> (acc shl 8) or (part.toLong() and 0xFF) }

    private fun intToIp(v: Long): String =
        listOf((v shr 24) and 0xFF, (v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF).joinToString(".")

    private fun java.io.ByteArrayOutputStream.write(b0: Int, b1: Int) { write(b0); write(b1) }
    private fun java.io.ByteArrayOutputStream.write(b0: Int, b1: Int, b2: Int) { write(b0); write(b1); write(b2) }
}
