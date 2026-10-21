package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.domain.config.Ikev2Auth
import dev.valid7996.narcicgeteway.domain.config.Ikev2Profile
import dev.valid7996.narcicgeteway.domain.config.OpenConnectProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelFormsTest {
    private val eap = Ikev2Form(server = "vpn.example.com", username = "u", password = "pw")

    @Test fun `ikev2 eap needs a user and a password`() {
        val c = Ikev2Form(server = "vpn.example.com").check()
        assertNotNull(c.errors[Ikev2Form.F_USER])
        assertNotNull(c.errors[Ikev2Form.F_PASSWORD])
        assertTrue(eap.check().ok)
    }

    @Test fun `ikev2 stored secrets satisfy the check when the field stays empty`() {
        val stored = eap.copy(password = "", storedPassword = "zsx:abc")
        assertTrue(stored.hasStoredPassword)
        assertTrue(stored.check().ok)
        assertEquals("zsx:abc", stored.toProfile().password)
    }

    @Test fun `ikev2 psk and certificate rules`() {
        val psk = Ikev2Form(server = "s", authType = Ikev2Auth.PSK)
        assertNotNull(psk.check().errors[Ikev2Form.F_PSK])
        assertTrue(psk.copy(psk = "key").check().ok)
        val cert = Ikev2Form(server = "s", authType = Ikev2Auth.CERTIFICATE)
        assertNotNull(cert.check().errors[Ikev2Form.F_CERT])
        assertTrue(cert.copy(userCertAlias = "alias").check().ok)
    }

    @Test fun `ikev2 check agrees with the profile's own isValid`() {
        listOf(
            eap, eap.copy(server = ""), eap.copy(username = ""), eap.copy(password = ""),
            Ikev2Form(server = "s", authType = Ikev2Auth.PSK, psk = "k"), Ikev2Form(server = "s", authType = Ikev2Auth.PSK),
            Ikev2Form(server = "s", authType = Ikev2Auth.CERTIFICATE, userCertAlias = "a"), Ikev2Form(server = "s", authType = Ikev2Auth.CERTIFICATE),
        ).forEach { assertEquals(it.toString(), it.toProfile().isValid, it.check().ok) }
    }

    @Test fun `ikev2 proposals are checked with the platform parser`() {
        val parser: (String, Boolean) -> Boolean = { spec, child -> spec == "aes256-sha256" && child || spec == "aes256-sha256-modp2048" && !child }
        val ok = eap.copy(ikeProposal = "aes256-sha256-modp2048", espProposal = "aes256-sha256")
        assertTrue(ok.check(parser).ok)
        assertNotNull(eap.copy(ikeProposal = "bogus").check(parser).errors[Ikev2Form.F_IKE])
        assertNotNull(eap.copy(espProposal = "bogus").check(parser).errors[Ikev2Form.F_ESP])
        assertTrue("without a parser nothing is rejected", eap.copy(ikeProposal = "bogus").check(null).ok)
    }

    @Test fun `ikev2 mtu is clamped and warned about`() {
        assertEquals(1500, eap.copy(mtu = "9000").toProfile().mtu)
        assertEquals(1280, eap.copy(mtu = "100").toProfile().mtu)
        assertEquals(1400, eap.copy(mtu = "").toProfile().mtu)
        assertNotNull(eap.copy(mtu = "9000").check().warnings[Ikev2Form.F_MTU])
    }

    @Test fun `ikev2 eap tls shows up as certificate`() {
        val p = Ikev2Profile(server = "s", authType = Ikev2Auth.EAP_TLS, userCertAlias = "a")
        assertEquals(Ikev2Auth.CERTIFICATE, Ikev2Form.from(p).authType)
    }

    private val oc = OpenConnectForm(server = "vpn.example.com")

    @Test fun `openconnect needs only a server`() {
        assertNotNull(OpenConnectForm().check().errors[OpenConnectForm.F_SERVER])
        assertTrue(oc.check().ok)
        assertEquals(oc.toProfile().isValid, oc.check().ok)
        assertEquals(OpenConnectForm().toProfile().isValid, OpenConnectForm().check().ok)
    }

    @Test fun `openconnect port is joined to the server`() {
        assertEquals("vpn.example.com:8443", oc.copy(port = "8443").toProfile().server)
        assertEquals("vpn.example.com", oc.copy(port = "").toProfile().server)
        assertNotNull(oc.copy(port = "70000").check().errors[OpenConnectForm.F_PORT])
    }

    @Test fun `openconnect splits a stored port out again`() {
        val f = OpenConnectForm.from(OpenConnectProfile(server = "https://vpn.example.com:4443/path"))
        assertEquals("https://vpn.example.com/path", f.server)
        assertEquals("4443", f.port)
        assertEquals("https://vpn.example.com:4443/path", f.toProfile().server)
    }

    @Test fun `openconnect numbers and defaults`() {
        assertNotNull(oc.copy(mtu = "x").check().errors[OpenConnectForm.F_MTU])
        assertEquals(OpenConnectProfile.DEFAULT_RECONNECT_TIMEOUT, oc.copy(reconnect = "0").toProfile().reconnectTimeoutSec)
        assertEquals(0, oc.copy(mtu = "").toProfile().mtu)
        assertEquals("android", oc.copy(reportedOs = " ").toProfile().reportedOs)
    }

    @Test fun `openconnect warns about a token mode without a secret and a half client certificate`() {
        assertNotNull(oc.copy(tokenMode = OpenConnectProfile.TOKEN_TOTP).check().warnings[OpenConnectForm.F_TOKEN])
        assertNull(oc.copy(tokenMode = OpenConnectProfile.TOKEN_TOTP, tokenSecret = "S").check().warnings[OpenConnectForm.F_TOKEN])
        assertNotNull(oc.copy(clientCert = "CERT").check().warnings[OpenConnectForm.F_CLIENT_CERT])
        assertNull(oc.copy(clientCert = "CERT", certP12Base64 = "AAAA").check().warnings[OpenConnectForm.F_CLIENT_CERT])
    }

    @Test fun `openconnect round trip keeps the gateway form answers`() {
        val p = OpenConnectProfile(
            server = "vpn.example.com:8443", protocol = OpenConnectProfile.PROTO_GP, username = "u", password = "p", authgroup = "g",
            caCertPem = "CA", serverCertSha256 = "sha256:ab", clientCertPem = "C", clientKeyPem = "K", clientKeyPassword = "kp",
            userAgent = "ua", reportedOs = "linux", tokenMode = OpenConnectProfile.TOKEN_TOTP, tokenSecret = "s", disableDtls = true,
            clientCertP12Base64 = "AAAA", sni = "x.example.com", mtu = 1300, reconnectTimeoutSec = 120, proxy = "socks5://h:1",
            disableIpv6 = true, formEntries = mapOf("a" to "b"),
        )
        assertEquals(p, OpenConnectForm.from(p, "n").toProfile())
        assertFalse(OpenConnectForm.from(p).keptFormEntries.isEmpty())
    }
}
