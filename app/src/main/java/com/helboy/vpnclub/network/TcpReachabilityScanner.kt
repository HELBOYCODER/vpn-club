package com.helboy.vpnclub.network

import android.util.Log
import com.helboy.vpnclub.data.model.VpnServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class ProbeResult(
    val server: VpnServer,
    val isReachable: Boolean,
    val latencyMs: Int,
    val responsivePort: Int
)

object TcpReachabilityScanner {
    private const val TAG = "TcpReachabilityScanner"

    /**
     * Performs a low-overhead TCP socket handshake to verify if an IP/port
     * is reachable through the user's active internet connection in Iran.
     */
    suspend fun probeServer(server: VpnServer, timeoutMs: Int = 1800): ProbeResult = withContext(Dispatchers.IO) {
        val portsToTest = mutableListOf(server.port)

        // If the server's default port isn't 995 and protocol is TCP, test 995 as well
        // because SoftEther servers typically listen on 995 (MS-SSTP) which is rarely blocked in Iran.
        if (server.port != 995 && server.protocol.equals("TCP", ignoreCase = true)) {
            portsToTest.add(995)
        }

        for (port in portsToTest) {
            var socket: Socket? = null
            try {
                val start = System.currentTimeMillis()
                socket = Socket().apply {
                    tcpNoDelay = true
                    soTimeout = timeoutMs
                }
                socket.connect(InetSocketAddress(server.ip, port), timeoutMs)
                val latency = (System.currentTimeMillis() - start).toInt()

                Log.d(TAG, "Reachable: ${server.ip}:$port in ${latency}ms (${server.countryLong})")
                server.probedLatencyMs = latency
                server.probedPort = port
                return@withContext ProbeResult(server, true, latency, port)
            } catch (e: Exception) {
                // Timeout, Connection Refused, or Network Unreachable
            } finally {
                try {
                    socket?.close()
                } catch (ignored: Exception) {}
            }
        }

        server.probedLatencyMs = -1
        server.probedPort = null
        return@withContext ProbeResult(server, false, -1, server.port)
    }

    /**
     * Concurrently probes a batch of candidate servers.
     */
    suspend fun probeBatch(
        candidates: List<VpnServer>,
        timeoutMs: Int = 2000,
        maxConcurrency: Int = 10
    ): List<ProbeResult> = withContext(Dispatchers.IO) {
        coroutineScope {
            candidates.take(maxConcurrency).map { server ->
                async { probeServer(server, timeoutMs) }
            }.awaitAll()
        }
    }
}
