package com.helboy.vpnclub.corex

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست زنده‌ی اجزای خالص-JVM موتور (بدون اندروید):
 *  - اسکنر کلادفلر (واقعاً چند IP تمیز پیدا می‌کند؟)
 *  - ping TCP
 *  - واکشی مخازن اشتراک (واقعاً کانفیگ می‌دهند؟)
 *  - تولید ClientHello / CIDR
 *
 * این تست‌ها شبکه لازم دارند؛ اگر شبکه نبود، با پیام روشن skip می‌شوند.
 */
class CorexLiveTest {

    @Test
    fun cidrMathProducesIpsInsideRange() {
        val ips = CloudflareScanner.randomIpsInCidr("104.16.0.0/13", 10)
        assertTrue("باید ۱۰ IP تولید شود", ips.size == 10)
        for (ip in ips) {
            val first = ip.substringBefore('.').toInt()
            assertTrue("IP باید داخل 104.x باشد: $ip", first == 104)
        }
    }

    @Test
    fun candidateGenerationCoversRanges() {
        val c = CloudflareScanner.buildCandidates(perRange = 2, ports = listOf(443))
        assertTrue("کاندید باید تولید شود", c.isNotEmpty())
        assertTrue("تعداد = رنج‌ها × perRange", c.size == CloudflareScanner.CF_RANGES_V4.size * 2)
    }

    @Test
    fun tcpPingWorksAgainstKnownHost() {
        val ms = HealthTester.tcpPing("1.1.1.1", 443, timeoutMs = 4000)
        assertTrue("1.1.1.1:443 باید پاسخ دهد (شد: $ms)", ms > 0)
    }

    @Test
    fun cloudflareScanFindsCleanIps() {
        val found = CloudflareScanner.scan(
            perRange = 8, ports = listOf(443), maxConcurrency = 24, timeoutMs = 1500
        )
        println("CF scan found ${found.size} clean IPs: ${found.take(5).map { "${it.ip}:${it.port} ${it.tlsMs}ms" }}")
        assertTrue("اسکنر باید حداقل یک IP تمیز پیدا کند", found.isNotEmpty())
        assertTrue("نتایج باید مرتب بر اساس tlsMs", found.zipWithNext().all { (a, b) -> a.tlsMs <= b.tlsMs })
    }

    @Test
    fun subscriptionSourcesYieldParsableConfigs() {
        val fetcher = SubscriptionFetcher()
        val raws = fetcher.fetchAll(SubscriptionFetcherTestSources.VERIFIED)
        println("fetched ${raws.size} raw config lines")
        assertTrue("مخازن باید کانفیگ بدهند (${raws.size})", raws.size > 10)

        val parsed = raws.mapNotNull { runCatching { LinkParser.parse(it) }.getOrNull() }
        println("parsed ${parsed.size} configs; schemes=${parsed.map { it.scheme }.distinct()}")
        assertTrue("باید حداقل ۱۰ کانفیگ پارس شود", parsed.size > 10)
        assertTrue("همه باید host و port داشته باشند", parsed.all { it.host.isNotEmpty() && it.port > 0 })

        val sample = parsed.first()
        println("sample: ${sample.scheme} ${sample.host}:${sample.port} sni=${sample.sniHost} tag=${sample.tag}")
    }

    @Test
    fun injectionRebuildsLinkWithSameSni() {
        val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443?type=ws&security=tls&sni=cdn.example.com&path=%2Fws&host=cdn.example.com#TestNode"
        val c = LinkParser.parse(link)!!
        val injected = c.withDial("104.16.1.2", 443)
        assertTrue("باید تزریق‌شده شناسایی شود", injected.isInjected)
        assertTrue("SNI باید حفظ شود", injected.sniHost == "cdn.example.com")
        val rebuilt = injected.toShareLink()
        println("rebuilt: $rebuilt")
        assertTrue("لینک بازساخته باید IP تازه را داشته باشد", rebuilt.contains("104.16.1.2"))
        assertTrue("دامنه اصلی باید به‌عنوان Host/SNI بماند", rebuilt.contains("cdn.example.com"))
    }
}

object SubscriptionFetcherTestSources {
    val VERIFIED = listOf(
        "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_Sub.txt",
        "https://raw.githubusercontent.com/ALIILAPRO/v2rayNG-Config/main/server.txt",
        // منابع کانال wbnet
        "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/all/configs.txt",
        "https://raw.githubusercontent.com/ShadowException/VPN/refs/heads/main/configs/VPN-cat"
    )
}
