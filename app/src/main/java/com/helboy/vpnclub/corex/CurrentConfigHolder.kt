package com.helboy.vpnclub.corex

import android.content.Context
import java.io.File

/**
 * کانفیگ انتخاب‌شده‌ی فعلی — بین سرویس و UI مشترک.
 * فایل ساده در filesDir؛ سرویس هنگام START می‌خواند.
 */
object CurrentConfigHolder {

    private fun file(ctx: Context) = File(ctx.filesDir, "current_config.link")

    fun save(ctx: Context, config: ProxyConfig) = file(ctx).writeText(config.raw)

    fun load(ctx: Context): ProxyConfig? = try {
        LinkParser.parse(file(ctx).readText().trim())
    } catch (_: Exception) { null }

    fun clear(ctx: Context) = file(ctx).delete()
}
