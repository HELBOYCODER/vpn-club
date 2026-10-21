package com.helboy.vpnclub.ui.proto.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEmpty
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoLoading
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import com.helboy.vpnclub.ui.theme.CardBg
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.core.LogBus
import dev.valid7996.narcicgeteway.core.VpnManager
import dev.valid7996.narcicgeteway.domain.model.LogLevel
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SCOPE_SESSION = "session"
private const val SCOPE_ALL = "all"

/**
 * The live app and core log: level filter, source filter, search, copy, share as a file, clear and an auto-scroll
 * switch. Filtering runs off the main thread; the tail source is attached only while the page is open.
 */
@Composable
fun LogsPage(onBack: () -> Unit) {
    val platform = LocalPlatform.current
    val settings by ProtoEnv.settings.settings.collectAsState()
    val allLines by LogBus.lines.collectAsState()
    val ownTags by LogBus.ownTags.collectAsState()
    val activeKind by VpnManager.activeKind.collectAsState()

    DisposableEffect(Unit) {
        LogBus.attach()
        onDispose { LogBus.detach() }
    }

    val sessionSources = remember(activeKind) { sourcesForKind(activeKind) }
    var scopeChoice by remember { mutableStateOf<String?>(null) } // null = follow the running tunnel, else "all" or a LogSource name
    val effective = scopeChoice ?: if (sessionSources.isNotEmpty()) SCOPE_SESSION else SCOPE_ALL
    var level by remember { mutableStateOf(LogViewLevel.All) }
    var query by remember { mutableStateOf("") }
    var follow by remember { mutableStateOf(true) }

    val sources: Set<LogSource>? = when (effective) {
        SCOPE_ALL -> null
        SCOPE_SESSION -> sessionSources + LogSource.App
        else -> LogSource.entries.firstOrNull { it.name == effective }?.let { setOf(it, LogSource.App) }
    }

    val lines by produceState<List<LogLine>?>(null, allLines, level, query, effective, sessionSources, ownTags) {
        value = withContext(Dispatchers.Default) { filterLog(allLines, level, query, sources, ownTags) }
    }

    val listState = rememberLazyListState()
    val count = lines?.size ?: 0
    LaunchedEffect(count, follow) {
        if (follow && count > 0) listState.scrollToItem(count - 1)
    }

    ProtoPage(
        title = "لاگ‌ها",
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                platform.copyToClipboard(logText(lines.orEmpty()))
                platform.toast("لاگ‌ها کپی شد")
            }, enabled = count > 0, modifier = Modifier.testTag("proto_log_copy")) {
                Icon(Icons.Rounded.ContentCopy, "کپی", tint = TextPrimary)
            }
            IconButton(onClick = {
                platform.shareFiles(listOf(logFileName(System.currentTimeMillis()) to logText(lines.orEmpty()).encodeToByteArray()))
            }, enabled = count > 0, modifier = Modifier.testTag("proto_log_share")) {
                Icon(Icons.Rounded.Share, "اشتراک به‌صورت فایل", tint = TextPrimary)
            }
            IconButton(onClick = { LogBus.clear() }, enabled = allLines.isNotEmpty(), modifier = Modifier.testTag("proto_log_clear")) {
                Icon(Icons.Rounded.DeleteSweep, "پاک کردن", tint = StatusRed)
            }
        },
    ) {
        ProtoField("جست‌وجو در لاگ", query, { query = it }, tag = "proto_log_search")
        ProtoChoice(
            null,
            LogViewLevel.entries.map { it.name to it.label },
            level.name,
            { level = LogViewLevel.valueOf(it) },
        )
        ProtoChoice(
            null,
            buildList {
                if (sessionSources.isNotEmpty()) add(SCOPE_SESSION to "همین تونل")
                add(SCOPE_ALL to "همهٔ منابع")
                LogSource.entries.forEach { add(it.name to it.label) }
            },
            effective,
            { scopeChoice = it },
        )
        ProtoSwitch("دنبال کردن آخرین خط", follow, { follow = it }, tag = "proto_log_follow")
        ProtoChoice(
            "سطح لاگ هسته (از اتصال بعدی)",
            LogLevel.entries.map { it.name to coreLevelLabel(it) },
            settings.logLevel.name,
            { name -> ProtoEnv.settings.update { it.copy(logLevel = LogLevel.valueOf(name)) } },
            hint = "هرچه پایین‌تر باشد لاگ هسته پرحجم‌تر است؛ «خاموش» هیچ لاگی نمی‌نویسد.",
        )
        Text("${count.fa()} از ${allLines.size.fa()} خط", color = TextTertiary, fontSize = 11.sp)

        val shown = lines
        when {
            allLines.isEmpty() -> ProtoEmpty("هنوز لاگی نیست", "با وصل شدن یا استفاده از برنامه، خط‌ها اینجا می‌آیند.")
            shown == null -> ProtoLoading("در حال فیلتر…")
            shown.isEmpty() -> ProtoEmpty("هیچ خطی با این فیلتر نمی‌خورد", "جست‌وجو، سطح یا منبع را عوض کنید.")
            else -> CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(12.dp)).background(CardBg)
                        .padding(horizontal = 8.dp, vertical = 6.dp).testTag("proto_log_list"),
                ) {
                    itemsIndexed(shown, key = { i, l -> "$i:${l.raw.hashCode()}" }) { _, line -> LogRow(line) }
                }
            }
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    val colour = when (line.level) {
        'E', 'F' -> StatusRed
        'W' -> StatusAmber
        'D', 'V' -> TextTertiary
        else -> TextPrimary
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Box(Modifier.width(3.dp).height(14.dp).background(if (line.level in "EFW") colour else androidx.compose.ui.graphics.Color.Transparent))
        Text(
            line.raw, color = colour, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

private fun coreLevelLabel(level: LogLevel): String = when (level) {
    LogLevel.Debug -> "اشکال‌زدایی"
    LogLevel.Info -> "اطلاع"
    LogLevel.Warning -> "هشدار"
    LogLevel.Error -> "خطا"
    LogLevel.None -> "خاموش"
}
