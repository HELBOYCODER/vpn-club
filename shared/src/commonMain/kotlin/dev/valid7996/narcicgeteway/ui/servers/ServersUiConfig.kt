package dev.valid7996.narcicgeteway.ui.servers

/**
 * The switches that hide parts of the servers interface without removing them. Flip one back to
 * true and the feature returns as it was.
 */
object ServersUiConfig {

    /** The share-link and share-QR items of a server card's menu. */
    const val SHOW_SHARE_ACTIONS: Boolean = true

    /** Every import and engine option of the add sheet; false leaves the individually-enabled
     *  options below plus the clipboard and subscriptions. */
    const val SHOW_ALL_ADD_OPTIONS: Boolean = true

    /** The QR scan option of the add sheet. */
    const val SHOW_QR_SCAN: Boolean = true

    /** The Psiphon engine option of the add sheet. */
    const val SHOW_PSIPHON: Boolean = true

    /** The SNI spoof (no root) engine option of the add sheet. */
    const val SHOW_SNI_SPOOF_NO_ROOT: Boolean = true

    /** The Tor engine option of the add sheet. */
    const val SHOW_TOR: Boolean = true

    /** The Aether engine option of the add sheet. */
    const val SHOW_AETHER: Boolean = true

    /** The Proxy Chain option of the add sheet. */
    const val SHOW_PROXY_CHAIN: Boolean = true

    /** The Cross-Chain (dual-engine) option of the add sheet. */
    const val SHOW_CROSS_CHAIN: Boolean = true

    /** The plain-TCP ping entry of the ping menu; the real-delay entry always stays. */
    const val SHOW_TCP_PING: Boolean = false

    /** The per-card actions menu (edit, rename, move, ping, delete) on the cards of both screens. */
    const val SHOW_CARD_MENU: Boolean = true
}
