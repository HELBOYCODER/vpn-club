package com.helboy.vpnclub.corex

import org.junit.Assert.*
import org.junit.Test

class NetworkFeaturesTest {
    @Test fun dnsValidationWithoutHostnameLookup() {
        assertTrue(DnsEndpoint.isValid("1.1.1.1"))
        assertTrue(DnsEndpoint.isValid("2606:4700:4700::1111"))
        assertTrue(DnsEndpoint.isValid("https://1.1.1.1/dns-query"))
        assertFalse(DnsEndpoint.isValid("999.1.1.1"))
        assertFalse(DnsEndpoint.isValid("http://1.1.1.1/dns-query"))
        assertFalse(DnsEndpoint.isValid("https://user:password@1.1.1.1/dns-query"))
        assertFalse(DnsEndpoint.isValid("https://example.com/dns-query"))
        assertFalse(DnsEndpoint.isValid("1.1.1"))
    }
    @Test fun lanListenersRequireAuthenticationAndSeparatePorts() {
        val inbounds = XrayCore().lanInbounds("a-long-random-password")
        assertEquals(setOf(10818, 10819), inbounds.map { it.getInt("port") }.toSet())
        inbounds.forEach {
            assertEquals("0.0.0.0", it.getString("listen"))
            val account = it.getJSONObject("settings").getJSONArray("accounts").getJSONObject(0)
            assertEquals("hellboy", account.getString("user"))
            assertEquals("a-long-random-password", account.getString("pass"))
        }
        assertEquals("password", inbounds.first().getJSONObject("settings").getString("auth"))
        assertFalse(inbounds.first().getJSONObject("settings").getBoolean("udp"))
    }
    @Test(expected = IllegalArgumentException::class) fun noEmptyLanPassword() { XrayCore().lanInbounds("") }
    @Test fun wireguardSubscriptionLinesAreAccepted() {
        val fetcher = SubscriptionFetcher()
        assertTrue(fetcher.looksLikeConfig("wg://key@host:2408"))
        assertTrue(fetcher.looksLikeConfig("wireguard://key@host:2408"))
    }
}
