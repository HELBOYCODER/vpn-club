package com.helboy.vpnclub.ui.proto.tools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import dev.valid7996.narcicgeteway.ui.platform.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PerAppPageTest {
    @get:Rule val rule = createComposeRule()
    private val h by lazy { PageHarness(rule) }

    private val apps = listOf(
        InstalledApp("com.test.alpha", "Alpha", false),
        InstalledApp("com.test.beta", "Beta", false),
        InstalledApp("com.android.systemthing", "Sysapp", true),
    )

    @Before fun setUp() { h.install() }

    private fun showList() {
        h.show(FakePlatform(apps = apps)) { PerAppPage(onBack = {}) }
        h.awaitTag("proto_app_list")
        h.settle()
    }

    private fun pick(mode: String) {
        rule.onNodeWithTag("proto_app_list").performScrollToNode(hasText(mode))
        rule.onNodeWithText(mode).performScrollTo().performClick()
        h.settle()
    }

    private fun selected() = h.settings.settings.value.perAppPackages

    @Test fun unsupportedPlatformShowsAMessageInsteadOfAList() {
        h.show(FakePlatform(perApp = false)) { PerAppPage(onBack = {}) }
        rule.onNodeWithTag("proto_empty").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("proto_app_list").fetchSemanticsNodes().size)
    }

    @Test fun selectingAnAppPersistsInSettings() {
        showList()
        pick("فقط انتخاب‌شده‌ها")
        assertTrue(h.settings.settings.value.perAppProxyEnabled)
        assertFalse(h.settings.settings.value.perAppBypassMode)

        rule.onNodeWithTag("proto_app_list").performScrollToNode(hasText("Alpha"))
        rule.onNodeWithText("Alpha").performClick()
        h.settle()
        assertEquals(setOf("com.test.alpha"), selected())

        // tapping again unselects
        rule.onNodeWithText("Alpha").performClick()
        h.settle()
        assertTrue(selected().isEmpty())
    }

    @Test fun selectAllOnlyTakesShownAppsAndSystemAppsStayHidden() {
        showList()
        pick("فقط انتخاب‌شده‌ها")
        rule.onNodeWithTag("proto_app_all").performScrollTo().performClick()
        h.settle()
        assertEquals(setOf("com.test.alpha", "com.test.beta"), selected())
        rule.onNodeWithTag("proto_app_none").performScrollTo().performClick()
        h.settle()
        assertTrue(selected().isEmpty())
    }

    @Test fun searchNarrowsTheList() {
        showList()
        rule.onNodeWithTag("proto_app_search").performScrollTo().performTextInput("bet")
        h.settle()
        rule.onNodeWithTag("proto_app_list").performScrollToNode(hasText("Beta"))
        rule.onNodeWithText("Beta").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Alpha").fetchSemanticsNodes().size)
    }

    @Test fun excludeModeIsStoredAsBypass() {
        showList()
        pick("همه به‌جز انتخاب‌شده‌ها")
        assertTrue(h.settings.settings.value.perAppProxyEnabled)
        assertTrue(h.settings.settings.value.perAppBypassMode)
    }
}
