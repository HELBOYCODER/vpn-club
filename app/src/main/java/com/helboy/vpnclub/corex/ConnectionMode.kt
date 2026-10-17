package com.helboy.vpnclub.corex

/**
 * All transport choices live in Hellboy UI. Native MSN-GUARD engines are
 * implementation details: no second activity or application is launched.
 */
enum class ConnectionMode(
    val id: String,
    val displayName: String,
    val nativeProtocol: String? = null,
) {
    AUTO("auto", "خودکار Xray"),
    VLESS("vless", "VLESS"),
    VMESS("vmess", "VMess"),
    TROJAN("trojan", "Trojan"),
    SHADOWSOCKS("ss", "Shadowsocks"),
    HYSTERIA2("hysteria2", "Hysteria2"),

    WIREGUARD("native_wireguard", "WireGuard", "wireguard"),
    MASQUE("native_masque", "MASQUE / HTTP3", "masque"),
    WARP_ON_WARP("native_gool", "WARP-on-WARP", "gool"),
    MASQUE_ON_MASQUE("native_mim", "MASQUE دوبل", "mim"),
    PSIPHON("native_psiphon", "Psiphon", "psiphon"),
    PSIPHON_OVER_WARP("native_psiphon_warp", "Psiphon روی WARP", "psiphon-over-warp"),
    TOR("native_tor", "Tor", "tor"),
    SHARD("native_shard", "SHARD", "shard");

    val isNative: Boolean get() = nativeProtocol != null

    /** Native choices are never passed into the Xray candidate scanner. */
    fun accepts(scheme: String): Boolean = when (this) {
        AUTO -> scheme in setOf("vless", "vmess", "trojan", "ss", "hysteria2", "hy2")
        HYSTERIA2 -> scheme == "hysteria2" || scheme == "hy2"
        WIREGUARD, MASQUE, WARP_ON_WARP, MASQUE_ON_MASQUE,
        PSIPHON, PSIPHON_OVER_WARP, TOR, SHARD -> false
        else -> scheme == id
    }

    companion object {
        val xrayModes: List<ConnectionMode> get() = entries.filterNot { it.isNative }
        val nativeModes: List<ConnectionMode> get() = entries.filter { it.isNative }
        fun fromId(id: String?): ConnectionMode = entries.firstOrNull { it.id == id } ?: AUTO
    }
}
