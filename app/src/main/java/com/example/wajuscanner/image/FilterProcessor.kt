package com.example.wajuscanner.image

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Canvas
import com.example.wajuscanner.domain.model.DocumentFilter
import kotlin.math.pow
import kotlin.math.roundToInt

object FilterProcessor {

    /**
     * Returns a new bitmap with the selected document filter applied.
     */
    fun applyFilter(source: Bitmap, filter: DocumentFilter): Bitmap {
        if (filter == DocumentFilter.WHITEN) return whitenBackground(source)
        val matrix = when (filter) {
            DocumentFilter.ORIGINAL -> return source
            DocumentFilter.AUTO -> autoEnhanceMatrix()
            DocumentFilter.DOCUMENT -> documentMatrix()
            // WHITEN diproses lebih awal (bukan ColorMatrix); kekal di sini
            // hanya untuk kesempunaian keputusan when.
            DocumentFilter.WHITEN -> return whitenBackground(source)
            DocumentFilter.BLACK_AND_WHITE -> blackAndWhiteMatrix()
            DocumentFilter.GRAYSCALE -> grayscaleMatrix()
            DocumentFilter.HIGH_CONTRAST -> highContrastMatrix()
        }

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
            isAntiAlias = true
            isFilterBitmap = true
        }

        val output = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * "Buang Gelap" — penormalan latar adaptif (teknik standard penambahbaikan
     * dokumen, setara mod "Document/Magic" CamScanner):
     * 1. Anggarkan warna latar kertas per saluran (persentil ke-85 luma daripada
     *    sampel kecil — kertas yang kelabu/kuning dikenal pasti).
     * 2. Skala setiap saluran supaya latar jadi putih penuh (gain 255/bg).
     * 3. Gamma ringan 1.12: cahaya tengah digelapkan sedikit supaya dakap
     *    kekal tebal, bukan diluntur bersama latar.
     * Dilaksana dengan LUT per saluran (pantas, tanpa Math.pow per piksel).
     */
    private fun whitenBackground(source: Bitmap): Bitmap {
        val sampleW = (source.width / 4).coerceAtLeast(1)
        val sampleH = (source.height / 4).coerceAtLeast(1)
        val sample = Bitmap.createScaledBitmap(source, sampleW, sampleH, true)
        val samplePixels = IntArray(sampleW * sampleH)
        sample.getPixels(samplePixels, 0, sampleW, 0, 0, sampleW, sampleH)
        sample.recycle()

        val histR = IntArray(256)
        val histG = IntArray(256)
        val histB = IntArray(256)
        for (p in samplePixels) {
            if (p ushr 24 == 0) continue
            histR[(p shr 16) and 0xFF]++
            histG[(p shr 8) and 0xFF]++
            histB[p and 0xFF]++
        }
        // Persentil 85: nilai latar (pencukupan awal) per saluran, tak makan.
        val bgR = percentile(histR, samplePixels.size, 0.85).coerceIn(90, 255)
        val bgG = percentile(histG, samplePixels.size, 0.85).coerceIn(90, 255)
        val bgB = percentile(histB, samplePixels.size, 0.85).coerceIn(90, 255)

        val gamma = 1.12
        fun buildLut(bg: Int): IntArray {
            val gain = 255.0 / bg
            return IntArray(256) { v ->
                val normalised = v * gain / 255.0
                (255.0 * normalised.pow(gamma)).roundToInt().coerceIn(0, 255)
            }
        }
        val lutR = buildLut(bgR)
        val lutG = buildLut(bgG)
        val lutB = buildLut(bgB)

        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            if (p ushr 24 == 0) continue
            pixels[i] = (p and 0xFF000000.toInt()) or
                (lutR[(p shr 16) and 0xFF] shl 16) or
                (lutG[(p shr 8) and 0xFF] shl 8) or
                lutB[p and 0xFF]
        }
        val out = Bitmap.createBitmap(w, h, source.config ?: Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /** Nilai (0-255) di mana kumulatif histogram mencapai [fraction] daripada jumlah. */
    private fun percentile(hist: IntArray, total: Int, fraction: Double): Int {
        if (total <= 0) return 255
        val target = (total * fraction).toInt()
        var cumulative = 0
        for (v in 255 downTo 0) {
            cumulative += hist[v]
            if (cumulative >= target) return v
        }
        return 255
    }

    private fun grayscaleMatrix(): ColorMatrix {
        return ColorMatrix().apply { setSaturation(0f) }
    }

    private fun blackAndWhiteMatrix(): ColorMatrix {
        val bw = ColorMatrix().apply { setSaturation(0f) }
        val threshold = ColorMatrix(floatArrayOf(
            85f, 85f, 85f, 0f, -128f * 255f,
            85f, 85f, 85f, 0f, -128f * 255f,
            85f, 85f, 85f, 0f, -128f * 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        val combined = ColorMatrix()
        bw.postConcat(threshold)
        combined.set(bw)
        return combined
    }

    private fun highContrastMatrix(): ColorMatrix {
        return ColorMatrix(
            floatArrayOf(
                2f, 0f, 0f, 0f, -160f,
                0f, 2f, 0f, 0f, -160f,
                0f, 0f, 2f, 0f, -160f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun autoEnhanceMatrix(): ColorMatrix {
        // Mild contrast + slight saturation boost typical of "auto" document mode.
        return ColorMatrix(
            floatArrayOf(
                1.2f, 0f, 0f, 0f, -20f,
                0f, 1.2f, 0f, 0f, -20f,
                0f, 0f, 1.2f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun documentMatrix(): ColorMatrix {
        // White-paper look: increase brightness and contrast.
        return ColorMatrix(
            floatArrayOf(
                1.4f, 0f, 0f, 0f, -40f,
                0f, 1.4f, 0f, 0f, -40f,
                0f, 0f, 1.4f, 0f, -40f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }
}
