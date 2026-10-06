package com.example.wajuscanner.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import com.example.wajuscanner.core.common.Constants
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object BitmapUtils {

    /**
     * Decode an image file path to Bitmap, sampling down to fit within
     * [maxDim] to avoid OOM on large scans.
     */
    fun decodeSampledBitmap(path: String, maxDim: Int = Constants.MAX_PREVIEW_DIMENSION): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        var sampleSize = 1
        while ((opts.outWidth / sampleSize) > maxDim || (opts.outHeight / sampleSize) > maxDim) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val raw = BitmapFactory.decodeFile(path, decodeOpts) ?: return null

        val larger = max(raw.width, raw.height)
        if (larger <= maxDim) return raw
        val scale = maxDim.toFloat() / larger
        val scaled = Bitmap.createScaledBitmap(
            raw,
            (raw.width * scale).toInt().coerceAtLeast(1),
            (raw.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== raw) raw.recycle()
        return scaled
    }

    /**
     * Crop a bitmap to the specified [rect] (absolute pixels), clamped to bounds.
     */
    fun crop(bitmap: Bitmap, rect: Rect): Bitmap {
        val left = rect.left.coerceAtLeast(0)
        val top = rect.top.coerceAtLeast(0)
        val width = min(rect.width(), bitmap.width - left).coerceAtLeast(1)
        val height = min(rect.height(), bitmap.height - top).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    /**
     * Write a bitmap to [outFile] as JPEG at [quality], returning success.
     */
    fun writeBitmapAsJpeg(bitmap: Bitmap, outFile: File, quality: Int = Constants.JPEG_QUALITY_BALANCED): Boolean {
        return try {
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(0, 100), out)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Estimate an average brightness of the bitmap (0-255) using a downscaled copy.
     */
    fun estimateBrightness(bitmap: Bitmap): Int {
        val larger = max(bitmap.width, bitmap.height)
        val scale = 64f / larger
        val thumb = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        var total = 0L
        var samples = 0L
        for (y in 0 until thumb.height step 4) {
            for (x in 0 until thumb.width step 4) {
                val px = thumb.getPixel(x, y)
                total += (Color.red(px) + Color.green(px) + Color.blue(px)) / 3
                samples++
            }
        }
        if (thumb !== bitmap) thumb.recycle()
        return (total / samples.coerceAtLeast(1L)).toInt()
    }
}