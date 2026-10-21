package com.helboy.vpnclub.ui.proto.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoNavRow
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.BuildConfig
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** About, privacy summary, licences and support links in one page. */
@Composable
fun AboutPage(onBack: () -> Unit) {
    val platform = LocalPlatform.current
    var showAllCredits by remember { mutableStateOf(false) }

    // Reading the id may create and store it the first time, so it is done off the main thread.
    val deviceId by produceState<String?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { ProtoEnv.repo.nrgDeviceId() }.getOrNull() ?: "" }
    }

    ProtoPage(title = "دربارهٔ برنامه", onBack = onBack) {
        ProtoSection(title = "Hellboy VPN") {
            Text(
                "نسخه ${BuildConfig.VERSION_NAME.fa()} (ساخت ${BuildConfig.VERSION_CODE.fa()})",
                color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("proto_about_version"),
            )
            Text(
                "کلاینت پیشرفتهٔ چندهسته‌ای فیلترشکن و تونل امن؛ پشتیبانی از V2Ray (VLESS، VMess، Trojan، Shadowsocks)، " +
                    "Psiphon، Tor، DNS tunnel، WireGuard، OpenConnect، IKEv2 و بیشتر.",
                color = TextSecondary, fontSize = 12.sp,
            )
        }

        ProtoNavRow("کد منبع و گیت‌هاب", "github.com/HELBOYCODER/vpn-club", { platform.openUri(REPO_URL) }, icon = Icons.Rounded.Code, tag = "proto_about_repo")
        ProtoNavRow("گزارش مشکل یا پیشنهاد", "در بخش Issues مخزن", { platform.openUri(ISSUES_URL) }, icon = Icons.Rounded.Description)
        ProtoNavRow("نسخه‌های منتشرشده", "دانلود و یادداشت‌های انتشار", { platform.openUri(RELEASES_URL) }, icon = Icons.Rounded.Policy)

        ProtoSection(title = "شناسهٔ دستگاه", hint = "برای محدود کردن یک کانفیگ قفل‌شده به این دستگاه. جایی ارسال نمی‌شود.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val id = deviceId
                Text(
                    when {
                        id == null -> "در حال خواندن…"
                        id.isEmpty() -> "خوانده نشد"
                        else -> id
                    },
                    color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                    modifier = Modifier.weight(1f).testTag("proto_about_device_id"),
                )
                IconButton(
                    enabled = !id.isNullOrEmpty(), modifier = Modifier.testTag("proto_about_copy_id"),
                    onClick = { id?.let { platform.copyToClipboard(it); platform.toast("شناسه کپی شد") } },
                ) { Icon(Icons.Rounded.ContentCopy, "کپی شناسه", tint = NeonCyan) }
            }
        }

        ProtoSection(title = "حریم خصوصی", hint = "خلاصهٔ کوتاه و صادقانه: بدون حساب کاربری، بدون سرور ما، بدون آنالیتیکس.") {
            PRIVACY.forEach { (title, body) ->
                Column(Modifier.padding(vertical = 2.dp)) {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(body, color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        ProtoSection(title = "مجوز (GPL-3.0)") {
            Chip("GPL-3.0", NeonCyan)
            Text(LICENSE_NOTE, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.testTag("proto_about_license"))
        }

        ProtoSection(title = "اجزای شخص ثالث", hint = THIRD_PARTY_NOTE) {
            val shown = if (showAllCredits) CREDITS else CREDITS.take(5)
            shown.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().let { m -> if (c.url != null) m.clickable { platform.openUri(c.url) } else m }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, color = TextPrimary, fontSize = 13.sp)
                        Text(c.use, color = TextTertiary, fontSize = 11.sp)
                    }
                    Chip(c.license, NeonCyan)
                }
            }
            Text(
                if (showAllCredits) "نمایش کمتر" else "نمایش همهٔ ${CREDITS.size.fa()} مورد",
                color = NeonCyan, fontSize = 12.sp,
                modifier = Modifier.clickable { showAllCredits = !showAllCredits }.padding(vertical = 4.dp).testTag("proto_about_more"),
            )
        }
    }
}
