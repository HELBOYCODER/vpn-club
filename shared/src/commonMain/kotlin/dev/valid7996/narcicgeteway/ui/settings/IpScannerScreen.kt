package dev.valid7996.narcicgeteway.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import dev.valid7996.narcicgeteway.data.tools.ScanResult
import dev.valid7996.narcicgeteway.data.tools.ScannerEngine
import dev.valid7996.narcicgeteway.data.tools.Status
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.ui.components.PageHeader
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun IpScannerSheet(
    settings: AppSettings? = null,
    onUpdateSettings: (((AppSettings) -> AppSettings) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
        dev.valid7996.narcicgeteway.ui.components.ProvidePageBack(onBack = onDismiss) {
            IpScannerScreen(
                settings = settings,
                onUpdateSettings = onUpdateSettings,
                contentPadding = PaddingValues(bottom = 28.dp),
            )
        }
    }
}

@Composable
fun IpScannerScreen(
    settings: AppSettings? = null,
    onUpdateSettings: (((AppSettings) -> AppSettings) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    val platform = LocalPlatform.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val scope = rememberCoroutineScope()

    var selectedCdn by remember { mutableStateOf("Cloudflare") }
    var customRange by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("443") }
    var checkCert by remember { mutableStateOf(false) }

    var isScanning by remember { mutableStateOf(false) }
    var scannedCount by remember { mutableIntStateOf(0) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    val results = remember { mutableStateListOf<ScanResult>() }

    fun sortByGreenFirst() {
        if (results.isEmpty()) {
            platform.toast("نتیجه‌ای برای مرتب‌سازی وجود ندارد")
            return
        }
        val sorted = results.distinctBy { it.ip }.sortedWith(
            compareBy<ScanResult> {
                when (it.status) {
                    Status.GREEN -> 0
                    Status.YELLOW -> 1
                    Status.RED -> 2
                }
            }.thenBy {
                if (it.latency <= 0) 9999 else it.latency
            }
        )
        results.clear()
        results.addAll(sorted)
        platform.toast("مرتب‌سازی: سبزها با کمترین پینگ در بالا")
    }

    fun stopScan() {
        isScanning = false
        ScannerEngine.stop()
        scanJob?.cancel()
        scanJob = null
    }

    DisposableEffect(Unit) {
        onDispose {
            stopScan()
        }
    }

    fun startScan() {
        if (isScanning) return
        results.clear()
        scannedCount = 0

        val port = portText.toIntOrNull() ?: 443
        val customTrimmed = customRange.trim()
        val rangesToScan: List<String> = if (customTrimmed.isNotEmpty()) {
            customTrimmed.split("\n", ",", ";", " ")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } else {
            ScannerEngine.getDefaultRangesForCdn(selectedCdn)
                .shuffled()
                .take(4)
        }

        if (rangesToScan.isEmpty()) {
            platform.toast("رنجی برای اسکن یافت نشد")
            return
        }

        isScanning = true
        platform.toast("اسکن آغاز شد: $selectedCdn | ${rangesToScan.size} رنج")

        scanJob = scope.launch {
            val resultChannel = Channel<ScanResult>(capacity = 128)

            val consumer = launch(Dispatchers.Main.immediate) {
                for (result in resultChannel) {
                    if (!isScanning) break
                    val existingIndex = results.indexOfFirst { it.ip == result.ip }
                    if (existingIndex >= 0) {
                        results[existingIndex] = result
                    } else {
                        if (result.status == Status.GREEN) {
                            results.add(0, result)
                        } else {
                            results.add(result)
                        }
                    }
                    scannedCount++
                }
            }

            try {
                ScannerEngine.scan(
                    ranges = rangesToScan,
                    domain = domain.trim(),
                    port = port,
                    ipPerRange = 150,
                    threadCount = 6,
                    checkCert = checkCert,
                    cdnType = selectedCdn,
                ) { result ->
                    if (isScanning) {
                        resultChannel.trySend(result)
                    }
                }
            } finally {
                resultChannel.close()
                consumer.join()
                isScanning = false
            }
        }
    }

    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val accentColor = Color(0xFF007AFF)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7))
            .padding(contentPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PageHeader(
                    title = "اسکنر Narcic Scanner",
                    subtitle = "Narcic Scanner Pro Engine & TLS Certificate Checker",
                )
            }

            // CDN Type Selector (Horizontal scrollable chips)
            item {
                Text(
                    text = "ارائه‌دهنده CDN (CDN Type)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cardBg,
                    border = BorderStroke(0.5.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ScannerEngine.cdnTypes.forEach { cdn ->
                            val isSelected = cdn.equals(selectedCdn, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) accentColor else (if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                                modifier = Modifier.clickable {
                                    selectedCdn = cdn
                                },
                            ) {
                                Text(
                                    text = cdn,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else (if (isDark) Color(0xFFE5E5EA) else Color(0xFF1C1C1E)),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Custom Range & Port Row
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cardBg,
                    border = BorderStroke(0.5.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = customRange,
                                onValueChange = { customRange = it },
                                label = { Text("Custom IP Range (اختیاری)") },
                                placeholder = { Text("مثال: 104.16.0.0/16") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = cardBorder,
                                ),
                                modifier = Modifier.weight(2.5f),
                            )

                            OutlinedTextField(
                                value = portText,
                                onValueChange = { portText = it },
                                label = { Text("Port") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = cardBorder,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Domain / CDN (Optional)
                        OutlinedTextField(
                            value = domain,
                            onValueChange = { domain = it },
                            label = { Text("Domain / CDN (اختیاری)") },
                            placeholder = { Text("مثال: www.hcaptcha.com") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = cardBorder,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Check Certificate Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "بررسی گواهی امنیتی (Check Certificate)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color(0xFFE5E5EA) else Color(0xFF1C1C1E),
                                )
                                Text(
                                    text = "تست هماهنگی TLS با هاست وارد شده",
                                    fontSize = 11.sp,
                                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                                )
                            }
                            Switch(
                                checked = checkCert,
                                onCheckedChange = { checkCert = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF34C759),
                                ),
                            )
                        }
                    }
                }
            }

            // Action Buttons for Narcic Scanner: START SCAN, STOP, CLEAR, GREEN, TOP16, COPY ALL
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Row 1: Start / Stop / Clear
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { if (isScanning) stopScan() else startScan() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning) Color(0xFFFF3B30) else accentColor,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.weight(1.5f).height(46.dp),
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("STOP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            } else {
                                Text("START SCAN", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Button(
                            onClick = {
                                results.clear()
                                scannedCount = 0
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                                contentColor = if (isDark) Color.White else Color.Black,
                            ),
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Text("CLEAR", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Row 2: SORT, GREEN, TOP16, COPY ALL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Button(
                            onClick = { sortByGreenFirst() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor.copy(alpha = 0.2f),
                                contentColor = accentColor,
                            ),
                            modifier = Modifier.weight(1.2f).height(42.dp),
                        ) {
                            Text("SORT ⚡", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val text = results.filter { it.status == Status.GREEN }.joinToString("\n") { it.ip }
                                if (text.isNotBlank()) {
                                    platform.copyToClipboard(text)
                                    platform.toast("آی‌پی‌های سبز کپی شدند")
                                } else {
                                    platform.toast("آی‌پی سبز برای کپی وجود ندارد")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF34C759).copy(alpha = 0.2f),
                                contentColor = Color(0xFF34C759),
                            ),
                            modifier = Modifier.weight(1f).height(42.dp),
                        ) {
                            Text("GREEN", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val text = results
                                    .filter { it.latency > 0 }
                                    .sortedBy { it.latency }
                                    .take(16)
                                    .joinToString("\n") { it.ip }
                                if (text.isNotBlank()) {
                                    platform.copyToClipboard(text)
                                    platform.toast("۱۶ آی‌پی برتر کپی شد")
                                } else {
                                    platform.toast("نتیجه‌ای برای کپی وجود ندارد")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                                contentColor = if (isDark) Color.White else Color.Black,
                            ),
                            modifier = Modifier.weight(1f).height(42.dp),
                        ) {
                            Text("TOP 16", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val text = results.joinToString("\n") { it.ip }
                                if (text.isNotBlank()) {
                                    platform.copyToClipboard(text)
                                    platform.toast("همه آی‌پی‌ها کپی شدند")
                                } else {
                                    platform.toast("نتیجه‌ای موجود نیست")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                                contentColor = if (isDark) Color.White else Color.Black,
                            ),
                            modifier = Modifier.weight(1f).height(42.dp),
                        ) {
                            Text("COPY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Results Counter Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "نتایج اسکن (${results.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isDark) Color.White else Color.Black,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.15f),
                            modifier = Modifier.clickable { sortByGreenFirst() },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "⚡ SORT (سبزها بالا)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                )
                            }
                        }

                        val greenCount = results.count { it.status == Status.GREEN }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF34C759).copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "سالم: $greenCount",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34C759),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            // Scan Results List
            itemsIndexed(results, key = { index, item -> "${item.ip}_$index" }) { index, item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cardBg,
                    border = BorderStroke(0.5.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                platform.copyToClipboard(item.ip)
                                platform.toast("${item.ip} کپی شد")
                            }
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val dotColor = when (item.status) {
                                    Status.GREEN -> Color(0xFF34C759)
                                    Status.YELLOW -> Color(0xFFFF9500)
                                    Status.RED -> Color(0xFFFF3B30)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(dotColor),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = item.ip,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isDark) Color.White else Color.Black,
                                )
                            }

                            val (badgeBg, badgeFg, label) = when (item.status) {
                                Status.GREEN -> Triple(Color(0xFF34C759).copy(alpha = 0.15f), Color(0xFF34C759), "⚡ ${item.latency} ms")
                                Status.YELLOW -> Triple(Color(0xFFFF9500).copy(alpha = 0.15f), Color(0xFFFF9500), "${item.latency} ms")
                                Status.RED -> Triple(Color(0xFFFF3B30).copy(alpha = 0.15f), Color(0xFFFF3B30), if (item.latency >= 999) "خطا" else "${item.latency} ms")
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badgeBg,
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeFg,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }

                        // Domain, Ping, Certificate / Subdomain info
                        if (item.status != Status.RED) {
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF2C2C2E).copy(alpha = 0.6f) else Color(0xFFF2F2F7))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // Explicit Ping / Latency row for healthy connection
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "⚡ پینگ اتصال (Ping):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (item.status == Status.GREEN) Color(0xFF34C759) else Color(0xFFFF9500),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "${item.latency} ms",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.status == Status.GREEN) Color(0xFF34C759) else Color(0xFFFF9500),
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                if (item.domain != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "🌐 دامنه سالم:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = item.domain,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDark) Color(0xFFE5E5EA) else Color(0xFF1C1C1E),
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }

                                if (item.certDomain != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "📜 زیرمجموعه گواهی:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = item.certDomain,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF34C759),
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }

                                if (item.cdnType != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "☁️ شبکه CDN:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = item.cdnType,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = accentColor,
                                        )
                                    }
                                }
                            }
                        }

                        // Option to set this IP in SNI Spoof Settings if settings and onUpdateSettings are present
                        if (item.status == Status.GREEN && onUpdateSettings != null && settings != null) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accentColor.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val portInt = portText.toIntOrNull() ?: 443
                                        val sniHost = item.domain ?: ScannerEngine.getDefaultDomainForCdn(selectedCdn)
                                        onUpdateSettings.invoke { s ->
                                            s.copy(
                                                rstaSpoofConnectIp = item.ip,
                                                rstaSpoofConnectPort = portInt,
                                                rstaSpoofFakeSni = sniHost,
                                            )
                                        }
                                        platform.toast("آی‌پی ${item.ip} و SNI در تنظیمات Spoof ذخیره شد")
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "⚡ ست کردن در جعل SNI (Spoof)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
