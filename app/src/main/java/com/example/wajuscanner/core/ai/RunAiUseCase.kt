package com.example.wajuscanner.core.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.wajuscanner.data.settings.AiProviderType
import com.example.wajuscanner.data.settings.AiSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menjalankan tindakan AI menggunakan kunci yang disimpan. Direka supaya
 * kegagalan (kunci luput/tak sah/kuota) hanya menghasilkan [Result.failure] —
 * pemanggil memutuskan bagaimana memaparkan ralat, tanpa menjejaskan aliran app.
 */
@Singleton
class RunAiUseCase @Inject constructor(
    private val settingsStore: AiSettingsStore,
) {

    /**
     * [pageImagePath] ≠ null → sertakan imej halaman sebagai input vision
     * (kemampuan bergantung pada model). Teks dokumen tetap dihadkan panjang.
     */
    suspend fun run(
        action: AiAction,
        documentText: String,
        question: String? = null,
        pageImagePath: String? = null,
    ): Result<String> {
        val bounded = boundDocumentText(documentText)
        val image = pageImagePath?.let { path ->
            withContext(Dispatchers.IO) { encodeThumbnailJpeg(path) }
        }
        if (bounded.isBlank() && image == null) {
            return Result.failure(IllegalStateException("Tiada teks atau imej untuk diproses."))
        }
        val prompt = AiPrompts.build(action, bounded, question)
        return provider().fold(
            onSuccess = { it.complete(prompt, AiPrompts.SYSTEM_INSTRUCTION, image) },
            onFailure = { Result.failure(it) },
        )
    }

    /** Ujian sambungan ringkas untuk butang "Uji" di Tetapan. */
    suspend fun testConnection(): Result<String> =
        provider().fold(
            onSuccess = { it.complete("Balas dengan satu perkataan: OK", null) },
            onFailure = { Result.failure(it) },
        )

    private suspend fun provider(): Result<AiProvider> {
        val settings = settingsStore.settings.first()
        if (!settings.hasKey) {
            return Result.failure(IllegalStateException("Tiada kunci API disimpan."))
        }
        val key = settingsStore.apiKey()
            ?: return Result.failure(IllegalStateException("Kunci API tidak dapat dibaca (silakan simpan semula)."))
        val provider = when (settings.provider) {
            AiProviderType.GEMINI -> GeminiProvider(key, settings.model)
            AiProviderType.OPENAI_COMPATIBLE -> OpenAiCompatProvider(key, settings.model, settings.baseUrl)
        }
        return Result.success(provider)
    }
}

/** Had aksara teks dokumen yang dihantar ke model (elak melebihi tetingkap konteks). */
internal const val MAX_DOCUMENT_CHARS = 16_000

internal fun boundDocumentText(text: String, maxChars: Int = MAX_DOCUMENT_CHARS): String {
    val trimmed = text.trim()
    if (trimmed.length <= maxChars) return trimmed
    return trimmed.take(maxChars) + "\n\n[…teks dipotong kerana terlalu panjang…]"
}

/** Decode + kecilkan imej halaman → bait JPEG (input vision). Null jika gagal. */
private fun encodeThumbnailJpeg(path: String, maxDim: Int = 1280, quality: Int = 80): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
    val bitmap = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null

    return try {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}
