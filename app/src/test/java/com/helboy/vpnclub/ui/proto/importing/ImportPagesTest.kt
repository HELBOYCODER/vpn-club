package com.helboy.vpnclub.ui.proto.importing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.helboy.vpnclub.ui.proto.ProtoEnv
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

/** Renders the import, subscriptions and lock-share pages on the JVM against an in-memory repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ImportPagesTest {
    @get:Rule val rule = createComposeRule()

    private val toasts = mutableListOf<String>()
    private val copied = mutableListOf<String>()
    private var clipboard: String? = null

    private val platform = object : Platform {
        override fun copyToClipboard(text: String) { copied += text }
        override fun readClipboard(): String? = clipboard
        override fun shareText(text: String) {}
        override fun openUri(url: String) {}
        override fun toast(message: String) { toasts += message }
    }

    private val vless = "vless://11111111-1111-1111-1111-111111111111@alpha.example:443" +
        "?encryption=none&security=tls&type=ws&host=alpha.example&path=%2F#Alpha"

    private var imported = 0
    private var backs = 0

    @Before
    fun setUp() {
        ThemeState.effects = false
        imported = 0
        backs = 0
        toasts.clear()
        copied.clear()
        clipboard = null
    }

    private fun settle() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
    }

    /** Real worker threads finish between these steps; the test clock is moved by hand. */
    private fun waitFor(what: String, condition: () -> Boolean) {
        repeat(300) {
            if (condition()) return
            Thread.sleep(20)
            rule.mainClock.advanceTimeBy(60)
            rule.waitForIdle()
        }
        throw AssertionError("timed out waiting for $what")
    }

    private fun exists(tag: String): Boolean = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun show(repo: ConfigRepository, content: @Composable () -> Unit) {
        ProtoEnv.install(repo)
        rule.mainClock.autoAdvance = false
        rule.setContent { HellboyTheme { CompositionLocalProvider(LocalPlatform provides platform) { content() } } }
        settle()
    }

    private fun showImport(repo: ConfigRepository) =
        show(repo) { ImportPage(onBack = { backs++ }, onImported = { imported++ }) }

    private fun type(tag: String, text: String) {
        rule.onNodeWithTag(tag).performTextInput(text)
        settle()
    }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
        settle()
    }

    // ----- ImportPage -----

    @Test
    fun importButtonWaitsForText() {
        showImport(ConfigRepository(InMemoryKeyValueStore()))
        rule.onNodeWithTag("proto_import_text").assertIsDisplayed()
        rule.onNodeWithTag("proto_import_button").assertIsNotEnabled()
    }

    @Test
    fun pastingAVlessLinkAndImportingAddsTheProfile() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        showImport(repo)
        type("proto_import_text", vless)
        click("proto_import_button")
        waitFor("the profile") { repo.profiles.value.isNotEmpty() }
        waitFor("the summary") { exists("proto_import_summary") }
        assertEquals("Alpha", repo.profiles.value.single().name)
        assertEquals(1, imported)
        assertTrue(toasts.any { it.contains("اضافه شد") })
    }

    @Test
    fun garbageShowsAnErrorAndAddsNothing() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        showImport(repo)
        type("proto_import_text", "this is definitely not a config")
        click("proto_import_button")
        waitFor("the error line") { exists("proto_import_error") }
        assertTrue(repo.profiles.value.isEmpty())
        assertEquals(0, imported)
    }

    @Test
    fun clipboardPasteFillsTheFieldThenImports() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        clipboard = vless
        showImport(repo)
        click("proto_import_paste")
        rule.onNodeWithTag("proto_import_kind").assertExists()
        click("proto_import_button")
        waitFor("the profile") { repo.profiles.value.isNotEmpty() }
    }

    @Test
    fun aBareAddressIsOfferedAsASubscription() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        showImport(repo)
        type("proto_import_text", "https://sub.example.com/api/sub/abc")
        click("proto_import_button")
        waitFor("the offer") { exists("proto_import_sub_add") }
        assertTrue(repo.subscriptions.value.isEmpty())
        assertTrue(repo.profiles.value.isEmpty())
    }

    // ----- SubscriptionsPage -----

    @Test
    fun noSubscriptionsShowsTheEmptyState() {
        show(ConfigRepository(InMemoryKeyValueStore())) { SubscriptionsPage(onBack = { backs++ }) }
        rule.onNodeWithTag("proto_empty").assertIsDisplayed()
    }

    @Test
    fun addingASubscriptionStoresItWithoutBlockingTheScreen() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        show(repo) { SubscriptionsPage(onBack = { backs++ }) }
        type("proto_subs_name", "Mine")
        type("proto_subs_url", "http://127.0.0.1:1/sub")
        click("proto_subs_add")
        val sub = repo.subscriptions.value.single()
        assertEquals("Mine", sub.name)
        // The refresh fails (nothing listens on port 1) and says so on the card.
        waitFor("the failure note") { exists("proto_sub_note_${sub.id}") }
    }

    @Test
    fun aBadAddressIsRefusedBeforeAnythingIsStored() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        show(repo) { SubscriptionsPage(onBack = { backs++ }) }
        type("proto_subs_url", "not a url")
        click("proto_subs_add")
        assertTrue(repo.subscriptions.value.isEmpty())
    }

    @Test
    fun switchAndDeleteActOnTheRepository() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("Alpha", "https://a.example/sub")
        repo.importText(vless, subscriptionId = sub.id).getOrThrow()
        show(repo) { SubscriptionsPage(onBack = { backs++ }) }
        rule.onNodeWithTag("proto_sub_card_${sub.id}").assertExists()

        click("proto_sub_switch_${sub.id}")
        assertTrue(!repo.subscriptions.value.single().enabled)

        click("proto_sub_delete_${sub.id}")
        rule.onNodeWithText("سرورها هم حذف شوند").assertExists()
        rule.onNodeWithTag("proto_dialog_ok").performClick()
        settle()
        assertTrue(repo.subscriptions.value.isEmpty())
        assertTrue(repo.profiles.value.isEmpty())
    }

    @Test
    fun deletingCanKeepTheServers() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        val sub = repo.addSubscription("Alpha", "https://a.example/sub")
        repo.importText(vless, subscriptionId = sub.id).getOrThrow()
        show(repo) { SubscriptionsPage(onBack = { backs++ }) }
        click("proto_sub_delete_${sub.id}")
        rule.onNodeWithText("سرورها بمانند").performClick()
        settle()
        rule.onNodeWithTag("proto_dialog_ok").performClick()
        settle()
        assertTrue(repo.subscriptions.value.isEmpty())
        assertEquals("", repo.profiles.value.single().subscriptionId)
    }

    // ----- LockSharePage -----

    @Test
    fun unknownProfileShowsTheEmptyState() {
        show(ConfigRepository(InMemoryKeyValueStore())) { LockSharePage(profileId = "nope", onBack = { backs++ }) }
        rule.onNodeWithTag("proto_empty").assertIsDisplayed()
    }

    @Test
    fun aPlainProfileShowsItsLinkAndCopiesIt() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        repo.importText(vless).getOrThrow()
        val id = repo.profiles.value.single().id
        show(repo) { LockSharePage(profileId = id, onBack = { backs++ }) }
        rule.onNodeWithTag("proto_share_link").assertExists()
        click("proto_share_copy")
        assertEquals(listOf(vless), copied)
        rule.onNodeWithTag("proto_device_code").assertExists()
    }

    @Test
    fun deviceLockNeedsTheBuyersCode() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        repo.importText(vless).getOrThrow()
        val id = repo.profiles.value.single().id
        show(repo) { LockSharePage(profileId = id, onBack = { backs++ }) }
        click("proto_lock_generate")
        rule.onNodeWithTag("proto_lock_error").assertExists()
        assertTrue(!exists("proto_lock_result"))
    }

    @Test
    fun aPublicLockProducesALinkThatImportsBack() {
        val repo = ConfigRepository(InMemoryKeyValueStore())
        repo.importText(vless).getOrThrow()
        val id = repo.profiles.value.single().id
        show(repo) { LockSharePage(profileId = id, onBack = { backs++ }) }
        rule.onNodeWithText("عمومی (بدون قفل دستگاه)").performScrollTo().performClick()
        settle()
        click("proto_lock_generate")
        waitFor("the locked link") { exists("proto_lock_result") }

        val other = ConfigRepository(InMemoryKeyValueStore())
        val link = copiedLinkFromScreen()
        val outcome = kotlinx.coroutines.runBlocking { ImportEngine.importText(other, link) }
        assertTrue(outcome.toString(), outcome is ImportOutcome.Done)
        assertTrue(other.profiles.value.single().isLocked)
    }

    /** The result card puts the link in a text node; read it back through the copy button. */
    private fun copiedLinkFromScreen(): String {
        click("proto_lock_copy_message")
        val message = copied.last()
        return message.lines().first { it.startsWith("nrg://") }
    }
}
