package com.example.wajuscanner.domain.usecase

import android.graphics.PointF
import com.example.wajuscanner.core.common.Constants
import com.example.wajuscanner.core.util.ImageUtils
import com.example.wajuscanner.data.local.storage.DocumentStorage
import com.example.wajuscanner.data.repository.DocumentRepositoryImpl
import com.example.wajuscanner.domain.model.DocumentFilter
import com.example.wajuscanner.image.DocumentTransform
import com.example.wajuscanner.image.FilterProcessor
import java.io.File
import javax.inject.Inject

class ProcessPageUseCase @Inject constructor(
    private val documentRepository: DocumentRepositoryImpl,
    private val storage: DocumentStorage
) {

    /**
     * Loads a page image, applies perspective crop, rotation, filter, and overwrites the saved image.
     */
    suspend fun applyEdit(
        pageId: Long,
        corners: List<PointF>? = null,
        rotationDegrees: Int = 0,
        filter: DocumentFilter = DocumentFilter.ORIGINAL
    ): Result<String> {
        return try {
            val page = documentRepository.getPageById(pageId)
                ?: return Result.failure(IllegalStateException("Page not found: $pageId"))

            var bitmap = ImageUtils.loadBitmapCorrected(page.imagePath)
                ?: return Result.failure(IllegalStateException("Failed to load page image"))

            // Apply perspective crop if corners provided (4 points)
            if (corners != null && corners.size == 4) {
                bitmap = DocumentTransform.perspectiveCrop(
                    source = bitmap,
                    topLeft = corners[0],
                    topRight = corners[1],
                    bottomRight = corners[2],
                    bottomLeft = corners[3]
                )
            }

            // Apply rotation
            if (rotationDegrees != 0) {
                bitmap = DocumentTransform.rotate(bitmap, rotationDegrees)
            }

            // Apply filter (returns same instance for ORIGINAL)
            bitmap = FilterProcessor.applyFilter(bitmap, filter)

            // Overwrite original page file with processed image
            val outputFile = File(page.imagePath)
            val saved = ImageUtils.saveBitmapJpeg(bitmap, outputFile, Constants.JPEG_QUALITY_BALANCED)
            if (!saved) {
                return Result.failure(IllegalStateException("Failed to save processed page"))
            }

            // Regenerate thumbnail
            val documentDirParent = storage.getDocumentDirectory(page.documentId).parentFile
            val thumbnailFile = documentDirParent?.let {
                File(
                    it,
                    "${Constants.THUMBNAILS_DIR}/${page.documentId}_${page.pageOrder}.${Constants.THUMBNAIL_EXTENSION}"
                )
            }
            thumbnailFile?.parentFile?.mkdirs()
            thumbnailFile?.let { ImageUtils.createThumbnail(page.imagePath, it) }

            Result.success(page.imagePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
