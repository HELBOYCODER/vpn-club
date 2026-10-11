package com.helboy.vpnclub.ui

import java.util.Locale

private val FA_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

/** ارقام لاتین → فارسی (فقط برای نمایش). */
fun String.fa(): String = map { if (it in '0'..'9') FA_DIGITS[it - '0'] else it }.joinToString("")

fun Int.fa(): String = toString().fa()
fun Long.fa(): String = toString().fa()

/** سرعت بر حسب بایت بر ثانیه → «۱٫۲ مگ/ث». */
fun formatSpeed(bps: Long): String = when {
    bps >= 1_000_000 -> String.format(Locale.US, "%.1f MB/s", bps / 1_000_000.0).fa()
    bps >= 1_000 -> String.format(Locale.US, "%.0f KB/s", bps / 1_000.0).fa()
    else -> "$bps B/s".fa()
}

fun formatBytes(b: Long): String = when {
    b >= 1_000_000_000 -> String.format(Locale.US, "%.2f GB", b / 1_000_000_000.0).fa()
    b >= 1_000_000 -> String.format(Locale.US, "%.1f MB", b / 1_000_000.0).fa()
    b >= 1_000 -> String.format(Locale.US, "%.0f KB", b / 1_000.0).fa()
    else -> "$b B".fa()
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, sec).fa()
}
