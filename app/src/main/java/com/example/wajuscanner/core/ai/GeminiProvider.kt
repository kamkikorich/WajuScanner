package com.example.wajuscanner.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Gemini melalui Interactions API (endpoint terkini, bukan `:generateContent`
 * yang lapuk). Dokumen: https://ai.google.dev/gemini-api/docs/text-generation
 */
class GeminiProvider(
    private val apiKey: String,
    private val model: String,
) : AiProvider {

    override suspend fun complete(prompt: String, system: String?, imageJpeg: ByteArray?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject {
                    put("model", model.ifBlank { DEFAULT_MODEL })
                    put("input", prompt)
                    system?.let { put("system_instruction", it) }
                }.toString()
                val response = postJson(
                    url = ENDPOINT,
                    headers = mapOf("x-goog-api-key" to apiKey),
                    body = body,
                )
                parseGeminiText(response).ifBlank { "(Tiada jawapan dikembalikan.)" }
            }
        }

    private companion object {
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
    }
}
