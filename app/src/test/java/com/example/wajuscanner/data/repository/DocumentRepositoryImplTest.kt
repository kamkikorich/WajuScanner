package com.example.wajuscanner.data.repository

import android.content.Context
import com.example.wajuscanner.data.local.db.dao.DocumentDao
import com.example.wajuscanner.data.local.db.dao.OcrResultDao
import com.example.wajuscanner.data.local.db.dao.PageDao
import com.example.wajuscanner.data.local.db.entity.DocumentEntity
import com.example.wajuscanner.data.local.db.entity.PageEntity
import com.example.wajuscanner.data.local.storage.DocumentStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DocumentRepositoryImplTest {

    private lateinit var repository: DocumentRepositoryImpl
    private lateinit var fakeStorage: DocumentStorage

    @Before
    fun setup() {
        val documentDao = FakeDocumentDao()
        val pageDao = FakePageDao()
        fakeStorage = DocumentStorage(org.mockito.Mockito.mock(Context::class.java))
        repository = DocumentRepositoryImpl(documentDao, pageDao, FakeOcrResultDao(), fakeStorage)
    }

    @Test
    fun `observeDocuments returns mapped domain list`() = runBlocking {
        repository.createDocument("Invoice")
        repository.createDocument("Receipt")

        val result = repository.observeDocuments().first()
        assertEquals(2, result.size)
    }

    @Test
    fun `renameDocument updates name`() = runBlocking {
        val id = repository.createDocument("Old Name")
        val renamed = repository.renameDocument(id, "New Name")
        assertTrue(renamed)

        val doc = repository.getDocumentById(id)
        assertEquals("New Name", doc?.name)
    }

    @Test
    fun `deleteDocument removes db row`() = runBlocking {
        val id = repository.createDocument("To Delete")
        val result = repository.deleteDocument(id)
        assertTrue(result)
        assertEquals(null, repository.getDocumentById(id))
    }

    private class FakeDocumentDao : DocumentDao {
        private val items = mutableListOf<DocumentEntity>()
        private var nextId = 1L

        override fun observeAll(): Flow<List<DocumentEntity>> = flowOf(items.toList())

        override fun search(query: String): Flow<List<DocumentEntity>> =
            flowOf(items.filter { it.name.contains(query, ignoreCase = true) })

        override fun observeByStatus(status: Int): Flow<List<DocumentEntity>> =
            flowOf(items.filter { it.status == status })

        override suspend fun getMostRecentByStatus(status: Int): DocumentEntity? =
            items.filter { it.status == status }.maxByOrNull { it.updatedAt }

        override suspend fun getById(id: Long): DocumentEntity? = items.find { it.id == id }

        override suspend fun insert(document: DocumentEntity): Long {
            val id = if (document.id == 0L) nextId++ else document.id
            items.add(document.copy(id = id))
            return id
        }

        override suspend fun update(document: DocumentEntity): Int {
            val index = items.indexOfFirst { it.id == document.id }
            if (index == -1) return 0
            items[index] = document
            return 1
        }

        override suspend fun deleteById(id: Long): Int {
            return if (items.removeAll { it.id == id }) 1 else 0
        }

        override suspend fun countPages(documentId: Long): Int = 0
        override suspend fun getDocumentWithPageCount(documentId: Long) = null
        override suspend fun searchImmediate(query: String): List<DocumentEntity> =
            items.filter { it.name.contains(query, ignoreCase = true) }
        override suspend fun getByIds(ids: Set<Long>): List<DocumentEntity> =
            items.filter { it.id in ids }
    }

    private class FakePageDao : PageDao {
        override fun observeByDocument(documentId: Long) = flowOf(emptyList<PageEntity>())
        override suspend fun getByDocument(documentId: Long) = emptyList<PageEntity>()
        override suspend fun getById(id: Long) = null
        override suspend fun getByIds(ids: Set<Long>) = emptyList<PageEntity>()
        override suspend fun insert(page: PageEntity) = 1L
        override suspend fun insertAll(pages: List<PageEntity>) = emptyList<Long>()
        override suspend fun update(page: PageEntity) = 1
        override suspend fun deleteById(id: Long) = 1
        override suspend fun deleteByDocument(documentId: Long) = 1
    }

    private class FakeOcrResultDao : OcrResultDao {
        override suspend fun getByPageId(pageId: Long) = null
        override fun observeByPageId(pageId: Long) = kotlinx.coroutines.flow.flowOf(null)
        override suspend fun upsert(result: com.example.wajuscanner.data.local.db.entity.OcrResultEntity) {}
        override suspend fun insert(result: com.example.wajuscanner.data.local.db.entity.OcrResultEntity) = 1L
        override suspend fun deleteByPageId(pageId: Long) = 1
        override suspend fun searchOcrText(query: String) =
            emptyList<com.example.wajuscanner.data.local.db.entity.OcrResultEntity>()
    }
}
