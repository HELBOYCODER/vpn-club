package dev.valid7996.narcicgeteway.ui.skin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.valid7996.narcicgeteway.domain.model.ConnectionState

/**
 * نقاط اتصال پوسته‌ی Hellboy به موتور Narcic. هسته‌ی Narcic این‌ها را نمی‌شناسد؛ اپ Android
 * (که کد Android دارد) آن‌ها را فراهم می‌کند. null = ظاهر پیش‌فرض Narcic.
 */
typealias SkinHero = @Composable (state: ConnectionState, downBps: Long, upBps: Long, size: Dp, onTap: (() -> Unit)?) -> Unit
typealias SkinBackdrop = @Composable (modifier: Modifier) -> Unit

val LocalSkinHero = staticCompositionLocalOf<SkinHero?> { null }
val LocalSkinBackdrop = staticCompositionLocalOf<SkinBackdrop?> { null }
