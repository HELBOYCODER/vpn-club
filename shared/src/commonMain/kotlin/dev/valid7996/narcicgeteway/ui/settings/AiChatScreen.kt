package dev.valid7996.narcicgeteway.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import dev.valid7996.narcicgeteway.domain.ai.AiAgent
import dev.valid7996.narcicgeteway.domain.ai.AiAppBridge
import dev.valid7996.narcicgeteway.domain.ai.AiClient
import dev.valid7996.narcicgeteway.domain.ai.AiRole
import dev.valid7996.narcicgeteway.domain.ai.AiSettings
import dev.valid7996.narcicgeteway.domain.ai.AiSystemPrompt
import dev.valid7996.narcicgeteway.domain.ai.AiTurn
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.ai_chat_hint
import dev.valid7996.narcicgeteway.shared.resources.ai_example_fastest
import dev.valid7996.narcicgeteway.shared.resources.ai_example_hot
import dev.valid7996.narcicgeteway.shared.resources.ai_example_mtu
import dev.valid7996.narcicgeteway.shared.resources.ai_example_nodata
import dev.valid7996.narcicgeteway.shared.resources.ai_intro_changes
import dev.valid7996.narcicgeteway.shared.resources.ai_intro_readonly
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun AiChatScreen(
    settings: AiSettings,
    bridge: AiAppBridge,
    languageName: String,
    languageNative: String,
    platform: String,
    appVersion: String,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(0.dp),
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<AiTurn>() }
    val steps = remember { mutableStateListOf<String>() }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val agent = remember(settings.provider, settings.model(), settings.allowChanges, languageName) {
        AiAgent(
            client = AiClient.of(settings.provider, settings.key()),
            bridge = bridge,
            model = settings.model(),
            systemPrompt = AiSystemPrompt.build(
                languageName = languageName,
                languageNative = languageNative,
                platform = platform,
                appVersion = appVersion,
                allowChanges = settings.allowChanges,
                extra = settings.extraPrompt,
            ),
            allowChanges = settings.allowChanges,
        )
    }

    LaunchedEffect(history.size, steps.size) {
        val last = history.size + steps.size - 1
        if (last >= 0) runCatching { listState.animateScrollToItem(last) }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7))
            .padding(contentPadding),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                settings.provider.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color.Black,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                settings.model(),
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (history.isEmpty()) {
                item { Intro(settings, isDark) }
            }
            items(history) { turn -> TurnBubble(turn, isDark) }
            if (steps.isNotEmpty()) {
                item { StepTrail(steps, isDark) }
            }
        }

        Surface(
            color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            border = BorderStroke(0.5.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = {
                        Text(
                            stringResource(Res.string.ai_chat_hint),
                            color = if (isDark) Color(0xFF8E8E93) else Color(0xFFC7C7CC),
                        )
                    },
                    enabled = !busy,
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF007AFF),
                        unfocusedBorderColor = if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA),
                        focusedContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                        unfocusedContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                        focusedTextColor = if (isDark) Color.White else Color.Black,
                        unfocusedTextColor = if (isDark) Color.White else Color.Black,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    enabled = draft.isNotBlank() && !busy,
                    onClick = {
                        val message = draft.trim()
                        draft = ""
                        busy = true
                        steps.clear()
                        scope.launch {
                            val produced = runCatching {
                                agent.ask(history.toList(), message) { step ->
                                    when (step) {
                                        is AiAgent.Step.Calling -> steps += "→ ${step.name}"
                                        is AiAgent.Step.Called -> steps += if (step.result.failed) {
                                            "✕ ${step.result.name}: ${step.result.content.take(200)}"
                                        } else {
                                            "✓ ${step.result.name}"
                                        }
                                        is AiAgent.Step.Failed -> steps += "✕ ${step.message}"
                                        else -> Unit
                                    }
                                }
                            }.getOrElse { e ->
                                if (e is kotlinx.coroutines.CancellationException) throw e

                                listOf(
                                    AiTurn(AiRole.USER, message),
                                    AiTurn(AiRole.ASSISTANT, "", error = e.message ?: e::class.simpleName),
                                )
                            }
                            history.addAll(produced)
                            steps.clear()
                            busy = false
                        }
                    },
                    modifier = Modifier.size(42.dp).clip(CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF007AFF),
                        contentColor = Color.White,
                        disabledContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                        disabledContentColor = if (isDark) Color(0xFF8E8E93) else Color(0xFFAFAFB4),
                    ),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("\u27A4", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Intro(settings: AiSettings, isDark: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            border = BorderStroke(0.5.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "${settings.provider.label} · ${settings.model()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        if (settings.allowChanges) Res.string.ai_intro_changes else Res.string.ai_intro_readonly,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
                )
                Spacer(Modifier.height(14.dp))
                listOf(
                    Res.string.ai_example_hot,
                    Res.string.ai_example_nodata,
                    Res.string.ai_example_fastest,
                    Res.string.ai_example_mtu,
                ).map { stringResource(it) }.forEach {
                    Text(
                        "· $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF007AFF),
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TurnBubble(turn: AiTurn, isDark: Boolean) {
    if (turn.role == AiRole.TOOL) {
        Text(
            turn.results.joinToString("  ") { (if (it.failed) "✕ " else "✓ ") + it.name },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
            modifier = Modifier.padding(start = 8.dp),
        )
        return
    }
    if (turn.text.isBlank() && turn.error == null) return

    val user = turn.role == AiRole.USER
    val error = turn.error
    Box(Modifier.fillMaxWidth(), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        error != null -> if (isDark) Color(0x33FF3B30) else Color(0x1AFF3B30)
                        user -> Color(0xFF007AFF)
                        else -> if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
                    },
                )
                .then(
                    if (!user && error == null) {
                        Modifier.border(0.5.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), RoundedCornerShape(16.dp))
                    } else Modifier
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                error ?: turn.text,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    error != null -> Color(0xFFFF3B30)
                    user -> Color.White
                    else -> if (isDark) Color.White else Color.Black
                },
            )
        }
    }
}

@Composable
private fun StepTrail(steps: List<String>, isDark: Boolean) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp)) {
        steps.takeLast(8).forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73),
            )
        }
    }
}
