package com.helboy.vpnclub.ui.proto.profiles

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoRoute
import com.helboy.vpnclub.ui.theme.HellboyTheme
import com.helboy.vpnclub.ui.theme.ThemeState
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.data.config.InMemoryKeyValueStore
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import dev.valid7996.narcicgeteway.ui.platform.Platform
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
class ProfilesPageTest {
    @get:Rule val rule = createComposeRule()

    private val platform = object : Platform {
        override fun copyToClipboard(text: String) {}
        override fun readClipboard(): String? = null
        override fun shareText(text: String) {}
        override fun openUri(url: String) {}
        override fun toast(message: String) {}
    }

    private val vless = "vless://11111111-1111-1111-1111-111111111111@alpha.example:443" +
        "?encryption=none&security=tls&type=ws&host=alpha.example&path=%2F#Alpha"
    private val trojan = "trojan://secret@gamma.example:443?security=tls&sni=gamma.example#Gamma"
    private val vmess = "vmess://eyJ2IjoiMiIsInBzIjoiQmV0YSIsImFkZCI6ImJldGEuZXhhbXBsZSIsInBvcnQiOiI0NDMiLCJpZCI6IjIyMjIyMjIyLTIyMjItMjIyMi0yMjIyLTIyMjIyMjIyMjIyMiIsImFpZCI6IjAiLCJuZXQiOiJ3cyIsInR5cGUiOiJub25lIiwiaG9zdCI6ImJldGEuZXhhbXBsZSIsInBhdGgiOiIvIiwidGxzIjoidGxzIn0="

    private var activated = 0
    private val routes = mutableListOf<ProtoRoute>()

    @Before
    fun setUp() {
        ThemeState.effects = false
        activated = 0
        routes.clear()
    }

    private fun settle() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
    }

    private fun show(repo: ConfigRepository) {
        ProtoEnv.install(repo)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HellboyTheme {
                CompositionLocalProvider(LocalPlatform provides platform) {
                    ProfilesPage(onNavigate = { routes += it }, onActivated = { activated++ })
                }
            }
        }
        settle()
    }

    private fun filled(): ConfigRepository {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        repo.importText(vless).getOrThrow()
        repo.importText(vmess).getOrThrow()
        repo.importText(trojan).getOrThrow()
        return repo
    }

    @Test
    fun listShowsEveryProfileName() {
        show(filled())
        rule.onNodeWithText("Alpha").assertIsDisplayed()
        rule.onNodeWithText("Beta").assertIsDisplayed()
        rule.onNodeWithText("Gamma").assertIsDisplayed()
    }

    @Test
    fun searchNarrowsTheList() {
        show(filled())
        rule.onNodeWithTag("proto_profiles_search").performTextInput("gamma")
        settle()
        rule.onNodeWithText("Gamma").assertIsDisplayed()
        rule.onNodeWithText("Alpha").assertDoesNotExist()
        rule.onNodeWithText("Beta").assertDoesNotExist()
    }

    @Test
    fun searchWithNoMatchShowsTheFilteredEmptyState() {
        show(filled())
        rule.onNodeWithTag("proto_profiles_search").performTextInput("zzzz-no-such")
        settle()
        rule.onNodeWithTag("proto_empty").assertIsDisplayed()
        rule.onNodeWithText("موردی پیدا نشد").assertIsDisplayed()
    }

    @Test
    fun tappingAProfileMakesItActiveAndReportsIt() {
        val repo = filled()
        show(repo)
        rule.onNodeWithText("Gamma").performClick()
        settle()
        assertEquals(1, activated)
        assertEquals("Gamma", repo.activeProfile()?.name)
    }

    @Test
    fun emptyRepositoryShowsTheEmptyStateAndOffersImport() {
        show(ConfigRepository(InMemoryKeyValueStore()))
        rule.onNodeWithTag("proto_empty").assertIsDisplayed()
        rule.onNodeWithText("وارد کردن کانفیگ").performClick()
        settle()
        assertTrue(routes.single() is ProtoRoute.Import)
    }

    @Test
    fun addDialogListsEveryWayToAdd() {
        show(filled())
        rule.onNodeWithTag("proto_profiles_add").performClick()
        settle()
        rule.onNodeWithTag("proto_add_import").assertIsDisplayed()
        rule.onNodeWithTag("proto_add_Psiphon").performClick()
        settle()
        assertTrue(routes.single() is ProtoRoute.New)
    }
}
