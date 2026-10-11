package com.helboy.vpnclub.corex

/*
 * خطاهای نوع‌دار — هر تابع دقیقاً همان خطاهایی را برمی‌گرداند که ممکن است رخ دهد
 * (به‌جای رشته‌ی آزاد یا جستجوی کلمه در متن پیام فارسی). تصمیم‌ها بر اساس «نوع» گرفته می‌شوند.
 */

/** نتیجه‌ی تست تاخیر واقعی یک سرور. */
sealed interface DelayResult {
    data class Ok(val ms: Long) : DelayResult

    /** هر شکست، یک متن کوتاه برای لاگ دارد. */
    sealed interface Fail : DelayResult { val log: String }

    /** در مهلت جواب نداد (سرور مرده/فیلتر). ارزش تست دوباره در آینده را دارد. */
    data object Timeout : Fail { override val log = "timeout" }

    /** جواب داد ولی کندتر از سقف قابل‌قبول. */
    data class Slow(val ms: Long) : Fail { override val log get() = "slow ${ms}ms" }

    /** کانفیگ از نظر ساختاری خراب است (هسته آن را نمی‌پذیرد) — تست دوباره بی‌فایده است. */
    data class ConfigError(val detail: String) : Fail { override val log get() = "config: $detail" }

    /** خطای شبکه/سرور گزارش‌شده از هسته (رد اتصال، هندشیک، ...). */
    data class CoreError(val detail: String) : Fail { override val log get() = detail }

    /** ساخت JSON کانفیگ در خود برنامه شکست خورد. */
    data class BuildError(val detail: String) : Fail { override val log get() = "build: $detail" }

    companion object {
        /** خروجی عددی measureOutboundDelay → نتیجه. */
        fun fromDelay(ms: Long, maxMs: Long): DelayResult = when {
            ms in 1..maxMs -> Ok(ms)
            ms > maxMs -> Slow(ms)
            else -> CoreError("delay=$ms")
        }

        /** پیام خطای هسته → نوع خطا (خطای پارس/ساخت کانفیگ Xray با «infra/conf» یا «config» شروع می‌شود). */
        fun fromCoreMessage(msg: String): Fail {
            val m = msg.take(160)
            return if (m.contains("infra/conf", true) || m.contains("failed to parse json", true) ||
                m.contains("config error", true)) ConfigError(m) else CoreError(m)
        }
    }
}

/** چرا تونل سیستم (TUN) ساخته نشد. */
enum class TunFailure {
    /** establish() تهی برگرداند: مجوز VPN گرفته نشده یا اپ VPN دیگری آن را گرفته. */
    PERMISSION_REVOKED,
    /** سیستم هنگام ساخت تونل استثنا داد (مثلاً تنظیم IPv6 روی این دستگاه). */
    ESTABLISH_EXCEPTION,
    /** تونل ساخته شد ولی hev-socks5-tunnel بالا نیامد. */
    HEV_FAILED
}

/** چرا سرویس VPN بالا نیامد. */
sealed interface SvcError {
    data object NoServer : SvcError
    data class Core(val detail: String) : SvcError
    data class Tun(val kind: TunFailure, val detail: String) : SvcError

    /** متن قابل‌فهم برای کاربر. */
    val userMessage: String get() = when (this) {
        NoServer -> "سروری برای اتصال انتخاب نشده"
        is Core -> "هسته‌ی VPN با این سرور بالا نیامد"
        is Tun -> when (kind) {
            TunFailure.PERMISSION_REVOKED -> "مجوز VPN لازم است"
            TunFailure.ESTABLISH_EXCEPTION -> "گوشی اجازه‌ی ساخت تونل نداد"
            TunFailure.HEV_FAILED -> "موتور تونل بالا نیامد"
        }
    }
}
