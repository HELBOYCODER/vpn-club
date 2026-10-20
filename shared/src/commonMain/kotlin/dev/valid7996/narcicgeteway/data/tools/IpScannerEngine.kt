package dev.valid7996.narcicgeteway.data.tools

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

data class ScanResult(
    val ip: String,
    val latency: Int,
    val status: Status,
    val domain: String? = null,
    val certDomain: String? = null,
    val cdnType: String? = null,
)

enum class Status {
    GREEN,
    YELLOW,
    RED,
}

object CidrGenerator {
    fun randomIps(cidr: String, count: Int): List<String> {
        val parts = cidr.split("/")
        if (parts.size != 2) return emptyList()

        val ip = parts[0].trim()
        val prefix = parts[1].trim().toIntOrNull() ?: return emptyList()

        val ipParts = ip.split(".").mapNotNull { it.trim().toIntOrNull() }
        if (ipParts.size != 4) return emptyList()

        val base =
            (ipParts[0] shl 24) or
            (ipParts[1] shl 16) or
            (ipParts[2] shl 8) or
            ipParts[3]

        val hostBits = (32 - prefix).coerceIn(1, 30)
        val maxHosts = 1 shl hostBits

        val list = mutableListOf<String>()
        val actualCount = minOf(count, maxHosts)

        repeat(actualCount) {
            val host = Random.nextInt(maxHosts)
            val value = base + host

            val a = (value ushr 24) and 0xff
            val b = (value ushr 16) and 0xff
            val c = (value ushr 8) and 0xff
            val d = value and 0xff

            list.add("$a.$b.$c.$d")
        }

        return list
    }
}

object PingEngine {
    fun ping(ip: String, port: Int = 443): Int {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "/system/bin/ping",
                    "-c",
                    "1",
                    "-W",
                    "1",
                    ip,
                ),
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()

            val regex = Regex("""time=([0-9.]+)""")
            val match = regex.find(output)

            if (match != null) {
                match.groupValues[1].toFloat().toInt()
            } else {
                // Fallback to TCP connect latency if ping binary returned non-zero (or non-Android)
                measureTcp(ip, port, 1000)
            }
        } catch (e: Exception) {
            // Fallback for desktop / environments without /system/bin/ping
            measureTcp(ip, port, 1000)
        }
    }

    fun test(ip: String, port: Int = 443): Int = ping(ip, port)

    private fun measureTcp(ip: String, port: Int, timeout: Int): Int {
        return try {
            val socket = Socket()
            val start = System.currentTimeMillis()
            socket.connect(InetSocketAddress(ip, port), timeout)
            val elapsed = (System.currentTimeMillis() - start).toInt()
            try { socket.close() } catch (_: Exception) {}
            if (elapsed > 0) elapsed else 1
        } catch (_: Exception) {
            999
        }
    }
}

object TlsEngine {
    data class TlsResult(
        val success: Boolean,
        val latencyMs: Int,
        val verifiedDomain: String?,
        val certSubject: String?,
    )

    fun testDirect(
        ip: String,
        host: String,
        port: Int = 443,
        timeout: Int = 2000,
    ): TlsResult {
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        val start = System.currentTimeMillis()
        try {
            rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(ip, port), timeout)
            rawSocket.soTimeout = timeout
            val tcpLatency = (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)

            if (port == 80) {
                try { rawSocket.close() } catch (_: Exception) {}
                return TlsResult(
                    success = true,
                    latencyMs = tcpLatency,
                    verifiedDomain = host.ifBlank { null },
                    certSubject = null,
                )
            }

            val context = SSLContext.getInstance("TLS")
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            context.init(null, trustAll, SecureRandom())

            sslSocket = context.socketFactory.createSocket(
                rawSocket,
                host.ifBlank { ip },
                port,
                true,
            ) as SSLSocket

            val params = sslSocket.sslParameters
            if (host.isNotBlank()) {
                params.serverNames = listOf(SNIHostName(host))
            }
            sslSocket.sslParameters = params
            sslSocket.soTimeout = timeout

            sslSocket.startHandshake()
            val totalLatency = (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)

            var certCn: String? = null
            try {
                val cert = sslSocket.session.peerCertificates.firstOrNull() as? X509Certificate
                if (cert != null) {
                    val dn = cert.subjectX500Principal.name
                    val cnMatch = Regex("""CN=([^,]+)""", RegexOption.IGNORE_CASE).find(dn)
                    val extractedCn = cnMatch?.groupValues?.get(1)?.trim()

                    val sans = cert.subjectAlternativeNames
                        ?.filter { it.size >= 2 && (it[0] as? Number)?.toInt() == 2 }
                        ?.mapNotNull { it[1] as? String }
                        ?: emptyList()

                    certCn = when {
                        !extractedCn.isNullOrBlank() -> extractedCn
                        sans.isNotEmpty() -> sans.first()
                        else -> null
                    }
                }
            } catch (_: Exception) {}

            return TlsResult(
                success = true,
                latencyMs = totalLatency,
                verifiedDomain = host.ifBlank { certCn },
                certSubject = certCn,
            )
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - start).toInt()
            return TlsResult(
                success = false,
                latencyMs = if (elapsed > 0) elapsed else 999,
                verifiedDomain = null,
                certSubject = null,
            )
        } finally {
            try { sslSocket?.close() } catch (_: Exception) {}
            try { rawSocket?.close() } catch (_: Exception) {}
        }
    }

    fun test(
        ip: String,
        host: String,
        port: Int = 443,
        timeout: Int = 3000,
    ): Boolean = testDirect(ip, host, port, timeout).success
}

object ScannerEngine {

    @Volatile
    private var stopRequested = false

    val cdnTypes = listOf(
        "Cloudflare",
        "Akamai",
        "Fastly",
        "Bunny",
        "EdgeCenter",
        "Gcore",
        "CDN77",
        "Edgio",
        "Imperva",
        "Azure Front Door",
        "Netlify",
        "Google Cloud CDN",
        "Vercel",
    )

    fun getDefaultRangesForCdn(cdnType: String): List<String> {
        return when (cdnType) {
            "Cloudflare" -> listOf(
                "104.16.0.0/13",
                "104.24.0.0/14",
                "172.64.0.0/13",
                "162.158.0.0/15",
                "198.41.128.0/17",
                "188.114.96.0/20",
                "141.101.64.0/18",
                "108.162.192.0/18",
            )
            "Akamai" -> listOf(
                "23.32.0.0/11",
                "23.192.0.0/11",
                "104.64.0.0/10",
                "184.84.0.0/14",
                "2.16.0.0/13",
                "96.6.0.0/15",
                "72.246.0.0/15",
            )
            "Fastly" -> listOf(
                "151.101.0.0/16",
                "199.232.0.0/16",
                "146.75.0.0/16",
                "167.82.0.0/17",
            )
            "Bunny" -> listOf(
                "89.187.160.0/20",
                "185.180.12.0/22",
                "169.150.200.0/22",
                "185.228.168.0/22",
                "169.150.220.0/22",
            )
            "EdgeCenter" -> listOf(
                "91.240.84.0/22",
                "91.240.88.0/22",
                "185.194.216.0/22",
                "185.194.220.0/22",
            )
            "Gcore" -> listOf(
                "92.223.84.0/22",
                "92.38.168.0/22",
                "146.185.240.0/21",
                "5.188.224.0/21",
            )
            "CDN77" -> listOf(
                "84.17.32.0/19",
                "89.187.160.0/20",
                "185.229.224.0/22",
                "195.181.160.0/19",
            )
            "Edgio" -> listOf(
                "192.229.128.0/17",
                "192.16.128.0/17",
                "93.184.216.0/22",
                "152.195.0.0/16",
            )
            "Imperva" -> listOf(
                "199.83.128.0/21",
                "198.143.32.0/19",
                "149.126.72.0/21",
                "103.28.248.0/22",
            )
            "Azure Front Door" -> listOf(
                "13.107.246.0/24",
                "13.107.213.0/24",
                "204.79.197.0/24",
                "13.107.6.0/24",
            )
            "Netlify" -> listOf(
                "75.2.60.0/24",
                "99.83.231.0/24",
                "100.24.0.0/16",
            )
            "Google Cloud CDN" -> listOf(
                "34.96.0.0/16",
                "34.120.0.0/16",
                "35.190.0.0/16",
                "130.211.0.0/16",
            )
            "Vercel" -> listOf(
                "76.76.21.0/24",
                "76.223.126.0/24",
            )
            else -> emptyList()
        }
    }

    fun getDefaultDomainForCdn(cdnType: String): String {
        return when (cdnType) {
            "Cloudflare" -> "speed.cloudflare.com"
            "Akamai" -> "www.akamai.com"
            "Fastly" -> "www.fastly.com"
            "Bunny" -> "bunny.net"
            "EdgeCenter" -> "edgecenter.ru"
            "Gcore" -> "gcore.com"
            "CDN77" -> "www.cdn77.com"
            "Edgio" -> "edg.io"
            "Imperva" -> "www.imperva.com"
            "Azure Front Door" -> "azureedge.net"
            "Netlify" -> "www.netlify.com"
            "Google Cloud CDN" -> "cloud.google.com"
            "Vercel" -> "vercel.com"
            else -> "speed.cloudflare.com"
        }
    }

    fun stop() {
        stopRequested = true
    }

    suspend fun scan(
        ranges: List<String>,
        domain: String = "",
        port: Int = 443,
        ipPerRange: Int = 150,
        threadCount: Int = 6,
        checkCert: Boolean = false,
        cdnType: String = "Cloudflare",
        onResult: (ScanResult) -> Unit,
    ) = coroutineScope {
        stopRequested = false
        val semaphore = Semaphore(threadCount.coerceIn(2, 16))
        val jobs = mutableListOf<Job>()

        val effectiveHost = if (domain.isNotBlank()) {
            domain.trim()
        } else {
            getDefaultDomainForCdn(cdnType)
        }

        for (range in ranges) {
            if (stopRequested) break

            val ips = if (range.contains("/")) {
                CidrGenerator.randomIps(range, ipPerRange)
            } else {
                listOf(range.trim())
            }

            for (ip in ips) {
                if (stopRequested) break

                val job = launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        if (stopRequested) return@withPermit

                        // Accurate TLS+TCP direct verification
                        val tlsRes = TlsEngine.testDirect(
                            ip = ip,
                            host = effectiveHost,
                            port = port,
                            timeout = 2500,
                        )
                        if (stopRequested) return@withPermit

                        val (status, latency) = if (tlsRes.success) {
                            val l = tlsRes.latencyMs
                            val s = when {
                                l <= 220 -> Status.GREEN
                                l <= 450 -> Status.YELLOW
                                else -> Status.RED
                            }
                            Pair(s, l)
                        } else {
                            // If TLS/TCP failed
                            Pair(Status.RED, tlsRes.latencyMs)
                        }

                        onResult(
                            ScanResult(
                                ip = ip,
                                latency = latency,
                                status = status,
                                domain = if (tlsRes.success) (tlsRes.verifiedDomain ?: effectiveHost) else null,
                                certDomain = tlsRes.certSubject,
                                cdnType = cdnType,
                            ),
                        )

                        delay(20)
                    }
                }
                jobs += job
            }
        }

        jobs.joinAll()
    }
}
