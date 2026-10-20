package dev.valid7996.narcicgeteway.data.tools

import dev.valid7996.narcicgeteway.core.VpnManager
import dev.valid7996.narcicgeteway.data.net.PingService
import dev.valid7996.narcicgeteway.platform.httpGetResponse
import dev.valid7996.narcicgeteway.platform.httpGetText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class TelegramProxyItem(
    val id: String,
    val server: String,
    val port: Int,
    val secret: String,
    val pingMs: Long = -1L,
    val country: String = "🇩🇪",
    val title: String = "",
    val isTesting: Boolean = false,
) {
    val tgUri: String
        get() = "tg://proxy?server=$server&port=$port&secret=$secret"

    val webUri: String
        get() = "https://t.me/proxy?server=$server&port=$port&secret=$secret"
}

object TelegramProxyRepository {

    // Built-in vetted MTProto proxies with active TLS camouflage secrets from verified channels
    private val DEFAULT_PROXIES = listOf(
        TelegramProxyItem(
            id = "p1",
            server = "rade94.tidelark.cc",
            port = 443,
            secret = "ee214011ba04108a7b1836df3a5e918b3b7261646539342e746964656c61726b2e6363",
            country = "⚡",
            title = "Tidelark TLS Direct",
        ),
        TelegramProxyItem(
            id = "p2",
            server = "mimizo19.tidelark.cc",
            port = 443,
            secret = "ee6ce7aa9636179a5f67366aaab4702a266d696d697a6f31392e746964656c61726b2e6363",
            country = "⚡",
            title = "Mimizo Secure Edge",
        ),
        TelegramProxyItem(
            id = "p3",
            server = "support.infinityhalo.co.uk",
            port = 8443,
            secret = "eeNEgYdJvXrFGRMCIMJdCQ",
            country = "🇬🇧",
            title = "UK Infinity Camo",
        ),
        TelegramProxyItem(
            id = "p4",
            server = "toka21.mosswren.vip",
            port = 443,
            secret = "ee9267d78681726d5ff1bf826b750ba4ba746f6b6132312e6d6f73737772656e2e766970",
            country = "⚡",
            title = "Mosswren VIP Fast",
        ),
        TelegramProxyItem(
            id = "p5",
            server = "premium.speed-benz.co.uk",
            port = 8443,
            secret = "eeNEgYdJvXrFGRMCIMJdCQ",
            country = "🇬🇧",
            title = "SpeedBenz Premium",
        ),
        TelegramProxyItem(
            id = "p6",
            server = "ir.closcloud.co.uk",
            port = 8443,
            secret = "eeNEgYdJvXrFGRMCIMJdCQ",
            country = "🇬🇧",
            title = "ClosCloud Direct",
        ),
        TelegramProxyItem(
            id = "p7",
            server = "bo0ji.co.uk",
            port = 443,
            secret = "ddf0eeb0bd9adc4fd4a93994ee3b2a216b",
            country = "🇬🇧",
            title = "Booji Secure TLS",
        ),
        TelegramProxyItem(
            id = "p8",
            server = "mito38.mosswren.vip",
            port = 443,
            secret = "eea02849a55daccdc7f7a05691b7ef7cd16d69746f33382e6d6f73737772656e2e766970",
            country = "⚡",
            title = "Mito38 VIP Edge",
        ),
    )

    fun getInitialProxies(): List<TelegramProxyItem> = DEFAULT_PROXIES

    suspend fun pingAll(
        proxies: List<TelegramProxyItem>,
        timeoutMs: Int = 1_500,
        concurrency: Int = 8,
    ): List<TelegramProxyItem> = withContext(Dispatchers.Default) {
        val semaphore = Semaphore(concurrency)
        proxies.map { proxy ->
            async {
                semaphore.withPermit {
                    val latency = PingService.tcpPing(proxy.server, proxy.port, timeoutMs)
                    proxy.copy(pingMs = latency, isTesting = false)
                }
            }
        }.awaitAll().sortedWith(compareBy<TelegramProxyItem> {
            if (it.pingMs > 0) 0 else 1
        }.thenBy { if (it.pingMs > 0) it.pingMs else Long.MAX_VALUE })
    }

    suspend fun pingSingle(
        proxy: TelegramProxyItem,
        timeoutMs: Int = 1_500,
    ): TelegramProxyItem = withContext(Dispatchers.Default) {
        val latency = PingService.tcpPing(proxy.server, proxy.port, timeoutMs)
        proxy.copy(pingMs = latency, isTesting = false)
    }

    fun parseProxyUri(uri: String): TelegramProxyItem? {
        val clean = uri.trim().replace("&amp;", "&")
        if (!clean.contains("proxy?")) return null
        val query = clean.substringAfter('?', "")
        if (query.isEmpty()) return null
        val params = query.split('&').associate {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2) parts[0].lowercase().trim() to parts[1].trim() else parts[0].lowercase().trim() to ""
        }
        val rawServer = params["server"] ?: return null
        val server = rawServer.trim().trimEnd('.').removePrefix("http://").removePrefix("https://").trimEnd('/')
        val port = params["port"]?.toIntOrNull() ?: return null
        val secret = params["secret"]?.trim() ?: return null
        if (server.isBlank() || secret.isBlank()) return null

        val country = when {
            server.endsWith(".ir", true) || server.contains(".ir.") -> "🇮🇷"
            server.endsWith(".de", true) || server.contains("germany") -> "🇩🇪"
            server.endsWith(".nl", true) || server.contains("amsterdam") -> "🇳🇱"
            server.endsWith(".uk", true) || server.endsWith(".co.uk", true) -> "🇬🇧"
            server.endsWith(".fr", true) || server.contains("paris") -> "🇫🇷"
            server.endsWith(".us", true) -> "🇺🇸"
            server.endsWith(".ru", true) -> "🇷🇺"
            server.endsWith(".top", true) || server.endsWith(".click", true) ||
            server.endsWith(".xyz", true) || server.endsWith(".cc", true) ||
            server.endsWith(".vip", true) || server.endsWith(".site", true) -> "⚡"
            else -> "🌐"
        }

        val title = when {
            server.contains("tidelark", true) -> "Tidelark TLS"
            server.contains("mosswren", true) -> "Mosswren VIP"
            server.contains("speed-benz", true) -> "SpeedBenz Camo"
            server.contains("infinityhalo", true) -> "InfinityHalo UK"
            server.contains("closcloud", true) -> "ClosCloud TLS"
            else -> server.substringBefore('.').replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }
        }

        return TelegramProxyItem(
            id = "${server}_${port}_${secret.take(8)}",
            server = server,
            port = port,
            secret = secret,
            country = country,
            title = title.ifBlank { "MTProto" },
        )
    }

    fun extractProxiesFromHtml(html: String): List<TelegramProxyItem> {
        val unescaped = html.replace("&amp;", "&")
        val regex = Regex("""(?:tg://|https?://(?:t\.me|telegram\.dog)/)?proxy\?([^"'\s<>&]+(?:&[^"'\s<>]+)*)""", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(unescaped)
        val list = mutableListOf<TelegramProxyItem>()
        for (match in matches) {
            val query = match.groupValues[1]
            val item = parseProxyUri("proxy?$query")
            if (item != null && list.none { it.server == item.server && it.port == item.port }) {
                list.add(item)
            }
        }
        return list
    }

    suspend fun fetchFromChannel(
        channel: String,
        limit: Int = 45,
    ): List<TelegramProxyItem> = withContext(Dispatchers.Default) {
        val cleanName = channel.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("telegram.me/")
            .removePrefix("telegram.dog/")
            .removePrefix("t.me/s/")
            .removePrefix("t.me/")
            .removePrefix("@")
            .trimEnd('/')

        if (cleanName.isBlank()) return@withContext emptyList()

        val urls = listOf(
            "https://t.me/s/$cleanName",
            "https://telegram.dog/s/$cleanName",
        )

        val collected = mutableListOf<TelegramProxyItem>()

        for (url in urls) {
            runCatching {
                val socks = VpnManager.activeSocksPort
                val content = if (socks != null) {
                    runCatching {
                        httpGetResponse(
                            url = url,
                            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                            socksPort = socks,
                            connectTimeoutMs = 5_000,
                            readTimeoutMs = 8_000,
                        ).body
                    }.getOrNull()
                } else null

                val html = content ?: httpGetText(
                    url = url,
                    userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    connectTimeoutMs = 5_000,
                    readTimeoutMs = 8_000,
                )

                val items = extractProxiesFromHtml(html)
                for (item in items) {
                    if (collected.none { it.server == item.server && it.port == item.port }) {
                        collected.add(item)
                        if (collected.size >= limit) break
                    }
                }
            }
            if (collected.isNotEmpty()) break
        }

        collected
    }

    suspend fun fetchOnlineProxies(
        channel: String = "ProxyMTProto",
        limit: Int = 45,
    ): List<TelegramProxyItem> = withContext(Dispatchers.Default) {
        // 1. Try specified channel
        val fromChannel = fetchFromChannel(channel, limit)
        if (fromChannel.isNotEmpty()) {
            return@withContext (fromChannel + DEFAULT_PROXIES).distinctBy { "${it.server}:${it.port}" }
        }

        // 2. Try default verified channels
        val fallbackChannels = listOf("ProxyMTProto", "MTProxy", "ProxyMTProto_Tel")
        for (fb in fallbackChannels) {
            if (fb.equals(channel.removePrefix("@"), ignoreCase = true)) continue
            val fbList = fetchFromChannel(fb, limit)
            if (fbList.isNotEmpty()) {
                return@withContext (fbList + DEFAULT_PROXIES).distinctBy { "${it.server}:${it.port}" }
            }
        }

        // 3. GitHub backup lists
        val sources = listOf(
            "https://raw.githubusercontent.com/tgmtproxy/telegram-mtproto-proxy-list/master/proxies.txt",
            "https://raw.githubusercontent.com/SoliSpirit/mtproto/master/all_proxies.txt",
        )
        val collected = mutableListOf<TelegramProxyItem>()

        for (url in sources) {
            runCatching {
                val socks = VpnManager.activeSocksPort
                val content = if (socks != null) {
                    runCatching {
                        httpGetResponse(
                            url = url,
                            userAgent = "Mozilla/5.0 Narcic-Getway",
                            socksPort = socks,
                            connectTimeoutMs = 3_500,
                            readTimeoutMs = 5_000,
                        ).body
                    }.getOrNull()
                } else null

                val text = content ?: httpGetText(
                    url = url,
                    userAgent = "Mozilla/5.0 Narcic-Getway",
                    connectTimeoutMs = 3_500,
                    readTimeoutMs = 5_000,
                )
                val lines = text.lines()
                for (line in lines) {
                    val parsed = parseProxyUri(line)
                    if (parsed != null && collected.none { it.server == parsed.server && it.port == parsed.port }) {
                        collected.add(parsed)
                        if (collected.size >= limit) break
                    }
                }
            }
            if (collected.size >= limit) break
        }

        if (collected.isNotEmpty()) {
            (collected + DEFAULT_PROXIES).distinctBy { "${it.server}:${it.port}" }
        } else {
            DEFAULT_PROXIES
        }
    }
}
