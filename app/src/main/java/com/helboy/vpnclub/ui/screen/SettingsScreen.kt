package com.helboy.vpnclub.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.corex.AppSettings
import com.helboy.vpnclub.corex.ConnectionMode
import com.helboy.vpnclub.corex.DiagLogger
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.theme.DarkBg
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** نام جدید ریپو؛ اگر هنوز تغییر نکرده، نام قدیم امتحان می‌شود. */
private val REPOS = listOf("HELBOYCODER/vpn-club")

/** تنظیمات: پروفایل شبکه، تونل تفکیکی، تعویض خودکار، لاگ تشخیصی، به‌روزرسانی. */
@Composable
fun SettingsScreen(settings: AppSettings) {
    val ctx = LocalContext.current
    var carrier by remember { mutableStateOf(settings.carrierMode) }
    var connectionMode by remember { mutableStateOf(settings.connectionMode) }
    var theme by remember { mutableStateOf(settings.themeMode) }
    var style by remember { mutableStateOf(settings.buttonStyle) }
    var fx by remember { mutableStateOf(settings.skinEffects) }
    var bypassIran by remember { mutableStateOf(settings.bypassIran) }
    var autoLaunch by remember { mutableStateOf(settings.autoConnectOnLaunch) }
    var showApps by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var bypassCount by remember { mutableStateOf(settings.bypassApps.size) }
    var updateText by remember { mutableStateOf("") }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("تنظیمات", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        ClubCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("روش‌های اتصال", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("همه روش‌ها داخل خود Hellboy انتخاب می‌شوند. دکمه اصلی برنامه همان دکمه اتصال است.",
                    color = TextSecondary, fontSize = 12.sp)
                Text("اتصال‌های Xray", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConnectionMode.xrayModes.forEach { mode ->
                        FilterChip(
                            selected = connectionMode == mode,
                            onClick = { connectionMode = mode; VpnController.setConnectionMode(mode) },
                            label = { Text(mode.displayName, fontSize = 12.sp) }
                        )
                    }
                }
                Text("اتصال‌های بومی (موتورهای منتقل‌شده)", color = NeonCyan,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConnectionMode.nativeModes.take(4).forEach { mode ->
                        FilterChip(
                            selected = connectionMode == mode,
                            onClick = { connectionMode = mode; VpnController.setConnectionMode(mode) },
                            label = { Text(mode.displayName, fontSize = 12.sp) }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConnectionMode.nativeModes.drop(4).forEach { mode ->
                        FilterChip(
                            selected = connectionMode == mode,
                            onClick = { connectionMode = mode; VpnController.setConnectionMode(mode) },
                            label = { Text(mode.displayName, fontSize = 12.sp) }
                        )
                    }
                }
                Text("تغییر روش، اتصال قبلی را قطع می‌کند. دکمه صفحه خانه روش انتخابی را اجرا می‌کند.",
                    color = TextTertiary, fontSize = 11.sp)
                if (connectionMode.isNative) {
                    var killSwitch by remember { mutableStateOf(settings.nativeKillSwitch) }
                    SwitchRow("قطع اینترنت هنگام افت تونل", "Kill Switch در موتورهای بومی",
                        killSwitch) { killSwitch = it; settings.nativeKillSwitch = it }
                }
            }
        }

        ClubCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ظاهر", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("تم", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("system" to "خودکار", "light" to "روز", "dark" to "شب").forEach { (id, label) ->
                        FilterChip(selected = theme == id, onClick = {
                            theme = id; settings.themeMode = id; com.helboy.vpnclub.ui.theme.ThemeState.mode = id
                        }, label = { Text(label, fontSize = 12.sp) })
                    }
                }
                SwitchRow("قطره‌های خون و آتش", "جلوه‌ی متحرک روی دیوار پس‌زمینه", fx) {
                    fx = it; settings.skinEffects = it; com.helboy.vpnclub.ui.theme.ThemeState.effects = it
                }
                Text("دکمه‌ی اتصال", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("hellboy" to "چهره‌ی هلبوی", "orb" to "گوی ذرات").forEach { (id, label) ->
                        FilterChip(selected = style == id, onClick = { style = id; settings.buttonStyle = id; com.helboy.vpnclub.ui.theme.ThemeState.buttonStyle = id },
                            label = { Text(label, fontSize = 12.sp) })
                    }
                }
            }
        }

        ClubCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("پروفایل شبکه", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("روش عبور از فیلتر برای اپراتور شما. «خودکار» همه را به‌ترتیب امتحان می‌کند.",
                    color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("auto" to "خودکار", "mci" to "همراه اول", "irancell" to "ایرانسل", "plain" to "مستقیم")
                        .forEach { (id, label) ->
                            FilterChip(
                                selected = carrier == id,
                                onClick = { carrier = id; settings.carrierMode = id },
                                label = { Text(label, fontSize = 12.sp) },
                            )
                        }
                }
            }
        }

        // سبک اتصال / DNS / قوانین مسیریابی (ZeroNet)
        ConnectionCard(VpnController.state.collectAsState().value)

        ClubCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SwitchRow("ترافیک داخل ایران مستقیم", "خاموش = تونل کامل، همه‌ی ترافیک و همه‌ی اپ‌ها از VPN (پیش‌فرض)",
                    bypassIran) { bypassIran = it; settings.bypassIran = it }
                SwitchRow("اتصال خودکار هنگام باز شدن", "به‌محض ورود به برنامه وصل می‌شود",
                    autoLaunch) { autoLaunch = it; settings.autoConnectOnLaunch = it }
            }
        }

        ClubCard(Modifier.fillMaxWidth().clickable { showApps = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("تونل تفکیکی (اپ‌های مستثنی)", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("اپ‌هایی که بدون VPN کار کنند، مثل بانک‌ها", color = TextSecondary, fontSize = 12.sp)
                }
                Text(bypassCount.fa(), color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        }

        LanSharingCard(settings)

        ClubCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("عیب‌یابی", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("لاگ جست‌وجوی سرورها، تست کیفیت، اتصال و بازیابی روی همین گوشی ذخیره می‌شود — اگر مشکلی بود «ارسال فایل» را بزنید و بفرستید.",
                    color = TextSecondary, fontSize = 12.sp)
                OutlinedButton(onClick = { showLog = true }) { Text("نمایش لاگ تشخیصی") }
            }
        }

        ClubCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("دربارهٔ برنامه", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Hellboy VPN نسخهٔ ${versionName(ctx)}", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            updateText = "در حال بررسی…"
                            scope.launch {
                                val r = checkUpdate(versionName(ctx))
                                updateText = r.first; updateUrl = r.second
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkBg),
                    ) { Text("بررسی به‌روزرسانی") }
                    updateUrl?.let { u ->
                        TextButton(onClick = {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("دانلود", color = NeonCyan) }
                    }
                }
                if (updateText.isNotEmpty()) Text(updateText, color = TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showApps) AppPickerDialog(ctx, settings, onClose = { showApps = false; bypassCount = settings.bypassApps.size })
    if (showLog) LogDialog(onClose = { showLog = false })
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = TextTertiary, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private data class AppItem(val pkg: String, val label: String)

@Composable
private fun AppPickerDialog(ctx: Context, settings: AppSettings, onClose: () -> Unit) {
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var selected by remember { mutableStateOf(settings.bypassApps) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.pkg != ctx.packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = { settings.bypassApps = selected; onClose() }) { Text("ذخیره", color = NeonCyan) } },
        dismissButton = { TextButton(onClick = onClose) { Text("لغو") } },
        title = { Text("اپ‌های خارج از تونل") },
        text = {
            if (apps.isEmpty()) Text("در حال بارگذاری…")
            else LazyColumn(Modifier.height(380.dp)) {
                items(apps, key = { it.pkg }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = if (a.pkg in selected) selected - a.pkg else selected + a.pkg
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = a.pkg in selected, onCheckedChange = {
                            selected = if (a.pkg in selected) selected - a.pkg else selected + a.pkg
                        })
                        Text(a.label, fontSize = 14.sp)
                    }
                }
            }
        },
    )
}

@Composable
private fun LogDialog(onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var lines by remember { mutableStateOf(DiagLogger.tail(400)) }
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(DiagLogger.fullText())); copied = true }) {
                    Text(if (copied) "کپی شد ✓" else "کپی کامل", color = NeonCyan)
                }
                TextButton(onClick = { shareLogFile(ctx) }) { Text("ارسال فایل", color = NeonCyan) }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { DiagLogger.clear(); lines = emptyList() }) { Text("پاک‌کردن", color = StatusRed) }
                TextButton(onClick = onClose) { Text("بستن") }
            }
        },
        title = { Text("لاگ تشخیصی · ${lines.size} خط آخر") },
        text = {
            Column(Modifier.height(380.dp).verticalScroll(rememberScrollState(Int.MAX_VALUE))) {
                Text(
                    if (lines.isEmpty()) "لاگی ثبت نشده." else lines.joinToString("\n"),
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                )
            }
        },
    )
}

/** فایل کامل لاگ (تا ۳۰۰۰ خط، بدون اسرار) برای ارسال در تلگرام/ایمیل. */
private fun shareLogFile(ctx: Context) {
    val f = DiagLogger.exportFile(ctx) ?: return
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".logs", f)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
        .putExtra(android.content.Intent.EXTRA_SUBJECT, "Hellboy VPN log")
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(android.content.Intent.createChooser(send, "ارسال لاگ").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun versionName(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
} catch (_: Exception) { "?" }

/** (پیام، لینک دانلود) */
private suspend fun checkUpdate(current: String): Pair<String, String?> = withContext(Dispatchers.IO) {
    for (repo in REPOS) {
        try {
            val body = com.helboy.vpnclub.corex.NetHttp.getText(
                "https://api.github.com/repos/$repo/releases/latest", "UPDATE")
            val o = JSONObject(body)
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) continue
            val tag = o.optString("tag_name").removePrefix("v")
            val url = o.optString("html_url")
            return@withContext if (tag.isEmpty()) "نسخه‌ای یافت نشد" to null
                else if (com.helboy.vpnclub.corex.ReleaseVersion.isNewer(tag, current)) "نسخهٔ جدید $tag موجود است" to url
                else "نسخهٔ شما به‌روز است ($current)" to null
        } catch (_: Exception) {}
    }
    "بررسی ناموفق بود — اینترنت را چک کنید" to null
}
