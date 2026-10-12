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
 * کانفیگ رسمی (PropagationChannelId / SponsorId / ...) را کاربر از Psiphon Inc. می‌گیرد و
 * در برنامه وارد می‌کند؛ بدون آن سایفون فعال نمی‌شود.
 */
object PsiphonRunner {

    const val LOCAL_SOCKS = 10830

    private val _state = MutableStateFlow(PsiphonState.OFF)
    val state: StateFlow<PsiphonState> = _state.asStateFlow()

    @Volatile private var proc: Process? = null
    @Volatile var lastError: String = ""
        private set

    private fun configFile(ctx: Context) = File(ctx.filesDir, "psiphon_config.json")

    fun hasConfig(ctx: Context): Boolean = loadUserConfig(ctx) != null

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

    /** پروتکل‌های سایفون (مثل MahsaNG): auto = همه، normal = OSSH کلاسیک، conduit = از طریق داوطلبان Conduit (WebRTC). */
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
    internal fun buildRunConfig(user: JSONObject, dataDir: String, upstreamSocks: Int?, st: AppSettings): JSONObject {
        val cfg = JSONObject(user.toString())
            .put("DataRootDirectory", dataDir)
            .put("LocalSocksProxyPort", LOCAL_SOCKS)
            .put("DisableLocalHTTPProxy", true)
            .put("EmitDiagnosticNotices", false)
        if (upstreamSocks != null) cfg.put("UpstreamProxyURL", "socks5://127.0.0.1:$upstreamSocks")
        else cfg.remove("UpstreamProxyURL")
        if (st.psiphonRegion.length == 2) cfg.put("EgressRegion", st.psiphonRegion)
        when (st.psiphonProtocol) {
            "normal" -> cfg.put("LimitTunnelProtocols", org.json.JSONArray(NORMAL_PROTOCOLS))
            "conduit" -> cfg.put("LimitTunnelProtocols", org.json.JSONArray(CONDUIT_PROTOCOLS))
        }
        if (st.psiphonAggressive) cfg.put("AggressiveEstablishment", true)
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
        val user = loadUserConfig(ctx) ?: run { fail("کانفیگ سایفون وارد نشده"); return false }
        val bin = binary(ctx)
        if (!bin.exists()) { fail("موتور سایفون در این نسخه/پردازنده موجود نیست"); return false }
        val dataDir = File(ctx.filesDir, "psiphon").apply { mkdirs() }
        val cfg = buildRunConfig(user, dataDir.absolutePath, upstreamSocks, AppSettings.of(ctx))
        DiagLogger.log("PSIPHON start ${if (upstreamSocks == null) "direct" else "over-server"} " +
            "region=${cfg.optString("EgressRegion", "auto")} protocols=${cfg.optJSONArray("LimitTunnelProtocols")?.length() ?: "all"}")
        val cfgFile = File(dataDir, "run_config.json").apply { writeText(cfg.toString()) }

        _state.value = PsiphonState.CONNECTING
        val connected = CountDownLatch(1)
        return try {
            val p = ProcessBuilder(bin.absolutePath, "-config", cfgFile.absolutePath)
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
