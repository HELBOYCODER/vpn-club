package com.helboy.vpnclub.corex

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/** Measurements come from a bounded request through the tunnel, never from idle user traffic. */
data class ConnectionQuality(val latencyMs: Long, val downloadBps: Double) {
    val healthy: Boolean get() = latencyMs in 1..MAX_LATENCY_MS && downloadBps >= MIN_DOWNLOAD_BPS
    val dead: Boolean get() = latencyMs <= 0 || downloadBps <= 0
    companion object {
        const val MAX_LATENCY_MS = 2_000L
        const val MIN_DOWNLOAD_BPS = 64.0 * 1024
    }
}

/** One cancellable owner for discovery, connection and recovery; no competing watchdog jobs. */
class SmartConnectionLoop<T>(
    private val driver: Driver<T>,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    interface Driver<T> {
        fun key(candidate: T): String
        fun networkAvailable(): Boolean
        suspend fun discover(excluded: Set<String>): List<T>
        suspend fun connect(candidate: T): Boolean
        suspend fun quality(): ConnectionQuality
        suspend fun dropTunnel()
        fun status(status: Status, candidate: T? = null, retrySeconds: Long = 0)
    }
    enum class Status { SEARCHING, CONNECTING, CONNECTED, RECOVERING, WAITING_NETWORK, RETRYING }

    private val rejectedUntil = mutableMapOf<String, Long>()

    suspend fun run() {
        var emptyRounds = 0
        try {
            while (currentCoroutineContext().isActive) {
                if (!driver.networkAvailable()) {
                    driver.dropTunnel()
                    driver.status(Status.WAITING_NETWORK)
                    pause(NETWORK_POLL_MS)
                    continue
                }
                rejectedUntil.entries.removeAll { it.value <= now() }
                driver.status(Status.SEARCHING)
                val candidates = transient(emptyList<T>()) { driver.discover(rejectedUntil.keys.toSet()) }.distinctBy(driver::key)
                currentCoroutineContext().ensureActive()
                var hadConnection = false
                for (candidate in candidates) {
                    currentCoroutineContext().ensureActive()
                    if (!driver.networkAvailable()) break
                    if ((rejectedUntil[driver.key(candidate)] ?: 0) > now()) continue
                    driver.status(Status.CONNECTING, candidate)
                    if (!transient(false) { driver.connect(candidate) }) {
                        rejectedUntil[driver.key(candidate)] = now() + REJECT_MS
                        continue
                    }
                    currentCoroutineContext().ensureActive()
                    hadConnection = true
                    emptyRounds = 0
                    driver.status(Status.CONNECTED, candidate)
                    var deadSamples = 0
                    var weakSamples = 0
                    while (currentCoroutineContext().isActive && driver.networkAvailable()) {
                        pause(HEALTH_INTERVAL_MS)
                        currentCoroutineContext().ensureActive()
                        if (!driver.networkAvailable()) break
                        val quality = transient(ConnectionQuality(-1, 0.0)) { driver.quality() }
                        if (quality.healthy) { deadSamples = 0; weakSamples = 0; continue }
                        if (quality.dead) { deadSamples++; weakSamples = 0 }
                        else { weakSamples++; deadSamples = 0 }
                        if (deadSamples >= 2 || weakSamples >= 3) {
                            rejectedUntil[driver.key(candidate)] = now() + REJECT_MS
                            driver.status(Status.RECOVERING, candidate)
                            break
                        }
                    }
                    driver.dropTunnel()
                    // Remaining tested backups are tried first. Exhaustion always starts fresh discovery.
                }
                currentCoroutineContext().ensureActive()
                driver.dropTunnel()
                if (!driver.networkAvailable()) continue
                if (!hadConnection) emptyRounds++
                val retryMs = (5_000L shl (emptyRounds - 1).coerceIn(0, 3)).coerceAtMost(30_000L)
                driver.status(Status.RETRYING, retrySeconds = retryMs / 1000)
                pause(retryMs)
            }
        } finally { withContext(NonCancellable) { driver.dropTunnel() } }
    }

    private suspend fun <R> transient(fallback: R, block: suspend () -> R): R = try { block() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { fallback }

    companion object {
        const val HEALTH_INTERVAL_MS = 30_000L
        const val NETWORK_POLL_MS = 3_000L
        const val REJECT_MS = 5 * 60_000L
    }
}
