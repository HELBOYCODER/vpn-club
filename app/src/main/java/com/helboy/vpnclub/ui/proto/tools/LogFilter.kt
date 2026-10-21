package com.helboy.vpnclub.ui.proto.tools

import dev.valid7996.narcicgeteway.core.VpnManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Who wrote a log line, by logcat tag. */
enum class LogSource(val label: String, val tags: Set<String>) {
    App(
        "برنامه",
        setOf(
            "NarcicGeteway", "NrgVpnService", "MainActivity", "VpnManager", "AndroidVpn", "PingService", "PingCoordinator",
            "ConfigRepository", "GeoAssets", "UpdateChecker", "Ikev2Controller", "Ikev2EventReceiver", "OpenConnectController",
            "NrgWidgetProvider", "HevTunCore", "SocksShim", "SpeedTest",
        ),
    ),
    Xray("Xray", setOf("GoLog", "Go", "XrayController", "Libv2ray", "v2ray", "LibXray", "CoreProbe")),
    Tor("Tor", setOf("Tor", "TorController", "Snowflake", "Obfs4", "Conjure")),
    Dns("DNS", setOf("DnsTunnel", "DnsProbe", "DnsScanner", "DnsTunnelController", "Dnstt", "VayDns")),
    MasterDns("MasterDNS", setOf("MasterDns", "MasterDnsController", "masterdns")),
    Mhrv("MHRV", setOf("mhrv_rs", "MhrvRelay", "MhrvRelayController", "mhrv")),
    Psiphon("Psiphon", setOf("Psiphon")),
    Ssh("SSH", setOf("Ssh", "SshController", "JSch")),
}

/** Minimum severity to show. Android priority letters: V D I W E F. */
enum class LogViewLevel(val label: String, val letters: Set<Char>) {
    All("همه", setOf('V', 'D', 'I', 'W', 'E', 'F')),
    Info("اطلاع و بالاتر", setOf('I', 'W', 'E', 'F')),
    Warn("هشدار و بالاتر", setOf('W', 'E', 'F')),
    Error("فقط خطا", setOf('E', 'F')),
}

data class LogLine(val raw: String, val level: Char, val tag: String?)

private val TAG_RE = Regex("""\s([VDIWEF])/([A-Za-z0-9_.$-]+)""")

/** Reads the priority and tag out of a `logcat -v time` line; lines without them count as Info with no tag. */
fun parseLogLine(raw: String): LogLine {
    val m = TAG_RE.find(raw)
    return LogLine(raw, m?.groupValues?.get(1)?.first() ?: 'I', m?.groupValues?.get(2)?.trim())
}

/** The sources a running tunnel of [kind] writes to, or empty when nothing is running. */
fun sourcesForKind(kind: String?): Set<LogSource> = when (kind) {
    VpnManager.KIND_XRAY, VpnManager.KIND_SNISPOOF -> setOf(LogSource.Xray)
    VpnManager.KIND_PSIPHON -> setOf(LogSource.Psiphon)
    VpnManager.KIND_TOR -> setOf(LogSource.Tor)
    VpnManager.KIND_DNS_TUNNEL -> setOf(LogSource.Dns)
    VpnManager.KIND_MASTERDNS -> setOf(LogSource.MasterDns)
    VpnManager.KIND_MHRV -> setOf(LogSource.Mhrv)
    VpnManager.KIND_SSH -> setOf(LogSource.Ssh, LogSource.Xray)
    VpnManager.KIND_CROSS_CHAIN -> LogSource.entries.toSet()
    else -> emptySet()
}

/**
 * Applies the viewer's choices. [sources] null means every line; otherwise a line needs a tag that belongs to one of
 * them ([ownTags] are tags the app itself wrote that no other source claims; they count as App).
 */
fun filterLog(
    lines: List<String>,
    level: LogViewLevel,
    query: String,
    sources: Set<LogSource>?,
    ownTags: Set<String> = emptySet(),
): List<LogLine> {
    val needle = query.trim()
    val appTags = ownTags.filterTo(HashSet()) { tag -> LogSource.entries.none { it != LogSource.App && tag in it.tags } }
    return lines.asSequence()
        .map(::parseLogLine)
        .filter { it.level in level.letters }
        .filter { line ->
            sources == null || line.tag != null && sources.any { s ->
                line.tag in s.tags || s == LogSource.App && line.tag in appTags
            }
        }
        .filter { needle.isEmpty() || it.raw.contains(needle, ignoreCase = true) }
        .toList()
}

fun logText(lines: List<LogLine>): String = lines.joinToString("\n") { it.raw }

fun logFileName(nowMs: Long): String =
    "hellboy-log-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(nowMs)) + ".txt"
