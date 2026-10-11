package com.helboy.vpnclub.corex

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * سرور فعلی (کانفیگ + IP تزریقی + پروفایل ایران + حالت fragment) — بین سرویس و UI مشترک.
 *
 * به‌جای لینک اشتراک، JSON ذخیره می‌شود: لینک بازسازی‌شده SNI را (وقتی sni/host صریح
 * نبود) به IP تبدیل می‌کرد و IPv6 را بدون براکت می‌نوشت → parse شکست می‌خورد (باگ ≤ v1.6.0).
 */
object CurrentConfigHolder {

    private fun file(ctx: Context) = File(ctx.filesDir, "current_server.json")

    fun save(ctx: Context, entry: ServerEntry) {
        try { file(ctx).writeText(entry.toJson().toString()) } catch (_: Exception) {}
    }

    fun load(ctx: Context): ServerEntry? = try {
        ServerEntry.fromJson(JSONObject(file(ctx).readText()))
    } catch (_: Exception) { null }

    fun clear(ctx: Context) { file(ctx).delete() }
}
