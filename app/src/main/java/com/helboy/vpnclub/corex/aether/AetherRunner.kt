package com.helboy.vpnclub.corex.aether

import android.content.Context
import com.helboy.vpnclub.corex.DiagLogger
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * موتور Aether (اولین بخش مهاجرت از Narcic Gateway): هسته‌ی `libaether.so` به‌صورت پردازه اجرا می‌شود و
 * یک SOCKS روی لوپ‌بک می‌سازد؛ تونل سیستم (hev) مثل بقیه‌ی سرورها به همان SOCKS وصل می‌شود.
 *
 * محدوده‌ی این نسخه: پروتکل‌های WARP (WireGuard / MASQUE / gool / …) مستقیم از گوشی. حامل‌های داخلی
 * Psiphon و Tor و زنجیره‌ی فرآیند خروجی Xray هنوز نیستند و پروفایل‌هایی که به آن‌ها نیاز دارند رد می‌شوند.
 */
class AetherSession internal constructor(
    private val process: Process,
    val port: Int,
    private val reader: Thread,
) {
    val isAlive: Boolean get() = process.isAlive

    fun stop() {
        runCatching { process.destroy() }
        runCatching { if (process.isAlive) process.destroyForcibly() }
        runCatching { reader.interrupt() }
    }
}

object AetherRunner {

    const val BINARY_NAME = "libaether.so"
    private const val PROBE_TIMEOUT_MS = 800
    private const val REGISTER_TIMEOUT_MS = 90_000L
    private const val READY_TIMEOUT_MS = 45_000L
    private const val POLL_MS = 250L

    /** فایل‌های هویت (کلید WARP) که هسته خودش در workDir می‌نویسد. */
    private const val BASE_FILE = "aether.toml"
    private const val MASQUE_FILE = "aether-masque.toml"
    private const val MASQUE_GOOL_FILE = "aether-masque-gool.toml"
    private const val MASQUE_INNER_FILE = "aether-masque-secondary.toml"
    private const val WIREGUARD_FILE = "aether-wg.toml"
    private const val WIREGUARD_INNER_FILE = "aether-wg-secondary.toml"

    fun binary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    fun isAvailable(context: Context): Boolean = binary(context).canExecute()

    fun workDir(context: Context): File = File(context.filesDir, "aether")

    /** پوشه‌ی گواهی‌های سیستم اندروید (اگر باشد) — هسته برای TLS به آن نیاز دارد. */
    private fun certDir(): String? = listOf("/system/etc/security/cacerts", "/apex/com.android.conscrypt/cacerts")
        .firstOrNull { File(it).isDirectory }

    internal fun environment(context: Context, workDir: File): MutableMap<String, String> = mutableMapOf(
        "HOME" to workDir.absolutePath,
        "TMPDIR" to context.cacheDir.absolutePath,
        "AETHER_CONFIG" to File(workDir, BASE_FILE).absolutePath,
        "AETHER_MASQUE_CONFIG" to File(workDir, MASQUE_FILE).absolutePath,
        "AETHER_WG_CONFIG" to File(workDir, WIREGUARD_FILE).absolutePath,
    ).apply { certDir()?.let { put("SSL_CERT_DIR", it) } }

    /** فایل‌هایی که پروتکل [protocol] لازم دارد؛ اگر یکی نباشد یا هویت نداشته باشد، کلید ثبت می‌شود. */
    internal fun keyFilesOf(protocol: String): List<String> = when (protocol) {
        AetherProfile.PROTO_MASQUE -> listOf(MASQUE_FILE)
        AetherProfile.PROTO_WG -> listOf(WIREGUARD_FILE)
        AetherProfile.PROTO_GOOL -> listOf(WIREGUARD_FILE, WIREGUARD_INNER_FILE)
        AetherProfile.PROTO_MIM -> listOf(MASQUE_FILE, MASQUE_INNER_FILE)
        AetherProfile.PROTO_WG_OVER_MASQUE -> listOf(MASQUE_FILE, MASQUE_GOOL_FILE)
        else -> listOf(WIREGUARD_FILE)
    }

    /** هویت معتبر = فایل با device_id غیرخالی. */
    internal fun hasIdentity(file: File): Boolean = try {
        file.isFile && Regex("""^device_id\s*=\s*"[^"]+"""", RegexOption.MULTILINE).containsMatchIn(file.readText())
    } catch (_: IOException) {
        false
    }

    fun answersSocks(port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), PROBE_TIMEOUT_MS)
            socket.soTimeout = PROBE_TIMEOUT_MS
            socket.getOutputStream().write(byteArrayOf(5, 1, 0))
            socket.getInputStream().read() == 5
        }
    } catch (_: Exception) {
        false
    }

    fun freePort(): Int = ServerSocket(0).use { it.localPort }

    /** اجرای ثبت کلید (اگر لازم است) و سپس اجرای هسته روی [port]. null = شکست؛ دلیل در DiagLogger. */
    fun start(context: Context, profile: AetherProfile, port: Int): AetherSession? {
        if (!isAvailable(context)) {
            DiagLogger.log("AETHER core missing for this ABI")
            return null
        }
        val problem = AetherCommands.problemOf(profile)
        if (problem != null || profile.psiphon != AetherProfile.CARRIER_OFF || profile.tor != AetherProfile.CARRIER_OFF) {
            DiagLogger.log("AETHER unsupported profile: ${problem ?: "carriers (Psiphon/Tor) not in this version"}")
            return null
        }
        val work = workDir(context).apply { mkdirs() }
        if (!ensureKeys(context, profile, work)) return null

        val arguments = AetherCommands.runArguments(profile, port)
        val listenPort = AetherCommands.listenerPortOf(arguments) ?: port
        val process = try {
            ProcessBuilder(listOf(binary(context).absolutePath) + arguments)
                .directory(work).redirectErrorStream(true)
                .apply { environment().putAll(environment(context, work)) }
                .start()
        } catch (e: IOException) {
            DiagLogger.log("AETHER spawn failed: ${e.message?.take(120)}")
            return null
        }
        val reader = Thread({
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    val clean = line.trim()
                    if (clean.isNotEmpty()) DiagLogger.log("AETHER ${clean.take(300)}")
                }
            } catch (_: IOException) {
            }
        }, "aether-log").apply { isDaemon = true; start() }

        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (answersSocks(listenPort)) {
                DiagLogger.log("AETHER listener up on 127.0.0.1:$listenPort (${profile.protocol})")
                return AetherSession(process, listenPort, reader)
            }
            if (!process.isAlive) break
            Thread.sleep(POLL_MS)
        }
        DiagLogger.log("AETHER listener did not come up (alive=${process.isAlive})")
        process.destroy()
        return null
    }

    /** کلیدهای WARP را اگر نبودند با هسته ثبت می‌کند. */
    private fun ensureKeys(context: Context, profile: AetherProfile, work: File): Boolean {
        val missing = keyFilesOf(profile.protocol).filterNot { hasIdentity(File(work, it)) }
        if (missing.isEmpty()) return true
        DiagLogger.log("AETHER registering WARP keys: ${missing.joinToString()}")
        val args = AetherCommands.keysArguments(
            AetherCommands.KeysRequest(kind = AetherProfile.registerKindOf(profile.protocol)),
        )
        val p = try {
            ProcessBuilder(listOf(binary(context).absolutePath) + args)
                .directory(work).redirectErrorStream(true)
                .apply { environment().putAll(environment(context, work)) }
                .start()
        } catch (e: IOException) {
            DiagLogger.log("AETHER register spawn failed: ${e.message?.take(120)}")
            return false
        }
        val drain = Thread({
            try { p.inputStream.bufferedReader().forEachLine { DiagLogger.log("AETHER-REG ${it.take(300)}") } } catch (_: IOException) {}
        }, "aether-reg-log").apply { isDaemon = true; start() }
        val finished = p.waitFor(REGISTER_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) p.destroyForcibly()
        drain.join(2_000)
        val ok = finished && p.exitValue() == 0 && missing.all { hasIdentity(File(work, it)) }
        if (!ok) DiagLogger.log("AETHER key registration failed (finished=$finished)")
        return ok
    }
}
