package com.helboy.vpnclub.ui.proto.engine

import dev.valid7996.narcicgeteway.domain.ai.AiAgent
import dev.valid7996.narcicgeteway.domain.ai.AiModel
import dev.valid7996.narcicgeteway.domain.ai.AiRole
import dev.valid7996.narcicgeteway.domain.ai.AiToolCall
import dev.valid7996.narcicgeteway.domain.ai.AiToolResult
import dev.valid7996.narcicgeteway.domain.ai.AiTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiLogicTest {
    @Test fun keyIsNeverShownInFull() {
        assertEquals("", AiLogic.maskKey(""))
        assertEquals("••••••••", AiLogic.maskKey("short"))
        val m = AiLogic.maskKey("sk-abcdefghijklmnop1234")
        assertTrue(m.endsWith("1234"))
        assertFalse(m.contains("abcdef"))
    }

    @Test fun modelFilterMatchesIdAndLabelIgnoringCase() {
        val models = listOf(AiModel("gemini-2.5-pro", "Gemini Pro"), AiModel("gemini-flash", "Flash"), AiModel("other"))
        assertEquals(3, AiLogic.filterModels(models, "  ").size)
        assertEquals(listOf("gemini-2.5-pro"), AiLogic.filterModels(models, "PRO").map { it.id })
        assertEquals(2, AiLogic.filterModels(models, "gemini").size)
        assertTrue(AiLogic.filterModels(models, "zzz").isEmpty())
    }

    @Test fun transcriptShowsReplyToolStepsAndErrors() {
        val turns = listOf(
            AiTurn(AiRole.USER, "hi"),
            AiTurn(AiRole.ASSISTANT, "checking", calls = listOf(AiToolCall("1", "get_status", "{}"))),
            AiTurn(AiRole.TOOL, results = listOf(AiToolResult("1", "get_status", "ok"), AiToolResult("2", "ping", "boom", failed = true))),
            AiTurn(AiRole.ASSISTANT, "", error = "quota"),
        )
        val items = AiLogic.display(turns, skipUser = true)
        assertEquals(listOf(AiLogic.Kind.Assistant, AiLogic.Kind.Tool, AiLogic.Kind.Tool, AiLogic.Kind.Tool, AiLogic.Kind.Error), items.map { it.kind })
        assertTrue(items[2].text.startsWith("✓"))
        assertTrue(items[3].failed)
        assertTrue(items.last().text.contains("quota"))
        assertEquals(AiLogic.Kind.User, AiLogic.display(turns).first().kind)
    }

    @Test fun typingLineExistsForWorkSteps() {
        assertTrue(AiLogic.stepLine(AiAgent.Step.Thinking(1))!!.isNotBlank())
        assertTrue(AiLogic.stepLine(AiAgent.Step.Calling("ping", "{}"))!!.contains("ping"))
        assertNull(AiLogic.stepLine(AiAgent.Step.Said("x")))
        assertNull(AiLogic.stepLine(AiAgent.Step.Failed("x")))
    }

    @Test fun longErrorsAreShortened() {
        val text = AiLogic.friendlyError("x".repeat(1000))
        assertTrue(text.length < 600)
        assertTrue(AiLogic.friendlyError("").isNotBlank())
    }
}
