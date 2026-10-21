package com.helboy.vpnclub.ui.proto.editors

import com.helboy.vpnclub.ui.fa
import dev.valid7996.narcicgeteway.domain.model.AppSettings

/** The Tor profile itself only has a name; bridges are app-wide settings. These helpers describe and edit them. */
object TorBridgeSettings {
    const val MODE_NONE = "none"
    const val MODE_DEFAULT = "default"
    const val MODE_OWN = "own"

    val MODES = listOf(MODE_NONE to "بدون پل", MODE_DEFAULT to "پل‌های پیش‌فرض", MODE_OWN to "پل‌های خودم")

    /** Bridge types the built-in list covers. */
    val BUILT_IN_TRANSPORTS = listOf("obfs4", "obfs3", "meek_lite", "snowflake", "conjure", "webtunnel", "vanilla")

    /** Bridge types you can type your own lines for (adds scramblesuit). */
    val OWN_TRANSPORTS = listOf("obfs4", "obfs3", "scramblesuit", "meek_lite", "snowflake", "conjure", "webtunnel", "vanilla")

    val RENDEZVOUS = listOf("amp" to "AMP", "cdn77" to "CDN77", "amazon" to "AMAZON")

    fun transports(mode: String): List<String> = if (mode == MODE_OWN) OWN_TRANSPORTS else BUILT_IN_TRANSPORTS

    fun transportLabel(t: String): String = when (t) {
        "obfs4" -> "obfs4"
        "obfs3" -> "obfs3"
        "scramblesuit" -> "ScrambleSuit"
        "meek_lite" -> "meek"
        "snowflake" -> "Snowflake"
        "conjure" -> "Conjure"
        "webtunnel" -> "WebTunnel"
        "vanilla" -> "ساده (Vanilla)"
        else -> t
    }

    /** The lines the person ticked in the bridge list. */
    fun enabledSet(raw: String): Set<String> = raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** How many of the built-in bridges Tor uses when none is ticked (the engine takes the first eight). */
    const val DEFAULT_COUNT = 8

    /** The lines of [all] that will be used: the ticked ones, or the first [DEFAULT_COUNT] when none is ticked. */
    fun effective(raw: String, all: List<String>): Set<String> {
        val ticked = enabledSet(raw).intersect(all.toSet())
        return if (ticked.isEmpty()) all.take(DEFAULT_COUNT).toSet() else ticked
    }

    fun isUsed(raw: String, line: String, all: List<String>): Boolean = line in effective(raw, all)

    /**
     * Ticking / unticking one bridge line of [all] (the lines being shown). Lines of other bridge types that are
     * already stored stay as they are. Unticking the last line goes back to the default selection.
     */
    fun toggle(raw: String, line: String, all: List<String>): String {
        val outside = enabledSet(raw) - all.toSet()
        val next = effective(raw, all).toMutableSet()
        if (!next.remove(line)) next.add(line)
        return (outside + next).joinToString("\n")
    }

    /** Every line of [all] ticked. */
    fun selectAll(raw: String, all: List<String>): String = ((enabledSet(raw) - all.toSet()) + all).joinToString("\n")

    /** Back to the default selection for the lines of [all]. */
    fun reset(raw: String, all: List<String>): String = (enabledSet(raw) - all.toSet()).joinToString("\n")

    fun ownLines(raw: String): List<String> = raw.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    /** One line of text that sums up what the profile will use. */
    fun summary(s: AppSettings): String = when (s.torBridgesMode) {
        MODE_DEFAULT -> {
            val picked = enabledSet(s.torEnabledBridges).size
            "پل‌های پیش‌فرض (${transportLabel(s.torBridgeTransport)})" + if (picked == 0) " · ${DEFAULT_COUNT.fa()} پل اول" else " · ${picked.fa()} انتخاب‌شده"
        }
        MODE_OWN -> {
            val n = ownLines(s.torOwnBridges).size
            "پل‌های شما (${transportLabel(s.torBridgeTransport)})" + if (n == 0) " · هنوز خطی وارد نشده" else " · ${n.fa()} خط"
        }
        else -> "بدون پل؛ اتصال مستقیم به شبکهٔ تور"
    }

    /** A problem that stops Tor from using the bridges, or null. */
    fun problem(s: AppSettings): String? =
        if (s.torBridgesMode == MODE_OWN && ownLines(s.torOwnBridges).isEmpty()) "حالت «پل‌های خودم» انتخاب شده ولی هیچ خطی وارد نشده." else null
}
