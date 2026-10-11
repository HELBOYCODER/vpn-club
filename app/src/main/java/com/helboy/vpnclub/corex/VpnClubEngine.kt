package com.helboy.vpnclub.corex

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.security.SecureRandom
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** فاز چرخه‌ی موتور — برای نمایش وضعیت در UI. */
enum class Phase { IDLE, FETCHING, SCANNING_CF, TESTING, CONNECTING, CONNECTED, FAILED }

/**
 * موتور کشف و انتخاب سرور (بدون وابستگی به سرویس VPN):
 *
 *   ۱. دریافت همزمان همه‌ی لینک‌های ساب + اسکن IP تمیز کلادفلر (موازی)
 *   ۲. پارس، حذف نوع‌های پشتیبانی‌نشده و تکراری‌ها
 *   ۳. غربال TCP موازی + تشخیص «پشت کلادفلر بودن»
 *   ۴. تست واقعی تونل (تاخیر + دانلود + آپلود) روی پورت جدا (۱۰۸۰۹) تا سرویس فعال مزاحم نشود
 *   ۵. هر سرور سالم فوراً گزارش می‌شود؛ هدف: ۱۰ سرور منتخب
 */
class VpnClubEngine(private val context: Context) {

    init { DiagLogger.appContext = context.applicationContext }

    private val fetcher = SubscriptionFetcher()
    private val store = ServerStore(context)
    private val settings = AppSettings.of(context)
    private val testXray = XrayCore(TEST_PORT).also { it.setAppContext(context) }

    /** مخازن اشتراک — سرور خودمان اول، بعد منابع تأییدشده. */
    val defaultSubscriptions = listOf(
        "https://vpnclub-sub.quilt-refract.workers.dev/sub",
        "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_Sub.txt",
        "https://raw.githubusercontent.com/Epodonios/v2ray-configs/main/All_Configs_Sub.txt",
        "https://raw.githubusercontent.com/ALIILAPRO/v2rayNG-Config/main/server.txt",
        "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/Eternity.txt",
        "https://raw.githubusercontent.com/mahdibland/ShadowsocksAggregator/master/Eternity.txt",
        "https://raw.githubusercontent.com/roosterkid/openproxylist/main/V2RAY_RAW.txt",
        "https://raw.githubusercontent.com/ermaozi/get_subscribe/main/subscribe/v2ray.txt",
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
        "https://raw.githubusercontent.com/LimeHi/LimeVPN/refs/heads/main/LimeVPN.txt",
        "https://cdn.jsdelivr.net/gh/zeghostwriter/ZeroNet@crowd-data/verified.txt"
    )

    /** یک سرور آماده‌ی تست. */
    data class Candidate(val config: ProxyConfig, val behindCf: Boolean, val tcpMs: Long)

    class Discovery(
        val candidates: List<Candidate>,
        val cleanIps: List<CloudflareScanner.CleanIp>,
        val totalParsed: Int
    )

    data class Progress(
        val phase: Phase,
        val message: String,
        val configCount: Int = 0,
        val cleanIpCount: Int = 0,
        val tested: Int = 0,
        val found: Int = 0
    )

    @Volatile private var learnedVariant: String? = null

    fun savedServers(): List<ServerEntry> = store.loadServers()
    fun saveServers(list: List<ServerEntry>) = store.saveServers(list)

    // ─────────────────────────────────────────────────────────────────────────
    // کشف: ساب‌ها + IP تمیز + غربال TCP
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun discover(onState: (Progress) -> Unit): Discovery? = withContext(Dispatchers.IO) {
        onState(Progress(Phase.FETCHING, "دریافت لینک‌های ساب و اسکن IP تمیز…"))

        // دریافت ساب‌ها و اسکن کلادفلر همزمان
        val (raws, cleanIps) = coroutineScope {
            val subs = async { fetcher.fetchAll(defaultSubscriptions) }
            val ips = async { findCleanIps(onState) }
            subs.await() to ips.await()
        }

        val parsed = raws.mapNotNull { runCatching { LinkParser.parse(it) }.getOrNull() }
            .filter { XrayCore.isSupported(it) }
            .distinctBy { ServerEntry(it).serverKey }
        if (parsed.isEmpty()) {
            onState(Progress(Phase.FAILED, "هیچ کانفیگ قابل‌استفاده‌ای از ساب‌ها گرفته نشد. اینترنت را بررسی کنید."))
            return@withContext null
        }
        currentCoroutineContext().ensureActive()

        onState(Progress(Phase.TESTING, "غربال سریع ${parsed.size} سرور…",
            configCount = parsed.size, cleanIpCount = cleanIps.size))
        val sample = if (parsed.size > MAX_PREFILTER) parsed.shuffled(SecureRandom()).take(MAX_PREFILTER) else parsed
        val alive = prefilter(sample)
        if (alive.isEmpty()) {
            onState(Progress(Phase.FAILED, "هیچ سروری به TCP پاسخ نداد (اینترنت قطع است؟)",
                configCount = parsed.size, cleanIpCount = cleanIps.size))
            return@withContext null
        }
        // اولویت: کمترین پینگ، با تناوب بین سرورهای پشت کلادفلر (قابل تزریق IP تمیز) و بقیه
        val cf = alive.filter { it.behindCf }.sortedBy { it.tcpMs }
        val other = alive.filter { !it.behindCf }.sortedBy { it.tcpMs }
        val ordered = ArrayList<Candidate>()
        var i = 0
        while (i < cf.size || i < other.size) {
            if (i < cf.size) ordered.add(cf[i])
            if (i < other.size) ordered.add(other[i])
            i++
        }
        Discovery(ordered.take(MAX_CANDIDATES), cleanIps, parsed.size)
    }

    /** IP تمیز: اول کش قبلی را بازبینی کن، اگر کم بود اسکن تازه. */
    private fun findCleanIps(onState: (Progress) -> Unit): List<CloudflareScanner.CleanIp> {
        val cached = CloudflareScanner.revalidate(store.loadCleanIps().take(30))
        var all = cached
        if (cached.size < 8) {
            val fresh = CloudflareScanner.scan(perRange = 14, ports = listOf(443), timeoutMs = 2_500)
            all = (cached + fresh).distinctBy { it.ip }.sortedBy { it.tlsMs }
        }
        val top = all.take(24)
        if (top.isNotEmpty()) store.saveCleanIps(top)
        onState(Progress(Phase.SCANNING_CF, "${top.size} IP تمیز کلادفلر", cleanIpCount = top.size))
        return top
    }

    /** غربال TCP موازی + resolve برای تشخیص کلادفلر. سقف زمانی کل: ۳۰ ثانیه. */
    private fun prefilter(configs: List<ProxyConfig>): List<Candidate> {
        val pool = Executors.newFixedThreadPool(64)
        val dnsCache = ConcurrentHashMap<String, Pair<String?, Boolean>>() // host → (ip, behindCf)
        val futures = configs.map { c ->
            pool.submit(Callable {
                val (ip, cf) = dnsCache.getOrPut(c.host) { resolveHost(c.host) }
                if (ip == null) return@Callable null
                val ms = HealthTester.tcpPing(ip, c.port, timeoutMs = 1_800)
                if (ms in 1..MAX_TCP_MS) Candidate(c, cf, ms) else null
            })
        }
        pool.shutdown()
        val deadline = System.currentTimeMillis() + 30_000
        val out = ArrayList<Candidate>()
        for (f in futures) {
            val left = (deadline - System.currentTimeMillis()).coerceAtLeast(50)
            val r = runCatching { f.get(left, TimeUnit.MILLISECONDS) }.getOrNull()
            if (r != null) out.add(r)
        }
        pool.shutdownNow()
        return out
    }

    private fun resolveHost(host: String): Pair<String?, Boolean> = try {
        val addrs = InetAddress.getAllByName(host)
        val v4 = addrs.firstOrNull { it.address.size == 4 }?.hostAddress
        val ip = v4 ?: addrs.firstOrNull()?.hostAddress
        val cf = addrs.any { a -> a.address.size == 4 && CloudflareScanner.isCfIp(a.hostAddress ?: "") }
        ip to cf
    } catch (_: Exception) { null to false }

    // ─────────────────────────────────────────────────────────────────────────
    // تست تونل واقعی
    // ─────────────────────────────────────────────────────────────────────────

    /** تست یک ServerEntry آماده (برای بازبینی لیست ذخیره‌شده). null = ناسالم. */
    suspend fun retest(e: ServerEntry): ServerEntry? = withContext(Dispatchers.IO) {
        val tunnel = TunnelLauncher.launch(testXray, e) ?: return@withContext null
        try {
            val r = HealthTester.testTunnel(TEST_PORT)
            DiagLogger.log("RETEST ${e.config.host}:${e.config.port} ${e.variantId} -> ${r.error ?: "OK"}")
            if (!r.ok) null
            else e.copy(latencyMs = r.latencyMs, downBps = r.downBps, upBps = r.upBps,
                testedAt = System.currentTimeMillis())
        } finally { tunnel.stop() }
    }

    /**
     * کاندیدها را به‌ترتیب تست می‌کند تا [want] سرور سالم پیدا شود.
     * هر سرور سالم بلافاصله با [onFound] گزارش می‌شود (اتصال بدون انتظار برای کل فرایند).
     */
    suspend fun findServers(
        disc: Discovery,
        want: Int,
        skipKeys: Set<String>,
        onFound: suspend (ServerEntry) -> Unit,
        onState: (Progress) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        var found = 0
        var tested = 0
        var cleanCursor = 0
        val rng = SecureRandom()

        for (cand in disc.candidates) {
            if (!currentCoroutineContext().isActive) break
            if (found >= want) break
            val elapsed = System.currentTimeMillis() - t0
            if (elapsed > HARD_BUDGET_MS || (elapsed > SOFT_BUDGET_MS && found >= 5)) break
            if (ServerEntry(cand.config).serverKey in skipKeys) continue

            tested++
            onState(Progress(Phase.TESTING, "تست تونل واقعی… (${tested}/${disc.candidates.size})",
                disc.totalParsed, disc.cleanIps.size, tested, found))

            val variants = IranVariants.order(settings.carrierMode, learnedVariant)
                .let { if (learnedVariant != null) it.take(MAX_ATTEMPTS_LEARNED) else it }

            for (v in variants) {
                if (!currentCoroutineContext().isActive) break
                if (v.needsCf && !cand.behindCf) continue
                val dial = pickDial(cand, v, disc.cleanIps, cleanCursor++, rng)
                val entry = ServerEntry(dial, v.id, v.useFrag)
                val tunnel = TunnelLauncher.launch(testXray, entry)
                val res = if (tunnel == null)
                    HealthTester.TunnelResult(-1, 0.0, 0.0, "core_fail: ${testXray.lastError.take(160)}")
                else try { HealthTester.testTunnel(TEST_PORT) } finally { tunnel.stop() }
                DiagLogger.log("${v.id} | ${dial.host}:${dial.port}->${dial.dialHost}/${dial.scheme} | " +
                    (res.error ?: "OK lat=${res.latencyMs}ms down=${res.downBps.toInt()}B/s up=${res.upBps.toInt()}B/s"))
                if (res.error?.startsWith("core_fail") == true)
                    DiagLogger.log("CONFIG_JSON: ${testXray.lastConfigJson.take(500)}")
                if (res.ok) {
                    learnedVariant = v.id
                    found++
                    onFound(entry.copy(latencyMs = res.latencyMs, downBps = res.downBps,
                        upBps = res.upBps, testedAt = System.currentTimeMillis()))
                    break
                }
            }
        }
        found
    }

    /** آدرس dial برای یک ترکیب (کاندید × پروفایل): IP تمیز / IPv6 کلادفلر / آدرس اصلی. */
    private fun pickDial(
        cand: Candidate, v: IranVariants.Variant,
        cleanIps: List<CloudflareScanner.CleanIp>, cursor: Int, rng: SecureRandom
    ): ProxyConfig {
        val c = cand.config
        if (!cand.behindCf || !injectable(c)) return c
        if (v.ipv6 && v.ipv6Prefix != null) return c.withDial(IranVariants.randomIpv6(v.ipv6Prefix, rng), c.port)
        if (v.id == "plain" && cursor % 3 == 2) return c // هر چند بار آدرس اصلی را هم امتحان کن
        if (cleanIps.isEmpty()) return c
        val ip = cleanIps[cursor % cleanIps.size]
        val port = if (c.port in CloudflareScanner.CF_PORTS) c.port else ip.port
        return c.withDial(ip.ip, port)
    }

    /** تزریق IP فقط وقتی معنی دارد که ترنسپورت از CDN رد شود و TLS باشد (نه Reality/TCP خام). */
    private fun injectable(c: ProxyConfig): Boolean {
        val q = c.query.toMap()
        val sec = q["security"] ?: if (q["tls"] == "tls" || c.scheme == "trojan") "tls" else ""
        if (sec != "tls") return false
        val net = q["type"] ?: q["network"] ?: "tcp"
        return net in setOf("ws", "grpc", "xhttp", "splithttp", "httpupgrade")
    }

    companion object {
        const val TEST_PORT = 10809
        const val MAX_TCP_MS = 2_500L
        const val MAX_PREFILTER = 700
        const val MAX_CANDIDATES = 160
        const val MAX_ATTEMPTS_LEARNED = 2
        const val SOFT_BUDGET_MS = 150_000L
        const val HARD_BUDGET_MS = 270_000L
        const val TARGET_SERVERS = 10
    }
}
