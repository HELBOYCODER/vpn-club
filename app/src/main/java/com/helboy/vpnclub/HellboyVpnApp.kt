package com.helboy.vpnclub

import android.app.Application
import android.util.Log

class HellboyVpnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("HellboyVpnApp", "VPN CLUB Application initialized")
    }
}
