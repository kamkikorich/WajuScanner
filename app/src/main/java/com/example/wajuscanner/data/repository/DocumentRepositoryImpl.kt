package com.example.wajuscanner.data.repository

import com.example.wajuscanner.core.util.FileUtils
import com.example.wajuscanner.data.local.db.dao.DocumentDao
import com.example.wajuscanner.data.local.db.dao.OcrResultDao
import com.example.wajuscanner.data.local.db.dao.PageDao
import com.example.wajuscanner.data.local.db.entity.DocumentEntity
import com.example.wajuscanner.data.local.db.entity.PageEntity
import com.example.wajuscanner.data.local.storage.DocumentStorage
import com.example.wajuscanner.domain.model.Document
import com.example.wajuscanner.domain.model.Page
import com.example.wajuscanner.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val ocrResultDao: OcrResultDao,
    private val storage: DocumentStorage
) : DocumentRepository {

    override fun observeDocuments(): Flow<List<Document>> {
        return documentDao.observeAll().map { entities ->
            entities.map { it.toDomain(0) } // Page count refreshed lazily or via separate query
        }
    }

    override fun searchDocuments(query: String): Flow<List<Document>> {
        return documentDao.search(query.trim()).map { entities ->
            entities.map { it.toDomain(0) }
        }
    }

    override suspend fun getDocumentById(id: Long): Document? {
        val entity = documentDao.getById(id) ?: return null
        val pageCount = pageDao.getByDocument(id).size
        return entity.toDomain(pageCount)
    }

    override suspend fun getPagesForDocument(documentId: Long): List<Page> {
        return pageDao.getByDocument(documentId).map { it.toDomain() }
    }

    override suspend fun createDocument(name: String): Long {
        val now = Date()
        val entity = DocumentEntity(
            name = name,
            createdAt = now,
            updatedAt = now
        )
        return documentDao.insert(entity)
    }

    override suspend fun renameDocument(id: Long, newName: String): Boolean {
        val entity = documentDao.getById(id) ?: return false
        documentDao.update(entity.copy(name = newName, updatedAt = Date()))
        return true
    }

    override suspend fun deleteDocument(id: Long): Boolean {
        val deletedRows = documentDao.deleteById(id)
        if (deletedRows > 0) {
            storage.deleteDocumentFiles(id)
        }
        return deletedRows > 0
    }

    override suspend fun searchIncludingOcr(query: String): List<Document> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val byName = documentDao.searchImmediate(trimmed)
        val ocrPageIds = ocrResultDao.searchOcrText(trimmed).map { it.pageId }.toSet()
        val ocrDocIds = if (ocrPageIds.isEmpty()) emptySet() else {
            pageDao.getByIds(ocrPageIds).map { it.documentId }.toSet()
        }
        val ids = (byName.map { it.id }.toSet() + ocrDocIds)
        if (ids.isEmpty()) return emptyList()
        return documentDao.getByIds(ids).map { it.toDomain(0) }
    }

    // Internal helpers used by other use cases (not part of public interface yet)
    internal suspend fun addPage(documentId: Long, pageOrder: Int, imagePath: String, thumbnailPath: String?): Long {
        val page = PageEntity(
            documentId = documentId,
            pageOrder = pageOrder,
            imagePath = imagePath,
            thumbnailPath = thumbnailPath
        )
        val pageId = pageDao.insert(page)

        // Bump document updatedAt when pages change
        val doc = documentDao.getById(documentId)
        if (doc != null) {
            documentDao.update(doc.copy(updatedAt = Date()))
        }
        return pageId
    }

    internal suspend fun getPageById(pageId: Long): PageEntity? = pageDao.getById(pageId)

    internal suspend fun updatePageOcrText(pageId: Long, text: String?) {
        val page = pageDao.getById(pageId) ?: return
        pageDao.update(page.copy(ocrText = text))
    }

    internal suspend fun updatePageOrder(pageId: Long, newOrder: Int) {
        val page = pageDao.getById(pageId) ?: return
        pageDao.update(page.copy(pageOrder = newOrder))
    }

    internal suspend fun deletePageById(pageId: Long): Boolean {
        val page = pageDao.getById(pageId) ?: return false
        val deletedRows = pageDao.deleteById(pageId)
        if (deletedRows > 0) {
            File(page.imagePath).delete()
            page.thumbnailPath?.let { File(it).delete() }
        }
        return deletedRows > 0
    }

    private fun DocumentEntity.toDomain(pageCount: Int): Document = Document(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pageCount = pageCount,
        isOcrProcessed = isOcrProcessed
    )

    private fun PageEntity.toDomain(): Page = Page(
        id = id,
        documentId = documentId,
        pageOrder = pageOrder,
        imagePath = imagePath,
        thumbnailPath = thumbnailPath,
        width = width,
        height = height,
        ocrText = ocrText
    )
}
