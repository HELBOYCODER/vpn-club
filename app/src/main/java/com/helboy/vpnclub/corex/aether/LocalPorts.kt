package com.helboy.vpnclub.corex.aether

/** پورت‌های لوپ‌بک Aether (از Narcic Gateway؛ GPL-3.0). */
object LocalPorts {
    const val AETHER_SOCKS = 10890

    /** SOCKS خروجیِ Xray کمکی که هسته‌ی Aether از آن بیرون می‌رود (فعلاً استفاده نمی‌شود). */
    const val AETHER_EXIT = AETHER_SOCKS + 3
}
