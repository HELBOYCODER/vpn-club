package dev.valid7996.narcicgeteway.desktop.config

import dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LockedConfigExtractorTest {
    private val link = "nrg://AbCdEfGh12345678_-AbCdEfGh12345678"

    @Test
    fun keyIsFoundInTheMessageTheSheetGenerates() {
        val msg = """
            🔒 کانفیگ اختصاصی و قفل‌شده Narcic Gateway
            🏷️ نام کانفیگ: Berlin
            🔑 کلید خصوصی یک‌بار مصرف شما: NG-ABCD-EFGH-JKLM
            ⚠️ توجه: این کلید اختصاصی و یک‌بار مصرف است و فقط روی دستگاه شما فعال می‌شود.

            🔗 لینک کانفیگ:
            $link

            جهت فعال‌سازی، لینک بالا را کپی کرده و در برنامه Narcic Gateway وارد نمایید.
        """.trimIndent()
        assertEquals("NG-ABCD-EFGH-JKLM", LockedConfigExtractor.extractKey(msg))
        assertEquals(link, LockedConfigExtractor.extractLink(msg))
    }

    @Test
    fun linkWrappedOverSeveralLinesIsJoined() {
        val wrapped = "nrg://AbCdEfGh12345678_-\nAbCdEfGh12345678"
        assertEquals(link, LockedConfigExtractor.extractLink(wrapped))
    }

    @Test
    fun trailingSentencePunctuationIsNotPartOfTheLink() {
        assertEquals(link, LockedConfigExtractor.extractLink("لینک: $link."))
    }

    @Test
    fun plainTextHasNoKeyOrLink() {
        assertNull(LockedConfigExtractor.extractKey("hello world"))
        assertNull(LockedConfigExtractor.extractLink("hello world"))
    }
}
