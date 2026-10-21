package com.helboy.vpnclub.ui.components

import android.graphics.Paint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.helboy.vpnclub.corex.Phase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * دکمه‌ی اتصال به شکل «گوی ذرات» (الهام از Voice Orb در shipnotes-components، پیاده‌سازی
 * مستقل با Canvas بومی اندروید — بدون WebView/WebGL).
 *
 * به‌جای میکروفون، «صدا»ی گوی ترافیک واقعی VPN است:
 *  - bass  = سرعت دانلود → کل کره بزرگ و کوچک می‌شود (تپش)
 *  - mids  = موج‌هایی که روی سطح کره می‌دوند (شدت با ترافیک کل)
 *  - highs = سرعت آپلود → جرقه‌هایی که از لبه‌ی کره پرت می‌شوند
 *
 * حالت‌ها: خاموش (تنفس آرام، آبی) · در حال اتصال (چرخش و گردباد، فیروزه‌ای/کهربایی)
 *          · متصل (سبز-فیروزه‌ای، واکنش به ترافیک) · ناموفق (قرمز، لرزش).
 */
@Composable
fun ParticleOrb(
    phase: Phase,
    connected: Boolean,
    busy: Boolean,
    downBps: Long,
    upBps: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
    particles: Int = 2600,
) {
    val failed = phase == Phase.FAILED && !busy && !connected

    // رنگ‌ها نرم بین حالت‌ها جابه‌جا می‌شوند
    val core by animateColorAsState(
        when { connected -> Color(0xFF2DD4BF); failed -> Color(0xFFF87171); busy -> Color(0xFF22D3EE); else -> Color(0xFF60A5FA) },
        tween(700), label = "core")
    val accent by animateColorAsState(
        when { connected -> Color(0xFF34D399); failed -> Color(0xFFFB923C); busy -> Color(0xFFFBBF24); else -> Color(0xFF818CF8) },
        tween(700), label = "accent")

    // «صدا»: لگاریتم سرعت → ۰..۱ (۱۰KB/s تقریباً صفر، ~۵MB/s حداکثر)
    fun level(bps: Long): Float = if (bps <= 10_000) 0f
        else ((ln(bps.toDouble()) - ln(10_000.0)) / (ln(5_000_000.0) - ln(10_000.0))).toFloat().coerceIn(0f, 1f)
    val bass by animateFloatAsState(if (connected) level(downBps) else 0f, tween(450), label = "bass")
    val highs by animateFloatAsState(if (connected) level(upBps) else 0f, tween(300), label = "highs")
    val energy by animateFloatAsState(
        when { busy -> 0.85f; connected -> 0.35f + 0.65f * maxOf(bass, highs); failed -> 0.5f; else -> 0.15f },
        tween(600), label = "energy")

    // نقاط کره (توزیع فیبوناچی — یکنواخت)
    val base = remember(particles) {
        val golden = PI * (3 - sqrt(5.0))
        FloatArray(particles * 3).also { a ->
            for (i in 0 until particles) {
                val y = 1 - (i / (particles - 1f)) * 2
                val r = sqrt(1 - y * y)
                val th = golden * i
                a[i * 3] = (cos(th) * r).toFloat(); a[i * 3 + 1] = y; a[i * 3 + 2] = (sin(th) * r).toFloat()
            }
        }
    }
    val buckets = remember(particles) { Array(4) { FloatArray(particles * 2) } }
    val counts = remember { IntArray(4) }
    val sparks = remember(particles) { FloatArray(400) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND } }

    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) t += ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
            }
        }
    }

    Box(
        modifier = modifier.size(size).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val cx = this.size.width / 2; val cy = this.size.height / 2
            val baseR = this.size.minDimension * 0.33f

            // هاله‌ی پشت گوی
            drawCircle(
                Brush.radialGradient(listOf(core.copy(alpha = 0.28f + 0.25f * energy), Color.Transparent),
                    center = Offset(cx, cy), radius = baseR * (1.9f + 0.4f * bass)),
                radius = baseR * (1.9f + 0.4f * bass), center = Offset(cx, cy))

            // پارامترهای حرکت بر اساس حالت
            val spin = if (busy) 1.6f else 0.25f + 0.5f * energy
            val ry = t * spin; val rx = 0.35f + 0.15f * sin(t * 0.4f)
            val cyR = cos(ry); val syR = sin(ry); val cxR = cos(rx); val sxR = sin(rx)
            val breathe = 1f + 0.03f * sin(t * 1.6f)
            val pulse = 1f + 0.18f * bass * (0.6f + 0.4f * sin(t * 9f))          // bass
            val waveAmp = 0.04f + 0.12f * energy                                 // mids
            val shake = if (failed) 0.02f * sin(t * 40f) else 0f
            val R = baseR * breathe * pulse

            counts.fill(0)
            val n = base.size / 3
            for (i in 0 until n) {
                var x = base[i * 3]; var y = base[i * 3 + 1]; var z = base[i * 3 + 2]
                // موج‌های روی سطح (mids) + گردباد در حالت اتصال
                val w = sin(y * 6f + t * 3.2f) * cos(x * 5f - t * 2.1f)
                var d = 1f + waveAmp * w + shake
                if (busy) d += 0.06f * sin(t * 6f + (x + z) * 8f)
                x *= d; y *= d; z *= d
                // دوران
                val x1 = x * cyR + z * syR; val z1 = -x * syR + z * cyR
                val y2 = y * cxR - z1 * sxR; val z2 = y * sxR + z1 * cxR
                // پرسپکتیو
                val p = 2.6f / (2.6f + z2)
                val sx = cx + x1 * R * p; val sy = cy + y2 * R * p
                val b = ((z2 + 1.2f) / 2.4f * 4).toInt().coerceIn(0, 3)   // عمق → ۴ لایه‌ی روشنایی
                val k = counts[b]
                buckets[b][k] = sx; buckets[b][k + 1] = sy
                counts[b] = k + 2
            }

            // جرقه‌ها (highs): ذراتی که از لبه پرتاب می‌شوند
            val sparkN = (highs * 90 + if (busy) 20 else 0).toInt().coerceAtMost(sparks.size / 2)
            for (s in 0 until sparkN) {
                val seed = s * 12.9898f
                val ang = (seed * 43758.5453f) % (2 * PI.toFloat())
                val life = ((t * (0.6f + (s % 7) * 0.12f) + s * 0.37f) % 1f)
                val dist = R * (1.02f + life * (0.35f + 0.5f * highs))
                sparks[s * 2] = cx + cos(ang + t * 0.2f) * dist
                sparks[s * 2 + 1] = cy + sin(ang + t * 0.2f) * dist
            }

            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                val alphas = floatArrayOf(0.18f, 0.38f, 0.65f, 1f)
                val widths = floatArrayOf(2.2f, 2.8f, 3.4f, 4.2f)
                for (b in 0..3) {
                    if (counts[b] == 0) continue
                    val col = lerp(core, accent, b / 3f)
                    paint.color = col.copy(alpha = alphas[b]).toArgb()
                    paint.strokeWidth = widths[b] * density / 2.6f
                    nc.drawPoints(buckets[b], 0, counts[b], paint)
                }
                if (sparkN > 0) {
                    paint.color = accent.copy(alpha = 0.9f).toArgb()
                    paint.strokeWidth = 3.2f * density / 2.6f
                    nc.drawPoints(sparks, 0, sparkN * 2, paint)
                }
            }
        }
        // آیکن روشن/خاموش در مرکز — روی گوی شناور
        val iconAlpha = 0.55f + 0.45f * (1 - exp(-3f * energy))
        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = if (connected) "قطع" else "اتصال",
            tint = Color.White.copy(alpha = iconAlpha), modifier = Modifier.size(size * 0.17f))
    }
}

private fun lerp(a: Color, b: Color, f: Float) = Color(
    red = a.red + (b.red - a.red) * f, green = a.green + (b.green - a.green) * f,
    blue = a.blue + (b.blue - a.blue) * f, alpha = 1f)
