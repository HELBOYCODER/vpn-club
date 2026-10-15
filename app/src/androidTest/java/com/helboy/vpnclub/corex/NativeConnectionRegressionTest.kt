package com.helboy.vpnclub.corex

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import libv2ray.CoreCallbackHandler
import libv2ray.Libv2ray
import org.junit.Assert.*
import org.junit.Test

/** Real JNI/core requests through a controlled local VLESS backend, not a fake driver. */
class NativeConnectionRegressionTest {
    @Test fun discoveryMeasurementAndSocksHealthAcceptWorkingVlessTunnel() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        XrayCore.ensureEnv(context)
        val callback = object : CoreCallbackHandler {
            override fun onEmitStatus(code: Long, message: String?): Long = 0
            override fun shutdown(): Long = 0
            override fun startup(): Long = 0
        }
        val backend = Libv2ray.newCoreController(callback)
        val client = XrayCore(18082)
        try {
            backend.startLoop("""{"log":{"loglevel":"warning"},"inbounds":[{"listen":"127.0.0.1","port":18081,"protocol":"vless","settings":{"clients":[{"id":"12345678-1234-1234-1234-123456789abc"}],"decryption":"none"}}],"outbounds":[{"protocol":"freedom"}]}""", 0)
            val config = LinkParser.parse("vless://12345678-1234-1234-1234-123456789abc@127.0.0.1:18081?security=none&type=tcp#controlled-regression")!!
            val (entry, result) = HellboyEngine(context).testConfig(config, allowFallback = false)
            assertTrue("Native discovery rejected working VLESS: $result", result is DelayResult.Ok)
            assertTrue(entry.latencyMs > 0)
            assertTrue("SOCKS client failed: ${client.lastError}", client.start(config))
            val quality = HealthTester.connectionQuality(18082)
            assertTrue("Working tunnel failed HTTPS activation: $quality", quality.usable)
        } finally {
            client.stop()
            backend.stopLoop()
        }
    }
}
