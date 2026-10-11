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
import com.helboy.vpnclub.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

enum class SvcState { STOPPED, STARTING, UP, FAILED }

data class SvcStatus(val state: SvcState, val message: String = "", val serverId: String = "")

/**
 * سرویس پیش‌زمینه‌ی VPN: هسته (Xray) + تونل (hev) در یک سرویس.
 *
 * - کار سنگین (بالا آوردن هسته تا ۵ ثانیه) روی thread جدا، نه main.
 * - ACTION_START دوباره = «عوض‌کردن سرور»: فقط هسته ری‌استارت می‌شود، TUN بالا می‌ماند.
 * - خود اپ از تونل خارج است (Tun2Socks) تا سوکت‌های هسته در حلقه نیفتند.
 */
class VpnClubService : VpnService() {

    private var xray: XrayCore? = null
    private var tun: Tun2Socks? = null
    private var launched: LaunchedTunnel? = null
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var tunnelUp = false
    @Volatile private var tickerOn = false
    private var lastServerName = ""

    override fun onCreate() {
        super.onCreate()
        xray = XrayCore(SOCKS_PORT, useRouting = true).also { it.setAppContext(this) }
        tun = Tun2Socks(this)
        DiagLogger.appContext = applicationContext
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification("در حال اتصال…"))
                _status.value = SvcStatus(SvcState.STARTING)
                worker.execute { startTunnel() }
                START_NOT_STICKY
            }
            ACTION_STOP -> {
                worker.execute { stopAll(); finish() }
                START_NOT_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    private fun startTunnel() {
        val entry = CurrentConfigHolder.load(this)
        if (entry == null) {
            _status.value = SvcStatus(SvcState.FAILED, "سروری برای اتصال انتخاب نشده")
            if (!tunnelUp) finish()
            return
        }
        launched?.stop(); launched = null
        var l = TunnelLauncher.launch(xray!!, entry)
        if (l == null) {
            // احتمال خطای geodata در قوانین مسیریابی؛ یک بار بدون قوانین امتحان کن
            DiagLogger.log("SERVICE retry without routing: ${xray?.lastError.orEmpty().take(120)}")
            xray = XrayCore(SOCKS_PORT, useRouting = false).also { it.setAppContext(this) }
            l = TunnelLauncher.launch(xray!!, entry)
        }
        if (l == null) {
            val err = xray?.lastError.orEmpty().take(200)
            DiagLogger.log("SERVICE core_fail ${entry.config.host}:${entry.config.port} $err")
            _status.value = SvcStatus(SvcState.FAILED, "هسته بالا نیامد $err", entry.id)
            if (!tunnelUp) { stopAll(); finish() }
            return
        }
        launched = l
        if (!tunnelUp || tun?.isRunning != true) {
            val ok = tun?.start(this, SOCKS_PORT, bypassApps = AppSettings.of(this).bypassApps) == true
            if (!ok) {
                DiagLogger.log("SERVICE tun_fail (VpnService.establish)")
                stopAll()
                _status.value = SvcStatus(SvcState.FAILED, "ساخت تونل سیستم ناموفق بود: ${tun?.lastFailure.orEmpty()}")
                finish()
                return
            }
            tunnelUp = true
        }
        lastServerName = entry.config.tag.ifEmpty { entry.config.host }
        DiagLogger.log("SERVICE up ${entry.config.host}:${entry.config.port} variant=${entry.variantId} dial=${entry.config.dialHost}")
        _status.value = SvcStatus(SvcState.UP, "", entry.id)
        updateNotification("متصل — $lastServerName")
        startTicker()
    }

    private fun startTicker() {
        if (tickerOn) return
        tickerOn = true
        Thread {
            var last = Tun2Socks.bytes()
            while (tickerOn) {
                try { Thread.sleep(3_000) } catch (_: InterruptedException) { break }
                val now = Tun2Socks.bytes()
                if (now != null && last != null && tunnelUp) {
                    val up = (now.first - last.first).coerceAtLeast(0) / 3
                    val down = (now.second - last.second).coerceAtLeast(0) / 3
                    updateNotification("$lastServerName  ↓${fmt(down)}  ↑${fmt(up)}")
                }
                last = now
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun stopAll() {
        tickerOn = false
        tunnelUp = false
        try { launched?.stop() } catch (_: Exception) {}
        launched = null
        try { tun?.stop() } catch (_: Exception) {}
        try { xray?.stop() } catch (_: Exception) {}
    }

    private fun finish() {
        if (_status.value.state != SvcState.FAILED) _status.value = SvcStatus(SvcState.STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAll()
        if (_status.value.state != SvcState.FAILED) _status.value = SvcStatus(SvcState.STOPPED)
        worker.shutdownNow()
        super.onDestroy()
    }

    /** کاربر از تنظیمات سیستم VPN را قطع کرد / اپ دیگری VPN گرفت. */
    override fun onRevoke() {
        stopAll()
        _status.value = SvcStatus(SvcState.STOPPED)
        stopSelf()
    }

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
        val openIntent = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("VPN CLUB")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "قطع", stopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
        } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_START = "com.helboy.vpnclub.START"
        const val ACTION_STOP = "com.helboy.vpnclub.STOP"
        const val CHANNEL_ID = "vpnclub"
        const val NOTIF_ID = 1001
        const val SOCKS_PORT = 10808

        private val _status = MutableStateFlow(SvcStatus(SvcState.STOPPED))
        val status: StateFlow<SvcStatus> = _status.asStateFlow()

        fun fmt(bps: Long): String = when {
            bps >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fMB/s", bps / 1_000_000.0)
            bps >= 1_000 -> String.format(java.util.Locale.US, "%.0fKB/s", bps / 1_000.0)
            else -> "${bps}B/s"
        }

        fun start(context: Context) {
            // وضعیت قبلی (UP سرور قبلی) نباید به‌عنوان نتیجه‌ی این شروع خوانده شود
            _status.value = SvcStatus(SvcState.STARTING)
            context.startForegroundService(
                Intent(context, VpnClubService::class.java).setAction(ACTION_START))
        }
        fun stop(context: Context) {
            context.startService(
                Intent(context, VpnClubService::class.java).setAction(ACTION_STOP))
        }
    }
}
