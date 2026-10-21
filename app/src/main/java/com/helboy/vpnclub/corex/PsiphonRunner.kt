package com.helboy.vpnclub.corex

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

enum class PsiphonState { OFF, CONNECTING, ON, FAILED }

/**
 * سایفون «روی» سرور فعلی: ترافیک ← سایفون ← (UpstreamProxy) هسته‌ی Xray ← سرور.
 *
 * موتور سایفون (psiphon-tunnel-core ConsoleClient) به‌صورت برنامه‌ی جدا از nativeLibraryDir
 * اجرا می‌شود (libpsiphon.so — tools/build_psiphon.sh). اعلان‌های JSON آن از stderr خوانده
 * می‌شود: «Tunnels» با count>0 یعنی وصل شد.
 *
 * حالت رایگان (پیش‌فرض، مثل PattNG/Aether): کانال عمومی FFFF… + لیست عمومی و امضاشده‌ی سرورهای خود
 * سایفون (RemoteServerListURLs + کلید امضای سایفون) + لیست همراه اپ (assets/psiphon_servers.dat، با -serverList)
 * تا اولین اتصال بدون دانلود لیست ممکن باشد. موتور: فورک «شیر و خورشید» با اسکن لبه‌های CDN.
 * کانفیگ رسمی واردشده توسط کاربر (اختیاری) روی این پیش‌فرض‌ها ادغام می‌شود.
 */
object PsiphonRunner {

    const val LOCAL_SOCKS = 10830

    private val _state = MutableStateFlow(PsiphonState.OFF)
    val state: StateFlow<PsiphonState> = _state.asStateFlow()

    @Volatile private var proc: Process? = null
    @Volatile var lastError: String = ""
        private set

    private fun configFile(ctx: Context) = File(ctx.filesDir, "psiphon_config.json")

    /** سایفون همیشه قابل اجراست (حالت رایگان)؛ کانفیگ کاربر فقط اختیاری است. */
    fun hasConfig(ctx: Context): Boolean = true

    /** کاربر کانفیگ رسمی خودش را وارد کرده؟ */
    fun hasUserConfig(ctx: Context): Boolean = loadUserConfig(ctx) != null

    // ── حالت رایگان: مقادیر عمومی سایفون (همان PattNG/Aether) ──
    private const val PUBLIC_CHANNEL = "FFFFFFFFFFFFFFFF"
    private const val SERVER_LIST_URL = "https://s3.amazonaws.com//psiphon/web/mjr4-p23r-puwl/server_list_compressed"
    private const val SERVER_LIST_KEY = "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH" +
        "5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5" +
        "OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42Kcot" +
        "LFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6" +
        "/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7G" +
        "stZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1O" +
        "geF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8" +
        "u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz" +
        "31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xal" +
        "KxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM="

    /** پروتکل‌ها (همان دسته‌های Aether). CDN = عبور از لبه‌های CDN (fronting) — بهترین برای ایران. */
    private val CDN_PROTOCOLS = listOf("FRONTED-MEEK-CDN-OSSH", "FRONTED-MEEK-CDN-HTTP-OSSH", "FRONTED-MEEK-CDN-QUIC-OSSH")
    private val NON_INPROXY_PROTOCOLS = listOf("SSH", "OSSH", "TLS-OSSH", "UNFRONTED-MEEK-OSSH", "UNFRONTED-MEEK-HTTPS-OSSH",
        "UNFRONTED-MEEK-SESSION-TICKET-OSSH", "QUIC-OSSH", "SHADOWSOCKS-OSSH", "FRONTED-MEEK-OSSH", "FRONTED-MEEK-CDN-OSSH",
        "FRONTED-MEEK-HTTP-OSSH", "FRONTED-MEEK-CDN-HTTP-OSSH", "FRONTED-MEEK-QUIC-OSSH", "FRONTED-MEEK-CDN-QUIC-OSSH")
    /** روی سرور (UpstreamProxy SOCKS) فقط پروتکل‌های TCP. */
    private val CHAINED_PROTOCOLS = listOf("SSH", "OSSH", "TLS-OSSH", "UNFRONTED-MEEK-OSSH", "UNFRONTED-MEEK-HTTPS-OSSH",
        "UNFRONTED-MEEK-SESSION-TICKET-OSSH", "SHADOWSOCKS-OSSH", "FRONTED-MEEK-OSSH", "FRONTED-MEEK-CDN-OSSH",
        "FRONTED-MEEK-HTTP-OSSH", "FRONTED-MEEK-CDN-HTTP-OSSH")

    /** کانفیگ پایه‌ی رایگان (بدون کانفیگ رسمی). */
    internal fun publicBaseConfig(): JSONObject = JSONObject()
        .put("PropagationChannelId", PUBLIC_CHANNEL)
        .put("SponsorId", PUBLIC_CHANNEL)
        .put("ClientPlatform", "Android_HellboyVPN")
        .put("ClientVersion", "1")
        .put("RemoteServerListURLs", org.json.JSONArray().put(JSONObject()
            .put("URL", java.util.Base64.getEncoder().encodeToString(SERVER_LIST_URL.toByteArray()))))
        .put("RemoteServerListSignaturePublicKey", SERVER_LIST_KEY)
        .put("FrontedMeekCDNScanUseBuiltInSpec", true)

    /**
     * لیست سرور همراه اپ (فشرده‌ی امضاشده) را یک بار باز می‌کند: zlib ← JSON ← data (هر خط یک سرور).
     * @return فایل برای -serverList، یا null
     */
    private fun bundledServerList(ctx: Context): File? = try {
        val out = File(ctx.filesDir, "psiphon/server_list.txt")
        val stamp = File(ctx.filesDir, "psiphon/server_list.ver")
        val ver = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime.toString()
        if (!out.exists() || stamp.takeIf { it.exists() }?.readText() != ver) {
            val raw = ctx.assets.open("psiphon_servers.dat").use { it.readBytes() }
            val json = java.util.zip.InflaterInputStream(raw.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
            val data = JSONObject(json).getString("data")
            out.parentFile?.mkdirs(); out.writeText(data); stamp.writeText(ver)
            DiagLogger.log("PSIPHON bundled server list unpacked: ${data.lines().count { it.isNotBlank() }} entries")
        }
        out
    } catch (e: Exception) { DiagLogger.log("PSIPHON bundled list unavailable: ${e.message}"); null }

    fun binaryAvailable(ctx: Context): Boolean = binary(ctx).let { it.exists() && it.canExecute() }

    private fun binary(ctx: Context) = File(ctx.applicationInfo.nativeLibraryDir, "libpsiphon.so")

    private fun loadUserConfig(ctx: Context): JSONObject? = try {
        JSONObject(configFile(ctx).readText()).takeIf { validate(it) == null }
    } catch (_: Exception) { null }

    /** null = معتبر؛ وگرنه دلیل. */
    fun validate(o: JSONObject): String? = when {
        o.optString("PropagationChannelId").isBlank() -> "PropagationChannelId ندارد"
        o.optString("SponsorId").isBlank() -> "SponsorId ندارد"
        else -> null
    }

    /** ذخیره‌ی کانفیگ واردشده توسط کاربر. null = موفق؛ وگرنه دلیل خطا. */
    fun saveUserConfig(ctx: Context, text: String): String? {
        val o = try { JSONObject(text.trim()) } catch (e: Exception) { return "JSON نامعتبر است" }
        validate(o)?.let { return it }
        configFile(ctx).writeText(o.toString())
        return null
    }

    fun clearUserConfig(ctx: Context) { configFile(ctx).delete() }

    /** پروتکل‌های سایفون (مثل MahsaNG/Aether): auto، cdn (fronting)، normal = OSSH کلاسیک، conduit = داوطلبان Conduit (WebRTC). */
    private val NORMAL_PROTOCOLS = listOf("SSH", "OSSH", "TLS-OSSH", "QUIC-OSSH", "SHADOWSOCKS-OSSH")
    private val CONDUIT_PROTOCOLS = listOf(
        "INPROXY-WEBRTC-OSSH", "INPROXY-WEBRTC-UNFRONTED-MEEK-HTTPS-OSSH",
        "INPROXY-WEBRTC-UNFRONTED-MEEK-SESSION-TICKET-OSSH", "INPROXY-WEBRTC-FRONTED-MEEK-OSSH",
        "INPROXY-WEBRTC-FRONTED-MEEK-HTTP-OSSH", "INPROXY-WEBRTC-QUIC-OSSH")

    /** کشورهای خروجی رایج سایفون (کد دوحرفی؛ "" = خودکار/بهترین). */
    val REGIONS = listOf("" to "خودکار", "DE" to "آلمان", "NL" to "هلند", "GB" to "انگلیس", "FR" to "فرانسه",
        "US" to "آمریکا", "CA" to "کانادا", "SE" to "سوئد", "FI" to "فنلاند", "AT" to "اتریش", "CH" to "سوئیس",
        "SG" to "سنگاپور", "JP" to "ژاپن", "IN" to "هند")

    /** کانفیگ نهایی اجرا: کانفیگ کاربر + تنظیمات اپ. [upstreamSocks] = null یعنی سایفون مستقیم (بدون سرور زیرین). */
    internal fun buildRunConfig(user: JSONObject?, dataDir: String, upstreamSocks: Int?, st: AppSettings): JSONObject =
        buildRunConfig(user, dataDir, upstreamSocks, st.psiphonRegion, st.psiphonProtocol, st.psiphonAggressive)

    internal fun buildRunConfig(user: JSONObject?, dataDir: String, upstreamSocks: Int?, region: String, protocol: String,
                                aggressive: Boolean): JSONObject {
        // پایه‌ی رایگان؛ کانفیگ رسمی کاربر (اگر هست) کلیدهایش را بازنویسی می‌کند
        val cfg = publicBaseConfig()
        user?.keys()?.forEach { k -> cfg.put(k, user.get(k)) }
        // Conduit فقط با in-proxy؛ بقیه بدون in-proxy (مثل Aether)
        if (protocol != "conduit") {
            cfg.put("InproxyTunnelProtocolPreferProbability", 0.0).put("InproxyTunnelProtocolSelectionProbability", 0.0)
        }
        when {
            protocol == "normal" -> cfg.put("LimitTunnelProtocols", org.json.JSONArray(NORMAL_PROTOCOLS))
            protocol == "conduit" -> cfg.put("LimitTunnelProtocols", org.json.JSONArray(CONDUIT_PROTOCOLS))
            upstreamSocks != null -> cfg.put("LimitTunnelProtocols", org.json.JSONArray(CHAINED_PROTOCOLS))
            protocol == "cdn" -> cfg.put("LimitTunnelProtocols", org.json.JSONArray(CDN_PROTOCOLS)).put("DisableTactics", true)
            else -> cfg.put("LimitTunnelProtocols", org.json.JSONArray(NON_INPROXY_PROTOCOLS))
        }
        cfg.put("DataRootDirectory", dataDir)
            .put("LocalSocksProxyPort", LOCAL_SOCKS)
            .put("DisableLocalHTTPProxy", true)
            .put("EmitDiagnosticNotices", false)
        if (upstreamSocks != null) cfg.put("UpstreamProxyURL", "socks5://127.0.0.1:$upstreamSocks")
        else cfg.remove("UpstreamProxyURL")
        if (region.length == 2) cfg.put("EgressRegion", region)
        if (aggressive) cfg.put("AggressiveEstablishment", true)
        if (!cfg.has("DNSResolverAlternateServers"))
            cfg.put("DNSResolverAlternateServers", org.json.JSONArray(listOf("1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4")))
        return cfg
    }

    /**
     * سایفون را بالا می‌آورد و تا [timeoutMs] منتظر اتصال می‌ماند.
     * [upstreamSocks] = پورت SOCKS هسته (سایفون روی سرور)؛ null = سایفون مستقیم.
     * @return true اگر وصل شد (SOCKS محلی روی [LOCAL_SOCKS] آماده است)
     */
    fun start(ctx: Context, upstreamSocks: Int?, timeoutMs: Long = 75_000): Boolean {
        stop()
        val user = loadUserConfig(ctx)
        val bin = binary(ctx)
        if (!bin.exists()) { fail("موتور سایفون در این نسخه/پردازنده موجود نیست"); return false }
        val dataDir = File(ctx.filesDir, "psiphon").apply { mkdirs() }
        val cfg = buildRunConfig(user, dataDir.absolutePath, upstreamSocks, AppSettings.of(ctx))
        DiagLogger.log("PSIPHON start ${if (upstreamSocks == null) "direct" else "over-server"} config=${if (user != null) "user" else "free/public"} " +
            "region=${cfg.optString("EgressRegion", "auto")} protocols=${cfg.optJSONArray("LimitTunnelProtocols")?.length() ?: "all"}")
        val cfgFile = File(dataDir, "run_config.json").apply { writeText(cfg.toString()) }

        _state.value = PsiphonState.CONNECTING
        val connected = CountDownLatch(1)
        return try {
            val args = mutableListOf(bin.absolutePath, "-config", cfgFile.absolutePath, "-dataRootDirectory", dataDir.absolutePath)
            bundledServerList(ctx)?.let { args += listOf("-serverList", it.absolutePath) }
            val p = ProcessBuilder(args)
                .directory(dataDir)
                .start()
            proc = p
            Thread {
                try {
                    p.errorStream.bufferedReader().forEachLine { line -> handleNotice(line, connected) }
                } catch (_: Exception) {}
                if (_state.value == PsiphonState.ON || _state.value == PsiphonState.CONNECTING) {
                    if (proc === p) fail("سایفون متوقف شد")
                }
            }.apply { isDaemon = true; name = "psiphon-notices" }.start()
            Thread { try { p.inputStream.readBytes() } catch (_: Exception) {} }.apply { isDaemon = true }.start()

            val ok = connected.await(timeoutMs, TimeUnit.MILLISECONDS) && _state.value == PsiphonState.ON
            if (!ok) { if (lastError.isEmpty()) lastError = "سایفون در زمان مقرر وصل نشد"; stop(); _state.value = PsiphonState.FAILED }
            ok
        } catch (e: Exception) {
            fail("اجرای موتور سایفون: ${e.message}"); stop(); false
        }
    }

    private fun handleNotice(line: String, connected: CountDownLatch) {
        val o = try { JSONObject(line) } catch (_: Exception) { return }
        val type = o.optString("noticeType")
        val data = o.optJSONObject("data") ?: JSONObject()
        when (type) {
            "Tunnels" -> {
                val n = data.optInt("count")
                if (n > 0) { _state.value = PsiphonState.ON; lastError = ""; connected.countDown() }
                else if (_state.value == PsiphonState.ON) _state.value = PsiphonState.CONNECTING
            }
            "UpstreamProxyError" -> lastError = "خطای سرور زیرین: ${data.optString("message").take(120)}"
            "InternalError", "Alert" -> DiagLogger.log("PSIPHON $type ${data.optString("message").take(160)}")
            "ActiveTunnel" -> DiagLogger.log("PSIPHON active ${data.optString("protocol")} ${data.optString("region")}")
            "Exiting" -> connected.countDown()
        }
    }

    private fun fail(msg: String) {
        lastError = msg
        _state.value = PsiphonState.FAILED
        DiagLogger.log("PSIPHON fail $msg")
    }

    fun stop() {
        val p = proc ?: run { if (_state.value != PsiphonState.FAILED) _state.value = PsiphonState.OFF; return }
        proc = null
        try {
            p.destroy()
            if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly()
        } catch (_: Exception) {}
        if (_state.value != PsiphonState.FAILED) _state.value = PsiphonState.OFF
    }
}
