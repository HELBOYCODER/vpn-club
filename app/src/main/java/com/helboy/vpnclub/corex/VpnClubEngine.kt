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
        // تأیید شده با تست زنده ۲۰۲۶-۱۰-۱۰ (code=200 و کانفیگ واقعی)
        "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_Sub.txt",
        "https://raw.githubusercontent.com/Epodonios/v2ray-configs/main/All_Configs_Sub.txt",
        "https://raw.githubusercontent.com/ALIILAPRO/v2rayNG-Config/main/server.txt",
        "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/Eternity.txt",
        "https://raw.githubusercontent.com/mahdibland/ShadowsocksAggregator/master/Eternity.txt",
        "https://raw.githubusercontent.com/roosterkid/openproxylist/main/V2RAY_RAW.txt",
        "https://raw.githubusercontent.com/ermaozi/get_subscribe/main/subscribe/v2ray.txt",
        // منابع کانال t.me/wbnet (تأییدشده با تست زنده)
        "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/all/configs.txt",
        "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/protocols/vless.txt",
        "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/top100.txt",
        "https://github.com/Delta-Kronecker/V2ray-Config/raw/refs/heads/main/config/protocols/vless.txt",
        "https://raw.githubusercontent.com/4n0nymou3/multi-proxy-config-fetcher/refs/heads/main/configs/proxy_configs.txt",
        "https://raw.githubusercontent.com/luxxuria/harvester/main/speed_tested.txt",
        "https://raw.githubusercontent.com/F0rc3Run/F0rc3Run/refs/heads/main/splitted-by-protocol/vless.txt",
        "https://raw.githubusercontent.com/ShadowException/VPN/refs/heads/main/configs/VPN-cat",
        "https://raw.githubusercontent.com/ByeWhiteLists/ByeWhiteLists2/refs/heads/main/ByeWhiteLists2.txt",
        "https://raw.githubusercontent.com/zieng2/wl/main/vless_universal.txt",
        "https://raw.githubusercontent.com/hiztin/VLESS-PO-GRIBI/main/deploy/subscriptions/1.txt",
        "https://raw.githubusercontent.com/hiztin/VLESS-PO-GRIBI/main/deploy/subscriptions/10.txt",
        "https://raw.githubusercontent.com/prominbro/sub/refs/heads/main/212.txt",
        "https://raw.githubusercontent.com/LimeHi/LimeVPN/refs/heads/main/LimeVPN.txt"
    )

    var subscriptions: List<String> = defaultSubscriptions

    /**
     * چرخه‌ی کامل. از کوثروتین UI یا WorkManager صدا زده می‌شود (کامل در IO).
     *
     * استراتژی ضدگیر (درخواست کارفرما): از کانفیگ‌هایی که پینگ TCP جواب دادند،
     * «هر بار یکی را تصادفی» برمی‌داریم، روی آن یک IP تمیز سالم کلادفلر تزریق
     * می‌کنیم و تست واقعی (دانلود/آپلود از روی تونل) می‌گیریم. اولین کاندیدی که
     * تونل زنده داشت وصل می‌شویم — به‌جای تست ۴۰ کاندید پشت‌سرهم که زمان می‌گیرد.
     *
     * @return کانفیگ متصل‌شده یا null اگر هیچ‌کدام سالم نبود.
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

            // ── فاز ۲: اسکن IP تمیز کلادفلر ───────────────────────────────────
            onState(EngineState(Phase.SCANNING_CF, "اسکن IPهای تمیز کلادفلر…", configCount = configs.size))
            val cleanIps = CloudflareScanner.scan(perRange = 12, ports = listOf(443))
                .take(15)
            if (cleanIps.isEmpty()) {
                onState(EngineState(Phase.FAILED, "هیچ IP تمیز کلادفلر پیدا نشد", configCount = configs.size))
                return@withContext null
            }

            // ── فاز ۳: غربال TCP سریع → استخر کاندیدهای «پینگ‌دار» ─────────────
            onState(EngineState(Phase.TESTING, "غربال سریع پینگ…",
                configCount = configs.size, cleanIpCount = cleanIps.size))
            val rng = java.security.SecureRandom()
            // تزریق IP تمیز روی کانفیگ‌های TLS-دار (هر بار IP متفاوت — دور شدن از IPهای شلوغ)
            val injected = configs.mapIndexed { i, c ->
                if (c.query.toMap()["security"] in listOf("tls", "reality") ||
                    c.query.toMap()["tls"] == "tls") {
                    val ip = cleanIps[i % cleanIps.size]
                    val port = if (c.port in CloudflareScanner.CF_PORTS) c.port else ip.port
                    c.withDial(ip.ip, port)
                } else c
            }
            // اول کاندیدهای تزریق‌شده (اولویت عبور از فیلترینگ)، بعد خام‌ها
            val pool = (injected + configs).shuffled(rng).take(200)
            val pinged = pool.asSequence()
                .filter { HealthTester.tcpPing(it.dialHost, it.dialPort, timeoutMs = 1_500) in 1..MAX_TCP_MS }
                .take(30)
                .toList()
            if (pinged.isEmpty()) {
                onState(EngineState(Phase.FAILED, "هیچ کانفیگی پینگ نداد",
                    configCount = configs.size, cleanIpCount = cleanIps.size))
                return@withContext null
            }

            // ── فاز ۴: تست واقعی (هر بار یکی رندوم + تونل + دانلود/آپلود) ─────
            val health = mutableMapOf<String, HealthResult>()
            val candidates = mutableListOf<ProxyConfig>()
            val queue = pinged.toMutableList()
            var attempt = 0
            while (queue.isNotEmpty() && attempt < MAX_TUNNEL_ATTEMPTS) {
                // هر بار یک کانفیگ رندوم از صف — جلوگیری از گیر کردن روی یک سرور بد
                val c = queue.removeAt(rng.nextInt(queue.size))
                attempt++
                onState(EngineState(Phase.TESTING, "تست تونل $attempt از ${pinged.size}…",
                    configs.size, cleanIps.size, health.values.count { it.isUsable }))

                // FragmentProxy: Xray به‌جای آدرس واقعی، از تکه‌کننده‌ی ClientHello عبور می‌کند
                val frag = if (c.isInjected) {
                    FragmentProxy(c.dialHost, c.dialPort).also { f ->
                        if (f.start()) xray.dialOverride = "127.0.0.1:${f.port}" else f.stop()
                    }
                } else null

                val coreOk = try { xray.start(c) } catch (_: Exception) { false }
                val result = if (coreOk)
                    HealthTester.testConfig(c, VpnClubService.SOCKS_PORT)
                else
                    HealthResult(c, -1, 0.0, 0.0, "core_fail")
                xray.stop()
                frag?.stop()
                xray.dialOverride = null

                health[keyOf(c)] = result
                candidates.add(c)
                if (result.isUsable) {
                    // ✅ اولین کانفیگ سالم — وصل شو
                    store.save(candidates, health, cleanIps, System.currentTimeMillis())
                    onState(EngineState(Phase.CONNECTING, "کانفیگ سالم پیدا شد",
                        configs.size, cleanIps.size, 1))
                    return@withContext c
                }
            }

            // هیچ‌کدام سالم نبود
            store.save(candidates, health, cleanIps, System.currentTimeMillis())
            onState(EngineState(Phase.FAILED, "کانفیگ سالمی پیدا نشد ($attempt تلاش)",
                configs.size, cleanIps.size, 0))
            null
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
        const val MAX_TCP_MS = 2_500L
        const val MAX_TUNNEL_ATTEMPTS = 25
        fun keyOf(c: ProxyConfig): String = "\${c.scheme}|\${c.host}|\${c.port}|\${c.uuidOrUser}"
    }
}
