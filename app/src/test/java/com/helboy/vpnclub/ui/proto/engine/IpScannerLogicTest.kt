package com.helboy.vpnclub.ui.proto.engine

import dev.valid7996.narcicgeteway.data.tools.ScanResult
import dev.valid7996.narcicgeteway.data.tools.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpScannerLogicTest {
    private fun ok(ip: String, ms: Int, st: Status) = ScanResult(ip, ms, st, domain = "d.example")
    private fun dead(ip: String) = ScanResult(ip, 999, Status.RED)

    @Test fun rangesAreSplitOnAnySeparatorAndDeduplicated() {
        val p = IpScannerLogic.parseRanges("104.16.0.0/13, 172.64.0.0/13\n104.16.0.0/13;1.1.1.1 1.1.1.1/32")
        assertEquals(listOf("104.16.0.0/13", "172.64.0.0/13", "1.1.1.1"), p.ranges)
        assertTrue(p.invalid.isEmpty())
    }

    @Test fun badRangesAreReportedNotSilentlyDropped() {
        val p = IpScannerLogic.parseRanges("300.1.1.1 1.2.3.4/40 1.2.3.4/4 hello 1.2.3 8.8.8.8")
        assertEquals(listOf("8.8.8.8"), p.ranges)
        assertEquals(5, p.invalid.size)
    }

    @Test fun blankTextMeansNoRangesAndNoErrors() {
        val p = IpScannerLogic.parseRanges("  \n ")
        assertTrue(p.ranges.isEmpty())
        assertTrue(p.invalid.isEmpty())
    }

    @Test fun portAndHostChecks() {
        assertEquals(Checked.Ok(8443), IpScannerLogic.checkPort("8443"))
        assertTrue(IpScannerLogic.checkPort("0") is Checked.Bad)
        assertTrue(IpScannerLogic.checkPort("70000") is Checked.Bad)
        assertNull(IpScannerLogic.checkHost(""))
        assertNull(IpScannerLogic.checkHost("speed.cloudflare.com"))
        assertNotNull(IpScannerLogic.checkHost("bad host"))
        assertNotNull(IpScannerLogic.checkHost("-bad.com"))
    }

    @Test fun expectedCountFollowsRangeSize() {
        assertEquals(1, IpScannerLogic.expected(listOf("1.1.1.1")))
        assertEquals(150, IpScannerLogic.expected(listOf("104.16.0.0/13")))
        assertEquals(2, IpScannerLogic.expected(listOf("10.0.0.0/31")))
        assertEquals(151, IpScannerLogic.expected(listOf("104.16.0.0/13", "9.9.9.9")))
    }

    @Test fun resultsAreRankedGreenFirstByLatencyAndDeadLast() {
        val list = listOf(dead("9.9.9.9"), ok("1.1.1.2", 400, Status.YELLOW), ok("1.1.1.3", 90, Status.GREEN), ok("1.1.1.4", 50, Status.GREEN))
        assertEquals(listOf("1.1.1.4", "1.1.1.3", "1.1.1.2", "9.9.9.9"), IpScannerLogic.sorted(list).map { it.ip })
        assertEquals(listOf("1.1.1.4", "1.1.1.3"), IpScannerLogic.green(list).map { it.ip })
        assertEquals(3, IpScannerLogic.top(list).size)
    }

    @Test fun anAddressSeenTwiceKeepsTheLatestAnswer() {
        val first = listOf(ok("1.1.1.1", 300, Status.YELLOW))
        val merged = IpScannerLogic.merge(first, ok("1.1.1.1", 80, Status.GREEN))
        assertEquals(1, merged.size)
        assertEquals(80, merged.first().latency)
        assertEquals(2, IpScannerLogic.merge(merged, ok("2.2.2.2", 10, Status.GREEN)).size)
    }

    @Test fun labelsAndSummary() {
        assertEquals("بدون پاسخ", IpScannerLogic.statusLabel(dead("1.1.1.1")))
        assertEquals("عالی", IpScannerLogic.statusLabel(ok("1.1.1.1", 10, Status.GREEN)))
        assertEquals("—", IpScannerLogic.latencyLabel(dead("1.1.1.1")))
        assertEquals("1.1.1.1\n2.2.2.2", IpScannerLogic.ipLines(listOf(ok("1.1.1.1", 1, Status.GREEN), ok("2.2.2.2", 1, Status.GREEN))))
        assertTrue(IpScannerLogic.summary(emptyList()).isNotBlank())
    }
}
