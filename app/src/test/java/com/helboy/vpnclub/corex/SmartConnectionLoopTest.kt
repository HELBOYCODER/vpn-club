package com.helboy.vpnclub.corex

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SmartConnectionLoopTest {
    private class End : CancellationException()
    private class Fake : SmartConnectionLoop.Driver<String> {
        var online = true
        val scans = mutableListOf<Set<String>>()
        val connects = mutableListOf<String>()
        val statuses = mutableListOf<SmartConnectionLoop.Status>()
        var drops = 0
        var scan: suspend (Int) -> List<String> = { listOf("a", "b") }
        var connectHandler: suspend (String) -> Boolean = { true }
        var health: () -> ConnectionQuality = { ConnectionQuality(100, 256_000.0) }
        var onStatus: (SmartConnectionLoop.Status, String?) -> Unit = { _, _ -> }
        override fun key(candidate: String) = candidate
        override fun networkAvailable() = online
        override suspend fun discover(excluded: Set<String>): List<String> {
            scans += excluded
            return scan(scans.size)
        }
        override suspend fun connect(candidate: String): Boolean { connects += candidate; return connectHandler(candidate) }
        override suspend fun quality() = health()
        override suspend fun dropTunnel() { drops++ }
        override fun status(status: SmartConnectionLoop.Status, candidate: String?, retrySeconds: Long) {
            statuses += status; onStatus(status, candidate)
        }
    }
    private fun run(fake: Fake, pause: suspend (Long) -> Unit = {}) = runBlocking {
        try { SmartConnectionLoop(fake, pause = pause).run() } catch (_: End) { }
    }

    @Test fun allFailedCandidatesStartFreshDiscoveryAndConnectNewServer() {
        val fake = Fake()
        fake.scan = { if (it == 1) listOf("a", "b") else listOf("fresh") }
        fake.connectHandler = { it == "fresh" }
        fake.onStatus = { status, _ -> if (status == SmartConnectionLoop.Status.CONNECTED) throw End() }
        run(fake)
        assertEquals(listOf("a", "b", "fresh"), fake.connects)
        assertEquals(setOf("a", "b"), fake.scans[1])
    }
    @Test fun threeWeakMeasurementsSwitchToBackupWithoutRescan() {
        val fake = Fake(); var samples = 0
        fake.health = { samples++; ConnectionQuality(300, 10_000.0) }
        fake.onStatus = { status, candidate -> if (status == SmartConnectionLoop.Status.CONNECTED && candidate == "b") throw End() }
        run(fake)
        assertEquals(3, samples)
        assertEquals(listOf("a", "b"), fake.connects)
        assertEquals(1, fake.scans.size)
    }
    @Test fun twoDeadMeasurementsTriggerRecovery() {
        val fake = Fake(); var samples = 0
        fake.health = { samples++; ConnectionQuality(-1, 0.0) }
        fake.onStatus = { status, candidate -> if (status == SmartConnectionLoop.Status.CONNECTED && candidate == "b") throw End() }
        run(fake)
        assertEquals(2, samples)
        assertTrue(SmartConnectionLoop.Status.RECOVERING in fake.statuses)
    }
    @Test fun oneBadMeasurementFollowedByHealthyTunnelDoesNotSwitch() {
        val fake = Fake(); var samples = 0
        fake.health = {
            samples++
            if (samples == 5) throw End()
            if (samples == 1) ConnectionQuality(-1, 0.0) else ConnectionQuality(100, 256_000.0)
        }
        run(fake)
        assertEquals(listOf("a"), fake.connects)
        assertFalse(SmartConnectionLoop.Status.RECOVERING in fake.statuses)
    }
    @Test fun offlineWaitsWithoutScanningThenStartsAfterNetworkReturns() {
        val fake = Fake(); fake.online = false
        fake.onStatus = { status, _ -> if (status == SmartConnectionLoop.Status.CONNECTED) throw End() }
        run(fake) { assertTrue(fake.scans.isEmpty()); fake.online = true }
        assertEquals(1, fake.scans.size)
        assertEquals(SmartConnectionLoop.Status.WAITING_NETWORK, fake.statuses.first())
    }
    @Test fun transientDiscoveryFailureIsRetried() {
        val fake = Fake()
        fake.scan = { if (it == 1) throw java.io.IOException("offline feed") else listOf("fresh") }
        fake.onStatus = { status, _ -> if (status == SmartConnectionLoop.Status.CONNECTED) throw End() }
        run(fake)
        assertEquals(2, fake.scans.size)
        assertEquals(listOf("fresh"), fake.connects)
    }
    @Test fun cancellingDuringDiscoveryPreventsLaterActivationAndDropsTunnel() = runBlocking {
        val fake = Fake(); val entered = CompletableDeferred<Unit>()
        fake.scan = { entered.complete(Unit); awaitCancellation() }
        val job = launch { SmartConnectionLoop(fake).run() }
        entered.await(); job.cancelAndJoin()
        assertTrue(fake.connects.isEmpty())
        assertTrue(fake.drops > 0)
    }
    @Test fun everyNewSessionStartsWithFreshDiscovery() {
        val fake = Fake()
        fake.onStatus = { status, _ -> if (status == SmartConnectionLoop.Status.CONNECTED) throw End() }
        run(fake); run(fake)
        assertEquals(2, fake.scans.size)
    }
    @Test fun exhaustedWeakPoolRescansWithoutRepeatingRejectedServer() {
        val fake = Fake()
        fake.scan = { if (it == 1) listOf("weak") else listOf("weak", "fresh") }
        fake.health = { ConnectionQuality(3_000, 200_000.0) }
        fake.onStatus = { status, candidate -> if (status == SmartConnectionLoop.Status.CONNECTED && candidate == "fresh") throw End() }
        run(fake)
        assertEquals(listOf("weak", "fresh"), fake.connects)
        assertEquals(setOf("weak"), fake.scans[1])
    }
    @Test fun alternatingDeadAndWeakSamplesStillRecoverAfterThreeUnhealthyChecks() {
        val fake = Fake(); var samples = 0
        fake.health = { samples++; if (samples % 2 == 0) ConnectionQuality(-1, 0.0) else ConnectionQuality(3_000, 20_000.0) }
        fake.onStatus = { status, candidate -> if (status == SmartConnectionLoop.Status.CONNECTED && candidate == "b") throw End() }
        run(fake)
        assertEquals(3, samples)
        assertEquals(listOf("a", "b"), fake.connects)
    }
    @Test fun qualityRequiresTrafficAndLatencyWithinLimits() {
        assertFalse(ConnectionQuality(100, 0.0).healthy)
        assertFalse(ConnectionQuality(3_000, 1_000_000.0).healthy)
        assertTrue(ConnectionQuality(100, 128_000.0).healthy)
    }
}
