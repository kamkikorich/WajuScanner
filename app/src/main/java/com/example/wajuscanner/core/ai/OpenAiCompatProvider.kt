package com.example.wajuscanner.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Endpoint serasi OpenAI (OpenAI, OpenRouter, Ollama, dsb):
 * POST {baseUrl}/chat/completions dengan Bearer token.
 */
class OpenAiCompatProvider(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String,
) : AiProvider {

    override suspend fun complete(prompt: String, system: String?, imageJpeg: ByteArray?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val messages = buildJsonArray {
                    system?.let {
                        add(buildJsonObject { put("role", "system"); put("content", it) })
                    }
                    if (imageJpeg == null) {
                        add(buildJsonObject { put("role", "user"); put("content", prompt) })
                    } else {
                        // Multimodal: teks + imej halaman (data URL, bentuk OpenAI standard).
                        val parts = buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", prompt) })
                            add(
                                buildJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject { put("url", jpegDataUrl(imageJpeg)) })
                                }
                            )
                        }
                        add(buildJsonObject { put("role", "user"); put("content", parts) })
                    }
                }
                val body = buildJsonObject {
                    put("model", model.ifBlank { DEFAULT_MODEL })
                    put("messages", messages)
                }.toString()
                val url = resolveChatCompletionsUrl(baseUrl)
                val response = postJson(
                    url = url,
                    headers = mapOf("Authorization" to "Bearer $apiKey"),
                    body = body,
                )
                parseOpenAiText(response).ifBlank { "(Tiada jawapan dikembalikan.)" }
            }
        }

    private companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}

/**
 * Bina URL `/chat/completions` daripada base URL pengguna.
 * Toleran: skema `https://` pilihan (ditambah jika tiada), `/` di hujung dibuang,
 * dan URL yang sudah tamat dengan `/chat/completions` digunakan seadanya.
 * Kosong → lalai OpenAI.
 */
internal fun resolveChatCompletionsUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isBlank()) return "https://api.openai.com/v1/chat/completions"
    val withScheme = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
        trimmed
    } else {
        "https://$trimmed"
    }
    return if (withScheme.endsWith("/chat/completions", true)) {
        withScheme
    } else {
        "$withScheme/chat/completions"
    }
}
