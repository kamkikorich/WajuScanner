package com.example.wajuscanner.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
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

object ImageUtils {

    private enum class CombineOrientation { VERTICAL, HORIZONTAL }

    /**
     * Loads a downsampled bitmap from a file path, respecting max dimension limits to avoid OOM.
     */
    fun loadSampledBitmap(path: String, maxDimension: Int = Constants.MAX_PREVIEW_DIMENSION): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDimension)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
    }

    fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        while ((width / inSampleSize) > maxDimension || (height / inSampleSize) > maxDimension) {
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
     * Combines two bitmaps vertically (top = [top], bottom = [bottom]) into a
     * single bitmap, scaled to a common width. Used for ID-card mode: front
     * and back of the card on ONE page (same approach as NAPS2 "Combine").
     */
    fun combineVertical(top: Bitmap, bottom: Bitmap, dividerPx: Int = 8): Bitmap {
        return combine(listOf(top, bottom), CombineOrientation.VERTICAL, dividerPx)
    }

    /**
     * Combines two bitmaps horizontally ([left], [right]) into a single bitmap,
     * scaled to a common height. Useful for wide-format ID cards (driver's
     * license) where the front and back read better side-by-side.
     */
    fun combineHorizontal(left: Bitmap, right: Bitmap, dividerPx: Int = 8): Bitmap {
        return combine(listOf(left, right), CombineOrientation.HORIZONTAL, dividerPx)
    }

    /**
     * Combines any number of bitmaps in the given orientation onto a single
     * bitmap with a uniform background and equal-sided dividers between them.
     * All bitmaps are scaled to a common dimension (width for vertical, height
     * for horizontal) so the final canvas is rectangular and predictable.
     */
    private fun combine(
        bitmaps: List<Bitmap>,
        orientation: CombineOrientation,
        dividerPx: Int,
    ): Bitmap {
        require(bitmaps.isNotEmpty()) { "combine requires at least one bitmap" }
        if (bitmaps.size == 1) return bitmaps.first()

        val isVertical = orientation == CombineOrientation.VERTICAL
        val targetDim = if (isVertical) {
            bitmaps.minOf { it.width }
        } else {
            bitmaps.minOf { it.height }
        }

        val scaled = bitmaps.map { src ->
            if (isVertical) scaleToWidth(src, targetDim) else scaleToHeight(src, targetDim)
        }

        val totalCrossDim = scaled.sumOf { if (isVertical) it.height else it.width }
        val totalDivider = dividerPx * (scaled.size - 1)

        val canvasWidth: Int
        val canvasHeight: Int
        if (isVertical) {
            canvasWidth = targetDim
            canvasHeight = totalCrossDim + totalDivider
        } else {
            canvasWidth = totalCrossDim + totalDivider
            canvasHeight = targetDim
        }

        val combined = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)
        canvas.drawColor(Color.WHITE)

        var cursor = 0
        scaled.forEachIndexed { index, bmp ->
            val offset: Int
            if (isVertical) {
                canvas.drawBitmap(bmp, 0f, cursor.toFloat(), null)
                offset = bmp.height
            } else {
                canvas.drawBitmap(bmp, cursor.toFloat(), 0f, null)
                offset = bmp.width
            }
            cursor += offset
            if (index < scaled.size - 1) cursor += dividerPx
        }
        return combined
    }

    private fun scaleToWidth(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width == targetWidth) return bitmap
        val newHeight = (bitmap.height.toLong() * targetWidth / bitmap.width).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, newHeight.coerceAtLeast(1), true)
    }

    private fun scaleToHeight(bitmap: Bitmap, targetHeight: Int): Bitmap {
        if (bitmap.height == targetHeight) return bitmap
        val newWidth = (bitmap.width.toLong() * targetHeight / bitmap.height).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth.coerceAtLeast(1), targetHeight, true)
    }

    /**
     * Menggantung [label] sebagai jalur keterangan di BAWAH imej sisi kad,
     * bukan timbul di atas kad. Ini menjamin apa-apa teks ID (lambang JPN,
     * nombor, tulisan kecil) tidak dilindungi oleh latar hitam label.
     * Kembali bitmap BARU; pemanggil punya kitaran hidupnya.
     */
    fun captionStrip(source: Bitmap, label: String): Bitmap {
        val stripHeight = (source.height * 0.06f).toInt().coerceAtLeast(28)
        val textSize = (stripHeight * 0.52f).coerceAtLeast(16f)
        val stripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 30, 30, 30)
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val bounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, bounds)

        val out =
            Bitmap.createBitmap(source.width, source.height + stripHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, 0f, 0f, null)
        canvas.drawRect(
            0f,
            source.height.toFloat(),
            source.width.toFloat(),
            out.height.toFloat(),
            stripPaint,
        )
        canvas.drawText(
            label,
            (source.width - bounds.width()) / 2f,
            source.height + (stripHeight + bounds.height()) / 2f,
            textPaint,
        )
        return out
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