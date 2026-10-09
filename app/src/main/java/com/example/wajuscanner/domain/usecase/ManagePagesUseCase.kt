package com.example.wajuscanner.domain.usecase

import com.example.wajuscanner.core.common.Constants
import com.example.wajuscanner.core.util.FileUtils
import com.example.wajuscanner.data.local.storage.DocumentStorage
import com.example.wajuscanner.data.repository.DocumentRepositoryImpl
import com.example.wajuscanner.domain.model.Page
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ManagePagesUseCase @Inject constructor(
    private val documentRepository: DocumentRepositoryImpl,
    private val storage: DocumentStorage
) {

    /**
     * Reorders pages by updating their pageOrder values sequentially.
     */
    suspend fun reorderPages(documentId: Long, orderedPageIds: List<Long>): Result<List<Page>> {
        return withContext(Dispatchers.IO) {
            try {
                val existingPages = documentRepository.getPagesForDocument(documentId)
                val pageMap = existingPages.associateBy { it.id }

                orderedPageIds.forEachIndexed { index, pageId ->
                    val page = pageMap[pageId]
                    if (page != null) {
                        documentRepository.updatePageOrder(pageId, index)
                    }
                }

                Result.success(documentRepository.getPagesForDocument(documentId))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Deletes a single page and reorders remaining pages.
     */
    suspend fun deletePage(documentId: Long, pageId: Long): Result<List<Page>> {
        return withContext(Dispatchers.IO) {
            try {
                documentRepository.deletePageById(pageId)
                Result.success(documentRepository.getPagesForDocument(documentId))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Duplicates a page by copying its image file and inserting a new Page record.
     */
    suspend fun duplicatePage(documentId: Long, pageId: Long): Result<List<Page>> {
        return withContext(Dispatchers.IO) {
            try {
                val page = documentRepository.getPagesForDocument(documentId)
                    .find { it.id == pageId }
                    ?: return@withContext Result.failure(IllegalStateException("Page not found"))

                val docDir = storage.getDocumentDirectory(documentId)
                val sourceFile = File(page.imagePath)
                val newOrder = documentRepository.getPagesForDocument(documentId).size
                val destFile = FileUtils.createUniqueFile(
                    docDir,
                    "page_${newOrder}",
                    Constants.IMAGE_EXTENSION
                )

                sourceFile.copyTo(destFile, overwrite = false)

                documentRepository.addPage(
                    documentId = documentId,
                    pageOrder = newOrder,
                    imagePath = destFile.absolutePath,
                    thumbnailPath = null
                )

                Result.success(documentRepository.getPagesForDocument(documentId))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
