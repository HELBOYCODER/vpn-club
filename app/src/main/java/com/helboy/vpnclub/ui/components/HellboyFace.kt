package com.helboy.vpnclub.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.helboy.vpnclub.corex.Phase
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * دکمه‌ی اتصال «چهره‌ی هلبوی» از ذرات — همان بافت گوی ذرات، با تصویر پروفایل گیت‌هاب
 * (assets/hellboy_particles.bin: ۵۰۰۰ ذره با رنگ واقعی، ساخته‌شده از عکس).
 *
 *  - خاموش: چهره کم‌نور و آرام نفس می‌کشد
 *  - در حال اتصال: ذرات بین «گوی» و «چهره» می‌چرخند (فکر کردن)
 *  - متصل: چهره کامل، **لبخند** می‌زند، چشم‌ها می‌درخشند، شعله‌ها با دانلود شعله‌ورتر می‌شوند
 *  - ناموفق: چهره **ناراحت** (گوشه‌های لب پایین)، تیره‌تر با لرزش
 */
@Composable
fun HellboyFace(
    phase: Phase,
    connected: Boolean,
    busy: Boolean,
    downBps: Long,
    upBps: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 290.dp,
) {
    val ctx = LocalContext.current
    val failed = phase == Phase.FAILED && !busy && !connected
    val data = remember { FaceData.load(ctx) }

    fun level(bps: Long): Float = if (bps <= 10_000) 0f
        else ((ln(bps.toDouble()) - ln(10_000.0)) / (ln(5_000_000.0) - ln(10_000.0))).toFloat().coerceIn(0f, 1f)

    // ۱ = چهره کامل، ۰ = گوی
    val form by animateFloatAsState(if (busy) 0.35f else 1f, tween(1100), label = "form")
    // +۱ لبخند، ۰ خنثی، −۱ ناراحت
    val mood by animateFloatAsState(when { connected -> 1f; failed -> -1f; else -> 0f },
        spring(dampingRatio = 0.5f, stiffness = 60f), label = "mood")
    val light by animateFloatAsState(when { connected -> 1f; busy -> 0.85f; failed -> 0.6f; else -> 0.62f }, tween(800), label = "light")
    val fire by animateFloatAsState(if (connected) 0.35f + 0.65f * level(downBps) else if (busy) 0.6f else 0.2f, tween(500), label = "fire")
    val spark by animateFloatAsState(if (connected) level(upBps) else 0f, tween(300), label = "spark")

    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L) t += ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
        }
    }

    val pos = remember { FloatArray(data.n * 2) }
    val sparks = remember { FloatArray(160) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND } }

    Box(
        modifier = modifier.size(size)
            .semantics { contentDescription = if (connected) "قطع اتصال" else "اتصال" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            if (data.n == 0) return@Canvas
            val cx = this.size.width / 2; val cy = this.size.height / 2
            val S = this.size.minDimension * 0.98f
            val glowCol = when { connected -> Color(0xFFFF7A1A); failed -> Color(0xFFB91C1C); else -> Color(0xFFFF4D2E) }

            // هاله‌ی گرم پشت چهره
            drawCircle(Brush.radialGradient(listOf(glowCol.copy(alpha = 0.18f + 0.22f * light * fire), Color.Transparent),
                center = Offset(cx, cy), radius = S * 0.62f), radius = S * 0.62f, center = Offset(cx, cy))

            // دوران گوی (برای حالت فکر کردن)
            val ry = t * 1.4f; val cyR = cos(ry); val syR = sin(ry)
            val shake = if (failed) 0.004f * sin(t * 38f) else 0f
            val breathe = 1f + 0.012f * sin(t * 1.5f)
            val swirl = 1f - form

            for (i in 0 until data.n) {
                var u = data.u[i]; var v = data.v[i]
                // ── حالت چهره: لب (لبخند/ناراحت) ──
                val mdx = (u - MOUTH_U) / MOUTH_W
                val mdy = (v - MOUTH_V) / MOUTH_H
                if (abs(mdx) < 1.6f && abs(mdy) < 1.8f) {
                    val fall = (1f - (mdy * mdy) / 3.3f).coerceAtLeast(0f) * (1f - (mdx * mdx) / 2.6f).coerceAtLeast(0f)
                    v += -mood * MOUTH_CURVE * (mdx * mdx - 0.35f) * fall
                }
                // شعله‌ها (بالای سر) با دانلود زنده‌تر
                if (data.flame[i]) {
                    val f = sin(t * 7f + u * 30f + i * 0.13f) * 0.5f + 0.5f
                    v -= (0.006f + 0.028f * fire) * f
                    u += 0.006f * fire * sin(t * 5f + i)
                }
                v += shake
                val fx = cx + (u - 0.5f) * S * breathe
                val fy = cy + (v - 0.5f) * S * breathe
                if (swirl > 0.001f) {
                    // نقطه‌ی متناظر روی گوی
                    val x0 = data.sx[i]; val y0 = data.sy[i]; val z0 = data.sz[i]
                    val x1 = x0 * cyR + z0 * syR; val z1 = -x0 * syR + z0 * cyR
                    val p = 2.6f / (2.6f + z1)
                    val r = S * 0.30f * (1f + 0.06f * sin(t * 5f + y0 * 6f))
                    val ox = cx + x1 * r * p; val oy = cy + y0 * r * p
                    pos[i * 2] = ox + (fx - ox) * form; pos[i * 2 + 1] = oy + (fy - oy) * form
                } else { pos[i * 2] = fx; pos[i * 2 + 1] = fy }
            }

            // جرقه‌ها از شعله‌ها (آپلود)
            val sparkN = (spark * 70).toInt().coerceAtMost(sparks.size / 2)
            for (s in 0 until sparkN) {
                val life = (t * (0.7f + (s % 5) * 0.15f) + s * 0.41f) % 1f
                val baseU = if (s % 2 == 0) 0.25f else 0.52f
                sparks[s * 2] = cx + (baseU - 0.5f + 0.08f * sin(s * 7.1f + t)) * S
                sparks[s * 2 + 1] = cy + (0.12f - 0.5f - life * 0.25f) * S
            }

            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                paint.strokeWidth = S / 175f
                for (b in 0 until data.bucketCount) {
                    val cnt = data.bucketLen[b]; if (cnt == 0) continue
                    paint.color = shade(data.bucketColor[b], light, mood)
                    nc.drawPoints(pos, data.bucketStart[b] * 2, cnt * 2, paint)
                }
                // خط لب درخشان تا حالت چهره خوانا باشد
                val ma = (0.25f + 0.75f * abs(mood)) * form
                if (ma > 0.05f) {
                    paint.color = Color(1f, 0.78f, 0.45f, ma * 0.9f).toArgbInt()
                    paint.strokeWidth = S / 130f
                    val pts = FloatArray(44)
                    for (k in 0 until 22) {
                        val dx = -1.15f + 2.3f * k / 21f
                        val u = MOUTH_U + dx * MOUTH_W
                        val v = MOUTH_V - mood * MOUTH_CURVE * (dx * dx - 0.35f) + 0.004f
                        pts[k * 2] = cx + (u - 0.5f) * S * breathe; pts[k * 2 + 1] = cy + (v - 0.5f) * S * breathe
                    }
                    nc.drawPoints(pts, paint)
                }
                if (sparkN > 0) {
                    paint.color = Color(0xFFFFC46B).toArgbInt(); paint.strokeWidth = S / 150f
                    nc.drawPoints(sparks, 0, sparkN * 2, paint)
                }
            }
            // چشم‌ها
            val eye = (0.25f + 0.75f * light) * form * (if (connected) 1f + 0.15f * sin(t * 3f) else 0.7f)
            for ((eu, ev) in EYES) {
                val c0 = Offset(cx + (eu - 0.5f) * S, cy + (ev - 0.5f) * S)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB020).copy(alpha = 0.75f * eye), Color.Transparent),
                    center = c0, radius = S * 0.035f), radius = S * 0.035f, center = c0)
            }
        }
    }
}

/** روشنایی کلی + تیره/سرد شدن در حالت ناراحت. */
private fun shade(argb: Int, light: Float, mood: Float): Int {
    val sad = (-mood).coerceAtLeast(0f)
    var r = ((argb shr 16) and 0xFF) / 255f; var g = ((argb shr 8) and 0xFF) / 255f; var b = (argb and 0xFF) / 255f
    val k = light * (1f - 0.25f * sad)
    r *= k; g *= k * (1f - 0.2f * sad); b *= k
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), 1f).toArgbInt()
}

private fun Color.toArgbInt(): Int =
    ((alpha * 255).toInt() shl 24) or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()

// موقعیت‌ها در مختصات تصویر (۰..۱) — از عکس اندازه‌گیری شده
private const val MOUTH_U = 0.446f
private const val MOUTH_V = 0.609f
private const val MOUTH_W = 0.085f
private const val MOUTH_H = 0.030f
private const val MOUTH_CURVE = 0.026f
private val EYES = listOf(0.337f to 0.450f, 0.472f to 0.428f)

/** ذرات چهره، گروه‌بندی‌شده بر اساس رنگ (برای رسم سریع با drawPoints). */
private class FaceData(
    val n: Int, val u: FloatArray, val v: FloatArray, val flame: BooleanArray,
    val sx: FloatArray, val sy: FloatArray, val sz: FloatArray,
    val bucketCount: Int, val bucketStart: IntArray, val bucketLen: IntArray, val bucketColor: IntArray,
) {
    companion object {
        fun load(ctx: android.content.Context): FaceData = try {
            val bytes = ctx.assets.open("hellboy_particles.bin").use { it.readBytes() }
            val n = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
            // کوانتیزه‌ی رنگ به ۴×۴×۴ سطل
            data class P(val u: Float, val v: Float, val r: Int, val g: Int, val b: Int, val q: Int)
            val ps = (0 until n).map { i ->
                val o = 2 + i * 5
                val r = bytes[o + 2].toInt() and 0xFF; val g = bytes[o + 3].toInt() and 0xFF; val b = bytes[o + 4].toInt() and 0xFF
                P((bytes[o].toInt() and 0xFF) / 255f, (bytes[o + 1].toInt() and 0xFF) / 255f, r, g, b,
                    (r / 64) * 16 + (g / 64) * 4 + (b / 64))
            }.sortedBy { it.q }
            val u = FloatArray(n) { ps[it].u }; val v = FloatArray(n) { ps[it].v }
            val flame = BooleanArray(n) { ps[it].v < 0.30f && ps[it].g > 110 && ps[it].r > 180 }
            val golden = PI * (3 - sqrt(5.0))
            val sx = FloatArray(n); val sy = FloatArray(n); val sz = FloatArray(n)
            for (i in 0 until n) {
                val y = 1 - (i * 7919 % n) / (n - 1f) * 2   // جای تصادفی ولی ثابت روی کره
                val rr = sqrt(1 - y * y); val th = golden * i
                sx[i] = (cos(th) * rr).toFloat(); sy[i] = y; sz[i] = (sin(th) * rr).toFloat()
            }
            val qs = ps.map { it.q }.distinct()
            val start = IntArray(qs.size); val len = IntArray(qs.size); val col = IntArray(qs.size)
            var idx = 0
            qs.forEachIndexed { k, q ->
                start[k] = idx
                val group = ps.filter { it.q == q }
                len[k] = group.size; idx += group.size
                val r = group.sumOf { it.r } / group.size; val g = group.sumOf { it.g } / group.size; val b = group.sumOf { it.b } / group.size
                col[k] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            FaceData(n, u, v, flame, sx, sy, sz, qs.size, start, len, col)
        } catch (_: Exception) {
            FaceData(0, FloatArray(0), FloatArray(0), BooleanArray(0), FloatArray(0), FloatArray(0), FloatArray(0),
                0, IntArray(0), IntArray(0), IntArray(0))
        }
    }
}
