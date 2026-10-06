package com.example.wajuscanner.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.wajuscanner.data.local.db.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageOrder ASC")
    fun observeByDocument(documentId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageOrder ASC")
    suspend fun getByDocument(documentId: Long): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PageEntity?

    @Query("SELECT * FROM pages WHERE id IN (:ids)")
    suspend fun getByIds(ids: Set<Long>): List<PageEntity>

    @Insert
    suspend fun insert(page: PageEntity): Long

    @Insert
    suspend fun insertAll(pages: List<PageEntity>): List<Long>

    @Update
    suspend fun update(page: PageEntity): Int

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM pages WHERE documentId = :documentId")
    suspend fun deleteByDocument(documentId: Long): Int
}
