package com.example.wajuscanner.image

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import androidx.core.graphics.withSave
import com.example.wajuscanner.core.common.Constants
import com.example.wajuscanner.core.util.ImageUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object DocumentTransform {

    /**
     * Crops and perspective-corrects a bitmap given four source corner points.
     * Points are in bitmap pixel coordinates.
     */
    fun perspectiveCrop(
        source: Bitmap,
        topLeft: PointF,
        topRight: PointF,
        bottomRight: PointF,
        bottomLeft: PointF,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap {
        val ordered = listOf(topLeft, topRight, bottomRight, bottomLeft)
        val (width, height) = if (targetWidth > 0 && targetHeight > 0) {
            targetWidth to targetHeight
        } else {
            estimateOutputSize(ordered)
        }

        val srcPoints = floatArrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y
        )
        val dstPoints = floatArrayOf(
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat()
        )

        val matrix = Matrix().apply {
            setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
        }

        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            ?: throw IllegalStateException("Perspective transform produced null bitmap")
    }

    /**
     * Estimates output width/height from the average edge lengths of a quadrilateral.
     */
    private fun estimateOutputSize(points: List<PointF>): Pair<Int, Int> {
        val top = distance(points[0], points[1])
        val right = distance(points[1], points[2])
        val bottom = distance(points[2], points[3])
        val left = distance(points[3], points[0])
        val width = ((top + bottom) / 2).toInt().coerceAtLeast(1)
        val height = ((left + right) / 2).toInt().coerceAtLeast(1)
        return width to height
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        return ImageUtils.rotateBitmap(bitmap, degrees)
    }

    /**
     * Scales a bitmap down if it exceeds the max dimension, preserving aspect ratio.
     */
    fun scaleIfTooLarge(bitmap: Bitmap, maxDimension: Int = Constants.MAX_PREVIEW_DIMENSION): Bitmap {
        return ImageUtils.scaleDownIfNeeded(bitmap, maxDimension)
    }
}
