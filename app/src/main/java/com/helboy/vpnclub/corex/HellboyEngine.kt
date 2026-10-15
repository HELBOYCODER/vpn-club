package com.helboy.vpnclub.corex

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Visible discovery phases of the automatic connection session. */
enum class Phase { IDLE, FETCHING, TESTING, CONNECTING, CONNECTED, FAILED }

/** Fetch fresh subscriptions on every scan, then test actual encrypted tunnel requests. */
class HellboyEngine(private val context: Context) {
    private val fetcher = SubscriptionFetcher()
    private val store = ServerStore(context)
    private val settings = AppSettings.of(context)
    private val delayPool = Executors.newFixedThreadPool(PARALLEL_TESTS)
    private val brokenConfigs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var learnedVariant: String? = null

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
    )

    /**
     * لیست‌های «تست‌شده» (لایه‌ی ۰ و ۱ ZeroNet): کانفیگ‌هایی که قبل از انتشار از داخل ایران/سرور تست
     * شده‌اند. مثل «ساب‌های من» همیشه در نمونه‌ی غربال جلوتر می‌آیند تا کمتر وقت روی لیست‌های بزرگ برود.
     * jsDelivr اول، چون وقتی raw.githubusercontent بسته است اغلب باز است.
     */
    val prioritySubscriptions = listOf(
        "https://cdn.jsdelivr.net/gh/zeghostwriter/ZeroNet@crowd-data/verified.txt",
        "https://raw.githubusercontent.com/sinavm/SVM/main/lite/subscriptions/xray/base64/mix",
        "https://raw.githubusercontent.com/4n0nymou3/multi-proxy-config-fetcher/main/configs/proxy_configs_tested.txt",
        "https://raw.githubusercontent.com/MatinGhanbari/v2ray-configs/main/subscriptions/v2ray/super-sub.txt",
        "https://raw.githubusercontent.com/yebekhe/vpn-fail/main/sub-link",
        "https://raw.githubusercontent.com/Mahdi0024/ProxyCollector/master/sub/proxies.txt",
        "https://raw.githubusercontent.com/MrAbolfazlNorouzi/iran-configs/main/configs/working-configs.txt",
        "https://raw.githubusercontent.com/morteza-v2/free-v2ray-irancell-config/main/Sub1.txt",
        "https://raw.githubusercontent.com/SoliSpirit/SolVPN/main/all_configs.txt",
        "https://raw.githubusercontent.com/miladtahanian/Config-Collector/main/mixed_iran.txt",
    )


    data class Progress(val phase: Phase, val message: String, val configCount: Int = 0, val found: Int = 0)
    fun savedServers(): List<ServerEntry> = store.loadServers().filter { supported(it.config) }
    fun saveServers(list: List<ServerEntry>) {
        // An offline/empty scan must not erase the last useful bootstrap pool.
        if (list.isNotEmpty()) store.saveServers(list)
    }

    suspend fun scan(excluded: Set<String>, onState: (Progress) -> Unit): List<ServerEntry> = withContext(Dispatchers.IO) {
        onState(Progress(Phase.FETCHING, "دریافت تازهٔ سرورها…"))
        val (priorityRaw, publicRaw) = coroutineScope {
            val priority = async { runInterruptible { fetcher.fetchAll(prioritySubscriptions) } }
            val public = async { runInterruptible { fetcher.fetchAll(defaultSubscriptions) } }
            priority.await() to public.await()
        }
        currentCoroutineContext().ensureActive()
        fun parse(raw: List<String>) = raw.mapNotNull { runCatching { LinkParser.parse(it) }.getOrNull() }
            .filter { supported(it) && ServerEntry(it).serverKey !in excluded }
            .distinctBy { ServerEntry(it).serverKey }
        val priority = parse(priorityRaw)
        val public = parse(publicRaw)
        val saved = savedServers().filter { it.serverKey !in excluded }
        // Reserve space for verified feeds instead of losing them in a tiny random sample.
        // Retest the complete saved entry: its successful dial address and carrier profile matter.
        val candidates = DiscoverySelection.select(saved, priority.shuffled().map { ServerEntry(it) },
            public.shuffled().map { ServerEntry(it) }, MAX_CANDIDATES) { it.serverKey }
        onState(Progress(Phase.TESTING, "غربال دسترسی ${candidates.size} سرور…", priority.size + public.size))
        val reachable = prefilter(candidates)
        val sample = reachable.take(MAX_TUNNEL_TESTS)
        onState(Progress(Phase.TESTING, "تست واقعی ${sample.size} سرور…", candidates.size))
        val queue = java.util.concurrent.ConcurrentLinkedQueue(sample)
        val found = mutableListOf<ServerEntry>()
        val lock = Mutex()
        val tested = java.util.concurrent.atomic.AtomicInteger()
        val deadline = android.os.SystemClock.elapsedRealtime() + SCAN_BUDGET_MS
        // Complete native environment initialization before parallel measurements start.
        XrayCore.ensureEnv(context)
        coroutineScope {
            repeat(PARALLEL_TESTS) {
                launch(Dispatchers.IO) {
                    while (isActive && android.os.SystemClock.elapsedRealtime() < deadline) {
                        if (lock.withLock { found.size >= TARGET_SERVERS }) break
                        val candidate = queue.poll() ?: break
                        val (entry, result) = if (candidate.variantId != null || candidate.config.isInjected)
                            candidate.let {
                                val measured = runInterruptible { delayTestR(it) }
                                it.copy(latencyMs = (measured as? DelayResult.Ok)?.ms ?: -1,
                                    testedAt = System.currentTimeMillis()) to measured
                            }
                        else testConfig(candidate.config, allowFallback = false)
                        val count = tested.incrementAndGet()
                        if (result is DelayResult.Ok) lock.withLock {
                            if (found.size < TARGET_SERVERS && found.none { it.serverKey == entry.serverKey }) found += entry
                        }
                        val size = lock.withLock { found.size }
                        onState(Progress(Phase.TESTING, "تست $count/${sample.size} · سالم: $size", candidates.size, size))
                    }
                }
            }
        }
        // Exhaust cheap plain tests first; one dead endpoint must not consume three full timeouts.
        // If the plain pass found nothing, restore the carrier fallbacks removed by the refactor.
        if (found.isEmpty()) {
            val fallbackQueue = java.util.concurrent.ConcurrentLinkedQueue(sample.take(40))
            coroutineScope {
                repeat(PARALLEL_TESTS) {
                    launch(Dispatchers.IO) {
                        while (isActive && lock.withLock { found.size < TARGET_SERVERS }) {
                            val candidate = fallbackQueue.poll() ?: break
                            if (!ServerEntry.injectableTransport(candidate.config)) continue
                            val (entry, result) = testConfig(candidate.config, skipPlain = true)
                            if (result is DelayResult.Ok) lock.withLock {
                                if (found.size < TARGET_SERVERS && found.none { it.serverKey == entry.serverKey }) found += entry
                            }
                            onState(Progress(Phase.TESTING, "تست روش‌های جایگزین · سالم: ${lock.withLock { found.size }}", candidates.size))
                        }
                    }
                }
            }
        }
        found.sortedBy { it.latencyMs }.also { DiagLogger.log("SCAN candidates=${candidates.size} reachable=${reachable.size} tested=${tested.get()} healthy=${it.size}") }
    }

    private suspend fun prefilter(candidates: List<ServerEntry>): List<ServerEntry> = coroutineScope {
        val queue = java.util.concurrent.ConcurrentLinkedQueue(candidates.withIndex().toList())
        val alive = java.util.concurrent.ConcurrentLinkedQueue<IndexedValue<ServerEntry>>()
        val deadline = android.os.SystemClock.elapsedRealtime() + 20_000
        repeat(64) {
            launch(Dispatchers.IO) {
                while (isActive && android.os.SystemClock.elapsedRealtime() < deadline) {
                    val item = queue.poll() ?: break
                    val cfg = item.value.config
                    // UDP protocols have no TCP listener; a TCP probe must never discard them.
                    if (cfg.scheme in listOf("hysteria2", "hy2") ||
                        runInterruptible { HealthTester.tcpPing(cfg.dialHost, cfg.dialPort, 1_500) } > 0) alive += item
                }
            }
        }
        // await children before taking the snapshot
        coroutineContext[Job]!!.children.toList().joinAll()
        alive.sortedBy { it.index }.map { it.value }
    }

    private fun supported(c: ProxyConfig) = XrayCore.isSupported(c) && c.scheme != "wireguard"

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
        val json = try { org.json.JSONObject(xr.buildConfig(e.config)).apply { remove("inbounds") }.toString() }
            catch (ex: Exception) { frag?.stop(); return DelayResult.BuildError(ex.message.orEmpty()) }
        var future: java.util.concurrent.Future<Long>? = null
        return try {
            future = delayPool.submit(Callable { libv2ray.Libv2ray.measureOutboundDelay(json, TEST_URL) })
            DelayResult.fromDelay(future.get(timeoutMs, TimeUnit.MILLISECONDS), MAX_DELAY_MS)
        } catch (_: java.util.concurrent.TimeoutException) {
            future?.cancel(true)
            DelayResult.Timeout
        } catch (_: InterruptedException) {
            future?.cancel(true)
            Thread.currentThread().interrupt()
            DelayResult.Timeout
        } catch (ex: Exception) {
            val result = DelayResult.fromCoreMessage(ex.cause?.message ?: ex.message.orEmpty())
            if (result is DelayResult.ConfigError) brokenConfigs += e.id
            result
        } finally { frag?.stop() }
    }

    suspend fun testConfig(cfg: ProxyConfig, variantId: String? = null, useFrag: Boolean = false, allowFallback: Boolean = true, skipPlain: Boolean = false): Pair<ServerEntry, DelayResult> = withContext(Dispatchers.IO) {
        var entry = ServerEntry(cfg, variantId, useFrag)
        var result: DelayResult = if (skipPlain) DelayResult.Timeout else runInterruptible { delayTestR(entry) }
        currentCoroutineContext().ensureActive()
        // Keep the existing carrier fragmentation profiles, without WARP or clean-IP scanning.
        if (allowFallback && result !is DelayResult.Ok && result !is DelayResult.ConfigError && ServerEntry.injectableTransport(cfg)) {
            for (v in IranVariants.order(settings.carrierMode, learnedVariant).filter { it.id != "plain" && !it.ipv6 }) {
                currentCoroutineContext().ensureActive()
                entry = ServerEntry(cfg, v.id, v.useFrag)
                result = runInterruptible { delayTestR(entry) }
                if (result is DelayResult.Ok) { learnedVariant = v.id; break }
                if (result is DelayResult.ConfigError) break
            }
        }
        entry.copy(latencyMs = (result as? DelayResult.Ok)?.ms ?: -1, testedAt = System.currentTimeMillis()) to result
    }

    companion object {
        const val TARGET_SERVERS = 5
        const val PARALLEL_TESTS = 8
        const val MAX_CANDIDATES = 2_500
        const val MAX_TUNNEL_TESTS = 400
        const val SCAN_BUDGET_MS = 90_000L
        const val TEST_URL = "https://www.gstatic.com/generate_204"
        const val DELAY_TIMEOUT_MS = 8_000L
        const val MAX_DELAY_MS = 4_000L
    }
}
