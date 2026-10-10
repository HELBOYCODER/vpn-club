package com.helboy.vpnclub.corex

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * سرویس پیش‌زمینه‌ی VPN: هسته (Xray) + تونل (hev) را در یک سرویس نگه می‌دارد.
 * با توقف سرویس، هر دو قطع می‌شوند.
 */
class VpnClubService : VpnService() {

    private var xray: XrayCore? = null
    private var tun: Tun2Socks? = null

    override fun onCreate() {
        super.onCreate()
        xray = XrayCore(SOCKS_PORT).also { it.setAppContext(this) }
        tun = Tun2Socks(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        return when (action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification("در حال اتصال…"))
                val cfg = CurrentConfigHolder.load(this)
                if (cfg == null) { stopSelf(); START_NOT_STICKY }
                else {
                    // پروفایل ایران (ECH / IPv6 / F&F) — همان که در تست موفق بود
                    xray?.setVariant(CurrentConfigHolder.loadVariant(this))
                    val coreOk = xray?.start(cfg) == true
                    val tunOk = coreOk && tun?.start(this, SOCKS_PORT) == true
                    updateNotification(if (tunOk) "متصل — VPN CLUB" else "اتصال ناموفق")
                    if (!tunOk) { stopAll(); stopSelf() }
                    START_STICKY
                }
            }
            ACTION_STOP -> { stopAll(); stopSelf(); START_NOT_STICKY }
            else -> START_NOT_STICKY
        }
    }

    private fun stopAll() {
        tun?.stop(); xray?.stop()
    }

    override fun onDestroy() { stopAll(); super.onDestroy() }

    override fun onRevoke() { stopAll(); stopSelf() }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "VPN CLUB", NotificationManager.IMPORTANCE_LOW))
        }
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, VpnClubService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("VPN CLUB")
            .setContentText(text)
            .setOngoing(true)
            .addAction(0, "قطع", stopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_START = "com.helboy.vpnclub.START"
        const val ACTION_STOP = "com.helboy.vpnclub.STOP"
        const val CHANNEL_ID = "vpnclub"
        const val NOTIF_ID = 1001
        const val SOCKS_PORT = 10808

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, VpnClubService::class.java).setAction(ACTION_START))
        }
        fun stop(context: Context) {
            context.startService(
                Intent(context, VpnClubService::class.java).setAction(ACTION_STOP))
        }
    }
}
