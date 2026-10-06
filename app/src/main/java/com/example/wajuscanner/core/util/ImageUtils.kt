package com.example.wajuscanner.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.wajuscanner.core.common.Constants
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

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
     * single bitmap, scaled to a common width. Used for ID-card mode: front and
     * back of the card on ONE page (same approach as NAPS2 "Combine").
     */
    fun combineVertical(top: Bitmap, bottom: Bitmap, dividerPx: Int = 8): Bitmap {
        val targetWidth = min(top.width, bottom.width)
        val topScaled = scaleToWidth(top, targetWidth)
        val bottomScaled = scaleToWidth(bottom, targetWidth)

        val totalHeight = topScaled.height + dividerPx + bottomScaled.height
        val combined = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(combined)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(topScaled, 0f, 0f, null)
        canvas.drawBitmap(bottomScaled, 0f, (topScaled.height + dividerPx).toFloat(), null)
        return combined
    }

    private fun scaleToWidth(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width == targetWidth) return bitmap
        val newHeight = (bitmap.height.toLong() * targetWidth / bitmap.width).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, newHeight.coerceAtLeast(1), true)
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
