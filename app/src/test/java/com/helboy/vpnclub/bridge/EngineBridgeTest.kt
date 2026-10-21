package com.helboy.vpnclub.bridge

import com.helboy.vpnclub.corex.Phase
import com.helboy.vpnclub.corex.Task
import com.helboy.vpnclub.corex.VpnController
import dev.valid7996.narcicgeteway.core.VpnManager
import dev.valid7996.narcicgeteway.domain.model.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineBridgeTest {
    private val idle = VpnController.UiState()

    @Test
    fun idleNarcicLeavesHellboyStateUntouched() {
        assertSame(idle, EngineBridge.overlay(idle, VpnManager.Status(state = ConnectionState.Idle)))
        val hb = idle.copy(connected = true, phase = Phase.CONNECTED)
        assertSame(hb, EngineBridge.overlay(hb, VpnManager.Status(state = ConnectionState.Idle)))
    }

    @Test
    fun connectedNarcicShowsAsConnectedWithStats() {
        val n = VpnManager.Status(
            state = ConnectionState.Connected, durationSeconds = 90, downloadBps = 2_000, uploadBps = 500,
            totalDownload = 10_000, totalUpload = 3_000, serverName = "Tokyo",
        )
        val ui = EngineBridge.overlay(idle, n, nowMs = 1_000_000L)
        assertTrue(ui.connected)
        assertEquals(Phase.CONNECTED, ui.phase)
        assertEquals("Tokyo", ui.message)
        assertEquals(2_000L, ui.downBps)
        assertEquals(3_000L, ui.upTotal)
        assertEquals(1_000_000L - 90_000L, ui.connectedSince)
        assertNull(ui.current)
    }

    @Test
    fun connectingNarcicDrivesTheBusyButton() {
        val ui = EngineBridge.overlay(idle, VpnManager.Status(state = ConnectionState.Connecting, serverName = "A"))
        assertFalse(ui.connected)
        assertTrue(ui.busyConnecting)
        assertEquals(Task.CONNECT, ui.task)
        assertEquals(Phase.CONNECTING, ui.phase)
    }

    @Test
    fun disconnectingNarcicShowsIdle() {
        val hb = idle.copy(phase = Phase.CONNECTED, connected = true)
        val ui = EngineBridge.overlay(hb, VpnManager.Status(state = ConnectionState.Disconnecting))
        assertFalse(ui.connected)
        assertEquals(Phase.IDLE, ui.phase)
    }

    @Test
    fun narcicErrorShowsFailureWhenHellboyIsIdle() {
        val ui = EngineBridge.overlay(idle, VpnManager.Status(state = ConnectionState.Error, error = "boom"))
        assertEquals(Phase.FAILED, ui.phase)
        assertEquals("boom", ui.message)
    }

    @Test
    fun staleNarcicErrorNeverHidesALiveHellboyConnection() {
        val hb = idle.copy(connected = true, phase = Phase.CONNECTED, message = "ok")
        assertSame(hb, EngineBridge.overlay(hb, VpnManager.Status(state = ConnectionState.Error, error = "old")))
    }

    @Test
    fun externalCardOnlyWhileNarcicOwnsTheTunnel() {
        assertNull(EngineBridge.externalOf(VpnManager.Status(state = ConnectionState.Idle)))
        assertNull(EngineBridge.externalOf(VpnManager.Status(state = ConnectionState.Error)))
        val ext = EngineBridge.externalOf(VpnManager.Status(state = ConnectionState.Connected, serverName = "X"))
        assertNotNull(ext)
        assertEquals("X", ext!!.name)
        assertTrue(ext.connected)
    }
}
