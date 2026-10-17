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

enum class SvcState { STOPPED, SEARCHING, STARTING, UP, FAILED }

data class SvcStatus(
    val state: SvcState,
    val message: String = "",
    val serverId: String = "",
    /** فقط وقتی state == FAILED: علت دقیق و نوع‌دار. */
    val error: SvcError? = null
)

/**
 * سرویس پیش‌زمینه‌ی VPN: هسته (Xray) + تونل (hev) در یک سرویس.
 *
 * - کار سنگین (بالا آوردن هسته تا ۵ ثانیه) روی thread جدا، نه main.
 * - ACTION_START دوباره = «عوض‌کردن سرور»: فقط هسته ری‌استارت می‌شود، TUN بالا می‌ماند.
 * - خود اپ از تونل خارج است (Tun2Socks) تا سوکت‌های هسته در حلقه نیفتند.
 */
class HellboyVpnService : VpnService() {

    private var xray: XrayCore? = null
    private var tun: Tun2Socks? = null
    private var launched: LaunchedTunnel? = null
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var tunnelUp = false
    @Volatile private var tickerOn = false
    private var ticker: Thread? = null
    private val commandEpoch = java.util.concurrent.atomic.AtomicLong()
    private var lastServerName = ""

    override fun onCreate() {
        super.onCreate()
        VpnController.init(applicationContext)
        xray = XrayCore(SOCKS_PORT, useRouting = true).also { it.setAppContext(this) }
        tun = Tun2Socks(this)
        DiagLogger.appContext = applicationContext
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = intent?.getLongExtra("session", -1) ?: -1
        if (intent?.action in listOf(ACTION_SEARCH, ACTION_START) && !VpnController.isSessionCurrent(session)) {
            if (!VpnController.state.value.sessionActive) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP && session >= 0 && VpnController.state.value.sessionActive && !VpnController.isSessionCurrent(session))
            return START_NOT_STICKY
        val token = commandEpoch.incrementAndGet()
        when (intent?.action) {
            ACTION_SEARCH -> {
                startForeground(NOTIF_ID, buildNotification("جست‌وجوی خودکار سرور…"))
                worker.execute {
                    if (token == commandEpoch.get()) {
                        stopAll()
                        _status.value = SvcStatus(SvcState.SEARCHING)
                        updateNotification("جست‌وجوی خودکار سرور…")
                    }
                }
            }
            ACTION_START -> {
                val entry = CurrentConfigHolder.load(this)
                startForeground(NOTIF_ID, buildNotification("در حال اتصال…"))
                _status.value = SvcStatus(SvcState.STARTING)
                worker.execute { startTunnel(entry, token, session) }
            }
            ACTION_STOP -> {
                VpnController.stopSession()
                worker.execute { stopAll(); finish() }
            }
            else -> { worker.execute { stopAll(); finish() } }
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(entry: ServerEntry?, token: Long, session: Long) {
        fun current() = token == commandEpoch.get() && VpnController.isSessionCurrent(session)
        if (!current()) return
        if (entry == null) {
            _status.value = SvcStatus(SvcState.FAILED, SvcError.NoServer.userMessage, error = SvcError.NoServer)
            return
        }
        launched?.stop(); launched = null
        val result = TunnelLauncher.launch(xray!!, entry)
        if (!current()) { result?.stop(); stopAll(); return }
        if (result == null) {
            val error = SvcError.Core(xray?.lastError.orEmpty().take(200))
            _status.value = SvcStatus(SvcState.FAILED, error.userMessage, entry.id, error)
            updateNotification("این سرور پاسخ نداد؛ انتخاب سرور بعدی…")
            return
        }
        launched = result
        if (!tunnelUp || tun?.isRunning != true) {
            if (tun?.start(this, SOCKS_PORT, bypassApps = AppSettings.of(this).bypassApps) != true) {
                stopAll()
                val error = SvcError.Tun(tun?.lastFailureKind ?: TunFailure.HEV_FAILED, tun?.lastFailure.orEmpty())
                _status.value = SvcStatus(SvcState.FAILED, error.userMessage, entry.id, error)
                return
            }
            tunnelUp = true
        }
        if (!current()) { stopAll(); return }
        lastServerName = entry.config.tag.ifEmpty { entry.config.host }
        _status.value = SvcStatus(SvcState.UP, "", entry.id)
        updateNotification("متصل — $lastServerName")
        startTicker()
    }

    private fun startTicker() {
        if (tickerOn) return
        tickerOn = true
        ticker = Thread {
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
        }.also { it.isDaemon = true; it.start() }
    }

    private fun stopAll() {
        tickerOn = false
        ticker?.interrupt(); ticker = null
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
        commandEpoch.incrementAndGet()
        VpnController.stopSession()
        stopAll()
        _status.value = SvcStatus(SvcState.STOPPED)
        stopSelf()
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Hellboy VPN", NotificationManager.IMPORTANCE_LOW))
        }
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, HellboyVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        val openIntent = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Hellboy VPN")
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
        const val ACTION_SEARCH = "com.helboy.vpnclub.SEARCH"
        val activeSocksPort: Int get() = SOCKS_PORT

        fun search(context: Context, session: Long) {
            context.startForegroundService(Intent(context, HellboyVpnService::class.java)
                .setAction(ACTION_SEARCH).putExtra("session", session))
        }

        private val _status = MutableStateFlow(SvcStatus(SvcState.STOPPED))
        val status: StateFlow<SvcStatus> = _status.asStateFlow()

        fun fmt(bps: Long): String = when {
            bps >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fMB/s", bps / 1_000_000.0)
            bps >= 1_000 -> String.format(java.util.Locale.US, "%.0fKB/s", bps / 1_000.0)
            else -> "${bps}B/s"
        }

        fun start(context: Context, session: Long) {
            // وضعیت قبلی (UP سرور قبلی) نباید به‌عنوان نتیجه‌ی این شروع خوانده شود
            _status.value = SvcStatus(SvcState.STARTING)
            context.startForegroundService(
                Intent(context, HellboyVpnService::class.java).setAction(ACTION_START).putExtra("session", session))
        }
        fun stop(context: Context, session: Long) {
            context.startService(
                Intent(context, HellboyVpnService::class.java).setAction(ACTION_STOP).putExtra("session", session))
        }
    }
}
