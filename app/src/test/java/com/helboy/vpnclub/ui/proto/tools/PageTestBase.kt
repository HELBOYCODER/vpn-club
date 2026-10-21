package com.helboy.vpnclub.ui.proto.tools

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.theme.HellboyTheme
import com.helboy.vpnclub.ui.theme.ThemeState
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.data.config.InMemoryKeyValueStore
import dev.valid7996.narcicgeteway.data.settings.SettingsRepository
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import dev.valid7996.narcicgeteway.ui.platform.Platform
import org.junit.Assert.fail

/** Shared plumbing for the Robolectric page tests (same clock handling as HellboyShellTest). */
internal class PageHarness(private val rule: ComposeContentTestRule) {
    lateinit var settings: SettingsRepository
        private set

    fun install(): SettingsRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // fresh preferences for every test
        context.getSharedPreferences("nrg_settings", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsRepository(context)
        ProtoEnv.install(ConfigRepository(InMemoryKeyValueStore()), settings)
        ThemeState.effects = false
        return settings
    }

    fun show(platform: Platform, page: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HellboyTheme {
                CompositionLocalProvider(LocalPlatform provides platform) { page() }
            }
        }
        settle()
    }

    fun settle() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
    }

    /** Background work (Dispatchers.IO) finishes on real time, so poll while moving the test clock. */
    fun awaitTag(tag: String, timeoutMs: Long = 8000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            rule.mainClock.advanceTimeBy(100)
            rule.waitForIdle()
            if (rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(25)
        }
        fail("no node with tag $tag appeared")
    }
}
