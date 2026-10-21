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

    private fun show(onboarding: Boolean = false) = rule.setContent {
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

    @Test
    fun homeIsTheFirstTabAndTheButtonStartsAConnection() {
        show()
        rule.onNodeWithTag("connect_button").assertIsDisplayed()
        rule.onNodeWithTag("connect_button").performClick()
        rule.waitForIdle()
        assertEquals(1, toggles)
    }

    @Test
    fun everyTabRenders() {
        show()
        rule.onNodeWithTag("nav_servers").performClick()
        rule.onNodeWithTag("nav_protocols").performClick()
        rule.onNodeWithText("PROTOCOLS_STUB").assertIsDisplayed()
        rule.onNodeWithTag("nav_scanner").performClick()
        rule.onNodeWithTag("nav_settings").performClick()
        rule.onNodeWithTag("screen_settings").assertIsDisplayed()
        rule.onNodeWithTag("nav_home").performClick()
        rule.onNodeWithTag("connect_button").assertIsDisplayed()
    }

    @Test
    fun advancedPageOpensFromSettingsAndGoesBack() {
        show()
        rule.onNodeWithTag("nav_settings").performClick()
        rule.onNodeWithTag("open_advanced").performClick()
        rule.onNodeWithText("ADVANCED_STUB").assertIsDisplayed()
        rule.onNodeWithTag("advanced_back").performClick()
        rule.onNodeWithTag("screen_settings").assertIsDisplayed()
    }

    @Test
    fun anImportedLinkJumpsToTheProtocolsTab() {
        show()
        rule.runOnIdle { importedCallback!!.invoke() }
        rule.onNodeWithText("PROTOCOLS_STUB").assertIsDisplayed()
    }

    @Test
    fun onboardingShowsFirstAndStartDismissesIt() {
        show(onboarding = true)
        rule.onNodeWithTag("onboarding").assertIsDisplayed()
        rule.onNodeWithTag("onboarding_start").performClick()
        assertEquals(1, onboardingDone)
    }
}
