package com.helboy.vpnclub.ui.proto.tools

import dev.valid7996.narcicgeteway.data.backup.BackupManager
import dev.valid7996.narcicgeteway.data.backup.BackupPayload
import dev.valid7996.narcicgeteway.domain.config.ProfileSource
import dev.valid7996.narcicgeteway.domain.config.Subscription
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupLogicTest {
    private fun profile(id: String) = VpnProfile(id, id, "vless", "1.2.3.4", 443, "tcp", ProfileSource.Link("vless://x@1.2.3.4:443#$id"), 0L)
    private fun sub(id: String, url: String) = Subscription(id = id, name = id, url = url)

    private val payload = BackupPayload(
        settings = AppSettings(),
        profiles = listOf(profile("a"), profile("b"), profile("c")),
        subscriptions = listOf(sub("s1", "https://x.test/1"), sub("s2", "https://X.test/2")),
    )

    @Test fun previewCountsOnlyWhatWouldBeAdded() {
        val p = previewOf(payload, listOf(profile("a")), listOf(sub("old", "https://x.test/2")))
        assertEquals(3, p.profiles)
        assertEquals(2, p.newProfiles)
        assertEquals(2, p.subscriptions)
        assertEquals(1, p.newSubscriptions) // s2 has the same url as an existing one (case-insensitive)
        assertTrue(p.hasSettings)
        assertFalse(p.isEmpty)
    }

    @Test fun emptyBackupIsEmpty() {
        val p = previewOf(BackupPayload(), emptyList(), emptyList())
        assertTrue(p.isEmpty)
    }

    @Test fun descriptionsDifferForReplace() {
        val p = previewOf(payload, listOf(profile("a")), emptyList())
        val add = describeRestore(p, RestoreChoice()).joinToString("\n")
        val replace = describeRestore(p, RestoreChoice(replace = true)).joinToString("\n")
        assertTrue(add.contains("اضافه می‌شود"))
        assertTrue(replace.contains("جایگزین می‌شود"))
        assertEquals(listOf("هیچ بخشی انتخاب نشده است."), describeRestore(p, RestoreChoice(false, false, false)))
    }

    @Test fun destructiveWhenSettingsOrReplacingExistingData() {
        val p = previewOf(payload, listOf(profile("a")), emptyList())
        assertTrue(isDestructive(p, RestoreChoice()))
        assertFalse(isDestructive(p, RestoreChoice(settings = false)))
        assertTrue(isDestructive(p, RestoreChoice(settings = false, replace = true)))
        val nothingNow = previewOf(payload, emptyList(), emptyList())
        assertFalse(isDestructive(nothingNow, RestoreChoice(settings = false, replace = true)))
    }

    @Test fun summaryNamesWhatChanged() {
        assertEquals("چیز تازه‌ای برای بازیابی نبود.", restoreSummary(false, 0, 0))
        assertTrue(restoreSummary(true, 2, 1).contains("تنظیمات"))
        assertTrue(restoreSummary(false, 2, 0).contains("۲ سرور"))
    }

    @Test fun exportedBackupParsesBack() {
        val text = BackupManager.exportBackup(AppSettings(), payload.profiles, payload.subscriptions)
        val back = BackupManager.parseBackup(text).getOrThrow()
        assertEquals(3, back.profiles.size)
        assertTrue(BackupManager.parseBackup("not json").isFailure)
    }

    @Test fun fileNameAndStamp() {
        assertTrue(backupFileName(0L).let { it.startsWith("hellboy-backup-") && it.endsWith(".json") })
        assertEquals("—", formatStamp(0L))
        assertTrue(formatStamp(1_700_000_000_000L).any { it in '۰'..'۹' })
    }
}
