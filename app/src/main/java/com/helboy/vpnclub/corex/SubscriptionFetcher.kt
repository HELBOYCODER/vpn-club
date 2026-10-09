package com.helboy.vpnclub.corex

import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * واکشی همزمان چند لینک اشتراک + بازکردن محتوایشان (base64 یا لیست متنی).
 */
class SubscriptionFetcher(
    private val timeoutMs: Int = 8_000,
    private val maxPerSource: Int = 400
) {

    private val pool = Executors.newFixedThreadPool(6)

    fun fetchAll(urls: List<String>): List<String> {
        val futures: List<Future<List<String>>> = urls.map { u -> pool.submit<List<String>> { fetchOne(u) } }
        val out = mutableListOf<String>()
        futures.forEach { f ->
            try { out += f.get(timeoutMs + 4_000L, TimeUnit.MILLISECONDS) } catch (_: Exception) {}
        }
        return out.distinct()
    }

    internal fun fetchOne(url: String): List<String> {
        val body = try { httpGet(url) } catch (_: Exception) { return emptyList() }
        if (body.isBlank()) return emptyList()
        val text = if (isLikelyBase64(body)) decodeBase64(body) ?: body else body
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length < 2_048 && looksLikeConfig(it) }
            .take(maxPerSource)
            .toList()
    }

    internal fun looksLikeConfig(line: String): Boolean =
        line.startsWith("vless://") || line.startsWith("vmess://") ||
        line.startsWith("trojan://") || line.startsWith("ss://") ||
        line.startsWith("hysteria2://") || line.startsWith("hy2://") ||
        line.startsWith("socks://") || line.startsWith("socks5://")

    /** اگر متن، دنباله‌ای از کاراکترهای base64 بدون فاصله باشد (لینک اشتراک کدشده). */
    private fun isLikelyBase64(s: String): Boolean {
        val trimmed = s.take(256).replace("\\s".toRegex(), "")
        return trimmed.length > 64 && trimmed.matches("^[A-Za-z0-9+/=_-]+$".toRegex())
    }

    private fun decodeBase64(s: String): String? = try {
        val normalized = s.trim().replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        String(Base64.getDecoder().decode(padded))
    } catch (_: Exception) { null }

    private fun httpGet(url: String): String {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "VPNClub/1.0 (Android)")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
