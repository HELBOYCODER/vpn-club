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

    /** سایفون روی سرور (فقط با کانفیگ رسمی واردشده). */
    var psiphonEnabled: Boolean
        get() = sp.getBoolean("psiphon", false)
        set(v) { sp.edit().putBoolean("psiphon", v).apply() }

    /** حالت سایفون: chain = روی سرور (ترافیک ← سایفون ← سرور)، direct = سایفون تنها بدون سرور. */
    var psiphonMode: String
        get() = sp.getString("psiphon_mode", "chain") ?: "chain"
        set(v) { sp.edit().putString("psiphon_mode", v).apply() }

    /** کشور خروجی سایفون (کد دوحرفی؛ خالی = خودکار). */
    var psiphonRegion: String
        get() = sp.getString("psiphon_region", "") ?: ""
        set(v) { sp.edit().putString("psiphon_region", v).apply() }

    /** پروتکل سایفون: auto | normal | conduit */
    var psiphonProtocol: String
        get() = sp.getString("psiphon_protocol", "auto") ?: "auto"
        set(v) { sp.edit().putString("psiphon_protocol", v).apply() }

    /** اتصال تهاجمی سایفون (تلاش هم‌زمان بیشتر، اتصال سریع‌تر، مصرف بیشتر). */
    var psiphonAggressive: Boolean
        get() = sp.getBoolean("psiphon_aggressive", false)
        set(v) { sp.edit().putBoolean("psiphon_aggressive", v).apply() }

    /** بعد از هر اسکن، IP منتخب خودکار روی ۱۰ سرور برتر اینجکت و با اینترنت گوشی تأیید شود. */
    var autoInjectAfterScan: Boolean
        get() = sp.getBoolean("auto_inject", true)
        set(v) { sp.edit().putBoolean("auto_inject", v).apply() }

    /** منبع سرورها: auto = ساب‌های عمومی + ساب‌های من؛ mine = فقط ساب‌های من (اگر ساب نداریم، خودکار). */
    var serverSource: String
        get() = sp.getString("server_source", "auto") ?: "auto"
        set(v) { sp.edit().putString("server_source", v).apply() }

    /** جلوه‌ی خون و آتش روی پوسته. */
    var skinEffects: Boolean
        get() = sp.getBoolean("skin_fx", true)
        set(v) { sp.edit().putBoolean("skin_fx", v).apply() }

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
