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
        // نیمی از کاندیدها از سرورهای «قابل تزریق IP تمیز» (پشت کلادفلر + ws/grpc/xhttp) تا
        // دکمه‌ی اینجکت روی لیست ۱۰تایی واقعاً اثر داشته باشد
        val (inj, rest) = alive.partition { it.behindCf && injectable(it.config) }
        val (st, weak) = rest.partition { strong(it) }
        val others = st.shuffled(rng) + weak.shuffled(rng)
        val injS = inj.shuffled(rng)
        val ordered = ArrayList<Candidate>()
        var ii = 0; var oi = 0
        while (ii < injS.size || oi < others.size) {
            if (ii < injS.size) ordered.add(injS[ii++])
            if (oi < others.size) ordered.add(others[oi++])
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

    // ─────────────────────────────────────────────────────────────────────────
    // تست تونل واقعی — با Libv2ray.measureOutboundDelay
    //
    // چرا: اجرای هم‌زمان چند CoreController (۴ هسته‌ی تست + هسته‌ی سرویس) احتمالاً باعث
    // کرش native و بسته شدن ناگهانی برنامه می‌شد. measureOutboundDelay همان مسیری است که
    // v2rayNG برای تست موازی صدها سرور استفاده می‌کند: هر تست یک نمونه‌ی موقت بدون inbound.
    // ─────────────────────────────────────────────────────────────────────────

    private val delayPool = Executors.newCachedThreadPool()

    /**
     * تاخیر واقعی (میلی‌ثانیه) یک درخواست HTTPS از داخل تونل این سرور با پروفایل/تزریق خودش.
     * @return -1 = ناسالم
     */
    fun delayTest(e: ServerEntry, timeoutMs: Long = DELAY_TIMEOUT_MS): Long = delayTestR(e, timeoutMs).first

    /** مثل [delayTest] ولی دلیل شکست را هم برمی‌گرداند (برای لاگ). */
    fun delayTestR(e: ServerEntry, timeoutMs: Long = DELAY_TIMEOUT_MS): Pair<Long, String> {
        XrayCore.ensureEnv(context)
        val xr = XrayCore(0)
        xr.setVariant(IranVariants.byId(e.variantId))
        var frag: FragmentProxy? = null
        if (e.useFrag) {
            val f = FragmentProxy(e.config.dialHost, e.config.dialPort)
            if (f.start()) { frag = f; xr.dialOverride = "127.0.0.1:${f.port}" }
        }
        // بدون inbound: تست فقط به outbound نیاز دارد (پورت آزاد/اشغال مهم نیست)
        val json = try {
            org.json.JSONObject(xr.buildConfig(e.config)).apply { remove("inbounds") }.toString()
        } catch (ex: Exception) { frag?.stop(); return -1L to "build: ${ex.message}" }
        return try {
            val fut = delayPool.submit(Callable { libv2ray.Libv2ray.measureOutboundDelay(json, TEST_URL) })
            val ms = fut.get(timeoutMs, TimeUnit.MILLISECONDS)
            when {
                ms in 1..MAX_DELAY_MS -> ms to ""
                ms > MAX_DELAY_MS -> -1L to "slow ${ms}ms"
                else -> -1L to "delay=$ms"
            }
        } catch (_: java.util.concurrent.TimeoutException) {
            -1L to "timeout"
        } catch (ex: Exception) {
            val msg = (ex.cause?.message ?: ex.message ?: ex.javaClass.simpleName).take(120)
            if (msg.contains("infra/conf", true))
                DiagLogger.log("CORE_CONFIG ${e.config.host}: $msg | ${json.take(400)}")
            -1L to msg
        } finally { frag?.stop() }
    }

    /** تست یک ServerEntry (برای تعویض خودکار). null = ناسالم. */
    suspend fun retest(e: ServerEntry): ServerEntry? = withContext(Dispatchers.IO) {
        val ms = delayTest(e)
        DiagLogger.log("RETEST ${e.config.host}:${e.config.port} dial=${e.config.dialHost} -> ${if (ms > 0) "OK ${ms}ms" else "fail"}")
        if (ms > 0) e.copy(latencyMs = ms, testedAt = System.currentTimeMillis()) else null
    }

    /** بازتست موازی یک لیست؛ فقط سالم‌ها (با تاخیر تازه) برمی‌گردند. */
    suspend fun retestAll(entries: List<ServerEntry>, onProgress: (done: Int, alive: Int) -> Unit = { _, _ -> }): List<ServerEntry> =
        withContext(Dispatchers.IO) {
            val done = java.util.concurrent.atomic.AtomicInteger()
            val alive = java.util.concurrent.atomic.AtomicInteger()
            entries.map { e ->
                async(Dispatchers.IO) {
                    val ms = delayTest(e)
                    if (ms > 0) alive.incrementAndGet()
                    onProgress(done.incrementAndGet(), alive.get())
                    if (ms > 0) e.copy(latencyMs = ms, testedAt = System.currentTimeMillis()) else null
                }
            }.mapNotNull { it.await() }
        }

    /** اسکن تازه‌ی IP تمیز کلادفلر: کش بازبینی + اسکن؛ تمیزترین‌ها (کمترین زمان هندشیک+HTTP) اول. */
    suspend fun freshCleanIps(onProgress: (tested: Int, found: Int) -> Unit = { _, _ -> }): List<CloudflareScanner.CleanIp> =
        withContext(Dispatchers.IO) {
            val cached = CloudflareScanner.revalidate(store.loadCleanIps().take(30))
            val fresh = CloudflareScanner.scan(perRange = 12, ports = listOf(443), maxConcurrency = 64,
                timeoutMs = 2_000, onProgress = onProgress)
            val all = (cached + fresh).distinctBy { it.ip }.sortedBy { it.tlsMs }.take(24)
            if (all.isNotEmpty()) store.saveCleanIps(all)
            all
        }

    fun savedCleanIps(): List<CloudflareScanner.CleanIp> = store.loadCleanIps()

    /**
     * تزریق تمیزترین IP به سرورهای پشت کلادفلر:
     *  ۱) پروب سبک و موازی (TLS/WS-101) هر سرور × ۶ تمیزترین IP، بدون هسته
     *  ۲) تمیزترینِ پروب‌گذشته با تونل واقعی تأیید می‌شود؛ اگر نشد آدرس قبلی می‌ماند
     * @return (لیست جدید، تعداد سرورهایی که IP جدید گرفتند)
     */
    suspend fun injectCleanIps(
        entries: List<ServerEntry>,
        ips: List<CloudflareScanner.CleanIp>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Pair<List<ServerEntry>, Int> = withContext(Dispatchers.IO) {
        if (ips.isEmpty()) return@withContext entries to 0
        val top = ips.take(6)
        fun portFor(e: ServerEntry, ip: CloudflareScanner.CleanIp) =
            if (e.config.port in CloudflareScanner.CF_ALL_PORTS) e.config.port else ip.port

        val targets = entries.filter { it.canInject }
        entries.filter { !it.canInject }.forEach { DiagLogger.log("INJECT skip ${it.config.host}: ${it.injectBlocker}") }
        val pool = Executors.newFixedThreadPool(48)
        val probes = targets.associate { e ->
            e.serverKey to top.map { ip ->
                pool.submit(Callable { ip to HealthTester.protocolProbe(e.config, ip.ip, portFor(e, ip), 2_200) })
            }
        }
        val chosen = HashMap<String, CloudflareScanner.CleanIp?>()
        for ((k, futs) in probes) {
            chosen[k] = futs.mapNotNull { f -> runCatching { f.get(6, TimeUnit.SECONDS) }.getOrNull() }
                .firstOrNull { it.second > 0 }?.first
        }
        pool.shutdownNow()
        DiagLogger.log("INJECT_PROBE targets=${targets.size} withIp=${chosen.values.count { it != null }}")

        val done = java.util.concurrent.atomic.AtomicInteger()
        val changed = java.util.concurrent.atomic.AtomicInteger()
        val out = entries.map { e ->
            async(Dispatchers.IO) {
                var best = e
                val ip = chosen[e.serverKey]
                if (ip != null) {
                    val port = portFor(e, ip)
                    if (e.config.dialHost != ip.ip || e.config.dialPort != port) {
                        val cand = e.copy(config = e.config.withDial(ip.ip, port))
                        val ms = delayTest(cand)
                        DiagLogger.log("INJECT ${e.config.host} -> ${ip.ip} ${if (ms > 0) "OK ${ms}ms" else "fail"}")
                        if (ms > 0) { best = cand.copy(latencyMs = ms, testedAt = System.currentTimeMillis()); changed.incrementAndGet() }
                    }
                }
                onProgress(done.incrementAndGet(), entries.size)
                best
            }
        }.map { it.await() }
        out to changed.get()
    }

    /**
     * تست موازی کاندیدها (ترتیب تصادفی) تا [want] سرور سالم پیدا شود.
     * دور اول: هر کاندید یک تلاش با پروفایل اول (آخرین موفق یا «مستقیم»).
     * دور دوم (اگر هیچ نیافت): ۴۰ کاندید با همه‌ی پروفایل‌های فایروال ایران.
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

        suspend fun pass(cands: List<Candidate>, allVariants: Boolean, parallelism: Int) {
            val queue = java.util.concurrent.ConcurrentLinkedQueue(cands)
            coroutineScope {
                (0 until parallelism).map {
                    async(Dispatchers.IO) {
                        while (currentCoroutineContext().isActive && !expired()) {
                            val cand = queue.poll() ?: break
                            if (ServerEntry(cand.config).serverKey in skipKeys) continue
                            val n = tested.incrementAndGet()
                            onState(Progress(Phase.TESTING, "تست تونل واقعی… ($n/${disc.candidates.size})",
                                disc.totalParsed, disc.cleanIps.size, n, found.get()))
                            val applicable = IranVariants.order(settings.carrierMode, learnedVariant)
                                .filter { !it.needsCf || cand.behindCf }
                            val variants = if (allVariants) applicable else applicable.take(1)
                            for (v in variants) {
                                if (!currentCoroutineContext().isActive || expired()) break
                                val dial = pickDial(cand, v, disc.cleanIps, cursor.getAndIncrement(), rng)
                                val entry = ServerEntry(dial, v.id, v.useFrag, cf = cand.behindCf)
                                val (ms, why) = delayTestR(entry)
                                DiagLogger.log("${v.id} | ${dial.host}:${dial.port}->${dial.dialHost}/${dial.scheme} | " +
                                    if (ms > 0) "OK ${ms}ms" else "fail $why")
                                if (ms > 0) {
                                    learnedVariant = v.id
                                    foundLock.withLock {
                                        if (found.get() < want) {
                                            found.incrementAndGet()
                                            onFound(entry.copy(latencyMs = ms, testedAt = System.currentTimeMillis()))
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

        val shuffled = disc.candidates.shuffled(rng)
        pass(shuffled, allVariants = false, parallelism = PARALLEL_TESTS)
        if (found.get() == 0 && currentCoroutineContext().isActive) {
            onState(Progress(Phase.TESTING, "دور دوم: همه‌ی پروفایل‌های فایروال ایران…", disc.totalParsed, disc.cleanIps.size))
            pass(shuffled.take(40), allVariants = true, parallelism = PARALLEL_TESTS)
        }
        found.get()
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
        val port = if (c.port in CloudflareScanner.CF_ALL_PORTS) c.port else ip.port
        return c.withDial(ip.ip, port)
    }

    /** تزریق IP فقط وقتی معنی دارد که ترنسپورت از CDN رد شود و TLS باشد (نه Reality/TCP خام). */
    private fun injectable(c: ProxyConfig): Boolean = ServerEntry.injectableTransport(c)

    companion object {
        const val PARALLEL_TESTS = 8
        const val TEST_URL = "https://cp.cloudflare.com/generate_204"
        const val DELAY_TIMEOUT_MS = 8_000L
        const val MAX_DELAY_MS = 4_000L
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
