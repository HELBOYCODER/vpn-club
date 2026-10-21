package com.helboy.vpnclub.ui.proto.engine

import com.helboy.vpnclub.ui.fa
import dev.valid7996.narcicgeteway.data.tools.ScanResult
import dev.valid7996.narcicgeteway.data.tools.Status

/** Pure helpers for the clean-IP scanner page. */
object IpScannerLogic {
    /** How many random addresses the engine tries per CIDR range. */
    const val IPS_PER_RANGE = 150
    const val DEFAULT_PORT = 443

    data class ParsedRanges(val ranges: List<String>, val invalid: List<String>)

    /** Splits free text into IPv4 addresses and CIDR ranges; "a.b.c.d/32" becomes the plain address. */
    fun parseRanges(text: String): ParsedRanges {
        val ok = linkedSetOf<String>()
        val bad = mutableListOf<String>()
        text.split('\n', ',', ';', ' ', '\t', '\r').map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
            val normal = normalize(token)
            if (normal != null) ok.add(normal) else bad.add(token)
        }
        return ParsedRanges(ok.toList(), bad)
    }

    private fun normalize(token: String): String? {
        val parts = token.split('/')
        if (parts.size > 2) return null
        val octets = parts[0].split('.')
        if (octets.size != 4) return null
        val nums = octets.map { o -> o.takeIf { it.length in 1..3 && it.all(Char::isDigit) }?.toIntOrNull() ?: return null }
        if (nums.any { it !in 0..255 }) return null
        val ip = nums.joinToString(".")
        if (parts.size == 1) return ip
        val prefix = parts[1].toIntOrNull() ?: return null
        return when {
            prefix == 32 -> ip
            prefix in 8..31 -> "$ip/$prefix"
            else -> null
        }
    }

    fun checkPort(text: String): Checked<Int> = SettingsValidation.checkInt(text, 1, 65535)

    /** An SNI/host: blank (use the CDN's own) or letters, digits, dots and dashes. */
    fun checkHost(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return if (Regex("[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?").matches(t)) null
        else "نام دامنه فقط حرف انگلیسی، عدد، نقطه و خط تیره دارد"
    }

    /** About how many results a scan of [ranges] will produce, for the progress bar. */
    fun expected(ranges: List<String>, perRange: Int = IPS_PER_RANGE): Int = ranges.sumOf { r ->
        if ('/' !in r) 1
        else {
            val prefix = r.substringAfter('/').toIntOrNull() ?: 32
            val hostBits = (32 - prefix).coerceIn(1, 30)
            minOf(perRange.toLong(), 1L shl hostBits).toInt()
        }
    }

    /** True when the TLS handshake worked; the engine marks a failure with RED and no domain. */
    fun reachable(r: ScanResult): Boolean = r.domain != null

    /** [list] with [r] added, replacing an earlier answer for the same address. */
    fun merge(list: List<ScanResult>, r: ScanResult): List<ScanResult> {
        val i = list.indexOfFirst { it.ip == r.ip }
        return if (i >= 0) list.toMutableList().also { it[i] = r } else list + r
    }

    /** Green first, then yellow, then red, each by latency; unreachable addresses last. */
    fun sorted(list: List<ScanResult>): List<ScanResult> = list.distinctBy { it.ip }.sortedWith(
        compareBy<ScanResult> { if (reachable(it)) 0 else 1 }
            .thenBy {
                when (it.status) {
                    Status.GREEN -> 0
                    Status.YELLOW -> 1
                    Status.RED -> 2
                }
            }
            .thenBy { if (it.latency <= 0) Int.MAX_VALUE else it.latency },
    )

    fun statusLabel(r: ScanResult): String = when {
        !reachable(r) -> "بدون پاسخ"
        r.status == Status.GREEN -> "عالی"
        r.status == Status.YELLOW -> "متوسط"
        else -> "کند"
    }

    fun latencyLabel(r: ScanResult): String = if (reachable(r)) "${r.latency} ms".fa() else "—"

    fun green(list: List<ScanResult>): List<ScanResult> = sorted(list).filter { reachable(it) && it.status == Status.GREEN }

    fun top(list: List<ScanResult>, n: Int = 16): List<ScanResult> = sorted(list).filter { reachable(it) }.take(n)

    fun ipLines(list: List<ScanResult>): String = list.joinToString("\n") { it.ip }

    fun summary(list: List<ScanResult>): String {
        val ok = list.count { reachable(it) }
        val green = list.count { reachable(it) && it.status == Status.GREEN }
        return "${list.size.fa()} آدرس بررسی شد - ${ok.fa()} پاسخ‌گو - ${green.fa()} عالی"
    }
}
