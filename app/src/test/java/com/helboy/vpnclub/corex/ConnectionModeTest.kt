package com.helboy.vpnclub.corex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionModeTest {
    @Test fun unknownSavedModeFallsBackToAuto() {
        assertEquals(ConnectionMode.AUTO, ConnectionMode.fromId(null))
        assertEquals(ConnectionMode.AUTO, ConnectionMode.fromId("masque"))
    }

    @Test fun autoMatchesOnlyInstalledCoreProtocols() {
        for (scheme in listOf("vless", "vmess", "trojan", "ss", "hysteria2", "hy2")) {
            assertTrue(ConnectionMode.AUTO.accepts(scheme))
        }
        for (scheme in listOf("wg", "wireguard", "psiphon", "tor", "masque", "socks")) {
            assertFalse(ConnectionMode.AUTO.accepts(scheme))
        }
    }

    @Test fun individualModesDoNotSilentlySwitchToOtherProtocols() {
        assertTrue(ConnectionMode.VLESS.accepts("vless"))
        assertFalse(ConnectionMode.VLESS.accepts("trojan"))
        assertTrue(ConnectionMode.SHADOWSOCKS.accepts("ss"))
        assertFalse(ConnectionMode.SHADOWSOCKS.accepts("vmess"))
        assertTrue(ConnectionMode.HYSTERIA2.accepts("hy2"))
        assertTrue(ConnectionMode.HYSTERIA2.accepts("hysteria2"))
        assertFalse(ConnectionMode.HYSTERIA2.accepts("vless"))
    }
}
