package com.example.wajuscanner.core.util

import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * Pulls a short, document-friendly name out of the FRONT side of an ID card.
 *
 * Strategy: run ML Kit Text Recognition on the front bitmap, then look for a
 * line that is most likely the holder's name — heuristically the longest
 * line that is letter-heavy (more letters than digits) so we skip over ID
 * numbers, dates and other numeric-only fields the recognizer emits first.
 * The result is meant to be a fallback suggestion for the document name
 * when the user has not yet renamed it; it is intentionally narrow so a
 * noisy OCR pass cannot derail the rest of the pipeline.
 *
 * Returns null when nothing in the OCR output looks like a name; callers
 * MUST treat that as "no suggestion available" rather than an error.
 */
object IdCardNameExtractor {

    private const val OCR_TIMEOUT_SECONDS = 10L
    private const val MAX_NAME_LENGTH = 40

    suspend fun suggest(context: android.content.Context, frontUri: Uri): String? {
        val bitmap = ImageUtils.loadBitmapFromUri(context, frontUri, maxDimension = 1600)
            ?: return null
        return try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = Tasks.await(recognizer.process(inputImage), OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            pickBestLine(visionText.text)
        } catch (_: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Heuristic ranking — pick the first line that is at least 4 chars long,
     * contains at least one letter, and has more letters than digits. The
     * "more letters than digits" filter rejects ID numbers, dates and other
     * numeric-only lines that the recognizer emits first.
     */
    private fun pickBestLine(rawText: String): String? {
        if (rawText.isBlank()) return null
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val candidates = lines.filter { line ->
            val letters = line.count { it.isLetter() }
            val digits = line.count { it.isDigit() }
            letters >= 3 && letters > digits
        }
        val chosen = candidates.maxByOrNull { it.length } ?: return null
        val collapsed = chosen.replace(Regex("\\s+"), " ")
        return collapsed.take(MAX_NAME_LENGTH).ifBlank { null }
    }
}