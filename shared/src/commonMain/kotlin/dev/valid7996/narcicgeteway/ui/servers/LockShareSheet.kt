@file:OptIn(
    kotlin.io.encoding.ExperimentalEncodingApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valid7996.narcicgeteway.crypto.NrgLink
import dev.valid7996.narcicgeteway.crypto.ZsxCrypto
import dev.valid7996.narcicgeteway.crypto.ZsxSealRequest
import dev.valid7996.narcicgeteway.data.config.NrgRelay
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.platform.currentTimeMillis
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.action_close
import dev.valid7996.narcicgeteway.shared.resources.action_copy
import dev.valid7996.narcicgeteway.shared.resources.action_share
import dev.valid7996.narcicgeteway.shared.resources.copied
import dev.valid7996.narcicgeteway.shared.resources.ic_check
import dev.valid7996.narcicgeteway.shared.resources.ic_content_paste
import dev.valid7996.narcicgeteway.shared.resources.ic_description
import dev.valid7996.narcicgeteway.shared.resources.ic_ios_share
import dev.valid7996.narcicgeteway.shared.resources.ic_key
import dev.valid7996.narcicgeteway.shared.resources.ic_lock
import dev.valid7996.narcicgeteway.shared.resources.ic_public
import dev.valid7996.narcicgeteway.shared.resources.ic_qr_code_2
import dev.valid7996.narcicgeteway.shared.resources.ic_schedule
import dev.valid7996.narcicgeteway.shared.resources.ic_sync
import dev.valid7996.narcicgeteway.shared.resources.qr_too_large
import dev.valid7996.narcicgeteway.ui.components.PageHeader
import dev.valid7996.narcicgeteway.ui.components.ProvidePageBack
import dev.valid7996.narcicgeteway.ui.components.QrDialog
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private fun formatExpiryTimestamp(epochMs: Long): String {
    val diffMs = epochMs - currentTimeMillis()
    if (diffMs <= 0) return "منقضی شده"
    val totalMinutes = diffMs / (60 * 1000L)
    val days = totalMinutes / (24 * 60)
    val remMinutesAfterDays = totalMinutes % (24 * 60)
    val hours = remMinutesAfterDays / 60
    val minutes = remMinutesAfterDays % 60
    return when {
        days > 0 && hours > 0 -> "$days روز و $hours ساعت دیگر"
        days > 0 -> "$days روز دیگر"
        hours > 0 && minutes > 0 -> "$hours ساعت و $minutes دقیقه دیگر"
        hours > 0 -> "$hours ساعت دیگر"
        else -> "$minutes دقیقه دیگر"
    }
}

@Composable
fun LockShareSheet(
    profile: VpnProfile,
    rawPayload: String,
    onDismiss: () -> Unit,
    relayEndpoint: String = "",
    relayToken: String = "",
    onRelayChanged: (endpoint: String, token: String) -> Unit = { _, _ -> },
) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    var configTitle by remember { mutableStateOf(profile.name) }
    var userNote by remember { mutableStateOf("") }
    var lockMode by remember { mutableIntStateOf(0) } // 0: By Device Code, 1: Public Lock (no device code)
    var buyerDeviceCode by remember { mutableStateOf("") }
    var selectedExpiryOption by remember { mutableIntStateOf(5) } // 0: None, 1: 5m, 2: 1h, 3: 6h, 4: 24h, 5: 3d, 6: 7d, 7: 30d, 8: custom
    var customDays by remember { mutableIntStateOf(0) }
    var customHours by remember { mutableIntStateOf(0) }
    var customMinutes by remember { mutableIntStateOf(5) }

    var useRelay by remember { mutableStateOf(relayEndpoint.isNotBlank()) }
    var relayUrlInput by remember { mutableStateOf(relayEndpoint) }
    var relayTokenInput by remember { mutableStateOf(relayToken) }
    var generating by remember { mutableStateOf(false) }

    var generatedNrgLink by remember { mutableStateOf<String?>(null) }
    var generatedSealedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var generatedExpiresAt by remember { mutableStateOf<Long?>(null) }
    var generatedDeviceCode by remember { mutableStateOf<String?>(null) }

    var linkCopied by remember { mutableStateOf(false) }
    var packageCopied by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.3f) else Color(0xFF3C3C43).copy(alpha = 0.3f)),
            )
        },
    ) {
        ProvidePageBack(onBack = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PageHeader(
                    title = "اشتراک امن و قفل اختصاصی (NRG)",
                    subtitle = "قفل کانفیگ بر روی کد دستگاه خریدار با زمان انقضا",
                )

                // ─── Settings Card ────────────────────────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color(0xFF242426) else Color(0xFFF2F2F7),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA)),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = configTitle,
                            onValueChange = { configTitle = it },
                            label = { Text("عنوان کانفیگ") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )

                        OutlinedTextField(
                            value = userNote,
                            onValueChange = { userNote = it },
                            label = { Text("یادداشت / نام خریدار (اختیاری)") },
                            placeholder = { Text("مثلاً: اختصاصی برای مهندس احمدی") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )

                        HorizontalDivider(color = if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA))

                        // Lock Mode Selection
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("نوع قفل‌گذاری کانفیگ:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = lockMode == 0,
                                    onClick = { lockMode = 0 },
                                    label = { Text("🔒 با کد دستگاه (اختصاصی)", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFFF9500).copy(alpha = 0.2f),
                                        selectedLabelColor = Color(0xFFFF9500),
                                    ),
                                )
                                FilterChip(
                                    selected = lockMode == 1,
                                    onClick = { lockMode = 1 },
                                    label = { Text("🌐 بدون کد دستگاه (عمومی)", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF007AFF).copy(alpha = 0.2f),
                                        selectedLabelColor = Color(0xFF007AFF),
                                    ),
                                )
                            }

                            if (lockMode == 0) {
                                // Device Lock Section
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_lock),
                                            contentDescription = null,
                                            tint = Color(0xFFFF9500),
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            "کد دستگاه خریدار (قفل اختصاصی):",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }

                                    OutlinedTextField(
                                        value = buyerDeviceCode,
                                        onValueChange = { buyerDeviceCode = it },
                                        label = { Text("کد دستگاه خریدار") },
                                        placeholder = { Text("مثلاً: NG-XXXX-XXXX") },
                                        supportingText = {
                                            Text(
                                                "خریدار کد دستگاه خود را از بخش «تنظیمات ← درباره ← کد دستگاه من» کپی کرده و ارسال می‌کند. کانفیگ رمزنگاری شده و منحصراً بر روی همان دستگاه باز می‌شود.",
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF9500),
                                        ),
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                val clip = platform.readClipboard()
                                                if (!clip.isNullOrBlank()) {
                                                    buyerDeviceCode = clip.trim()
                                                }
                                            }) {
                                                Icon(
                                                    painter = painterResource(Res.drawable.ic_content_paste),
                                                    contentDescription = "جای‌گذاری کد دستگاه",
                                                    tint = Color(0xFFFF9500),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                    )
                                }
                            } else {
                                // Public Lock Explanation
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDark) Color(0xFF1E2836) else Color(0xFFE8F2FD),
                                    border = BorderStroke(1.dp, Color(0xFF007AFF).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_public),
                                            contentDescription = null,
                                            tint = Color(0xFF007AFF),
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Text(
                                            "این کانفیگ بدون وابستگی به کد دستگاه رمزنگاری می‌شود. سورس، آدرس سرور و مشخصات کانفیگ کاملاً رمزنگاری و پنهان می‌ماند و می‌توانید آن را در کانال عمومی تلگرام قرار دهید. همه کاربران برنامه می‌توانند متصل شوند اما دسترسی به سورس آن نخواهند داشت.",
                                            fontSize = 11.sp,
                                            color = if (isDark) Color(0xFFCFE4FD) else Color(0xFF0A3E80),
                                            lineHeight = 16.sp,
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA))

                        // Expiration Section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painter = painterResource(Res.drawable.ic_schedule), contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("مدت اعتبار و تاریخ انقضا (Expiration):", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val expiryOptions = listOf(
                                    0 to "دائمی (بدون انقضا)",
                                    1 to "۵ دقیقه (تست سریع)",
                                    2 to "۱ ساعت (تست)",
                                    3 to "۶ ساعت",
                                    4 to "۲۴ ساعت (۱ روز)",
                                    5 to "۳ روز",
                                    6 to "۷ روز (یک هفته)",
                                    7 to "۳۰ روز (یک ماه)",
                                    8 to "سفارشی",
                                )
                                expiryOptions.forEach { (code, label) ->
                                    val isSelected = selectedExpiryOption == code
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedExpiryOption = code },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF007AFF).copy(alpha = 0.2f),
                                            selectedLabelColor = Color(0xFF007AFF),
                                        ),
                                    )
                                }
                            }

                            AnimatedVisibility(visible = selectedExpiryOption == 8) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("تنظیم دقیق روز، ساعت و دقیقه اعتبار:", fontSize = 11.sp, color = Color.Gray)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = if (customDays > 0) customDays.toString() else "",
                                            onValueChange = { customDays = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 },
                                            label = { Text("روز") },
                                            placeholder = { Text("0") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            singleLine = true,
                                        )
                                        OutlinedTextField(
                                            value = if (customHours > 0) customHours.toString() else "",
                                            onValueChange = { customHours = it.filter { c -> c.isDigit() }.toIntOrNull()?.coerceIn(0, 23) ?: 0 },
                                            label = { Text("ساعت") },
                                            placeholder = { Text("0") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            singleLine = true,
                                        )
                                        OutlinedTextField(
                                            value = if (customMinutes > 0) customMinutes.toString() else "",
                                            onValueChange = { customMinutes = it.filter { c -> c.isDigit() }.toIntOrNull()?.coerceIn(0, 59) ?: 0 },
                                            label = { Text("دقیقه") },
                                            placeholder = { Text("5") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            singleLine = true,
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA))

                        // Short link (relay)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("لینک کوتاه (از طریق سرور رله)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "فقط برای کوتاه‌شدن لینک (اختیاری): کانفیگ رمزشده روی سرور شما می‌ماند و در لینک فقط یک شناسه است.",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                )
                            }
                            Switch(checked = useRelay, onCheckedChange = { useRelay = it })
                        }

                        AnimatedVisibility(visible = useRelay) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = relayUrlInput,
                                    onValueChange = { relayUrlInput = it },
                                    label = { Text("آدرس سرور رله") },
                                    placeholder = { Text("https://nrg.example.workers.dev") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = relayTokenInput,
                                    onValueChange = { relayTokenInput = it },
                                    label = { Text("توکن آپلود (اختیاری)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                )
                            }
                        }

                        // Generate Button
                        Button(
                            onClick = {
                                val device = buyerDeviceCode.trim()
                                if (lockMode == 0 && device.isEmpty()) {
                                    platform.toast("لطفاً ابتدا کد دستگاه خریدار را وارد نمایید")
                                    return@Button
                                }

                                val now = currentTimeMillis()
                                val expiresAt: Long? = when (selectedExpiryOption) {
                                    0 -> null
                                    1 -> now + 5 * 60 * 1000L
                                    2 -> now + 60 * 60 * 1000L
                                    3 -> now + 6 * 3600 * 1000L
                                    4 -> now + 24 * 3600 * 1000L
                                    5 -> now + 3 * 24 * 3600 * 1000L
                                    6 -> now + 7 * 24 * 3600 * 1000L
                                    7 -> now + 30 * 24 * 3600 * 1000L
                                    8 -> {
                                        val totalMs = (customDays * 24 * 3600L + customHours * 3600L + customMinutes * 60L) * 1000L
                                        if (totalMs > 0) now + totalMs else null
                                    }
                                    else -> null
                                }

                                val sealReq = ZsxSealRequest(
                                    configPayload = rawPayload,
                                    nameEn = configTitle,
                                    nameFa = configTitle,
                                    note = userNote,
                                    expiresAt = expiresAt,
                                    password = if (lockMode == 0) device else null,
                                    deviceBound = (lockMode == 0),
                                )

                                if (generating) return@Button
                                generating = true
                                scope.launch {
                                    try {
                                        // Argon2 (64 MiB) + compression: keep it off the UI thread.
                                        val sealedBytes = withContext(Dispatchers.Default) { ZsxCrypto.seal(sealReq) }
                                        generatedSealedBytes = sealedBytes
                                        var link = NrgLink.encode(sealedBytes)
                                        if (useRelay) {
                                            val hostOk = NrgRelay.hostOf(relayUrlInput) != null
                                            if (!hostOk) {
                                                platform.toast("آدرس سرور رله نامعتبر است؛ لینک کامل ساخته شد")
                                            } else {
                                                val up = runCatching {
                                                    NrgRelay.upload(relayUrlInput, relayTokenInput, sealedBytes, expiresAt, null)
                                                }
                                                up.onSuccess {
                                                    link = NrgLink.relayLink(it)
                                                    onRelayChanged(relayUrlInput, relayTokenInput)
                                                }.onFailure {
                                                    platform.toast("آپلود روی سرور رله ناموفق بود (${it.message}) ؛ لینک کامل ساخته شد")
                                                }
                                            }
                                        }
                                        generatedNrgLink = link
                                        generatedExpiresAt = expiresAt
                                        generatedDeviceCode = if (lockMode == 0) device else null
                                        linkCopied = false
                                        packageCopied = false
                                        platform.toast(if (lockMode == 0) "کانفیگ اختصاصی با موفقیت قفل و تولید شد" else "کانفیگ قفل‌شده عمومی با موفقیت تولید شد")
                                    } catch (e: Throwable) {
                                        platform.toast("خطا در ساخت کانفیگ قفل‌شده: ${e.message}")
                                    } finally {
                                        generating = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500)),
                        ) {
                            Icon(painter = painterResource(Res.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (generating) "در حال ساخت…" else "تولید کانفیگ قفل‌شده (NRG)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                // ─── Result Output Card ────────────────────────────────────────────────────
                AnimatedVisibility(visible = generatedNrgLink != null) {
                    val nrgLink = generatedNrgLink ?: ""
                    val expiresAt = generatedExpiresAt
                    val lockedDevice = generatedDeviceCode ?: ""

                    val fullPackageText = buildString {
                        appendLine("🔒 کانفیگ اختصاصی و قفل‌شده Narcic Gateway")
                        appendLine("🏷️ نام کانفیگ: $configTitle")
                        appendLine("⏳ مدت اعتبار: ${if (expiresAt != null) formatExpiryTimestamp(expiresAt) else "دائمی (بدون انقضا)"}")
                        if (lockedDevice.isNotEmpty()) {
                            appendLine("📱 قفل اختصاصی برای کد دستگاه: $lockedDevice")
                            appendLine("⚠️ توجه: این کانفیگ اختصاصی بوده و منحصراً بر روی دستگاه ثبت‌شده شما فعال می‌شود و روی سایر دستگاه‌ها کار نخواهد کرد.")
                        } else {
                            appendLine("🌐 نوع قفل: عمومی (محافظت‌شده - مناسب برای کانال)")
                            appendLine("ℹ️ اطلاعات سرور و کانفیگ کاملاً پنهان بوده و برای تمامی کاربران برنامه قابل اتصال است.")
                        }
                        if (userNote.isNotBlank()) {
                            appendLine("📝 یادداشت: $userNote")
                        }
                        appendLine()
                        appendLine("🔗 لینک کانفیگ:")
                        appendLine(nrgLink)
                        appendLine()
                        appendLine("جهت فعال‌سازی، لینک بالا یا فایل کانفیگ را در برنامه Narcic Gateway وارد نمایید (کانفیگ به صورت خودکار شناسایی و اضافه خواهد شد).")
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) Color(0xFF1E261E) else Color(0xFFEDF8ED),
                        border = BorderStroke(1.dp, Color(0xFF34C759).copy(alpha = 0.4f)),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("✔ کانفیگ آماده اشتراک است", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF34C759))
                                Text(
                                    if (expiresAt != null) "انقضا: ${formatExpiryTimestamp(expiresAt)}" else "دائمی",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray,
                                )
                            }

                            Text(
                                "طول لینک: ${nrgLink.length} کاراکتر" + if (nrgLink.startsWith("nrg://r.")) " (لینک کوتاه)" else "",
                                fontSize = 11.sp,
                                color = Color.Gray,
                            )

                            // Link Box
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isDark) Color(0xFF161618) else Color(0xFFFFFFFF),
                                border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        nrgLink,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                        color = if (isDark) Color.LightGray else Color.DarkGray,
                                    )
                                    IconButton(
                                        onClick = {
                                            platform.copyToClipboard(nrgLink)
                                            linkCopied = true
                                            platform.toast("لینک کانفیگ کپی شد")
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(if (linkCopied) Res.drawable.ic_check else Res.drawable.ic_content_paste),
                                            contentDescription = "Copy Link",
                                            tint = Color(0xFFFF9500),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }

                            // Device Code Badge
                            if (lockedDevice.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDark) Color(0xFF28241D) else Color(0xFFFFF8EC),
                                    border = BorderStroke(1.dp, Color(0xFFFF9500).copy(alpha = 0.3f)),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_lock),
                                            contentDescription = null,
                                            tint = Color(0xFFFF9500),
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("قفل اختصاصی برای کد دستگاه:", fontSize = 11.sp, color = Color.Gray)
                                            Text(
                                                lockedDevice,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFF9500),
                                            )
                                        }
                                    }
                                }
                            }

                            // Share, File and QR Actions
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            platform.copyToClipboard(fullPackageText)
                                            packageCopied = true
                                            platform.toast("پیام کامل اشتراک در کلیپ‌بورد کپی شد")
                                        },
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                                    ) {
                                        Icon(painter = painterResource(if (packageCopied) Res.drawable.ic_check else Res.drawable.ic_content_paste), contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (packageCopied) "کپی شد" else "کپی پیام برای کاربر", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val safeName = (configTitle.trim().ifEmpty { "narcic_config" })
                                                .replace(Regex("""[^A-Za-z0-9_.\-\u0600-\u06FF]"""), "_")
                                            val rawBytes = generatedSealedBytes ?: runCatching { NrgLink.decode(nrgLink) }.getOrNull()
                                            if (rawBytes != null) {
                                                platform.shareFiles(listOf("$safeName.nrg" to rawBytes))
                                                platform.toast("فایل $safeName.nrg آماده ارسال شد")
                                            } else {
                                                platform.toast("خطا در ایجاد فایل")
                                            }
                                        },
                                        modifier = Modifier.height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(painter = painterResource(Res.drawable.ic_description), contentDescription = "Share .nrg file", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("فایل .nrg", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = { platform.shareText(fullPackageText) },
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(painter = painterResource(Res.drawable.ic_ios_share), contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("اشتراک متنی", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { showQrDialog = true },
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(painter = painterResource(Res.drawable.ic_qr_code_2), contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("کد QR", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQrDialog && generatedNrgLink != null) {
        val link = generatedNrgLink!!
        QrDialog(
            title = configTitle,
            text = link,
            copyLabel = stringResource(Res.string.action_copy),
            shareLabel = stringResource(Res.string.action_share),
            closeLabel = stringResource(Res.string.action_close),
            unsupportedLabel = stringResource(Res.string.qr_too_large),
            onCopy = {
                platform.copyToClipboard(link)
                platform.toast("لینک کپی شد")
            },
            onShare = { platform.shareText(link) },
            onDismiss = { showQrDialog = false },
        )
    }
}
