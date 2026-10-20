package dev.valid7996.narcicgeteway.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import dev.valid7996.narcicgeteway.domain.model.NotifChip
import androidx.core.graphics.drawable.IconCompat
import dev.valid7996.narcicgeteway.R
import dev.valid7996.narcicgeteway.ui.format.formatBytes
import dev.valid7996.narcicgeteway.ui.format.formatRate

class VpnNotifications(private val ctx: Context) {
    enum class Stage { Preparing, Interface, Engine, Tunnel, Connected }

    sealed interface Phase {
        data class Connecting(val stage: Stage) : Phase
        data object Connected : Phase

        data class Failed(val reason: String, val retryable: Boolean = true) : Phase
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            ctx.getString(R.string.notif_channel_name),

            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = ctx.getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun build(
        server: String,
        phase: Phase,
        protocolLabel: String = "",
        downBps: Long = 0,
        upBps: Long = 0,
        totalDown: Long = 0,
        totalUp: Long = 0,
        connectedAt: Long = 0,
        showSpeed: Boolean = true,
        livePromotion: Boolean = true,
        chip: NotifChip = NotifChip.Speed,
        autoBadge: String? = null,
    ): android.app.Notification {
        val title = BidiText.auto(server.ifBlank { ctx.getString(R.string.app_name) })
        val b = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_nrg)
            .setContentTitle(title)
            .setColor(BRAND)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp())

            .setRequestPromotedOngoing(livePromotion)

        return when (phase) {
            is Phase.Connecting -> b.applyConnecting(phase.stage, protocolLabel).build()
            Phase.Connected -> b.applyConnected(
                autoBadge,
                protocolLabel, downBps, upBps, totalDown, totalUp, connectedAt, showSpeed, chip,
                title,
            ).build()
            is Phase.Failed -> b.applyFailed(phase).build()
        }
    }

    private fun NotificationCompat.Builder.applyConnecting(
        stage: Stage,
        protocolLabel: String,
    ): NotificationCompat.Builder {
        val step = stage.ordinal.coerceIn(0, STAGE_COUNT)
        val progress = step * SEGMENT_LEN
        val stageText = ctx.getString(stageLabel(stage))

        val style = NotificationCompat.ProgressStyle()
            .setProgressSegments(
                SEGMENT_COLORS.map { NotificationCompat.ProgressStyle.Segment(SEGMENT_LEN).setColor(it) },
            )
            .setProgressPoints(
                (1 until STAGE_COUNT).map { i ->
                    NotificationCompat.ProgressStyle.Point(i * SEGMENT_LEN).setColor(POINT_COLOR)
                },
            )
            .setProgress(progress)
            .setProgressTrackerIcon(IconCompat.createWithResource(ctx, R.drawable.ic_notif_rocket))
            .setProgressStartIcon(IconCompat.createWithResource(ctx, R.drawable.ic_notif_origin))
            .setProgressEndIcon(IconCompat.createWithResource(ctx, R.drawable.ic_notif_gateway))

        return setContentText(stageText)
            .setStyle(style)

            .setShortCriticalText(ctx.getString(R.string.notif_chip_connecting))
            .setSubText(protocolLabel.takeIf { it.isNotBlank() }?.let(BidiText::ltr))
            .setShowWhen(false)
            .addAction(R.drawable.ic_close, ctx.getString(R.string.action_cancel), stopTunnel())
    }

    private fun NotificationCompat.Builder.applyConnected(
        autoBadge: String?,
        protocolLabel: String,
        downBps: Long,
        upBps: Long,
        totalDown: Long,
        totalUp: Long,
        connectedAt: Long,
        showSpeed: Boolean,
        chip: NotifChip,
        configName: String,
    ): NotificationCompat.Builder {
        val (dl, dlU) = formatRate(downBps)
        val (ul, ulU) = formatRate(upBps)

        val speeds = BidiText.ltr(ctx.getString(R.string.notif_speeds, "$dl $dlU", "$ul $ulU"))

        val totals = ctx.getString(
            R.string.notif_totals,
            BidiText.ltr(formatBytes(totalDown)),
            BidiText.ltr(formatBytes(totalUp)),
        )
        val headline = if (showSpeed) speeds else ctx.getString(R.string.state_connected)

        val detail = if (showSpeed) "$speeds\n$totals" else totals

        fun views(): RemoteViews = RemoteViews(ctx.packageName, R.layout.notif_connected).apply {
            setTextViewText(R.id.notif_brand, ctx.getString(R.string.app_name))
            setTextViewText(
                R.id.notif_meta,
                listOf(
                    protocolLabel.takeIf { it.isNotBlank() },
                    connectedAt.takeIf { it > 0 }?.let { elapsedShort(it) },
                ).filterNotNull().joinToString(" · ").let(BidiText::ltr),
            )
            setTextViewText(R.id.notif_server, BidiText.auto(configName))
            setTextViewText(R.id.notif_state_pill, ctx.getString(R.string.state_connected))
            setViewVisibility(
                R.id.notif_auto_badge,
                if (autoBadge.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE,
            )
            setTextViewText(R.id.notif_auto_badge, autoBadge.orEmpty())
            setTextViewText(R.id.notif_down_rate, BidiText.ltr("$dl $dlU"))
            setTextViewText(R.id.notif_down_label, ctx.getString(R.string.notif_down))
            setTextViewText(R.id.notif_up_rate, BidiText.ltr("$ul $ulU"))
            setTextViewText(R.id.notif_up_label, ctx.getString(R.string.notif_up))
            setTextViewText(R.id.notif_session_totals, BidiText.auto(totals))
            setOnClickPendingIntent(R.id.notif_action_disconnect, stopTunnel())
            setTextViewText(R.id.notif_action_disconnect, ctx.getString(R.string.action_disconnect))
            setOnClickPendingIntent(R.id.notif_action_open, openApp())
        }

        return setContentText(headline)
            .setCustomContentView(views())
            .setCustomBigContentView(views())

            .setShortCriticalText(
                chipText(chip, showSpeed, "$dl$dlU", totalDown + totalUp, connectedAt, configName),
            )
            .setSubText(protocolLabel.takeIf { it.isNotBlank() }?.let(BidiText::ltr))

            .setShowWhen(connectedAt > 0)
            .setWhen(connectedAt.takeIf { it > 0 } ?: System.currentTimeMillis())
            .setUsesChronometer(connectedAt > 0)
            .addAction(R.drawable.ic_close, ctx.getString(R.string.action_disconnect), stopTunnel())
    }

    /** The flag emoji of a two-letter country code, or empty when unknown. */
    fun flagEmojiOf(code: String?): String = flagEmoji(code)

    /** The flag emoji of a two-letter country code, or empty when unknown. */
    private fun flagEmoji(code: String?): String {
        val c = code?.trim()?.uppercase() ?: return ""
        if (c.length != 2 || c.any { it !in 'A'..'Z' }) return ""
        val sb = StringBuilder()
        for (ch in c) sb.append(String(Character.toChars(0x1F1E6 + (ch - 'A'))))
        return sb.toString()
    }

    private fun chipText(
        chip: NotifChip,
        showSpeed: Boolean,
        downRate: String,
        totalBytes: Long,
        connectedAt: Long,
        configName: String,
    ): String = when (chip) {
        NotifChip.Speed ->
            if (showSpeed) BidiText.ltr("↓ $downRate").toString() else ctx.getString(R.string.notif_chip_on)
        NotifChip.ConfigName -> BidiText.auto(shorten(configName)).toString()
        NotifChip.Duration -> BidiText.ltr(elapsedShort(connectedAt)).toString()
        NotifChip.Total -> BidiText.ltr(formatBytes(totalBytes)).toString()
        NotifChip.None -> ctx.getString(R.string.notif_chip_on)
    }

    private fun shorten(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return ctx.getString(R.string.app_name)
        return if (trimmed.length <= CHIP_MAX_CHARS) trimmed
        else trimmed.take(CHIP_MAX_CHARS - 1).trimEnd() + "…"
    }

    private fun elapsedShort(connectedAt: Long): String {
        if (connectedAt <= 0) return "0s"
        val seconds = ((System.currentTimeMillis() - connectedAt) / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m"
            else -> "${seconds / 3600}h"
        }
    }

    private fun NotificationCompat.Builder.applyFailed(
        phase: Phase.Failed,
    ): NotificationCompat.Builder {
        val reason = BidiText.auto(phase.reason)
        val b = setContentTitle(ctx.getString(R.string.notif_title_disconnected))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setShortCriticalText(ctx.getString(R.string.notif_chip_off))
            .setShowWhen(false)
            .setColor(ERROR_COLOR)
        if (phase.retryable) {
            b.addAction(R.drawable.ic_sync, ctx.getString(R.string.action_retry), reconnect())
        }
        return b.addAction(R.drawable.ic_close, ctx.getString(R.string.action_disconnect), stopTunnel())
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            ctx, REQ_OPEN, ctx.packageManager.getLaunchIntentForPackage(ctx.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun stopTunnel(): PendingIntent =
        PendingIntent.getService(
            ctx, REQ_STOP,
            Intent(ctx, NrgVpnService::class.java)
                .putExtra(VpnManager.EXTRA_COMMAND, VpnManager.CMD_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun reconnect(): PendingIntent =
        PendingIntent.getActivity(
            ctx, REQ_RETRY,
            Intent(ctx, dev.valid7996.narcicgeteway.MainActivity::class.java).apply {
                action = ACTION_CONNECT
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun stageLabel(stage: Stage): Int = when (stage) {
        Stage.Preparing -> R.string.notif_stage_preparing
        Stage.Interface -> R.string.notif_stage_interface
        Stage.Engine -> R.string.notif_stage_engine
        Stage.Tunnel -> R.string.notif_stage_tunnel
        Stage.Connected -> R.string.state_connected
    }

    companion object {
        private const val CHIP_MAX_CHARS = 10

        const val CHANNEL_ID = "nrg_vpn"
        const val NOTIFICATION_ID = 1

        const val ACTION_CONNECT = "dev.valid7996.narcicgeteway.action.CONNECT"

        private const val REQ_OPEN = 0
        private const val REQ_STOP = 1
        private const val REQ_RETRY = 2

        private const val STAGE_COUNT = 4
        private const val SEGMENT_LEN = 25

        private const val BRAND = 0xFF0F9D6C.toInt()
        private const val ERROR_COLOR = 0xFFB3261E.toInt()
        private const val POINT_COLOR = 0xFFFFE066.toInt()
        private val SEGMENT_COLORS = listOf(
            0xFF0F9D6C.toInt(),
            0xFF3FD898.toInt(),
            0xFFA7F3D0.toInt(),
            0xFFFFE066.toInt(),
        )
    }
}
