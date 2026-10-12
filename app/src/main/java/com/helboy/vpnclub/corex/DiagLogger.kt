package com.helboy.vpnclub.corex

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * لاگ تشخیصی روی خود گوشی (filesDir/diag.log) — برای عیب‌یابی بدون adb.
 *
 * هر خط: «زمان با میلی‌ثانیه | thread | پیام». برچسب‌های اصلی:
 *  SESSION / NET      سربرگ اجرا: نسخه، گوشی، اندروید، نوع شبکه، تنظیمات
 *  SCAN_*             اسکن IP تمیز (هر IP پیدا شده با زمان TLS و colo)
 *  PICK_IP            انتخاب IP منتخب (خودکار یا دستی) + پینگ مستقیم با اینترنت گوشی
 *  INJECT_*           اینجکت: پروب پروتکل و تست تونل هر سرور با دلیل دقیق شکست
 *  RETEST / ACTIVATE  تست سرورها و اتصال، با نوع خطا
 *  SERVICE / PSIPHON  سرویس VPN و سایفون
 * اسرار (uuid، رمز، کلید) قبل از نوشتن حذف می‌شوند تا لاگ قابل اشتراک باشد.
 */
object DiagLogger {
    private const val MAX_LINES = 3000
    private const val FILE = "diag.log"

    @Volatile var appContext: Context? = null
    private val lock = Any()
    private var writes = 0

    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    /** آخرین n خط لاگ. */
    fun tail(n: Int): List<String> = try {
        val ctx = appContext
        if (ctx == null) emptyList() else synchronized(lock) { File(ctx.filesDir, FILE).readLines().takeLast(n) }
    } catch (_: Exception) { emptyList() }

    /** کل لاگ به‌صورت متن (برای کپی). */
    fun fullText(): String = tail(MAX_LINES).joinToString("\n")

    fun clear() { try { appContext?.let { synchronized(lock) { File(it.filesDir, FILE).delete() } } } catch (_: Exception) {} }

    /** فایل لاگ برای اشتراک (کپی در cache تا FileProvider بتواند بدهد). */
    fun exportFile(ctx: Context): File? = try {
        val out = File(ctx.cacheDir, "logs").apply { mkdirs() }.let { File(it, "hellboy-vpn-log.txt") }
        out.writeText(fullText()); out
    } catch (_: Exception) { null }

    /** اسرار از لاگ حذف می‌شوند — لاگ قابل کپی/اشتراک است. */
    internal fun redact(m: String): String = m
        .replace(Regex("\"(id|password|user|publicKey|shortId|pass)\"\\s*:\\s*\"[^\"]*\""), "\"$1\":\"***\"")
        .replace(Regex("(vless|vmess|trojan|ss|hysteria2|hy2|socks5?)://[^\\s@/]+@"), "$1://***@")
        .replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), "<uuid>")

    fun log(rawMsg: String) {
        val msg = redact(rawMsg)
        try {
            val ctx = appContext ?: return
            synchronized(lock) {
                val f = File(ctx.filesDir, FILE)
                f.appendText("${fmt.format(Date())} | ${Thread.currentThread().name.take(14)} | $msg\n")
                if (++writes % 200 == 0) {
                    val lines = f.readLines()
                    if (lines.size > MAX_LINES) f.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
                }
            }
        } catch (_: Exception) {}
    }

    /** جداکننده‌ی خوانا برای شروع هر کار (اسکن، اینجکت، اتصال…). */
    fun section(title: String) = log("════════ $title ════════")

    /** سربرگ اجرا: نسخه، گوشی، شبکه، تنظیمات — اولین چیزی که برای دیباگ لازم است. */
    fun sessionHeader(ctx: Context) {
        val ver = try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            "${pi.versionName} (${if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()})"
        } catch (_: Exception) { "?" }
        section("SESSION start")
        log("SESSION app=$ver device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.joinToString(",")}")
        log("NET ${networkSummary(ctx)}")
        val st = AppSettings.of(ctx)
        log("SETTINGS carrier=${st.carrierMode} bypassIran=${st.bypassIran} failover=${st.autoFailover} fastest=${st.fastestMode} " +
            "autoInject=${st.autoInjectAfterScan} selectedIp=${st.selectedIp.ifEmpty { "-" }} psiphon=${st.psiphonEnabled}/${st.psiphonMode}/${st.psiphonProtocol}/${st.psiphonRegion.ifEmpty { "auto" }} bypassApps=${st.bypassApps.size}")
    }

    /** نوع شبکه‌ی زیرین (سیم‌کارت/وای‌فای) و اینکه VPN فعال است یا نه. */
    fun networkSummary(ctx: Context): String = try {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps == null) "none" else buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
            add("validated=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")
            add("down=${caps.linkDownstreamBandwidthKbps}kbps")
        }.joinToString(" ")
    } catch (e: Exception) { "err ${e.javaClass.simpleName}" }
}
