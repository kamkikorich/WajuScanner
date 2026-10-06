package com.example.wajuscanner.image

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Canvas
import com.example.wajuscanner.domain.model.DocumentFilter

object FilterProcessor {

    /**
     * Returns a new bitmap with the selected document filter applied.
     */
    fun applyFilter(source: Bitmap, filter: DocumentFilter): Bitmap {
        val matrix = when (filter) {
            DocumentFilter.ORIGINAL -> return source
            DocumentFilter.AUTO -> autoEnhanceMatrix()
            DocumentFilter.DOCUMENT -> documentMatrix()
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
