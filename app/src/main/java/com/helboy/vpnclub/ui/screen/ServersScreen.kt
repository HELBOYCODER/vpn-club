package com.helboy.vpnclub.ui.screen

import com.helboy.vpnclub.ui.theme.Palette
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.helboy.vpnclub.corex.ConfigEditor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.corex.IranVariants
import com.helboy.vpnclub.corex.ServerEntry
import com.helboy.vpnclub.corex.Task
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.components.ActionButton
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.NeonIndigo
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary

/** سرورها: ۱۰ سرور سالم + دکمه‌ی «ری‌اسکن» (لیست تازه) و «اینجکت IP» (تزریق IP تمیز). لمس سرور = اتصال. */
@Composable
fun ServersScreen(
    ui: VpnController.UiState,
    onConnect: (ServerEntry) -> Unit,
    onRescan: () -> Unit,
    onInject: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var protocol by rememberSaveable { mutableStateOf("") }
    var favorites by remember { mutableStateOf(VpnController.settings().favoriteServers) }
    val filtered = ui.servers.filter { s ->
        (!favoritesOnly || s.favoriteKey in favorites) && (protocol.isEmpty() || s.config.scheme == protocol) &&
        (search.isBlank() || listOf(s.config.tag, s.config.host, s.country, s.config.scheme)
            .any { it.contains(search.trim(), ignoreCase = true) })
    }.sortedByDescending { it.favoriteKey in favorites }
    var actionsFor by remember { mutableStateOf<ServerEntry?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var showAddSub by remember { mutableStateOf(false) }
    if (showAddSub) AddSubDialog(onClose = { showAddSub = false })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer); if (n < 0) break
                            require(out.size() + n <= 1_048_576) { "فایل باید کوچک‌تر از یک مگابایت باشد" }
                            out.write(buffer, 0, n)
                        }
                        out.toString("UTF-8")
                    } ?: error("فایل باز نشد")
                }
                VpnController.importConfigs(text)
            } catch (e: Exception) { VpnController.notify(e.message ?: "خواندن فایل ناموفق بود") }
        }
    }
    val clipboard = LocalClipboardManager.current
    actionsFor?.let { s -> ServerActionsDialog(s, onClose = { actionsFor = null }) }
    if (showImport) ImportConfigDialog(onClose = { showImport = false })
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("سرورهای منتخب", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("${ui.servers.size.fa()} سرور سالم · لمس = اتصال · «اینجکت» = IP منتخب روی همان سرور", color = TextSecondary, fontSize = 12.sp)
            Text(if (ui.selectedIp.isEmpty()) "IP منتخب: — (از تب اسکنر)" else "IP منتخب: ${ui.selectedIp}",
                color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("ری‌اسکن سرورها", Icons.Rounded.Refresh,
                    running = ui.task == Task.RESCAN, enabled = !ui.testing,
                    accent = NeonCyan, onClick = onRescan, modifier = Modifier.weight(1f))
                ActionButton("اینجکت به همه", Icons.Rounded.Vaccines,
                    running = ui.task == Task.INJECT, enabled = !ui.testing && ui.servers.isNotEmpty(),
                    accent = StatusAmber, onClick = onInject, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("افزودن کانفیگ", Icons.Rounded.Add, running = false, enabled = !ui.testing,
                    accent = NeonIndigo, onClick = { showImport = true }, modifier = Modifier.weight(1f))
                ActionButton("کپی ۱۰ برتر", Icons.Rounded.ContentCopy, running = false, enabled = ui.servers.isNotEmpty(),
                    accent = NeonIndigo, onClick = {
                        val links = ui.servers.sortedByDescending { it.score }.take(10).joinToString("\n") { ConfigEditor.shareLink(it) }
                        clipboard.setText(AnnotatedString(links))
                        VpnController.notify("لینک ${minOf(10, ui.servers.size)} سرور برتر کپی شد (با IP تمیز)")
                    }, modifier = Modifier.weight(1f))
            }
            androidx.compose.material3.TextButton(onClick = { filePicker.launch(arrayOf("text/*", "application/octet-stream")) }, enabled = !ui.testing) {
                Text("وارد کردن فایل کانفیگ / ساب Base64")
            }
            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                label = { Text("جست‌وجو: نام، کشور، آدرس یا پروتکل") }, singleLine = true)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(favoritesOnly, { favoritesOnly = !favoritesOnly }, label = { Text("علاقه‌مندی‌ها") })
                FilterChip(protocol.isEmpty(), { protocol = "" }, label = { Text("همه") })
                ui.servers.map { it.config.scheme }.distinct().sorted().forEach { id ->
                    FilterChip(protocol == id, { protocol = id }, label = { Text(id.uppercase()) })
                }
            }
            TaskLine(ui)
        }

        item(key = "warp") { WarpCard(ui) }
        item(key = "serverless") { ServerlessCard(ui) }

        // ── ساب‌های من (پنل‌های BPB / Zeus نصب‌شده + دستی) ──
        item(key = "subs-h") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ساب‌های من · ${ui.mySubs.size.fa()}", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { showAddSub = true }) { Text("+ افزودن ساب", color = NeonCyan) }
            }
            ServerSourceSelector(ui)
            if (ui.mySubs.isEmpty()) Text("از «تنظیمات ← پنل شخصی روی کلادفلر» پنل Netra یا Zeus نصب کنید؛ لینک ساب خودکار اینجا می‌آید.",
                color = TextTertiary, fontSize = 12.sp)
        }
        for (sub in ui.mySubs) item(key = "sub-" + sub.id) { MySubCard(sub, ui) }

        if (ui.servers.isEmpty()) {
            item {
                ClubCard(Modifier.fillMaxWidth()) {
                    Text("لیست خالی است. «ری‌اسکن سرورها» را بزنید تا ۱۰ سرور سالم از ساب‌ها پیدا شود.",
                        color = TextSecondary, fontSize = 13.sp)
                }
            }
        }

        if (filtered.isEmpty() && ui.servers.isNotEmpty()) item {
            Text("سروری با این فیلتر پیدا نشد؛ جست‌وجو یا فیلتر را تغییر دهید.", color = TextSecondary)
        }
        // دسته‌بندی: قابل اینجکت (کلادفلر) ← CDN ← Reality ← مستقیم
        val order = listOf("کلادفلر · قابل اینجکت", "CDN (احتمالاً قابل اینجکت)", "Reality", "مستقیم")
        val groups = filtered.groupBy { it.category }.toList().sortedBy { order.indexOf(it.first).let { i -> if (i < 0) 99 else i } }
        var rank = 0
        for ((cat, list) in groups) {
            item(key = "h-$cat") {
                Text("$cat · ${list.size.fa()}", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp))
            }
            for (s in list) {
                rank++
                val r = rank
                item(key = s.serverKey) {
                    ServerRow(rank = r, s = s,
                        active = ui.connected && ui.current?.serverKey == s.serverKey,
                        selectedIp = ui.selectedIp,
                        busy = ui.testing,
                        onClick = { if (!ui.busyConnecting) onConnect(s) },
                        onInject = { VpnController.injectInto(s) },
                        onMore = { actionsFor = s }, favorite = s.favoriteKey in favorites,
                        onFavorite = {
                            favorites = if (s.favoriteKey in favorites) favorites - s.favoriteKey else favorites + s.favoriteKey
                            VpnController.settings().favoriteServers = favorites
                        })
                }
            }
        }
    }
}

/** خط وضعیت کار در حال اجرا + پیام نتیجه‌ی آخرین کار. */
@Composable
fun TaskLine(ui: VpnController.UiState) {
    if (ui.testing) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = NeonCyan, trackColor = Palette.track)
        Text(ui.taskMsg, color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    } else if (ui.message.isNotEmpty()) {
        Text(ui.message, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ServerRow(
    rank: Int, s: ServerEntry, active: Boolean, selectedIp: String, busy: Boolean,
    onClick: () -> Unit, onInject: () -> Unit, onMore: () -> Unit, favorite: Boolean, onFavorite: () -> Unit,
) {
    ClubCard(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = if (active) StatusGreen else Palette.border,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(30.dp).background(if (active) StatusGreen else Palette.cardElevated, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(rank.fa(), color = if (active) Color.Black else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            Text(ServerEntry.flagOf(s.country), fontSize = 26.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.config.tag.ifEmpty { s.config.host }, color = TextPrimary, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Chip(s.config.scheme.uppercase(), NeonIndigo)
                    when {
                        s.injected && s.canInject -> Chip("IP تمیز ${s.config.dialHost}", NeonCyan)
                        s.canInject -> Chip("کلادفلر · قابل اینجکت", NeonCyan.copy(alpha = 0.7f))
                        else -> Chip("بدون اینجکت", TextTertiary)
                    }
                    IranVariants.byId(s.variantId)?.takeIf { it.id != "plain" }?.let { Chip(it.label, StatusAmber) }
                    if (s.source.startsWith("sub-")) Chip("ساب من", NeonCyan)
                    if (s.source == "warp") Chip("WARP", StatusAmber)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (s.latencyMs > 0) "${s.latencyMs.fa()} ms" else "بدون پاسخ",
                    color = if (s.latencyMs > 0) StatusGreen else StatusAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (active) Text("متصل", color = StatusGreen, fontSize = 11.sp)
                IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        if (favorite) "حذف از علاقه‌مندی‌ها" else "افزودن به علاقه‌مندی‌ها", tint = if (favorite) StatusAmber else TextTertiary)
                }
                IconButton(onClick = onMore, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.MoreVert, "کپی · QR · ویرایش", tint = TextSecondary)
                }
                // اینجکت دستی IP منتخب روی همین سرور
                if (s.canInjectManual) {
                    androidx.compose.material3.TextButton(onClick = onInject, enabled = !busy && selectedIp.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text(if (s.config.dialHost == selectedIp) "اینجکت‌شده" else "اینجکت", color = NeonCyan, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
