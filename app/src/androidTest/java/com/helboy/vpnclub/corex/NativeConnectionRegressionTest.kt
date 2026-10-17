package com.helboy.vpnclub.corex

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Real native Xray executable + controlled local VLESS server, not a fake
 * GoMobile driver. This guards against the JNI-collision workaround accidentally
 * disabling Hellboy's original automatic VLESS connection path.
 */
class NativeConnectionRegressionTest {
    @Test fun discoveryMeasurementAndSocksHealthAcceptWorkingVlessTunnel() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        XrayCore.ensureEnv(context)
        val backendConfig = """
            {
              "log": {"loglevel":"warning"},
              "inbounds": [{
                "listen":"127.0.0.1",
                "port":18081,
                "protocol":"vless",
                "settings":{
                  "clients":[{"id":"12345678-1234-1234-1234-123456789abc"}],
                  "decryption":"none"
                }
              }],
              "outbounds":[{"protocol":"freedom"}]
            }
        """.trimIndent()
        val backend = NativeXrayRunner.start(context, backendConfig)
        val client = XrayCore(18082).apply { setAppContext(context) }
        try {
            assertTrue("Native Xray server did not start",
                NativeXrayRunner.listenerReady(backend, 18081, 5_000))
            val config = LinkParser.parse(
                "vless://12345678-1234-1234-1234-123456789abc@127.0.0.1:18081?security=none&type=tcp#controlled-regression"
            )!!
            val (entry, result) = HellboyEngine(context).testConfig(config, allowFallback = false)
            assertTrue("Native discovery rejected working VLESS: $result", result is DelayResult.Ok)
            assertTrue(entry.latencyMs > 0)
            assertTrue("SOCKS client failed: ${client.lastError}", client.start(config))
            val quality = HealthTester.connectionQuality(18082)
            assertTrue("Working tunnel failed HTTPS activation: $quality", quality.usable)
        } finally {
            client.stop()
            NativeXrayRunner.stop(backend)
        }
    }
}
