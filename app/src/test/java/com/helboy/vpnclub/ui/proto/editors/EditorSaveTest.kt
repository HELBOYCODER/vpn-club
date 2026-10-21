package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.data.config.InMemoryKeyValueStore
import dev.valid7996.narcicgeteway.domain.config.DnsTunnelProfile
import dev.valid7996.narcicgeteway.domain.config.Ikev2Auth
import dev.valid7996.narcicgeteway.domain.config.MasterDnsProfile
import dev.valid7996.narcicgeteway.domain.config.MhrvRelayProfile
import dev.valid7996.narcicgeteway.domain.config.OpenConnectProfile
import dev.valid7996.narcicgeteway.domain.config.PsiphonProfile
import dev.valid7996.narcicgeteway.domain.config.SshProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSaveTest {
    private val repo = ConfigRepository(InMemoryKeyValueStore())

    private fun withMeta(id: String) {
        val p = repo.profile(id)!!
        repo.update(p.copy(note = "from the provider", expiresAt = 1_900_000_000_000L, subscriptionId = "sub1", lastPingMs = 80, countryCode = "NL", bytesDown = 5, bytesUp = 6))
    }

    @Test fun `a new ssh profile is added with the typed name`() {
        val saved = EditorSave.ssh(repo, null, " Work ", SshForm(host = "h.example", username = "u", password = "p").toProfile())
        assertEquals(1, repo.profiles.value.size)
        assertEquals("Work", saved.name)
        assertTrue(saved.isSsh)
        assertEquals("h.example", saved.address)
    }

    @Test fun `a blank name gets the repository default`() {
        val saved = EditorSave.ssh(repo, null, "", SshProfile(host = "h.example"))
        assertEquals("SSH h.example", saved.name)
    }

    @Test fun `editing ssh keeps the id and everything the form does not own`() {
        val first = EditorSave.ssh(repo, null, "a", SshProfile(host = "h1"))
        withMeta(first.id)
        val existing = repo.profile(first.id)!!
        val edited = EditorSave.ssh(repo, existing, "b", SshProfile(host = "h2", port = 2222, password = "x"))
        assertEquals(1, repo.profiles.value.size)
        val stored = repo.profile(first.id)!!
        assertEquals(edited, stored)
        assertEquals("b", stored.name)
        assertEquals("h2", stored.address)
        assertEquals(2222, stored.port)
        assertEquals(existing.addedAt, stored.addedAt)
        assertEquals("from the provider", stored.note)
        assertEquals(1_900_000_000_000L, stored.expiresAt)
        assertEquals("sub1", stored.subscriptionId)
        assertEquals(80, stored.lastPingMs)
        assertEquals("NL", stored.countryCode)
        assertEquals(5L, stored.bytesDown)
    }

    @Test fun `editing a dns tunnel keeps the note and the expiry`() {
        val first = EditorSave.dnsTunnel(repo, null, "d", DnsTunnelProfile(engine = DnsTunnelProfile.ENGINE_DNSTT, domain = "t.example.com", publicKey = "k"))
        withMeta(first.id)
        EditorSave.dnsTunnel(repo, repo.profile(first.id), "d2", DnsTunnelProfile(engine = DnsTunnelProfile.ENGINE_VAYDNS, domain = "u.example.com", publicKey = "k2"))
        val stored = repo.profile(first.id)!!
        assertEquals(1, repo.profiles.value.size)
        assertEquals("d2", stored.name)
        assertEquals("u.example.com", stored.dnsTunnelSettings()!!.domain)
        assertEquals("from the provider", stored.note)
        assertEquals(1_900_000_000_000L, stored.expiresAt)
        assertEquals("sub1", stored.subscriptionId)
    }

    @Test fun `every other protocol creates and then edits in place`() {
        val md = EditorSave.masterDns(repo, null, "m", MasterDnsProfile(domains = "d", encryptionKey = "k", resolvers = "8.8.8.8"))
        val ik = EditorSave.ikev2(repo, null, "i", Ikev2Form(server = "s", username = "u", password = "p").toProfile())
        val oc = EditorSave.openConnect(repo, null, "o", OpenConnectProfile(server = "vpn.example.com"))
        val ps = EditorSave.psiphon(repo, null, "p", PsiphonProfile(country = "NL"))
        val mh = EditorSave.mhrv(repo, null, "h", MhrvRelayProfile(scriptIds = "A", authKey = "k"))
        assertEquals(5, repo.profiles.value.size)

        EditorSave.masterDns(repo, md, "m2", MasterDnsProfile(domains = "d2", encryptionKey = "k", resolvers = "1.1.1.1"))
        EditorSave.ikev2(repo, ik, "i2", Ikev2Form(server = "s2", authType = Ikev2Auth.PSK, psk = "x").toProfile())
        EditorSave.openConnect(repo, oc, "o2", OpenConnectProfile(server = "other.example.com"))
        EditorSave.psiphon(repo, ps, "p2", PsiphonProfile(country = "DE"))
        EditorSave.mhrv(repo, mh, "h2", MhrvRelayProfile(scriptIds = "B", authKey = "k2"))

        assertEquals(5, repo.profiles.value.size)
        assertEquals("d2", repo.profile(md.id)!!.masterDnsSettings()!!.domains)
        assertEquals("s2", repo.profile(ik.id)!!.ikev2Settings()!!.server)
        assertEquals("other.example.com", repo.profile(oc.id)!!.openConnectSettings()!!.server)
        assertEquals("DE", repo.profile(ps.id)!!.psiphonSettings()!!.country)
        assertEquals("B", repo.profile(mh.id)!!.mhrvRelaySettings()!!.scriptIds)
        listOf("m2", "i2", "o2", "p2", "h2").forEach { n -> assertTrue(n, repo.profiles.value.any { it.name == n }) }
    }

    @Test fun `tor is created by name and renamed in place`() {
        val tor = EditorSave.tor(repo, null, " My Tor ")
        assertTrue(tor.isTor)
        assertEquals("My Tor", tor.name)
        val renamed = EditorSave.tor(repo, tor, "Renamed")
        assertEquals(tor.id, renamed.id)
        assertEquals("Renamed", repo.profile(tor.id)!!.name)
        EditorSave.tor(repo, repo.profile(tor.id), "  ")
        assertEquals("a blank name keeps the old one", "Renamed", repo.profile(tor.id)!!.name)
        assertEquals(1, repo.profiles.value.size)
    }

    @Test fun `the first profile becomes the active one`() {
        assertNull(repo.activeId.value)
        val p = EditorSave.ssh(repo, null, "x", SshProfile(host = "h"))
        assertEquals(p.id, repo.activeId.value)
        assertNotNull(repo.activeProfile())
    }
}
