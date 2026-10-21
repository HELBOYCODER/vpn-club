package com.helboy.vpnclub.ui.proto.editors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEmpty
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoError
import com.helboy.vpnclub.ui.proto.ProtoLoading
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.core.tor.TorBridges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * The built-in Tor bridges by type (obfs4, Snowflake, meek, WebTunnel, …): pick the type, tick the bridges to use,
 * and ping them. The selection is stored in the app's Tor settings.
 */
@Composable
fun TorBridgesPage(onBack: () -> Unit) {
    val settingsRepo = remember { runCatching { ProtoEnv.settings }.getOrNull() }
    ProtoPage(title = "پل‌های تور", onBack = onBack, scroll = false) {
        if (settingsRepo == null) {
            ProtoError("تنظیمات برنامه در دسترس نیست؛ بعداً دوباره امتحان کنید.")
        } else {
            BridgesBody(settingsRepo)
        }
    }
}

@Composable
private fun ColumnScope.BridgesBody(settingsRepo: dev.valid7996.narcicgeteway.data.settings.SettingsRepository) {
    val s by settingsRepo.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val transport = s.torBridgeTransport
    var attempt by remember { mutableStateOf(0) }
    var state by remember(transport, attempt) { mutableStateOf<BridgeLoad>(BridgeLoad.Loading) }
    val pings = remember(transport) { mutableStateMapOf<String, Long>() }
    var pingingAll by remember(transport) { mutableStateOf(false) }

    LaunchedEffect(transport, attempt) {
        state = BridgeLoad.Loading
        state = try {
            BridgeLoad.Ready(TorBridges.load(transport))
        } catch (e: Exception) {
            BridgeLoad.Failed
        }
    }

    ProtoChoice(
        title = "نوع پل",
        options = TorBridgeSettings.BUILT_IN_TRANSPORTS.map { it to TorBridgeSettings.transportLabel(it) },
        selected = transport,
        onSelect = { t -> settingsRepo.update { it.copy(torBridgeTransport = t) } },
    )
    ProtoSection(
        hint = "انتخاب کنید کدام پل‌ها استفاده شوند. اگر هیچ‌کدام تیک نخورد، ${TorBridgeSettings.DEFAULT_COUNT.fa()} پل اول به کار می‌رود. " +
            "پینگ، دسترس‌پذیری اولین گام هر پل را نشان می‌دهد — رلهٔ مستقیم، یا نقطهٔ فرانتینگ/بروکر برای Snowflake و meek.",
    ) {
        if (s.torBridgesMode != TorBridgeSettings.MODE_DEFAULT) {
            Text("حالت پل‌ها روی «پل‌های پیش‌فرض» نیست؛ انتخاب‌های اینجا تا آن موقع اثری ندارند.", color = StatusAmber, fontSize = 12.sp,
                modifier = Modifier.testTag("proto_bridges_mode_warning"))
            ProtoOutlined("استفاده از پل‌های پیش‌فرض", { settingsRepo.update { it.copy(torBridgesMode = TorBridgeSettings.MODE_DEFAULT) } }, tag = "proto_bridges_use_default")
        }
        if (transport == "snowflake") {
            Text("Snowflake پل‌ها را از نقطهٔ دیدار می‌گیرد و فهرست زیر را برای اتصال نادیده می‌گیرد.", color = TextSecondary, fontSize = 12.sp)
        }
    }

    when (val st = state) {
        BridgeLoad.Loading -> ProtoLoading("در حال خواندن فهرست پل‌ها…")
        BridgeLoad.Failed -> ProtoError("خواندن فهرست پل‌های داخلی ممکن نشد.", onRetry = { attempt++ })
        is BridgeLoad.Ready -> {
            val bridges = st.bridges.distinctBy { it.line }
            if (bridges.isEmpty()) {
                ProtoEmpty("پلی نیست", "برای این نوع پل، فهرست داخلی‌ای همراه برنامه نیست.")
            } else {
                val lines = bridges.map { it.line }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProtoOutlined(
                        text = "پینگ همه",
                        onClick = {
                            pingingAll = true
                            scope.launch {
                                bridges.map { b ->
                                    pings[b.line] = PING_RUNNING
                                    launch(Dispatchers.Default) { pings[b.line] = runCatching { TorBridges.ping(b, s.torSnowflakeRendezvous) }.getOrDefault(-1L) }
                                }.joinAll()
                                pingingAll = false
                            }
                        },
                        enabled = !pingingAll,
                        tag = "proto_bridges_ping_all",
                    )
                    ProtoOutlined("انتخاب همه", { settingsRepo.update { it.copy(torEnabledBridges = TorBridgeSettings.selectAll(it.torEnabledBridges, lines)) } }, tag = "proto_bridges_all")
                    ProtoOutlined("پیش‌فرض", { settingsRepo.update { it.copy(torEnabledBridges = TorBridgeSettings.reset(it.torEnabledBridges, lines)) } }, tag = "proto_bridges_reset")
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("proto_bridges_list")) {
                    items(bridges, key = { it.line }) { b ->
                        BridgeRow(
                            address = b.address,
                            line = b.line,
                            checked = TorBridgeSettings.isUsed(s.torEnabledBridges, b.line, lines),
                            ping = pings[b.line],
                            onToggle = { settingsRepo.update { it.copy(torEnabledBridges = TorBridgeSettings.toggle(it.torEnabledBridges, b.line, lines)) } },
                            onPing = {
                                pings[b.line] = PING_RUNNING
                                scope.launch(Dispatchers.Default) { pings[b.line] = runCatching { TorBridges.ping(b, s.torSnowflakeRendezvous) }.getOrDefault(-1L) }
                            },
                        )
                    }
                }
            }
        }
    }
}

private sealed interface BridgeLoad {
    data object Loading : BridgeLoad
    data object Failed : BridgeLoad
    data class Ready(val bridges: List<TorBridges.Bridge>) : BridgeLoad
}

private const val PING_RUNNING = -2L

@Composable
private fun BridgeRow(address: String, line: String, checked: Boolean, ping: Long?, onToggle: () -> Unit, onPing: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, modifier = Modifier.testTag("proto_bridge_check"))
        Column(Modifier.weight(1f)) {
            Text(address, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, color = TextTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (ping) {
            PING_RUNNING -> CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp, color = NeonCyan)
            null -> ProtoOutlined("پینگ", onPing, tag = "proto_bridge_ping")
            -1L -> Text("در دسترس نیست", color = StatusRed, fontSize = 12.sp)
            else -> Text("${ping.fa()} ms", color = if (ping < 400) StatusGreen else if (ping < 1200) StatusAmber else StatusRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
