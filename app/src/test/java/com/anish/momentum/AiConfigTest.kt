package com.anish.momentum

import com.anish.momentum.ai.AiConfig
import com.anish.momentum.ai.HabitReplyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiConfigTest {

    @Test
    fun `default base url points at openrouter`() {
        assertEquals("https://openrouter.ai/api/v1", AiConfig.DEFAULT_BASE_URL)
    }

    @Test
    fun `trailing slash is added when missing`() {
        val config = AiConfig(baseUrl = "https://openrouter.ai/api/v1")
        assertEquals("https://openrouter.ai/api/v1/", config.normalizedBaseUrl)
    }

    @Test
    fun `existing trailing slash is preserved`() {
        val config = AiConfig(baseUrl = "https://openrouter.ai/api/v1/")
        assertEquals("https://openrouter.ai/api/v1/", config.normalizedBaseUrl)
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        val config = AiConfig(baseUrl = "  http://10.0.2.2:11434/v1  ")
        assertEquals("http://10.0.2.2:11434/v1/", config.normalizedBaseUrl)
    }

    @Test
    fun `default model is a free openrouter id`() {
        assertTrue(AiConfig.DEFAULT_MODEL.isNotBlank())
        assertTrue(AiConfig.DEFAULT_MODEL.endsWith(":free"))
        assertTrue(AiConfig().isUsable)
    }

    @Test
    fun `blank base url or model is not usable`() {
        assertTrue(!AiConfig(baseUrl = "", model = "m").isUsable)
        assertTrue(!AiConfig(baseUrl = "https://x/v1", model = " ").isUsable)
        assertTrue(AiConfig(baseUrl = "https://x/v1", model = "m").isUsable)
    }
}

class HabitReplyParserTest {

    @Test
    fun `parses plain emoji lines`() {
        val result = HabitReplyParser.parse("🧘 Meditation\n💧 Drink water")
        assertEquals(2, result.size)
        assertEquals("🧘", result[0].emoji)
        assertEquals("Meditation", result[0].name)
        assertEquals("Drink water", result[1].name)
    }

    @Test
    fun `strips dashes and numbering`() {
        val result = HabitReplyParser.parse("- 🏃 Morning run\n2. 🥗 Eat vegetables\n3) 😴 Sleep by 11pm")
        assertEquals(3, result.size)
        assertEquals("Morning run", result[0].name)
        assertEquals("Eat vegetables", result[1].name)
        assertEquals("Sleep by 11pm", result[2].name)
    }

    @Test
    fun `strips bold and backticks`() {
        val result = HabitReplyParser.parse("**📖 Read 10 pages**\n`🧹 Tidy the desk`")
        assertEquals("Read 10 pages", result[0].name)
        assertEquals("Tidy the desk", result[1].name)
    }

    @Test
    fun `ignores blank lines and prose without an emoji prefix`() {
        val result = HabitReplyParser.parse("\nHere are 5 habits:\n\n🧘 Meditate\n\n")
        assertEquals(1, result.size)
        assertEquals("Meditate", result[0].name)
    }

    @Test
    fun `empty reply yields nothing`() {
        assertTrue(HabitReplyParser.parse("").isEmpty())
    }

    @Test
    fun `rejects a row whose name is a long sentence`() {
        val result = HabitReplyParser.parse(
            "\uD83E\uDDD8 Sure! Here is a detailed plan you can follow to build a routine " +
                "that works even on your busiest days and never falls apart"
        )
        assertTrue(result.isEmpty())
    }
}
