package com.helboy.vpnclub.corex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** قانون «کدام سرور IP تمیز کلادفلر می‌گیرد» + اینکه تزریق آدرس dial را عوض می‌کند و SNI را نه. */
class InjectEligibilityTest {

    private fun cfg(link: String) = LinkParser.parse(link)!!

    @Test
    fun cfWebsocketTlsIsInjectable() {
        val c = cfg("vless://11111111-1111-1111-1111-111111111111@sub.example.com:443?security=tls&type=ws&host=sub.example.com&path=%2F#x")
        assertTrue(ServerEntry(c, cf = true).canInject)
    }

    @Test
    fun realityIsNeverInjectable() {
        val c = cfg("vless://11111111-1111-1111-1111-111111111111@1.2.3.4:443?security=reality&type=tcp&pbk=abc&sni=yahoo.com#x")
        val e = ServerEntry(c, cf = true)
        assertFalse(e.canInject)
        assertTrue(e.injectBlocker!!.contains("Reality"))
    }

    @Test
    fun nonCfIsNotInjectable() {
        val c = cfg("vless://11111111-1111-1111-1111-111111111111@sub.example.com:443?security=tls&type=ws#x")
        assertFalse(ServerEntry(c, cf = false).canInject)
    }

    @Test
    fun injectionChangesDialButKeepsSni() {
        val c = cfg("vless://11111111-1111-1111-1111-111111111111@sub.example.com:443?security=tls&type=ws&sni=sub.example.com#x")
        val inj = c.withDial("104.16.1.1", 443)
        assertEquals("104.16.1.1", inj.dialHost)
        assertEquals("sub.example.com", inj.sniHost)
        assertEquals(ServerEntry(c).serverKey, ServerEntry(inj).serverKey) // همان سرور ← اتصال زنده جایگزین می‌شود
        assertTrue(ServerEntry(inj).injected)
    }
}
