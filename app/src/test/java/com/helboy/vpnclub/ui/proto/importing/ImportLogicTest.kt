package com.helboy.vpnclub.ui.proto.importing

import dev.valid7996.narcicgeteway.crypto.NrgLink
import dev.valid7996.narcicgeteway.crypto.ZsxExpiredException
import dev.valid7996.narcicgeteway.crypto.ZsxKeyConsumedException
import dev.valid7996.narcicgeteway.crypto.ZsxWrongPasswordException
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.data.config.RelayAlreadyClaimedException
import dev.valid7996.narcicgeteway.domain.config.ConfigParseException
import dev.valid7996.narcicgeteway.domain.config.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportLogicTest {
    private val vless = "vless://11111111-1111-1111-1111-111111111111@alpha.example:443" +
        "?encryption=none&security=tls&type=ws&host=alpha.example&path=%2F#Alpha"
    private val trojan = "trojan://secret@gamma.example:443?security=tls&sni=gamma.example#Gamma"
    private val ss = "ss://YWVzLTI1Ni1nY206cGFzcw@ss.example:8388#SS"

    private val wireguard = """
        [Interface]
        PrivateKey = 6M2Bc2yGf0J8gQm0Wm0n8hQe1x3o0y9u0b3c4d5e6f8=
        Address = 10.0.0.2/32

        [Peer]
        PublicKey = 6M2Bc2yGf0J8gQm0Wm0n8hQe1x3o0y9u0b3c4d5e6f8=
        Endpoint = wg.example:51820
        AllowedIPs = 0.0.0.0/0
    """.trimIndent()

    private val ovpn = """
        client
        dev tun
        proto udp
        remote vpn.example.net 1194
        cipher AES-256-GCM
    """.trimIndent()

    // ----- classification -----

    @Test fun `empty and blank text is Empty`() {
        assertEquals(InputKind.Empty, ImportLogic.classify(""))
        assertEquals(InputKind.Empty, ImportLogic.classify("  \n \t "))
    }

    @Test fun `a single link and a list of links are Links`() {
        assertEquals(InputKind.Links, ImportLogic.classify(vless))
        assertEquals(InputKind.Links, ImportLogic.classify("$vless\n\n$trojan\r\n$ss"))
    }

    @Test fun `a bare web address is a subscription url`() {
        assertEquals(InputKind.SubscriptionUrl, ImportLogic.classify("https://sub.example.com/api/sub/abcdef"))
        assertEquals(InputKind.SubscriptionUrl, ImportLogic.classify("  http://203.0.113.5:2096/sub/x?token=1  \n"))
        assertEquals("https://a.example/s", ImportLogic.subscriptionUrlOf("‎https://a.example/s "))
    }

    @Test fun `a url with words around it is not a subscription url but its sole url is found`() {
        val msg = "اشتراک شما: https://sub.example.com/s/1."
        assertNull(ImportLogic.subscriptionUrlOf(msg))
        assertEquals(InputKind.Other, ImportLogic.classify(msg))
        assertEquals("https://sub.example.com/s/1", ImportLogic.soleUrlIn(msg))
        assertNull(ImportLogic.soleUrlIn("https://a.example/1 and https://b.example/2"))
        assertNull(ImportLogic.soleUrlIn("no url here"))
    }

    @Test fun `json objects and arrays are Json`() {
        assertEquals(InputKind.Json, ImportLogic.classify("""{"outbounds":[{"type":"direct"}]}"""))
        assertEquals(InputKind.Json, ImportLogic.classify("""[{"server":"a.example"}]"""))
    }

    @Test fun `wireguard and openvpn files are recognised before the generic cases`() {
        assertEquals(InputKind.WireguardConf, ImportLogic.classify(wireguard))
        assertEquals(InputKind.Ovpn, ImportLogic.classify(ovpn))
    }

    @Test fun `base64 subscription body is Base64`() {
        val body = java.util.Base64.getEncoder().encodeToString("$vless\n$trojan".toByteArray())
        assertEquals(InputKind.Base64, ImportLogic.classify(body))
        // Chat apps wrap long lines.
        assertEquals(InputKind.Base64, ImportLogic.classify(body.chunked(40).joinToString("\n")))
    }

    @Test fun `sealed links and relay links are told apart`() {
        val blob = ByteArray(40) { (it * 7 + 3).toByte() }
        assertEquals(InputKind.Locked, ImportLogic.classify(NrgLink.encode(blob)))
        assertEquals(InputKind.Locked, ImportLogic.classify("کلید: NG-ABCD-EFGH-JKLM\n${NrgLink.encode(blob)}"))
        assertEquals(InputKind.Relay, ImportLogic.classify("nrg://r.Abc123xyz@relay.example.com"))
    }

    @Test fun `garbage is Other`() {
        assertEquals(InputKind.Other, ImportLogic.classify("hello world, this is not a config"))
        assertEquals(InputKind.Other, ImportLogic.classify("abc"))
    }

    @Test fun `link lines need a scheme`() {
        assertTrue(ImportLogic.isLinkLine(vless))
        assertTrue(ImportLogic.isLinkLine("  hysteria2://pw@h.example:443#H "))
        assertFalse(ImportLogic.isLinkLine("just words"))
        assertFalse(ImportLogic.isLinkLine("://missing"))
        assertEquals(2, ImportLogic.lines("\n $vless \n\n$trojan\n").size)
    }

    // ----- files -----

    @Test fun `binary nrg and zsx headers become a link`() {
        val nrg = byteArrayOf('N'.code.toByte(), 'R'.code.toByte(), 'G'.code.toByte(), '1'.code.toByte()) + ByteArray(30) { it.toByte() }
        val zsx = byteArrayOf('Z'.code.toByte(), 'S'.code.toByte(), 'X'.code.toByte(), '1'.code.toByte()) + ByteArray(30) { (it + 5).toByte() }
        assertTrue(ImportLogic.hasSealedHeader(nrg))
        assertTrue(ImportLogic.hasSealedHeader(zsx))
        assertFalse(ImportLogic.hasSealedHeader("vless".toByteArray()))
        val decoded = ImportLogic.decodePickedFile("a.bin", nrg)!!
        assertTrue(decoded.startsWith("nrg://"))
        assertEquals(nrg.toList(), NrgLink.decode(decoded).toList())
        assertEquals(zsx.toList(), NrgLink.decode(ImportLogic.decodePickedFile("b.zsx", zsx)!!).toList())
    }

    @Test fun `text files are decoded and trimmed only when sealed`() {
        assertEquals(vless, ImportLogic.decodePickedFile("links.txt", vless.toByteArray()))
        assertEquals("  x\n", ImportLogic.decodePickedFile("a.txt", "  x\n".toByteArray()))
        val link = NrgLink.encode(ByteArray(30) { it.toByte() })
        assertEquals(link, ImportLogic.decodePickedFile("share.nrg", ("\n$link\n").toByteArray()))
        assertEquals(vless, ImportLogic.decodePickedFile("a.txt", ("﻿$vless").toByteArray()))
    }

    @Test fun `empty and blank files are rejected`() {
        assertNull(ImportLogic.decodePickedFile("a.txt", ByteArray(0)))
        assertNull(ImportLogic.decodePickedFile("a.txt", "  \n ".toByteArray()))
    }

    @Test fun `binary content under a sealed name is encoded as a link`() {
        val junk = ByteArray(24) { (0x80 + it).toByte() }
        assertTrue(ImportLogic.decodePickedFile("x.nrg", junk)!!.startsWith("nrg://"))
    }

    @Test fun `base name drops the extension`() {
        assertEquals("my config", ImportLogic.baseName("my config.ovpn"))
        assertEquals("noext", ImportLogic.baseName("noext"))
        assertNull(ImportLogic.baseName(null))
        assertNull(ImportLogic.baseName(" .ovpn"))
    }

    // ----- summaries and errors -----

    @Test fun `summary text counts imported duplicates and failures`() {
        assertEquals("۳ کانفیگ اضافه شد", ImportLogic.formatSummary(ImportSummary(3)))
        assertEquals("۲ کانفیگ اضافه شد · ۱ تکراری · ۴ مورد ناموفق", ImportLogic.formatSummary(ImportSummary(2, 1, 4)))
        assertEquals("چیزی اضافه نشد", ImportLogic.formatSummary(ImportSummary(0)))
        assertTrue(ImportLogic.formatSummary(ImportSummary(0, duplicates = 2)).contains("از قبل"))
        assertEquals(
            "اشتراک «A» اضافه شد و ۱۰ سرور دریافت شد",
            ImportLogic.formatSummary(ImportSummary(10, subscriptionName = "A")),
        )
        assertTrue(ImportLogic.formatSummary(ImportSummary(0, subscriptionName = "A", savedOffline = true)).contains("ذخیره شد"))
    }

    @Test fun `changedList is true for imports and kept subscriptions only`() {
        assertTrue(ImportSummary(1).changedList)
        assertTrue(ImportSummary(0, subscriptionName = "A", savedOffline = true).changedList)
        assertFalse(ImportSummary(0, duplicates = 3).changedList)
    }

    @Test fun `sealed config errors have their own Persian sentence`() {
        assertEquals(ImportLogic.MSG_EXPIRED, ImportLogic.describeError(ZsxExpiredException()))
        assertEquals(ImportLogic.MSG_CONSUMED, ImportLogic.describeError(ZsxKeyConsumedException()))
        assertEquals(ImportLogic.MSG_WRONG_KEY, ImportLogic.describeError(ZsxWrongPasswordException()))
        assertEquals(ImportLogic.MSG_OTHER_DEVICE, ImportLogic.describeUnlockError(ZsxWrongPasswordException(), deviceBound = true))
        assertEquals(ImportLogic.MSG_WRONG_KEY, ImportLogic.describeUnlockError(ZsxWrongPasswordException(), deviceBound = false))
        assertTrue(ImportLogic.describeError(RelayAlreadyClaimedException()).contains("دستگاه دیگر"))
    }

    @Test fun `parse failures never leak English messages or class names`() {
        val e = ConfigParseException("Unsupported or invalid link scheme")
        val text = ImportLogic.describeError(e)
        assertFalse(text.contains("Exception"))
        assertFalse(text.contains("scheme"))
        assertEquals(ImportLogic.MSG_NOTHING_FOUND, ImportLogic.describeError(IllegalArgumentException("no supported links found")))
        assertEquals(ImportLogic.MSG_GENERIC, ImportLogic.describeError(IllegalStateException("boom at line 3")))
        assertTrue(
            ImportLogic.describeError(ConfigRepository.SubscriptionNotFetchedException("S", true, RuntimeException("x"))).contains("ذخیره شد"),
        )
    }

    @Test fun `engine's own Persian device message is passed through`() {
        val msg = "این کانفیگ اختصاصی برای دستگاه دیگری قفل شده است و روی این دستگاه فعال نمی‌شود."
        assertEquals(msg, ImportLogic.describeError(IllegalArgumentException(msg)))
    }

    @Test fun `update errors are explained`() {
        assertTrue(ImportLogic.describeUpdateError(ConfigRepository.SubscriptionUnreachableException(RuntimeException("x"))).contains("اتصال"))
        assertTrue(ImportLogic.describeUpdateError(IllegalArgumentException("no supported configs found")).contains("کانفیگ"))
        assertTrue(ImportLogic.describeUpdateError(IllegalArgumentException("unknown subscription")).contains("وجود ندارد"))
    }

    // ----- subscriptions -----

    @Test fun `short url hides userinfo and query`() {
        assertEquals("sub.example.com/api/sub/abc", ImportLogic.shortUrl("https://user:pw@sub.example.com/api/sub/abc?token=SECRET#x"))
        assertEquals("sub.example.com", ImportLogic.shortUrl("https://sub.example.com"))
        val long = ImportLogic.shortUrl("https://sub.example.com/" + "a".repeat(100), max = 20)
        assertEquals(20, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test fun `relative update time`() {
        val now = 1_000_000_000_000L
        assertEquals("هنوز به‌روزرسانی نشده", ImportLogic.agoText(now, 0))
        assertEquals("چند ثانیه پیش", ImportLogic.agoText(now, now - 20_000))
        assertEquals("۵ دقیقه پیش", ImportLogic.agoText(now, now - 5 * 60_000))
        assertEquals("۳ ساعت پیش", ImportLogic.agoText(now, now - 3 * 3_600_000))
        assertEquals("۲ روز پیش", ImportLogic.agoText(now, now - 2 * 86_400_000))
    }

    @Test fun `usage and expiry from the subscription headers`() {
        val now = 1_700_000_000_000L
        val sub = Subscription(
            id = "1", name = "S", url = "https://s.example/x", upload = 100_000_000, download = 400_000_000,
            total = 1_000_000_000, expireAt = now + 10 * 86_400_000L + 1000,
        )
        assertEquals("۵۰۰.۰ MB از ۱.۰۰ GB مصرف شده", ImportLogic.usageText(sub))
        assertEquals(0.5f, ImportLogic.usageFraction(sub)!!, 0.001f)
        assertEquals("۱۰ روز مانده", ImportLogic.expiryText(sub, now))
        assertEquals("منقضی شده", ImportLogic.expiryText(sub.copy(expireAt = now - 1), now))
        assertEquals("یک روز مانده", ImportLogic.expiryText(sub.copy(expireAt = now + 86_400_000L + 5), now))
        assertEquals("امروز تمام می‌شود", ImportLogic.expiryText(sub.copy(expireAt = now + 3_600_000L), now))
        assertNull(ImportLogic.expiryText(sub.copy(expireAt = null), now))
        val plain = Subscription(id = "2", name = "P", url = "https://s.example/y")
        assertNull(ImportLogic.usageText(plain))
        assertNull(ImportLogic.usageFraction(plain))
    }

    @Test fun `auto update line`() {
        assertTrue(ImportLogic.autoUpdateText(true, 12).contains("۱۲"))
        assertTrue(ImportLogic.autoUpdateText(false, 12).contains("خاموش"))
    }

    // ----- locked export -----

    @Test fun `expiry options map to absolute times`() {
        val now = 1_000_000L
        assertNull(ImportLogic.expiryFor(0, now))
        assertEquals(now + 300_000L, ImportLogic.expiryFor(1, now))
        assertEquals(now + 3 * 86_400_000L, ImportLogic.expiryFor(5, now))
        assertEquals(now + 30 * 86_400_000L, ImportLogic.expiryFor(7, now))
        assertEquals(now + 86_400_000L + 2 * 3_600_000L + 30 * 60_000L, ImportLogic.expiryFor(8, now, 1, 2, 30))
        assertNull(ImportLogic.expiryFor(8, now, 0, 0, 0))
        assertEquals(9, ImportLogic.EXPIRY_OPTIONS.size)
    }

    @Test fun `remaining time wording`() {
        val now = 5_000_000_000L
        assertEquals("دائمی", ImportLogic.remainingText(null, now))
        assertEquals("منقضی شده", ImportLogic.remainingText(now - 1, now))
        assertEquals("۲ روز و ۳ ساعت دیگر", ImportLogic.remainingText(now + 2 * 86_400_000L + 3 * 3_600_000L + 1000, now))
        assertEquals("۲ ساعت دیگر", ImportLogic.remainingText(now + 2 * 3_600_000L + 1000, now))
        assertEquals("۵ دقیقه دیگر", ImportLogic.remainingText(now + 5 * 60_000L + 1000, now))
    }

    @Test fun `generated keys have the NG format and no look-alike characters`() {
        val key = ImportLogic.userKeyFrom(ByteArray(12) { (it * 21).toByte() })
        assertTrue(key, Regex("NG-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}").matches(key))
        assertFalse(key.drop(3).any { it in "01OI" })
        assertNotNull(dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor.extractKey("کلید: $key"))
        assertEquals(key, dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor.extractKey("کلید: $key"))
    }

    @Test fun `lock options are validated per mode`() {
        assertNotNull(ImportLogic.validateLockOptions("device", " ", ""))
        assertNull(ImportLogic.validateLockOptions("device", "ABC", ""))
        assertNotNull(ImportLogic.validateLockOptions("key", "", ""))
        assertNotNull(ImportLogic.validateLockOptions("key", "", "abc"))
        assertNull(ImportLogic.validateLockOptions("key", "", "NG-AAAA-BBBB-CCCC"))
        assertNull(ImportLogic.validateLockOptions("public", "", ""))
    }

    @Test fun `file names keep persian letters and drop path characters`() {
        assertEquals("سرور_۱.nrg", ImportLogic.safeFileName("سرور ۱"))
        assertEquals("a_b_c.nrg", ImportLogic.safeFileName("a/b\\c"))
        assertEquals("config.nrg", ImportLogic.safeFileName("   "))
    }

    @Test fun `share message carries the link and who it is for`() {
        val link = "nrg://AAAA"
        val device = ImportLogic.buildShareMessage("N", link, null, 0, deviceCode = "DEV123")
        assertTrue(device.contains(link) && device.contains("DEV123") && device.contains("دائمی"))
        val keyed = ImportLogic.buildShareMessage("N", link, null, 0, key = "NG-AAAA-BBBB-CCCC", note = " برای علی ")
        assertTrue(keyed.contains("NG-AAAA-BBBB-CCCC") && keyed.contains("برای علی"))
        assertFalse(keyed.contains("DEV123"))
        assertTrue(ImportLogic.buildShareMessage("N", link, null, 0).contains("عمومی"))
    }
}
