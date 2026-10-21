package com.helboy.vpnclub.ui.proto.profiles

import com.helboy.vpnclub.ui.proto.ProtoKind
import dev.valid7996.narcicgeteway.domain.config.ProfileSource
import dev.valid7996.narcicgeteway.domain.config.Subscription
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileListLogicTest {
    private fun profile(
        id: String,
        name: String = id,
        protocol: String = "VLESS",
        address: String = "$id.example.com",
        port: Int = 443,
        sub: String = "",
        ping: Int? = null,
        cc: String? = null,
        transport: String = "ws",
    ) = VpnProfile(
        id = id, name = name, protocol = protocol, address = address, port = port, transportLabel = transport,
        source = ProfileSource.Tor, addedAt = 0L, subscriptionId = sub, lastPingMs = ping, countryCode = cc,
    )

    private fun sub(id: String, name: String) = Subscription(id = id, name = name, url = "https://x/$id")

    private val all = listOf(
        profile("a", "Alpha", "VLESS", sub = ""),
        profile("b", "Beta", "VMESS", sub = "s1"),
        profile("c", "Gamma", "TROJAN", address = "gamma.test", sub = "s1"),
        profile("d", "Delta", "VLESS", sub = "s2"),
    )

    @Test fun `latency buckets`() {
        assertEquals(LatencyBucket.Unknown, latencyBucket(null))
        assertEquals(LatencyBucket.Failed, latencyBucket(-1))
        assertEquals(LatencyBucket.Failed, latencyBucket(0))
        assertEquals(LatencyBucket.Fast, latencyBucket(120))
        assertEquals(LatencyBucket.Fast, latencyBucket(299))
        assertEquals(LatencyBucket.Medium, latencyBucket(300))
        assertEquals(LatencyBucket.Medium, latencyBucket(799))
        assertEquals(LatencyBucket.Slow, latencyBucket(800))
    }

    @Test fun `blank query matches everything`() {
        assertEquals(4, filterProfiles(all, "  ", GROUP_ALL).size)
    }

    @Test fun `query matches name address and protocol ignoring case`() {
        assertEquals(listOf("a"), filterProfiles(all, "alp", GROUP_ALL).map { it.id })
        assertEquals(listOf("c"), filterProfiles(all, "GAMMA.TEST", GROUP_ALL).map { it.id })
        assertEquals(listOf("b"), filterProfiles(all, "vmess", GROUP_ALL).map { it.id })
        assertEquals(listOf("a", "d"), filterProfiles(all, "vless", GROUP_ALL).map { it.id })
        assertTrue(filterProfiles(all, "nothing-here", GROUP_ALL).isEmpty())
    }

    @Test fun `group and query combine`() {
        assertEquals(listOf("b", "c"), filterProfiles(all, "", "s1").map { it.id })
        assertEquals(listOf("c"), filterProfiles(all, "trojan", "s1").map { it.id })
        assertEquals(listOf("a"), filterProfiles(all, "", GROUP_MANUAL).map { it.id })
    }

    @Test fun `chips carry counts`() {
        val chips = groupChips(all, listOf(sub("s1", "One"), sub("s2", "Two")), showAll = true)
        assertEquals(listOf("همه", "دستی", "One", "Two"), chips.map { it.label })
        assertEquals(listOf(4, 1, 2, 1), chips.map { it.count })
    }

    @Test fun `without groups only the all chip is offered`() {
        assertEquals(listOf(GROUP_ALL), groupChips(all, emptyList(), true).map { it.id })
    }

    @Test fun `all chip can be hidden`() {
        val chips = groupChips(all, listOf(sub("s1", "One")), showAll = false)
        assertEquals(listOf(GROUP_MANUAL, "s1"), chips.map { it.id })
    }

    @Test fun `a vanished group falls back to the first chip`() {
        val chips = groupChips(all, listOf(sub("s1", "One")), showAll = true)
        assertEquals("s1", effectiveGroup("s1", chips))
        assertEquals(GROUP_ALL, effectiveGroup("gone", chips))
    }

    @Test fun `sort puts fast first and failed last`() {
        val list = listOf(profile("x", ping = null), profile("y", ping = 300), profile("z", ping = -1), profile("w", ping = 50))
        assertEquals(listOf("w", "y", "x", "z"), sortedByLatency(list).map { it.id })
    }

    @Test fun `moveWithin moves and clamps`() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), moveWithin(ids, "a", 1))
        assertEquals(listOf("a", "c", "b"), moveWithin(ids, "c", -1))
        assertEquals(ids, moveWithin(ids, "a", -1))
        assertEquals(ids, moveWithin(ids, "c", 1))
        assertEquals(listOf("c", "a", "b"), moveWithin(ids, "c", -9))
        assertEquals(ids, moveWithin(ids, "missing", 1))
    }

    @Test fun `flags`() {
        assertEquals("🇩🇪", flagEmoji("de"))
        assertNull(flagEmoji("DEU"))
        assertNull(flagEmoji(null))
        assertEquals("DE", countryFromName("🇩🇪 Berlin"))
        assertNull(countryFromName("Berlin"))
        assertEquals("Berlin", cleanName("🇩🇪  Berlin"))
        assertEquals("🇩🇪", profileFlag(profile("f", cc = "DE")))
        assertEquals("🇫🇷", profileFlag(profile("g", name = "🇫🇷 Paris")))
        assertNull(profileFlag(profile("h")))
    }

    @Test fun `labels`() {
        assertEquals("VLESS · WS", protocolLabel(profile("a")))
        assertEquals("TOR", protocolLabel(profile("a", protocol = "Tor", transport = "tor")))
        assertEquals("a.example.com:443", addressLabel(profile("a")))
        assertEquals("—", addressLabel(profile("a", address = "-")))
        assertEquals("VLE", protocolBadge(profile("a")))
    }

    @Test fun `expiry text`() {
        val now = 1_000_000_000L
        assertNull(expiryLabel(null, now))
        assertEquals("منقضی شده", expiryLabel(now - 1, now))
        assertEquals("کمتر از یک دقیقه دیگر", expiryLabel(now + 30_000, now))
        assertEquals("5 دقیقه دیگر", expiryLabel(now + 5 * 60_000L, now))
        assertEquals("3 ساعت دیگر", expiryLabel(now + 3 * 3_600_000L, now))
        assertEquals("3 روز دیگر", expiryLabel(now + 3 * 86_400_000L, now))
    }

    @Test fun `bulk targets prefer the selection`() {
        assertEquals(4, bulkTargets(all, emptySet()).size)
        assertEquals(listOf("b"), bulkTargets(all, setOf("b", "zzz")).map { it.id })
    }

    @Test fun `every protocol kind has an add entry with text`() {
        assertEquals(ProtoKind.entries.toSet(), ADD_KIND_ENTRIES.map { it.kind }.toSet())
        assertEquals(ProtoKind.entries.size, ADD_KIND_ENTRIES.size)
        assertTrue(ADD_KIND_ENTRIES.all { it.title.isNotBlank() && it.hint.isNotBlank() })
        assertFalse(ADD_KIND_ENTRIES.any { it.hint.contains('\n') })
    }
}
