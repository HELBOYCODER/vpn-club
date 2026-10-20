package dev.valid7996.narcicgeteway.ui.home

import dev.valid7996.narcicgeteway.ui.theme.Personalization
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.data.net.NetworkInfo
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.ic_close
import dev.valid7996.narcicgeteway.shared.resources.ic_map
import dev.valid7996.narcicgeteway.ui.components.FlagBadge
import org.jetbrains.compose.resources.painterResource
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private class CountryGeometry(
    val iso: String,
    val center: Offset,
    val rings: List<FloatArray>,
)

private object WorldMapCache {
    var countries: Map<String, CountryGeometry>? = null

    fun parse(raw: String): Map<String, CountryGeometry> {
        countries?.let { return it }
        val result = HashMap<String, CountryGeometry>(190)
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split('|')
            if (parts.size < 2) continue
            val iso = parts[0].uppercase()
            val cParts = parts[1].split(',')
            if (cParts.size < 2) continue
            val cx = cParts[0].toFloatOrNull() ?: continue
            val cy = cParts[1].toFloatOrNull() ?: continue
            val rings = ArrayList<FloatArray>(parts.size - 2)
            for (i in 2 until parts.size) {
                val ringStr = parts[i]
                if (ringStr.isEmpty()) continue
                val nums = ringStr.split(',')
                if (nums.size >= 6) {
                    val arr = FloatArray(nums.size)
                    var valid = true
                    for (j in nums.indices) {
                        val v = nums[j].toFloatOrNull()
                        if (v == null) {
                            valid = false
                            break
                        }
                        arr[j] = v
                    }
                    if (valid) rings.add(arr)
                }
            }
            result[iso] = CountryGeometry(iso = iso, center = Offset(cx, cy), rings = rings)
        }
        countries = result
        return result
    }
}

private val PERSIAN_COUNTRY_NAMES = mapOf(
    "IR" to "ایران",
    "DE" to "آلمان",
    "NL" to "هلند",
    "TR" to "ترکیه",
    "FI" to "فنلاند",
    "SE" to "سوئد",
    "NO" to "نروژ",
    "DK" to "دانمارک",
    "GB" to "انگلیس",
    "UK" to "انگلیس",
    "FR" to "فرانسه",
    "CH" to "سوئیس",
    "AT" to "اتریش",
    "IT" to "ایتالیا",
    "ES" to "اسپانیا",
    "PL" to "لهستان",
    "CZ" to "چک",
    "RO" to "رومانی",
    "BG" to "بلغارستان",
    "HU" to "مجارستان",
    "UA" to "اوکراین",
    "RU" to "روسیه",
    "AE" to "امارات",
    "SA" to "عربستان",
    "QA" to "قطر",
    "OM" to "عمان",
    "KW" to "کویت",
    "BH" to "بحرین",
    "IQ" to "عراق",
    "AM" to "ارمنستان",
    "GE" to "گرجستان",
    "AZ" to "آذربایجان",
    "US" to "آمریکا",
    "CA" to "کانادا",
    "BR" to "برزیل",
    "SG" to "سنگاپور",
    "HK" to "هنگ‌کنگ",
    "JP" to "ژاپن",
    "KR" to "کره جنوبی",
    "IN" to "هند",
    "AU" to "استرالیا",
)

@Composable
fun ConnectionMapTriggerButton(
    expanded: Boolean,
    destCountryCode: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    personalization: Personalization = Personalization.Default,
    isAzkaban: Boolean = false,
    isNoir: Boolean = false,
    isBlossom: Boolean = false,
) {
    val bgBrush = when {
        personalization.amoledBlack -> Brush.horizontalGradient(listOf(Color(0xFF000000), Color(0xFF000000)))
        isBlossom -> Brush.horizontalGradient(listOf(Color(0xFF260D1A), Color(0xFF381226)))
        isNoir -> Brush.horizontalGradient(listOf(Color(0xFF141416), Color(0xFF1E1E22)))
        isAzkaban -> Brush.horizontalGradient(listOf(Color(0xFF0C192E), Color(0xFF07101F)))
        else -> Brush.horizontalGradient(listOf(Color(0xFF0B7D5A), Color(0xFF064834)))
    }

    val accent = when {
        personalization.dynamicColor -> MaterialTheme.colorScheme.primary
        isBlossom -> Color(0xFFF472B6)
        isNoir -> Color(0xFFFFFFFF)
        isAzkaban -> Color(0xFF38BDF8)
        else -> Color(0xFFFFE066)
    }
    val accentSub = when {
        personalization.dynamicColor -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        isBlossom -> Color(0xFFFFD1DC)
        isNoir -> Color(0xFF94A3B8)
        isAzkaban -> Color(0xFF94A3B8)
        else -> Color(0xFFFFE066).copy(alpha = 0.88f)
    }
    val borderColor = when {
        personalization.dynamicColor -> MaterialTheme.colorScheme.primary.copy(alpha = if (personalization.amoledBlack) 0.65f else 0.50f)
        isBlossom -> Color(0xFFF472B6).copy(alpha = if (personalization.amoledBlack) 0.65f else 0.50f)
        isNoir -> Color(0xFFFFFFFF).copy(alpha = if (personalization.amoledBlack) 0.45f else 0.30f)
        isAzkaban -> Color(0xFF38BDF8).copy(alpha = if (personalization.amoledBlack) 0.65f else 0.50f)
        personalization.amoledBlack -> Color(0xFF10B981).copy(alpha = 0.60f)
        else -> Color(0xFFFFE066).copy(alpha = 0.55f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgBrush)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_map),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column {
                    Text(
                        text = if (expanded) "بستن نقشه مسیر اتصال" else "نمایش نقشه مسیر اتصال زنده",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "مسیر کشور مبدأ به مقصد + IPv4 و نام کانفیگ",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentSub,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FlagBadge(countryCode = "IR", size = 18.dp)
                Text(
                    text = "⟷",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                FlagBadge(countryCode = destCountryCode, size = 18.dp)
            }
        }
    }
}

@Composable
fun ConnectionRouteMapCard(
    info: NetworkInfo,
    activeConfigName: String?,
    fallbackCountryCode: String?,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    personalization: Personalization = Personalization.Default,
    isAzkaban: Boolean = false,
    isNoir: Boolean = false,
    isBlossom: Boolean = false,
) {
    var mapData by remember { mutableStateOf(WorldMapCache.countries) }
    var smartZoom by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (mapData == null) {
            val loaded = runCatching {
                val bytes = Res.readBytes("files/world_countries.txt")
                WorldMapCache.parse(bytes.decodeToString())
            }.getOrNull()
            if (loaded != null) mapData = loaded
        }
    }

    val originCode = "IR"
    val destCode = (info.countryCode?.takeIf { it.isNotBlank() } ?: fallbackCountryCode ?: "DE").uppercase()

    val originGeo = mapData?.get(originCode)
    val destGeo = mapData?.get(destCode)

    val originPt = originGeo?.center ?: Offset(642.8f, 150.9f) // Tehran
    val destPt = destGeo?.center ?: Offset(524.1f, 110.8f)   // Frankfurt fallback

    // Camera viewBox in 1000x500 world space
    val targetCam = remember(originPt, destPt, smartZoom) {
        if (!smartZoom) {
            floatArrayOf(110f, 35f, 790f, 360f)
        } else {
            val minX = min(originPt.x, destPt.x)
            val maxX = max(originPt.x, destPt.x)
            val minY = min(originPt.y, destPt.y)
            val maxY = max(originPt.y, destPt.y)
            val spanX = max(135f, (maxX - minX) * 1.95f)
            val spanY = max(72f, (maxY - minY) * 2.15f)
            val w = max(spanX, spanY * 1.95f).coerceAtMost(840f)
            val h = w / 1.95f
            val cx = (minX + maxX) / 2f
            val cy = (minY + maxY) / 2f - h * 0.06f
            val vx = (cx - w / 2f).coerceIn(0f, 1000f - w)
            val vy = (cy - h / 2f).coerceIn(0f, 500f - h)
            floatArrayOf(vx, vy, w, h)
        }
    }

    val camX by animateFloatAsState(targetCam[0], tween(650, easing = FastOutSlowInEasing), label = "camX")
    val camY by animateFloatAsState(targetCam[1], tween(650, easing = FastOutSlowInEasing), label = "camY")
    val camW by animateFloatAsState(targetCam[2], tween(650, easing = FastOutSlowInEasing), label = "camW")
    val camH by animateFloatAsState(targetCam[3], tween(650, easing = FastOutSlowInEasing), label = "camH")

    val infinite = rememberInfiniteTransition(label = "route-map-anim")
    val dashPhase by infinite.animateFloat(
        initialValue = 36f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "dash-phase",
    )
    val packetT by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2100, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "packet-t",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "pin-pulse",
    )

    val destCountryLabel = PERSIAN_COUNTRY_NAMES[destCode]
        ?: info.country?.takeIf { it.isNotBlank() }
        ?: destCode

    val shape = RoundedCornerShape(24.dp)
    val cardBg = when {
        personalization.amoledBlack -> Color(0xFF000000)
        isBlossom -> Color(0xFF1F0B18)
        isNoir -> Color(0xFF101012)
        isAzkaban -> Color(0xFF060E1A)
        else -> Color(0xFF071915)
    }
    val cardBorder = when {
        personalization.dynamicColor -> MaterialTheme.colorScheme.primary.copy(alpha = if (personalization.amoledBlack) 0.65f else 0.45f)
        isBlossom -> Color(0xFFF472B6).copy(alpha = if (personalization.amoledBlack) 0.65f else 0.50f)
        isNoir -> Color(0xFFFFFFFF).copy(alpha = if (personalization.amoledBlack) 0.45f else 0.30f)
        isAzkaban -> Color(0xFF38BDF8).copy(alpha = if (personalization.amoledBlack) 0.60f else 0.45f)
        personalization.amoledBlack -> Color(0xFF10B981).copy(alpha = 0.55f)
        else -> Color(0xFF10B981).copy(alpha = 0.45f)
    }
    val primaryAccent = when {
        personalization.dynamicColor -> MaterialTheme.colorScheme.primary
        isBlossom -> Color(0xFFFF85A1)
        isNoir -> Color(0xFFFFFFFF)
        isAzkaban -> Color(0xFF38BDF8)
        else -> Color(0xFFFFE066)
    }
    val secondaryAccent = when {
        personalization.dynamicColor -> MaterialTheme.colorScheme.tertiary
        isBlossom -> Color(0xFFF472B6)
        isNoir -> Color(0xFFE2E8F0)
        isAzkaban -> Color(0xFFBAE6FD)
        else -> Color(0xFF6EE7B7)
    }

    Surface(
        shape = shape,
        color = cardBg,
        modifier = modifier
            .fillMaxWidth()
            .border(1.2.dp, cardBorder, shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Top Header Bar: Origin (Iran) ---> Destination Country + Smart Zoom Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Origin: Iran
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlagBadge(countryCode = originCode, size = 22.dp)
                    Column {
                        Text(
                            text = "مبدأ: ایران (IR)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryAccent,
                        )
                        Text(
                            text = "Tehran",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                }

                // Center Ping & Tunnel Arrow
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    info.pingMs?.let { ms ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(cardBorder.copy(alpha = 0.20f))
                                .border(1.dp, cardBorder, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "$ms ms ⚡",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = secondaryAccent,
                            )
                        }
                    }
                    Text(
                        text = "─── ✈️ ───►",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = primaryAccent,
                    )
                }

                // Destination: Connected Server Country
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "مقصد: $destCountryLabel ($destCode)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = secondaryAccent,
                            maxLines = 1,
                        )
                        Text(
                            text = info.city?.takeIf { it.isNotBlank() } ?: (info.country ?: destCode),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                        )
                    }
                    FlagBadge(countryCode = destCode, size = 22.dp)
                }
            }

            // 2. Natural World Map Canvas with Smart Zoom & Country Highlighting
            val mapCanvasBg = when {
                personalization.amoledBlack -> Color(0xFF000000)
                isBlossom -> Color(0xFF0F040A)
                isNoir -> Color(0xFF08080A)
                isAzkaban -> Color(0xFF03070E)
                else -> Color(0xFF05120F)
            }
            val gridColor = when {
                personalization.dynamicColor -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                isBlossom -> Color(0xFFF472B6).copy(alpha = 0.10f)
                isNoir -> Color(0xFFFFFFFF).copy(alpha = 0.08f)
                isAzkaban -> Color(0xFF38BDF8).copy(alpha = 0.08f)
                else -> Color(0xFF10B981).copy(alpha = 0.08f)
            }
            val baseFill = when {
                personalization.amoledBlack -> Color(0xFF080808)
                isBlossom -> Color(0xFF2B0E1E)
                isNoir -> Color(0xFF1A1A1E)
                isAzkaban -> Color(0xFF0C192E)
                else -> Color(0xFF12352C)
            }
            val baseStroke = when {
                personalization.amoledBlack -> Color(0xFF181818)
                isBlossom -> Color(0xFF4E1A38)
                isNoir -> Color(0xFF2E2E34)
                isAzkaban -> Color(0xFF1E3A5F)
                else -> Color(0xFF216653)
            }
            val originFill = when {
                personalization.dynamicColor -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                isBlossom -> Color(0xFF9D174D)
                isNoir -> Color(0xFF3F3F46)
                isAzkaban -> Color(0xFF0284C7)
                else -> Color(0xFFD99B00)
            }
            val originStroke = when {
                personalization.dynamicColor -> MaterialTheme.colorScheme.secondary
                isBlossom -> Color(0xFFFF85A1)
                isNoir -> Color(0xFFFFFFFF)
                isAzkaban -> Color(0xFF38BDF8)
                else -> Color(0xFFFFE066)
            }
            val destFill = when {
                personalization.dynamicColor -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                isBlossom -> Color(0xFFDB2777)
                isNoir -> Color(0xFF52525B)
                isAzkaban -> Color(0xFF0369A1)
                else -> Color(0xFF10B981)
            }
            val destStroke = when {
                personalization.dynamicColor -> MaterialTheme.colorScheme.primary
                isBlossom -> Color(0xFFF472B6)
                isNoir -> Color(0xFFE2E8F0)
                isAzkaban -> Color(0xFFBAE6FD)
                else -> Color(0xFFA7F3D0)
            }
            val arcBrush = Brush.linearGradient(
                colors = when {
                    personalization.dynamicColor -> listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)
                    isBlossom -> listOf(Color(0xFFFF85A1), Color(0xFFF472B6))
                    isNoir -> listOf(Color(0xFFFFFFFF), Color(0xFF94A3B8))
                    isAzkaban -> listOf(Color(0xFFBAE6FD), Color(0xFF38BDF8))
                    else -> listOf(Color(0xFFFFE066), Color(0xFF10B981))
                },
                start = originPt,
                end = destPt,
            )
            val packetColor = when {
                personalization.dynamicColor -> MaterialTheme.colorScheme.primary
                isBlossom -> Color(0xFFFFD1DC)
                isNoir -> Color(0xFFFFFFFF)
                isAzkaban -> Color(0xFFBAE6FD)
                else -> Color(0xFFFFE066)
            }
            val originPinColor = originStroke
            val destPinColor = when {
                personalization.dynamicColor -> MaterialTheme.colorScheme.primary
                isBlossom -> Color(0xFFF472B6)
                isNoir -> Color(0xFFFFFFFF)
                isAzkaban -> Color(0xFF38BDF8)
                else -> Color(0xFF10B981)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(195.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(mapCanvasBg)
                    .border(1.dp, cardBorder.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
            ) {
                val reusablePath = remember { Path() }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    clipRect {
                        val scaleX = size.width / camW
                        val scaleY = size.height / camH
                        val s = min(scaleX, scaleY)
                        val offsetX = (size.width - camW * s) / 2f - camX * s
                        val offsetY = (size.height - camH * s) / 2f - camY * s

                        withTransform({
                            translate(left = offsetX, top = offsetY)
                            scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
                        }) {
                            // Subtle lat/lon grid lines
                            val gridStroke = (1.0f / s).coerceAtLeast(0.25f)
                            for (gy in listOf(125f, 250f, 375f)) {
                                drawLine(gridColor, Offset(0f, gy), Offset(1000f, gy), strokeWidth = gridStroke)
                            }
                            for (gx in listOf(250f, 500f, 750f)) {
                                drawLine(gridColor, Offset(gx, 0f), Offset(gx, 500f), strokeWidth = gridStroke)
                            }

                            // Draw all real country borders
                            val countries = mapData
                            if (countries != null) {
                                val borderW = (0.85f / s).coerceAtLeast(0.3f)
                                val highlightW = (1.5f / s).coerceAtLeast(0.6f)

                                for ((iso, geom) in countries) {
                                    if (geom.rings.isEmpty()) continue
                                    reusablePath.reset()
                                    for (ring in geom.rings) {
                                        reusablePath.moveTo(ring[0], ring[1])
                                        var idx = 2
                                        while (idx < ring.size) {
                                            reusablePath.lineTo(ring[idx], ring[idx + 1])
                                            idx += 2
                                        }
                                        reusablePath.close()
                                    }

                                    when (iso) {
                                        originCode -> {
                                            drawPath(reusablePath, color = originFill)
                                            drawPath(
                                                reusablePath,
                                                color = originStroke,
                                                style = Stroke(width = highlightW, join = StrokeJoin.Round),
                                            )
                                        }
                                        destCode -> {
                                            drawPath(reusablePath, color = destFill)
                                            drawPath(
                                                reusablePath,
                                                color = destStroke,
                                                style = Stroke(width = highlightW, join = StrokeJoin.Round),
                                            )
                                        }
                                        else -> {
                                            drawPath(reusablePath, color = baseFill)
                                            drawPath(
                                                reusablePath,
                                                color = baseStroke,
                                                style = Stroke(width = borderW, join = StrokeJoin.Round),
                                            )
                                        }
                                    }
                                }
                            }

                            // Curved Tunnel Arc from Iran (originPt) to Server Country (destPt)
                            val dist = hypot(destPt.x - originPt.x, destPt.y - originPt.y)
                            val ctrlX = (originPt.x + destPt.x) / 2f
                            val ctrlY = min(originPt.y, destPt.y) - max(18f, dist * 0.28f)

                            reusablePath.reset()
                            reusablePath.moveTo(originPt.x, originPt.y)
                            reusablePath.quadraticTo(ctrlX, ctrlY, destPt.x, destPt.y)

                            // Soft outer glow
                            drawPath(
                                path = reusablePath,
                                brush = arcBrush,
                                alpha = 0.28f,
                                style = Stroke(width = 6.5f / s, cap = StrokeCap.Round),
                            )

                            // Animated dashed tunnel line
                            drawPath(
                                path = reusablePath,
                                brush = arcBrush,
                                style = Stroke(
                                    width = 2.6f / s,
                                    cap = StrokeCap.Round,
                                    pathEffect = PathEffect.dashPathEffect(
                                        intervals = floatArrayOf(8f / s, 6f / s),
                                        phase = dashPhase / s,
                                    ),
                                ),
                            )

                            // Traveling data packet along the quadratic Bezier curve
                            val u = 1f - packetT
                            val px = u * u * originPt.x + 2f * u * packetT * ctrlX + packetT * packetT * destPt.x
                            val py = u * u * originPt.y + 2f * u * packetT * ctrlY + packetT * packetT * destPt.y
                            drawCircle(
                                color = Color.White.copy(alpha = 0.4f),
                                radius = 6.5f / s,
                                center = Offset(px, py),
                            )
                            drawCircle(
                                color = packetColor,
                                radius = 3.2f / s,
                                center = Offset(px, py),
                            )

                            // Origin Pin (Iran)
                            val pulseR = (4f + pulse * 11f) / s
                            drawCircle(
                                color = originPinColor.copy(alpha = (1f - pulse) * 0.75f),
                                radius = pulseR,
                                center = originPt,
                                style = Stroke(width = 1.6f / s),
                            )
                            drawCircle(color = mapCanvasBg, radius = 4.6f / s, center = originPt)
                            drawCircle(color = originPinColor, radius = 3.4f / s, center = originPt)

                            // Destination Pin (Connected Country)
                            drawCircle(
                                color = destPinColor.copy(alpha = (1f - pulse) * 0.85f),
                                radius = pulseR * 1.1f,
                                center = destPt,
                                style = Stroke(width = 1.6f / s),
                            )
                            drawCircle(color = Color.White, radius = 4.8f / s, center = destPt)
                            drawCircle(color = destPinColor, radius = 3.5f / s, center = destPt)
                        }
                    }
                }

                // Floating Zoom Toggle Button (Top-Left of Map)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .border(1.dp, cardBorder.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .clickable { smartZoom = !smartZoom }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = if (smartZoom) "🔍 زوم هوشمند" else "🌍 نمای کل جهان",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = secondaryAccent,
                        )
                    }
                }

                if (onClose != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            // 3. Bottom Bar: IPv4 + Active Config Name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.50f))
                    .border(1.dp, cardBorder.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // IPv4 Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "آدرس IPv4 متصل:",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryAccent.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = info.ipv4?.takeIf { it.isNotBlank() } ?: "در حال دریافت...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .background(Color.White.copy(alpha = 0.16f)),
                )

                Spacer(Modifier.width(12.dp))

                // Active Config Name Column
                Column(modifier = Modifier.weight(1.15f)) {
                    Text(
                        text = "نام کانفیگ فعال:",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryAccent.copy(alpha = 0.88f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FlagBadge(countryCode = destCode, size = 15.dp)
                        Text(
                            text = activeConfigName?.takeIf { it.isNotBlank() } ?: "سرور متصل",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
