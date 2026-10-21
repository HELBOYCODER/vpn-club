package com.helboy.vpnclub.ui.proto.engine

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.theme.HellboyTheme
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.data.config.InMemoryKeyValueStore
import dev.valid7996.narcicgeteway.data.settings.SettingsRepository
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class MtuPageTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        settings = SettingsRepository(ctx)
        ProtoEnv.install(ConfigRepository(InMemoryKeyValueStore()), settings)
    }

    private fun settle() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
    }

    private fun show() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HellboyTheme {
                CompositionLocalProvider(LocalPlatform provides FakePlatform()) {
                    MtuPage(onBack = {})
                }
            }
        }
        settle()
    }

    private fun type(text: String) {
        rule.onNodeWithTag("proto_mtu_manual").performScrollTo().performTextReplacement(text)
        settle()
    }

    @Test
    fun aValidManualValueIsSaved() {
        show()
        type("1400")
        rule.onNodeWithTag("proto_mtu_save").performScrollTo().assertIsEnabled().performClick()
        settle()
        assertEquals(1400, settings.settings.value.vpnMtu)
        rule.onNodeWithTag("proto_mtu_saved").assertExists()
    }

    @Test
    fun anInvalidManualValueDisablesSaveShowsWhyAndChangesNothing() {
        show()
        val before = settings.settings.value.vpnMtu
        for (bad in listOf("abc", "999", "9001", "")) {
            type(bad)
            rule.onNodeWithTag("proto_mtu_save").performScrollTo().assertIsNotEnabled()
        }
        type("999")
        val message = (MtuLogic.check("999") as Checked.Bad).message
        rule.onNodeWithText(message).assertExists()
        assertEquals(before, settings.settings.value.vpnMtu)
    }

    @Test
    fun anUnusualButLegalValueWarnsYetCanBeSaved() {
        show()
        type("1100")
        rule.onNodeWithTag("proto_mtu_warning").assertExists()
        rule.onNodeWithTag("proto_mtu_save").performScrollTo().assertIsEnabled().performClick()
        settle()
        assertEquals(1100, settings.settings.value.vpnMtu)
    }

    @Test
    fun withoutAnActiveProfileTheMeasureButtonStillShows() {
        show()
        rule.onNodeWithTag("proto_mtu_measure").assertExists()
    }
}
