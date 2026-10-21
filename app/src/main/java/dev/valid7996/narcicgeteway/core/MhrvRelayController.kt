package dev.valid7996.narcicgeteway.core

import com.therealaleph.mhrv.Native
import dev.valid7996.narcicgeteway.core.AppLog as Log
import dev.valid7996.narcicgeteway.domain.config.MhrvRelayProfile
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Runs the MasterHttpRelayVPN core (libmhrv_rs.so) in this process. The core opens a local SOCKS5 listener
 * that the service bridges to the TUN, exactly as it does for the other SOCKS-based engines.
 *
 * The native core writes its own log to logcat under the tag `mhrv_rs`; the in-app log screen reads logcat and
 * files that tag under the MHRV relay, so nothing is pumped by hand (it would only print every line twice).
 */
class MhrvRelayController(
    private val profile: MhrvRelayProfile,
    private val filesDir: File,
    val httpPort: Int,
    val socksPort: Int,
) {
    @Volatile private var handle = 0L
    @Volatile private var stopped = false

    /** The reason the last [start] or [awaitReady] failed, as a short English line for the log. */
    @Volatile var failure: String? = null
        private set

    @Volatile var missingCredentials = false
        private set

    /** True from a successful [start] until [stop]; the core lives in this process, so it cannot die on its own. */
    val isRunning: Boolean
        get() = handle != 0L && !stopped

    @Synchronized
    fun start(): Boolean {
        failure = null
        missingCredentials = false
        stopped = false
        if (!profile.isValid) {
            missingCredentials = profile.needsCredentials
            failure = "config is incomplete (deployment ID and auth key are required)"
            return false
        }
        return try {
            Native.setDataDir(dataDir().absolutePath)
            if (handle != 0L) {
                runCatching { Native.stopProxy(handle) }
                handle = 0L
            }
            val config = profile.toConfigJson(httpPort, socksPort)
            handle = Native.startProxy(config)
            if (handle == 0L) {
                failure = "the core rejected the config (see the mhrv_rs lines in the log)"
                false
            } else {
                Log.i(TAG, "MHRV relay core ${runCatching { Native.version() }.getOrDefault("?")} started, " +
                    "mode=${profile.mode}, SOCKS 127.0.0.1:$socksPort")
                true
            }
        } catch (e: Throwable) {
            failure = e.message ?: e.toString()
            Log.e(TAG, "MHRV relay start failed", e)
            handle = 0L
            false
        }
    }

    /** Waits until the core's SOCKS listener accepts connections. */
    fun awaitReady(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (stopped || handle == 0L) return false
            if (portAccepts(socksPort, 400)) return true
            try { Thread.sleep(150) } catch (_: InterruptedException) { return false }
        }
        failure = "the SOCKS listener on $socksPort never opened"
        return false
    }

    @Synchronized
    fun stop() {
        stopped = true
        val h = handle
        handle = 0L
        if (h != 0L) {
            runCatching { Native.stopProxy(h) }.onFailure { Log.w(TAG, "stopProxy threw: ${it.message}") }
        }
    }

    private fun dataDir(): File = File(filesDir, "mhrv").apply { mkdirs() }

    private fun portAccepts(port: Int, timeoutMs: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), timeoutMs); true }
    }.getOrDefault(false)

    companion object {
        private const val TAG = "MhrvRelayController"

        /**
         * Exports the relay's CA to a file the system installer can read, creating the CA on first use.
         * Returns the DER bytes, or null when the core could not produce it.
         */
        fun exportCaDer(filesDir: File): ByteArray? = runCatching {
            Native.setDataDir(File(filesDir, "mhrv").apply { mkdirs() }.absolutePath)
            val out = File(filesDir, "mhrv-ca.crt")
            if (!Native.exportCa(out.absolutePath) || !out.exists() || out.length() == 0L) return null
            pemToDer(out.readBytes()) ?: out.readBytes()
        }.getOrNull()

        private fun pemToDer(raw: ByteArray): ByteArray? {
            val text = raw.decodeToString()
            val begin = "-----BEGIN CERTIFICATE-----"
            val end = "-----END CERTIFICATE-----"
            val a = text.indexOf(begin)
            val b = text.indexOf(end)
            if (a < 0 || b < 0) return null
            val body = text.substring(a + begin.length, b).filter { !it.isWhitespace() }
            return runCatching { android.util.Base64.decode(body, android.util.Base64.DEFAULT) }.getOrNull()
        }
    }
}
