package com.example.wajuscanner.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Abstraksi klien AI: hantar prompt (+ imej pilihan) → pulangkan teks jawapan. */
interface AiProvider {
    suspend fun complete(
        prompt: String,
        system: String? = null,
        imageJpeg: ByteArray? = null,
    ): Result<String>
}

/** Tukar bait JPEG → data URL untuk medan `image_url` (OpenAI-compatible). */
internal fun jpegDataUrl(jpeg: ByteArray): String =
    "data:image/jpeg;base64," + android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)

internal val aiJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** UA app (bukan "Dalvik/…" lalai Android yang disekat Cloudflare). */
private const val USER_AGENT = "WajuScanner/1.0 (Android)"

/** POST JSON ringkas + tambah mesej ralat yang boleh dibaca. */
internal fun postJson(url: String, headers: Map<String, String>, body: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 20_000
        readTimeout = 60_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        // UA jelas app kita. UA lalai Android ("Dalvik/…") DISEKAT oleh Cloudflare
        // (WAF) di hadapan sesetengah API (cth. ollama.com) → 403 HTML.
        setRequestProperty("User-Agent", USER_AGENT)
        headers.forEach { (name, value) -> setRequestProperty(name, value) }
    }
    return try {
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IOException("HTTP $code: ${errorMessage(text)}")
        text
    } finally {
        connection.disconnect()
    }
}

internal fun errorMessage(body: String): String {
    val apiMessage = runCatching {
        aiJson.parseToJsonElement(body).jsonObject["error"]
            ?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull()
    if (!apiMessage.isNullOrBlank()) return apiMessage
    // Badan HTML bermakna permintaan tidak sampai ke API (cth. ditolak oleh WAF/Cloudflare).
    if (body.trimStart().startsWith("<")) {
        return "permintaan ditolak oleh pelayan (respons HTML, bukan JSON)"
    }
    return body.take(200)
}

/** Gabungkan semua blok teks dalam respons Gemini Interactions API. */
internal fun parseGeminiText(body: String): String {
    val root = aiJson.parseToJsonElement(body).jsonObject
    root["output_text"]?.jsonPrimitive?.contentOrNull
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    val builder = StringBuilder()
    root["steps"]?.jsonArray?.forEach { step ->
        step.jsonObject["content"]?.let { content ->
            appendText(content, builder)
        }
    }
    return builder.toString().trim()
}

private fun appendText(element: JsonElement, builder: StringBuilder) {
    when {
        element is JsonPrimitive -> element.contentOrNull?.let { builder.append(it) }
        else -> runCatching {
            element.jsonArray.forEach { item ->
                item.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.let { builder.append(it) }
            }
        }
    }
}

/** Ambil choices[0].message.content daripada respons OpenAI-compatible. */
internal fun parseOpenAiText(body: String): String =
    aiJson.parseToJsonElement(body).jsonObject["choices"]
        ?.jsonArray?.firstOrNull()
        ?.jsonObject?.get("message")
        ?.jsonObject?.get("content")
        ?.jsonPrimitive?.contentOrNull
        .orEmpty()
