package dev.valid7996.narcicgeteway.core

import android.content.Context
import dev.valid7996.narcicgeteway.domain.config.AetherCommands
import dev.valid7996.narcicgeteway.domain.config.AetherProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * The ping of an Aether profile: a core of its own is started for it, on a free loopback port, with the profile's exit
 * proxy behind it; once it answers, the caller times a request through it, and everything is torn down again. An
 * Aether profile has no server to knock on (its endpoint is found by scan, and WireGuard answers on UDP), so this is
 * the only measure there is. One core at a time: they share the exit proxy and the key files.
 */
object AetherDelayProbe {

    /** How long a core gets to bring its tunnel up: a scan on a hard network and a Psiphon or Tor carrier take their time. */
    private const val READY_TIMEOUT_MS = 120_000L

    private val one = Mutex()

    /**
     * Runs a core for [profile] and hands the port of its SOCKS listener to [measure], whose result is returned;
     * -1 when the core did not come up, or while a session of Aether is connected (the exit proxy and the
     * keys are its own, and a second core would take them from it).
     */
    suspend fun run(
        context: Context,
        profile: AetherProfile,
        measure: suspend (socksPort: Int) -> Long,
    ): Long = one.withLock {
        withContext(Dispatchers.IO) {
            if (AetherSupport.sessionRunning || !AetherSupport.isCoreSupported(context)) return@withContext -1L
            val workDir = AetherSupport.workDir(context).apply { mkdirs() }
            val port = AetherSupport.findFreePort()
            var process: Process? = null
            try {
                var arguments = AetherCommands.runArguments(profile, port)
                if (AetherCommands.UPSTREAM !in arguments) {
                    AetherSupport.startExitProxy(profile)?.let {
                        arguments = arguments + listOf(AetherCommands.UPSTREAM, "socks5://127.0.0.1:$it")
                    }
                }
                val needsWord = AetherCommands.readyNeedsWord(arguments)
                if (needsWord) arguments = AetherCommands.withInfoLines(arguments)
                val listenPort = AetherCommands.listenerPortOf(arguments) ?: port

                val started = AetherSupport.startProcess(context, arguments, workDir)
                process = started
                val wordSeen = java.util.concurrent.atomic.AtomicBoolean(false)
                Thread({
                    try {
                        started.inputStream.bufferedReader().forEachLine { line ->
                            if (AetherCommands.isReadyWord(line)) wordSeen.set(true)
                        }
                    } catch (_: IOException) {
                    }
                }, "aether-probe-out").apply { isDaemon = true }.start()

                val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
                while (coroutineContext.isActive && started.isAlive && System.currentTimeMillis() < deadline) {
                    if ((!needsWord || wordSeen.get()) && AetherSupport.answersSocks(listenPort)) {
                        return@withContext measure(listenPort)
                    }
                    delay(400)
                }
                -1L
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                LogBus.append("W/aether ping failed: ${e.message}")
                -1L
            } finally {
                process?.let { p ->
                    p.destroy()
                    try { if (!p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly() } catch (_: InterruptedException) {}
                }
                AetherSupport.stopExitProxy()
            }
        }
    }
}
