package com.helboy.vpnclub.ui.proto.tools

import dev.valid7996.narcicgeteway.ui.platform.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterTest {
    private val apps = listOf(
        InstalledApp("org.telegram.messenger", "Telegram", false),
        InstalledApp("com.android.chrome", "Chrome", false),
        InstalledApp("com.android.settings", "Settings", true),
        InstalledApp("com.example.zed", "zed", false),
    )

    @Test fun modeFlagsRoundTrip() {
        PerAppMode.entries.forEach { m ->
            val (enabled, bypass) = m.toFlags()
            assertEquals(m, perAppMode(enabled, bypass))
        }
    }

    @Test fun systemAppsAreHiddenUnlessAsked() {
        assertEquals(listOf("Chrome", "Telegram", "zed"), filterApps(apps, "", showSystem = false).map { it.label })
        assertTrue(filterApps(apps, "", showSystem = true).any { it.isSystem })
    }

    @Test fun searchMatchesLabelAndPackageIgnoringCase() {
        assertEquals(listOf("Telegram"), filterApps(apps, "TELE", true).map { it.label })
        assertEquals(listOf("Chrome", "Settings"), filterApps(apps, "com.android", true).map { it.label })
    }

    @Test fun pinnedAppsComeFirst() {
        val out = filterApps(apps, "", false, pinned = setOf("com.example.zed"))
        assertEquals("zed", out.first().label)
    }

    @Test fun bulkOperationsOnlyTouchShownApps() {
        val shown = apps.take(2)
        val start = setOf("com.example.zed", "org.telegram.messenger")
        assertEquals(start + "com.android.chrome", selectAll(start, shown))
        assertEquals(setOf("com.example.zed"), selectNone(start, shown))
        assertEquals(setOf("com.example.zed", "com.android.chrome"), invert(start, shown))
        assertEquals(setOf("a"), toggle(emptySet(), "a"))
        assertEquals(emptySet<String>(), toggle(setOf("a"), "a"))
    }

    @Test fun packageListParsing() {
        assertEquals(setOf("a.b", "c.d"), parsePackageList("a.b\n c.d ,a.b\nnot a package"))
        assertEquals("a.b\nc.d", exportPackageList(setOf("c.d", "a.b")))
    }

    @Test fun staleSelectionIsWhatIsNotInstalled() {
        assertEquals(setOf("gone.app"), staleSelection(apps, setOf("gone.app", "com.android.chrome")))
    }
}
