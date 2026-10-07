package com.helboy.vpnclub.vpn

import android.content.Context
import android.provider.Settings
import java.util.UUID

object Ipv6Ula {
    private const val PREFS_NAME = "vpnclub_ipv6_prefs"
    private const val KEY_ULA_V6 = "key_ula_v6"

    fun getOrDerive(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_ULA_V6, null)?.let { return it }

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }

        val seed = if (!androidId.isNullOrBlank()) {
            val hex = androidId.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.lowercase()
            if (hex.length >= 16) hex.take(16) else hex.padEnd(16, '0')
        } else {
            UUID.randomUUID().toString().replace("-", "").take(16)
        }

        val ula = "fd00::" + seed.chunked(4).joinToString(":")
        prefs.edit().putString(KEY_ULA_V6, ula).apply()
        return ula
    }
}
