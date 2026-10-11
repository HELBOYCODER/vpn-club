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

    /** راه‌اندازی تونل — پس از گرفتن مجوز VpnService. */
    fun start(
        service: VpnService,
        socksPort: Int,
        mtu: Int = 1500,
        bypassApps: Set<String> = emptySet()
    ): Boolean {
        stop()
        return try {
            val builder = service.Builder()
                .setMtu(mtu)
                .addAddress(VPN_IP, 30)
                .addAddress(VPN_IP6, 126)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0) // نشت IPv6 را می‌بندد
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setSession("VPN CLUB")
                .setBlocking(true)
            // ── باگ ریشه‌ای ≤ v1.6.0: سوکت‌های خروجی خود Xray (و FragmentProxy) هم وارد TUN
            // می‌شدند → حلقه‌ی مسیریابی → «متصل» ولی هیچ دیتایی رد نمی‌شد.
            // با کنار گذاشتن خود اپ از تونل، ارتباط هسته با سرور مستقیم می‌رود.
            runCatching { builder.addDisallowedApplication(context.packageName) }
            for (pkg in bypassApps) runCatching { builder.addDisallowedApplication(pkg) }
            val fd = builder.establish() ?: return false
            tun = fd

            val cfg = File(context.filesDir, "hev-socks5-tunnel.yaml")
            cfg.writeText(buildHevConfig(mtu, socksPort))
            val ok = TProxyService.TProxyStartService(cfg.absolutePath, fd.fd)
            if (!ok) stop()
            ok
        } catch (e: Exception) {
            Log.e(TAG, "tun start failed", e)
            stop()
            false
        }
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

    private fun buildHevConfig(mtu: Int, socksPort: Int): String = """
tunnel:
  mtu: $mtu
  ipv4: $VPN_IP
  ipv6: '$VPN_IP6'

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
