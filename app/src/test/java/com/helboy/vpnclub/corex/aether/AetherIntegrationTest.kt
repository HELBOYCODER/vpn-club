package com.helboy.vpnclub.corex.aether

import com.helboy.vpnclub.corex.LinkParser
import com.helboy.vpnclub.corex.XrayCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** مسیر ورود aether:// به اپ: لینک → ProxyConfig → پشتیبانی → پروفایل قابل اجرا. */
class AetherIntegrationTest {

    private val warp = AetherProfile(
        protocol = AetherProfile.PROTO_WG,
        server = "162.159.192.1",
        serverPort = 2408,
    )

    @Test fun linkRoundTripKeepsEndpointAndProtocol() {
        val link = AetherLink.build(warp, "نام تست")
        assertTrue(link.startsWith("aether://162.159.192.1:2408?"))
        val (name, back) = AetherLink.parse(link)!!
        assertEquals("نام تست", name)
        assertEquals(AetherProfile.PROTO_WG, back.protocol)
        assertEquals("162.159.192.1", back.server)
        assertEquals(2408, back.serverPort)
    }

    @Test fun linkParserAcceptsAetherAndKeepsRawForLater() {
        val link = AetherLink.build(warp, "x")
        val c = LinkParser.parse(link)!!
        assertEquals("aether", c.scheme)
        assertEquals("162.159.192.1", c.host)
        assertEquals(2408, c.port)
        assertEquals(link, c.raw)
        assertTrue(XrayCore.isSupported(c))
    }

    @Test fun twoHopProfileWithoutEndpointGetsStableNominalHost() {
        val chain = AetherProfile(protocol = AetherProfile.PROTO_GOOL, wiwOuter = "1.2.3.4:500", wiwInner = "5.6.7.8:600")
        val c = LinkParser.parse(AetherLink.build(chain, "chain"))!!
        assertEquals("aether.local", c.host)
        assertEquals(443, c.port)
    }

    @Test fun carrierProfilesAreRefusedInThisVersion() {
        val withPsiphon = warp.copy(psiphon = AetherProfile.CARRIER_CHAIN)
        val c = LinkParser.parse(AetherLink.build(withPsiphon, "p"))!!
        assertFalse(XrayCore.isSupported(c))
    }

    @Test fun nonAetherTextIsNotAetherLink() {
        assertEquals(null, AetherLink.parse("vless://a@b:1"))
    }
}
