package com.helboy.vpnclub.ui.proto.engine

import dev.valid7996.narcicgeteway.data.tools.TelegramProxyItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramLogicTest {
    private fun p(id: String, ping: Long = -1L, server: String = "$id.example.org") =
        TelegramProxyItem(id = id, server = server, port = 443, secret = "ee00", pingMs = ping, title = id)

    @Test fun channelNamesAreCleanedFromAnyFormat() {
        assertEquals("ProxyMTProto", TelegramLogic.cleanChannel("@ProxyMTProto"))
        assertEquals("ProxyMTProto", TelegramLogic.cleanChannel("https://t.me/s/ProxyMTProto/"))
        assertEquals("ProxyMTProto", TelegramLogic.cleanChannel(" t.me/ProxyMTProto "))
        assertEquals("", TelegramLogic.cleanChannel("  "))
    }

    @Test fun onlyRealChannelNamesAreAccepted() {
        assertTrue(TelegramLogic.isValidChannel("ProxyMTProto"))
        assertTrue(TelegramLogic.isValidChannel("a_b_c_d_e"))
        assertFalse(TelegramLogic.isValidChannel("abc"))
        assertFalse(TelegramLogic.isValidChannel("1abcdef"))
        assertFalse(TelegramLogic.isValidChannel("has space"))
        assertFalse(TelegramLogic.isValidChannel(""))
    }

    @Test fun pingBands() {
        assertEquals(TelegramLogic.Band.Fast, TelegramLogic.band(p("a", 120)))
        assertEquals(TelegramLogic.Band.Medium, TelegramLogic.band(p("a", 500)))
        assertEquals(TelegramLogic.Band.Slow, TelegramLogic.band(p("a", 900)))
        assertEquals(TelegramLogic.Band.Untested, TelegramLogic.band(p("a", -1), tested = false))
        assertEquals(TelegramLogic.Band.Failed, TelegramLogic.band(p("a", -1), tested = true))
    }

    @Test fun sortingPutsWorkingFastFirstThenUntestedThenDead() {
        val list = listOf(p("dead"), p("slow", 700), p("fresh"), p("fast", 90))
        val sorted = TelegramLogic.sorted(list, tested = setOf("dead", "slow", "fast")).map { it.id }
        assertEquals(listOf("fast", "slow", "fresh", "dead"), sorted)
    }

    @Test fun refreshCountsOnlyProxiesBeyondTheBuiltInList() {
        val builtIn = listOf(p("a"), p("b"))
        assertEquals(0, TelegramLogic.fetchedNew(builtIn, builtIn))
        assertEquals(1, TelegramLogic.fetchedNew(builtIn + p("c"), builtIn))
    }

    @Test fun linksAreJoinedWithTitles() {
        val text = TelegramLogic.allLinks(listOf(p("a"), p("b")))
        assertTrue(text.contains("a: https://t.me/proxy?server=a.example.org"))
        assertTrue(text.contains("\n\n"))
    }

    @Test fun summaryMentionsTheBestPingWhenThereIsOne() {
        assertTrue(TelegramLogic.summary(listOf(p("a", 80), p("b", 200))).contains("۸۰"))
        assertFalse(TelegramLogic.summary(listOf(p("a"))).contains("سالم"))
    }
}
