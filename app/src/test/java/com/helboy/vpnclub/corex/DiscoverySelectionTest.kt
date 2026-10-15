package com.helboy.vpnclub.corex

import org.junit.Assert.*
import org.junit.Test

class DiscoverySelectionTest {
    @Test fun hugePublicFeedCannotCrowdOutVerifiedOrPreviouslyWorkingServers() {
        val selected = DiscoverySelection.select(listOf("saved"), listOf("verified"),
            (1..20_000).map { "public-$it" }, 2_500) { it }
        assertEquals("saved", selected.first())
        assertTrue("verified" in selected)
        assertEquals(2_500, selected.size)
        assertTrue("public-2498" in selected)
    }
    @Test fun bothFeedClassesReachTunnelTestingInsteadOfOnlyFirstFeed() {
        val selected = DiscoverySelection.select(emptyList<String>(), (1..1500).map { "v$it" },
            (1..1500).map { "p$it" }, 400) { it }
        assertEquals(200, selected.count { it.startsWith("v") })
        assertEquals(200, selected.count { it.startsWith("p") })
    }
    @Test fun duplicatesKeepSuccessfulDialProfileAndFillRemainingCapacity() {
        data class Entry(val key: String, val dial: String)
        val selected = DiscoverySelection.select(listOf(Entry("a", "successful-dial")),
            listOf(Entry("a", "original")), listOf(Entry("b", "other")), 2) { it.key }
        assertEquals(listOf(Entry("a", "successful-dial"), Entry("b", "other")), selected)
    }
}
