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

    /** ترافیک داخل ایران مستقیم برود (geoip:ir). */
    var bypassIran: Boolean
        get() = sp.getBoolean("bypass_iran", false) // پیش‌فرض: تونل کامل (همه‌ی ترافیک)
        set(v) { sp.edit().putBoolean("bypass_iran", v).apply() }

    /** تعویض خودکار سرور هنگام قطعی. */
    var autoFailover: Boolean
        get() = sp.getBoolean("failover", true)
        set(v) { sp.edit().putBoolean("failover", v).apply() }

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

    /** IP منتخب برای اینجکت (خودکار از هر اسکن، یا دستی). */
    var selectedIp: String
        get() = sp.getString("selected_ip", "") ?: ""
        set(v) { sp.edit().putString("selected_ip", v).apply() }

    /** دکمه‌ی اصلی به سریع‌ترین سرور وصل شود (وگرنه بهترین سرور لیست). */
    var fastestMode: Boolean
        get() = sp.getBoolean("fastest", true)
        set(v) { sp.edit().putBoolean("fastest", v).apply() }

    /** اپ‌هایی که از تونل خارج‌اند (split tunneling). */
    var bypassApps: Set<String>
        get() = sp.getStringSet("bypass_apps", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("bypass_apps", v.toSet()).apply() }

    companion object {
        fun of(ctx: Context) = AppSettings(ctx)
    }
}
