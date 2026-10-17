package com.helboy.vpnclub.corex

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Executes MSN-GUARD's pinned Xray binary as a *separate Linux process*.
 *
 * A second GoMobile library inside the Android process is unsafe because the
 * Xray and Psiphon AARs ship conflicting Go JNI symbol/class namespaces.
 * Xray's executable runs with its own Go runtime and cannot collide.
 */
internal object NativeXrayRunner {
    private fun executable(ctx: Context): File =
        File(ctx.applicationInfo.nativeLibraryDir, "libxray.so")

    private fun writeConfig(ctx: Context, json: String): File {
        val dir = File(ctx.filesDir, "xray-native").apply { mkdirs() }
        val f = File.createTempFile("config-", ".json", dir)
        f.writeText(json)
        return f
    }

    fun start(ctx: Context, config: String): Process {
        val bin = executable(ctx)
        require(bin.isFile) { "Xray executable is missing: ${bin.absolutePath}" }
        val f = writeConfig(ctx, config)
        try {
            val p = ProcessBuilder(bin.absolutePath, "run", "-c", f.absolutePath)
                .directory(f.parentFile)
                .redirectErrorStream(true)
                .apply {
                    environment()["HOME"] = ctx.filesDir.absolutePath
                    environment()["XRAY_LOCATION_ASSET"] = ctx.getDir("assets", Context.MODE_PRIVATE).absolutePath
                }
                .start()
            Thread({
                try {
                    p.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.isNotBlank() && line.contains("error", ignoreCase = true))
                                DiagLogger.log("NATIVE_XRAY ${line.take(250)}")
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    f.delete()
                }
            }, "hellboy-xray-stdout").apply { isDaemon = true }.start()
            return p
        } catch (e: Exception) {
            f.delete()
            throw e
        }
    }

    fun listenerReady(process: Process, port: Int, waitMs: Long): Boolean {
        val deadline = System.nanoTime() + waitMs * 1_000_000L
        while (System.nanoTime() < deadline && process.isAlive) {
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 150) }
                return true
            } catch (_: Exception) {
                Thread.sleep(80)
            }
        }
        return false
    }

    fun stop(process: Process?) {
        process ?: return
        if (!process.isAlive) return
        process.destroy()
        if (!process.waitFor(600, TimeUnit.MILLISECONDS)) process.destroyForcibly()
    }

    /**
     * Test a candidate by sending an HTTPS request through its *real* SOCKS
     * listener, rather than trusting a TLS handshake or a TCP-only ping.
     */
    fun measure(ctx: Context, outboundOnly: String, timeoutMs: Long): Long {
        val port = ServerSocket(0).use { it.localPort }
        var process: Process? = null
        return try {
            process = start(ctx, NativeXrayConfig.withSocksPort(outboundOnly, port))
            val warmupMs = minOf(timeoutMs, 4_000)
            val started = System.nanoTime()
            if (!listenerReady(process, port, warmupMs)) return -1L
            val usedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            val remaining = (timeoutMs - usedMs).coerceAtLeast(500L).coerceAtMost(5_000L).toInt()
            HealthTester.tunnelLatency(port, remaining)
        } catch (e: Exception) {
            DiagLogger.log("NATIVE_XRAY_TEST ${e.javaClass.simpleName}: ${e.message?.take(130)}")
            -1L
        } finally {
            stop(process)
        }
    }
}
