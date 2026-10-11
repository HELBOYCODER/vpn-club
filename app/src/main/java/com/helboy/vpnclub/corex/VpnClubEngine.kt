package com.helboy.vpnclub.corex

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** testXray و پورت ۱۰۸۰۹ مشترک است؛ هر تست باید انحصاری باشد. */
    private val testLock = Mutex()

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

        onState(Progress(Phase.TESTING, "غربال سریع ${minOf(parsed.size, MAX_PREFILTER)} سرور (هندشیک واقعی)…",
            configCount = parsed.size, cleanIpCount = cleanIps.size))
        val sample = if (parsed.size > MAX_PREFILTER) parsed.shuffled(SecureRandom()).take(MAX_PREFILTER) else parsed
        val alive = prefilter(sample)
        if (alive.isEmpty()) {
            onState(Progress(Phase.FAILED, "هیچ سروری به TCP پاسخ نداد (اینترنت قطع است؟)",
                configCount = parsed.size, cleanIpCount = cleanIps.size))
            return@withContext null
        }
        // تصادفی، ولی سرورهای با «اثبات پروتکلی» (TLS/WS) جلوتر از TCP-خام (ss/vmess بدون TLS)
        fun strong(c: Candidate): Boolean {
            val q = c.config.query.toMap()
            return q["security"] in listOf("tls", "reality") || q["tls"] == "tls" || c.config.scheme == "trojan"
        }
        val rng = SecureRandom()
        val (st, weak) = alive.partition { strong(it) }
        val ordered = st.shuffled(rng) + weak.shuffled(rng)
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

    /**
     * غربال بدون هسته با پروب پروتکلی (هندشیک TLS با SNI، یا Upgrade وب‌سوکت و انتظار 101).
     * اندازه‌گیری واقعی: قبل از این پروب فقط ~۱٪ کاندیدها تونل سالم داشتند، بعد از آن ~۱۲٪.
     * نتایج هرچه تا مهلت رسید استفاده می‌شود (ExecutorCompletionService).
     */
    private fun prefilter(configs: List<ProxyConfig>): List<Candidate> {
        val pool = Executors.newFixedThreadPool(PROBE_THREADS)
        val ecs = java.util.concurrent.ExecutorCompletionService<Candidate?>(pool)
        val dnsCache = ConcurrentHashMap<String, Pair<String?, Boolean>>() // host → (ip, behindCf)
        configs.forEach { c ->
            ecs.submit(Callable {
                val (ip, cf) = dnsCache.getOrPut(c.host) { resolveHost(c.host) }
                if (ip == null) return@Callable null
                val ms = HealthTester.protocolProbe(c, ip, c.port, timeoutMs = 2_200)
                if (ms > 0) Candidate(c, cf, ms) else null
            })
        }
        val deadline = System.currentTimeMillis() + PREFILTER_MS
        val out = ArrayList<Candidate>()
        var done = 0
        while (done < configs.size) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) break
            val f = ecs.poll(left, TimeUnit.MILLISECONDS) ?: break
            done++
            val r = runCatching { f.get() }.getOrNull()
            if (r != null) out.add(r)
        }
        pool.shutdownNow()
        DiagLogger.log("PREFILTER probed=$done/${configs.size} pass=${out.size}")
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
    suspend fun retest(e: ServerEntry): ServerEntry? = withContext(Dispatchers.IO) { testLock.withLock {
        val tunnel = TunnelLauncher.launch(testXray, e) ?: return@withContext null
        try {
            val r = HealthTester.testTunnel(TEST_PORT)
            DiagLogger.log("RETEST ${e.config.host}:${e.config.port} ${e.variantId} -> ${r.error ?: "OK"}")
            if (!r.ok) null
            else e.copy(latencyMs = r.latencyMs, downBps = r.downBps, upBps = r.upBps,
                testedAt = System.currentTimeMillis())
        } finally { tunnel.stop() }
    } }

    /** بازتست موازی یک لیست؛ فقط سالم‌ها (با آمار تازه) برمی‌گردند. */
    suspend fun retestAll(entries: List<ServerEntry>, onProgress: (done: Int, alive: Int) -> Unit = { _, _ -> }): List<ServerEntry> =
        withContext(Dispatchers.IO) {
            val queue = java.util.concurrent.ConcurrentLinkedQueue(entries)
            val alive = java.util.concurrent.ConcurrentLinkedQueue<ServerEntry>()
            val done = java.util.concurrent.atomic.AtomicInteger()
            coroutineScope {
                (0 until WORKERS).map { w ->
                    async(Dispatchers.IO) {
                        val xr = workerXray(w)
                        while (currentCoroutineContext().isActive) {
                            val e = queue.poll() ?: break
                            val tunnel = TunnelLauncher.launch(xr, e)
                            val r = if (tunnel == null) null else try {
                                HealthTester.testTunnel(xr.port, withUpload = false, quick = true)
                            } finally { tunnel.stop() }
                            DiagLogger.log("RETEST ${e.config.host}:${e.config.port} dial=${e.config.dialHost} -> ${r?.error ?: if (r == null) "core_fail" else "OK"}")
                            if (r != null && r.ok) alive.add(e.copy(latencyMs = r.latencyMs, downBps = r.downBps,
                                testedAt = System.currentTimeMillis()))
                            onProgress(done.incrementAndGet(), alive.size)
                        }
                    }
                }.forEach { it.await() }
            }
            alive.toList()
        }

    /** اسکن تازه‌ی IP تمیز کلادفلر: کش بازبینی + اسکن؛ تمیزترین‌ها (کمترین زمان هندشیک+HTTP) اول. */
    suspend fun freshCleanIps(): List<CloudflareScanner.CleanIp> = withContext(Dispatchers.IO) {
        val cached = CloudflareScanner.revalidate(store.loadCleanIps().take(30))
        val fresh = CloudflareScanner.scan(perRange = 10, ports = listOf(443), maxConcurrency = 64, timeoutMs = 2_000)
        val all = (cached + fresh).distinctBy { it.ip }.sortedBy { it.tlsMs }.take(24)
        if (all.isNotEmpty()) store.saveCleanIps(all)
        all
    }

    /**
     * تزریق تمیزترین IP به همه‌ی سرورهای منتخبِ پشت کلادفلر — سریع:
     *  ۱) پروب سبک و موازی (هندشیک TLS + Upgrade وب‌سوکت) از هر سرور × ۶ تمیزترین IP، بدون هسته (~۳ ثانیه)
     *  ۲) برای هر سرور «تمیزترینِ» پروب‌گذشته فقط یک بار با تونل واقعی تأیید می‌شود
     *  ۳) اگر تأیید نشد، آدرس قبلی (که سالم تست شده) حفظ می‌شود
     */
    suspend fun injectCleanIps(
        entries: List<ServerEntry>,
        ips: List<CloudflareScanner.CleanIp>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<ServerEntry> = withContext(Dispatchers.IO) {
        if (ips.isEmpty()) return@withContext entries
        val top = ips.take(6)
        fun portFor(e: ServerEntry, ip: CloudflareScanner.CleanIp) =
            if (e.config.port in CloudflareScanner.CF_PORTS) e.config.port else ip.port

        // ── ۱) پروب موازی ──
        val targets = entries.filter { it.cf && injectable(it.config) }
        val pool = Executors.newFixedThreadPool(48)
        val probes = targets.associate { e ->
            e.serverKey to top.map { ip ->
                pool.submit(Callable { ip to HealthTester.protocolProbe(e.config, ip.ip, portFor(e, ip), 2_200) })
            }
        }
        val chosen = HashMap<String, CloudflareScanner.CleanIp?>()
        for ((k, futs) in probes) {
            chosen[k] = futs.mapNotNull { f -> runCatching { f.get(6, TimeUnit.SECONDS) }.getOrNull() }
                .firstOrNull { it.second > 0 }?.first   // به ترتیب «تمیزترین»
        }
        pool.shutdownNow()
        DiagLogger.log("INJECT_PROBE targets=${targets.size} withIp=${chosen.values.count { it != null }}")

        // ── ۲) تأیید تونل واقعی (موازی با هسته‌های تست) ──
        val queue = java.util.concurrent.ConcurrentLinkedQueue(entries)
        val out = ConcurrentHashMap<String, ServerEntry>()
        val done = java.util.concurrent.atomic.AtomicInteger()
        coroutineScope {
            (0 until WORKERS).map { w ->
                async(Dispatchers.IO) {
                    val xr = workerXray(w)
                    while (currentCoroutineContext().isActive) {
                        val e = queue.poll() ?: break
                        var best = e
                        val ip = chosen[e.serverKey]
                        if (ip != null) {
                            val port = portFor(e, ip)
                            if (e.config.dialHost == ip.ip && e.config.dialPort == port) {
                                best = e
                            } else {
                                val cand = e.copy(config = e.config.withDial(ip.ip, port))
                                val tunnel = TunnelLauncher.launch(xr, cand)
                                val r = if (tunnel == null) null else try {
                                    HealthTester.testTunnel(xr.port, withUpload = false, quick = true)
                                } finally { tunnel.stop() }
                                DiagLogger.log("INJECT ${e.config.host} -> ${ip.ip} ${r?.error ?: if (r == null) "core_fail" else "OK lat=${r.latencyMs}"}")
                                if (r != null && r.ok) best = cand.copy(latencyMs = r.latencyMs,
                                    downBps = r.downBps, testedAt = System.currentTimeMillis())
                            }
                        }
                        out[e.serverKey] = best
                        onProgress(done.incrementAndGet(), entries.size)
                    }
                }
            }.forEach { it.await() }
        }
        entries.mapNotNull { out[it.serverKey] }
    }

    /**
     * تست موازی (WORKERS هسته‌ی مستقل، هرکدام روی پورت خودش) تا [want] سرور سالم پیدا شود.
     *
     * دور اول: هر کاندید (تصادفی) فقط «یک» تلاش با یک پروفایل (آخرین موفق، یا تصادفی)، تست سبک
     * (تاخیر + ۵۰۰KB دانلود). اگر دور اول هیچ سالمی نداد، دور دوم روی بهترین ۴۰ کاندید با همه‌ی پروفایل‌ها.
     * هر سرور سالم بلافاصله با [onFound] گزارش می‌شود (به‌صورت سریالی).
     */
    suspend fun findServers(
        disc: Discovery,
        want: Int,
        skipKeys: Set<String>,
        onFound: suspend (ServerEntry) -> Unit,
        onState: (Progress) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val found = java.util.concurrent.atomic.AtomicInteger()
        val tested = java.util.concurrent.atomic.AtomicInteger()
        val cursor = java.util.concurrent.atomic.AtomicInteger()
        val foundLock = Mutex()
        val rng = SecureRandom()

        fun expired(): Boolean {
            val el = System.currentTimeMillis() - t0
            return found.get() >= want || el > HARD_BUDGET_MS || (el > SOFT_BUDGET_MS && found.get() >= 5)
        }

        suspend fun pass(cands: List<Candidate>, allVariants: Boolean, workerCount: Int) {
            val queue = java.util.concurrent.ConcurrentLinkedQueue(cands)
            kotlinx.coroutines.coroutineScope {
                (0 until workerCount).map { w ->
                    async(Dispatchers.IO) {
                        val xr = workerXray(w)
                        while (currentCoroutineContext().isActive && !expired()) {
                            val cand = queue.poll() ?: break
                            if (ServerEntry(cand.config).serverKey in skipKeys) continue
                            val n = tested.incrementAndGet()
                            onState(Progress(Phase.TESTING, "تست تونل واقعی… ($n/${disc.candidates.size})",
                                disc.totalParsed, disc.cleanIps.size, n, found.get()))
                            val applicable = IranVariants.order(settings.carrierMode, learnedVariant)
                                .filter { !it.needsCf || cand.behindCf }
                            val variants = if (allVariants) applicable
                                else applicable.take(1) // اولین = آخرین موفق یا (در حالت خودکار) مستقیم
                            for (v in variants) {
                                if (!currentCoroutineContext().isActive || expired()) break
                                val dial = pickDial(cand, v, disc.cleanIps, cursor.getAndIncrement(), rng)
                                val entry = ServerEntry(dial, v.id, v.useFrag, cf = cand.behindCf)
                                val tunnel = TunnelLauncher.launch(xr, entry)
                                val res = if (tunnel == null)
                                    HealthTester.TunnelResult(-1, 0.0, 0.0, "core_fail: ${xr.lastError.take(160)}")
                                else try { HealthTester.testTunnel(xr.port, withUpload = false, quick = true) }
                                     finally { tunnel.stop() }
                                DiagLogger.log("${v.id} | ${dial.host}:${dial.port}->${dial.dialHost}/${dial.scheme} | " +
                                    (res.error ?: "OK lat=${res.latencyMs}ms down=${res.downBps.toInt()}B/s"))
                                if (res.error?.startsWith("core_fail") == true)
                                    DiagLogger.log("CONFIG_JSON: ${xr.lastConfigJson.take(500)}")
                                if (res.ok) {
                                    learnedVariant = v.id
                                    foundLock.withLock {
                                        if (found.get() < want) {
                                            found.incrementAndGet()
                                            onFound(entry.copy(latencyMs = res.latencyMs, downBps = res.downBps,
                                                upBps = res.upBps, testedAt = System.currentTimeMillis()))
                                        }
                                    }
                                    break
                                }
                            }
                        }
                    }
                }.forEach { it.await() }
            }
        }

        // تصادفی‌سازی کامل ترتیب کاندیدها (تنوع + جلوگیری از گیر کردن روی یک رنج بد)
        val shuffled = disc.candidates.shuffled(rng)
        pass(shuffled, allVariants = false, workerCount = WORKERS)
        if (found.get() == 0 && currentCoroutineContext().isActive) {
            onState(Progress(Phase.TESTING, "دور دوم: امتحان همه‌ی پروفایل‌ها…", disc.totalParsed, disc.cleanIps.size))
            // تک‌هسته‌ای (محافظ در برابر مشکل احتمالی اجرای هم‌زمان چند هسته) + همه‌ی پروفایل‌ها
            pass(shuffled.take(40), allVariants = true, workerCount = 1)
        }
        found.get()
    }

    private val workers = HashMap<Int, XrayCore>()
    @Synchronized private fun workerXray(i: Int): XrayCore =
        workers.getOrPut(i) { XrayCore(TEST_PORT + 10 + i).also { it.setAppContext(context) } }

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
        const val WORKERS = 4
        const val MAX_TCP_MS = 2_500L
        const val MAX_PREFILTER = 2_500
        const val PROBE_THREADS = 128
        const val PREFILTER_MS = 15_000L
        const val MAX_CANDIDATES = 400
        const val MAX_ATTEMPTS_LEARNED = 2
        const val SOFT_BUDGET_MS = 90_000L
        const val HARD_BUDGET_MS = 180_000L
        const val TARGET_SERVERS = 10
    }
}
