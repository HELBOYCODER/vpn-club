package com.helboy.vpnclub.ui.proto.editors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormBasicsTest {
    @Test fun `persian and arabic digits become ascii`() {
        assertEquals("443", FormText.asciiDigits("۴۴۳"))
        assertEquals("8080", FormText.asciiDigits("٨٠٨٠"))
        assertEquals("a1b", FormText.asciiDigits("a۱b"))
    }

    @Test fun `digitsOnly strips letters and caps the length`() {
        assertEquals("1234", FormText.digitsOnly("12ab34"))
        assertEquals("12345", FormText.digitsOnly("1234567", max = 5))
        assertEquals("22", FormText.digitsOnly("۲۲"))
    }

    @Test fun `decimalOnly keeps one dot`() {
        assertEquals("1.5", FormText.decimalOnly("1.5"))
        assertEquals("1.55", FormText.decimalOnly("1.5.5"))
        assertEquals("12", FormText.decimalOnly("1a2"))
    }

    @Test fun `port accepts only 1 to 65535`() {
        assertEquals(443, FormText.port("443"))
        assertEquals(65535, FormText.port(" 65535 "))
        assertNull(FormText.port("0"))
        assertNull(FormText.port("65536"))
        assertNull(FormText.port(""))
        assertNull(FormText.port("abc"))
        assertEquals(22, FormText.port("۲۲"))
    }

    @Test fun `items splits on lines commas and semicolons`() {
        assertEquals(listOf("a", "b", "c", "d"), FormText.items("a, b\nc;d\n\n"))
    }

    @Test fun `host and port`() {
        assertTrue(FormText.isHostPort("127.0.0.1:10808"))
        assertTrue(FormText.isHostPort("[::1]:1080"))
        assertFalse(FormText.isHostPort("127.0.0.1"))
        assertFalse(FormText.isHostPort("host:99999"))
        assertFalse(FormText.isHostPort("ho st:80"))
        assertFalse(FormText.isHostPort(":80"))
    }

    @Test fun `form check is ok only without errors`() {
        assertTrue(FormCheck().ok)
        assertTrue(FormCheck(warnings = mapOf("a" to "w")).ok)
        assertFalse(FormCheck(errors = mapOf("a" to "e")).ok)
    }
}
