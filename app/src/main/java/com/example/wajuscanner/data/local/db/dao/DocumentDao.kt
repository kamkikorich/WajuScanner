package com.example.wajuscanner.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.wajuscanner.data.local.db.entity.DocumentEntity
import com.example.wajuscanner.data.local.db.entity.DocumentWithPages
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE name LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE status = :status ORDER BY updatedAt DESC")
    fun observeByStatus(status: Int): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE status = :status ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getMostRecentByStatus(status: Int): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE name LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    suspend fun searchImmediate(query: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id IN (:ids)")
    suspend fun getByIds(ids: Set<Long>): List<DocumentEntity>

    @Insert
    suspend fun insert(document: DocumentEntity): Long

    @Update
    suspend fun update(document: DocumentEntity): Int

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT COUNT(*) FROM pages WHERE documentId = :documentId")
    suspend fun countPages(documentId: Long): Int

    @Query("""
        SELECT d.*, COUNT(p.id) as pageCount
        FROM documents d
        LEFT JOIN pages p ON d.id = p.documentId
        WHERE d.id = :documentId
        GROUP BY d.id
    """)
    suspend fun getDocumentWithPageCount(documentId: Long): DocumentWithPages?

    companion object {
        // Convenience wrapper for repository usage
        suspend fun DocumentDao.countPagesForDocument(documentId: Long): Int = countPages(documentId)
    }
}
