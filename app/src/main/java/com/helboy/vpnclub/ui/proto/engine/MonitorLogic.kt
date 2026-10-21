package com.helboy.vpnclub.ui.proto.engine

import com.helboy.vpnclub.ui.fa
import dev.valid7996.narcicgeteway.core.VpnManager
import dev.valid7996.narcicgeteway.domain.model.ConnectionState
import dev.valid7996.narcicgeteway.ui.platform.LiveStats
import java.util.Locale

/** Pure helpers for the live monitor: rolling series, names and one-line verdicts. */
object MonitorLogic {
    /** Samples kept per graph. */
    const val HISTORY = 90

    /** [this] with [value] appended, oldest samples dropped beyond [cap]. */
    fun List<Float>.pushed(value: Float, cap: Int = HISTORY): List<Float> =
        (this + value).let { if (it.size > cap) it.subList(it.size - cap, it.size).toList() else it }

    /** The largest value, but never below [floor] so an idle graph is flat instead of exploding. */
    fun ceiling(vararg series: List<Float>, floor: Float = 1f): Float =
        maxOf(floor, series.maxOfOrNull { s -> s.maxOrNull() ?: 0f } ?: 0f)

    /** CPU use as a share of the whole device (the platform reports it as a share of one core). */
    fun deviceShare(stats: LiveStats): Float? {
        val ofOneCore = stats.cpuPercent ?: return null
        return (ofOneCore / stats.coreCount.coerceAtLeast(1)).coerceIn(0f, 100f)
    }

    fun percent(v: Float?): String = if (v == null) "—" else (String.format(Locale.US, "%.1f", v) + "%").fa()

    fun memory(bytes: Long?): String = if (bytes == null) "—" else "${(bytes / 1_048_576L)} MB".fa()

    fun temperature(c: Float?): String = if (c == null) "—" else (String.format(Locale.US, "%.1f", c) + "°C").fa()

    fun stateLabel(state: ConnectionState): String = when (state) {
        ConnectionState.Idle -> "قطع"
        ConnectionState.Connecting -> "در حال اتصال"
        ConnectionState.Connected -> "متصل"
        ConnectionState.Reconnecting -> "اتصال دوباره"
        ConnectionState.Disconnecting -> "در حال قطع"
        ConnectionState.Error -> "خطا"
    }

    fun engineName(kind: String?, active: Boolean): String = when (kind) {
        VpnManager.KIND_XRAY -> "Xray-Core"
        VpnManager.KIND_SINGBOX -> "Sing-Box"
        VpnManager.KIND_SNISPOOF -> "Rsta SNI-Spoof"
        VpnManager.KIND_PSIPHON -> "Psiphon"
        VpnManager.KIND_TOR -> "Tor"
        VpnManager.KIND_SSH -> "SSH"
        VpnManager.KIND_OPENCONNECT -> "OpenConnect"
        VpnManager.KIND_IKEV2 -> "IKEv2 / IPsec"
        VpnManager.KIND_AETHER -> "Aether"
        VpnManager.KIND_DNS_TUNNEL -> "تونل DNS"
        VpnManager.KIND_MASTERDNS -> "MasterDNS"
        VpnManager.KIND_MHRV -> "رله‌ی MHRV"
        VpnManager.KIND_CROSS_CHAIN -> "زنجیره‌ی متقاطع"
        else -> if (active) "هسته‌ی VPN" else "بیکار"
    }

    enum class Pressure(val title: String, val text: String) {
        Calm("بدون فشار", "پردازنده، باتری و حافظه در وضعیت آرام‌اند."),
        Moderate("فشار متوسط", "دستگاه در حال کار معمول است و دما در محدوده‌ی ایمن است."),
        High("فشار زیاد", "دمای دستگاه بالاست؛ برای سلامت باتری ترافیک سنگین را کم کنید."),
    }

    fun pressure(stats: LiveStats): Pressure {
        val temp = stats.tempC
        val headroom = stats.thermalHeadroom
        val cpu = deviceShare(stats) ?: 0f
        val hot = (temp != null && temp >= 43f) || (headroom != null && headroom >= 0.85f) ||
            (stats.thermalStatus != null && stats.thermalStatus >= 2)
        val warm = (temp != null && temp >= 39f) || (headroom != null && headroom >= 0.60f) || cpu > 25f
        return when {
            hot -> Pressure.High
            warm -> Pressure.Moderate
            else -> Pressure.Calm
        }
    }

    fun thermalLabel(status: Int?): String? = when (status) {
        null, 0 -> null
        1 -> "گرمای سبک"
        2 -> "گرمای متوسط"
        else -> "گرمای شدید"
    }

    /** True when the crypto line from the platform says AES/SHA hardware is in use. */
    fun cryptoAccelerated(raw: String): Boolean =
        (raw.contains("repaired=[") && !raw.contains("repaired=[]")) ||
            (raw.contains("already=[") && !raw.contains("already=[]"))
}
