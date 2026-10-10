package com.helboy.vpnclub.corex

import android.content.Context
import java.io.File

/**
 * کانفیگ انتخاب‌شده‌ی فعلی — بین سرویس و UI مشترک.
 * فایل ساده در filesDir؛ سرویس هنگام START می‌خواند.
 *
 * نکته: لینک تزریق‌شده (dial override) ذخیره می‌شود، نه raw خام —
 * وگرنه IP تمیز کلادفلر هنگام راه‌اندازی سرویس گم می‌شود.
 */
object CurrentConfigHolder {

    private fun file(ctx: Context) = File(ctx.filesDir, "current_config.link")

    fun save(ctx: Context, config: ProxyConfig) {
        // اگر تزریق شده، لینک بازسازی‌شده با dial جدید — وگرنه raw اصلی
        file(ctx).writeText(config.toShareLink())
    }

    fun load(ctx: Context): ProxyConfig? = try {
        LinkParser.parse(file(ctx).readText().trim())
    } catch (_: Exception) { null }

    fun clear(ctx: Context) = file(ctx).delete()
}
