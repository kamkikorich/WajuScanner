package com.example.wajuscanner.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.wajuscanner.core.common.Constants
import com.example.wajuscanner.core.util.FileUtils
import com.example.wajuscanner.core.util.ImageUtils
import com.example.wajuscanner.domain.model.PdfOptions
import com.example.wajuscanner.domain.model.PdfPageSize
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rendering filters supported by the editor/PDF pipeline.
 */
enum class ScanFilter {
    ORIGINAL, AUTO, DOCUMENT, BLACK_WHITE, GRAYSCALE, HIGH_CONTRAST
}

@Singleton
class PdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Generate a PDF from image file paths, one PDF page per image, centred
     * and scaled to fit. Writes into app cache/exports.
     */
    fun generatePdf(
        documentName: String,
        imagePaths: List<String>,
        options: PdfOptions = PdfOptions(),
        filter: ScanFilter = ScanFilter.ORIGINAL
    ): Result<File> {
        if (imagePaths.isEmpty()) {
            return Result.failure(IllegalStateException("No images to generate PDF"))
        }
        return try {
            val (pageWidth, pageHeight) = resolvePageDimensions(options)
            val pdfDocument = PdfDocument()
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            imagePaths.forEachIndexed { index, path ->
                val bitmap = ImageUtils.loadBitmapCorrected(path, maxDimension = Constants.MAX_PREVIEW_DIMENSION)
                    ?: throw IOException("Failed to load image for PDF: $path")

                val processed = applyFilter(bitmap, filter)
                if (processed !== bitmap) bitmap.recycle()

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                drawBitmapCentered(page.canvas, processed, pageWidth, pageHeight, paint)
                pdfDocument.finishPage(page)

                if (processed != processed) { /* unreachable, kept for clarity */ }
                processed.recycle()
            }

            val outputDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outputFile = FileUtils.createUniqueFile(outputDir, documentName, Constants.PDF_EXTENSION)

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Apply a scan filter to a bitmap and return the (possibly new) bitmap.
     * Caller owns BOTH the returned bitmap and the source when they differ.
     */
    fun applyFilter(source: Bitmap, filter: ScanFilter): Bitmap = when (filter) {
        ScanFilter.ORIGINAL -> source
        ScanFilter.GRAYSCALE -> colorMatrixBitmap(source, grayscaleMatrix())
        ScanFilter.BLACK_WHITE -> colorMatrixBitmap(source, blackWhiteMatrix())
        ScanFilter.HIGH_CONTRAST -> colorMatrixBitmap(source, highContrastMatrix())
        ScanFilter.DOCUMENT -> colorMatrixBitmap(source, documentMatrix())
        ScanFilter.AUTO -> autoEnhance(source)
    }

    private fun colorMatrixBitmap(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun grayscaleMatrix() = ColorMatrix().apply { setSaturation(0f) }

    private fun highContrastMatrix() = ColorMatrix(
        floatArrayOf(
            1.3f, 0f, 0f, 0f, -32f,
            0f, 1.3f, 0f, 0f, -32f,
            0f, 0f, 1.3f, 0f, -32f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    private fun blackWhiteMatrix(): ColorMatrix {
        // Two-pass: grayscale first, then a hard threshold via contrast boost.
        val gray = ColorMatrix().apply { setSaturation(0f) }
        val threshold = ColorMatrix(
            floatArrayOf(
                2.4f, 0f, 0f, 0f, -190f,
                0f, 2.4f, 0f, 0f, -190f,
                0f, 0f, 2.4f, 0f, -190f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val combined = ColorMatrix()
        combined.setConcat(threshold, gray)
        return combined
    }

    private fun documentMatrix() = ColorMatrix(
        floatArrayOf(
            1.15f, 0f, 0f, 0f, -18f,   // brighter whites
            0f, 1.12f, 0f, 0f, -14f,   // lift green slightly for paper feel
            0f, 0f, 1.02f, 0f, -28f,   // cut blue haze / yellow tint
            0f, 0f, 0f, 1f, 0f
        )
    )

    private fun autoEnhance(source: Bitmap): Bitmap {
        // Document filter with a mild contrast nudge — safe default for scans.
        val docM = documentMatrix()
        val contrast = highContrastMatrix()
        val combined = ColorMatrix()
        combined.setConcat(contrast, docM)
        return colorMatrixBitmap(source, combined)
    }

    private fun drawBitmapCentered(
        canvas: Canvas,
        bitmap: Bitmap,
        pageWidth: Int,
        pageHeight: Int,
        paint: Paint
    ) {
        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val pageRatio = pageWidth.toFloat() / pageHeight.toFloat()

        val (drawWidth, drawHeight) = if (bitmapRatio > pageRatio) {
            pageWidth.toFloat() to (pageWidth / bitmapRatio)
        } else {
            (pageHeight * bitmapRatio) to pageHeight.toFloat()
        }

        val left = (pageWidth - drawWidth) / 2f
        val top = (pageHeight - drawHeight) / 2f
        canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + drawWidth, top + drawHeight), paint)
    }

    private fun resolvePageDimensions(options: PdfOptions): Pair<Int, Int> {
        val (width, height) = when (options.pageSize) {
            PdfPageSize.A4 -> Constants.A4_WIDTH_PTS to Constants.A4_HEIGHT_PTS
            PdfPageSize.LETTER -> Constants.LETTER_WIDTH_PTS to Constants.LETTER_HEIGHT_PTS
            PdfPageSize.ORIGINAL -> Constants.A4_WIDTH_PTS to Constants.A4_HEIGHT_PTS
        }
        return if (options.orientation == com.example.wajuscanner.domain.model.PdfOrientation.LANDSCAPE) {
            height to width
        } else {
            width to height
        }
    }

    fun getUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}