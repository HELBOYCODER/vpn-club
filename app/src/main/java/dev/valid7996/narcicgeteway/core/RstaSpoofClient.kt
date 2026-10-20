package dev.valid7996.narcicgeteway.core

import android.content.Context
import android.content.Intent
import dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Client for managing the isolated RstaSpoofService from the main process.
 * By keeping this separate from RstaSpoofEngine, the main process never loads
 * `libsnispf.so` and avoids Go runtime clashing with `libgojni.so`.
 */
object RstaSpoofClient {

    private val probeLock = Mutex()

    private const val PORT = SniSpoofConfig.RSTA_LOCAL_PORT

    private data class Params(val ip: String, val port: Int, val sni: String, val method: String)

    private var current: Params? = null
    private var holders = 0

    /**
     * The no-root SNI spoof as RstaNG keeps it: one engine on 127.0.0.1:[PORT], with the connect IP, port, fake SNI and
     * method of the app's settings, for every profile that points at that address. It is started for whoever needs it
     * first (a connection, or a batch of delay tests) and stopped when the last lets go; changed settings restart it.
     * Blocking: returns once the listener answers. False when the engine did not come up.
     */
    @Synchronized
    fun acquire(context: Context, settings: AppSettings): Boolean {
        val wanted = Params(
            ip = settings.rstaSpoofConnectIp.trim().ifEmpty { "104.18.38.202" },
            port = settings.rstaSpoofConnectPort.takeIf { it in 1..65535 } ?: 443,
            sni = settings.rstaSpoofFakeSni.trim().ifEmpty { "cdnjs.cloudflare.com" },
            method = SniSpoofConfig.method(settings.rstaSpoofMethod),
        )
        // Running already, with these settings, and answering: nothing to start (it may have outlived the main process).
        val up = current == wanted && awaitListener(PORT, timeoutMs = 400L)
        if (!up) {
            start(context, PORT, wanted.ip, wanted.port, wanted.sni, wanted.method)
            current = wanted
            if (!awaitListener(PORT, timeoutMs = 6_000L)) {
                stop(context)
                current = null
                holders = 0
                return false
            }
        }
        holders++
        return true
    }

    /** One who needed the engine lets go; it stops with the last of them. */
    @Synchronized
    fun release(context: Context) {
        if (holders > 0) holders--
        if (holders == 0 && current != null) {
            val vpnActive = VpnManager.status.value.state.isActive
            val spoofVpn = VpnManager.activeKind.value == VpnManager.KIND_SNISPOOF
            if (vpnActive && spoofVpn) {
                // Keep spoof engine alive while VPN connection is actively using it
                return
            }
            stop(context)
            current = null
        }
    }

    /** The lease of a batch of delay tests: acquires off the main thread, and gives back the function that releases. */
    suspend fun lease(context: Context, settings: AppSettings): (() -> Unit)? = withContext(Dispatchers.IO) {
        if (acquire(context, settings)) ({ release(context) }) else null
    }

    /** What the settings screen shows as the engine's state: the service's own word, while the listener really answers. */
    fun statusSummary(context: Context): String {
        val text = runCatching { File(context.filesDir, RstaSpoofService.STATUS_FILE).readText().trim() }.getOrDefault("")
        return when {
            text.startsWith("active") -> if (awaitListener(PORT, timeoutMs = 300L)) text else "inactive"
            text.isEmpty() -> "inactive"
            else -> text
        }
    }

    fun recentLog(context: Context): List<String> =
        runCatching { File(context.filesDir, RstaSpoofService.LOG_FILE).readLines().takeLast(200) }.getOrDefault(emptyList())

    fun start(context: Context, listenPort: Int, ip: String, port: Int, fakeSni: String, method: String) {
        val intent = Intent(context, RstaSpoofService::class.java).apply {
            action = RstaSpoofService.ACTION_START
            putExtra(RstaSpoofService.EXTRA_LISTEN_PORT, listenPort)
            putExtra(RstaSpoofService.EXTRA_IP, ip)
            putExtra(RstaSpoofService.EXTRA_PORT, port)
            putExtra(RstaSpoofService.EXTRA_FAKE_SNI, fakeSni)
            putExtra(RstaSpoofService.EXTRA_METHOD, method)
        }
        context.startService(intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, RstaSpoofService::class.java).apply {
            action = RstaSpoofService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun awaitListener(port: Int, timeoutMs: Long = 4_000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val ok = runCatching {
                Socket().use {
                    it.connect(InetSocketAddress("127.0.0.1", port), 500)
                    true
                }
            }.getOrDefault(false)
            if (ok) return true
            try { Thread.sleep(150) } catch (_: InterruptedException) { return false }
        }
        return false
    }

    suspend fun probe(
        context: Context,
        ip: String,
        port: Int,
        fakeSni: String,
        method: String,
        block: suspend (Int) -> Long
    ): Long = probeLock.withLock {
        withContext(Dispatchers.IO) {
            val listen = AetherSupport.findFreePort()
            start(context, listen, ip, port, fakeSni, method)
            try {
                if (awaitListener(listen)) block(listen) else -1L
            } finally {
                stop(context)
            }
        }
    }
}
