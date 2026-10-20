package dev.valid7996.narcicgeteway.ui.settings.patt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.data.tools.patt.CloudflareFixEngine
import dev.valid7996.narcicgeteway.data.tools.patt.CloudflareFixResult
import dev.valid7996.narcicgeteway.data.tools.patt.EchEngine
import dev.valid7996.narcicgeteway.data.tools.patt.EchResult
import dev.valid7996.narcicgeteway.data.tools.patt.SniIpResult
import dev.valid7996.narcicgeteway.data.tools.patt.SniResultStatus
import dev.valid7996.narcicgeteway.data.tools.patt.SniSpoofCheckConfig
import dev.valid7996.narcicgeteway.data.tools.patt.SniSpoofCheckEngine
import dev.valid7996.narcicgeteway.data.tools.patt.kAllSniPorts
import dev.valid7996.narcicgeteway.data.tools.patt.kAvailableFingerprints
import dev.valid7996.narcicgeteway.data.tools.patt.kDefaultSniPorts
import dev.valid7996.narcicgeteway.data.tools.patt.kDefaultSniTargets
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.ic_bolt
import dev.valid7996.narcicgeteway.shared.resources.ic_check
import dev.valid7996.narcicgeteway.shared.resources.ic_close
import dev.valid7996.narcicgeteway.shared.resources.ic_content_paste
import dev.valid7996.narcicgeteway.shared.resources.ic_description
import dev.valid7996.narcicgeteway.shared.resources.ic_dns
import dev.valid7996.narcicgeteway.shared.resources.ic_encrypted
import dev.valid7996.narcicgeteway.shared.resources.ic_info
import dev.valid7996.narcicgeteway.shared.resources.ic_ios_share
import dev.valid7996.narcicgeteway.shared.resources.ic_public
import dev.valid7996.narcicgeteway.shared.resources.ic_speed
import dev.valid7996.narcicgeteway.shared.resources.ic_tune
import dev.valid7996.narcicgeteway.ui.components.PageHeader
import dev.valid7996.narcicgeteway.ui.components.ProvidePageBack
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PattSheet(
    onDismiss: () -> Unit,
    settings: AppSettings? = null,
    onUpdateSettings: (((AppSettings) -> AppSettings) -> Unit)? = null,
    onImportConfig: ((String) -> Unit)? = null,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
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
            PattScreen(
                contentPadding = PaddingValues(bottom = 28.dp),
                settings = settings,
                onUpdateSettings = onUpdateSettings,
                onImportConfig = onImportConfig,
            )
        }
    }
}

@Composable
fun PattScreen(
    contentPadding: PaddingValues = PaddingValues(),
    settings: AppSettings? = null,
    onUpdateSettings: (((AppSettings) -> AppSettings) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onImportConfig: ((String) -> Unit)? = null,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        PageHeader(
            title = "ابزارهای Patt",
            subtitle = "SNI Check • Cloudflare Fix • Add ECH",
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Color(0xFFFF9500),
            edgePadding = 16.dp,
            divider = { HorizontalDivider(color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)) },
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("SNI Check", fontWeight = FontWeight.SemiBold) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Cloudflare Fix", fontWeight = FontWeight.SemiBold) },
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Add ECH", fontWeight = FontWeight.SemiBold) },
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> SniCheckTab(isDark = isDark, settings = settings, onUpdateSettings = onUpdateSettings)
                1 -> CloudflareFixTab(isDark = isDark, onImportConfig = onImportConfig)
                2 -> AddEchTab(isDark = isDark, onImportConfig = onImportConfig)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. SNI Check Tab
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SniCheckTab(
    isDark: Boolean,
    settings: AppSettings? = null,
    onUpdateSettings: (((AppSettings) -> AppSettings) -> Unit)? = null,
) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()

    var targetsText by remember { mutableStateOf(kDefaultSniTargets) }
    val selectedPorts = remember { mutableStateListOf<Int>().apply { addAll(kDefaultSniPorts) } }
    var showSettings by remember { mutableStateOf(false) }

    var timeout by remember { mutableIntStateOf(5) }
    var retries by remember { mutableIntStateOf(3) }
    var concurrency by remember { mutableIntStateOf(20) }
    var enableIpCheck by remember { mutableStateOf(true) }
    var manualIp by remember { mutableStateOf("") }

    var isScanning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var currentStatus by remember { mutableStateOf("") }
    var userPublicIp by remember { mutableStateOf<String?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var scanReportCopied by remember { mutableStateOf(false) }

    val results = remember { mutableStateListOf<SniIpResult>() }

    var cleanIpsCopied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Input Targets Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDark) Color(0xFF252528) else Color(0xFFF2F2F7),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA)),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "دامنه‌های هدف (SNI Targets)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isDark) Color.White else Color.Black,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { targetsText = kDefaultSniTargets }) {
                            Text("نمونه", fontSize = 12.sp, color = Color(0xFFFF9500))
                        }
                        IconButton(onClick = { showSettings = !showSettings }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_tune),
                                contentDescription = "Settings",
                                tint = if (showSettings) Color(0xFFFF9500) else Color.Gray,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = targetsText,
                    onValueChange = { targetsText = it },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    placeholder = { Text("دامنه دامنه‌ها به صورت خط به خط...") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF9500),
                        unfocusedBorderColor = if (isDark) Color(0xFF3C3C3E) else Color(0xFFD1D1D6),
                    ),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )

                // Ports row
                Text("پورت‌های مورد بررسی:", fontSize = 12.sp, color = Color.Gray)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kAllSniPorts.forEach { p ->
                        val isSelected = selectedPorts.contains(p)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    if (selectedPorts.size > 1) selectedPorts.remove(p)
                                } else {
                                    selectedPorts.add(p)
                                }
                            },
                            label = { Text("$p", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF9500).copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFFFF9500),
                            ),
                        )
                    }
                }
            }
        }

        // Settings collapsible
        AnimatedVisibility(visible = showSettings) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFEBEBF0),
                border = BorderStroke(1.dp, Color(0xFFFF9500).copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("تنظیمات اسکن SNI", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFF9500))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("تأیید دست‌دست آی‌پی کلادفلر", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("ارسال مستقیم درخواست TLS به آی‌پی و بررسی /cdn-cgi/trace", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(checked = enableIpCheck, onCheckedChange = { enableIpCheck = it })
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("همزمانی تست (Concurrency)", fontSize = 12.sp)
                            Text("$concurrency تِرد", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9500))
                        }
                        Slider(
                            value = concurrency.toFloat(),
                            onValueChange = { concurrency = it.toInt() },
                            valueRange = 1f..50f,
                            steps = 49,
                        )
                    }

                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("آی‌پی دستی عمومی (اختیاری)") },
                        placeholder = { Text("خالی بگذارید تا خودکار تشخیص دهد") },
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                    )
                }
            }
        }

        // Action Buttons Row
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        if (isScanning) {
                            scanJob?.cancel()
                            scanJob = null
                            isScanning = false
                            currentStatus = "متوقف شد"
                        } else {
                            scanJob?.cancel()
                            isScanning = true
                            results.clear()
                            scanReportCopied = false
                            cleanIpsCopied = false
                            currentStatus = "در حال شروع اسکن..."
                            scanJob = scope.launch {
                                try {
                                    val config = SniSpoofCheckConfig(
                                        ports = selectedPorts.toList(),
                                        timeout = timeout,
                                        retries = retries,
                                        concurrency = concurrency,
                                        enableIpCheck = enableIpCheck,
                                        manualIp = manualIp.ifBlank { null },
                                    )
                                    SniSpoofCheckEngine.runScan(
                                        targetsText = targetsText,
                                        config = config,
                                        onPublicIpDetected = { userPublicIp = it },
                                        onProgress = { cur, tot, status ->
                                            progress = if (tot > 0) cur.toFloat() / tot else 0f
                                            currentStatus = status
                                        },
                                        onResult = { res ->
                                            results.add(res)
                                        },
                                    )
                                    currentStatus = "اسکن کامل شد (${results.size} نتیجه)"
                                } catch (_: kotlinx.coroutines.CancellationException) {
                                    currentStatus = "متوقف شد"
                                } catch (e: Exception) {
                                    currentStatus = "خطا: ${e.message}"
                                } finally {
                                    isScanning = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) Color(0xFFFF3B30) else Color(0xFFFF9500),
                    ),
                ) {
                    Icon(
                        painter = painterResource(if (isScanning) Res.drawable.ic_close else Res.drawable.ic_bolt),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isScanning) "توقف اسکن" else "شروع تست SNI Spoof", fontWeight = FontWeight.Bold)
                }
            }

            if (results.isNotEmpty()) {
                val okIps = results.filter { it.status == SniResultStatus.OK && it.ip.isNotBlank() }
                    .map { it.ip }.distinct()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (okIps.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                platform.copyToClipboard(okIps.joinToString("\n"))
                                cleanIpsCopied = true
                                platform.toast("تعداد ${okIps.size} آی‌پی سالم کپی شد")
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(if (cleanIpsCopied) Res.drawable.ic_check else Res.drawable.ic_content_paste),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF34C759),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (cleanIpsCopied) "کپی شد" else "کپی آی‌پی‌های سالم (${okIps.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF34C759),
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val report = SniSpoofCheckEngine.generateReport(results, userPublicIp)
                            platform.copyToClipboard(report)
                            scanReportCopied = true
                            platform.toast("گزارش تست کپی شد")
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(if (scanReportCopied) Res.drawable.ic_check else Res.drawable.ic_description),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (scanReportCopied) "کپی شد" else "کپی کل گزارش", fontSize = 12.sp)
                    }
                }
            }
        }

        // Live progress indicator & IP Banner
        if (isScanning || progress > 0f || userPublicIp != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF202023) else Color(0xFFEBEBF0),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(currentStatus, fontSize = 12.sp, color = if (isDark) Color.LightGray else Color.DarkGray)
                        if (userPublicIp != null) {
                            Text("IP: $userPublicIp", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                        }
                    }
                    LinearProgressIndicator(
                        progress = { if (isScanning) progress else 1f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFFFF9500),
                    )
                }
            }
        }

        // Results List
        if (results.isNotEmpty()) {
            Text(
                "نتایج بررسی (${results.size}):",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isDark) Color.White else Color.Black,
            )

            results.forEach { res ->
                SniResultCard(
                    res = res,
                    isDark = isDark,
                    onApplySniSpoof = { ip, port, sni ->
                        onUpdateSettings?.invoke { s ->
                            s.copy(
                                rstaSpoofConnectIp = ip,
                                rstaSpoofConnectPort = port,
                                rstaSpoofFakeSni = sni,
                            )
                        }
                        platform.toast("در تنظیمات جعل SNI ست شد:\nIP: $ip\nPort: $port\nSNI: $sni")
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SniResultCard(
    res: SniIpResult,
    isDark: Boolean,
    onApplySniSpoof: ((ip: String, port: Int, sni: String) -> Unit)? = null,
) {
    val platform = LocalPlatform.current
    val statusColor = when (res.status) {
        SniResultStatus.OK -> Color(0xFF34C759)
        SniResultStatus.FAIL -> Color(0xFFFF3B30)
        SniResultStatus.FILTERED -> Color(0xFFFF9500)
        SniResultStatus.ERROR -> Color(0xFF8E8E93)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color(0xFF222225) else Color(0xFFF7F7FA),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(res.target, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (res.ip.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(res.ip, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    platform.copyToClipboard(res.ip)
                                    platform.toast("آی‌پی ${res.ip} کپی شد")
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_content_paste),
                                    contentDescription = "Copy IP",
                                    tint = Color(0xFFFF9500),
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            platform.copyToClipboard(res.toDisplayString())
                            platform.toast("نتیجه سطر کپی شد")
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_ios_share),
                            contentDescription = "Copy full row",
                            tint = Color.Gray,
                            modifier = Modifier.size(15.dp),
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            res.status.name,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Port results chips
            if (res.portResults.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    res.portResults.forEach { p ->
                        val portColor = when {
                            p.tlsSuccess -> Color(0xFF34C759)
                            p.isOpen -> Color(0xFFFF9500)
                            else -> Color(0xFFFF3B30)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = portColor.copy(alpha = 0.12f),
                        ) {
                            val label = when {
                                p.tlsSuccess -> {
                                    val codeStr = p.httpStatus?.let { " HTTP $it" } ?: ""
                                    "${p.port} TLS+HTTP✔$codeStr (${p.latencyMs}ms)"
                                }
                                p.isOpen -> "${p.port} (TCP Only ⚠)"
                                else -> "${p.port} ✖"
                            }
                            Text(
                                label,
                                color = portColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // Apply to SNI Spoof Settings Button
            if (res.status == SniResultStatus.OK && res.ip.isNotBlank() && onApplySniSpoof != null) {
                val healthyPort = res.portResults.firstOrNull { it.tlsSuccess }?.port
                    ?: res.portResults.firstOrNull { it.isOpen }?.port
                    ?: 443
                Button(
                    onClick = {
                        onApplySniSpoof(res.ip, healthyPort, res.target)
                    },
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF34C759),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_tune),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "ست کردن در تنظیمات جعل SNI (Spoof)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Direct TLS IP Handshake Match
            if (res.ipCheckResult != null) {
                val ipMatch = res.ipCheckResult.matched
                val checkColor = if (ipMatch) Color(0xFF34C759) else if (res.ipCheckResult.tlsSuccess) Color(0xFF32ADE6) else Color(0xFFFF9500)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            ipMatch -> "✔ تأیید هویت IP کلادفلر (IP Verified)"
                            res.ipCheckResult.tlsSuccess && res.ipCheckResult.detectedIp != null ->
                                "✔ هندشیک TLS موفق (IP شناسایی‌شده: ${res.ipCheckResult.detectedIp})"
                            res.ipCheckResult.tlsSuccess ->
                                "✔ هندشیک TLS با SNI موفق (SNI Spoof OK)"
                            else -> "✖ هویت IP تأیید نشد (${res.ipCheckResult.detectedIp ?: "بدون پاسخ"})"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = checkColor,
                    )
                }
            }

            if (res.errorMessage != null) {
                Text(
                    res.errorMessage,
                    fontSize = 11.sp,
                    color = Color(0xFFFF3B30),
                    maxLines = 2,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. Cloudflare Fix Tab
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudflareFixTab(
    isDark: Boolean,
    onImportConfig: ((String) -> Unit)? = null,
) {
    val platform = LocalPlatform.current

    val sampleVless = "vless://00000000-0000-0000-0000-000000000000@example.com:443?path=%2Fpath&security=tls&alpn=http%2F1.1&encryption=none&host=example.com&fp=unsafe&type=ws&sni=example.com#CloudflareFixExample"

    var inputLink by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    var socksPort by remember { mutableIntStateOf(10808) }
    var httpPort by remember { mutableIntStateOf(10809) }
    var dnsServer by remember { mutableStateOf(CloudflareFixEngine.DEFAULT_DNS_SERVER) }
    var remarkSuffix by remember { mutableStateOf("-custom") }
    var selectedFp by remember { mutableStateOf("unsafe") }
    var alpnText by remember { mutableStateOf("http/1.1") }
    var enableFinalmask by remember { mutableStateOf(true) }

    var result by remember { mutableStateOf<CloudflareFixResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var jsonCopied by remember { mutableStateOf(false) }
    var importedSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Info Banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF007AFF).copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color(0xFF007AFF).copy(alpha = 0.3f)),
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(Res.drawable.ic_info),
                    contentDescription = null,
                    tint = Color(0xFF007AFF),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "پشتیبانی انحصاری از لینک‌های VLESS+TLS+WS و VLESS+TLS+xHTTP. کانفیگ نهایی با تکه‌تکه‌سازی TCP Finalmask و uTLS ساخته می‌شود.",
                    fontSize = 12.sp,
                    color = if (isDark) Color.White else Color.Black,
                )
            }
        }

        // Input Link Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDark) Color(0xFF252528) else Color(0xFFF2F2F7),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA)),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "لینک کانفیگ VLESS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isDark) Color.White else Color.Black,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { inputLink = sampleVless }) {
                            Text("نمونه", fontSize = 12.sp, color = Color(0xFFFF9500))
                        }
                        IconButton(onClick = { showSettings = !showSettings }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_tune),
                                contentDescription = "Settings",
                                tint = if (showSettings) Color(0xFFFF9500) else Color.Gray,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = inputLink,
                    onValueChange = { inputLink = it },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    placeholder = { Text("vless://... با شبکه ws یا xhttp") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }

        // Settings collapsible
        AnimatedVisibility(visible = showSettings) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFEBEBF0),
                border = BorderStroke(1.dp, Color(0xFFFF9500).copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تنظیمات Cloudflare Fix", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFF9500))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("تکه‌تکه‌سازی TCP Finalmask", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("تزریق بسته‌های چندتکه جهت دور زدن سانسور", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(checked = enableFinalmask, onCheckedChange = { enableFinalmask = it })
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = socksPort.toString(),
                            onValueChange = { socksPort = it.toIntOrNull() ?: 10808 },
                            label = { Text("پورت SOCKS") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = httpPort.toString(),
                            onValueChange = { httpPort = it.toIntOrNull() ?: 10809 },
                            label = { Text("پورت HTTP") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )
                    }

                    OutlinedTextField(
                        value = dnsServer,
                        onValueChange = { dnsServer = it },
                        label = { Text("سرور DoH") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                    )

                    Text("اثرانگشت uTLS Fingerprint:", fontSize = 12.sp, color = Color.Gray)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        kAvailableFingerprints.take(6).forEach { fp ->
                            FilterChip(
                                selected = selectedFp == fp,
                                onClick = { selectedFp = fp },
                                label = { Text(fp, fontSize = 11.sp) },
                            )
                        }
                    }
                }
            }
        }

        // Action Button
        Button(
            onClick = {
                errorMessage = null
                result = null
                jsonCopied = false
                importedSuccess = false
                try {
                    val res = CloudflareFixEngine.transformVlessUrl(
                        shareLink = inputLink,
                        socksPort = socksPort,
                        httpPort = httpPort,
                        dnsServer = dnsServer,
                        remarkSuffix = remarkSuffix,
                        fingerprint = selectedFp,
                        alpn = alpnText.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                        enableFinalmask = enableFinalmask,
                    )
                    result = res
                } catch (e: Exception) {
                    errorMessage = e.message ?: "خطا در پردازش کانفیگ"
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500)),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_bolt), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("تبدیل به کانفیگ بهینه‌شده Cloudflare Fix", fontWeight = FontWeight.Bold)
        }

        if (errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFF3B30).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFFFF3B30)),
            ) {
                Text(
                    errorMessage!!,
                    color = Color(0xFFFF3B30),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // Results Card
        result?.let { res ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF222225) else Color(0xFFF7F7FA),
                border = BorderStroke(1.dp, Color(0xFFFF9500).copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("خلاصه کانفیگ بهینه‌شده:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BadgeChip("نام", res.remarks)
                        BadgeChip("سرور", "${res.address}:${res.port}")
                        BadgeChip("شبکه", res.network)
                        BadgeChip("SNI", res.sni)
                        BadgeChip("Fingerprint", res.fingerprint)
                        BadgeChip("ALPN", res.alpn.joinToString(","))
                    }

                    HorizontalDivider(color = if (isDark) Color(0xFF333336) else Color(0xFFE5E5EA))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("کانفیگ نهایی Xray JSON:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (onImportConfig != null) {
                                Button(
                                    onClick = {
                                        onImportConfig(res.formattedJson)
                                        importedSuccess = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(if (importedSuccess) "اضافه شد ✔" else "افزودن به سرورها", fontSize = 11.sp)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    platform.copyToClipboard(res.formattedJson)
                                    jsonCopied = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(if (jsonCopied) "کپی شد ✔" else "کپی JSON", fontSize = 11.sp)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Color(0xFF141416) else Color(0xFFFFFFFF),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                    ) {
                        Text(
                            res.formattedJson,
                            modifier = Modifier.padding(10.dp).fillMaxWidth().height(180.dp).verticalScroll(rememberScrollState()),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. Add ECH Tab
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddEchTab(
    isDark: Boolean,
    onImportConfig: ((String) -> Unit)? = null,
) {
    val platform = LocalPlatform.current

    val sampleVless = "vless://00000000-0000-0000-0000-000000000000@example.com:443?path=%2Fws&security=tls&alpn=http%2F1.1&encryption=none&host=example.com&fp=chrome&type=ws&sni=example.com#EchSample"

    var inputConfig by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    var echConfigList by remember { mutableStateOf(EchEngine.DEFAULT_ECH_CONFIG_LIST) }
    var socksPort by remember { mutableIntStateOf(10808) }
    var httpPort by remember { mutableStateOf("") }
    var dnsServer by remember { mutableStateOf(EchEngine.DEFAULT_DNS_SERVER) }
    var domesticDns by remember { mutableStateOf(EchEngine.DEFAULT_DOMESTIC_DNS) }
    var remarkSuffix by remember { mutableStateOf("-ech") }
    var selectedFp by remember { mutableStateOf("chrome") }
    var alpnText by remember { mutableStateOf("http/1.1") }
    var allowInsecure by remember { mutableStateOf(false) }

    var result by remember { mutableStateOf<EchResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var jsonCopied by remember { mutableStateOf(false) }
    var importedSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFAF52DE).copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color(0xFFAF52DE).copy(alpha = 0.3f)),
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(Res.drawable.ic_encrypted),
                    contentDescription = null,
                    tint = Color(0xFFAF52DE),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Encrypted Client Hello (ECH)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "تزریق رمزنگاری ECH به لینک‌های پراکسی (VLESS, VMess, Trojan, SS) و کانفیگ‌های کامل لودبالانسر و چِین همراه FakeDNS و روتینگ داخلی.",
                        fontSize = 11.5.sp,
                        color = if (isDark) Color.LightGray else Color.DarkGray,
                    )
                }
            }
        }

        // Input Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDark) Color(0xFF252528) else Color(0xFFF2F2F7),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA)),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "لینک یا کانفیگ JSON ورودی",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isDark) Color.White else Color.Black,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { inputConfig = sampleVless }) {
                            Text("نمونه", fontSize = 12.sp, color = Color(0xFFFF9500))
                        }
                        IconButton(onClick = { showSettings = !showSettings }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_tune),
                                contentDescription = "Settings",
                                tint = if (showSettings) Color(0xFFFF9500) else Color.Gray,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = inputConfig,
                    onValueChange = { inputConfig = it },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    placeholder = { Text("vless://, vmess://, trojan://, ss:// یا کانفیگ کامل Xray JSON...") },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }

        // Settings collapsible
        AnimatedVisibility(visible = showSettings) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFEBEBF0),
                border = BorderStroke(1.dp, Color(0xFFFF9500).copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تنظیمات ECH & TLS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFF9500))

                    OutlinedTextField(
                        value = echConfigList,
                        onValueChange = { echConfigList = it },
                        label = { Text("ECH Config List") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "cloudflare-ech.com+udp://1.1.1.1",
                            "cloudflare-ech.com+tcp://1.1.1.1",
                            "cloudflare-ech.com+udp://8.8.8.8",
                        ).forEach { ech ->
                            FilterChip(
                                selected = echConfigList == ech,
                                onClick = { echConfigList = ech },
                                label = { Text(ech, fontSize = 10.sp) },
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = socksPort.toString(),
                            onValueChange = { socksPort = it.toIntOrNull() ?: 10808 },
                            label = { Text("پورت SOCKS") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = httpPort,
                            onValueChange = { httpPort = it },
                            label = { Text("پورت HTTP (اختیاری)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dnsServer,
                            onValueChange = { dnsServer = it },
                            label = { Text("سرور DoH") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = domesticDns,
                            onValueChange = { domesticDns = it },
                            label = { Text("دی‌ان‌اس داخلی") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )
                    }

                    Text("اثرانگشت uTLS Fingerprint:", fontSize = 12.sp, color = Color.Gray)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        kAvailableFingerprints.take(6).forEach { fp ->
                            FilterChip(
                                selected = selectedFp == fp,
                                onClick = { selectedFp = fp },
                                label = { Text(fp, fontSize = 11.sp) },
                            )
                        }
                    }
                }
            }
        }

        // Action Button
        Button(
            onClick = {
                errorMessage = null
                result = null
                jsonCopied = false
                importedSuccess = false
                try {
                    val res = EchEngine.addEchToInput(
                        rawInput = inputConfig,
                        echConfigList = echConfigList,
                        socksPort = socksPort,
                        httpPort = httpPort.toIntOrNull(),
                        dnsServer = dnsServer,
                        domesticDns = domesticDns,
                        remarkSuffix = remarkSuffix,
                        fingerprint = selectedFp,
                        alpn = alpnText.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                        allowInsecure = allowInsecure,
                    )
                    result = res
                } catch (e: Exception) {
                    errorMessage = e.message ?: "خطا در اعمال ECH"
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAF52DE)),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_encrypted), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("اعمال رمزنگاری ECH به کانفیگ", fontWeight = FontWeight.Bold)
        }

        if (errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFF3B30).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFFFF3B30)),
            ) {
                Text(
                    errorMessage!!,
                    color = Color(0xFFFF3B30),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // Results Card
        result?.let { res ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF222225) else Color(0xFFF7F7FA),
                border = BorderStroke(1.dp, Color(0xFFAF52DE).copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("خلاصه کانفیگ مجهز به ECH:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BadgeChip("نام", res.remarks)
                        BadgeChip("پروتکل", res.protocol)
                        BadgeChip("سرور/SNI", res.sni.ifEmpty { res.address })
                        BadgeChip("شبکه", res.network)
                        BadgeChip("تعداد نود", "${res.proxyCount}")
                        BadgeChip("ECH Config", res.echConfigList.substringBefore('+'))
                    }

                    HorizontalDivider(color = if (isDark) Color(0xFF333336) else Color(0xFFE5E5EA))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("کانفیگ نهایی Xray ECH JSON:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (onImportConfig != null) {
                                Button(
                                    onClick = {
                                        onImportConfig(res.formattedJson)
                                        importedSuccess = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(if (importedSuccess) "اضافه شد ✔" else "افزودن به سرورها", fontSize = 11.sp)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    platform.copyToClipboard(res.formattedJson)
                                    jsonCopied = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(if (jsonCopied) "کپی شد ✔" else "کپی JSON", fontSize = 11.sp)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Color(0xFF141416) else Color(0xFFFFFFFF),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                    ) {
                        Text(
                            res.formattedJson,
                            modifier = Modifier.padding(10.dp).fillMaxWidth().height(180.dp).verticalScroll(rememberScrollState()),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isDark) Color(0xFFAF52DE) else Color(0xFF6B21A8),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFF9500).copy(alpha = 0.12f),
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text("$label: ", fontSize = 11.sp, color = Color.Gray)
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF9500))
        }
    }
}
