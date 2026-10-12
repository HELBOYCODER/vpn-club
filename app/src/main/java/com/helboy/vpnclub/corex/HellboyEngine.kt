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
class HellboyEngine(private val context: Context) {

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
        val mine = SubStore(context).load().map { it.url }
        val (pair, cleanIps) = coroutineScope {
            // حالت «فقط ساب‌های من»: ساب‌های عمومی اصلاً دریافت نمی‌شوند
            val onlyMine = settings.serverSource == "mine" && mine.isNotEmpty()
            val subs = async { if (onlyMine) emptyList() else fetcher.fetchAll(defaultSubscriptions) }
            val my = async { if (mine.isEmpty()) emptyList() else fetcher.fetchAll(mine) }
            val ips = async { findCleanIps(onState) }
            (subs.await() to my.await()) to ips.await()
        }
        val (publicRaws, myRaws) = pair
        // سرورهای «ساب‌های من» اول و همیشه در نمونه (نه قربانی انتخاب تصادفی)
        val myParsed = myRaws.mapNotNull { runCatching { LinkParser.parse(it) }.getOrNull() }.filter { XrayCore.isSupported(it) }
        DiagLogger.log("DISCOVER mySubs=${mine.size} myConfigs=${myParsed.size} public=${publicRaws.size}")
        val raws = myRaws + publicRaws

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
        val myKeys = myParsed.map { ServerEntry(it).serverKey }.toSet()
        val (myPart, publicPart) = parsed.partition { ServerEntry(it).serverKey in myKeys }
        val sample = if (parsed.size > MAX_PREFILTER)
            myPart.take(MAX_PREFILTER / 2) + publicPart.shuffled(SecureRandom()).take(MAX_PREFILTER - minOf(myPart.size, MAX_PREFILTER / 2))
        else parsed
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
    fun delayTest(e: ServerEntry, timeoutMs: Long = DELAY_TIMEOUT_MS): Long =
        (delayTestR(e, timeoutMs) as? DelayResult.Ok)?.ms ?: -1L

    /** کانفیگ‌هایی که هسته از نظر ساختاری رد کرد (در این اجرا دوباره تست نمی‌شوند). */
    private val brokenConfigs: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** تست تاخیر با خطای نوع‌دار — نگاه کنید به [DelayResult]. */
    fun delayTestR(e: ServerEntry, timeoutMs: Long = DELAY_TIMEOUT_MS): DelayResult {
        if (e.id in brokenConfigs) return DelayResult.ConfigError("cached")
        XrayCore.ensureEnv(context)
        val xr = XrayCore(0)
        xr.setVariant(IranVariants.byId(e.variantId))
        var frag: FragmentProxy? = null
        if (e.useFrag) {
            val f = FragmentProxy(e.config.dialHost, e.config.dialPort)
            if (f.start()) { frag = f; xr.dialOverride = "127.0.0.1:${f.port}" }
        }
        // بدون inbound: تست فقط به outbound نیاز دارد
        val json = try {
            org.json.JSONObject(xr.buildConfig(e.config)).apply { remove("inbounds") }.toString()
        } catch (ex: Exception) { frag?.stop(); return DelayResult.BuildError(ex.message ?: ex.javaClass.simpleName) }
        return try {
            val fut = delayPool.submit(Callable { libv2ray.Libv2ray.measureOutboundDelay(json, TEST_URL) })
            DelayResult.fromDelay(fut.get(timeoutMs, TimeUnit.MILLISECONDS), MAX_DELAY_MS)
        } catch (_: java.util.concurrent.TimeoutException) {
            DelayResult.Timeout
        } catch (ex: Exception) {
            val r = DelayResult.fromCoreMessage(ex.cause?.message ?: ex.message ?: ex.javaClass.simpleName)
            if (r is DelayResult.ConfigError) {
                brokenConfigs.add(e.id)
                DiagLogger.log("CORE_CONFIG ${e.config.host}: ${r.detail} | ${json.take(400)}")
            }
            r
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
            val t0 = System.currentTimeMillis()
            val prev = store.loadCleanIps().take(30)
            val cached = CloudflareScanner.revalidate(prev)
            DiagLogger.log("SCAN_CACHE revalidated ${cached.size}/${prev.size}")
            val fresh = CloudflareScanner.scan(perRange = 12, ports = listOf(443), maxConcurrency = 64,
                timeoutMs = 2_000, onProgress = onProgress)
            val all = (cached + fresh).distinctBy { it.ip }.sortedBy { it.tlsMs }.take(24)
            DiagLogger.log("SCAN_DONE fresh=${fresh.size} total=${all.size} in ${System.currentTimeMillis() - t0}ms")
            all.forEachIndexed { i, ip -> DiagLogger.log("SCAN_IP #${i + 1} ${ip.ip}:${ip.port} ${ip.tlsMs}ms colo=${ip.colo.ifEmpty { "?" }}") }
            if (all.isNotEmpty()) store.saveCleanIps(all)
            all
        }

    fun savedCleanIps(): List<CloudflareScanner.CleanIp> = store.loadCleanIps()

    /**
     * انتخاب IP منتخب: از بین [candidates] (سریع‌ترین‌ها اول) هر کدام با اینترنت خود گوشی ۳ بار پینگ
     * می‌شود؛ اولین IP پایدار (هر ۳ بار جواب) با کمترین میانه برنده است. null = هیچ‌کدام پایدار نبود.
     */
    suspend fun pickBestIp(candidates: List<CloudflareScanner.CleanIp>): CloudflareScanner.CleanIp? =
        withContext(Dispatchers.IO) {
            val top = candidates.take(6)
            val pinged = top.map { c -> async(Dispatchers.IO) { c to CloudflareScanner.ping(c.ip, c.port, tries = 3) } }.map { it.await() }
            pinged.forEach { (c, p) -> DiagLogger.log("PICK_IP ping ${c.ip} scan=${c.tlsMs}ms direct=${p?.tlsMs?.let { "${it}ms" } ?: "FAIL"} colo=${p?.colo ?: c.colo}") }
            pinged.mapNotNull { it.second }.minByOrNull { it.tlsMs }
        }

    /** پینگ مستقیم یک IP (دستی) با اینترنت گوشی. */
    suspend fun pingIp(ip: String): CloudflareScanner.CleanIp? = withContext(Dispatchers.IO) {
        CloudflareScanner.ping(ip).also { DiagLogger.log("PING_IP $ip -> ${it?.let { r -> "${r.tlsMs}ms colo=${r.colo}" } ?: "FAIL"}") }
    }

    /** نتیجه‌ی اینجکت روی یک سرور (برای نمایش و لاگ). */
    data class InjectResult(
        val serverKey: String, val name: String, val ip: String,
        val ok: Boolean, val ms: Long, val reason: String
    )

    data class InjectOutcome(val list: List<ServerEntry>, val changed: Int, val report: List<InjectResult>)

    /**
     * اینجکت IP منتخب به سرورهای لیست (۱۰ سرور برتر):
     *  ۱) سرورهای غیرقابل‌تزریق (Reality/مستقیم) با دلیل رد می‌شوند
     *  ۲) پروب سبک پروتکل (TLS با SNI سرور / WS-101) روی IP — بدون هسته
     *  ۳) تست تونل واقعی (هسته‌ی Xray) با اینترنت گوشی؛ فقط اگر پینگ داد اعمال می‌شود
     * هر مرحله با دلیل دقیق لاگ می‌شود.
     */
    suspend fun injectCleanIps(
        entries: List<ServerEntry>,
        ips: List<CloudflareScanner.CleanIp>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): InjectOutcome = withContext(Dispatchers.IO) {
        if (ips.isEmpty()) return@withContext InjectOutcome(entries, 0, emptyList())
        val top = ips.take(6)
        fun portFor(e: ServerEntry, ip: CloudflareScanner.CleanIp) =
            if (e.config.port in CloudflareScanner.CF_ALL_PORTS) e.config.port else ip.port
        fun nameOf(e: ServerEntry) = e.config.tag.ifEmpty { e.config.host }.take(32)
        DiagLogger.section("INJECT ${top.joinToString { it.ip }} -> ${entries.size} servers")

        val report = java.util.concurrent.ConcurrentHashMap<String, InjectResult>()
        val targets = entries.filter { it.canInject }
        entries.filter { !it.canInject }.forEach {
            val why = it.injectBlocker ?: "غیرقابل تزریق"
            DiagLogger.log("INJECT_SKIP ${it.config.scheme} ${it.config.host}:${it.config.port} net=${it.config.query.toMap()["type"] ?: "tcp"} sec=${it.config.query.toMap()["security"] ?: "-"} cf=${it.cf} -> $why")
            report[it.serverKey] = InjectResult(it.serverKey, nameOf(it), "", false, -1, why)
        }
        val pool = Executors.newFixedThreadPool(48)
        val probes = targets.associate { e ->
            e.serverKey to top.map { ip ->
                pool.submit(Callable { ip to HealthTester.protocolProbe(e.config, ip.ip, portFor(e, ip), 2_200) })
            }
        }
        val chosen = HashMap<String, CloudflareScanner.CleanIp?>()
        for ((k, futs) in probes) {
            val res = futs.mapNotNull { f -> runCatching { f.get(6, TimeUnit.SECONDS) }.getOrNull() }
            val e = targets.first { it.serverKey == k }
            DiagLogger.log("INJECT_PROBE ${e.config.host} sni=${e.config.sniHost} " + res.joinToString(" ") { (ip, ms) -> "${ip.ip}=${if (ms > 0) "${ms}ms" else "x"}" })
            chosen[k] = res.firstOrNull { it.second > 0 }?.first
        }
        pool.shutdownNow()
        DiagLogger.log("INJECT_PROBE_SUM targets=${targets.size} withIp=${chosen.values.count { it != null }}")

        val done = java.util.concurrent.atomic.AtomicInteger()
        val changed = java.util.concurrent.atomic.AtomicInteger()
        val out = entries.map { e ->
            async(Dispatchers.IO) {
                var best = e
                if (e.canInject) {
                    val ip = chosen[e.serverKey]
                    if (ip == null) {
                        val why = "سرور روی IP ${top.first().ip} جواب TLS/WebSocket نداد (دامنه شاید روی کلادفلر نیست یا فیلتر است)"
                        DiagLogger.log("INJECT_FAIL ${e.config.host} probe: no response on ${top.joinToString { it.ip }}")
                        report[e.serverKey] = InjectResult(e.serverKey, nameOf(e), top.first().ip, false, -1, why)
                    } else {
                        val port = portFor(e, ip)
                        val cand = e.copy(config = e.config.withDial(ip.ip, port))
                        val r = delayTestR(cand)
                        when (r) {
                            is DelayResult.Ok -> {
                                best = cand.copy(latencyMs = r.ms, testedAt = System.currentTimeMillis())
                                if (e.config.dialHost != ip.ip || e.config.dialPort != port) changed.incrementAndGet()
                                DiagLogger.log("INJECT_OK ${e.config.host} -> ${ip.ip}:$port tunnel=${r.ms}ms variant=${e.variantId ?: "-"}")
                                report[e.serverKey] = InjectResult(e.serverKey, nameOf(e), ip.ip, true, r.ms, "پینگ تونل ${r.ms}ms با اینترنت گوشی")
                            }
                            is DelayResult.Fail -> {
                                DiagLogger.log("INJECT_FAIL ${e.config.host} -> ${ip.ip}:$port tunnel: ${r.log}")
                                report[e.serverKey] = InjectResult(e.serverKey, nameOf(e), ip.ip, false, -1,
                                    "پروب گذشت ولی تونل واقعی پینگ نداد (${r.log.take(60)}) — آدرس قبلی حفظ شد")
                            }
                        }
                    }
                }
                onProgress(done.incrementAndGet(), entries.size)
                best
            }
        }.map { it.await() }
        val ordered = entries.mapNotNull { report[it.serverKey] }
        DiagLogger.log("INJECT_SUM ok=${ordered.count { it.ok }} fail=${ordered.count { !it.ok }} changed=${changed.get()}")
        InjectOutcome(out, changed.get(), ordered)
    }

    /** دریافت کانفیگ‌های یک ساب (با تونل اگر وصلیم). */
    suspend fun fetchSub(url: String): List<ProxyConfig> = withContext(Dispatchers.IO) {
        val lines = fetcher.fetchOne(url)
        lines.mapNotNull { LinkParser.parse(it) }.distinctBy { ServerEntry(it).serverKey }
            .also { DiagLogger.log("SUB_FETCH ${java.net.URI(url).host} lines=${lines.size} parsed=${it.size}") }
    }

    /**
     * تست سرورهای یک ساب، مثل اسکنر اصلی:
     *  دور ۱: همه با پروفایل «مستقیم» (سریع)
     *  دور ۲: سرورهای CDN (ws/grpc/… پشت TLS) که جواب ندادند — مثل ورکرهای BPB/Zeus روی workers.dev که
     *         SNI آن‌ها در ایران فیلتر است — با IP منتخب + پروفایل‌های فرگمنت ایران؛ اولین ترکیب سالم ذخیره می‌شود.
     */
    suspend fun testMany(cfgs: List<ProxyConfig>, source: String, onProgress: (done: Int, alive: Int) -> Unit,
                         preferredIp: String? = null): List<Pair<ServerEntry, DelayResult>> =
        withContext(Dispatchers.IO) {
            val done = java.util.concurrent.atomic.AtomicInteger()
            val alive = java.util.concurrent.atomic.AtomicInteger()
            val gate = kotlinx.coroutines.sync.Semaphore(8)
            val first = cfgs.map { c ->
                async(Dispatchers.IO) {
                    gate.acquire()
                    try {
                        val (e, r) = testConfig(c)
                        if (r is DelayResult.Ok) alive.incrementAndGet()
                        onProgress(done.incrementAndGet(), alive.get())
                        e.copy(source = source) to r
                    } finally { gate.release() }
                }
            }.map { it.await() }.toMutableList()

            // دور ۲: فرگمنت + IP تمیز برای سرورهای CDN ناموفق
            val retry = first.withIndex().filter { (_, p) ->
                p.second !is DelayResult.Ok && p.second !is DelayResult.ConfigError && injectable(p.first.config) &&
                    (p.first.config.query.toMap()["security"] == "tls" || p.first.config.query.toMap()["tls"] == "tls")
            }.take(16)
            if (retry.isNotEmpty()) DiagLogger.log("SUB_TEST pass2 frag/cleanIp for ${retry.size} CDN servers (ip=${preferredIp ?: "-"})")
            val variants = IranVariants.order(settings.carrierMode, learnedVariant).filter { it.id != "plain" }
            retry.map { (idx, p) ->
                async(Dispatchers.IO) {
                    gate.acquire()
                    try {
                        val base = p.first
                        val c = base.config
                        val port = if (c.port in CloudflareScanner.CF_ALL_PORTS) c.port else 443
                        val dials = listOfNotNull(preferredIp?.let { c.withDial(it, port) }, c)
                        val combos = buildList {
                            preferredIp?.let { add(IranVariants.PLAIN to c.withDial(it, port)) }
                            for (v in variants.filter { !it.needsCf || base.cf }.take(2)) for (d in dials) add(v to d)
                        }.take(4)
                        for ((v, d) in combos) {
                            val e = base.copy(config = d, variantId = v.id, useFrag = v.useFrag)
                            val r = delayTestR(e)
                            DiagLogger.log("SUB_TEST ${v.id} ${c.host}:${c.port} dial=${d.dialHost} -> ${when (r) { is DelayResult.Ok -> "OK ${r.ms}ms"; is DelayResult.Fail -> "fail ${r.log}" }}")
                            if (r is DelayResult.Ok) {
                                learnedVariant = v.id
                                first[idx] = e.copy(latencyMs = r.ms, testedAt = System.currentTimeMillis()) to r
                                onProgress(done.get(), alive.incrementAndGet())
                                break
                            }
                        }
                    } finally { gate.release() }
                }
            }.forEach { it.await() }
            first
        }

    /**
     * تست یک کانفیگ ویرایش‌شده/واردشده: تشخیص «پشت کلادفلر» با DNS و تست تونل واقعی
     * با اینترنت گوشی. پروفایل فایروال [variantId] حفظ می‌شود (برای ویرایش).
     */
    suspend fun testConfig(cfg: ProxyConfig, variantId: String? = null, useFrag: Boolean = false): Pair<ServerEntry, DelayResult> =
        withContext(Dispatchers.IO) {
            val (_, cf) = resolveHost(cfg.host)
            val e = ServerEntry(cfg, variantId, useFrag, cf = cf)
            val r = delayTestR(e)
            DiagLogger.log("TEST_CONFIG ${cfg.scheme} ${cfg.host}:${cfg.port} cf=$cf variant=${variantId ?: "-"} -> ${when (r) { is DelayResult.Ok -> "OK ${r.ms}ms"; is DelayResult.Fail -> "fail ${r.log}" }}")
            e.copy(latencyMs = (r as? DelayResult.Ok)?.ms ?: -1L, testedAt = System.currentTimeMillis()) to r
        }

    /**
     * اینجکت دستی یک IP به یک سرور (انتخاب کاربر): آدرس dial عوض می‌شود و نتیجه‌ی تست واقعی
     * در latencyMs ثبت می‌شود (−۱ = جواب نداد). تصمیم اعمال با فراخواننده است.
     */
    suspend fun injectOne(e: ServerEntry, ip: String): ServerEntry = withContext(Dispatchers.IO) {
        val port = if (e.config.port in CloudflareScanner.CF_ALL_PORTS) e.config.port else 443
        val cand = e.copy(config = e.config.withDial(ip, port))
        val r = delayTestR(cand)
        DiagLogger.log("INJECT_MANUAL ${e.config.host} -> $ip:$port ${when (r) { is DelayResult.Ok -> "OK ${r.ms}ms"; is DelayResult.Fail -> "fail ${r.log}" }}")
        cand.copy(latencyMs = (r as? DelayResult.Ok)?.ms ?: -1L, testedAt = System.currentTimeMillis())
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
        onState: (Progress) -> Unit,
        /** IP منتخب کاربر (از اسکنر) — سرورهای کلادفلر با همین IP تست می‌شوند. */
        preferredIp: String? = null,
        /** حداقل سرورِ «قابل اینجکت» در لیست، اگر کاندیدش وجود داشته باشد. */
        minInjectable: Int = 3
    ): Int = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val found = java.util.concurrent.atomic.AtomicInteger()
        val foundInj = java.util.concurrent.atomic.AtomicInteger()
        val injLeft = java.util.concurrent.atomic.AtomicInteger(
            disc.candidates.count { it.behindCf && injectable(it.config) })
        val cleanForDial = preferredIp?.takeIf { it.isNotBlank() }
            ?.let { listOf(CloudflareScanner.CleanIp(it, 443, 0)) } ?: disc.cleanIps
        val tested = java.util.concurrent.atomic.AtomicInteger()
        val cursor = java.util.concurrent.atomic.AtomicInteger()
        val foundLock = Mutex()
        val rng = SecureRandom()

        fun expired(): Boolean {
            val el = System.currentTimeMillis() - t0
            // قبلاً با ۵ سرور بعد از ۹۰ ثانیه متوقف می‌شد (لاگ کاربر) — حالا تا ۸ ادامه می‌دهد
            return found.get() >= want || el > HARD_BUDGET_MS || (el > SOFT_BUDGET_MS && found.get() >= 8)
        }

        suspend fun pass(cands: List<Candidate>, allVariants: Boolean, parallelism: Int) {
            val queue = java.util.concurrent.ConcurrentLinkedQueue(cands)
            coroutineScope {
                (0 until parallelism).map {
                    async(Dispatchers.IO) {
                        while (currentCoroutineContext().isActive && !expired()) {
                            val cand = queue.poll() ?: break
                            val candInj = cand.behindCf && injectable(cand.config)
                            if (candInj) injLeft.decrementAndGet()
                            if (ServerEntry(cand.config).serverKey in skipKeys) continue
                            val n = tested.incrementAndGet()
                            onState(Progress(Phase.TESTING, "تست تونل واقعی… ($n/${disc.candidates.size})",
                                disc.totalParsed, disc.cleanIps.size, n, found.get()))
                            val applicable = IranVariants.order(settings.carrierMode, learnedVariant)
                                .filter { !it.needsCf || cand.behindCf }
                            val variants = if (allVariants) applicable else applicable.take(1)
                            for (v in variants) {
                                if (!currentCoroutineContext().isActive || expired()) break
                                val dial = pickDial(cand, v, cleanForDial, cursor.getAndIncrement(), rng, preferredIp != null)
                                val entry = ServerEntry(dial, v.id, v.useFrag, cf = cand.behindCf)
                                val r = delayTestR(entry)
                                DiagLogger.log("${v.id} | ${dial.host}:${dial.port}->${dial.dialHost}/${dial.scheme} | " +
                                    when (r) { is DelayResult.Ok -> "OK ${r.ms}ms"; is DelayResult.Fail -> "fail ${r.log}" })
                                val ms = (r as? DelayResult.Ok)?.ms ?: -1L
                                // کانفیگ خراب با هیچ پروفایلی درست نمی‌شود — بقیه‌ی پروفایل‌ها را امتحان نکن
                                if (r is DelayResult.ConfigError) break
                                if (ms > 0) {
                                    learnedVariant = v.id
                                    foundLock.withLock {
                                        // سهمیه: جای چند سرور قابل‌اینجکت خالی بماند تا اینجکت روی لیست اثر داشته باشد
                                        val reserve = (minInjectable - foundInj.get()).coerceAtLeast(0)
                                        val nonInjFull = !candInj && injLeft.get() > 0 &&
                                            (found.get() - foundInj.get()) >= want - reserve
                                        if (found.get() < want && !nonInjFull) {
                                            found.incrementAndGet()
                                            if (candInj) foundInj.incrementAndGet()
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
        cleanIps: List<CloudflareScanner.CleanIp>, cursor: Int, rng: SecureRandom,
        pinned: Boolean = false
    ): ProxyConfig {
        val c = cand.config
        if (!cand.behindCf || !injectable(c)) return c
        if (v.ipv6 && v.ipv6Prefix != null) return c.withDial(IranVariants.randomIpv6(v.ipv6Prefix, rng), c.port)
        // با IP منتخب کاربر همیشه همان را تزریق کن؛ بدون آن، گاهی آدرس اصلی را هم امتحان کن
        if (!pinned && v.id == "plain" && cursor % 3 == 2) return c
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
