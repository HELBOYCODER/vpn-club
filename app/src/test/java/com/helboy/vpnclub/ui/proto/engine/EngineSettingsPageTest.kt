package com.helboy.vpnclub.ui.proto.engine

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the engine settings page on the JVM and checks that what the person does reaches SettingsRepository.
 * The compose clock is driven by hand like HellboyShellTest does, so nothing waits on animations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class EngineSettingsPageTest {
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
                    EngineSettingsPage(onBack = {})
                }
            }
        }
        settle()
    }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
        settle()
    }

    @Test
    fun togglingASwitchWritesToTheRepository() {
        show()
        assertFalse(settings.settings.value.killSwitch)
        click("proto_engine_section_mode")
        click("proto_engine_killSwitch")
        assertTrue(settings.settings.value.killSwitch)
        click("proto_engine_killSwitch")
        assertFalse(settings.settings.value.killSwitch)
    }

    @Test
    fun aValidNumberIsSavedAndAnInvalidOneIsRejectedWithAMessage() {
        show()
        click("proto_engine_section_vpn")
        rule.onNodeWithTag("proto_engine_vpnMtu").performScrollTo().performTextReplacement("1400")
        settle()
        assertEquals(1400, settings.settings.value.vpnMtu)

        rule.onNodeWithTag("proto_engine_vpnMtu").performTextReplacement("50")
        settle()
        assertEquals(1400, settings.settings.value.vpnMtu)
        val message = (SettingsValidation.checkInt("50", 1000, 9000) as Checked.Bad).message
        rule.onNodeWithText(message).assertExists()
    }

    @Test
    fun aTextFieldWithAValidatorKeepsTheOldValueWhileTheTextIsInvalid() {
        show()
        click("proto_engine_section_tests")
        val before = settings.settings.value.delayTestUrl
        rule.onNodeWithTag("proto_engine_delayTestUrl").performScrollTo().performTextReplacement("not a url")
        settle()
        assertEquals(before, settings.settings.value.delayTestUrl)
        rule.onNodeWithTag("proto_engine_delayTestUrl").performTextReplacement("https://example.org/204")
        settle()
        assertEquals("https://example.org/204", settings.settings.value.delayTestUrl)
    }

    @Test
    fun restoringASectionAsksFirstAndThenPutsDefaultsBack() {
        show()
        click("proto_engine_section_mode")
        click("proto_engine_killSwitch")
        assertTrue(settings.settings.value.killSwitch)

        click("proto_engine_reset_mode")
        // Nothing changes until the question is answered.
        assertTrue(settings.settings.value.killSwitch)
        rule.onNodeWithTag("proto_confirm").performClick()
        settle()
        assertFalse(settings.settings.value.killSwitch)
    }

    @Test
    fun theTunSectionOffersTheThreeEngines() {
        show()
        click("proto_engine_section_tun")
        rule.onNodeWithText("Xray-TUN").assertExists()
        rule.onNodeWithText("Hev").assertExists()
        rule.onNodeWithText("ZepTun").assertExists()
        rule.onNodeWithText("Hev").performClick()
        settle()
        assertTrue(settings.settings.value.useHevTun)
        assertFalse(settings.settings.value.useZepTun)
    }
}
