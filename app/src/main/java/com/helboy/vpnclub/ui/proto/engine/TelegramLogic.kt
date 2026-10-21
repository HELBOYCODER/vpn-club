package com.helboy.vpnclub.ui.proto.engine

import com.helboy.vpnclub.ui.fa
import dev.valid7996.narcicgeteway.data.tools.TelegramProxyItem

/** Pure helpers for the Telegram proxy list. */
object TelegramLogic {
    const val DEFAULT_CHANNEL = "ProxyMTProto"
    val QUICK_CHANNELS = listOf("ProxyMTProto", "MTProxy", "TelMTProto", "ProxyMTProto_Tel")

    /** "@name", "t.me/name", "https://t.me/s/name" -> "name"; blank stays blank. */
    fun cleanChannel(input: String): String = input.trim()
        .removePrefix("https://").removePrefix("http://")
        .removePrefix("telegram.me/").removePrefix("telegram.dog/")
        .removePrefix("t.me/s/").removePrefix("t.me/")
        .removePrefix("@").trimEnd('/')

    /** A channel name Telegram accepts: 5-32 letters, digits or underscores. */
    fun isValidChannel(name: String): Boolean = Regex("[A-Za-z][A-Za-z0-9_]{4,31}").matches(name)

    enum class Band { Untested, Failed, Fast, Medium, Slow }

    /** The repository reports a failed test as -1, the same value an untested proxy carries; [tested] tells them apart. */
    fun band(p: TelegramProxyItem, tested: Boolean = true): Band = when {
        p.pingMs <= 0L -> if (tested) Band.Failed else Band.Untested
        p.pingMs < 300 -> Band.Fast
        p.pingMs < 800 -> Band.Medium
        else -> Band.Slow
    }

    fun pingLabel(p: TelegramProxyItem, tested: Boolean = true): String = when (band(p, tested)) {
        Band.Untested -> "تست نشده"
        Band.Failed -> "بدون پاسخ"
        else -> "${p.pingMs} ms".fa()
    }

    /** Working proxies first by latency, then the untested, then the dead. */
    fun sorted(list: List<TelegramProxyItem>, tested: Set<String> = emptySet()): List<TelegramProxyItem> =
        list.sortedWith(compareBy<TelegramProxyItem> {
            when (band(it, it.id in tested)) {
                Band.Fast, Band.Medium, Band.Slow -> 0
                Band.Untested -> 1
                Band.Failed -> 2
            }
        }.thenBy { if (it.pingMs > 0) it.pingMs else Long.MAX_VALUE })

    /**
     * What a refresh achieved. The repository falls back to its built-in list when the network fails, so a result
     * with nothing beyond the built-ins means the online fetch did not work.
     */
    fun fetchedNew(result: List<TelegramProxyItem>, builtIn: List<TelegramProxyItem>): Int {
        val known = builtIn.map { "${it.server}:${it.port}" }.toSet()
        return result.count { "${it.server}:${it.port}" !in known }
    }

    fun allLinks(list: List<TelegramProxyItem>): String = list.joinToString("\n\n") { "${it.title}: ${it.webUri}" }

    fun summary(list: List<TelegramProxyItem>): String {
        val ok = list.count { it.pingMs > 0 }
        val best = list.filter { it.pingMs > 0 }.minOfOrNull { it.pingMs }
        return "${list.size.fa()} پروکسی" + (if (ok > 0) " - ${ok.fa()} سالم، بهترین ${best} ms".fa() else "")
    }
}
