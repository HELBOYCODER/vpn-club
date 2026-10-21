package com.helboy.vpnclub.ui.proto.importing

import dev.valid7996.narcicgeteway.crypto.NrgLink
import dev.valid7996.narcicgeteway.crypto.ZsxSealRequest
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.data.config.InMemoryKeyValueStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportEngineTest {
    private val vless = "vless://11111111-1111-1111-1111-111111111111@alpha.example:443" +
        "?encryption=none&security=tls&type=ws&host=alpha.example&path=%2F#Alpha"
    private val trojan = "trojan://secret@gamma.example:443?security=tls&sni=gamma.example#Gamma"

    private fun repo() = ConfigRepository(InMemoryKeyValueStore())

    private fun seal(repo: ConfigRepository, password: String?, deviceBound: Boolean = false, expiresAt: Long? = null): ByteArray =
        repo.createLockedZsx(
            ZsxSealRequest(
                configPayload = vless, nameEn = "Locked One", nameFa = "", note = "", expiresAt = expiresAt,
                password = password, deviceBound = deviceBound,
            ),
        )

    private fun import(repo: ConfigRepository, text: String, file: String? = null) =
        runBlocking { ImportEngine.importText(repo, text, file) }

    private fun done(o: ImportOutcome): ImportSummary = (o as? ImportOutcome.Done)?.summary ?: error("expected Done, got $o")

    @Test fun `a vless link is imported`() {
        val repo = repo()
        val s = done(import(repo, vless))
        assertEquals(1, s.imported)
        assertEquals("Alpha", repo.profiles.value.single().name)
    }

    @Test fun `a list imports every good line and reports the bad ones`() {
        val repo = repo()
        val s = done(import(repo, "$vless\n$trojan\nvless://broken"))
        assertEquals(2, s.imported)
        assertEquals(1, s.failed)
        assertNotNull(s.firstFailure)
        assertEquals(2, repo.profiles.value.size)
    }

    @Test fun `repeating a link counts it as a duplicate`() {
        val repo = repo()
        done(import(repo, vless))
        val s = done(import(repo, "$vless\n$trojan"))
        assertEquals(1, s.imported)
        assertEquals(1, s.duplicates)
        assertEquals(2, repo.profiles.value.size)
    }

    @Test fun `only duplicates is not a failure but changes nothing`() {
        val repo = repo()
        done(import(repo, vless))
        val s = done(import(repo, vless))
        assertEquals(0, s.imported)
        assertEquals(1, s.duplicates)
        assertTrue(!s.changedList)
    }

    @Test fun `garbage fails with a Persian sentence and adds nothing`() {
        val repo = repo()
        val o = import(repo, "hello world, this is not a config")
        assertTrue(o is ImportOutcome.Failed)
        assertTrue((o as ImportOutcome.Failed).message.any { it in '؀'..'ۿ' })
        assertTrue(repo.profiles.value.isEmpty())
    }

    @Test fun `blank text fails`() {
        assertTrue(import(repo(), "   ") is ImportOutcome.Failed)
    }

    @Test fun `a bare address is offered as a subscription and not fetched`() {
        val repo = repo()
        val before = repo.subscriptions.value.size
        val o = import(repo, "https://sub.example.com/api/sub/abc")
        assertEquals(ImportOutcome.OfferSubscription("https://sub.example.com/api/sub/abc"), o)
        assertEquals(before, repo.subscriptions.value.size)
        assertEquals(ImportOutcome.OfferSubscription("https://sub.example.com/s/1"), import(repo, "اشتراک: https://sub.example.com/s/1"))
    }

    @Test fun `an unreachable subscription is kept for later`() {
        val repo = repo()
        val before = repo.subscriptions.value.size
        val s = done(runBlocking { ImportEngine.addSubscription(repo, "http://127.0.0.1:1/sub", "Mine") })
        assertTrue(s.savedOffline)
        assertEquals("Mine", s.subscriptionName)
        assertEquals(before + 1, repo.subscriptions.value.size)
    }

    @Test fun `an open sealed link imports straight away`() {
        val repo = repo()
        val link = NrgLink.encode(seal(repo, password = null))
        assertEquals(1, done(import(repo, link)).imported)
        assertTrue(repo.profiles.value.single().isLocked)
    }

    @Test fun `a password sealed link asks for the key`() {
        val repo = repo()
        val link = NrgLink.encode(seal(repo, password = "NG-AAAA-BBBB-CCCC"))
        val o = import(repo, link)
        assertTrue(o is ImportOutcome.NeedKey)
        assertTrue(repo.profiles.value.isEmpty())
        val need = o as ImportOutcome.NeedKey

        val wrong = runBlocking { ImportEngine.unlock(repo, need.bytes, need.meta, "NG-WRONG-WRONG-WRONG") }
        assertNotNull(wrong)
        assertTrue(repo.profiles.value.isEmpty())
        assertNull(runBlocking { ImportEngine.unlock(repo, need.bytes, need.meta, " NG-AAAA-BBBB-CCCC ") })
        assertEquals(1, repo.profiles.value.size)
    }

    @Test fun `a key found in the pasted message opens it once`() {
        val repo = repo()
        val link = NrgLink.encode(seal(repo, password = "NG-AAAA-BBBB-CCCC"))
        val message = "کلید: NG-AAAA-BBBB-CCCC\n$link"
        assertEquals(1, done(import(repo, message)).imported)
        val again = import(repo, message)
        assertEquals(ImportOutcome.Failed(ImportLogic.MSG_CONSUMED), again)
    }

    @Test fun `a config bound to this device opens without a key`() {
        val repo = repo()
        val link = NrgLink.encode(seal(repo, password = repo.nrgDeviceId(), deviceBound = true))
        assertEquals(1, done(import(repo, link)).imported)
    }

    @Test fun `a config bound to another device says so`() {
        val repo = repo()
        val link = NrgLink.encode(seal(repo, password = "SOMEONE-ELSES-DEVICE", deviceBound = true))
        assertEquals(ImportOutcome.Failed(ImportLogic.MSG_OTHER_DEVICE), import(repo, link))
        assertTrue(repo.profiles.value.isEmpty())
    }

    @Test fun `an expired sealed config says so`() {
        val repo = repo()
        val link = NrgLink.encode(seal(repo, password = null, expiresAt = 1L))
        assertEquals(ImportOutcome.Failed(ImportLogic.MSG_EXPIRED), import(repo, link))
    }

    @Test fun `a damaged sealed link fails politely`() {
        val o = import(repo(), "nrg://" + "A".repeat(40))
        assertTrue(o is ImportOutcome.Failed)
    }

    @Test fun `openvpn with inline login imports and one that asks for a login is held back`() {
        val repo = repo()
        val plain = "client\ndev tun\nproto udp\nremote vpn.example.net 1194\nremote-cert-tls server\ncipher AES-256-GCM\n" +
            "<ca>\n-----BEGIN CERTIFICATE-----\nMIIBexample\n-----END CERTIFICATE-----\n</ca>\n"
        assertEquals(1, done(import(repo, plain, "office.ovpn")).imported)
        assertEquals("office", repo.profiles.value.single().name)

        val asks = plain + "auth-user-pass\n"
        val o = import(repo, asks, "home.ovpn")
        assertTrue(o is ImportOutcome.NeedOvpnCredentials)
        assertEquals("home", (o as ImportOutcome.NeedOvpnCredentials).name)
        assertEquals(1, repo.profiles.value.size)
        assertEquals(1, done(runBlocking { ImportEngine.addOvpn(repo, o.text, o.name, "user", "pw") }).imported)
        assertEquals(2, repo.profiles.value.size)
    }
}
