package com.helboy.vpnclub.corex

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * پروکسی محلی تکه‌کننده‌ی ClientHello (Anti-DPI) — پورت Kotlin از SenPaiScanner.
 *
 * چرا لازم است: DPI ایران SNI را داخل نخستین TLS record می‌بیند و اتصال را می‌بندد.
 * اگر نخستین record را به چند تکه با فاصله‌ی زمانی بشکنیم، DPI نمی‌تواند SNI را
 * تطبیق دهد و اتصال عبور می‌کند. این همان روشی است که PattNG و SenPaiScanner استفاده می‌کنند.
 *
 * معماری: روی 127.0.0.1 گوش می‌دهد؛ هسته‌ی Xray به‌جای آدرس واقعی، به این پروکسی وصل
 * می‌شود. پروکسی اتصال واقعی را می‌زند و نخستین flight را تکه‌تکه می‌فرستد.
 *
 * مقادیر پیش‌فرض = پروفایل منتشرشده‌ی ایران (t.me/MatinSenPaii/5469).
 */
class FragmentProxy(
    private val targetHost: String,
    private val targetPort: Int,
    private val lengthFirst: Int = 104,
    private val delayMs: Long = 0
) {
    private var server: ServerSocket? = null
    private var acceptThread: Thread? = null

    @Volatile private var running = false

    /** پورتی که پروکسی روی آن گوش می‌دهد (0 = ناموفق). */
    var port: Int = 0
        private set

    /** شروع گوش‌دادن. true = موفق. */
    fun start(): Boolean {
        if (running) return true
        return try {
            val s = ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"))
            server = s
            port = s.localPort
            running = true
            acceptThread = thread(name = "frag-accept", isDaemon = true) {
                while (running) {
                    val client = try { s.accept() } catch (_: Exception) { break }
                    thread(isDaemon = true) { handle(client) }
                }
            }
            Log.i(TAG, "fragment proxy on 127.0.0.1:$port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "fragment proxy start failed", e)
            false
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        server = null
        port = 0
    }

    val isRunning: Boolean get() = running

    private fun handle(client: Socket) {
        var upstream: Socket? = null
        try {
            client.tcpNoDelay = true
            upstream = Socket()
            upstream.tcpNoDelay = true
            upstream.connect(InetSocketAddress(targetHost, targetPort), CONNECT_TIMEOUT_MS)

            val up = upstream
            // سرور → کلاینت: بدون تغییر
            thread(isDaemon = true) {
                try { pipe(up.getInputStream(), client.getOutputStream()) } catch (_: Exception) {}
                finally { runCatching { client.close() } }
            }

            // کلاینت → سرور: نخستین TLS record را تکه‌تکه می‌کنیم
            val input = client.getInputStream()
            val out = upstream.getOutputStream()
            val header = ByteArray(5)
            if (!readFully(input, header)) return

            // اگر TLS handshake نیست، ساده کپی کن
            if (header[0].toInt() and 0xFF != 0x16) {
                out.write(header); out.flush()
                pipe(input, out)
                return
            }

            val recordLen = ((header[3].toInt() and 0xFF) shl 8) or (header[4].toInt() and 0xFF)
            val body = ByteArray(recordLen)
            if (!readFully(input, body)) return

            // تکه‌بندی: نخستین lengthFirst بایت، سپس بقیه (پروفایل پیش‌فرض ایران)
            var sent = 0
            val pieces = mutableListOf<Int>()
            if (lengthFirst in 1 until recordLen) {
                pieces.add(lengthFirst)
                pieces.add(recordLen - lengthFirst)
            } else {
                pieces.add(recordLen)
            }

            for ((i, size) in pieces.withIndex()) {
                val rec = ByteArray(5 + size)
                System.arraycopy(header, 0, rec, 0, 3)
                rec[3] = ((size shr 8) and 0xFF).toByte()
                rec[4] = (size and 0xFF).toByte()
                System.arraycopy(body, sent, rec, 5, size)
                out.write(rec)
                out.flush()
                sent += size
                if (i < pieces.size - 1 && delayMs > 0) Thread.sleep(delayMs)
            }

            // بقیه‌ی جریان، بدون تغییر
            pipe(input, out)
        } catch (_: Exception) {
        } finally {
            runCatching { upstream?.close() }
            runCatching { client.close() }
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        val buf = ByteArray(32 * 1024)
        while (running) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            output.flush()
        }
    }

    companion object {
        private const val TAG = "FragmentProxy"
        private const val CONNECT_TIMEOUT_MS = 10_000
    }
}
