package com.helboy.vpnclub.corex

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** بردارهای آزمون رسمی RFC 7748 — اگر بشکند، کلید WARP نامعتبر می‌شود. */
class X25519Test {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun rfc7748Section52Vector() {
        val k = hex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4")
        val u = hex("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c")
        assertArrayEquals(hex("c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552"), X25519.scalarMult(k, u))
    }

    @Test fun rfc7748Section61DiffieHellman() {
        val alicePriv = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val bobPriv = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        val alicePub = X25519.publicKey(alicePriv)
        val bobPub = X25519.publicKey(bobPriv)
        assertArrayEquals(hex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"), alicePub)
        assertArrayEquals(hex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f"), bobPub)
        val shared = hex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")
        assertArrayEquals(shared, X25519.scalarMult(alicePriv, bobPub))
        assertArrayEquals(shared, X25519.scalarMult(bobPriv, alicePub))
    }

    @Test fun newKeysAreClampedAnd32Bytes() {
        val k = X25519.newPrivateKey()
        assertEquals(32, k.size); assertEquals(0, k[0].toInt() and 7); assertEquals(64, k[31].toInt() and 192)
        assertEquals(32, X25519.publicKey(k).size)
    }
}
