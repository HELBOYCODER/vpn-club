package com.helboy.vpnclub.ui.proto.editors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** The outcome of one connectivity test. */
sealed interface ProbeResult {
    data class Reachable(val ms: Long) : ProbeResult
    data class Unreachable(val reason: String) : ProbeResult
}

/**
 * A plain TCP connect to [host]:[port], timed. It proves the clean IP answers on that port from this network; it does
 * not run the spoof engine, so it says nothing about the fake SNI itself.
 */
object TcpProbe {
    suspend fun connect(host: String, port: Int, timeoutMs: Int = 4000): ProbeResult = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        try {
            Socket().use { it.connect(InetSocketAddress(host.trim().trim('[', ']'), port), timeoutMs) }
            ProbeResult.Reachable(((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L))
        } catch (e: Exception) {
            ProbeResult.Unreachable(reasonOf(e))
        }
    }

    fun reasonOf(e: Exception): String = when (e) {
        is SocketTimeoutException -> "مهلت اتصال تمام شد"
        is UnknownHostException -> "آدرس پیدا نشد"
        else -> "اتصال برقرار نشد"
    }
}
