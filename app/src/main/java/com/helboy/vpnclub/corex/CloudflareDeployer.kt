package com.helboy.vpnclub.corex

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * نصب پنل شخصی روی حساب کلادفلر خود کاربر با توکن API،
 * داخل اپ و بدون ترمینال:
 *   توکن ← حساب ← زیردامنه‌ی workers.dev ← KV یا D1 ← آپلود ورکر ← فعال‌سازی ← لینک ساب
 *
 * کد پنل در لحظه‌ی نصب از مخزن رسمی خودش دانلود می‌شود (پس از بررسی نسخه دریافت می‌شود؛ مسیر دورزدن ورود Netra غیرفعال می‌شود):
 *  - Netra: github.com/netrair/netra-panel (MIT), pinned worker revision
 *  - Zeus: github.com/panel-zeus/Z-E-U-S (استفاده‌ی شخصی)     — ساب: /sub/<username>
 * توکن کاربر در اپ ذخیره نمی‌شود و در لاگ نمی‌آید.
 */
object CloudflareDeployer {

    enum class Panel(val label: String) { NETRA("Netra Panel"), ZEUS("Zeus Panel") }

    data class Result(
        val panel: Panel,
        val workerName: String,
        val host: String,
        val panelUrl: String,
        val subUrl: String,
        /** رمز پنل (Zeus) — فقط یک بار به کاربر نشان داده می‌شود. */
        val panelPassword: String = "",
        /** اگر ساخت خودکار کاربر Zeus نشد، توضیح برای کاربر. */
        val note: String = "",
    )

    class DeployException(msg: String) : Exception(msg)

    private const val API = "https://api.cloudflare.com/client/v4"
    private const val NETRA_WORKER_URL = "https://raw.githubusercontent.com/netrair/netra-panel/9f2c7824fee248005bb0bb60b3da63914fd21b8c/worker.js"
    private const val ZEUS_WORKER_URL = "https://raw.githubusercontent.com/panel-zeus/Z-E-U-S/main/Source.js"

    /** لینک ساخت توکن با دسترسی‌های لازم از پیش انتخاب‌شده (مثل BPB-Wizard + D1 برای Zeus). */
    fun tokenUrl(panel: Panel = Panel.NETRA): String {
        val keys = mutableListOf("workers_scripts", "workers_kv_storage", "account_settings")
        if (panel == Panel.ZEUS) keys += "d1"
        val perms = JSONArray(keys.map { key -> JSONObject().put("key", key).put("type", if (key == "account_settings") "read" else "edit") })
        fun e(v: String) = URLEncoder.encode(v, "UTF-8")
        return "https://dash.cloudflare.com/profile/api-tokens?permissionGroupKeys=${e(perms.toString())}" +
            "&accountId=*&zoneId=all&name=${e("Hellboy-VPN")}"
    }

    /** نام ورکر معتبر: حروف کوچک، عدد و خط تیره؛ شروع/پایان بدون خط تیره. */
    fun isValidName(n: String) = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$").matches(n)

    fun randomName(prefix: String): String {
        val cs = "abcdefghijklmnopqrstuvwxyz0123456789"
        val r = SecureRandom()
        return prefix + "-" + (1..8).map { cs[r.nextInt(cs.length)] }.joinToString("")
    }

    /**
     * نصب کامل. [onStep] هر مرحله را گزارش می‌دهد (برای نمایش زنده و لاگ).
     * @throws DeployException با پیام قابل‌فهم
     */
    fun deploy(panel: Panel, token: String, workerName: String, onStep: (String) -> Unit): Result {
        val t = token.trim()
        if (t.length < 20) throw DeployException("توکن کلادفلر معتبر نیست")
        if (!isValidName(workerName)) throw DeployException("نام ورکر فقط حروف کوچک انگلیسی، عدد و «-» (نه در ابتدا/انتها)")
        DiagLogger.section("CF_DEPLOY ${panel.name} name=$workerName")
        fun step(m: String) { DiagLogger.log("CF_DEPLOY $m"); onStep(m) }

        step("بررسی توکن…")
        val verify = call(t, "GET", "/user/tokens/verify")
        if (verify.optJSONObject("result")?.optString("status") != "active") throw DeployException("توکن فعال نیست")
        val accounts = call(t, "GET", "/accounts").optJSONArray("result")
        val acc = accounts?.optJSONObject(0) ?: throw DeployException("توکن به هیچ حساب کلادفلری دسترسی ندارد (دسترسی Account Settings: Read)")
        val accId = acc.getString("id")
        val email = runCatching { call(t, "GET", "/user").optJSONObject("result")?.optString("email").orEmpty() }.getOrDefault("")
        step("حساب: ${acc.optString("name").ifEmpty { accId.take(8) }}")

        if (workerExists(t, accId, workerName))
            throw DeployException("ورکری با نام «$workerName» از قبل وجود دارد؛ نام دیگری انتخاب کنید")

        step("زیردامنه‌ی workers.dev…")
        val sub = workersSubdomain(t, accId)
        val host = "$workerName.$sub.workers.dev"

        step("دانلود کد ${panel.label} از مخزن رسمی…")
        val code = NetHttp.getText(if (panel == Panel.NETRA) NETRA_WORKER_URL else ZEUS_WORKER_URL, "CF_CODE")
        if (code.length < 10_000 || !looksLikeWorkerModule(code)) throw DeployException("کد پنل دانلود نشد یا ناقص است (${code.length} بایت)")
        if (panel == Panel.NETRA && sha256Hex(code) != "050a80a4344e1862f60d2b2fea7e4c7f9217fca16a02fc59fc5b92d3f0b76e93")
            throw DeployException("کد Netra با نسخه‌ی بررسی‌شده مطابقت ندارد؛ نصب متوقف شد")
        step("کد دریافت شد (${code.length / 1024} KB)")

        return when (panel) {
            Panel.NETRA -> deployNetra(t, accId, workerName, host, hardenNetraWorker(code), ::step)
            Panel.ZEUS -> deployZeus(t, accId, workerName, host, code, ::step)
        }
    }

    /** ماژول ورکر: «export default» (Zeus) یا شکل باندل‌شده‌ی «export{X as default}» (BPB). */
    internal fun looksLikeWorkerModule(code: String): Boolean =
        code.contains("export default") || Regex("export\\s*\\{[^}]*\\bas\\s+default\\b").containsMatchIn(code)

    /** Applied only after the pinned upstream checksum has matched. Never install an unrecognized patch target. */
    internal fun hardenNetraWorker(code: String): String {
        val target = "async function skipAuth(e,t){"
        val start = code.indexOf(target)
        if (start < 0 || code.indexOf(target, start + target.length) >= 0)
            throw DeployException("مسیر امنیتی Netra قابل بررسی نیست؛ نصب متوقف شد")
        return code.replace(target, target + "return m(!1,403,\"Password authentication is required.\");")
    }

    // ─────────────────────────────── Netra ───────────────────────────────

    private fun deployNetra(t: String, accId: String, name: String, host: String, code: String, step: (String) -> Unit): Result {
        // Netra's defaults are public. Set unique credentials through supported bindings,
        // not through a copy of BPB's settings, and never persist the account API token.
        val securePath = randomString("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789", 24, 24)
        val password = randomString("ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789", 20, 20)
        step("ساخت KV و رمز اختصاصی Netra…")
        val kvId = call(t, "POST", "/accounts/$accId/storage/kv/namespaces",
            JSONObject().put("title", "$name-netra-${isoNow()}")).getJSONObject("result").getString("id")
        var uploaded = false
        try {
            putKv(t, accId, kvId, "pwd", password)
            putKv(t, accId, kvId, "authDisabled", "false")
            val bindings = netraBindings(kvId, UUID.randomUUID().toString(),
                randomString("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789", 24, 24), securePath)
            step("آپلود Netra و فعال‌سازی…")
            uploadWorker(t, accId, name, code, bindings)
            uploaded = true
            enableSubdomain(t, accId, name, step)
        } catch (e: Exception) {
            // Only resources created by this attempt may be rolled back.
            if (uploaded) runCatching { call(t, "DELETE", "/accounts/$accId/workers/scripts/$name") }
            runCatching { call(t, "DELETE", "/accounts/$accId/storage/kv/namespaces/$kvId") }
            throw e
        }
        val (panelUrl, subUrl) = netraUrls(host, securePath)
        // A newly deployed worker needs time to propagate. Don't manufacture a health result.
        step("✓ Netra نصب شد: $host")
        return Result(Panel.NETRA, name, host, panelUrl, subUrl, password,
            note = "رمز پنل را یادداشت کنید. ساب خام Xray اضافه شد؛ دریافت و تست سرورها جداگانه انجام می‌شود. توکن کلادفلر در ورکر ذخیره نشده؛ تغییرات پنل و KV فعال است، عملیات حساب کلادفلر را از داشبورد انجام دهید.")
    }

    internal fun netraUrls(host: String, securePath: String): Pair<String, String> {
        val p = URLEncoder.encode(securePath, "UTF-8")
        return "https://$host/$p/panel" to "https://$host/$p/sub/raw?app=xray&raw=1"
    }

    internal fun netraBindings(kvId: String, uuid: String, trojanPassword: String, securePath: String): JSONArray =
        JSONArray().put(JSONObject().put("type", "kv_namespace").put("name", "kv").put("namespace_id", kvId))
            .put(JSONObject().put("type", "secret_text").put("name", "UUID").put("text", uuid))
            .put(JSONObject().put("type", "secret_text").put("name", "TR_PASS").put("text", trojanPassword))
            .put(JSONObject().put("type", "secret_text").put("name", "SUB_PATH").put("text", securePath))

    private fun putKv(t: String, accId: String, kvId: String, key: String, value: String) {
        val req = Request.Builder().url("$API/accounts/$accId/storage/kv/namespaces/$kvId/values/$key")
            .header("Authorization", "Bearer $t")
            .put(value.toRequestBody("text/plain".toMediaType())).build()
        NetHttp.execute(req, "CF_KV").use { r ->
            if (!r.isSuccessful) throw DeployException("تنظیم KV انجام نشد (HTTP ${r.code})")
        }
    }

    // ─────────────────────────────── Zeus ───────────────────────────────

    private fun deployZeus(t: String, accId: String, name: String, host: String, code: String, step: (String) -> Unit): Result {
        step("ساخت پایگاه داده D1…")
        val dbId = call(t, "POST", "/accounts/$accId/d1/database", JSONObject().put("name", "$name-db"))
            .getJSONObject("result").getString("uuid")

        step("آپلود ورکر…")
        uploadWorker(t, accId, name, code, JSONArray()
            .put(JSONObject().put("type", "d1").put("name", "DB").put("id", dbId))
            .put(JSONObject().put("type", "plain_text").put("name", "CF_ACCOUNT_ID").put("text", accId))
            .put(JSONObject().put("type", "plain_text").put("name", "WORKER_NAME").put("text", name))
            .put(JSONObject().put("type", "secret_text").put("name", "CF_API_TOKEN").put("text", t)))
        enableSubdomain(t, accId, name, step)
        val panelUrl = "https://$host/panel"

        // رمز پنل و یک کاربر برای خود صاحب گوشی، با API خود پنل
        val password = randomString("ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789", 14, 14)
        val user = "hellboy"
        step("راه‌اندازی پنل (ممکن است تا یک دقیقه طول بکشد تا ورکر در دسترس شود)…")
        var cookie: String? = null
        var lastErr = ""
        for (i in 0 until 8) {
            try {
                val r = panelPost("https://$host/api/setup-password", JSONObject().put("password", password), null)
                if (r.first in 200..299) { cookie = "panel_session=" + sha256Hex(password); break }
                lastErr = "HTTP ${r.first} ${r.second.take(100)}"
            } catch (e: Exception) { lastErr = e.message ?: e.javaClass.simpleName }
            Thread.sleep(6_000)
        }
        if (cookie == null) {
            DiagLogger.log("CF_DEPLOY zeus setup-password failed: $lastErr")
            return Result(Panel.ZEUS, name, host, panelUrl, "",
                note = "پنل نصب شد ولی راه‌اندازی خودکار انجام نشد ($lastErr). پنل را باز کنید، رمز بگذارید، کاربر بسازید و لینک ساب را در «ساب‌های من» اضافه کنید.")
        }
        step("رمز پنل تنظیم شد؛ ساخت کاربر…")
        val created = runCatching {
            // همان بدنه‌ای که خود پنل Zeus برای «کاربر مستقیم» می‌فرستد (null = نامحدود)
            panelPost("https://$host/api/users", JSONObject()
                .put("username", user).put("limit_gb", JSONObject.NULL).put("expiry_days", JSONObject.NULL)
                .put("limit_req", JSONObject.NULL).put("ip_limit", JSONObject.NULL)
                .put("auto_reset_vol_days", 0).put("auto_reset_req_days", 0)
                .put("fingerprint", "chrome").put("block_ads", 0).put("block_porn", 0)
                .put("port", "443").put("tls", "on").put("ip_operator", "all").put("ip_count", 20)
                .put("auto_rotate_ip", 0).put("rotate_time", 0).put("auto_rotate_user_proxy", 0)
                .put("connection_type", "vless").put("enable_direct", true), cookie)
        }.getOrElse { 0 to (it.message ?: "") }
        DiagLogger.log("CF_DEPLOY zeus create user -> ${created.first} ${created.second.take(160)}")
        val subUrl = "https://$host/sub/$user"
        val subOk = created.first in 200..299 && runCatching { NetHttp.getText(subUrl, "CF_SUBCHECK").isNotBlank() }.getOrDefault(false)
        step(if (subOk) "✓ نصب شد: $host" else "✓ پنل نصب شد؛ کاربر را از داخل پنل بسازید")
        return Result(Panel.ZEUS, name, host, panelUrl, if (subOk) subUrl else "", password,
            note = if (subOk) "کاربر «$user» با حجم و زمان نامحدود ساخته شد." else
                "ساخت خودکار کاربر انجام نشد (${created.first}). با رمز بالا وارد پنل شوید، کاربر بسازید و لینک ساب را در «ساب‌های من» اضافه کنید.")
    }

    // ─────────────────────────────── API ───────────────────────────────

    private fun workerExists(t: String, account: String, name: String): Boolean {
        val req = Request.Builder().url("$API/accounts/$account/workers/scripts/$name/settings")
            .header("Authorization", "Bearer $t").build()
        NetHttp.execute(req, "CF_EXISTS").use { r ->
            if (r.code == 404) return false
            if (r.isSuccessful) return true
            throw DeployException("بررسی نام ورکر انجام نشد (HTTP ${r.code})؛ هیچ ورکری جایگزین نشد")
        }
    }

    private fun workersSubdomain(t: String, accId: String): String {
        runCatching { call(t, "GET", "/accounts/$accId/workers/subdomain").optJSONObject("result")?.optString("subdomain") }
            .getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        // حساب تازه: ساخت زیردامنه‌ی تصادفی (تا ۳ بار)
        repeat(3) {
            val s = randomName("hb").replace("-", "")
            val r = runCatching { call(t, "PUT", "/accounts/$accId/workers/subdomain", JSONObject().put("subdomain", s)) }
            r.getOrNull()?.optJSONObject("result")?.optString("subdomain")?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        throw DeployException("ساخت زیردامنه‌ی workers.dev ممکن نشد")
    }

    private fun uploadWorker(t: String, accId: String, name: String, script: String, bindings: JSONArray) {
        val meta = JSONObject()
            .put("main_module", "worker.js")
            .put("compatibility_date", SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date()))
            .put("compatibility_flags", JSONArray().put("nodejs_compat"))
            .put("bindings", bindings)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("metadata", null, meta.toString().toRequestBody("application/json".toMediaType()))
            .addFormDataPart("worker.js", "worker.js", script.toRequestBody("application/javascript+module".toMediaType()))
            .build()
        call(t, "PUT", "/accounts/$accId/workers/scripts/$name", rawBody = body)
    }

    private fun enableSubdomain(t: String, accId: String, name: String, step: (String) -> Unit) {
        step("فعال‌سازی آدرس workers.dev…")
        call(t, "POST", "/accounts/$accId/workers/scripts/$name/subdomain",
            JSONObject().put("enabled", true).put("previews_enabled", false))
    }

    /** درخواست به API کلادفلر؛ success=false → DeployException با متن خطای خود کلادفلر. */
    private fun call(t: String, method: String, path: String, json: JSONObject? = null, rawBody: RequestBody? = null): JSONObject {
        val body = rawBody ?: json?.toString()?.toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(API + path).header("Authorization", "Bearer $t")
            .method(method, body ?: if (method == "GET") null else ByteArray(0).toRequestBody()).build()
        NetHttp.execute(req, "CF_API").use { r ->
            val text = r.body?.string().orEmpty()
            val o = runCatching { JSONObject(text) }.getOrElse { throw DeployException("پاسخ نامعتبر از کلادفلر (HTTP ${r.code})") }
            if (!r.isSuccessful || !o.optBoolean("success", false)) {
                val errs = o.optJSONArray("errors")
                val msg = (0 until (errs?.length() ?: 0)).joinToString(" | ") { i ->
                    errs!!.getJSONObject(i).let { e -> "${e.optInt("code")}: ${e.optString("message")}" }
                }.ifEmpty { "HTTP ${r.code}" }
                DiagLogger.log("CF_API $method $path error $msg")
                throw DeployException(friendly(msg))
            }
            return o
        }
    }

    private fun friendly(msg: String): String = when {
        msg.contains("10000") || msg.contains("Authentication error", true) ->
            "توکن دسترسی کافی ندارد ($msg) — توکن را با لینک «ساخت توکن» بسازید"
        msg.contains("10013") || msg.contains("free", true) && msg.contains("limit", true) -> "سقف ورکرهای حساب پر است ($msg)"
        else -> "خطای کلادفلر: $msg"
    }

    /** POST به API خود پنل (Zeus). (کد HTTP، متن پاسخ) */
    private fun panelPost(url: String, json: JSONObject, cookie: String?): Pair<Int, String> {
        val b = Request.Builder().url(url).post(json.toString().toRequestBody("application/json".toMediaType()))
        cookie?.let { b.header("Cookie", it) }
        NetHttp.execute(b.build(), "PANEL").use { r -> return r.code to r.body?.string().orEmpty() }
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun randomString(cs: String, min: Int, max: Int): String {
        val r = SecureRandom()
        val n = min + r.nextInt(max - min + 1)
        return (1..n).map { cs[r.nextInt(cs.length)] }.joinToString("")
    }

    private fun isoNow(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
