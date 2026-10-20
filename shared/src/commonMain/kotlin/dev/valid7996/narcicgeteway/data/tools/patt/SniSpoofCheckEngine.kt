package dev.valid7996.narcicgeteway.data.tools.patt

import dev.valid7996.narcicgeteway.platform.httpGetText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// ─── Configuration ───────────────────────────────────────────────────────────

val kDefaultSniTargets = """
hcaptcha.com
www.sciencedirect.com
auth.vercel.com
chess.com
unpkg.com
static.cloudflareinsights.com
www.speedtest.net
""".trimIndent()

val kDefaultSniPorts = listOf(443)
val kAllSniPorts = listOf(443, 2053, 2083, 2087, 2096, 8443)

private val PUBLIC_IP_API_URLS = listOf(
    "http://chabokan.net/ip/",
    "https://api.ipify.org",
    "https://icanhazip.com",
    "https://cloudflare.com/cdn-cgi/trace",
)

data class SniSpoofCheckConfig(
    val ports: List<Int> = kDefaultSniPorts,
    val timeout: Int = 3,
    val retries: Int = 2,
    val concurrency: Int = 15,
    val enableIpCheck: Boolean = true,
    val manualIp: String? = null,
)

// ─── Result types ────────────────────────────────────────────────────────────

enum class SniResultStatus {
    OK,
    FAIL,
    FILTERED,
    ERROR,
}

data class SniPortResult(
    val port: Int,
    val isOpen: Boolean,
    val tlsSuccess: Boolean = false,
    val httpStatus: Int? = null,
    val latencyMs: Int = 0,
)

data class SniIpCheckResult(
    val matched: Boolean,
    val detectedIp: String? = null,
    val tlsSuccess: Boolean = false,
)

data class SniIpResult(
    val target: String,
    val ip: String,
    val status: SniResultStatus,
    val portResults: List<SniPortResult> = emptyList(),
    val ipCheckResult: SniIpCheckResult? = null,
    val errorMessage: String? = null,
) {
    val openPortCount: Int get() = portResults.count { it.isOpen }
    val tlsPortCount: Int get() = portResults.count { it.tlsSuccess }

    fun toDisplayString(): String {
        val tag = "[${status.name}]"
        if (status == SniResultStatus.ERROR) {
            return "$tag $target (${errorMessage ?: "Could not resolve"})"
        }
        if (status == SniResultStatus.FILTERED) {
            return "$tag $target -> $ip (Blocked/Internal IP)"
        }

        val portStr = portResults.joinToString(" ") { p ->
            val flag = when {
                p.tlsSuccess -> "✔(TLS+HTTP${p.httpStatus?.let { " $it" } ?: ""})"
                p.isOpen -> "⚠(TCP)"
                else -> "✖"
            }
            "${p.port}$flag"
        }

        var line = "$tag $target -> $ip -> $portStr"
        if (ipCheckResult != null) {
            line += if (ipCheckResult.matched) {
                " IP✔"
            } else if (ipCheckResult.detectedIp != null) {
                " IP✖(${ipCheckResult.detectedIp})"
            } else {
                " IP✖"
            }
        }
        return line
    }
}

data class SniSpoofScanProgress(
    val latestResult: SniIpResult? = null,
    val completedTargets: Int = 0,
    val totalTargets: Int = 0,
    val allResults: List<SniIpResult> = emptyList(),
) {
    val progress: Float get() = if (totalTargets > 0) completedTargets.toFloat() / totalTargets else 0f
    val okCount: Int get() = allResults.count { it.status == SniResultStatus.OK }
    val failCount: Int get() = allResults.count { it.status == SniResultStatus.FAIL }
    val filteredCount: Int get() = allResults.count { it.status == SniResultStatus.FILTERED }
    val errorCount: Int get() = allResults.count { it.status == SniResultStatus.ERROR }
}

// ─── Service ─────────────────────────────────────────────────────────────────

class SniSpoofCheckService(
    var config: SniSpoofCheckConfig = SniSpoofCheckConfig(),
) {
    @Volatile
    private var isCancelled = false

    fun cancel() {
        isCancelled = true
    }

    companion object {
        private val IPV4_REGEX = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")

        fun parseTargets(input: String): List<String> {
            return input.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
        }

        fun isIpAddress(target: String): Boolean = IPV4_REGEX.matches(target)

        suspend fun resolveTarget(target: String): List<String> = withContext(Dispatchers.IO) {
            if (isIpAddress(target)) return@withContext listOf(target)
            try {
                val addresses = InetAddress.getAllByName(target)
                addresses.mapNotNull { addr ->
                    val hostAddr = addr.hostAddress ?: return@mapNotNull null
                    if (IPV4_REGEX.matches(hostAddr)) hostAddr else null
                }.distinct()
            } catch (_: Exception) {
                emptyList()
            }
        }

        suspend fun getUserPublicIp(): String? = withContext(Dispatchers.IO) {
            for (url in PUBLIC_IP_API_URLS) {
                try {
                    val body = httpGetText(
                        url = url,
                        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                        connectTimeoutMs = 3_500,
                        readTimeoutMs = 3_500,
                    ).trim()

                    val match = Regex("""(?m)^(?:ip=)?\s*(\d{1,3}(?:\.\d{1,3}){3})\b""").find(body)
                        ?: Regex(""""ip"\s*:\s*"([^"]+)"""").find(body)
                    if (match != null) {
                        val ip = match.groupValues[1]
                        if (IPV4_REGEX.matches(ip)) return@withContext ip
                    }
                    if (IPV4_REGEX.matches(body)) return@withContext body
                } catch (_: Exception) {
                    // Try next fallback endpoint
                }
            }
            null
        }

        fun generateReport(
            results: List<SniIpResult>,
            config: SniSpoofCheckConfig,
        ): String {
            val sb = StringBuilder()
            sb.appendLine("=== SNI Spoof Check Report ===")
            sb.appendLine("Ports: ${config.ports.joinToString(",")}")
            sb.appendLine("Timeout: ${config.timeout}s | Retries: ${config.retries}")
            sb.appendLine("Total Checked: ${results.size}")
            sb.appendLine("---------------------------------------------------")

            val ok = results.filter { it.status == SniResultStatus.OK }
            val fail = results.filter { it.status == SniResultStatus.FAIL }
            val error = results.filter { it.status == SniResultStatus.ERROR }
            val filtered = results.filter { it.status == SniResultStatus.FILTERED }

            if (ok.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("=== OK (Clean / Open ports) [${ok.size}] ===")
                ok.forEach { sb.appendLine(it.toDisplayString()) }
            }

            if (fail.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("=== FAIL (all ports closed) [${fail.size}] ===")
                fail.forEach { sb.appendLine(it.toDisplayString()) }
            }

            if (error.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("=== RESOLVE FAILED [${error.size}] ===")
                error.forEach { sb.appendLine(it.toDisplayString()) }
            }

            if (filtered.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("=== FILTERED (Blocked/IP 10.x) [${filtered.size}] ===")
                filtered.forEach { sb.appendLine(it.toDisplayString()) }
            }

            sb.appendLine("---------------------------------------------------")
            sb.appendLine("Scan finished.")
            return sb.toString()
        }
    }

    suspend fun checkPort(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        val timeoutMs = (config.timeout.coerceIn(1, 10)) * 1000
        val retries = config.retries.coerceIn(1, 3)
        for (attempt in 0 until retries) {
            if (isCancelled) return@withContext false
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                try { socket.close() } catch (_: Exception) {}
                return@withContext true
            } catch (_: Exception) {
                // retry
            }
        }
        false
    }

    suspend fun checkTlsHttps(
        targetDomain: String,
        ip: String,
        port: Int,
    ): SniPortResult = withContext(Dispatchers.IO) {
        val timeoutMs = (config.timeout.coerceIn(2, 10)) * 1000
        val retries = config.retries.coerceIn(1, 3)
        for (attempt in 0 until retries) {
            if (isCancelled) return@withContext SniPortResult(port = port, isOpen = false)
            var rawSocket: Socket? = null
            var sslSocket: SSLSocket? = null
            val start = System.currentTimeMillis()
            try {
                rawSocket = Socket()
                rawSocket.connect(InetSocketAddress(ip, port), timeoutMs)
                rawSocket.soTimeout = timeoutMs

                if (port == 80) {
                    val latency = (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
                    try { rawSocket.close() } catch (_: Exception) {}
                    return@withContext SniPortResult(
                        port = port,
                        isOpen = true,
                        tlsSuccess = false,
                        httpStatus = 200,
                        latencyMs = latency,
                    )
                }

                val sslContext = SSLContext.getInstance("TLS")
                val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                sslContext.init(null, trustAll, SecureRandom())

                sslSocket = sslContext.socketFactory.createSocket(rawSocket, targetDomain, port, true) as SSLSocket
                val params = sslSocket.sslParameters
                if (!isIpAddress(targetDomain)) {
                    params.serverNames = listOf(SNIHostName(targetDomain))
                }
                sslSocket.sslParameters = params
                sslSocket.soTimeout = timeoutMs

                sslSocket.startHandshake()

                val out = sslSocket.outputStream
                val req = "GET / HTTP/1.1\r\nHost: $targetDomain\r\nUser-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)\r\nConnection: close\r\n\r\n"
                out.write(req.toByteArray(Charsets.UTF_8))
                out.flush()

                val reader = BufferedReader(InputStreamReader(sslSocket.inputStream))
                val firstLine = reader.readLine()
                val latency = (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)

                val statusCode = firstLine?.let { line ->
                    Regex("""HTTP/[0-9.]+\s+([0-9]{3})""").find(line)?.groupValues?.get(1)?.toIntOrNull()
                }

                return@withContext SniPortResult(
                    port = port,
                    isOpen = true,
                    tlsSuccess = true,
                    httpStatus = statusCode ?: 200,
                    latencyMs = latency,
                )
            } catch (e: Exception) {
                val latency = (System.currentTimeMillis() - start).toInt()
                val tcpConnected = rawSocket?.isConnected == true
                if (attempt == retries - 1) {
                    return@withContext SniPortResult(
                        port = port,
                        isOpen = tcpConnected,
                        tlsSuccess = false,
                        httpStatus = null,
                        latencyMs = latency,
                    )
                }
            } finally {
                try { sslSocket?.close() } catch (_: Exception) {}
                try { rawSocket?.close() } catch (_: Exception) {}
            }
        }
        SniPortResult(port = port, isOpen = false)
    }

    suspend fun checkIp(domain: String, ip: String, userPublicIp: String): SniIpCheckResult =
        withContext(Dispatchers.IO) {
            var rawSocket: Socket? = null
            var sslSocket: SSLSocket? = null
            try {
                val timeoutMs = (config.timeout.coerceIn(2, 8)) * 1000
                rawSocket = Socket()
                rawSocket.connect(InetSocketAddress(ip, 443), timeoutMs)
                rawSocket.soTimeout = timeoutMs

                val sslContext = SSLContext.getInstance("TLS")
                val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                sslContext.init(null, trustAll, SecureRandom())

                sslSocket = sslContext.socketFactory.createSocket(rawSocket, domain, 443, true) as SSLSocket
                val params = sslSocket.sslParameters
                params.serverNames = listOf(SNIHostName(domain))
                sslSocket.sslParameters = params
                sslSocket.soTimeout = timeoutMs

                sslSocket.startHandshake()

                val out = sslSocket.outputStream
                val req = "GET /cdn-cgi/trace HTTP/1.1\r\nHost: $domain\r\nConnection: close\r\n\r\n"
                out.write(req.toByteArray(Charsets.UTF_8))
                out.flush()

                val reader = BufferedReader(InputStreamReader(sslSocket.inputStream))
                val sb = StringBuilder()
                var line = reader.readLine()
                var linesRead = 0
                while (line != null && linesRead < 40) {
                    sb.append(line).append("\n")
                    line = reader.readLine()
                    linesRead++
                }

                val responseStr = sb.toString()
                val ipMatch = Regex("""(?m)^ip=(.+)$""").find(responseStr)
                if (ipMatch == null) {
                    return@withContext SniIpCheckResult(matched = false, tlsSuccess = true)
                }

                val detectedIp = ipMatch.groupValues[1].trim()
                SniIpCheckResult(
                    matched = detectedIp.equals(userPublicIp.trim(), ignoreCase = true),
                    detectedIp = detectedIp,
                    tlsSuccess = true,
                )
            } catch (_: Exception) {
                SniIpCheckResult(matched = false, tlsSuccess = false)
            } finally {
                try { sslSocket?.close() } catch (_: Exception) {}
                try { rawSocket?.close() } catch (_: Exception) {}
            }
        }

    suspend fun scanTargets(
        targets: List<String>,
        userPublicIp: String?,
        onTargetResult: (List<SniIpResult>, completed: Int, total: Int) -> Unit,
    ) = coroutineScope {
        isCancelled = false
        if (targets.isEmpty()) return@coroutineScope

        var completedCount = 0
        val sem = Semaphore(config.concurrency.coerceIn(1, 30))

        val jobs = targets.map { target ->
            launch(Dispatchers.IO) {
                sem.withPermit {
                    if (isCancelled) return@withPermit
                    val results = processTarget(target, userPublicIp)
                    if (!isCancelled) {
                        synchronized(this@SniSpoofCheckService) {
                            completedCount++
                            onTargetResult(results, completedCount, targets.size)
                        }
                    }
                }
            }
        }
        jobs.forEach { it.join() }
    }

    private suspend fun processTarget(
        target: String,
        userPublicIp: String?,
    ): List<SniIpResult> = coroutineScope {
        val ips = resolveTarget(target)
        if (ips.isEmpty()) {
            return@coroutineScope listOf(
                SniIpResult(
                    target = target,
                    ip = "",
                    status = SniResultStatus.ERROR,
                    errorMessage = "عدم پاسخ DNS / عدم ترجمه دامنه",
                ),
            )
        }

        val results = mutableListOf<SniIpResult>()
        for (ip in ips) {
            if (isCancelled) break

            if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("127.")) {
                results.add(
                    SniIpResult(
                        target = target,
                        ip = ip,
                        status = SniResultStatus.FILTERED,
                    ),
                )
                continue
            }

            // Check all ports for this IP concurrently with TLS + HTTPS validation
            val portDeferreds = config.ports.map { port ->
                async(Dispatchers.IO) {
                    checkTlsHttps(target, ip, port)
                }
            }
            val portResults = portDeferreds.awaitAll()
            val tlsOkCount = portResults.count { it.tlsSuccess || (it.isOpen && it.port == 80) }
            val openCount = portResults.count { it.isOpen }

            if (tlsOkCount > 0) {
                var ipCheckResult: SniIpCheckResult? = null
                if (config.enableIpCheck && userPublicIp != null && !isIpAddress(target) && portResults.any { it.port == 443 && it.tlsSuccess }) {
                    ipCheckResult = checkIp(target, ip, userPublicIp)
                }

                results.add(
                    SniIpResult(
                        target = target,
                        ip = ip,
                        status = SniResultStatus.OK,
                        portResults = portResults,
                        ipCheckResult = ipCheckResult,
                    ),
                )
            } else if (openCount > 0) {
                results.add(
                    SniIpResult(
                        target = target,
                        ip = ip,
                        status = SniResultStatus.FAIL,
                        portResults = portResults,
                        errorMessage = "پورت باز است اما هندشیک TLS یا پاسخ HTTPS ناموفق بود (احتمال فیلتر SNI)",
                    ),
                )
            } else {
                results.add(
                    SniIpResult(
                        target = target,
                        ip = ip,
                        status = SniResultStatus.FAIL,
                        portResults = portResults,
                        errorMessage = "عدم اتصال / همه پورت‌ها بسته",
                    ),
                )
            }
        }
        results
    }
}

object SniSpoofCheckEngine {
    fun parseTargets(input: String): List<String> = SniSpoofCheckService.parseTargets(input)

    suspend fun getUserPublicIp(): String? = SniSpoofCheckService.getUserPublicIp()

    fun generateReport(
        results: List<SniIpResult>,
        userPublicIp: String? = null,
        config: SniSpoofCheckConfig = SniSpoofCheckConfig(),
    ): String = SniSpoofCheckService.generateReport(results, config)

    suspend fun runScan(
        targetsText: String,
        config: SniSpoofCheckConfig = SniSpoofCheckConfig(),
        onPublicIpDetected: (String) -> Unit = {},
        onProgress: (current: Int, total: Int, status: String) -> Unit = { _, _, _ -> },
        onResult: (SniIpResult) -> Unit = {},
    ) {
        val targets = parseTargets(targetsText)
        if (targets.isEmpty()) return
        val service = SniSpoofCheckService(config)

        var publicIp: String? = null
        if (config.enableIpCheck) {
            publicIp = config.manualIp ?: getUserPublicIp()
            if (publicIp != null) onPublicIpDetected(publicIp)
        }

        onProgress(0, targets.size, "در حال بررسی دامنه‌ها...")

        service.scanTargets(targets, publicIp) { targetResults, completed, total ->
            for (res in targetResults) {
                onResult(res)
            }
            onProgress(completed, total, "بررسی $completed از $total دامنه...")
        }
    }
}
