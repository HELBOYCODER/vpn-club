package com.helboy.vpnclub.corex

/**
 * Protocol selection supported by the currently shipped Xray runtime.
 * Unsupported MSN-GUARD transports must not silently fall back to Xray.
 */
enum class ConnectionMode(val id: String, val displayName: String) {
    AUTO("auto", "خودکار"),
    VLESS("vless", "VLESS"),
    VMESS("vmess", "VMess"),
    TROJAN("trojan", "Trojan"),
    SHADOWSOCKS("ss", "Shadowsocks"),
    HYSTERIA2("hysteria2", "Hysteria2");

    fun accepts(scheme: String): Boolean = when (this) {
        AUTO -> scheme in setOf("vless", "vmess", "trojan", "ss", "hysteria2", "hy2")
        HYSTERIA2 -> scheme == "hysteria2" || scheme == "hy2"
        else -> scheme == id
    }

    companion object {
        fun fromId(id: String?): ConnectionMode = entries.firstOrNull { it.id == id } ?: AUTO
    }
}
