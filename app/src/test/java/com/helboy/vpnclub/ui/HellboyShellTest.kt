package com.helboy.vpnclub.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.screen.AppRoot
import com.helboy.vpnclub.ui.screen.ProtocolPages
import com.helboy.vpnclub.ui.theme.HellboyTheme
import com.helboy.vpnclub.ui.theme.ThemeState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the Hellboy shell on the JVM (Robolectric) and walks the navigation. It cannot show how the VPN
 * behaves, but it does catch composition crashes and wrong wiring between tabs, the advanced page and onboarding.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class HellboyShellTest {
    @get:Rule val rule = createComposeRule()

    private var toggles = 0
    private var onboardingDone = 0
    private var importedCallback: (() -> Unit)? = null

    private val pages = ProtocolPages(
        profiles = { _, _ -> Text("PROTOCOLS_STUB") },
        advanced = { Text("ADVANCED_STUB") },
        hosts = { onImported -> importedCallback = onImported },
    )

    @Before
    fun setUp() {
        VpnController.init(ApplicationProvider.getApplicationContext())
        ThemeState.effects = false
    }

    // The Hellboy face and the particle orb run frame loops forever, so the test clock must not auto-advance
    // (it would never be idle); time is moved by hand after each interaction instead.
    private fun settle() = rule.mainClock.advanceTimeBy(600)

    private fun click(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        settle()
    }

    private fun show(onboarding: Boolean = false) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HellboyTheme {
                AppRoot(
                    onToggle = { toggles++ },
                    onConnect = {},
                    pages = pages,
                    showOnboarding = onboarding,
                    onOnboardingDone = { onboardingDone++ },
                )
            }
        }
        settle()
    }

    @Test
    fun homeIsTheFirstTabAndTheButtonStartsAConnection() {
        show()
        rule.onNodeWithTag("connect_button").assertIsDisplayed()
        click("connect_button")
        assertEquals(1, toggles)
    }

    @Test
    fun everyTabRenders() {
        show()
        click("nav_servers")
        click("nav_protocols")
        rule.onNodeWithText("PROTOCOLS_STUB").assertIsDisplayed()
        click("nav_scanner")
        click("nav_settings")
        rule.onNodeWithTag("screen_settings").assertIsDisplayed()
        click("nav_home")
        rule.onNodeWithTag("connect_button").assertIsDisplayed()
    }

    @Test
    fun advancedPageOpensFromSettingsAndGoesBack() {
        show()
        click("nav_settings")
        click("open_advanced")
        rule.onNodeWithText("ADVANCED_STUB").assertIsDisplayed()
        click("advanced_back")
        rule.onNodeWithTag("screen_settings").assertIsDisplayed()
    }

    @Test
    fun anImportedLinkJumpsToTheProtocolsTab() {
        show()
        rule.runOnUiThread { importedCallback!!.invoke() }
        settle()
        rule.onNodeWithText("PROTOCOLS_STUB").assertIsDisplayed()
    }

    @Test
    fun onboardingShowsFirstAndStartDismissesIt() {
        show(onboarding = true)
        rule.onNodeWithTag("onboarding").assertIsDisplayed()
        click("onboarding_start")
        assertEquals(1, onboardingDone)
    }
}
