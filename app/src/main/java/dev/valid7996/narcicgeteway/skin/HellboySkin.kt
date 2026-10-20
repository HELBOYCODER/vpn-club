package dev.valid7996.narcicgeteway.skin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import dev.valid7996.narcicgeteway.R
import dev.valid7996.narcicgeteway.domain.model.ConnectionState
import dev.valid7996.narcicgeteway.ui.skin.BloodAndFire
import dev.valid7996.narcicgeteway.ui.skin.LocalSkinBackdrop
import dev.valid7996.narcicgeteway.ui.skin.LocalSkinHero

/**
 * پوسته‌ی Hellboy روی موتور Narcic: چهره‌ی هلبوی جای هیرو، دیوار بتنی و خون/آتش پس‌زمینه.
 * موتور و منطق (هسته‌ها، سرورها، تنظیمات) از Narcic است؛ فقط نمای بصری از Hellboy.
 */
@Composable
fun HellboySkinProviders(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSkinHero provides { state, downBps, upBps, size, onTap ->
            HellboyFace(
                isFailed = state == ConnectionState.Error,
                connected = state == ConnectionState.Connected,
                busy = state == ConnectionState.Connecting || state == ConnectionState.Reconnecting ||
                    state == ConnectionState.Disconnecting,
                downBps = downBps,
                upBps = upBps,
                onClick = { onTap?.invoke() },
                size = size,
            )
        },
        LocalSkinBackdrop provides { modifier ->
            val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Box(modifier.fillMaxSize().concreteWall(dark)) {
                BloodAndFire(modifier = Modifier.fillMaxSize(), dark = dark)
            }
        },
    ) { content() }
}

/** دیوار بتنی: بافت بی‌درز + سایه‌ی لبه‌ها (همان نورپردازی عکس هلبوی). */
@Composable
private fun Modifier.concreteWall(dark: Boolean): Modifier {
    val tex: ImageBitmap = ImageBitmap.imageResource(if (dark) R.drawable.bg_concrete_dark else R.drawable.bg_concrete_light)
    val brush = remember(tex) {
        ShaderBrush(ImageShader(tex, TileMode.Repeated, TileMode.Repeated))
    }
    val base = if (dark) Color(0xFF222929) else Color(0xFFA8AAA5)
    return this.drawBehind {
        drawRect(base)
        drawRect(brush)
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.Black.copy(alpha = 0.35f))))
        drawRect(Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.55f else 0.18f)),
            center = center, radius = size.maxDimension * 0.75f))
    }
}
