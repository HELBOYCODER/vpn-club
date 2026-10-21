package com.helboy.vpnclub.ui.proto.engine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEmpty
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.domain.ai.AiAgent
import dev.valid7996.narcicgeteway.domain.ai.AiAppBridge
import dev.valid7996.narcicgeteway.domain.ai.AiClient
import dev.valid7996.narcicgeteway.domain.ai.AiModel
import dev.valid7996.narcicgeteway.domain.ai.AiProvider
import dev.valid7996.narcicgeteway.domain.ai.AiSettings
import dev.valid7996.narcicgeteway.domain.ai.AiSystemPrompt
import dev.valid7996.narcicgeteway.domain.ai.AiTurn
import dev.valid7996.narcicgeteway.platform.AppInfo
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAB_CHAT = "chat"
private const val TAB_SETTINGS = "settings"

/**
 * The built-in assistant: provider, model and API key (stored in AppSettings.ai, the key sealed), and a chat that
 * lets the model call the app's own tools through [bridge]. A null [bridge] means this build cannot offer the chat
 * (the page says so); settings still work. Requests and tool calls run off the main thread and are cancelled when
 * the page is left or Stop is pressed.
 */
@Composable
fun AiAssistantPage(bridge: AiAppBridge?, onBack: () -> Unit) {
    val repo = ProtoEnv.settings
    val settings by repo.settings.collectAsState()
    val ai = settings.ai
    var tab by remember { mutableStateOf(if (ai.isReady()) TAB_CHAT else TAB_SETTINGS) }
    val updateAi: ((AiSettings) -> AiSettings) -> Unit = { f -> repo.update { it.copy(ai = f(it.ai)) } }

    ProtoPage(title = "دستیار هوشمند", onBack = onBack, scroll = false) {
        ProtoChoice(
            title = null,
            options = listOf(TAB_CHAT to "گفتگو", TAB_SETTINGS to "تنظیمات"),
            selected = tab, onSelect = { tab = it },
            modifier = Modifier.testTag("proto_ai_tabs"),
        )
        if (tab == TAB_CHAT) {
            ChatTab(ai, bridge, onOpenSettings = { tab = TAB_SETTINGS })
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsTab(ai, updateAi)
            }
        }
    }
}

@Composable
private fun SettingsTab(ai: AiSettings, update: ((AiSettings) -> AiSettings) -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val savedKey = remember(ai.provider, ai.sealedKeys[ai.provider.name]) { ai.key() }
    var keyDraft by remember(ai.provider) { mutableStateOf("") }
    var models by remember(ai.provider) { mutableStateOf<List<AiModel>>(emptyList()) }
    var filter by remember(ai.provider) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember(ai.provider) { mutableStateOf<String?>(null) }
    var failed by remember(ai.provider) { mutableStateOf(false) }
    var verifyJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) { onDispose { verifyJob?.cancel() } }

    fun verify(key: String) {
        if (busy || key.isBlank()) return
        busy = true
        status = null
        val provider = ai.provider
        verifyJob = scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { AiClient.of(provider, key).models() }
                r.onSuccess { list ->
                    models = list
                    failed = false
                    status = "کلید معتبر است؛ ${list.size.fa()} مدل پیدا شد."
                    if (list.none { it.id == ai.model(provider) }) update { s -> s.withModel(provider, "") }
                }.onFailure {
                    failed = true
                    status = AiLogic.friendlyError(it.message.orEmpty())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failed = true
                status = AiLogic.friendlyError(e.message.orEmpty())
            } finally {
                busy = false
            }
        }
    }

    ProtoSection(title = "فعال‌سازی", hint = "دستیار با کلید API خودتان کار می‌کند و پیام‌ها مستقیم به ارائه‌دهنده‌ی انتخابی می‌روند؛ چیزی از طرف برنامه ذخیره یا ارسال نمی‌شود.") {
        ProtoSwitch("استفاده از دستیار هوشمند", ai.enabled, { v -> update { it.copy(enabled = v) } }, tag = "proto_ai_enabled")
    }

    if (!ai.enabled) return

    ProtoSection(title = "ارائه‌دهنده") {
        ProtoChoice(
            title = null,
            options = AiProvider.entries.map { it.name to it.label },
            selected = ai.provider.name,
            onSelect = { n -> AiProvider.entries.firstOrNull { it.name == n }?.let { p -> update { it.copy(provider = p) } } },
        )
        ProtoField(
            label = "کلید API", value = keyDraft, onValueChange = { keyDraft = it.trim() },
            placeholder = if (savedKey.isNotBlank()) AiLogic.maskKey(savedKey) else "کلید را اینجا بچسبانید",
            password = true, monospace = true, tag = "proto_ai_key",
        )
        if (savedKey.isNotBlank()) {
            Text("کلید ذخیره‌شده: ${AiLogic.maskKey(savedKey)}", color = StatusGreen, fontSize = 12.sp, modifier = Modifier.testTag("proto_ai_key_saved"))
        } else {
            Text("هنوز کلیدی ذخیره نشده.", color = TextTertiary, fontSize = 12.sp)
        }
        ProtoButton(
            "ذخیره‌ی کلید و بررسی", Icons.Rounded.Save,
            {
                val k = keyDraft
                update { it.withKey(it.provider, k) }
                keyDraft = ""
                verify(k)
            },
            enabled = keyDraft.isNotBlank(), running = busy, tag = "proto_ai_save_key",
        )
        if (savedKey.isNotBlank() && models.isEmpty()) {
            ProtoButton("بررسی کلید ذخیره‌شده و دریافت مدل‌ها", Icons.Rounded.VerifiedUser, { verify(savedKey) }, running = busy)
        }
        ProtoOutlined("گرفتن کلید از ${ai.provider.label}", { platform.openUri(ai.provider.keyUrl) })
        status?.let { Text(it, color = if (failed) StatusRed else StatusGreen, fontSize = 12.sp, modifier = Modifier.testTag("proto_ai_status")) }
    }

    ProtoSection(title = "مدل", hint = if (ai.model().isBlank()) "ابتدا کلید را بررسی کنید تا فهرست مدل‌ها بیاید." else "مدل فعلی: ${ai.model()}") {
        if (models.isEmpty()) {
            Text("فهرست مدل‌ها خالی است.", color = TextTertiary, fontSize = 12.sp)
        } else {
            ProtoField("جستجو در مدل‌ها", filter, { filter = it })
            val shown = AiLogic.filterModels(models, filter)
            shown.take(40).forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { update { s -> s.withModel(s.provider, m.id) } }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.label, color = if (m.id == ai.model()) NeonCyan else TextPrimary, fontSize = 14.sp)
                        if (m.label != m.id) Text(m.id, color = TextTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    if (m.id == ai.model()) Text("انتخاب شده", color = NeonCyan, fontSize = 11.sp)
                }
            }
            if (shown.size > 40) Text("${(shown.size - 40).fa()} مدل دیگر؛ جستجو را دقیق‌تر کنید.", color = TextTertiary, fontSize = 11.sp)
            if (shown.isEmpty()) Text("مدلی پیدا نشد.", color = TextTertiary, fontSize = 12.sp)
        }
    }

    ProtoSection(title = "رفتار") {
        ProtoSwitch(
            "اجازه‌ی تغییر در برنامه", ai.allowChanges, { v -> update { it.copy(allowChanges = v) } },
            subtitle = "اگر روشن باشد، دستیار می‌تواند تنظیمات را عوض کند، سرور بسازد یا حذف کند و اتصال را برقرار یا قطع کند. اگر خاموش باشد فقط می‌خواند.",
        )
        ProtoField(
            "دستور ثابت برای دستیار", ai.extraPrompt, { v -> update { it.copy(extraPrompt = v) } },
            placeholder = "مثلا: کوتاه جواب بده", singleLine = false, minLines = 2,
        )
    }
}

@Composable
private fun ColumnScope.ChatTab(ai: AiSettings, bridge: AiAppBridge?, onOpenSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    val history = remember { mutableListOf<AiTurn>() }
    val chat = remember { mutableStateListOf<AiLogic.ChatItem>() }
    val steps = remember { mutableStateListOf<String>() }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    if (bridge == null) {
        ProtoEmpty("گفتگو در این اجرا در دسترس نیست", "دستیار به موتور برنامه وصل نشده است. تنظیمات را می‌توانید ببینید.")
        return
    }
    if (!ai.isReady()) {
        ProtoEmpty(
            "دستیار هنوز آماده نیست",
            "برای گفتگو، دستیار را فعال کنید و کلید API و یک مدل انتخاب کنید.",
            actionText = "رفتن به تنظیمات", onAction = onOpenSettings,
        )
        return
    }

    val agent = remember(ai.provider, ai.model(), ai.allowChanges, ai.extraPrompt, ai.sealedKeys[ai.provider.name]) {
        AiAgent(
            client = AiClient.of(ai.provider, ai.key()),
            bridge = bridge,
            model = ai.model(),
            systemPrompt = AiSystemPrompt.build(
                languageName = "Persian", languageNative = "فارسی", platform = "Android",
                appVersion = AppInfo.versionName, allowChanges = ai.allowChanges, extra = ai.extraPrompt,
            ),
            allowChanges = ai.allowChanges,
        )
    }

    LaunchedEffect(chat.size, steps.size) {
        val last = chat.size - 1
        if (last >= 0) runCatching { listState.animateScrollToItem(last) }
    }

    fun send() {
        val message = draft.trim()
        if (message.isEmpty() || busy) return
        draft = ""
        busy = true
        steps.clear()
        chat.add(AiLogic.ChatItem(AiLogic.Kind.User, message))
        job = scope.launch {
            try {
                val produced = withContext(Dispatchers.Default) {
                    agent.ask(history.toList(), message) { step ->
                        AiLogic.stepLine(step)?.let { line ->
                            if (steps.isNotEmpty()) steps.removeAt(steps.size - 1)
                            steps.add(line)
                        }
                    }
                }
                history.addAll(produced)
                chat.addAll(AiLogic.display(produced, skipUser = true))
            } catch (e: CancellationException) {
                chat.add(AiLogic.ChatItem(AiLogic.Kind.Note, "متوقف شد."))
                throw e
            } catch (e: Throwable) {
                chat.add(AiLogic.ChatItem(AiLogic.Kind.Error, AiLogic.friendlyError(e.message.orEmpty()), failed = true))
            } finally {
                busy = false
                steps.clear()
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("${ai.provider.label} · ${ai.model()}", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
        IconButton(
            onClick = { job?.cancel(); history.clear(); chat.clear(); steps.clear(); busy = false },
            enabled = chat.isNotEmpty() || busy, modifier = Modifier.testTag("proto_ai_clear"),
        ) { Icon(Icons.Rounded.DeleteSweep, "پاک کردن گفتگو", tint = TextSecondary) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("proto_ai_list"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (chat.isEmpty() && !busy) {
            item {
                ProtoEmpty(
                    "از دستیار بپرسید",
                    if (ai.allowChanges) "مثلا: «چرا وصل نمی‌شوم؟» یا «بهترین سرور را انتخاب کن». دستیار می‌تواند تنظیمات را هم تغییر دهد."
                    else "مثلا: «وضعیت اتصال من چیست؟». دستیار فقط می‌خواند و چیزی را تغییر نمی‌دهد.",
                )
            }
        }
        items(chat.size) { i -> Bubble(chat[i]) }
        if (busy) {
            item {
                Row(Modifier.fillMaxWidth().testTag("proto_ai_typing"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.padding(end = 2.dp), strokeWidth = 2.dp, color = NeonCyan)
                    Text(steps.lastOrNull() ?: "در حال فکر کردن…", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProtoField(
            label = "پیام", value = draft, onValueChange = { draft = it },
            modifier = Modifier.weight(1f), enabled = !busy, singleLine = false, tag = "proto_ai_input",
        )
        if (busy) {
            IconButton(onClick = { job?.cancel() }, modifier = Modifier.testTag("proto_ai_stop")) {
                Icon(Icons.Rounded.Stop, "توقف", tint = StatusRed)
            }
        } else {
            IconButton(onClick = { send() }, enabled = draft.isNotBlank(), modifier = Modifier.testTag("proto_ai_send")) {
                Icon(Icons.AutoMirrored.Rounded.Send, "ارسال", tint = if (draft.isNotBlank()) NeonCyan else TextTertiary)
            }
        }
    }
}

@Composable
private fun Bubble(item: AiLogic.ChatItem) {
    when (item.kind) {
        AiLogic.Kind.Tool -> Text(
            item.text, color = if (item.failed) StatusRed else TextTertiary, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        AiLogic.Kind.Note -> Text(item.text, color = TextTertiary, fontSize = 12.sp)
        AiLogic.Kind.Error -> ClubCard(Modifier.fillMaxWidth(), border = StatusRed) {
            Text(item.text, color = StatusRed, fontSize = 13.sp)
        }
        AiLogic.Kind.User -> ClubCard(Modifier.fillMaxWidth(), border = NeonCyan) {
            Text(item.text, color = TextPrimary, fontSize = 14.sp)
        }
        AiLogic.Kind.Assistant -> ClubCard(Modifier.fillMaxWidth()) {
            Text(item.text, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Normal)
        }
    }
}
