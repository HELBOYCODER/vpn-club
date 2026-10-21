package com.helboy.vpnclub.ui.proto.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.components.StatTile
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.formatBytes
import com.helboy.vpnclub.ui.formatDuration
import com.helboy.vpnclub.ui.formatSpeed
import com.helboy.vpnclub.ui.proto.ProtoError
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.proto.engine.MonitorLogic.pushed
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.NeonIndigo
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.core.VpnManager
import dev.valid7996.narcicgeteway.domain.model.ConnectionState
import dev.valid7996.narcicgeteway.ui.platform.LiveStats
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Live view of the tunnel and of the phone carrying it. Works while disconnected: the traffic cards then show
 * an idle state and only the device cards move. The sampling loops live in LaunchedEffects, so they stop
 * when the page is left.
 */
@Composable
fun LiveMonitorPage(onBack: () -> Unit) {
    val platform = LocalPlatform.current
    val status by VpnManager.status.collectAsState()
    val kind by VpnManager.activeKind.collectAsState()
    var stats by remember { mutableStateOf(LiveStats()) }
    var crypto by remember { mutableStateOf("") }
    var down by remember { mutableStateOf(listOf<Float>()) }
    var up by remember { mutableStateOf(listOf<Float>()) }
    var cpu by remember { mutableStateOf(listOf<Float>()) }

    LaunchedEffect(Unit) {
        crypto = withContext(Dispatchers.Default) { runCatching { platform.cryptoAcceleration() }.getOrDefault("") }
    }
    // A new connection starts a new graph.
    LaunchedEffect(status.sessionId) {
        down = emptyList()
        up = emptyList()
    }
    // Traffic: once a second, whatever the engine last reported.
    LaunchedEffect(Unit) {
        while (true) {
            val st = VpnManager.status.value
            val live = st.state == ConnectionState.Connected
            down = down.pushed(if (live) st.downloadBps.toFloat() else 0f)
            up = up.pushed(if (live) st.uploadBps.toFloat() else 0f)
            delay(1_000)
        }
    }
    // Device: CPU, memory, battery and heat, read off the main thread.
    LaunchedEffect(Unit) {
        while (true) {
            val s = withContext(Dispatchers.Default) { runCatching { platform.liveStats() }.getOrNull() }
            if (s != null) {
                stats = s
                cpu = cpu.pushed(MonitorLogic.deviceShare(s) ?: 0f)
            }
            delay(2_000)
        }
    }

    val state = status.state
    val active = state.isActive

    ProtoPage(title = "مانیتور زنده", onBack = onBack) {
        ProtoSection(title = "اتصال") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(
                    MonitorLogic.stateLabel(state),
                    when {
                        state == ConnectionState.Connected -> StatusGreen
                        state == ConnectionState.Error -> StatusRed
                        active || state.isTransitioning -> StatusAmber
                        else -> TextTertiary
                    },
                    Modifier.testTag("proto_monitor_state"),
                )
                status.serverName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = TextPrimary, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                }
            }
            if (state == ConnectionState.Error) {
                ProtoError(status.error?.takeIf { it.isNotBlank() } ?: "اتصال با خطا قطع شد. دوباره امتحان کنید.")
            } else if (!active) {
                Text(
                    "اتصالی برقرار نیست؛ سرعت و حجم پس از اتصال نمایش داده می‌شود. کارت‌های دستگاه پایین همچنان زنده‌اند.",
                    color = TextSecondary, fontSize = 12.sp, modifier = Modifier.testTag("proto_monitor_idle"),
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("دانلود", formatSpeed(if (state == ConnectionState.Connected) status.downloadBps else 0L),
                "جمع: " + formatBytes(status.totalDownload), NeonCyan, Modifier.weight(1f))
            StatTile("آپلود", formatSpeed(if (state == ConnectionState.Connected) status.uploadBps else 0L),
                "جمع: " + formatBytes(status.totalUpload), NeonIndigo, Modifier.weight(1f))
        }
        ProtoSection(title = "نمودار سرعت", hint = "نود ثانیه‌ی اخیر؛ آبی دانلود، بنفش آپلود") {
            DualGraph(down, up, NeonCyan, NeonIndigo, Modifier.fillMaxWidth().height(90.dp).testTag("proto_monitor_graph"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("بیشینه: " + formatSpeed(MonitorLogic.ceiling(down, up, floor = 0f).toLong()), color = TextTertiary, fontSize = 11.sp)
                Text("مدت نشست: " + formatDuration(status.durationSeconds * 1000L), color = TextSecondary, fontSize = 11.sp)
            }
        }

        val pressure = MonitorLogic.pressure(stats)
        ProtoSection(title = "دستگاه", hint = "فشار: ${pressure.title} - ${pressure.text}") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("پردازنده", color = TextSecondary, fontSize = 12.sp)
                    Text(MonitorLogic.percent(MonitorLogic.deviceShare(stats)), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Graph(cpu, NeonCyan, Modifier.weight(1.4f).height(48.dp), top = 100f)
            }
            InfoRow("حافظه‌ی برنامه", MonitorLogic.memory(stats.memoryBytes))
            InfoRow("باتری", stats.batteryPercent?.let { "$it%".fa() + if (stats.charging) " (در حال شارژ)" else "" } ?: "—")
            InfoRow("دمای باتری", MonitorLogic.temperature(stats.tempC))
            MonitorLogic.thermalLabel(stats.thermalStatus)?.let { InfoRow("وضعیت حرارتی", it) }
        }

        ProtoSection(title = "هسته") {
            InfoRow("هسته‌ی در حال اجرا", MonitorLogic.engineName(kind, active))
            VpnManager.activeSocksPort?.takeIf { active }?.let { InfoRow("پروکسی محلی", "127.0.0.1:$it".fa()) }
            stats.threads?.let { InfoRow("تردهای فعال", it.fa()) }
            stats.processUptimeMs?.let { InfoRow("زمان کارکرد پردازش", formatDuration(it)) }
            val fast = MonitorLogic.cryptoAccelerated(crypto)
            InfoRow(
                "شتاب رمزنگاری",
                when {
                    crypto.isBlank() -> "—"
                    fast -> "سخت‌افزاری"
                    else -> "نرم‌افزاری (کندتر)"
                },
            )
            if (crypto.isNotBlank()) {
                Text(crypto, color = TextTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Graph(values: List<Float>, color: Color, modifier: Modifier, top: Float? = null) {
    val grid = TextTertiary.copy(alpha = 0.25f)
    Canvas(modifier) {
        for (f in listOf(0.33f, 0.66f)) drawLine(grid, Offset(0f, size.height * f), Offset(size.width, size.height * f), strokeWidth = 1f)
        if (values.size < 2) return@Canvas
        val ceiling = top ?: MonitorLogic.ceiling(values)
        val step = size.width / (values.size - 1).toFloat()
        fun y(v: Float) = size.height - (v / ceiling).coerceIn(0f, 1f) * size.height
        val line = Path().apply {
            moveTo(0f, y(values.first()))
            values.forEachIndexed { i, v -> if (i > 0) lineTo(i * step, y(v)) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.02f))))
        drawPath(line, color, style = Stroke(width = 2.5f * density, cap = StrokeCap.Round))
    }
}

/** Two series on one shared scale, so download and upload can be compared by eye. */
@Composable
private fun DualGraph(a: List<Float>, b: List<Float>, colorA: Color, colorB: Color, modifier: Modifier) {
    val grid = TextTertiary.copy(alpha = 0.25f)
    Canvas(modifier) {
        for (f in listOf(0.33f, 0.66f)) drawLine(grid, Offset(0f, size.height * f), Offset(size.width, size.height * f), strokeWidth = 1f)
        val ceiling = MonitorLogic.ceiling(a, b, floor = 50_000f)
        fun draw(values: List<Float>, color: Color) {
            if (values.size < 2) return
            val step = size.width / (values.size - 1).toFloat()
            fun y(v: Float) = size.height - (v / ceiling).coerceIn(0f, 1f) * size.height
            val line = Path().apply {
                moveTo(0f, y(values.first()))
                values.forEachIndexed { i, v -> if (i > 0) lineTo(i * step, y(v)) }
            }
            drawPath(line, color, style = Stroke(width = 2.5f * density, cap = StrokeCap.Round))
        }
        draw(a, colorA)
        draw(b, colorB)
    }
}
