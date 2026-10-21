package com.helboy.vpnclub.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valid7996.narcicgeteway.R
import kotlin.math.sin

/** فونت تیتر به سبک هلبوی (Metal Mania — مجوز OFL). */
val HellboyFont = FontFamily(Font(R.font.hellboy_font))

/**
 * کلید روشن/خاموش با فونت هلبوی:
 *  - روشن  → نوشته **آتش می‌گیرد**: گرادیان شعله، زبانه‌های آتش و اخگر از روی حروف
 *  - خاموش → نوشته **یخ می‌زند**: آبی یخی، قندیل‌های آویزان، کریستال‌های برفک
 * گذار بین دو حالت نرم است (ذوب/انجماد).
 */
@Composable
fun FireIceToggle(
    text: String,
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    height: Dp = 96.dp,
    subtitle: String? = null,
) {
    // ۱ = آتش، ۰ = یخ
    val heat by animateFloatAsState(if (on) 1f else 0f, tween(900), label = "heat")
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L) t += ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
        }
    }
    val alpha = if (enabled) 1f else 0.45f
    val flicker = if (busy) 0.5f + 0.5f * sin(t * 9f) else 1f

    Column(
        modifier.fillMaxWidth()
            .semantics { contentDescription = "$text ${if (on) "روشن" else "خاموش"}" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                role = Role.Switch, onClick = onToggle),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            // ── افکت پشت/روی نوشته ──
            Canvas(Modifier.matchParentSize()) {
                val w = size.width; val h = size.height
                val cx = w / 2; val textW = w * 0.62f
                val left = cx - textW / 2; val top = h * 0.22f; val bottom = h * 0.78f

                // هاله: گرم یا سرد
                val glow = lerpC(Color(0xFF7DD3FC), Color(0xFFFF6A1A), heat)
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.30f * alpha * flicker), Color.Transparent),
                    center = Offset(cx, h / 2), radius = textW * 0.7f), radius = textW * 0.7f, center = Offset(cx, h / 2))

                // آتش: زبانه‌ها روی لبه‌ی بالای حروف
                if (heat > 0.02f) {
                    for (i in 0 until 16) {
                        val fx = left + textW * (i + 0.5f) / 16f
                        val f = 0.5f + 0.5f * sin(t * (7f + i % 3) + i * 1.7f)
                        val fh = h * (0.18f + 0.22f * f) * heat
                        val fw = textW / 16f * 1.3f
                        val path = Path().apply {
                            moveTo(fx - fw / 2, top + h * 0.08f)
                            quadraticBezierTo(fx - fw * 0.1f, top - fh * 0.5f, fx + fw * 0.15f * sin(t * 5f + i), top - fh)
                            quadraticBezierTo(fx + fw * 0.2f, top - fh * 0.4f, fx + fw / 2, top + h * 0.08f)
                            close()
                        }
                        drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFFF1A8).copy(alpha = 0f),
                            Color(0xFFFFB020).copy(alpha = 0.8f * heat * alpha), Color(0xFFFF4A12).copy(alpha = 0.9f * heat * alpha)),
                            startY = top - fh, endY = top + h * 0.08f))
                    }
                    // اخگرها
                    for (i in 0 until 22) {
                        val s = ((i * 0.6180339f) % 1f)
                        val life = (t * (0.5f + s * 0.6f) + s * 3f) % 1f
                        val x = left + textW * s + 6f * sin(t * 3f + i)
                        val y = top - life * h * 0.55f
                        drawCircle(Color(0xFFFFC46B).copy(alpha = (1f - life) * heat * alpha), radius = 2.2f * (1f - life) + 0.8f, center = Offset(x, y))
                    }
                }

                // یخ: قندیل‌ها زیر حروف + برفک
                val cold = 1f - heat
                if (cold > 0.02f) {
                    for (i in 0 until 13) {
                        val ix = left + textW * (i + 0.5f) / 13f
                        val ih = h * (0.10f + 0.12f * ((i * 37 % 7) / 7f)) * cold
                        val iw = textW / 13f * 0.45f
                        val path = Path().apply {
                            moveTo(ix - iw / 2, bottom - h * 0.04f)
                            lineTo(ix + iw / 2, bottom - h * 0.04f)
                            lineTo(ix, bottom - h * 0.04f + ih)
                            close()
                        }
                        drawPath(path, Brush.verticalGradient(listOf(Color(0xFFE0F7FF).copy(alpha = 0.9f * cold * alpha),
                            Color(0xFF7DD3FC).copy(alpha = 0.25f * cold * alpha)), startY = bottom - h * 0.04f, endY = bottom + ih))
                    }
                    for (i in 0 until 18) {
                        val s = ((i * 0.7548776f) % 1f); val s2 = ((i * 0.5698403f) % 1f)
                        val tw = 0.5f + 0.5f * sin(t * 2.5f + i * 2.1f)
                        val c = Offset(left - 10f + (textW + 20f) * s, top + (bottom - top) * s2)
                        val r = (2.5f + 3f * tw) * cold
                        val col = Color.White.copy(alpha = 0.75f * tw * cold * alpha)
                        drawLine(col, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 1.2f)
                        drawLine(col, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 1.2f)
                    }
                }
            }

            // ── نوشته با فونت هلبوی ──
            val fire = listOf(Color(0xFFFFE27A), Color(0xFFFF8A1F), Color(0xFFD62A0A))
            val ice = listOf(Color(0xFFFFFFFF), Color(0xFFBDEBFF), Color(0xFF4BA8D8))
            val cols = fire.indices.map { lerpC(ice[it], fire[it], heat).copy(alpha = alpha) }
            Text(
                text,
                style = TextStyle(
                    fontFamily = HellboyFont,
                    fontSize = (height.value * 0.52f).sp,
                    brush = Brush.verticalGradient(cols),
                    shadow = Shadow(lerpC(Color(0xFF0EA5E9), Color(0xFFFF3D00), heat).copy(alpha = 0.8f * alpha * flicker),
                        Offset(0f, 0f), blurRadius = 18f),
                ),
            )
        }
        subtitle?.let {
            Text(it, color = lerpC(Color(0xFF93C5FD), Color(0xFFFDBA74), heat).copy(alpha = alpha), fontSize = 12.sp)
        }
    }
}

private fun lerpC(a: Color, b: Color, f: Float) = Color(
    red = a.red + (b.red - a.red) * f, green = a.green + (b.green - a.green) * f,
    blue = a.blue + (b.blue - a.blue) * f, alpha = a.alpha + (b.alpha - a.alpha) * f)
