package com.helboy.vpnclub.corex

import android.content.Context
import android.content.SharedPreferences

/** تنظیمات کاربر (SharedPreferences ساده). */
class AppSettings(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("vpnclub_settings", Context.MODE_PRIVATE)

    /** auto | mci | irancell | plain */
    var carrierMode: String
        get() = sp.getString("carrier", "auto") ?: "auto"
        set(v) { sp.edit().putString("carrier", v).apply() }

    /** Only modes backed by the installed Xray core are selectable. */
    var connectionMode: ConnectionMode
        get() = ConnectionMode.fromId(sp.getString("connection_mode", "auto"))
        set(v) { sp.edit().putString("connection_mode", v.id).apply() }

    /** ترافیک داخل ایران مستقیم برود (geoip:ir). */
    var bypassIran: Boolean
        get() = sp.getBoolean("bypass_iran", false) // پیش‌فرض: تونل کامل (همه‌ی ترافیک)
        set(v) { sp.edit().putBoolean("bypass_iran", v).apply() }

    /** Lock down traffic while the native transport reconnects. */
    var nativeKillSwitch: Boolean
        get() = sp.getBoolean("native_kill_switch", false)
        set(v) { sp.edit().putBoolean("native_kill_switch", v).apply() }

    /** اتصال خودکار به‌محض باز شدن اپ. */
    var autoConnectOnLaunch: Boolean
        get() = sp.getBoolean("auto_launch", false)
        set(v) { sp.edit().putBoolean("auto_launch", v).apply() }

    /** تم: system | light | dark */
    var themeMode: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) { sp.edit().putString("theme", v).apply() }

    /** ظاهر دکمه‌ی اتصال: hellboy | orb */
    var buttonStyle: String
        get() = sp.getString("button_style", "hellboy") ?: "hellboy"
        set(v) { sp.edit().putString("button_style", v).apply() }

    /** DNS تونل: 1.1.1.1 | 8.8.8.8 | 9.9.9.9 | 94.140.14.14 (ضد تبلیغ) | آدرس دلخواه. */
    var dnsServer: String
        get() = sp.getString("dns_server", "1.1.1.1") ?: "1.1.1.1"
        set(v) { sp.edit().putString("dns_server", v).apply() }

    /** قوانین مسیریابی کاربر (هر خط یک دامنه / IP / CIDR / geosite:… / geoip:…). */
    var directRules: String
        get() = sp.getString("rules_direct", "") ?: ""
        set(v) { sp.edit().putString("rules_direct", v).apply() }

    var blockRules: String
        get() = sp.getString("rules_block", "") ?: ""
        set(v) { sp.edit().putString("rules_block", v).apply() }

    var proxyRules: String
        get() = sp.getString("rules_proxy", "") ?: ""
        set(v) { sp.edit().putString("rules_proxy", v).apply() }

    /** جلوه‌ی خون و آتش روی پوسته. */
    var skinEffects: Boolean
        get() = sp.getBoolean("skin_fx", true)
        set(v) { sp.edit().putBoolean("skin_fx", v).apply() }

    /** اپ‌هایی که از تونل خارج‌اند (split tunneling). */
    var bypassApps: Set<String>
        get() = sp.getStringSet("bypass_apps", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("bypass_apps", v.toSet()).apply() }

    var lanShare: Boolean
        get() = sp.getBoolean("lan_share", false)
        set(v) { sp.edit().putBoolean("lan_share", v).apply() }
    var lanPassword: String
        get() = sp.getString("lan_password", "").orEmpty()
        set(v) { sp.edit().putString("lan_password", v).apply() }

    var favoriteServers: Set<String>
        get() = sp.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("favorites", v.toSet()).apply() }

    /** Remove retired options once, so older gaming/mine-only settings cannot disable recovery. */
    fun clearRetiredSettings() {
        if (sp.getBoolean("smart_only_v43", false)) return
        val edit = sp.edit()
        listOf("psiphon", "psiphon_mode", "psiphon_region", "psiphon_protocol", "psiphon_aggressive",
            "auto_inject", "server_source", "warp_noise", "conn_style", "selected_ip", "fastest", "failover")
            .forEach { edit.remove(it) }
        edit.putBoolean("smart_only_v43", true).apply()
        contextFilesCleanup()
    }

    private val appFiles = context.applicationContext.filesDir
    private fun contextFilesCleanup() {
        listOf("clean_ips.json", "warp_account.json", "psiphon_config.json").forEach {
            runCatching { java.io.File(appFiles, it).delete() }
        }
        runCatching { java.io.File(appFiles, "psiphon").deleteRecursively() }
    }

    companion object {
        fun of(ctx: Context) = AppSettings(ctx)
    }
}
