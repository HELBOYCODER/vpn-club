package com.helboy.vpnclub.corex

/**
 * راه‌انداز مشترک تست و سرویس: یک [ServerEntry] را دقیقاً یکسان بالا می‌آورد
 * (پروفایل ایران + FragmentProxy + dial تزریقی).
 */
class LaunchedTunnel(private val xray: XrayCore, private val frag: FragmentProxy?) {
    fun stop() {
        xray.stop()
        frag?.stop()
        xray.dialOverride = null
        xray.setVariant(null)
    }
}

object TunnelLauncher {

    /** @return null اگر هسته بالا نیامد. در این حالت [XrayCore.lastError] دلیل را دارد. */
    fun launch(xray: XrayCore, e: ServerEntry): LaunchedTunnel? {
        val variant = IranVariants.byId(e.variantId)
        xray.setVariant(variant)
        var frag: FragmentProxy? = null
        xray.dialOverride = null
        if (e.useFrag) {
            val f = FragmentProxy(e.config.dialHost, e.config.dialPort)
            if (f.start()) { frag = f; xray.dialOverride = "127.0.0.1:${f.port}" }
        }
        val ok = try { xray.start(e.config) } catch (_: Exception) { false }
        if (!ok) {
            xray.stop()
            frag?.stop()
            xray.dialOverride = null
            xray.setVariant(null)
            return null
        }
        return LaunchedTunnel(xray, frag)
    }
}
