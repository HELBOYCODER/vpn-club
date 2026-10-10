package com.helboy.vpnclub.corex

import android.content.Context
import java.io.File

/**
 * کانفیگ انتخاب‌شده‌ی فعلی + پروفایل ایران (ECH / IPv6 / F&F) — بین سرویس و UI مشترک.
 *
 * نکته: لینک تزریق‌شده (dial override) ذخیره می‌شود، نه raw خام —
 * وگرنه IP تمیز کلادفلر هنگام راه‌اندازی سرویس گم می‌شود.
 * پروفایل ایران هم جدا ذخیره می‌شود چون فقط در JSON هسته ست می‌شود (نه در لینک).
 */
object CurrentConfigHolder {

    private fun file(ctx: Context) = File(ctx.filesDir, "current_config.link")
    private fun variantFile(ctx: Context) = File(ctx.filesDir, "current_variant.id")

    fun save(ctx: Context, config: ProxyConfig, variantId: String? = null) {
        // اگر تزریق شده، لینک بازسازی‌شده با dial جدید — وگرنه raw اصلی
        file(ctx).writeText(config.toShareLink())
        if (variantId.isNullOrBlank()) variantFile(ctx).delete()
        else variantFile(ctx).writeText(variantId)
    }

    fun load(ctx: Context): ProxyConfig? = try {
        LinkParser.parse(file(ctx).readText().trim())
    } catch (_: Exception) { null }

    /** پروفایل ایرانِ ذخیره‌شده (اگر اتصال از مسیر موتور آمده باشد). */
    fun loadVariant(ctx: Context): IranVariants.Variant? = try {
        val id = variantFile(ctx).readText().trim()
        IranVariants.all().firstOrNull { it.id == id }
    } catch (_: Exception) { null }

    fun clear(ctx: Context) {
        file(ctx).delete()
        variantFile(ctx).delete()
    }
}
