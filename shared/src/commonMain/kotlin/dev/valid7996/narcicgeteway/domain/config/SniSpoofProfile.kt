package dev.valid7996.narcicgeteway.domain.config

import kotlinx.serialization.Serializable

@Serializable
data class SniSpoofProfile(

    val link: String,

    val fakeSni: String,

    val cleanIp: String = "",

    /**
     * Runs the spoof without root, on the Rsta engine (a Go library of this app: it splits and disguises the
     * ClientHello with plain sockets). Off, the spoof is the one that injects packets and needs root.
     */
    val noRoot: Boolean = false,

    /** The Rsta method that hides the real server name ([SniSpoofConfig.METHODS]); only the no-root spoof has one. */
    val method: String = SniSpoofConfig.DEFAULT_METHOD,

    /** The port the engine connects to; 0 takes the link's own (443 when the link only points at this device). */
    val connectPort: Int = 0,
)
