package com.example.wajuscanner.data.local.storage

import android.content.Context
import android.graphics.Bitmap
import com.example.wajuscanner.core.common.Constants
import com.example.wajuscanner.core.util.FileUtils
import com.example.wajuscanner.core.util.ImageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val documentsDir: File
        get() = FileUtils.ensurePrivateDirectory(context, Constants.DOCUMENTS_DIR)

    private val thumbnailsDir: File
        get() = FileUtils.ensurePrivateDirectory(context, Constants.THUMBNAILS_DIR)

    private val tempDir: File
        get() = FileUtils.ensurePrivateDirectory(context, Constants.TEMP_DIR)

    /**
     * Returns a directory for a specific document. Creates it if needed.
     */
    fun getDocumentDirectory(documentId: Long): File {
        val dir = File(documentsDir, documentId.toString())
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Saves a full page image and its thumbnail for a document. Files are named by page order.
     */
    fun savePageImage(
        documentId: Long,
        pageOrder: Int,
        bitmap: Bitmap,
        quality: Int = Constants.JPEG_QUALITY_PAGE
    ): Pair<File, File?> {
        val docDir = getDocumentDirectory(documentId)
        val imageFile = File(docDir, "page_${pageOrder}.${Constants.IMAGE_EXTENSION}")
        val saved = ImageUtils.saveBitmapJpeg(bitmap, imageFile, quality)
        if (!saved) throw IllegalStateException("Failed to save page image: ${imageFile.absolutePath}")

        val thumbnailFile = File(thumbnailsDir, "${documentId}_${pageOrder}.${Constants.THUMBNAIL_EXTENSION}")
        ImageUtils.createThumbnail(imageFile.absolutePath, thumbnailFile)

        return imageFile to thumbnailFile.takeIf { it.exists() }
    }

    /**
     * Deletes all files associated with a document, including its pages and thumbnails.
     */
    fun deleteDocumentFiles(documentId: Long): Boolean {
        val docDir = getDocumentDirectory(documentId)
        val deleted = docDir.deleteRecursively()

        val thumbnails = thumbnailsDir.listFiles { _, name ->
            name.startsWith("${documentId}_")
        } ?: emptyArray()
        val thumbsDeleted = thumbnails.all { it.delete() }

        return deleted && thumbsDeleted
    }

    /**
     * Clears temporary files created during scanning or export.
     */
    fun clearTempFiles(): Boolean {
        return FileUtils.deleteDirectoryContents(tempDir)
    }

    /**
     * Provides a fresh temporary file for intermediate processing.
     */
    fun createTempFile(prefix: String = "tmp", extension: String = Constants.IMAGE_EXTENSION): File {
        return File.createTempFile(prefix, ".$extension", tempDir)
    }

    /**
     * Returns the current total size of stored documents and thumbnails in bytes.
     */
    fun calculateStorageUsage(): Long {
        val docs = documentsDir.walkBottomUp().filter { it.isFile }.map { it.length() }.sum()
        val thumbs = thumbnailsDir.walkBottomUp().filter { it.isFile }.map { it.length() }.sum()
        return docs + thumbs
    }
}
