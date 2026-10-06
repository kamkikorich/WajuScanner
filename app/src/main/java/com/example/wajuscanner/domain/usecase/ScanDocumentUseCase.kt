package com.example.wajuscanner.domain.usecase

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.wajuscanner.core.common.Constants
import com.example.wajuscanner.core.util.FileUtils
import com.example.wajuscanner.core.util.ImageUtils
import com.example.wajuscanner.data.local.storage.DocumentStorage
import com.example.wajuscanner.data.repository.DocumentRepositoryImpl
import com.example.wajuscanner.domain.model.Page
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class ScanDocumentUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepositoryImpl,
    private val storage: DocumentStorage
) {
    /**
     * Creates a new document and persists scanned page images from content URIs.
     * Returns the new document id and the list of persisted Page domain models.
     */
    suspend operator fun invoke(
        documentName: String,
        pageUris: List<String>
    ): Result<Pair<Long, List<Page>>> = withContext(Dispatchers.IO) {
        try {
            val documentId = documentRepository.createDocument(documentName)
            val pages = pageUris.mapIndexed { index, uriString ->
                val uri = Uri.parse(uriString)
                val bitmap = ImageUtils.loadBitmapCorrected(
                    copyUriToTempFile(uri).absolutePath,
                    Constants.MAX_PREVIEW_DIMENSION
                ) ?: throw IllegalStateException("Failed to load scanned image: $uriString")

                val (imageFile, thumbnailFile) = storage.savePageImage(
                    documentId = documentId,
                    pageOrder = index,
                    bitmap = bitmap
                )

                val pageId = documentRepository.addPage(
                    documentId = documentId,
                    pageOrder = index,
                    imagePath = imageFile.absolutePath,
                    thumbnailPath = thumbnailFile?.absolutePath
                )

                Page(
                    id = pageId,
                    documentId = documentId,
                    pageOrder = index,
                    imagePath = imageFile.absolutePath,
                    thumbnailPath = thumbnailFile?.absolutePath,
                    width = bitmap.width,
                    height = bitmap.height
                )
            }
            Result.success(documentId to pages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun copyUriToTempFile(uri: Uri): File {
        val tempFile = storage.createTempFile(prefix = "scan_import", extension = Constants.IMAGE_EXTENSION)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open input stream for $uri")
        return tempFile
    }

    /**
     * ID-card mode: exactly two scanned sides (front + back) are combined
     * vertically into a SINGLE page (NAPS2-style "Combine"). Returns the new
     * document id and its one Page.
     */
    suspend fun invokeIdCardMode(
        documentName: String,
        pageUris: List<String>
    ): Result<Pair<Long, List<Page>>> = withContext(Dispatchers.IO) {
        try {
            if (pageUris.size < 2) {
                return@withContext Result.failure(
                    IllegalStateException("ID card mode requires 2 sides (front + back)")
                )
            }
            val front = ImageUtils.loadBitmapFromUri(context, Uri.parse(pageUris[0]))
                ?: return@withContext Result.failure(IllegalStateException("Failed to load front side"))
            val back = ImageUtils.loadBitmapFromUri(context, Uri.parse(pageUris[1]))
                ?: return@withContext Result.failure(IllegalStateException("Failed to load back side"))

            val combined = ImageUtils.combineVertical(front, back)

            val documentId = documentRepository.createDocument(documentName)
            val (imageFile, thumbnailFile) = storage.savePageImage(
                documentId = documentId,
                pageOrder = 0,
                bitmap = combined
            )
            val pageId = documentRepository.addPage(
                documentId = documentId,
                pageOrder = 0,
                imagePath = imageFile.absolutePath,
                thumbnailPath = thumbnailFile?.absolutePath
            )
            Result.success(
                documentId to listOf(
                    Page(
                        id = pageId,
                        documentId = documentId,
                        pageOrder = 0,
                        imagePath = imageFile.absolutePath,
                        thumbnailPath = thumbnailFile?.absolutePath,
                        width = combined.width,
                        height = combined.height
                    )
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
