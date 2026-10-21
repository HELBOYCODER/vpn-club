package com.helboy.vpnclub.ui.proto.tools

import dev.valid7996.narcicgeteway.domain.config.RulesetTransfer
import dev.valid7996.narcicgeteway.domain.model.RoutingPresetType
import dev.valid7996.narcicgeteway.domain.model.RulesetItem
import dev.valid7996.narcicgeteway.domain.model.RulesetPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleTransferLogicTest {
    private val a = RulesetItem(id = "a", remarks = "A", domain = listOf("domain:a.com"))
    private val locked = RulesetItem(id = "l", remarks = "L", ip = listOf("geoip:private"), locked = true)

    @Test fun blankTextIsNoPreviewAndGarbageIsInvalid() {
        assertNull(previewImport("  ", emptyList()))
        assertFalse(previewImport("not json", emptyList())!!.valid)
        assertFalse(previewImport("[]", emptyList())!!.valid)
    }

    @Test fun previewCountsDuplicatesAndLocked() {
        val text = RulesetTransfer.encode(listOf(a, RulesetItem(id = "z", port = "443")))
        val p = previewImport(text, listOf(a.copy(id = "other"), locked))!!
        assertTrue(p.valid)
        assertEquals(2, p.incoming.size)
        assertEquals(1, p.duplicates)
        assertEquals(1, p.fresh)
        assertEquals(1, p.keptLocked)
    }

    @Test fun appendSkipsDuplicatesReplaceKeepsLocked() {
        val incoming = listOf(a.copy(id = "n1"), RulesetItem(id = "n2", port = "22"))
        val appended = applyImport(listOf(a), incoming, ImportMode.Append)
        assertEquals(listOf("a", "n2"), appended.map { it.id })
        val replaced = applyImport(listOf(a, locked), incoming, ImportMode.Replace)
        assertEquals(listOf("l", "n1", "n2"), replaced.map { it.id })
    }

    @Test fun presetsAreAddedOnlyOnce() {
        val preset = RulesetPresets.blockAds()
        val once = addPreset(emptyList(), preset)
        assertEquals(1, once.size)
        assertEquals(0, presetFreshCount(once, preset))
        assertEquals(1, addPreset(once, preset).size)
    }

    @Test fun everyPresetHasAPersianLabel() {
        RulesetPresets.all().forEach { assertNotNull(presetLabel(it.label)); assertTrue(presetLabel(it.label) != it.label) }
        RoutingPresetType.entries.forEach { assertTrue(presetTypeLabel(it).isNotBlank()) }
    }
}
