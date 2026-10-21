package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.domain.config.MhrvRelayProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MhrvFormTest {
    private val ok = MhrvForm(scriptIds = listOf("AKfycbxID1"), authKey = "secret")

    @Test fun `apps script mode needs a deployment and an auth key`() {
        val c = MhrvForm().check()
        assertNotNull(c.errors[MhrvForm.F_IDS])
        assertNotNull(c.errors[MhrvForm.F_AUTH])
        assertTrue(ok.check().ok)
    }

    @Test fun `direct mode needs no credentials`() {
        assertTrue(MhrvForm(mode = MhrvRelayProfile.MODE_DIRECT).check().ok)
    }

    @Test fun `check agrees with the profile's own isValid in every mode`() {
        MhrvRelayProfile.MODES.forEach { mode ->
            listOf(MhrvForm(mode = mode), ok.copy(mode = mode), ok.copy(mode = mode, authKey = ""), ok.copy(mode = mode, scriptIds = emptyList()))
                .forEach { assertEquals("$mode $it", it.toProfile().isValid, it.check().ok) }
        }
    }

    @Test fun `upstream socks must be host and port, as a warning`() {
        assertNotNull(ok.copy(upstreamSocks5 = "nonsense").check().warnings[MhrvForm.F_UPSTREAM])
        assertNull(ok.copy(upstreamSocks5 = "127.0.0.1:10808").check().warnings[MhrvForm.F_UPSTREAM])
        assertTrue(ok.copy(upstreamSocks5 = "nonsense").check().ok)
    }

    @Test fun `profile round trip`() {
        val p = MhrvRelayProfile(
            mode = MhrvRelayProfile.MODE_FULL, scriptIds = "id1\nid2", authKey = "k", googleIp = "1.2.3.4", frontDomain = "front.example",
            sniHosts = "a.google.com\nb.google.com", verifySsl = false, parallelRelay = 3, blockQuic = false, blockStun = false,
            blockDoh = false, tunnelDoh = false, coalesceStepMs = 20, coalesceMaxMs = 2000, youtubeViaRelay = true,
            upstreamSocks5 = "127.0.0.1:1", logLevel = "debug",
        )
        assertEquals(p, MhrvForm.from(p, "n").toProfile())
    }

    @Test fun `pasting a config keeps the auth key when the paste has none`() {
        val parsed = MhrvRelayProfile.fromText("https://script.google.com/macros/s/ABC123/exec")!!
        val f = ok.copy(authKey = "mine").withPasted(parsed)
        assertEquals(listOf("ABC123"), f.scriptIds)
        assertEquals("mine", f.authKey)
        assertEquals("name stays", ok.copy(name = "name stays").withPasted(parsed).name)
    }

    @Test fun `deployment ids accept urls, many at once, and drop duplicates`() {
        assertEquals(listOf("A", "B"), MhrvLists.parseIds("https://script.google.com/macros/s/A/exec, B\nA"))
        assertEquals(listOf("A", "B", "C"), MhrvLists.addIds(listOf("A", "B"), "B C"))
        assertEquals(listOf("A"), MhrvLists.removeAt(listOf("A", "B"), 1))
        assertEquals(listOf("A", "B"), MhrvLists.removeAt(listOf("A", "B"), 5))
        assertTrue(MhrvLists.parseIds("  ,; ").isEmpty())
    }

    @Test fun `editing a row can split a paste into several rows`() {
        assertEquals(listOf("A", "X", "Y", "C"), MhrvLists.editAt(listOf("A", "B", "C"), 1, "X Y"))
        assertEquals(listOf("A", "Z", "C"), MhrvLists.editAt(listOf("A", "B", "C"), 1, "Z"))
        assertEquals(listOf("A"), MhrvLists.editAt(listOf("A"), 3, "Q"))
    }

    @Test fun `an empty sni pool means the defaults are active`() {
        assertEquals(MhrvRelayProfile.DEFAULT_SNI_POOL, MhrvLists.activePool(""))
        assertEquals(listOf("a.com"), MhrvLists.activePool("a.com"))
    }

    @Test fun `ticking and unticking sni names`() {
        val without = MhrvLists.setEnabled("", "www.google.com", false)
        assertFalse("www.google.com" in MhrvLists.activePool(without))
        assertEquals(MhrvRelayProfile.DEFAULT_SNI_POOL.size - 1, MhrvLists.activePool(without).size)
        val withNew = MhrvLists.addCustom(without, "maps.example.com, x.example.com")
        assertTrue("maps.example.com" in MhrvLists.activePool(withNew))
        assertTrue("x.example.com" in MhrvLists.shownPool(withNew))
        assertTrue("www.google.com" in MhrvLists.shownPool(withNew))
    }

    @Test fun `probe answers are read from the native json`() {
        assertEquals(SniProbe.Ok(42), SniProbe.parse("""{"ok":true,"latencyMs":42}"""))
        assertEquals(SniProbe.Ok(7), SniProbe.parse("""{ "ok": true, "latencyMs": 7 }"""))
        assertEquals(SniProbe.Failed("timeout"), SniProbe.parse("""{"ok":false,"error":"timeout"}"""))
        assertTrue(SniProbe.parse("""{"ok":false}""") is SniProbe.Failed)
        assertTrue(SniProbe.parse("") is SniProbe.Failed)
    }
}
