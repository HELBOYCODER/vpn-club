package dev.valid7996.narcicgeteway.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valid7996.narcicgeteway.domain.model.AppLanguage
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.domain.model.ThemeMode
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.platform.BackHandler
import dev.valid7996.narcicgeteway.ui.theme.ThemeProfiles
import dev.valid7996.narcicgeteway.ui.theme.applyThemeProfile
import dev.valid7996.narcicgeteway.ui.theme.resetPersonalization
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnboardingFlow(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val totalSteps = 4

    BackHandler(enabled = step > 0) {
        step--
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 44.dp, bottom = 28.dp),
        ) {
            StepIndicator(currentStep = step, totalSteps = totalSteps)
            Spacer(Modifier.height(20.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(350)) { it } + fadeIn(tween(350))) togetherWith
                            (slideOutHorizontally(tween(350)) { -it } + fadeOut(tween(350)))
                    } else {
                        (slideInHorizontally(tween(350)) { -it } + fadeIn(tween(350))) togetherWith
                            (slideOutHorizontally(tween(350)) { it } + fadeOut(tween(350)))
                    }
                },
                label = "onboarding-step-transition",
                modifier = Modifier.weight(1f),
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (current) {
                        0 -> WelcomeStep()
                        1 -> LanguageStep(settings, onLanguage)
                        2 -> ThemeProfileStep(settings, onUpdate)
                        3 -> ThemeModeStep(settings, onUpdate)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.onb_back),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (step < totalSteps - 1) {
                    Button(
                        onClick = { step++ },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.onb_next),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                } else {
                    Button(
                        onClick = onFinish,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.onb_get_started),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { idx ->
            val isCurrent = idx == currentStep
            val isDone = idx < currentStep
            val width by animateDpAsState(
                targetValue = if (isCurrent) 32.dp else 12.dp,
                animationSpec = tween(300),
                label = "indicator-width",
            )
            val color by animateColorAsState(
                targetValue = when {
                    isCurrent -> MaterialTheme.colorScheme.primary
                    isDone -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                animationSpec = tween(300),
                label = "indicator-color",
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        ),
                    ),
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        ),
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_nrg_mark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(54.dp),
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = stringResource(Res.string.onb_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(Res.string.onb_welcome_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(32.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                WelcomeFeatureRow(
                    icon = "🌐",
                    title = "زبان برنامه (Language)",
                    subtitle = "پشتیبانی کامل از فارسی، انگلیسی و سایر زبان‌ها",
                )
                WelcomeFeatureRow(
                    icon = "🎨",
                    title = "انتخاب طرح ظاهری (Themes)",
                    subtitle = "چهار تم اختصاصی: پیش‌فرض، آزکابان، سیاه و سفید، و دخترونه صورتی",
                )
                WelcomeFeatureRow(
                    icon = "🌓",
                    title = "حالت تیره و روشن (Dark Mode)",
                    subtitle = "امکان هماهنگی خودکار با سیستم یا انتخاب حالت شب/روز",
                )
            }
        }
    }
}

@Composable
private fun WelcomeFeatureRow(icon: String, title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(icon, fontSize = 22.sp)
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class ThemePreviewInfo(
    val name: String,
    val accent: Color,
    val avatar: String,
    val desc: String,
)

@Composable
private fun LanguageStep(
    settings: AppSettings,
    onLanguage: (AppLanguage) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.onb_lang_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.onb_lang_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        val languages = listOf(
            Triple(AppLanguage.Persian, "فارسی" to "Persian", "🇮🇷"),
            Triple(AppLanguage.English, "English" to "انگلیسی", "🇬🇧"),
            Triple(AppLanguage.System, stringResource(Res.string.settings_language_system) to "System Default", "🌐"),
            Triple(AppLanguage.Chinese, "中文" to "Chinese", "🇨🇳"),
            Triple(AppLanguage.Russian, "Русский" to "Russian", "🇷🇺"),
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            languages.forEach { (lang, labels, flag) ->
                val (primaryLabel, subLabel) = labels
                val isSelected = settings.language == lang
                ChoiceCard(
                    title = primaryLabel,
                    subtitle = subLabel,
                    selected = isSelected,
                    leading = { Text(flag, fontSize = 20.sp) },
                    onClick = {
                        if (settings.language != lang) onLanguage(lang)
                    },
                )
            }
        }
    }
}

@Composable
private fun ThemeProfileStep(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.onb_preset_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.onb_preset_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // Live Interactive Theme Preview Card
        val activeProfileId = settings.themeProfileId
        val preview = when (activeProfileId) {
            "azkaban" -> ThemePreviewInfo("Azkaban (آزکابان)", Color(0xFF38BDF8), "🪐", "آبی سایبرنتیک نئونی و های‌تک")
            "noir" -> ThemePreviewInfo("Noir (سیاه و سفید)", Color(0xFFFFFFFF), "🌑", "مشکی مونوکروم دارک و مینیمال")
            "blossom" -> ThemePreviewInfo("Blossom (دخترونه صورتی)", Color(0xFFF472B6), "🌸", "صورتی و رز پاستلی دخترونه")
            else -> ThemePreviewInfo("Narcis (پیش‌فرض)", Color(0xFF0F9D6C), "🛡️", "سبز زمردی کلاسیک نارسیس")
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = if (settings.amoledBlack) Color(0xFF000000) else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, preview.accent.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(preview.accent.copy(alpha = 0.18f))
                            .border(1.5.dp, preview.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = preview.avatar, fontSize = 22.sp)
                    }
                    Column {
                        Text(
                            text = preview.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = preview.accent,
                        )
                        Text(
                            text = preview.desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(preview.accent)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "فعال",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (preview.accent == Color.White) Color.Black else Color.White,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 1. Default (پیش‌فرض)
            val isStockSelected = settings.themeProfileId == null || settings.themeProfileId == "narcis"
            ChoiceCard(
                title = stringResource(Res.string.onb_preset_stock),
                subtitle = "سبز زمردی کلاسیک نارسیس",
                selected = isStockSelected,
                accentColor = Color(0xFF0F9D6C),
                onClick = { onUpdate { it.resetPersonalization().copy(themeProfileId = "narcis", dynamicColor = false) } },
                trailing = {
                    SwatchDots(listOf(Color(0xFF0F9D6C), Color(0xFF3FD898), Color(0xFFFFCE54)))
                },
            )

            // 2, 3, 4. The 3 Ready ThemeProfiles (Azkaban, Noir, Blossom)
            ThemeProfiles.forEach { profile ->
                val isSelected = settings.themeProfileId == profile.id
                val (description, cardAccent) = when (profile.id) {
                    "azkaban" -> "آبی سایبرنتیک نئونی و های‌تک" to Color(0xFF38BDF8)
                    "noir" -> "مشکی مونوکروم دارک و مینیمال" to Color(0xFFFFFFFF)
                    "blossom" -> "صورتی و رز پاستلی دخترونه" to Color(0xFFF472B6)
                    else -> null to MaterialTheme.colorScheme.primary
                }
                ChoiceCard(
                    title = profile.name,
                    subtitle = description,
                    selected = isSelected,
                    accentColor = cardAccent,
                    onClick = { onUpdate { it.applyThemeProfile(profile) } },
                    trailing = {
                        SwatchDots(profile.swatches)
                    },
                )
            }
        }
    }
}

@Composable
private fun ThemeModeStep(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.onb_theme_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.onb_theme_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        val modes = listOf(
            Triple(
                ThemeMode.System,
                stringResource(Res.string.settings_theme_system),
                "هماهنگ با حالت تیره یا روشن تنظیمات گوشی شما",
            ) to ("📱" to MaterialTheme.colorScheme.primary),
            Triple(
                ThemeMode.Dark,
                stringResource(Res.string.settings_theme_dark),
                "رنگ‌های تیره برای راحتی چشم و مصرف باتری کمتر",
            ) to ("🌙" to Color(0xFF818CF8)),
            Triple(
                ThemeMode.Light,
                stringResource(Res.string.settings_theme_light),
                "رنگ‌های روشن برای دید واضح در نور روز",
            ) to ("☀️" to Color(0xFFF59E0B)),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            modes.forEach { (info, meta) ->
                val (mode, title, desc) = info
                val (icon, accent) = meta
                val isSelected = settings.themeMode == mode
                ChoiceCard(
                    title = title,
                    subtitle = desc,
                    selected = isSelected,
                    accentColor = accent,
                    leading = { Text(icon, fontSize = 22.sp) },
                    onClick = { onUpdate { it.copy(themeMode = mode) } },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "گزینه‌های تکمیلی نمایش (Display Options)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // AMOLED Black
            ChoiceCard(
                title = "مشکی عمیق امولد (AMOLED Black)",
                subtitle = "پس‌زمینه کاملاً سیاه خالص (#000000) سازگار با تمام ۴ قالب",
                selected = settings.amoledBlack,
                accentColor = Color.White,
                leading = { Text("🖤", fontSize = 20.sp) },
                onClick = { onUpdate { it.copy(amoledBlack = !it.amoledBlack) } },
            )

            // Dynamic Color
            ChoiceCard(
                title = "رنگ پویا سیستم (Dynamic Color)",
                subtitle = "استفاده از پالت رنگی پس‌زمینه گوشی (اندروید ۱۲ به بالا)",
                selected = settings.dynamicColor,
                accentColor = MaterialTheme.colorScheme.primary,
                leading = { Text("✨", fontSize = 20.sp) },
                onClick = { onUpdate { it.copy(dynamicColor = !it.dynamicColor) } },
            )
        }
    }
}

@Composable
private fun SwatchDots(colors: List<Color>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEach { swatch ->
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            )
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val activeAccent = accentColor ?: MaterialTheme.colorScheme.primary
    val container by animateColorAsState(
        if (selected) activeAccent.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "choice-container-color",
    )
    val borderColor by animateColorAsState(
        if (selected) activeAccent
        else Color.Transparent,
        label = "choice-border-color",
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(20.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) (if (activeAccent == Color.White) MaterialTheme.colorScheme.onSurface else activeAccent)
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) (if (activeAccent == Color.White) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else activeAccent.copy(alpha = 0.85f))
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (trailing != null) {
                trailing()
            } else if (selected) {
                Icon(
                    painter = painterResource(Res.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = activeAccent,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
