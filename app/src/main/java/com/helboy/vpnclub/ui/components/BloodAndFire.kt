package com.helboy.vpnclub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.sin

/**
 * لایه‌ی تزئینی روی دیوار بتنی:
 *  - قطره‌های خون که آرام از لبه‌ی بالا روی دیوار سُر می‌خورند (با لکه‌های پاشیده‌ی ثابت)
 *  - اخگرهای آتش که از پایین بالا می‌روند و یک درخشش گرم در کف صفحه
 * فقط Canvas — بدون تصویر اضافه. شدت در تم روز کمتر است.
 */
@Composable
fun BloodAndFire(modifier: Modifier = Modifier, dark: Boolean = true) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L) t += ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
        }
    }
    // قطره‌ها: موقعیت افقی، سرعت، طول بیشینه، پهنا (ثابت، شبه‌تصادفی)
    val drips = remember {
        List(9) { i ->
            val r = { k: Int -> frac(sin((i * 12.9898 + k * 78.233)) * 43758.5453) }
            Drip(x = 0.04f + 0.92f * r(1), speed = 0.012f + 0.02f * r(2), maxLen = 0.10f + 0.28f * r(3),
                width = 3.5f + 6f * r(4), phase = r(5))
        }
    }
    val splats = remember {
        List(14) { i ->
            val r = { k: Int -> frac(sin((i * 39.346 + k * 11.135)) * 24634.6345) }
            Splat(0.02f + 0.96f * r(1), 0.005f + 0.05f * r(2), 3f + 11f * r(3), r(4))
        }
    }
    val intensity = if (dark) 1f else 0.55f

    Canvas(modifier) {
        val w = size.width; val h = size.height
        val bloodDark = Color(0xFF3D0303); val blood = Color(0xFF8E0B0B); val bloodHi = Color(0xFFD23A2A)

        // ── لکه‌های خون پاشیده بالای دیوار ──
        for (s in splats) {
            val c = Offset(s.x * w, s.y * h)
            drawCircle(blood.copy(alpha = 0.75f * intensity), radius = s.r, center = c)
            // ترشح‌های ریز اطراف
            for (k in 0 until 4) {
                val a = (s.seed * 6.28f) + k * 1.7f
                drawCircle(blood.copy(alpha = 0.6f * intensity), radius = s.r * 0.28f,
                    center = Offset(c.x + kotlin.math.cos(a) * s.r * 2.1f, c.y + sin(a) * s.r * 1.6f))
            }
        }

        // ── قطره‌های در حال چکیدن ──
        for (d in drips) {
            // چرخه: رشد آرام → جدا شدن قطره و سقوط → از نو
            val cycle = ((t * d.speed * 6f) + d.phase) % 1f
            val grow = (cycle / 0.75f).coerceAtMost(1f)
            val len = d.maxLen * h * easeOut(grow)
            val x = d.x * w
            val top = 0f
            val path = Path().apply {
                moveTo(x - d.width / 2, top)
                lineTo(x + d.width / 2, top)
                lineTo(x + d.width * 0.32f, top + len)
                lineTo(x - d.width * 0.32f, top + len)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(bloodDark.copy(alpha = 0.95f * intensity),
                blood.copy(alpha = 0.95f * intensity)), startY = top, endY = top + len + 1f))
            // سر قطره (گرد و براق)
            val headR = d.width * 0.62f
            drawCircle(blood.copy(alpha = intensity), radius = headR, center = Offset(x, top + len))
            drawCircle(bloodHi.copy(alpha = 0.55f * intensity), radius = headR * 0.32f,
                center = Offset(x - headR * 0.3f, top + len - headR * 0.3f))
            // قطره‌ی جداشده که سقوط می‌کند
            if (cycle > 0.75f) {
                val fall = (cycle - 0.75f) / 0.25f
                val y = top + len + fall * fall * h * 0.6f
                drawCircle(blood.copy(alpha = intensity * (1f - fall * 0.6f)), radius = headR * 0.8f, center = Offset(x, y))
            }
        }

        // ── درخشش آتش در کف ──
        drawRect(Brush.verticalGradient(
            listOf(Color.Transparent, Color(0xFFFF5A1F).copy(alpha = (0.10f + 0.04f * sin(t * 2.3f)) * intensity),
                Color(0xFFFF2A00).copy(alpha = 0.22f * intensity)),
            startY = h * 0.72f, endY = h), topLeft = Offset(0f, h * 0.72f), size = Size(w, h * 0.28f))

        // ── اخگرها: از پایین بالا می‌روند، می‌لرزند و خاموش می‌شوند ──
        for (i in 0 until 46) {
            val seed = frac(sin(i * 91.7) * 1000.0)
            val life = ((t * (0.10f + 0.12f * seed)) + seed * 7f) % 1f
            val x = w * (frac(seed * 13.0 + 0.1) + 0.03f * sin(t * 2f + i))
            val y = h * (1.02f - life * (0.45f + 0.35f * seed))
            val fade = (1f - life) * (if (life < 0.1f) life * 10f else 1f)
            val r = 1.4f + 3.2f * seed * (1f - life)
            val col = if (seed > 0.6f) Color(0xFFFFD27A) else if (seed > 0.3f) Color(0xFFFF8A2B) else Color(0xFFFF4E1A)
            drawCircle(col.copy(alpha = 0.35f * fade * intensity), radius = r * 3.2f, center = Offset(x, y))
            drawCircle(col.copy(alpha = 0.9f * fade * intensity), radius = r, center = Offset(x, y))
        }

        // شعله‌های کوتاه در لبه‌ی پایین
        for (i in 0 until 12) {
            val fx = w * (i + 0.5f) / 12f
            val fh = h * (0.025f + 0.02f * (0.5f + 0.5f * sin(t * 6f + i * 1.9f)))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFB347).copy(alpha = 0.0f),
                Color(0xFFFF6A1A).copy(alpha = 0.35f * intensity)), startY = h - fh, endY = h),
                topLeft = Offset(fx - w / 30f, h - fh), size = Size(w / 15f, fh), cornerRadius = CornerRadius(w / 30f))
        }
    }
}

private data class Drip(val x: Float, val speed: Float, val maxLen: Float, val width: Float, val phase: Float)
private data class Splat(val x: Float, val y: Float, val r: Float, val seed: Float)

private fun frac(v: Double): Float = (v - kotlin.math.floor(v)).toFloat()
private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x)
@Suppress("unused") private const val TAU = (2 * PI).toFloat()
