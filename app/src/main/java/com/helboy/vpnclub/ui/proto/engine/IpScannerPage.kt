package com.helboy.vpnclub.ui.proto.engine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEmpty
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoLoading
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import androidx.compose.ui.text.input.KeyboardType
import dev.valid7996.narcicgeteway.data.tools.ScanResult
import dev.valid7996.narcicgeteway.data.tools.ScannerEngine
import dev.valid7996.narcicgeteway.data.tools.Status
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Finds clean addresses of a CDN: tries random addresses of its ranges with a real TLS handshake and ranks them by
 * latency. Results can be copied, or written into the SNI-spoof settings. The scan stops when it is cancelled or the
 * page is left.
 */
@Composable
fun IpScannerPage(onBack: () -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var cdn by remember { mutableStateOf(ScannerEngine.cdnTypes.first()) }
    var rangesText by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf(IpScannerLogic.DEFAULT_PORT.toString()) }
    var showFailed by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(0) }
    var expected by remember { mutableStateOf(0) }
    var results by remember { mutableStateOf(listOf<ScanResult>()) }
    var usedPort by remember { mutableStateOf(IpScannerLogic.DEFAULT_PORT) }
    var started by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun stop() {
        ScannerEngine.stop()
        job?.cancel()
        job = null
        running = false
    }

    DisposableEffect(Unit) { onDispose { if (running || job != null) stop() } }

    val parsed = IpScannerLogic.parseRanges(rangesText)
    val portCheck = IpScannerLogic.checkPort(portText)
    val hostError = IpScannerLogic.checkHost(domain)
    val rangesError = if (parsed.invalid.isNotEmpty()) "رنج نامعتبر: " + parsed.invalid.take(3).joinToString(" ، ") else null

    fun start() {
        if (running) return
        val port = (portCheck as? Checked.Ok)?.value
        if (port == null || hostError != null || rangesError != null) {
            problem = "ورودی‌های قرمز را درست کنید و دوباره شروع کنید."
            return
        }
        val ranges = if (parsed.ranges.isNotEmpty()) parsed.ranges
        else ScannerEngine.getDefaultRangesForCdn(cdn).shuffled().take(4)
        if (ranges.isEmpty()) {
            problem = "برای این CDN رنجی شناخته‌شده نیست؛ رنج خودتان را وارد کنید."
            return
        }
        problem = null
        results = emptyList()
        scanned = 0
        expected = IpScannerLogic.expected(ranges)
        usedPort = port
        started = true
        running = true
        val cdnNow = cdn
        val host = domain.trim()
        job = scope.launch {
            val channel = Channel<ScanResult>(Channel.UNLIMITED)
            val consumer = launch {
                for (r in channel) {
                    results = IpScannerLogic.merge(results, r)
                    scanned++
                }
            }
            try {
                ScannerEngine.scan(
                    ranges = ranges, domain = host, port = port,
                    ipPerRange = IpScannerLogic.IPS_PER_RANGE, threadCount = 6,
                    checkCert = false, cdnType = cdnNow,
                ) { r -> channel.trySend(r) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                problem = "اسکن با خطا متوقف شد؛ اینترنت را بررسی و دوباره امتحان کنید."
            } finally {
                channel.close()
                running = false
            }
        }
    }

    ProtoPage(title = "اسکنر IP تمیز", onBack = onBack, scroll = false) {
        Text(
            "آدرس‌های CDN را با دست‌دادن واقعی TLS آزمایش می‌کند و سریع‌ترین‌ها را بالا می‌آورد. آدرس خوب را کپی کنید یا مستقیم در تنظیمات جعل SNI بگذارید.",
            color = TextSecondary, fontSize = 12.sp,
        )
        ProtoChoice(
            title = "CDN", options = ScannerEngine.cdnTypes.map { it to it }, selected = cdn,
            onSelect = { if (!running) cdn = it },
            hint = "SNI پیش‌فرض: " + ScannerEngine.getDefaultDomainForCdn(cdn),
        )
        ProtoField(
            "رنج‌ها (اختیاری)", rangesText, { rangesText = it },
            placeholder = "104.16.0.0/13\n172.64.0.0/13", error = rangesError,
            singleLine = false, minLines = 2, monospace = true, enabled = !running, tag = "proto_scan_ranges",
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProtoField(
                "SNI / دامنه", domain, { domain = it }, modifier = Modifier.weight(1f),
                placeholder = "خالی = پیش‌فرض CDN", error = hostError, enabled = !running, monospace = true, tag = "proto_scan_domain",
            )
            ProtoField(
                "پورت", portText, { portText = it.filter(Char::isDigit).take(5) }, modifier = Modifier.weight(0.5f),
                error = (portCheck as? Checked.Bad)?.message, keyboard = KeyboardType.Number, enabled = !running, tag = "proto_scan_port",
            )
        }
        problem?.let { Text(it, color = StatusRed, fontSize = 12.sp, modifier = Modifier.testTag("proto_scan_problem")) }
        if (running) {
            ProtoButton("توقف اسکن", Icons.Rounded.Stop, { stop() }, accent = StatusRed, tag = "proto_scan_cancel")
            LinearProgressIndicator(
                progress = { if (expected <= 0) 0f else (scanned.toFloat() / expected).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(), color = NeonCyan,
            )
        } else {
            ProtoButton("شروع اسکن", Icons.Rounded.Radar, { start() }, tag = "proto_scan_start")
        }

        val visible = if (showFailed) IpScannerLogic.sorted(results) else IpScannerLogic.sorted(results).filter { IpScannerLogic.reachable(it) }
        when {
            !started -> ProtoEmpty("هنوز اسکنی انجام نشده", "CDN را انتخاب کنید و «شروع اسکن» را بزنید. بدون رنج دستی، چند رنج آماده‌ی همان CDN آزمایش می‌شود.")
            running && visible.isEmpty() -> ProtoLoading("در حال آزمایش آدرس‌ها… ${scanned.fa()} از حدود ${expected.fa()}")
            !running && visible.isEmpty() -> ProtoEmpty(
                "آدرس پاسخ‌گویی پیدا نشد",
                "رنج یا پورت دیگری امتحان کنید، یا اینترنت/VPN را بررسی کنید.",
                actionText = "اسکن دوباره", onAction = { start() },
            )
            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(IpScannerLogic.summary(results), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { copyLines(platform::copyToClipboard, platform::toast, IpScannerLogic.green(results), "آی‌پی‌های عالی کپی شدند", "آی‌پی عالی برای کپی نیست") }) { Text("کپی عالی‌ها", fontSize = 11.sp) }
                    OutlinedButton(onClick = { copyLines(platform::copyToClipboard, platform::toast, IpScannerLogic.top(results), "۱۶ آی‌پی برتر کپی شد", "نتیجه‌ای برای کپی نیست") }) { Text("کپی ۱۶ برتر", fontSize = 11.sp) }
                    OutlinedButton(onClick = { copyLines(platform::copyToClipboard, platform::toast, visible, "همه‌ی آی‌پی‌ها کپی شدند", "نتیجه‌ای برای کپی نیست") }) { Text("کپی همه", fontSize = 11.sp) }
                }
                ProtoSwitch("نمایش آدرس‌های بدون پاسخ", showFailed, { showFailed = it })
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().testTag("proto_scan_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.ip }) { r ->
                        ResultRow(
                            r,
                            onCopy = { platform.copyToClipboard(r.ip); platform.toast("${r.ip} کپی شد") },
                            onSpoof = {
                                val sni = r.domain ?: ScannerEngine.getDefaultDomainForCdn(r.cdnType ?: cdn)
                                ProtoEnv.settings.update { it.copy(rstaSpoofConnectIp = r.ip, rstaSpoofConnectPort = usedPort, rstaSpoofFakeSni = sni) }
                                platform.toast("آی‌پی ${r.ip} در تنظیمات جعل SNI ذخیره شد")
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun copyLines(
    copy: (String) -> Unit, toast: (String) -> Unit,
    list: List<ScanResult>, done: String, none: String,
) {
    if (list.isEmpty()) toast(none) else {
        copy(IpScannerLogic.ipLines(list))
        toast(done)
    }
}

@Composable
private fun ResultRow(r: ScanResult, onCopy: () -> Unit, onSpoof: () -> Unit) {
    val color = when {
        !IpScannerLogic.reachable(r) -> TextTertiary
        r.status == Status.GREEN -> StatusGreen
        r.status == Status.YELLOW -> StatusAmber
        else -> StatusRed
    }
    ClubCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(r.ip, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Chip(IpScannerLogic.statusLabel(r) + " · " + IpScannerLogic.latencyLabel(r), color)
            }
            val sub = listOfNotNull(r.cdnType, r.domain, r.certDomain?.let { "cert: $it" }).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, color = TextTertiary, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopy) { Text("کپی", fontSize = 12.sp) }
                if (r.status == Status.GREEN && IpScannerLogic.reachable(r)) {
                    OutlinedButton(onClick = onSpoof) { Text("قرار دادن در جعل SNI", fontSize = 12.sp) }
                }
            }
        }
    }
}
