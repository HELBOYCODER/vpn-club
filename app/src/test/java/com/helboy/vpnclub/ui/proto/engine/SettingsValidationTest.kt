package com.helboy.vpnclub.ui.proto.engine

import dev.valid7996.narcicgeteway.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValidationTest {
    @Test fun intInsideBoundsIsAccepted() {
        assertEquals(Checked.Ok(1500), SettingsValidation.checkInt(" 1500 ", 1000, 9000))
    }

    @Test fun intOutsideBoundsOrNotANumberIsRejectedWithAMessage() {
        assertTrue(SettingsValidation.checkInt("999", 1000, 9000) is Checked.Bad)
        assertTrue(SettingsValidation.checkInt("9001", 1000, 9000) is Checked.Bad)
        assertEquals(Checked.Bad(SettingsValidation.NOT_A_NUMBER), SettingsValidation.checkInt("abc", 1, 5))
        assertEquals(Checked.Bad(SettingsValidation.NOT_A_NUMBER), SettingsValidation.checkInt("", 1, 5))
    }

    @Test fun rangeNeedsOrderedWholeNumbers() {
        assertNull(SettingsValidation.checkRange("50-100"))
        assertNull(SettingsValidation.checkRange("10"))
        assertNotNull(SettingsValidation.checkRange("100-50"))
        assertNotNull(SettingsValidation.checkRange("a-b"))
        assertNotNull(SettingsValidation.checkRange("1-2-3"))
        assertNotNull(SettingsValidation.checkRange(""))
    }

    @Test fun lanPortMustBeUsableAndNotInternal() {
        assertNull(SettingsValidation.checkLanPort(10880))
        assertNotNull(SettingsValidation.checkLanPort(80))
        assertNotNull(SettingsValidation.checkLanPort(10808)) // the engine's own SOCKS port
        assertNotNull(SettingsValidation.checkLanPort(10807)) // port + 1 would hit 10808
        assertNotNull(SettingsValidation.checkLanPort(65535))
    }

    @Test fun urlNeedsSchemeAndNoSpaces() {
        assertNull(SettingsValidation.checkUrl("https://www.gstatic.com/generate_204"))
        assertNotNull(SettingsValidation.checkUrl("www.gstatic.com"))
        assertNotNull(SettingsValidation.checkUrl("https://a b"))
        assertNotNull(SettingsValidation.checkUrl("  "))
    }

    @Test fun hevTimeoutIsTwoPositiveNumbers() {
        assertNull(SettingsValidation.checkHevTimeout("300,60"))
        assertNull(SettingsValidation.checkHevTimeout(" 10 , 5 "))
        assertNotNull(SettingsValidation.checkHevTimeout("300"))
        assertNotNull(SettingsValidation.checkHevTimeout("0,5"))
        assertNotNull(SettingsValidation.checkHevTimeout("a,b"))
    }

    @Test fun portTextBounds() {
        assertNull(SettingsValidation.checkPortText(443))
        assertNotNull(SettingsValidation.checkPortText(0))
        assertNotNull(SettingsValidation.checkPortText(70000))
    }

    @Test fun tokenHasRequestedLengthAndOnlySafeCharacters() {
        val t = SettingsValidation.randomToken(20)
        assertEquals(20, t.length)
        assertTrue(t.all { it.isLetterOrDigit() })
        assertTrue(SettingsValidation.randomToken() != SettingsValidation.randomToken())
    }

    @Test fun tunEngineRoundTripsThroughTheTwoFlags() {
        val base = AppSettings()
        TunEngine.entries.forEach { e ->
            assertEquals(e, TunEngine.of(e.applyTo(base)))
        }
        assertEquals(TunEngine.Xray, TunEngine.of(AppSettings(useHevTun = false, useZepTun = true)))
        assertEquals(TunEngine.Hev, TunEngine.of(AppSettings(useHevTun = true, useZepTun = false)))
        assertEquals(TunEngine.Zep, TunEngine.of(AppSettings(useHevTun = true, useZepTun = true)))
    }

    @Test fun lanShareLinesListEveryAddressAndOptionalHttp() {
        assertTrue(LanShare.lines(emptyList(), 10880, true, "nrg").isEmpty())
        val lines = LanShare.lines(listOf("192.168.1.5"), 10880, true, "nrg")
        assertEquals(listOf("SOCKS5  192.168.1.5:10880", "HTTP  192.168.1.5:10881", "نام کاربری: nrg"), lines)
        assertEquals(2, LanShare.lines(listOf("192.168.1.5"), 10880, false, "nrg").size)
    }
}
