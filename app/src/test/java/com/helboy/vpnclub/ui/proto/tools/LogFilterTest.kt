package com.helboy.vpnclub.ui.proto.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFilterTest {
    private val lines = listOf(
        "10-21 10:00:00.100 I/NrgVpnService(123): started",
        "10-21 10:00:01.200 W/GoLog(123): slow handshake",
        "10-21 10:00:02.300 E/Tor(123): circuit failed",
        "10-21 10:00:03.400 D/MyOwnTag(123): debug thing",
        "plain line without tag",
    )

    @Test fun parsesLevelAndTag() {
        val l = parseLogLine(lines[1])
        assertEquals('W', l.level)
        assertEquals("GoLog", l.tag)
        val plain = parseLogLine(lines[4])
        assertEquals('I', plain.level)
        assertNull(plain.tag)
    }

    @Test fun levelFilter() {
        assertEquals(5, filterLog(lines, LogViewLevel.All, "", null).size)
        assertEquals(4, filterLog(lines, LogViewLevel.Info, "", null).size)
        assertEquals(2, filterLog(lines, LogViewLevel.Warn, "", null).size)
        assertEquals(1, filterLog(lines, LogViewLevel.Error, "", null).size)
    }

    @Test fun searchIsCaseInsensitive() {
        assertEquals(1, filterLog(lines, LogViewLevel.All, "HANDSHAKE", null).size)
        assertEquals(0, filterLog(lines, LogViewLevel.All, "nothing", null).size)
    }

    @Test fun sourceFilterNeedsAKnownTag() {
        val xray = filterLog(lines, LogViewLevel.All, "", setOf(LogSource.Xray))
        assertEquals(listOf("GoLog"), xray.map { it.tag })
        val xrayAndApp = filterLog(lines, LogViewLevel.All, "", setOf(LogSource.Xray, LogSource.App))
        assertEquals(listOf("NrgVpnService", "GoLog"), xrayAndApp.map { it.tag })
    }

    @Test fun ownTagsCountAsAppUnlessAnotherSourceClaimsThem() {
        val out = filterLog(lines, LogViewLevel.All, "", setOf(LogSource.App), ownTags = setOf("MyOwnTag", "Tor"))
        assertTrue(out.any { it.tag == "MyOwnTag" })
        assertTrue(out.none { it.tag == "Tor" })
    }

    @Test fun sessionSourcesFollowTheRunningKind() {
        assertTrue(sourcesForKind(null).isEmpty())
        assertEquals(setOf(LogSource.Tor), sourcesForKind("tor"))
        assertTrue(LogSource.Xray in sourcesForKind("ssh"))
        assertEquals(LogSource.entries.toSet(), sourcesForKind("cross_chain"))
    }

    @Test fun textAndFileName() {
        assertEquals("a\nb", logText(listOf(parseLogLine("a"), parseLogLine("b"))))
        assertTrue(logFileName(0L).startsWith("hellboy-log-") && logFileName(0L).endsWith(".txt"))
    }
}
