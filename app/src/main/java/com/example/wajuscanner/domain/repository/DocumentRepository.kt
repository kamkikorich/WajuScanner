package com.example.wajuscanner.domain.repository

import com.example.wajuscanner.domain.model.Document
import com.example.wajuscanner.domain.model.Page
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>
    fun searchDocuments(query: String): Flow<List<Document>>
    fun observeDrafts(): Flow<List<Document>>
    suspend fun getMostRecentDraft(): Document?
    suspend fun getDocumentById(id: Long): Document?
    suspend fun getPagesForDocument(documentId: Long): List<Page>
    suspend fun createDocument(name: String): Long
    suspend fun renameDocument(id: Long, newName: String): Boolean
    suspend fun deleteDocument(id: Long): Boolean
    suspend fun markAsExported(id: Long): Boolean
    /** Documents whose name OR any page's OCR text matches [query]. */
    suspend fun searchIncludingOcr(query: String): List<Document>
}
