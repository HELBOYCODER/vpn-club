package com.helboy.vpnclub.ui.proto.engine

import dev.valid7996.narcicgeteway.domain.ai.AiAgent
import dev.valid7996.narcicgeteway.domain.ai.AiModel
import dev.valid7996.narcicgeteway.domain.ai.AiRole
import dev.valid7996.narcicgeteway.domain.ai.AiTurn

/** Pure helpers for the assistant page: what the transcript shows, how a key is masked, how models are filtered. */
object AiLogic {
    enum class Kind { User, Assistant, Tool, Error, Note }

    /** One line or bubble in the transcript. */
    data class ChatItem(val kind: Kind, val text: String, val failed: Boolean = false)

    /** "" for no key, otherwise bullets and the last four characters (nothing but bullets for a short key). */
    fun maskKey(key: String): String = when {
        key.isBlank() -> ""
        key.length <= 8 -> "••••••••"
        else -> "••••••••" + key.takeLast(4)
    }

    /** Models whose id or label contains [query] (case-insensitive); everything for a blank query. */
    fun filterModels(models: List<AiModel>, query: String): List<AiModel> {
        val q = query.trim()
        if (q.isEmpty()) return models
        return models.filter { it.id.contains(q, ignoreCase = true) || it.label.contains(q, ignoreCase = true) }
    }

    /** The transcript for [turns]. [skipUser] drops user turns already shown when they were typed. */
    fun display(turns: List<AiTurn>, skipUser: Boolean = false): List<ChatItem> = buildList {
        for (t in turns) when (t.role) {
            AiRole.USER -> if (!skipUser) add(ChatItem(Kind.User, t.text))
            AiRole.ASSISTANT -> {
                if (t.text.isNotBlank()) add(ChatItem(Kind.Assistant, t.text))
                t.calls.forEach { add(ChatItem(Kind.Tool, "→ ${it.name}")) }
                t.error?.let { add(ChatItem(Kind.Error, friendlyError(it), failed = true)) }
            }
            AiRole.TOOL -> t.results.forEach {
                add(
                    ChatItem(
                        Kind.Tool,
                        if (it.failed) "✕ ${it.name}: ${it.content.take(200)}" else "✓ ${it.name}",
                        failed = it.failed,
                    ),
                )
            }
        }
    }

    /** One short line for the typing indicator, or null for steps that change nothing on screen. */
    fun stepLine(step: AiAgent.Step): String? = when (step) {
        is AiAgent.Step.Thinking -> "در حال فکر کردن…"
        is AiAgent.Step.Calling -> "در حال اجرای ابزار ${step.name}…"
        is AiAgent.Step.Called -> if (step.result.failed) "ابزار ${step.result.name} ناموفق بود" else "ابزار ${step.result.name} انجام شد"
        is AiAgent.Step.Said -> null
        is AiAgent.Step.Failed -> null
    }

    /** Provider errors can be long JSON; keep the first part and say what to try. */
    fun friendlyError(message: String): String {
        val m = message.trim().ifEmpty { "خطای ناشناخته" }
        return (if (m.length > 280) m.take(280) + "…" else m) + "\nکلید و مدل را در تنظیمات بررسی کنید؛ اگر VPN قطع است، ارائه‌دهنده ممکن است از این شبکه در دسترس نباشد."
    }
}
