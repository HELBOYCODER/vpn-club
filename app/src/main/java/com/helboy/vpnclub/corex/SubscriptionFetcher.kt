package com.helboy.vpnclub.corex

import java.util.Base64
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Collect completed feeds within one global deadline; a stalled source cannot serialize every scan. */
class SubscriptionFetcher(private val timeoutMs: Int = 8_000, private val maxPerSource: Int = 1_500) {
    fun fetchAll(urls: List<String>): List<String> {
        val pool = Executors.newFixedThreadPool(8)
        val completion = ExecutorCompletionService<List<String>>(pool)
        val futures = urls.distinct().map { url -> completion.submit(Callable { fetchOne(url) }) }
        val out = mutableListOf<String>()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(24)
        try {
            repeat(futures.size) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0 || Thread.currentThread().isInterrupted) return out.distinct()
                val result = completion.poll(remaining, TimeUnit.NANOSECONDS) ?: return out.distinct()
                out += runCatching { result.get() }.getOrDefault(emptyList())
            }
            return out.distinct()
        } finally { futures.forEach { it.cancel(true) }; pool.shutdownNow() }
    }

    internal fun fetchOne(url: String): List<String> {
        val body = try { NetHttp.getFeedText(url, timeoutMs) } catch (_: Exception) {
            val match = Regex("https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/(?:refs/heads/)?([^/]+)/(.*)").matchEntire(url)
                ?: return emptyList()
            val (owner, repo, ref, path) = match.destructured
            try { NetHttp.getFeedText("https://cdn.jsdelivr.net/gh/$owner/$repo@$ref/$path", timeoutMs) }
            catch (_: Exception) { return emptyList() }
        }
        val text = if (isLikelyBase64(body)) decodeBase64(body) ?: body else body
        return text.lineSequence().map { it.trim() }.filter {
            it.length <= 16_384 && looksLikeConfig(it)
        }.take(maxPerSource).toList()
    }
    internal fun looksLikeConfig(line: String): Boolean = listOf("vless://", "vmess://", "trojan://", "ss://", "hysteria2://", "hy2://", "socks://", "socks5://", "wireguard://", "wg://").any(line::startsWith)
    private fun isLikelyBase64(value: String) = value.take(256).replace("\\s".toRegex(), "").let {
        it.length > 64 && it.matches("^[A-Za-z0-9+/=_-]+$".toRegex())
    }
    private fun decodeBase64(value: String): String? = runCatching {
        val normalized = value.replace("\\s".toRegex(), "").replace('-', '+').replace('_', '/')
        String(Base64.getDecoder().decode(normalized + "=".repeat((4 - normalized.length % 4) % 4)))
    }.getOrNull()
}
