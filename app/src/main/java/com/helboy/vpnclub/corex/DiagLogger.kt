package com.helboy.vpnclub.corex

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * لاگ تشخیصی روی خود گوشی (filesDir/diag.log) — برای عیب‌یابی بدون adb.
 * هر تست تونل یک خط: زمان | variant | host:port | نتیجه/خطا.
 * آخرین ۲۰۰ خط نگه داشته می‌شود.
 */
object DiagLogger {
    @Volatile var appContext: Context? = null

    fun log(msg: String) {
        try {
            val ctx = appContext ?: return
            val f = File(ctx.filesDir, "diag.log")
            val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
            f.appendText("$ts | $msg\n")
            // نگه‌داری ۲۰۰ خط آخر
            val lines = f.readLines()
            if (lines.size > 200) f.writeText(lines.takeLast(200).joinToString("\n") + "\n")
        } catch (_: Exception) {}
    }
}
