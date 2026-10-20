package dev.valid7996.narcicgeteway.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherCommandsTest {

    private val psiphonChain = AetherProfile(protocol = AetherProfile.PROTO_MASQUE, psiphon = AetherProfile.CARRIER_CHAIN)

    @Test
    fun exitProxyNeverSharesAPortWithTheCoreListeners() {
        val port = LocalPorts.AETHER_SOCKS
        val arguments = AetherCommands.buildArguments(
            AetherProfile(tor = AetherProfile.CARRIER_REVERSE, psiphon = AetherProfile.CARRIER_CHAIN, protocol = AetherProfile.PROTO_MASQUE),
            port,
        )
        val bound = listOf(AetherCommands.BIND, AetherCommands.TOR_BIND, AetherCommands.PSIPHON_BIND)
            .mapNotNull { flag -> arguments.indexOf(flag).takeIf { it >= 0 }?.let { arguments[it + 1] } }
            .map { it.substringAfterLast(':').toInt() }
        assertTrue(bound.isNotEmpty())
        assertFalse(AetherExitJson.EXIT_SOCKS_PORT in bound)
        assertEquals(port + 3, AetherExitJson.EXIT_SOCKS_PORT)
    }

    @Test
    fun shippedWordIsReplacedByTheListOrTheFlagGoes() {
        val arguments = listOf("--psiphon", AetherCommands.PSIPHON_SERVER_ENTRIES, AetherCommands.SHIPPED_LIST, "--log-level", "info")
        assertEquals(
            listOf("--psiphon", AetherCommands.PSIPHON_SERVER_ENTRIES, "/data/list.txt", "--log-level", "info"),
            AetherCommands.withShippedList(arguments, "/data/list.txt"),
        )
        assertEquals(listOf("--psiphon", "--log-level", "info"), AetherCommands.withShippedList(arguments, null))
        val own = listOf(AetherCommands.PSIPHON_SERVER_ENTRIES, "/mine.txt")
        assertEquals(own, AetherCommands.withShippedList(own, null))
    }

    @Test
    fun theBundledListFlagIsWrittenOnlyWithPsiphon() {
        val withPsiphon = AetherCommands.buildArguments(psiphonChain, LocalPorts.AETHER_SOCKS)
        assertTrue(AetherCommands.PSIPHON_SERVER_ENTRIES in withPsiphon)
        assertFalse(AetherCommands.PSIPHON_SERVER_ENTRIES in AetherCommands.buildArguments(AetherProfile(), LocalPorts.AETHER_SOCKS))
    }

    @Test
    fun psiphonSessionWaitsForTheReadyWordAndTheInfoLevel() {
        val arguments = AetherCommands.buildArguments(psiphonChain, LocalPorts.AETHER_SOCKS)
        assertTrue(AetherCommands.readyNeedsWord(arguments))
        assertFalse(AetherCommands.readyNeedsWord(AetherCommands.buildArguments(AetherProfile(), LocalPorts.AETHER_SOCKS)))
        assertTrue(AetherCommands.isReadyWord("[2026-01-01 INFO] psiphon is ready"))
        val quiet = listOf("--psiphon", "--log-level", "error")
        assertTrue(AetherCommands.showsInfo(AetherCommands.withInfoLines(quiet)))
    }

    @Test
    fun customCommandGetsAListenerAndAnInfoLevelWhenItNamesNone() {
        val profile = AetherProfile(command = "aether --protocol wg --psiphon")
        val arguments = AetherCommands.runArguments(profile, 10890)
        assertEquals(10890, AetherCommands.listenerPortOf(arguments))
        assertEquals(AetherCommands.PSIPHON_BIND, AetherCommands.listenerFlagOf(arguments))
        assertTrue(AetherCommands.LOG_LEVEL in arguments)
        // A listener the command names stays.
        val named = AetherCommands.runArguments(AetherProfile(command = "--bind 127.0.0.1:1234"), 10890)
        assertEquals(1234, AetherCommands.listenerPortOf(named))
    }

    @Test
    fun keysRequestCarriesItsSettings() {
        val arguments = AetherCommands.keysArguments(
            AetherCommands.KeysRequest(kind = "masque", enrollAddress = "1.2.3.4:443", fragment = true, fragmentSize = "8-16", ech = true),
        )
        assertEquals(listOf(AetherCommands.REGISTER, "masque"), arguments.take(2))
        assertEquals("1.2.3.4:443", arguments[arguments.indexOf(AetherCommands.API_ADDRESS) + 1])
        assertEquals("8-16", arguments[arguments.indexOf("--fragment-size") + 1])
        assertTrue("--ech" in arguments)
    }

    @Test
    fun editorRulesOfPattNgHold() {
        assertNull(AetherCommands.problemOf(AetherProfile()))
        assertEquals(
            AetherCommands.Problem.CARRIER_NEEDS_MASQUE,
            AetherCommands.problemOf(AetherProfile(protocol = AetherProfile.PROTO_WG, psiphon = AetherProfile.CARRIER_REVERSE)),
        )
        assertEquals(
            AetherCommands.Problem.TOR_PSIPHON_CONFLICT,
            AetherCommands.problemOf(AetherProfile(tor = AetherProfile.CARRIER_CHAIN, psiphon = AetherProfile.CARRIER_CHAIN)),
        )
        assertNull(
            AetherCommands.problemOf(
                AetherProfile(protocol = AetherProfile.PROTO_MASQUE, tor = AetherProfile.CARRIER_CHAIN, psiphon = AetherProfile.CARRIER_REVERSE),
            ),
        )
        assertEquals(
            AetherCommands.Problem.TOR_BRIDGES_MISSING,
            AetherCommands.problemOf(AetherProfile(tor = AetherProfile.CARRIER_CHAIN, torBridges = AetherProfile.TOR_BRIDGES_OWN)),
        )
    }

    @Test
    fun wireGuardOverMasqueIsNotAskedForAsWarpInWarp() {
        val profile = AetherProfile(
            protocol = AetherProfile.PROTO_WG_OVER_MASQUE,
            wiwOuter = "162.159.198.1:443",
            wiwInner = "162.159.192.1:2408",
        )
        val arguments = AetherCommands.buildArguments(profile, LocalPorts.AETHER_SOCKS)
        assertEquals("gool", arguments[arguments.indexOf("--protocol") + 1])
        assertEquals("162.159.198.1:443", arguments[arguments.indexOf("--peer") + 1])
        assertEquals("162.159.192.1:2408", arguments[arguments.indexOf(AetherCommands.GOOL_PEER) + 1])
        assertFalse("--wiw-scan" in arguments)
        assertFalse("--wiw-outer" in arguments)
        assertFalse(AetherCommands.GOOL_CLASSIC in arguments)
        // WARP-in-WARP, by contrast, is the classic gool by name.
        val classic = AetherCommands.buildArguments(AetherProfile(protocol = AetherProfile.PROTO_GOOL), LocalPorts.AETHER_SOCKS)
        assertTrue(AetherCommands.GOOL_CLASSIC in classic)
        assertTrue("--wiw-scan" in classic)
    }

    @Test
    fun aetherLinkRoundTrips() {
        val profile = AetherProfile(
            protocol = AetherProfile.PROTO_MASQUE,
            server = "162.159.192.1",
            serverPort = 443,
            transport = AetherProfile.TRANSPORT_H2,
            masqueSni = "example.com",
            fragment = true,
            fragmentSize = "8-16",
            psiphon = AetherProfile.CARRIER_CHAIN,
            psiphonRegion = "DE",
            torBridgeLines = "",
            finalMask = "mask",
        )
        val link = AetherLink.build(profile, "My Aether ✓")
        assertTrue(AetherLink.isAetherLink(link))
        val (name, back) = AetherLink.parse(link)!!
        assertEquals("My Aether ✓", name)
        assertEquals(profile.protocol, back.protocol)
        assertEquals("162.159.192.1", back.server)
        assertEquals(443, back.serverPort)
        assertEquals(AetherProfile.TRANSPORT_H2, back.transport)
        assertEquals("example.com", back.masqueSni)
        assertTrue(back.fragment)
        assertEquals("8-16", back.fragmentSize)
        assertEquals(AetherProfile.CARRIER_CHAIN, back.psiphon)
        assertEquals("DE", back.psiphonRegion)
        assertEquals("mask", back.finalMask)
    }

    @Test
    fun twoHopLinkCarriesItsHops() {
        val profile = AetherProfile(
            protocol = AetherProfile.PROTO_WG_OVER_MASQUE,
            wiwOuter = "162.159.198.1:443",
            wiwInner = "162.159.192.1:2408",
        )
        val back = AetherLink.parse(AetherLink.build(profile, "x"))!!.second
        assertEquals("162.159.198.1:443", back.wiwOuter)
        assertEquals("162.159.192.1:2408", back.wiwInner)
        assertEquals("", back.server)
    }
}

class AetherFrontTest {

    private fun json(text: String) = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject

    @Test
    fun frontIsLeftOutWithoutRoutingAndBuiltWithIt() {
        assertFalse(AetherFront.needed(XrayJsonBuilder.BuildOptions()))
        assertTrue(AetherFront.needed(XrayJsonBuilder.BuildOptions(bypassIran = true)))
    }

    @Test
    fun frontCarriesTheExitAndSendsTheProxyToTheCore() {
        val config = json(AetherFront.config(AetherProfile(), XrayJsonBuilder.BuildOptions(bypassIran = true)))
        val ports = (config["inbounds"] as kotlinx.serialization.json.JsonArray)
            .map { (it as kotlinx.serialization.json.JsonObject)["port"].toString().trim('"').toInt() }
        assertTrue(LocalPorts.XRAY_SOCKS in ports)
        assertTrue(LocalPorts.AETHER_EXIT in ports)

        // The exit's own traffic is the first rule: no rule of the user's can send the core's dialling back into the core.
        val rules = (config["routing"] as kotlinx.serialization.json.JsonObject)["rules"] as kotlinx.serialization.json.JsonArray
        val first = rules.first() as kotlinx.serialization.json.JsonObject
        assertEquals(AetherExitJson.EXIT_OUTBOUND_TAG, first["outboundTag"].toString().trim('"'))
        assertTrue(first["inboundTag"].toString().contains(AetherExitJson.EXIT_INBOUND_TAG))

        val outbounds = (config["outbounds"] as kotlinx.serialization.json.JsonArray).map { it as kotlinx.serialization.json.JsonObject }
        val proxy = outbounds.first { it["tag"].toString().trim('"') == "proxy" }
        assertEquals("socks", proxy["protocol"].toString().trim('"'))
        assertTrue(proxy.toString().contains("\"port\":${LocalPorts.AETHER_SOCKS}"))
        assertTrue(outbounds.any { it["tag"].toString().trim('"') == AetherExitJson.EXIT_OUTBOUND_TAG })
    }
}

class PsiphonFrontingTest {

    @Test
    fun listsAreCleanedTheWayShirokhorshidCleansThem() {
        assertEquals("1.2.3.4,5.6.7.0/24", PsiphonFronting.ipFlagValue("1.2.3.4\n5.6.7.0/24; bad 999.1.1.1"))
        assertEquals("a.example.com,b.example.com", PsiphonFronting.sniFlagValue("https://A.example.com/x, b.example.com."))
        assertNull(PsiphonFronting.sniFlagValue("1.2.3.4"))
        assertNull(PsiphonFronting.ipFlagValue("not an address"))
    }

    @Test
    fun standalonePsiphonCarriesTheOverridesTheEdgesCertificatesNeed() {
        val json = PsiphonConfigBuilder.build(PsiphonProfile(cdnSni = "front.example.com", cdnIps = "1.2.3.4"), "/d", 1, 2)
        // A fronted dial is verified against these, not against the name the user typed.
        assertTrue("a248.e.akamai.net" in json)
        assertTrue("\"FrontedMeekDialOverridesProbability\":1.0" in json)
        assertTrue("FrontedMeekCDNScanSpec" in json)
        assertTrue("FrontedMeekCDNScanUseBuiltInSpec" in json)
        assertTrue("AggressiveEstablishment" in json)
        assertFalse("AggressiveEstablishment" in PsiphonConfigBuilder.build(PsiphonProfile(beast = false), "/d", 1, 2))
    }

    @Test
    fun aetherAsksForTheOverlayAndKeepsAnSniOnItsOwn() {
        val profile = AetherProfile(protocol = AetherProfile.PROTO_MASQUE, psiphon = AetherProfile.CARRIER_CHAIN, psiphonCdnSni = "front.example.com")
        val arguments = AetherCommands.buildArguments(profile, LocalPorts.AETHER_SOCKS)
        assertEquals("front.example.com", arguments[arguments.indexOf("--psiphon-cdn-sni") + 1])
        assertFalse("--psiphon-cdn-ips" in arguments)
        assertEquals(AetherCommands.OVERLAY_BEAST_WORD, arguments[arguments.indexOf(AetherCommands.PSIPHON_CONFIG) + 1])
        val overlay = AetherCommands.overlayOf(arguments)!!
        assertTrue("front.example.com" in overlay)
        assertTrue("AggressiveEstablishment" in overlay)
        val placed = AetherCommands.withOverlay(arguments, "/x/o.json")
        assertEquals("/x/o.json", placed[placed.indexOf(AetherCommands.PSIPHON_CONFIG) + 1])
        assertFalse(AetherCommands.PSIPHON_CONFIG in AetherCommands.withOverlay(arguments, null))
        // Beast mode off: the overlay still carries the overrides, not the aggressive establishment.
        val calm = AetherCommands.buildArguments(profile.copy(psiphonBeast = false), LocalPorts.AETHER_SOCKS)
        assertFalse("AggressiveEstablishment" in AetherCommands.overlayOf(calm)!!)
    }
}

class SniSpoofNoRootTest {

    private val vless = """{"outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"127.0.0.1","port":40443,"users":[{"id":"x"}]}]}},{"tag":"direct","protocol":"freedom"}]}"""

    @Test
    fun linkRoundTripsTheNoRootSettings() {
        val profile = SniSpoofProfile(link = "vless://x@1.2.3.4:443", fakeSni = "cdnjs.cloudflare.com", cleanIp = "104.18.38.202", noRoot = true, method = "fragment", connectPort = 8443)
        val (name, back) = SniSpoofLink.parse(SniSpoofLink.build(profile, "rsta"))!!
        assertEquals("rsta", name)
        assertTrue(back.noRoot)
        assertEquals("fragment", back.method)
        assertEquals(8443, back.connectPort)
        // A link of the root spoof stays one.
        val plain = SniSpoofLink.parse(SniSpoofLink.build(SniSpoofProfile(link = "vless://x@1.2.3.4:443", fakeSni = "a.com"), "x"))!!.second
        assertFalse(plain.noRoot)
    }

    @Test
    fun aProfileMadeForTheRstaAppNeedsTheCleanIpToKnowTheServer() {
        val (host, port) = SniSpoofConfig.outboundOf(vless)!!
        assertEquals("127.0.0.1", host)
        assertEquals(40443, port)
        // No clean IP: falls back to default clean IP and port
        assertEquals(SniSpoofConfig.defaultConnectIp to SniSpoofConfig.defaultConnectPort, SniSpoofConfig.target(SniSpoofProfile(link = "", fakeSni = "a.com", noRoot = true), host, port))
        // With one: its address, and 443 where the link only points at this device.
        assertEquals("104.18.38.202" to 443, SniSpoofConfig.target(SniSpoofProfile(link = "", fakeSni = "a.com", cleanIp = "104.18.38.202", noRoot = true), host, port))
        // A link with a real address keeps its own port, and a port of the profile overrides it.
        assertEquals("1.2.3.4" to 2053, SniSpoofConfig.target(SniSpoofProfile(link = "", fakeSni = "a.com", noRoot = true), "1.2.3.4", 2053))
        assertEquals("1.2.3.4" to 8443, SniSpoofConfig.target(SniSpoofProfile(link = "", fakeSni = "a.com", noRoot = true, connectPort = 8443), "1.2.3.4", 2053))
    }

    @Test
    fun theProxyOutboundIsPointedAtTheEngineAndNothingElseChanges() {
        val rewritten = SniSpoofConfig.withOutbound(vless, "127.0.0.1", 51234)
        assertEquals("127.0.0.1" to 51234, SniSpoofConfig.outboundOf(rewritten))
        assertTrue("\"id\":\"x\"" in rewritten)
        assertTrue("\"tag\":\"direct\"" in rewritten)
        // A config without a proxy outbound is returned as it was.
        assertEquals("{}", SniSpoofConfig.withOutbound("{}", "127.0.0.1", 1))
        assertEquals("combined", SniSpoofConfig.method("nonsense"))
        assertEquals("oob", SniSpoofConfig.method("oob"))
    }
}
