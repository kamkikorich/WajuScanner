package com.example.wajuscanner.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.wajuscanner.core.common.Constants
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Susun atur helaian Kad ID pada kertas A4 (rujuk [ImageUtils.idCardSheetLayout]).
 * Semua nilai dalam piksel.
 */
data class IdCardSheetLayout(
    val sheetWidth: Int,
    val sheetHeight: Int,
    val cardWidth: Int,
    val cardHeight: Int,
    val labelHeight: Int,
    val gap: Int,
    val startX: Int,
    val startY: Int,
)

object ImageUtils {

    /**
     * Loads a downsampled bitmap from a file path, respecting max dimension limits to avoid OOM.
     */
    fun loadSampledBitmap(path: String, maxDimension: Int = Constants.MAX_PREVIEW_DIMENSION): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDimension)
        options.inJustDecodeBounds = false
        val decoded = BitmapFactory.decodeFile(path, options) ?: return null

        // inSampleSize hanya kuasa 2; skala tepat supaya hasil betul-betul ≤ maxDimension
        // TANPA membuang resolusi berlebihan.
        val scaled = scaleDownIfNeeded(decoded, maxDimension)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    /**
     * Pilih `inSampleSize` (kuasa 2) yang TIDAK terlalu agresif: hanya dibahagi dua
     * selagi hasil bahagi itu masih >= [maxDimension]. Ini mengelak kehilangan
     * resolusi besar (cth. imej 2412px TIDAK lagi dipotong jadi 1206px).
     */
    fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        while ((width / inSampleSize) / 2 >= maxDimension ||
            (height / inSampleSize) / 2 >= maxDimension
        ) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun readExifOrientation(path: String): Int {
        return try {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    fun exifOrientationToDegrees(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    fun loadBitmapCorrected(path: String, maxDimension: Int = Constants.MAX_PREVIEW_DIMENSION): Bitmap? {
        val sampled = loadSampledBitmap(path, maxDimension) ?: return null
        val degrees = exifOrientationToDegrees(readExifOrientation(path))
        return if (degrees == 0) sampled else rotateBitmap(sampled, degrees)
    }

    fun saveBitmapJpeg(bitmap: Bitmap, outputFile: File, quality: Int = Constants.JPEG_QUALITY_BALANCED): Boolean {
        return try {
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(0, 100), out)
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    fun createThumbnail(sourcePath: String, outputFile: File): Boolean {
        val bitmap = loadSampledBitmap(sourcePath, Constants.MAX_THUMBNAIL_DIMENSION) ?: return false
        return saveBitmapJpeg(bitmap, outputFile, Constants.JPEG_QUALITY_BALANCED)
    }

    /**
     * Returns a scaled-down copy of a bitmap if it exceeds the target dimension.
     * Callers should manage the lifecycle of both source and returned bitmaps.
     */
    fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val larger = max(bitmap.width, bitmap.height)
        if (larger <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / larger
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth.coerceAtLeast(1), newHeight.coerceAtLeast(1), true)
    }

    /**
     * Susun atur helaian Kad ID: kad diletak pada saiz FIZIKAL sebenar
     * (85.6 × 54 mm, ISO/IEC 7810 ID-1) atas helaian bersaiz A4 — bukan
     * diregangkan memenuhi halaman. Ini kelakuan "ID copy" mesin fotokopi:
     * cetakan 1:1, jimat dakwat. Fungsi tulen (boleh diuji tanpa Android).
     */
    fun idCardSheetLayout(vertical: Boolean): IdCardSheetLayout {
        val sheetWidth = Constants.ID_SHEET_WIDTH_PX
        val sheetHeight = Constants.ID_SHEET_HEIGHT_PX
        val pxPerMm = sheetWidth.toFloat() / Constants.A4_WIDTH_MM
        val cardWidth = (Constants.ID_CARD_WIDTH_MM * pxPerMm).roundToInt()
        val cardHeight = (Constants.ID_CARD_HEIGHT_MM * pxPerMm).roundToInt()
        val labelHeight = (Constants.ID_CARD_LABEL_MM * pxPerMm).roundToInt()
        val gap = (Constants.ID_CARD_GAP_MM * pxPerMm).roundToInt()

        val cellHeight = cardHeight + labelHeight
        val blockWidth = if (vertical) cardWidth else cardWidth * 2 + gap
        val blockHeight = if (vertical) cellHeight * 2 + gap else cellHeight

        return IdCardSheetLayout(
            sheetWidth = sheetWidth,
            sheetHeight = sheetHeight,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            labelHeight = labelHeight,
            gap = gap,
            startX = (sheetWidth - blockWidth) / 2,
            startY = (sheetHeight - blockHeight) / 2,
        )
    }

    /**
     * Gabungkan dua sisi kad ID pada SATU helaian A4 dengan saiz cetakan 1:1
     * (rujuk [idCardSheetLayout]). Label sisi dilukis DI BAWAH setiap kad
     * supaya tidak menutupi teks kad. Pemanggil punya kitaran hidup bitmap ini.
     */
    fun composeIdCardSheet(
        front: Bitmap,
        back: Bitmap,
        vertical: Boolean,
        frontLabel: String,
        backLabel: String,
    ): Bitmap {
        val layout = idCardSheetLayout(vertical)
        val sheet = Bitmap.createBitmap(layout.sheetWidth, layout.sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)

        val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = layout.labelHeight * 0.62f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        listOf(front to frontLabel, back to backLabel).forEachIndexed { index, (bitmap, label) ->
            val left = if (vertical) {
                layout.startX
            } else {
                layout.startX + index * (layout.cardWidth + layout.gap)
            }
            val top = if (vertical) {
                layout.startY + index * (layout.cardHeight + layout.labelHeight + layout.gap)
            } else {
                layout.startY
            }
            drawCardContained(canvas, bitmap, left, top, layout.cardWidth, layout.cardHeight, imagePaint)
            canvas.drawText(
                label,
                left + (layout.cardWidth - labelPaint.measureText(label)) / 2f,
                top + layout.cardHeight + layout.labelHeight * 0.8f,
                labelPaint,
            )
        }
        return sheet
    }

    /** Lukis [bitmap] "contain" (kekalkan nisbah) di tengah kotak kad. */
    private fun drawCardContained(
        canvas: Canvas,
        bitmap: Bitmap,
        left: Int,
        top: Int,
        boxWidth: Int,
        boxHeight: Int,
        paint: Paint,
    ) {
        val scale = min(
            boxWidth.toFloat() / bitmap.width,
            boxHeight.toFloat() / bitmap.height,
        )
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val dx = left + (boxWidth - width) / 2f
        val dy = top + (boxHeight - height) / 2f
        canvas.drawBitmap(bitmap, null, RectF(dx, dy, dx + width, dy + height), paint)
    }

    /** Loads a downscaled, EXIF-corrected bitmap directly from a content URI. */
    fun loadBitmapFromUri(context: android.content.Context, uri: Uri, maxDimension: Int = Constants.MAX_PREVIEW_DIMENSION): Bitmap? {
        return try {
            val temp = File.createTempFile("waju_uri", ".jpg", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            val bmp = loadBitmapCorrected(temp.absolutePath, maxDimension)
            temp.delete()
            bmp
        } catch (_: Exception) {
            null
        }
    }
}