package com.helboy.vpnclub.ui.proto.engine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Speed
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
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.data.tools.TelegramProxyItem
import dev.valid7996.narcicgeteway.data.tools.TelegramProxyRepository
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * MTProto proxies for Telegram: the built-in list, a refresh from a public channel, a latency test and one tap to
 * open a proxy in Telegram. Network work runs in the repository's own background dispatchers and is cancelled when
 * the page is left.
 */
@Composable
fun TelegramProxyPage(onBack: () -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val builtIn = remember { TelegramProxyRepository.getInitialProxies() }
    var proxies by remember { mutableStateOf(builtIn) }
    var tested by remember { mutableStateOf(setOf<String>()) }
    var channel by remember { mutableStateOf(TelegramLogic.DEFAULT_CHANNEL) }
    var fetching by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeBad by remember { mutableStateOf(false) }
    var fetchJob by remember { mutableStateOf<Job?>(null) }
    var testJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            fetchJob?.cancel()
            testJob?.cancel()
        }
    }

    fun refresh(target: String) {
        if (fetching) return
        val name = TelegramLogic.cleanChannel(target).ifBlank { TelegramLogic.DEFAULT_CHANNEL }
        if (!TelegramLogic.isValidChannel(name)) {
            notice = "نام کانال درست نیست. فقط حرف انگلیسی، عدد و _ (بین ۵ تا ۳۲ نویسه) مجاز است."
            noticeBad = true
            return
        }
        channel = name
        fetching = true
        notice = null
        fetchJob = scope.launch {
            try {
                val list = TelegramProxyRepository.fetchOnlineProxies(channel = name, limit = 45)
                tested = emptySet()
                proxies = list
                val fresh = TelegramLogic.fetchedNew(list, builtIn)
                if (fresh == 0) {
                    notice = "از @$name چیزی دریافت نشد؛ فهرست آماده نمایش داده می‌شود. اینترنت یا VPN را بررسی کنید و دوباره امتحان کنید."
                    noticeBad = true
                } else {
                    notice = "${fresh.fa()} پروکسی تازه از @$name دریافت شد."
                    noticeBad = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                notice = "دریافت فهرست ناموفق بود. اینترنت را بررسی و دوباره امتحان کنید."
                noticeBad = true
            } finally {
                fetching = false
            }
        }
    }

    fun testAll() {
        if (testing || proxies.isEmpty()) return
        testing = true
        testJob = scope.launch {
            try {
                val res = TelegramProxyRepository.pingAll(proxies)
                tested = tested + res.map { it.id }
                proxies = res
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                notice = "تست پینگ ناموفق بود."
                noticeBad = true
            } finally {
                testing = false
            }
        }
    }

    fun testOne(p: TelegramProxyItem) {
        proxies = proxies.map { if (it.id == p.id) it.copy(isTesting = true) else it }
        scope.launch {
            val res = try {
                TelegramProxyRepository.pingSingle(p)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                p.copy(pingMs = 0L, isTesting = false)
            }
            tested = tested + p.id
            proxies = proxies.map { if (it.id == p.id) res else it }
        }
    }

    ProtoPage(title = "پروکسی تلگرام", onBack = onBack, scroll = false) {
        Text(
            "پروکسی‌های MTProto برای تلگرام. «باز کردن» پروکسی را مستقیم در تلگرام اضافه می‌کند. این پروکسی‌ها عمومی‌اند؛ برای چیز حساس به آنها تکیه نکنید.",
            color = TextSecondary, fontSize = 12.sp,
        )
        ClubCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtoField(
                    label = "کانال تلگرام منبع", value = channel, onValueChange = { channel = it },
                    placeholder = "ProxyMTProto یا @ProxyMTProto", enabled = !fetching, tag = "proto_tg_channel",
                )
                ProtoChoice(
                    title = "کانال‌های پیشنهادی",
                    options = TelegramLogic.QUICK_CHANNELS.map { it to "@$it" },
                    selected = TelegramLogic.cleanChannel(channel),
                    onSelect = { channel = it; refresh(it) },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProtoButton("دریافت فهرست", Icons.Rounded.CloudDownload, { refresh(channel) },
                        modifier = Modifier.weight(1f), running = fetching, tag = "proto_tg_refresh")
                    ProtoButton("تست پینگ", Icons.Rounded.Speed, { testAll() },
                        modifier = Modifier.weight(1f), running = testing, enabled = proxies.isNotEmpty(), tag = "proto_tg_ping_all")
                }
                notice?.let {
                    Text(it, color = if (noticeBad) StatusAmber else StatusGreen, fontSize = 12.sp, modifier = Modifier.testTag("proto_tg_notice"))
                }
            }
        }
        if (proxies.isEmpty()) {
            ProtoEmpty("فهرست خالی است", "از «دریافت فهرست» یک کانال را بخوانید.")
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(TelegramLogic.summary(proxies), color = TextSecondary, fontSize = 12.sp)
                OutlinedButton(onClick = {
                    platform.copyToClipboard(TelegramLogic.allLinks(proxies))
                    platform.toast("همه‌ی پروکسی‌ها کپی شدند")
                }) { Text("کپی همه", fontSize = 12.sp) }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag("proto_tg_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(proxies, key = { it.id }) { p -> ProxyRow(p, p.id in tested, onTest = { testOne(p) },
                    onOpen = { platform.openUri(p.tgUri) },
                    onCopy = { platform.copyToClipboard(p.webUri); platform.toast("لینک پروکسی کپی شد") }) }
            }
        }
    }
}

@Composable
private fun ProxyRow(p: TelegramProxyItem, tested: Boolean, onTest: () -> Unit, onOpen: () -> Unit, onCopy: () -> Unit) {
    val band = TelegramLogic.band(p, tested)
    val color = when (band) {
        TelegramLogic.Band.Fast -> StatusGreen
        TelegramLogic.Band.Medium -> StatusAmber
        TelegramLogic.Band.Slow, TelegramLogic.Band.Failed -> StatusRed
        TelegramLogic.Band.Untested -> TextTertiary
    }
    ClubCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.title.ifBlank { "MTProto" }, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                Chip(if (p.isTesting) "در حال تست…" else TelegramLogic.pingLabel(p, tested), color)
            }
            Text("${p.server}:${p.port}", color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.testTag("proto_tg_open_${p.id}")) { Text("باز کردن در تلگرام", fontSize = 12.sp) }
                OutlinedButton(onClick = onCopy) { Text("کپی", fontSize = 12.sp) }
                OutlinedButton(onClick = onTest, enabled = !p.isTesting) { Text("تست", fontSize = 12.sp) }
            }
        }
    }
}
