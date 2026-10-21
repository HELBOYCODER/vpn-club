package com.helboy.vpnclub.ui.proto.engine

import com.helboy.vpnclub.ui.proto.engine.MonitorLogic.pushed
import dev.valid7996.narcicgeteway.core.VpnManager
import dev.valid7996.narcicgeteway.domain.model.ConnectionState
import dev.valid7996.narcicgeteway.ui.platform.LiveStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MonitorLogicTest {
    @Test fun seriesKeepsTheNewestSamples() {
        var s = emptyList<Float>()
        repeat(200) { s = s.pushed(it.toFloat()) }
        assertEquals(MonitorLogic.HISTORY, s.size)
        assertEquals(199f, s.last(), 0f)
        assertEquals((200 - MonitorLogic.HISTORY).toFloat(), s.first(), 0f)
    }

    @Test fun ceilingNeverFallsBelowTheFloor() {
        assertEquals(1f, MonitorLogic.ceiling(emptyList(), emptyList()), 0f)
        assertEquals(50_000f, MonitorLogic.ceiling(listOf(1f), listOf(2f), floor = 50_000f), 0f)
        assertEquals(900f, MonitorLogic.ceiling(listOf(100f, 900f), listOf(2f), floor = 10f), 0f)
    }

    @Test fun cpuIsSharedAcrossCores() {
        assertEquals(25f, MonitorLogic.deviceShare(LiveStats(cpuPercent = 200f, coreCount = 8))!!, 0.001f)
        assertNull(MonitorLogic.deviceShare(LiveStats(cpuPercent = null)))
        assertEquals(100f, MonitorLogic.deviceShare(LiveStats(cpuPercent = 900f, coreCount = 1))!!, 0f)
    }

    @Test fun pressureFollowsHeatAndLoad() {
        assertEquals(MonitorLogic.Pressure.Calm, MonitorLogic.pressure(LiveStats(tempC = 30f, cpuPercent = 10f, coreCount = 4)))
        assertEquals(MonitorLogic.Pressure.Moderate, MonitorLogic.pressure(LiveStats(tempC = 40f)))
        assertEquals(MonitorLogic.Pressure.High, MonitorLogic.pressure(LiveStats(tempC = 45f)))
        assertEquals(MonitorLogic.Pressure.High, MonitorLogic.pressure(LiveStats(thermalStatus = 3)))
        assertEquals(MonitorLogic.Pressure.Calm, MonitorLogic.pressure(LiveStats()))
    }

    @Test fun unknownValuesShowADash() {
        assertEquals("—", MonitorLogic.percent(null))
        assertEquals("—", MonitorLogic.memory(null))
        assertEquals("—", MonitorLogic.temperature(null))
    }

    @Test fun everyConnectionStateHasALabel() {
        ConnectionState.entries.forEach { assertTrue(MonitorLogic.stateLabel(it).isNotBlank()) }
    }

    @Test fun engineNameFollowsTheActiveKindAndFallsBackToTheState() {
        assertEquals("Xray-Core", MonitorLogic.engineName(VpnManager.KIND_XRAY, true))
        assertEquals("Sing-Box", MonitorLogic.engineName(VpnManager.KIND_SINGBOX, true))
        assertEquals("هسته‌ی VPN", MonitorLogic.engineName(null, true))
        assertEquals("بیکار", MonitorLogic.engineName(null, false))
    }

    @Test fun thermalLabelIgnoresNoneAndGrowsWithSeverity() {
        assertNull(MonitorLogic.thermalLabel(null))
        assertNull(MonitorLogic.thermalLabel(0))
        assertTrue(MonitorLogic.thermalLabel(1) != MonitorLogic.thermalLabel(3))
    }

    @Test fun cryptoLineIsReadAsAcceleratedOnlyWhenSomethingWasListed() {
        assertTrue(MonitorLogic.cryptoAccelerated("already=[aes] repaired=[]"))
        assertTrue(MonitorLogic.cryptoAccelerated("already=[] repaired=[sha]"))
        assertFalse(MonitorLogic.cryptoAccelerated("already=[] repaired=[]"))
        assertFalse(MonitorLogic.cryptoAccelerated(""))
    }
}
