package dev.valid7996.narcicgeteway.core

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dev.valid7996.narcicgeteway.core.AppLog as Log
import dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig
import java.io.File

/**
 * Runs in an isolated Linux process (`:snispoof`) to host the Go runtime of `libsnispf.so`.
 * This prevents the Go runtime in `libsnispf.so` from clashing with the Go runtime in `libgojni.so`
 * (sing-box / Xray), which would otherwise crash with `fatal error: addspecial on invalid pointer` (SIGABRT).
 *
 * The main process cannot read this process's memory, so what it shows of the engine (status and log, as RstaNG's
 * spoof screen does) is written to two files in the app's folder, which [RstaSpoofClient] reads.
 */
class RstaSpoofService : Service() {

    companion object {
        private const val TAG = "RstaSpoofService"
        const val ACTION_START = "dev.valid7996.narcicgeteway.snispoof.START"
        const val ACTION_STOP = "dev.valid7996.narcicgeteway.snispoof.STOP"
        const val EXTRA_LISTEN_PORT = "listen_port"
        const val EXTRA_IP = "ip"
        const val EXTRA_PORT = "port"
        const val EXTRA_FAKE_SNI = "fake_sni"
        const val EXTRA_METHOD = "method"

        const val STATUS_FILE = "snispf.status"
        const val LOG_FILE = "snispf.log"
        private const val LOG_MAX_LINES = 200

        private const val LOG_FLUSH_INTERVAL_MS = 3_000L

        private var activeSessionId = 0L

        @Volatile
        var isRunning = false
            private set
    }

    private val logLock = Any()
    private val logBuffer = ArrayDeque<String>(LOG_MAX_LINES)
    @Volatile private var lastLogFlushMs = 0L

    override fun onCreate() {
        super.onCreate()
        synchronized(logLock) {
            runCatching {
                val file = File(filesDir, LOG_FILE)
                if (file.isFile) {
                    file.readLines().takeLast(LOG_MAX_LINES).forEach { logBuffer.addLast(it) }
                }
            }
        }
        RstaSpoofEngine.logSink = { line -> appendLog(line) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val listenPort = intent.getIntExtra(EXTRA_LISTEN_PORT, 0)
                val ip = intent.getStringExtra(EXTRA_IP).orEmpty()
                val port = intent.getIntExtra(EXTRA_PORT, 0)
                val fakeSni = intent.getStringExtra(EXTRA_FAKE_SNI).orEmpty()
                // "split" was the default here and is no method of the engine; a name it does not know is the default one.
                val method = SniSpoofConfig.method(intent.getStringExtra(EXTRA_METHOD).orEmpty())

                if (activeSessionId != 0L) {
                    RstaSpoofEngine.stop(activeSessionId)
                    activeSessionId = 0L
                }

                Log.i(TAG, "Starting RstaSpoofEngine in isolated process :snispoof on port $listenPort -> $ip:$port")
                activeSessionId = RstaSpoofEngine.start(listenPort, ip, port, fakeSni, method)
                isRunning = activeSessionId != 0L
                writeStatus(
                    if (isRunning) "active \u2192 $ip:$port (sni=$fakeSni, method=$method)"
                    else "failed: ${RstaSpoofEngine.loadError() ?: "the engine did not start (port $listenPort in use?)"}",
                )
            }
            ACTION_STOP -> {
                Log.i(TAG, "Stopping RstaSpoofEngine")
                if (activeSessionId != 0L) {
                    RstaSpoofEngine.stop(activeSessionId)
                    activeSessionId = 0L
                }
                isRunning = false
                writeStatus("inactive")
                synchronized(logLock) { flushLogLocked() }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (activeSessionId != 0L) {
            RstaSpoofEngine.stop(activeSessionId)
            activeSessionId = 0L
        }
        isRunning = false
        writeStatus("inactive")
        synchronized(logLock) { flushLogLocked() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun writeStatus(text: String) {
        runCatching { File(filesDir, STATUS_FILE).writeText(text) }
    }

    private fun appendLog(line: String) {
        synchronized(logLock) {
            if (logBuffer.size >= LOG_MAX_LINES) {
                logBuffer.removeFirst()
            }
            logBuffer.addLast(line.trim())
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastLogFlushMs >= LOG_FLUSH_INTERVAL_MS) {
                lastLogFlushMs = now
                flushLogLocked()
            }
        }
    }

    private fun flushLogLocked() {
        runCatching {
            val file = File(filesDir, LOG_FILE)
            file.writeText(logBuffer.joinToString("\n") + "\n")
        }
    }
}
