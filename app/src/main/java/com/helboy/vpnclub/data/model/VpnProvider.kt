package com.helboy.vpnclub.data.model

/**
 * A free VPN provider whose servers can be fetched and connected to from inside Iran.
 *
 * - VPNGATE: University of Tsukuba volunteer servers (SoftEther). Free, no account.
 *   SSTP-capable servers listen on port 995 (MS-SSTP) which bypasses SNI inspection in Iran.
 * - RISEUP: Riseup VPN (riseup.net), donation-funded, no account needed. Censorship-resistant
 *   OpenVPN with obfs support. Client cert is issued by a public API and is valid for 90 days.
 */
enum class VpnProvider(val displayName: String, val shortCode: String, val description: String) {
    VPNGATE("VPNGate (داوطلبین)", "VPNGate", "سرورهای داوطلبین ژاپنی — رایگان، بدون نیاز به اکانت، پورت ۹۹۵ (SSTP) برای ایران"),
    RISEUP("Riseup VPN", "Riseup", "سرویس رایگان ضد سانسور — بدون نیاز به ثبت‌نام، گواهی ۹۰ روزه از API عمومی");

    val isCertBased: Boolean
        get() = this == RISEUP
}

/**
 * Credentials/authentication mode a [VpnServer] requires.
 */
enum class AuthMode {
    /** VPNGate default: username "vpn", password "vpn" */
    DEFAULT_USERPASS,
    /** Riseup: client certificate + key issued by the public API */
    CLIENT_CERT
}
