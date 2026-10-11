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
    /** آخرین n خط لاگ. */
    fun tail(n: Int): List<String> = try {
        val ctx = appContext
        if (ctx == null) emptyList() else File(ctx.filesDir, "diag.log").readLines().takeLast(n)
    } catch (_: Exception) { emptyList() }

    fun clear() { try { appContext?.let { File(it.filesDir, "diag.log").delete() } } catch (_: Exception) {} }

    @Volatile var appContext: Context? = null

    /** اسرار (uuid/رمز/کلید) از لاگ حذف می‌شوند — لاگ قابل کپی/اشتراک است. */
    private fun redact(m: String): String = m
        .replace(Regex("\"(id|password|user|publicKey|shortId)\"\\s*:\\s*\"[^\"]*\""), "\"$1\":\"***\"")

    fun log(rawMsg: String) {
        val msg = redact(rawMsg)
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
