package com.helboy.vpnclub

import android.app.Application
import android.util.Log

class VPNClubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("VPNClubApp", "VPN CLUB Application initialized")
    }
}
