package com.helboy.vpnclub.corex

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import hev.htproxy.TProxyService
import java.io.File

/**
 * تونل سراسری: VpnService + hev-socks5-tunnel (کتابخانه‌ی از پیش‌ساخته).
 *
 * ترافیک همه‌ی اپ‌ها → تون TUN → hev (JNI، درون-پردازه) → SOCKS5 محلی
 * (Xray روی 127.0.0.1) → خروجی.
 *
 * از همان کلاس JNI که خودِ v2rayNG استفاده می‌کند صدا زده می‌شود
 * (hev.htproxy.TProxyService) تا نیازی به ndk-build در هر بیلد نباشد.
 */
class Tun2Socks(private val context: Context) {

    private var tun: ParcelFileDescriptor? = null

    /** دلیل آخرین شکست — برای تشخیص «مجوز VPN لغو شده» در کنترلر. */
    @Volatile var lastFailure: String = ""
        private set

    /** نوع آخرین شکست (برای تصمیم‌گیری؛ [lastFailure] فقط برای لاگ است). */
    @Volatile var lastFailureKind: TunFailure? = null
        private set

    /**
     * راه‌اندازی تونل با سه سطح عقب‌نشینی (دستگاه‌های عجیب/سازنده‌های خاص):
     *  ۰) کامل (IPv4+IPv6، اپ‌های مستثنی)  ۱) بدون IPv6  ۲) فقط IPv4 و فقط خود اپ مستثنی.
     * دلیل هر شکست در diag.log ثبت می‌شود.
     */
    fun start(
        service: VpnService,
        socksPort: Int,
        mtu: Int = 1500,
        bypassApps: Set<String> = emptySet()
    ): Boolean {
        stop()
        lastFailure = ""; lastFailureKind = null
        for (tier in 0..2) {
            val ok = try {
                establish(service, socksPort, mtu, bypassApps, ipv6 = tier == 0, withBypass = tier < 2)
            } catch (e: Exception) {
                lastFailure = "${e.javaClass.simpleName}: ${e.message}"
                lastFailureKind = TunFailure.ESTABLISH_EXCEPTION
                Log.e(TAG, "tun start failed", e)
                false
            }
            if (ok) return true
            DiagLogger.log("TUN fail tier$tier ${lastFailureKind} $lastFailure")
            // بدون مجوز، سطح‌های بعدی هم بی‌فایده‌اند
            if (lastFailureKind == TunFailure.PERMISSION_REVOKED) { stop(); return false }
            stop()
        }
        return false
    }

    private fun establish(
        service: VpnService, socksPort: Int, mtu: Int,
        bypassApps: Set<String>, ipv6: Boolean, withBypass: Boolean
    ): Boolean {
        val builder = service.Builder()
            .setMtu(mtu)
            .addAddress(VPN_IP, 30)
            .addRoute("0.0.0.0", 0)
            .setSession("Hellboy VPN")
            .setBlocking(true)
        // DNS انتخابی کاربر (تنظیمات) + یک پشتیبان متفاوت
        val dns = runCatching { AppSettings.of(context).dnsServer }.getOrDefault("1.1.1.1").trim()
        for (d in listOf(dns, if (dns == "8.8.8.8") "1.1.1.1" else "8.8.8.8").distinct())
            runCatching { builder.addDnsServer(d) }.onFailure { DiagLogger.log("TUN dns $d rejected: ${it.message}") }
        if (ipv6) {
            builder.addAddress(VPN_IP6, 126)
            builder.addRoute("::", 0) // نشت IPv6 را می‌بندد
        }
        // سوکت‌های خروجی خود Xray/FragmentProxy نباید وارد TUN شوند (حلقه‌ی مسیریابی)
        runCatching { builder.addDisallowedApplication(context.packageName) }
        if (withBypass) for (pkg in bypassApps) runCatching { builder.addDisallowedApplication(pkg) }
        val fd = builder.establish() ?: run {
            lastFailure = "establish=null"; lastFailureKind = TunFailure.PERMISSION_REVOKED; return false
        }
        tun = fd
        val cfg = File(context.filesDir, "hev-socks5-tunnel.yaml")
        cfg.writeText(buildHevConfig(mtu, socksPort, ipv6))
        val ok = TProxyService.TProxyStartService(cfg.absolutePath, fd.fd)
        if (!ok) { lastFailure = "hev start=false"; lastFailureKind = TunFailure.HEV_FAILED }
        return ok
    }

    fun stop() {
        try { TProxyService.TProxyStopService() } catch (_: Exception) {}
        try { tun?.close() } catch (_: Exception) {}
        tun = null
    }

    val isRunning: Boolean
        get() = try { TProxyService.TProxyIsRunning() } catch (_: Throwable) { false }

    companion object {
        private const val TAG = "Tun2Socks"
        private const val VPN_IP = "26.26.26.1"
        private const val VPN_IP6 = "fd00:26::1"

        /**
         * آمار زنده‌ی تونل: Pair(آپلود، دانلود) بر حسب بایت تجمعی، یا null.
         * hev: [tx_packets, tx_bytes, rx_packets, rx_bytes] (tx = از دستگاه به سمت شبکه).
         */
        fun bytes(): Pair<Long, Long>? = try {
            val s = TProxyService.TProxyGetStats()
            if (s != null && s.size >= 4) s[1] to s[3] else null
        } catch (_: Throwable) { null }
    }

    private fun buildHevConfig(mtu: Int, socksPort: Int, ipv6: Boolean): String = """
tunnel:
  mtu: $mtu
  ipv4: $VPN_IP
${if (ipv6) "  ipv6: '$VPN_IP6'\n" else ""}
socks5:
  port: $socksPort
  address: 127.0.0.1
  udp: 'udp'

misc:
  tcp-read-write-timeout: 300000
  udp-read-write-timeout: 60000
  log-level: warn
""".trimIndent()


}
