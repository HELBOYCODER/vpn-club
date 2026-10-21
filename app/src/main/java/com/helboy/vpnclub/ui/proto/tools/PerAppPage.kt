package com.helboy.vpnclub.ui.proto.tools

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEmpty
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoError
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoLoading
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import com.helboy.vpnclub.ui.theme.BorderDark
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.ui.platform.InstalledApp
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface AppsLoad {
    data object Loading : AppsLoad
    data class Ready(val apps: List<InstalledApp>) : AppsLoad
    data class Failed(val reason: String) : AppsLoad
}

/**
 * Per-app proxy: pick which apps use the tunnel. The list is read off the main thread, icons are loaded one by one
 * as rows scroll into view, and every change is stored in the engine settings at once (reconnect to apply).
 */
@Composable
fun PerAppPage(onBack: () -> Unit) {
    val platform = LocalPlatform.current
    val store = ProtoEnv.settings
    val settings by store.settings.collectAsState()

    if (!platform.supportsPerAppProxy) {
        ProtoPage(title = "پروکسی برای هر برنامه", onBack = onBack) {
            ProtoEmpty(
                "در این دستگاه پشتیبانی نمی‌شود",
                "مسیریابی جداگانه برای هر برنامه یک قابلیت اندروید است. روی این پلتفرم تونل کل سیستم را می‌گیرد.",
            )
        }
        return
    }

    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    // apps picked before this page opened stay on top while the person ticks more, so rows do not jump under the finger
    val pinned = remember { settings.perAppPackages }

    val load by produceState<AppsLoad>(AppsLoad.Loading, reload) {
        value = AppsLoad.Loading
        value = withContext(Dispatchers.IO) {
            try {
                AppsLoad.Ready(platform.installedApps()) as AppsLoad
            } catch (e: Exception) {
                AppsLoad.Failed(e.message?.take(120) ?: "خطای ناشناخته")
            }
        }
    }

    val mode = perAppMode(settings.perAppProxyEnabled, settings.perAppBypassMode)
    val selected = settings.perAppPackages

    fun setSelection(next: Set<String>) = store.update { it.copy(perAppPackages = next) }

    val iconCache = remember { LruCache<String, ImageBitmap>(300) }

    ProtoPage(title = "پروکسی برای هر برنامه", onBack = onBack, scroll = false) {
        when (val l = load) {
            AppsLoad.Loading -> ProtoLoading("در حال خواندن برنامه‌های نصب‌شده…")
            is AppsLoad.Failed -> ProtoError("فهرست برنامه‌ها خوانده نشد (${l.reason}). دسترسی را بررسی کنید و دوباره امتحان کنید.", onRetry = { reload++ })
            is AppsLoad.Ready -> {
                val visible = remember(l.apps, query, showSystem, pinned) { filterApps(l.apps, query, showSystem, pinned) }
                val stale = remember(l.apps, selected) { staleSelection(l.apps, selected) }
                LazyColumn(
                    Modifier.fillMaxSize().testTag("proto_app_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "controls") {
                        ProtoSection(title = "حالت", hint = "تغییرها فوری ذخیره می‌شوند؛ برای اعمال، دوباره وصل شوید.") {
                            ProtoChoice(
                                null,
                                listOf(
                                    PerAppMode.Off.name to "همه برنامه‌ها",
                                    PerAppMode.Include.name to "فقط انتخاب‌شده‌ها",
                                    PerAppMode.Exclude.name to "همه به‌جز انتخاب‌شده‌ها",
                                ),
                                mode.name,
                                { name ->
                                    val (enabled, bypass) = PerAppMode.valueOf(name).toFlags()
                                    store.update { it.copy(perAppProxyEnabled = enabled, perAppBypassMode = bypass) }
                                },
                            )
                            if (mode == PerAppMode.Include && selected.isEmpty()) {
                                Text(
                                    "هیچ برنامه‌ای انتخاب نشده؛ در حالت «فقط انتخاب‌شده‌ها» هیچ ترافیکی از تونل نمی‌گذرد.",
                                    color = StatusAmber, fontSize = 12.sp, modifier = Modifier.testTag("proto_app_warning"),
                                )
                            }
                            ProtoField("جست‌وجو (نام یا بستهٔ برنامه)", query, { query = it }, tag = "proto_app_search")
                            ProtoSwitch("نمایش برنامه‌های سیستمی", showSystem, { showSystem = it })
                        }
                    }
                    item(key = "bulk") {
                        ProtoSection(title = "${selected.size.fa()} برنامه انتخاب شده · ${visible.size.fa()} نمایش داده می‌شود") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ProtoOutlined("همه", { setSelection(selectAll(selected, visible)) }, Modifier.weight(1f), tag = "proto_app_all")
                                ProtoOutlined("هیچ‌کدام", { setSelection(selectNone(selected, visible)) }, Modifier.weight(1f), tag = "proto_app_none")
                                ProtoOutlined("معکوس", { setSelection(invert(selected, visible)) }, Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ProtoOutlined("وارد کردن از کلیپ‌بورد", {
                                    val parsed = parsePackageList(platform.readClipboard().orEmpty())
                                    if (parsed.isEmpty()) platform.toast("کلیپ‌بورد فهرست برنامه ندارد")
                                    else {
                                        setSelection(parsed)
                                        platform.toast("${parsed.size.fa()} برنامه وارد شد")
                                    }
                                }, Modifier.weight(1f))
                                ProtoOutlined("کپی فهرست", {
                                    platform.copyToClipboard(exportPackageList(selected))
                                    platform.toast("فهرست برنامه‌ها کپی شد")
                                }, Modifier.weight(1f), enabled = selected.isNotEmpty())
                            }
                            if (stale.isNotEmpty()) {
                                Text("${stale.size.fa()} برنامهٔ انتخاب‌شده دیگر نصب نیست.", color = TextTertiary, fontSize = 11.sp)
                                ProtoOutlined("پاک کردن موارد نصب‌نشده", { setSelection(selected - stale) })
                            }
                        }
                    }
                    if (visible.isEmpty()) {
                        item(key = "empty") {
                            ProtoEmpty(
                                if (l.apps.isEmpty()) "برنامه‌ای پیدا نشد" else "چیزی پیدا نشد",
                                if (l.apps.isEmpty()) "دستگاه فهرست برنامه‌ها را نداد." else "جست‌وجو یا نمایش برنامه‌های سیستمی را عوض کنید.",
                            )
                        }
                    }
                    items(visible, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            checked = app.packageName in selected,
                            enabled = mode != PerAppMode.Off,
                            cache = iconCache,
                            onToggle = { store.update { s -> s.copy(perAppPackages = toggle(s.perAppPackages, app.packageName)) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, checked: Boolean, enabled: Boolean, cache: LruCache<String, ImageBitmap>, onToggle: () -> Unit) {
    val platform = LocalPlatform.current
    val icon by produceState<ImageBitmap?>(cache.get(app.packageName), app.packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = app.iconPng ?: platform.appIcon(app.packageName)
                    bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                }.getOrNull()
            }?.also { cache.put(app.packageName, it) }
        }
    }
    ClubCard(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onToggle).testTag("proto_app_row"),
        border = if (checked) NeonCyan else BorderDark,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val shape = RoundedCornerShape(9.dp)
            val bmp = icon
            if (bmp != null) {
                Image(bmp, null, Modifier.size(36.dp).clip(shape))
            } else {
                Box(Modifier.size(36.dp).clip(shape).background(BorderDark), contentAlignment = Alignment.Center) {
                    Text(app.label.firstOrNull()?.uppercase() ?: "?", color = TextSecondary, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(app.label, color = if (enabled) TextPrimary else TextTertiary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled, modifier = Modifier.testTag("proto_app_check"))
        }
    }
}
