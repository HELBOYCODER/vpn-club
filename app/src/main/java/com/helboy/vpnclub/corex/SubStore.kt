package com.helboy.vpnclub.corex

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * «ساب‌های من»: لینک‌های ساب شخصی کاربر (پنل‌های BPB / Zeus نصب‌شده از داخل اپ، یا دستی).
 * سرورهای این ساب‌ها در اسکن‌ها همیشه اول و کامل تست می‌شوند (نه نمونه‌ی تصادفی).
 */
data class MySub(
    val id: String,
    val name: String,
    val url: String,
    /** bpb | zeus | manual */
    val kind: String = "manual",
    val panelUrl: String = "",
    val lastFetchAt: Long = 0,
    val configCount: Int = 0,
    val aliveCount: Int = 0,
    val bestMs: Long = -1,
    val lastError: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("url", url).put("kind", kind)
        .put("panel", panelUrl).put("at", lastFetchAt).put("n", configCount).put("alive", aliveCount)
        .put("best", bestMs).put("err", lastError)

    companion object {
        fun fromJson(o: JSONObject) = MySub(o.optString("id"), o.optString("name"), o.optString("url"),
            o.optString("kind", "manual"), o.optString("panel"), o.optLong("at"), o.optInt("n"),
            o.optInt("alive"), o.optLong("best", -1), o.optString("err"))

        fun newId() = "sub-" + java.lang.Long.toString(System.currentTimeMillis(), 36)
    }
}

class SubStore(context: Context) {
    private val file = File(context.filesDir, "my_subs.json")
    private val lock = Any()

    fun load(): List<MySub> = synchronized(lock) {
        try {
            if (!file.exists()) return emptyList()
            val a = JSONArray(file.readText())
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(MySub::fromJson) }.filter { it.url.isNotEmpty() }
        } catch (_: Exception) { emptyList() }
    }

    fun save(list: List<MySub>) = synchronized(lock) {
        try { file.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()) } catch (_: Exception) {}
    }
}
