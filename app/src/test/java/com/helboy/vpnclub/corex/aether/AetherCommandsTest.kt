package com.helboy.vpnclub.corex.aether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherCommandsTest {

    private val psiphonChain = AetherProfile(protocol = AetherProfile.PROTO_MASQUE, psiphon = AetherProfile.CARRIER_CHAIN)

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
    fun listsAreCleanedTheWayShirokhorshidCleansThem() {
        assertEquals("1.2.3.4,5.6.7.0/24", PsiphonFronting.ipFlagValue("1.2.3.4\n5.6.7.0/24; bad 999.1.1.1"))
        assertEquals("a.example.com,b.example.com", PsiphonFronting.sniFlagValue("https://A.example.com/x, b.example.com."))
        assertNull(PsiphonFronting.sniFlagValue("1.2.3.4"))
        assertNull(PsiphonFronting.ipFlagValue("not an address"))
    }

}
