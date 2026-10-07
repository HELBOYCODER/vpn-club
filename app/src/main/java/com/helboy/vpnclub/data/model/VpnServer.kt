package com.helboy.vpnclub.data.model

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

data class VpnServer(
    val hostName: String,
    val ip: String,
    val score: Int = 0,
    val ping: Int = 0,
    val speed: Long = 0L,
    val countryLong: String = "Unknown",
    val countryShort: String = "UN",
    val numVpnSessions: Int = 0,
    val uptime: Long = 0L,
    val totalUsers: Long = 0L,
    val totalTraffic: Long = 0L,
    val logType: String = "",
    val operator: String = "",
    val message: String = "",
    val configDataBase64: String = ""
) {
    // Dynamically tested reachability latency & port from the user's active device connection
    var probedLatencyMs: Int? = null
    var probedPort: Int? = null

    val isTsukubaSubnet: Boolean
        get() = ip.startsWith("219.100.37.") || ip.startsWith("219.100.") || ip.startsWith("130.158.")

    val port: Int by lazy {
        extractPortFromConfig()
    }

    val protocol: String by lazy {
        extractProtocolFromConfig()
    }

    /**
     * Determines whether this server is likely reachable on Iranian networks:
     * - Excludes known blacklisted Tsukuba university subnets (219.100.* and 130.158.*)
     * - Prioritizes TCP connections (especially port 995 MS-SSTP, non-standard high ports, and port 443 on non-Tsukuba IPs)
     */
    val isIranCompatible: Boolean
        get() {
            if (isTsukubaSubnet) return false
            return protocol.equals("TCP", ignoreCase = true) || port == 995 || port > 1024
        }

    val countryFlag: String
        get() {
            if (countryShort.length != 2) return "🌐"
            return try {
                val upper = countryShort.uppercase()
                val firstChar = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
                val secondChar = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
                String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
            } catch (e: Exception) {
                "🌐"
            }
        }

    val speedMbpsFormatted: String
        get() {
            val mbps = speed.toDouble() / (1000.0 * 1000.0)
            return if (mbps >= 1.0) {
                String.format("%.1f Mbps", mbps)
            } else {
                String.format("%.0f Kbps", (speed.toDouble() / 1000.0).coerceAtLeast(10.0))
            }
        }

    val pingFormatted: String
        get() {
            return if (probedLatencyMs != null && probedLatencyMs!! > 0) {
                "${probedLatencyMs} ms (تست زنده)"
            } else if (ping > 0) {
                "${ping} ms"
            } else {
                "نامشخص"
            }
        }

    val protocolDetected: String
        get() = "$protocol $port"

    private fun extractPortFromConfig(): Int {
        if (configDataBase64.isBlank()) return 443
        return try {
            val bytes = Base64.decode(configDataBase64, Base64.DEFAULT)
            val str = String(bytes, StandardCharsets.UTF_8)
            val matcher = Pattern.compile("""^\s*remote\s+[\w\.\-]+\s+(\d+)""", Pattern.MULTILINE).matcher(str)
            if (matcher.find()) {
                matcher.group(1)?.toIntOrNull() ?: 443
            } else {
                443
            }
        } catch (e: Exception) {
            443
        }
    }

    private fun extractProtocolFromConfig(): String {
        if (configDataBase64.isBlank()) return "TCP"
        return try {
            val bytes = Base64.decode(configDataBase64, Base64.DEFAULT)
            val str = String(bytes, StandardCharsets.UTF_8)
            val matcher = Pattern.compile("""^\s*proto\s+(\w+)""", Pattern.MULTILINE).matcher(str)
            if (matcher.find()) {
                val proto = matcher.group(1)?.uppercase() ?: "TCP"
                if (proto.contains("UDP")) "UDP" else "TCP"
            } else {
                "TCP"
            }
        } catch (e: Exception) {
            "TCP"
        }
    }

    /**
     * Generates a sanitized and optimized OpenVPN configuration file:
     * 1. Replaces remote domain names with the direct IPv4 to bypass Iranian DNS poisoning.
     * 2. Supports customPort override (e.g. falling back to port 995 if 443 is filtered).
     * 3. Injects cellular MTU and MSS clamping (mssfix 1280) to prevent packet drops on MCI/Irancell LTE.
     * 4. Injects auto-credentials (auth-user-pass) and full gateway routing.
     */
    fun getDecodedOvpnConfig(customPort: Int? = null): String {
        if (configDataBase64.isBlank()) return ""
        val bytes = Base64.decode(configDataBase64, Base64.DEFAULT)
        var configStr = String(bytes, StandardCharsets.UTF_8)

        val targetPort = if (customPort != null && customPort > 0) customPort else port

        // Enforce direct IP address and target port to bypass DNS poisoning and SNI filtering
        configStr = configStr.replace(Regex("""^\s*remote\s+[\w\.\-]+\s+\d+""", RegexOption.MULTILINE), "remote $ip $targetPort")

        // Ensure auth-user-pass directive is active so default credentials (vpn/vpn) are injected
        if (configStr.contains("#auth-user-pass")) {
            configStr = configStr.replace("#auth-user-pass", "auth-user-pass")
        } else if (!configStr.contains("auth-user-pass")) {
            configStr = configStr + "\nauth-user-pass\n"
        }

        // Add compatibility routing
        if (!configStr.contains("redirect-gateway")) {
            configStr = configStr + "\nredirect-gateway def1\n"
        }

        // Anti-throttling & cellular MTU/MSS tuning directives for Iran networks
        val tuningDirectives = """
            
# VPN CLUB Anti-Throttle & Cellular Tuning
mssfix 1280
tun-mtu 1400
connect-retry 1 300
connect-retry-max 1
connect-timeout 8
handshake-window 15
resolv-retry 3
nobind
persist-key
persist-tun
data-ciphers AES-128-CBC:AES-256-CBC:AES-128-GCM:AES-256-GCM:BF-CBC
""".trimIndent()

        configStr = configStr + "\n" + tuningDirectives

        return configStr
    }

    companion object {
        fun fromCsvLine(line: String): VpnServer? {
            if (line.isBlank() || line.startsWith("*") || line.startsWith("#")) return null
            val tokens = line.split(",".toRegex()).toTypedArray()
            if (tokens.size < 15) return null

            return try {
                VpnServer(
                    hostName = tokens[0].trim(),
                    ip = tokens[1].trim(),
                    score = tokens[2].trim().toIntOrNull() ?: 0,
                    ping = tokens[3].trim().toIntOrNull() ?: 0,
                    speed = tokens[4].trim().toLongOrNull() ?: 0L,
                    countryLong = tokens[5].trim().ifBlank { "Unknown" },
                    countryShort = tokens[6].trim().ifBlank { "UN" },
                    numVpnSessions = tokens[7].trim().toIntOrNull() ?: 0,
                    uptime = tokens[8].trim().toLongOrNull() ?: 0L,
                    totalUsers = tokens[9].trim().toLongOrNull() ?: 0L,
                    totalTraffic = tokens[10].trim().toLongOrNull() ?: 0L,
                    logType = tokens[11].trim(),
                    operator = tokens[12].trim(),
                    message = tokens[13].trim(),
                    configDataBase64 = tokens[14].trim()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
