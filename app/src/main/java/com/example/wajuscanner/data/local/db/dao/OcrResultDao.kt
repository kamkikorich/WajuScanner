package com.example.wajuscanner.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.wajuscanner.data.local.db.entity.OcrResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrResultDao {

    @Query("SELECT * FROM ocr_results WHERE pageId = :pageId LIMIT 1")
    suspend fun getByPageId(pageId: Long): OcrResultEntity?

    @Query("SELECT * FROM ocr_results WHERE pageId = :pageId LIMIT 1")
    fun observeByPageId(pageId: Long): Flow<OcrResultEntity?>

    @Upsert
    suspend fun upsert(result: OcrResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: OcrResultEntity): Long

    @Query("DELETE FROM ocr_results WHERE pageId = :pageId")
    suspend fun deleteByPageId(pageId: Long): Int

    /** Search across all saved OCR text. */
    @Query(
        """
        SELECT * FROM ocr_results
        WHERE text LIKE '%' || :query || '%'
        ORDER BY processedAt DESC
        LIMIT 100
        """
    )
    suspend fun searchOcrText(query: String): List<OcrResultEntity>
}