package dev.valid7996.narcicgeteway.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MhrvRelayProfileTest {
    @Test
    fun `deployment URL and bare id both reduce to the id`() {
        val p = MhrvRelayProfile(scriptIds = "https://script.google.com/macros/s/AKfycbX1/exec\nAKfycbY2", authKey = "k")
        assertEquals(listOf("AKfycbX1", "AKfycbY2"), p.scriptIdList())
        assertTrue(p.isValid)
    }

    @Test
    fun `apps script and full mode need an id and a key, direct does not`() {
        assertFalse(MhrvRelayProfile(scriptIds = "AKfy").isValid)
        assertFalse(MhrvRelayProfile(authKey = "k").isValid)
        assertTrue(MhrvRelayProfile(mode = MhrvRelayProfile.MODE_DIRECT).isValid)
        assertTrue(MhrvRelayProfile(mode = MhrvRelayProfile.MODE_FULL, scriptIds = "AKfy", authKey = "k").isValid)
    }

    @Test
    fun `only apps script mode needs the CA`() {
        assertTrue(MhrvRelayProfile().needsCa)
        assertFalse(MhrvRelayProfile(mode = MhrvRelayProfile.MODE_FULL).needsCa)
    }

    @Test
    fun `core config carries ports ids and key`() {
        val json = MhrvRelayProfile(scriptIds = "AKfy", authKey = "secret")
            .toConfigJson(httpPort = 10900, socksPort = 10901)
        assertTrue(json, json.contains("\"listen_port\":10900"))
        assertTrue(json, json.contains("\"socks5_port\":10901"))
        assertTrue(json, json.contains("\"script_ids\":[\"AKfy\"]"))
        assertTrue(json, json.contains("\"auth_key\":\"secret\""))
        assertTrue(json, json.contains("\"mode\":\"apps_script\""))
    }

    @Test
    fun `pasted core json is read back`() {
        val p = MhrvRelayProfile.fromText(
            """{"mode":"full","script_ids":["AKfy","AKfz"],"auth_key":"k","google_ip":"1.2.3.4","verify_ssl":false}""",
        )
        assertNotNull(p)
        assertEquals(MhrvRelayProfile.MODE_FULL, p!!.mode)
        assertEquals(listOf("AKfy", "AKfz"), p.scriptIdList())
        assertEquals("1.2.3.4", p.googleIp)
        assertFalse(p.verifySsl)
    }

    @Test
    fun `text that is not an mhrv config is rejected`() {
        assertNull(MhrvRelayProfile.fromText("vless://abc"))
        assertNull(MhrvRelayProfile.fromText("""{"outbounds":[]}"""))
    }

    @Test
    fun `a profile survives the narcic share link`() {
        val src = ProfileSource.MhrvRelay(MhrvRelayProfile(scriptIds = "AKfy", authKey = "k"))
        val link = NrgLink.build("relay", src)!!
        val back = NrgLink.parse(link)!!.source as ProfileSource.MhrvRelay
        assertEquals(src.settings, back.settings)
    }
}
