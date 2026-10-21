package com.helboy.vpnclub.ui.proto.tools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.valid7996.narcicgeteway.domain.model.RulesetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RoutingPageTest {
    @get:Rule val rule = createComposeRule()
    private val h by lazy { PageHarness(rule) }
    private val platform = FakePlatform()

    @Before fun setUp() { h.install() }

    private fun showPage() = h.show(platform) { RoutingPage(onBack = {}) }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
        h.settle()
    }

    private fun type(tag: String, text: String) {
        rule.onNodeWithTag(tag).performScrollTo().performTextInput(text)
        h.settle()
    }

    private fun rules() = h.settings.settings.value.rulesets

    @Test fun emptyListShowsTheEmptyState() {
        showPage()
        rule.onNodeWithTag("proto_empty").performScrollTo().assertIsDisplayed()
    }

    @Test fun addingADomainRuleShowsItAndStoresIt() {
        showPage()
        click("proto_add_rule")
        type("proto_rule_name", "my rule")
        type("proto_rule_domain", "domain:example.com")
        click("proto_dialog_ok")

        rule.onNodeWithText("my rule").performScrollTo().assertIsDisplayed()
        assertEquals(1, rules().size)
        assertEquals(listOf("domain:example.com"), rules().single().domain)
        assertEquals(RulesetItem.OUTBOUND_PROXY, rules().single().outboundTag)
        assertTrue(h.settings.settings.value.routingMigrated)
        assertEquals(0, rule.onAllNodesWithTag("proto_empty").fetchSemanticsNodes().size)
    }

    @Test fun invalidRuleCannotBeSaved() {
        showPage()
        click("proto_add_rule")
        type("proto_rule_name", "bad")
        type("proto_rule_domain", "geoip:ir")

        rule.onNodeWithText("به فیلد IP", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("proto_dialog_ok").assertIsNotEnabled()
        rule.onNodeWithTag("proto_dialog_ok").performClick()
        h.settle()
        assertTrue(rules().isEmpty())
    }

    @Test fun badPortAndMissingConditionAreRejected() {
        showPage()
        click("proto_add_rule")
        type("proto_rule_name", "ports")
        // no condition yet
        rule.onNodeWithTag("proto_dialog_ok").assertIsNotEnabled()
        type("proto_rule_port", "99999")
        rule.onNodeWithText("پورت نامعتبر است", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("proto_dialog_ok").assertIsNotEnabled()
        assertTrue(rules().isEmpty())
    }

    @Test fun rulesCanBeReorderedToggledAndDeleted() {
        val a = RulesetItem(id = "a", remarks = "first", domain = listOf("domain:a.com"))
        val b = RulesetItem(id = "b", remarks = "second", domain = listOf("domain:b.com"))
        h.settings.update { it.withRules(listOf(a, b)) }
        showPage()

        rule.onAllNodesWithTag("proto_rule_down")[0].performScrollTo().performClick()
        h.settle()
        assertEquals(listOf("b", "a"), rules().map { it.id })

        rule.onAllNodesWithTag("proto_rule_enabled")[0].performScrollTo().performClick()
        h.settle()
        assertEquals(false, rules().first { it.id == "b" }.enabled)

        rule.onAllNodesWithTag("proto_rule_delete")[0].performScrollTo().performClick()
        h.settle()
        rule.onNodeWithTag("proto_confirm").performClick()
        h.settle()
        assertEquals(listOf("a"), rules().map { it.id })
    }

    @Test fun legacyRulesShowAMigrationNoticeAndConvert() {
        h.settings.update { it.copy(customDirectRules = "example.com") }
        showPage()
        rule.onNodeWithTag("proto_migrate").performScrollTo().assertIsDisplayed()
        click("proto_migrate")
        assertEquals(1, rules().size)
        assertEquals("", h.settings.settings.value.customDirectRules)
    }
}
