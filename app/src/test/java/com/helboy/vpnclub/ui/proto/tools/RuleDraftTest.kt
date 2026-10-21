package com.helboy.vpnclub.ui.proto.tools

import dev.valid7996.narcicgeteway.domain.config.RuleValidation
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.domain.model.RulesetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleDraftTest {
    @Test fun splitEntriesTrimsAndDropsEmpties() {
        assertEquals(listOf("a.com", "b.com", "c"), splitEntries(" a.com, b.com\n\n c ,"))
        assertEquals(emptyList<String>(), splitEntries("  , \n "))
    }

    @Test fun draftRoundTripKeepsEverything() {
        val rule = RulesetItem(
            id = "r1", remarks = "x", outboundTag = RulesetItem.OUTBOUND_DIRECT, domain = listOf("domain:a.com", "geosite:ir"),
            ip = listOf("geoip:ir", "10.0.0.0/8"), port = "80,443", network = "tcp", protocol = listOf("tls"), enabled = false, locked = true,
        )
        val back = rule.toDraft().applyTo(rule)
        assertEquals(rule, back)
    }

    @Test fun applyKeepsIdAndEnabledAndLowercasesProtocols() {
        val base = RulesetItem(id = "keep", enabled = false)
        val out = RuleDraft(remarks = "  n ", protocol = "TLS, Http").applyTo(base)
        assertEquals("keep", out.id)
        assertFalse(out.enabled)
        assertEquals("n", out.remarks)
        assertEquals(listOf("tls", "http"), out.protocol)
    }

    @Test fun sameMatcherIgnoresIdAndRemarks() {
        val a = RulesetItem(id = "1", remarks = "a", domain = listOf("x"))
        val b = RulesetItem(id = "2", remarks = "b", domain = listOf("x"))
        assertTrue(a.sameMatcher(b))
        assertFalse(a.sameMatcher(b.copy(outboundTag = RulesetItem.OUTBOUND_BLOCK)))
    }

    @Test fun movedSwapsNeighboursAndIgnoresEdges() {
        val l = listOf(1, 2, 3)
        assertEquals(listOf(2, 1, 3), l.moved(1, -1))
        assertEquals(listOf(1, 3, 2), l.moved(1, 1))
        assertSame(l, l.moved(0, -1))
        assertSame(l, l.moved(2, 1))
        assertSame(l, l.moved(7, 1))
    }

    @Test fun outboundLabels() {
        assertEquals("مستقیم", outboundLabel(RulesetItem.OUTBOUND_DIRECT))
        assertEquals("مسدود", outboundLabel(RulesetItem.OUTBOUND_BLOCK))
        assertEquals("پروکسی", outboundLabel(RulesetItem.OUTBOUND_PROXY))
        assertEquals("سرور من", outboundLabel(RulesetItem.profileTag("p1"), listOf("p1" to "سرور من")))
        assertTrue(outboundLabel(RulesetItem.profileTag("gone")).contains("موجود نیست"))
    }

    @Test fun summaryShowsFirstEntriesAndCounts() {
        val r = RulesetItem(id = "1", domain = listOf("a", "b", "c"), ip = listOf("1.1.1.1"), port = "443", network = "udp", protocol = listOf("tls"))
        assertEquals("a، b، +1، 1.1.1.1، port:443، udp، tls", ruleSummary(r))
        assertEquals("بدون شرط", ruleSummary(RulesetItem(id = "2")))
    }

    @Test fun geoUseOnlyCountsEnabledRules() {
        val geo = RulesetItem(id = "1", ip = listOf("geoip:ir"))
        assertTrue(usesGeoData(listOf(geo)))
        assertFalse(usesGeoData(listOf(geo.copy(enabled = false))))
        assertFalse(usesGeoData(listOf(RulesetItem(id = "2", domain = listOf("domain:a.com")))))
    }

    @Test fun legacyRulesAreDetectedUntilMigrated() {
        val legacy = AppSettings(customDirectRules = "example.com")
        assertTrue(hasLegacyRules(legacy))
        assertEquals(1, effectiveRules(legacy).size)
        val migrated = legacy.withRules(effectiveRules(legacy))
        assertFalse(hasLegacyRules(migrated))
        assertEquals("", migrated.customDirectRules)
        assertEquals(1, migrated.rulesets.size)
        assertTrue(migrated.routingMigrated)
    }
}

class RuleMessagesTest {
    private fun ok() = RuleDraft(remarks = "n", domain = "domain:a.com")

    @Test fun validDraftPasses() = assertTrue(checkDraft(ok()).ok)

    @Test fun blankNameIsRejected() = assertNotNull(checkDraft(ok().copy(remarks = " ")).remarks)

    @Test fun geoipInDomainFieldIsExplained() {
        val e = checkDraft(ok().copy(domain = "geoip:ir"))
        assertEquals(RuleValidation.Problem.GeoIpInDomainField.message(), e.domain!!.substringAfter(": "))
        assertFalse(e.ok)
    }

    @Test fun geositeInIpFieldIsRejected() = assertNotNull(checkDraft(ok().copy(ip = "geosite:google")).ip)

    @Test fun badIpAndNegatedDomain() {
        assertNotNull(checkDraft(ok().copy(ip = "300.1.1.1")).ip)
        assertNotNull(checkDraft(ok().copy(domain = "!a.com")).domain)
        assertNull(checkDraft(ok().copy(ip = "!geoip:cn, 10.0.0.0/8, ::1/128")).ip)
    }

    @Test fun badRegexAndDotless() {
        assertNotNull(checkDraft(ok().copy(domain = "regexp:([")).domain)
        assertNotNull(checkDraft(ok().copy(domain = "dotless:a.b")).domain)
    }

    @Test fun portRules() {
        assertNull(checkDraft(ok().copy(port = "80,443,8000-9000")).port)
        assertNotNull(checkDraft(ok().copy(port = "99999")).port)
        assertNotNull(checkDraft(ok().copy(port = "9000-8000")).port)
    }

    @Test fun ruleNeedsAtLeastOneCondition() {
        val e = checkDraft(RuleDraft(remarks = "n"))
        assertEquals(MSG_NO_CONDITION, e.condition)
        assertFalse(e.ok)
        assertTrue(checkDraft(RuleDraft(remarks = "n", network = "udp")).ok)
    }

    @Test fun unknownProtocolIsFlaggedAndSniffingWarned() {
        assertNotNull(checkDraft(ok().copy(protocol = "gopher")).protocol)
        assertNull(checkDraft(ok().copy(protocol = "TLS, bittorrent")).protocol)
        assertTrue(needsSniffingWarning(ok().copy(protocol = "tls"), sniffingEnabled = false))
        assertFalse(needsSniffingWarning(ok().copy(protocol = "tls"), sniffingEnabled = true))
        assertFalse(needsSniffingWarning(ok(), sniffingEnabled = false))
    }

    @Test fun everyProblemHasAMessage() {
        RuleValidation.Problem.entries.forEach { assertTrue(it.message().isNotBlank()) }
    }
}
