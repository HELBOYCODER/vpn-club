package com.helboy.vpnclub.corex

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** فاز چرخه‌ی موتور — برای نمایش وضعیت در UI. */
enum class Phase { IDLE, FETCHING, SCANNING_CF, TESTING, CONNECTING, CONNECTED, FAILED }

/**
 * موتور اصلی VPN Club — «همه چیز خودکار، کاربر فقط یک دکمه».
 *
 * چرخه‌ی کامل (هر بار که کاربر روشن می‌کند و به‌صورت دوره‌ای در پس‌زمینه):
 *
 *   ۱. واکشی همزمان همه‌ی مخازن (اشتراک‌ها + VPNGate + منابع عمومی)
 *   ۲. پارس و حذف تکراری‌ها
 *   ۳. اسکن IP تمیز کلادفلر
 *   ۴. تزریق IPهای تازه روی کانفیگ‌های TLS-دار (رفتار پتنگ برای اینترنت ایران)
 *   ۵. تست سلامت (پینگ + دانلود + آپلود) روی همه‌ی کاندیدها — در پس‌زمینه
 *   ۶. نگه‌داشتن فقط کانفیگ‌های «سالم» و انتخاب سریع‌ترین
 *   ۷. اتصال به بهترین کاندید
 *
 * کانفیگی که فقط پینگ دارد ولی دانلود/آپلودش صفر است، از لیست حذف می‌شود.
 */
class VpnClubEngine(private val context: Context) {

    private val store = ProfileStore(context)
    private val fetcher = SubscriptionFetcher()
    private val xray = XrayCore(VpnClubService.SOCKS_PORT).also { it.setAppContext(context) }

    data class EngineState(
        val phase: Phase = Phase.IDLE,
        val message: String = "",
        val configCount: Int = 0,
        val cleanIpCount: Int = 0,
        val usableCount: Int = 0
    )

    /** زیرساخت‌های پایدار — مخازن اشتراک همیشه‌به‌روز. */
    val defaultSubscriptions = listOf(
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/vless.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/vmess.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/trojan.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/ss.txt",
        "https://raw.githubusercontent.com/mahdibland/ShadowsocksAggregator/master/Eternity.txt",
        "https://raw.githubusercontent.com/freefq/free/master/v2",
        "https://raw.githubusercontent.com/ALIILAPRO/v2rayNG-Config/main/server.txt"
    )

    var subscriptions: List<String> = defaultSubscriptions

    /**
     * چرخه‌ی کامل. از کوثروتین UI یا WorkManager صدا زده می‌شود (تطویل کامل در IO).
     * @return بهترین کانفیگ سالم یا null اگر هیچ‌کدام سالم نبود.
     */
    suspend fun runFullCycle(onState: (EngineState) -> Unit): ProxyConfig? = withContext(Dispatchers.IO) {
        try {
            // ── فاز ۱: واکشی مخازن ────────────────────────────────────────────
            onState(EngineState(Phase.FETCHING, "دریافت مخازن…"))
            val raws = fetcher.fetchAll(subscriptions)
            val configs = raws.mapNotNull { runCatching { LinkParser.parse(it) }.getOrNull() }
                .distinctBy { "${it.host}:${it.port}:${it.uuidOrUser}" }
            if (configs.isEmpty()) {
                onState(EngineState(Phase.FAILED, "هیچ کانفیگی از مخازن گرفته نشد"))
                return@withContext null
            }

            // ── فاز ۲: اسکن IP کلادفلر ────────────────────────────────────────
            onState(EngineState(Phase.SCANNING_CF, "اسکن IPهای تمیز کلادفلر…", configCount = configs.size))
            val cleanIps = CloudflareScanner.scan(perRange = 16, ports = listOf(443, 2053))
                .take(20)

            // ── فاز ۳: تزریق IP تازه روی کانفیگ‌های TLS-دار ───────────────────
            val tlsConfigs = configs.filter { c ->
                val q = c.query.toMap()
                q["tls"] == "tls" || q["security"] in listOf("tls", "reality")
            }
            val injected = if (cleanIps.isNotEmpty()) tlsConfigs.mapIndexed { i, c ->
                val ip = cleanIps[i % cleanIps.size]
                c.withDial(ip.ip, ip.port)
            } else emptyList()

            // ترکیب: کانفیگ‌های تزریق‌شده اول (اولویت عبور از فیلترینگ)، بعد بقیه
            val candidates = (injected + configs).take(MAX_CANDIDATES)

            // ── فاز ۴: تست سلامت (پینگ + دانلود + آپلود) ──────────────────────
            onState(EngineState(Phase.TESTING, "تست پینگ/دانلود/آپلود…",
                configCount = configs.size, cleanIpCount = cleanIps.size))
            val health = mutableMapOf<String, HealthResult>()
            var tested = 0
            for (c in candidates) {
                // برای تست دانلود/آپلود واقعی، هسته را موقتاً با این کانفیگ بالا می‌آوریم
                val coreOk = xray.start(c)
                val result = if (coreOk)
                    HealthTester.testConfig(c, VpnClubService.SOCKS_PORT)
                else
                    HealthResult(c, -1, 0.0, 0.0, "core_fail")
                xray.stop()
                health[keyOf(c)] = result
                tested++
                if (tested % 5 == 0)
                    onState(EngineState(Phase.TESTING, "تست $tested از ${candidates.size}",
                        configs.size, cleanIps.size, health.values.count { it.isUsable }))
            }

            // ── فاز ۵: ذخیره و انتخاب بهترین ──────────────────────────────────
            val usable = health.values.filter { it.isUsable }.sortedByDescending { it.score }
            store.save(candidates, health, cleanIps, System.currentTimeMillis())
            val best = usable.firstOrNull()?.config
            onState(EngineState(
                if (best != null) Phase.CONNECTING else Phase.FAILED,
                if (best != null) "بهترین کانفیگ پیدا شد" else "کانفیگ سالمی پیدا نشد",
                configs.size, cleanIps.size, usable.size))
            best
        } catch (e: Exception) {
            onState(EngineState(Phase.FAILED, "خطا: ${e.message}"))
            null
        }
    }

    /** اتصال به کانفیگ مشخص — پس از تست سلامت. */
    fun connectTo(context: Context, config: ProxyConfig): Boolean {
        CurrentConfigHolder.save(context, config)
        VpnClubService.start(context)
        return true
    }

    fun disconnect(context: Context) {
        VpnClubService.stop(context)
        CurrentConfigHolder.clear(context)
    }

    /** چرخه‌ی خودکار کامل: پیدا کردن بهترین و اتصال. */
    suspend fun autoConnect(onState: (EngineState) -> Unit): Boolean {
        val best = runFullCycle(onState) ?: return false
        connectTo(context, best)
        onState(EngineState(Phase.CONNECTED, "متصل شد — بهترین کانفیگ", usableCount = 1))
        return true
    }

    companion object {
        const val MAX_CANDIDATES = 40
        fun keyOf(c: ProxyConfig): String = "\${c.scheme}|\${c.host}|\${c.port}|\${c.uuidOrUser}"
    }
}
