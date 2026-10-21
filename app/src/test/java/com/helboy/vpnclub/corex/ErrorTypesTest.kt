package com.helboy.vpnclub.corex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorTypesTest {
    @Test fun delayClassification() {
        assertEquals(DelayResult.Ok(120), DelayResult.fromDelay(120, 4_000))
        assertEquals(DelayResult.Slow(5_000), DelayResult.fromDelay(5_000, 4_000))
        assertTrue(DelayResult.fromDelay(-1, 4_000) is DelayResult.CoreError)
    }

    @Test fun coreMessageClassification() {
        // پیام واقعی دیده‌شده در لاگ کاربر
        assertTrue(DelayResult.fromCoreMessage("config error: infra/conf/serial: failed to parse json config > infra/conf: failed to build routing configuration")
            is DelayResult.ConfigError)
        assertTrue(DelayResult.fromCoreMessage("dial tcp 1.2.3.4:443: connect: connection refused") is DelayResult.CoreError)
    }

    @Test fun svcErrorMessages() {
        assertEquals("مجوز VPN لازم است", SvcError.Tun(TunFailure.PERMISSION_REVOKED, "").userMessage)
        assertEquals("سروری برای اتصال انتخاب نشده", SvcError.NoServer.userMessage)
    }
}
