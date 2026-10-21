package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.domain.config.DnsTunnelProfile
import dev.valid7996.narcicgeteway.domain.config.MasterDnsProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsFormsTest {
    private val dns = DnsTunnelForm(domain = "t.example.com", publicKey = "ab".repeat(32))

    @Test fun `dns tunnel needs a domain and a key`() {
        val c = DnsTunnelForm().check()
        assertNotNull(c.errors[DnsTunnelForm.F_DOMAIN])
        assertNotNull(c.errors[DnsTunnelForm.F_KEY])
        assertTrue(dns.check().ok)
    }

    @Test fun `a domain must look like a domain`() {
        assertNotNull(dns.copy(domain = "localhost").check().errors[DnsTunnelForm.F_DOMAIN])
        assertNotNull(dns.copy(domain = "a b.com").check().errors[DnsTunnelForm.F_DOMAIN])
    }

    @Test fun `doh url must be https when given`() {
        val doh = dns.copy(transport = DnsTunnelProfile.TRANSPORT_DOH)
        assertTrue(doh.check().ok) // empty: the default server is used
        assertNotNull(doh.copy(dohUrl = "http://x/dns").check().errors[DnsTunnelForm.F_DOH])
        assertTrue(doh.copy(dohUrl = "https://dns.google/dns-query").check().ok)
    }

    @Test fun `ssh over dns needs its own fields only when enabled`() {
        assertTrue(dns.copy(sshHost = "").check().ok)
        val on = dns.copy(sshEnabled = true)
        assertNotNull(on.check().errors[DnsTunnelForm.F_SSH_HOST])
        assertTrue(on.copy(sshHost = "ssh.example.com").check().ok)
        assertNotNull(on.copy(sshHost = "h", sshPort = "0").check().errors[DnsTunnelForm.F_SSH_PORT])
        assertNotNull(on.copy(sshHost = "h", sshAuth = DnsTunnelProfile.AUTH_KEY).check().errors[DnsTunnelForm.F_SSH_KEY])
    }

    @Test fun `numbers must parse`() {
        assertNotNull(dns.copy(idleTimeout = "x").check().errors[DnsTunnelForm.F_IDLE])
        assertNotNull(dns.copy(rps = "1.2.3").check().errors[DnsTunnelForm.F_RPS])
        assertTrue(dns.copy(rps = "2.5", idleTimeout = "10").check().ok)
    }

    @Test fun `resolvers are normalised and the multi resolver flag follows`() {
        val f = dns.copy(resolvers = "8.8.8.8\n 1.1.1.1 ,9.9.9.9\n")
        assertTrue(f.multiResolver)
        assertEquals("8.8.8.8,1.1.1.1,9.9.9.9", f.toProfile().resolvers)
        assertFalse(dns.copy(resolvers = "8.8.8.8").multiResolver)
    }

    @Test fun `dns profile round trip`() {
        val p = DnsTunnelProfile(
            engine = DnsTunnelProfile.ENGINE_VAYDNS, domain = "t.example.com", publicKey = "k", resolvers = "8.8.8.8,1.1.1.1",
            recordType = "cname", maxQnameLen = 90, rps = 5.0, idleTimeout = 10, keepalive = 2, udpTimeout = 500,
            maxNumLabels = 3, clientIdSize = 2, socksUser = "u", socksPass = "p", sshEnabled = true, sshHost = "h", sshPort = 2200,
            sshUsername = "root", sshAuthType = DnsTunnelProfile.AUTH_KEY, sshPrivateKey = "KEY", sshKeyPassphrase = "pp",
            forwardDnsThroughSsh = true, dnsPayloadSize = 80, authoritative = false, autoTune = true,
        )
        val back = DnsTunnelForm.from(p, "x").toProfile()
        assertEquals(p, back)
    }

    private val mdns = MasterDnsForm(domains = "v.example.com", key = "k", resolvers = "8.8.8.8\n1.1.1.1:53")

    @Test fun `master dns agrees with the profile's own isValid`() {
        listOf(
            mdns, mdns.copy(domains = ""), mdns.copy(key = ""), mdns.copy(resolvers = ""), mdns.copy(resolvers = "# only a comment"),
        ).forEach { f -> assertEquals(f.toProfile().isValid, f.check().ok) }
    }

    @Test fun `master dns messages name the missing field`() {
        val c = MasterDnsForm().check()
        assertNotNull(c.errors[MasterDnsForm.F_DOMAINS])
        assertNotNull(c.errors[MasterDnsForm.F_KEY])
        assertNotNull(c.errors[MasterDnsForm.F_RESOLVERS])
    }

    @Test fun `master dns resolvers must be ip addresses`() {
        val allBad = mdns.copy(resolvers = "dns.google\nexample.com")
        assertNotNull(allBad.check().errors[MasterDnsForm.F_RESOLVERS])
        val someBad = mdns.copy(resolvers = "8.8.8.8\ndns.google")
        assertTrue(someBad.check().ok)
        assertNotNull(someBad.check().warnings[MasterDnsForm.F_RESOLVERS])
        assertEquals(listOf("dns.google"), someBad.badResolvers)
    }

    @Test fun `master dns keeps the port and log level it does not show`() {
        val p = MasterDnsProfile(domains = "d", encryptionKey = "k", resolvers = "8.8.8.8", listenPort = 19999, logLevel = "DEBUG", packetDuplication = 4)
        val back = MasterDnsForm.from(p, "n").toProfile()
        assertEquals(19999, back.listenPort)
        assertEquals("DEBUG", back.logLevel)
        assertEquals(p, back)
        assertNull(MasterDnsForm.from(null).toProfile().domains.takeIf { it.isNotEmpty() })
    }
}
