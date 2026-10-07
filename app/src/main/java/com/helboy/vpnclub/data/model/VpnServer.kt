package com.helboy.vpnclub.data.model

import android.util.Base64
import java.nio.charset.StandardCharsets

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
        get() = if (ping > 0) "${ping} ms" else "نامشخص"

    val protocolDetected: String
        get() {
            return try {
                val decoded = getDecodedOvpnConfig()
                if (decoded.contains("proto udp", ignoreCase = true)) "UDP" else "TCP"
            } catch (e: Exception) {
                "TCP"
            }
        }

    fun getDecodedOvpnConfig(): String {
        if (configDataBase64.isBlank()) return ""
        val bytes = Base64.decode(configDataBase64, Base64.DEFAULT)
        var configStr = String(bytes, StandardCharsets.UTF_8)

        // Ensure auth-user-pass directive is active so default credentials (vpn/vpn) are used
        if (configStr.contains("#auth-user-pass")) {
            configStr = configStr.replace("#auth-user-pass", "auth-user-pass")
        } else if (!configStr.contains("auth-user-pass")) {
            configStr = configStr + "\nauth-user-pass\n"
        }

        // Add compatibility options
        if (!configStr.contains("redirect-gateway")) {
            configStr = configStr + "\nredirect-gateway def1\n"
        }

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
