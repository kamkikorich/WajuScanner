package com.example.wajuscanner.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiParsersTest {

    @Test
    fun geminiParsesStepsContentText() {
        val json = """
            {"steps":[
              {"content":[{"type":"text","text":"Hello "}]},
              {"content":[{"type":"text","text":"world"}]}
            ]}
        """.trimIndent()
        assertEquals("Hello world", parseGeminiText(json))
    }

    @Test
    fun geminiPrefersOutputTextField() {
        assertEquals("Ringkasan ringkas.", parseGeminiText("""{"output_text":"Ringkasan ringkas."}"""))
    }

    @Test
    fun openAiParsesFirstChoiceMessage() {
        val json = """{"choices":[{"message":{"role":"assistant","content":"OK"}}]}"""
        assertEquals("OK", parseOpenAiText(json))
    }

    @Test
    fun errorMessageExtractsApiError() {
        assertEquals(
            "Invalid API key",
            errorMessage("""{"error":{"message":"Invalid API key"}}"""),
        )
    }

    @Test
    fun chatUrlAddsHttpsSchemeWhenMissing() {
        assertEquals(
            "https://ollama.com/v1/chat/completions",
            resolveChatCompletionsUrl("ollama.com/v1"),
        )
    }

    @Test
    fun chatUrlKeepsExistingSchemeAndAppendsPath() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            resolveChatCompletionsUrl("https://api.openai.com/v1/"),
        )
    }

    @Test
    fun chatUrlDefaultsToOpenAiWhenBlank() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            resolveChatCompletionsUrl("   "),
        )
    }

    @Test
    fun chatUrlDoesNotDuplicateFullEndpoint() {
        assertEquals(
            "https://ollama.com/v1/chat/completions",
            resolveChatCompletionsUrl("https://ollama.com/v1/chat/completions"),
        )
    }

    @Test
    fun errorMessageSummarisesHtmlBody() {
        val html = "<!doctype html><meta charset=\"utf-8\"><title>403</title>403 Forbidden"
        assertTrue(errorMessage(html).contains("HTML"))
    }

    @Test
    fun boundDocumentTextKeepsShortTextIntact() {
        assertEquals("hello", boundDocumentText("  hello  ", maxChars = 100))
    }

    @Test
    fun boundDocumentTextTruncatesLongText() {
        val bounded = boundDocumentText("a".repeat(200), maxChars = 50)
        assertTrue(bounded.startsWith("a".repeat(50)))
        assertTrue(bounded.contains("dipotong"))
    }
}
