package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorSettingsTest {
    private val all = (1..12).map { "obfs4 10.0.0.$it:443 CERT$it" }

    @Test fun `with nothing ticked the first eight bridges are used, as the engine does`() {
        assertEquals(all.take(8).toSet(), TorBridgeSettings.effective("", all))
        assertTrue(TorBridgeSettings.isUsed("", all[0], all))
        assertFalse(TorBridgeSettings.isUsed("", all[9], all))
    }

    @Test fun `unticking starts from the default selection`() {
        val next = TorBridgeSettings.toggle("", all[0], all)
        assertEquals(all.subList(1, 8).toSet(), TorBridgeSettings.enabledSet(next))
        assertFalse(TorBridgeSettings.isUsed(next, all[0], all))
    }

    @Test fun `ticking an unused bridge adds it`() {
        val next = TorBridgeSettings.toggle("", all[10], all)
        assertTrue(TorBridgeSettings.isUsed(next, all[10], all))
        assertEquals(9, TorBridgeSettings.effective(next, all).size)
    }

    @Test fun `unticking the last one goes back to the default selection`() {
        val one = TorBridgeSettings.toggle(TorBridgeSettings.selectAll("", listOf(all[0])), all[0], listOf(all[0]))
        assertEquals("", one)
    }

    @Test fun `lines of other bridge types survive`() {
        val other = "snowflake 192.0.2.3:80 FP"
        val stored = other + "\n" + all[2]
        val next = TorBridgeSettings.toggle(stored, all[3], all)
        val set = TorBridgeSettings.enabledSet(next)
        assertTrue(other in set)
        assertTrue(all[2] in set && all[3] in set)
        assertEquals(setOf(other), TorBridgeSettings.enabledSet(TorBridgeSettings.reset(next, all)))
    }

    @Test fun `select all ticks every shown line`() {
        val s = TorBridgeSettings.selectAll("", all)
        assertEquals(all.toSet(), TorBridgeSettings.enabledSet(s))
        assertTrue(all.all { TorBridgeSettings.isUsed(s, it, all) })
    }

    @Test fun `own bridge lines skip blanks and comments`() {
        assertEquals(listOf("obfs4 a", "obfs4 b"), TorBridgeSettings.ownLines("# note\n\nobfs4 a\n obfs4 b \n"))
    }

    @Test fun `summary describes the mode`() {
        assertTrue(TorBridgeSettings.summary(AppSettings()).contains("بدون پل"))
        assertTrue(TorBridgeSettings.summary(AppSettings(torBridgesMode = "default", torBridgeTransport = "snowflake")).contains("Snowflake"))
        assertTrue(TorBridgeSettings.summary(AppSettings(torBridgesMode = "own", torOwnBridges = "obfs4 a\nobfs4 b")).contains("۲"))
    }

    @Test fun `own mode without lines is a problem`() {
        assertNotNull(TorBridgeSettings.problem(AppSettings(torBridgesMode = "own")))
        assertNull(TorBridgeSettings.problem(AppSettings(torBridgesMode = "own", torOwnBridges = "obfs4 a")))
        assertNull(TorBridgeSettings.problem(AppSettings()))
    }

    @Test fun `own mode adds scramblesuit`() {
        assertFalse("scramblesuit" in TorBridgeSettings.transports("default"))
        assertTrue("scramblesuit" in TorBridgeSettings.transports("own"))
    }
}
