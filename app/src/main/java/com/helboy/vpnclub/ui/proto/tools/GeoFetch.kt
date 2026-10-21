package com.helboy.vpnclub.ui.proto.tools

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Blocking HTTP for the geo files; call from Dispatchers.IO only. [check] is called between chunks and may throw
 * (the page passes `ensureActive`) so a cancel stops the transfer quickly.
 */
object GeoFetch {
    private const val UA = "HellboyVPN"
    private const val MAX_BYTES = 200L * 1024 * 1024

    /** Size and date of the file behind [url] without downloading it. */
    fun head(url: String): RemoteInfo {
        val c = open(url, "HEAD")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw GeoHttpException(code)
            val size = c.contentLengthLong.takeIf { it > 0 }
            val modified = c.lastModified.takeIf { it > 0 }
            return RemoteInfo(size, modified)
        } finally {
            c.disconnect()
        }
    }

    fun download(url: String, check: () -> Unit, onProgress: (done: Long, total: Long?) -> Unit): ByteArray {
        val c = open(url, "GET")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw GeoHttpException(code)
            val total = c.contentLengthLong.takeIf { it > 0 }
            val out = ByteArrayOutputStream(total?.coerceAtMost(MAX_BYTES)?.toInt() ?: 1 shl 20)
            c.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    check()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (done > MAX_BYTES) throw GeoBadFileException("too large")
                    onProgress(done, total)
                }
            }
            if (total != null && out.size().toLong() != total) throw GeoBadFileException("truncated")
            return out.toByteArray()
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
        }
}
