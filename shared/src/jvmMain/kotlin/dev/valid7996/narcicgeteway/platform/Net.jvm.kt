package dev.valid7996.narcicgeteway.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate

private val permissiveTrustManager = arrayOf<TrustManager>(
    object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
)

private val permissiveSslSocketFactory: SSLSocketFactory by lazy {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, permissiveTrustManager, SecureRandom())
    sslContext.socketFactory
}

private val permissiveHostnameVerifier = HostnameVerifier { _, _ -> true }

internal fun applySocksSslIfNeeded(connection: HttpURLConnection, socksPort: Int?) {
    if (connection is HttpsURLConnection && socksPort != null) {
        connection.sslSocketFactory = permissiveSslSocketFactory
        connection.hostnameVerifier = permissiveHostnameVerifier
    }
}

internal actual suspend fun httpGetViaSocks(
    url: String,
    socksPort: Int?,
    userAgent: String,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    closeConnection: Boolean,
): String = withContext(Dispatchers.IO) {
    val connection = if (socksPort != null) {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", socksPort))
        URL(url).openConnection(proxy) as HttpURLConnection
    } else {
        URL(url).openConnection() as HttpURLConnection
    }
    applySocksSslIfNeeded(connection, socksPort)
    connection.apply {
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", userAgent)
        if (closeConnection) setRequestProperty("Connection", "close")
    }
    try {
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${connection.responseCode}")
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    } catch (e: NullPointerException) {
        throw platformHttpFailure(e)
    } finally {
        connection.disconnectQuietly()
    }
}

internal actual suspend fun httpTimedTransfer(
    url: String,
    socksPort: Int?,
    upload: Boolean,
    uploadBytes: Int,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
): HttpTiming = withContext(Dispatchers.IO) {
    val connection = if (socksPort != null) {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", socksPort))
        URL(url).openConnection(proxy) as HttpURLConnection
    } else {
        URL(url).openConnection() as HttpURLConnection
    }
    applySocksSslIfNeeded(connection, socksPort)
    connection.apply {
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "NarcicGeteway-SpeedTest")
        setRequestProperty("Accept-Encoding", "identity")
    }
    var bytes = 0L
    val t0 = System.nanoTime()
    try {
        val buf = ByteArray(64 * 1024)
        if (upload) {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(uploadBytes)
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.outputStream.use { os ->
                var remaining = uploadBytes
                while (remaining > 0) {
                    val n = minOf(buf.size, remaining)
                    os.write(buf, 0, n)
                    os.flush()
                    remaining -= n
                    bytes += n
                }
            }
        }
        val code = connection.responseCode
        val ttfb = System.nanoTime() - t0
        if (code !in 200..299) throw IllegalStateException("HTTP $code")

        connection.inputStream.use { ins ->
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                if (!upload) bytes += n
            }
        }
        val wall = System.nanoTime() - t0
        val server = parseServerTimingMillis(connection.getHeaderField("server-timing"))
        HttpTiming(bytes = bytes, wallNanos = wall, ttfbNanos = ttfb, serverMillis = server)
    } catch (e: NullPointerException) {
        throw platformHttpFailure(e)
    } finally {
        connection.disconnectQuietly()
    }
}

private fun parseServerTimingMillis(header: String?): Double {
    if (header.isNullOrBlank()) return 0.0
    val idx = header.indexOf("dur=", ignoreCase = true)
    if (idx < 0) return 0.0
    val tail = header.substring(idx + 4)
    val num = tail.takeWhile { it.isDigit() || it == '.' }
    return num.toDoubleOrNull() ?: 0.0
}

internal actual suspend fun resolveHostAddress(host: String): String? = withContext(Dispatchers.IO) {
    runCatching { InetAddress.getByName(host).hostAddress }.getOrNull()
}

internal actual suspend fun tcpConnectMillis(host: String, port: Int, timeoutMs: Int): Long =
    withContext(Dispatchers.IO) {
        var socket: Socket? = null
        val start = System.currentTimeMillis()
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            -1L
        } finally {
            runCatching { socket?.takeIf { !it.isClosed }?.close() }
        }
    }

internal actual suspend fun httpStreamTransfer(
    url: String,
    socksPort: Int?,
    upload: Boolean,
    uploadBytes: Long,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    onChunk: (deltaBytes: Long) -> Boolean,
): HttpTiming = withContext(Dispatchers.IO) {
    val connection = if (socksPort != null) {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", socksPort))
        URL(url).openConnection(proxy) as HttpURLConnection
    } else {
        URL(url).openConnection() as HttpURLConnection
    }
    applySocksSslIfNeeded(connection, socksPort)
    connection.apply {
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "NarcicGeteway-SpeedTest")
        setRequestProperty("Accept-Encoding", "identity")
    }

    val cancelHandle = coroutineContext[Job]?.invokeOnCompletion {
        connection.disconnectQuietly()
    }
    var bytes = 0L
    val t0 = System.nanoTime()
    try {
        val buf = ByteArray(CHUNK)
        if (upload) {
            connection.requestMethod = "POST"
            connection.doOutput = true

            connection.setFixedLengthStreamingMode(uploadBytes)
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.outputStream.use { os ->
                while (bytes < uploadBytes) {
                    val n = minOf(buf.size.toLong(), uploadBytes - bytes).toInt()
                    os.write(buf, 0, n)
                    bytes += n

                    if (!onChunk(n.toLong())) break
                }
                os.flush()
            }
        }

        val code = connection.responseCode
        val ttfb = System.nanoTime() - t0
        if (code !in 200..299) throw IllegalStateException("HTTP $code")
        connection.inputStream.use { ins ->
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                if (!upload) {
                    bytes += n
                    if (!onChunk(n.toLong())) break
                }
            }
        }
        val wall = System.nanoTime() - t0
        val server = parseServerTimingMillis(connection.getHeaderField("server-timing"))
        HttpTiming(bytes = bytes, wallNanos = wall, ttfbNanos = ttfb, serverMillis = server)
    } catch (e: NullPointerException) {
        ensureActive()
        throw platformHttpFailure(e)
    } finally {
        cancelHandle?.dispose()
        connection.disconnectQuietly()
    }
}

private const val CHUNK = 64 * 1024
