package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.domain.config.PsiphonConfigBuilder
import dev.valid7996.narcicgeteway.domain.config.PsiphonProfile
import dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig
import dev.valid7996.narcicgeteway.domain.config.SniSpoofProfile
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SpoofFormsTest {
    @Test fun `psiphon is valid with nothing filled in`() {
        val f = PsiphonForm()
        assertTrue(f.check().ok)
        assertTrue(f.check().warnings.isEmpty())
        assertTrue(f.toProfile().beast)
        assertEquals(PsiphonConfigBuilder.MODE_AUTO, f.toProfile().mode)
    }

    @Test fun `psiphon warns about fronting lists nothing in them can use`() {
        assertNotNull(PsiphonForm(cdnIps = "not-an-ip").check().warnings[PsiphonForm.F_IPS])
        assertNull(PsiphonForm(cdnIps = "104.16.35.164, 10.0.0.0/8").check().warnings[PsiphonForm.F_IPS])
        assertNotNull(PsiphonForm(cdnIps = "104.16.35.164 oops").check().warnings[PsiphonForm.F_IPS])
        assertNotNull(PsiphonForm(cdnSni = "localhost").check().warnings[PsiphonForm.F_SNI])
        assertNull(PsiphonForm(cdnSni = "cdn.example.com").check().warnings[PsiphonForm.F_SNI])
        assertNotNull(PsiphonForm(country = "ZZ").check().warnings[PsiphonForm.F_COUNTRY])
        assertNull(PsiphonForm(country = "nl").check().warnings[PsiphonForm.F_COUNTRY])
    }

    @Test fun `psiphon round trip and country normalisation`() {
        val p = PsiphonProfile(country = "NL", mode = PsiphonConfigBuilder.MODE_CDN, cdnIps = "1.2.3.4", cdnSni = "a.example.com", beast = false)
        assertEquals(p, PsiphonForm.from(p, "n").toProfile())
        assertEquals("NL", PsiphonForm(country = " nl ").toProfile().country)
        assertEquals(PsiphonConfigBuilder.MODE_AUTO, PsiphonForm(mode = "weird").toProfile().mode)
    }

    @Test fun `country list is searchable by name and by code`() {
        assertTrue("NL" in PsiphonForm.filterCountries("nl", Locale.ENGLISH))
        assertTrue("DE" in PsiphonForm.filterCountries("germany", Locale.ENGLISH))
        assertTrue(PsiphonForm.filterCountries("zzzz", Locale.ENGLISH).isEmpty())
        assertTrue(PsiphonForm.filterCountries("", Locale.ENGLISH).size > 20)
        assertEquals("Germany", PsiphonForm.countryName("DE", Locale.ENGLISH))
    }

    @Test fun `sni spoof defaults are valid`() {
        val f = SniSpoofForm()
        assertTrue(f.check().ok)
        val p = f.toProfile()
        assertTrue(p.noRoot)
        assertEquals(SniSpoofConfig.DEFAULT_CONNECT_PORT, p.connectPort)
    }

    @Test fun `sni spoof needs an ip, a valid port and a fake sni`() {
        val c = SniSpoofForm(ip = "", port = "0", fakeSni = "").check()
        assertNotNull(c.errors[SniSpoofForm.F_IP])
        assertNotNull(c.errors[SniSpoofForm.F_PORT])
        assertNotNull(c.errors[SniSpoofForm.F_SNI])
        assertNotNull(SniSpoofForm(ip = "1.2.3 4").check().errors[SniSpoofForm.F_IP])
        assertNotNull(SniSpoofForm(port = "99999").check().errors[SniSpoofForm.F_PORT])
    }

    @Test fun `sni spoof keeps the link and normalises the method`() {
        val stored = SniSpoofProfile(link = "vless://x", fakeSni = "a.example.com", cleanIp = "1.2.3.4", noRoot = true, method = "fragment", connectPort = 8443)
        val f = SniSpoofForm.from(stored, "mine")
        assertEquals("mine", f.name)
        assertEquals("8443", f.port)
        assertEquals(stored, f.toProfile())
        assertEquals(SniSpoofConfig.method("nonsense"), SniSpoofForm(method = "nonsense").toProfile().method)
    }

    @Test fun `sni spoof falls back to the engine defaults for blank stored values`() {
        val blank = SniSpoofProfile(link = "", fakeSni = "", cleanIp = "", noRoot = true, method = "")
        val defaults = SniSpoofProfile(link = "", fakeSni = "d.example.com", cleanIp = "9.9.9.9", noRoot = true, method = "oob", connectPort = 2053)
        val f = SniSpoofForm.from(blank, "", defaults)
        assertEquals("9.9.9.9", f.ip)
        assertEquals("2053", f.port)
        assertEquals("d.example.com", f.fakeSni)
        assertEquals("oob", f.method)
        assertEquals("SNI Spoof", f.name)
    }

    @Test fun `engine settings are applied to the app settings and nothing else`() {
        val s = AppSettings()
        val f = SpoofEngineForm.from(s)
        assertTrue(f.sameAs(s))
        val changed = f.copy(ip = "1.1.1.1", port = "8443", fakeSni = "x.example.com", method = "oob").applyTo(s)
        assertEquals("1.1.1.1", changed.rstaSpoofConnectIp)
        assertEquals(8443, changed.rstaSpoofConnectPort)
        assertEquals("x.example.com", changed.rstaSpoofFakeSni)
        assertEquals("oob", changed.rstaSpoofMethod)
        assertEquals(s.copy(rstaSpoofConnectIp = "1.1.1.1", rstaSpoofConnectPort = 8443, rstaSpoofFakeSni = "x.example.com", rstaSpoofMethod = "oob"), changed)
        assertFalse(f.copy(ip = "1.1.1.1").sameAs(s))
    }

    @Test fun `engine settings validate and fall back like the reference sheet`() {
        val bad = SpoofEngineForm(ip = "", port = "0", fakeSni = " ", method = "x")
        assertFalse(bad.check().ok)
        val applied = bad.applyTo(AppSettings())
        assertEquals(SniSpoofConfig.DEFAULT_CONNECT_IP, applied.rstaSpoofConnectIp)
        assertEquals(SniSpoofConfig.DEFAULT_CONNECT_PORT, applied.rstaSpoofConnectPort)
        assertEquals(SniSpoofConfig.DEFAULT_FAKE_SNI, applied.rstaSpoofFakeSni)
    }

    @Test fun `engine status lines are read into a tone and a text`() {
        assertEquals(SpoofStatusText.Tone.Active, SpoofStatusText.tone("active -> 1.2.3.4:443 (sni=a, method=b)"))
        assertEquals(SpoofStatusText.Tone.Failed, SpoofStatusText.tone("failed: bind"))
        assertEquals(SpoofStatusText.Tone.Idle, SpoofStatusText.tone("inactive"))
        assertEquals(SpoofStatusText.Tone.Idle, SpoofStatusText.tone(""))
        assertTrue(SpoofStatusText.describe("active -> 1.2.3.4:443").contains("1.2.3.4:443"))
        assertTrue(SpoofStatusText.describe("failed: bind").endsWith("bind"))
        assertTrue(SpoofStatusText.describe("").isNotBlank())
    }
}
