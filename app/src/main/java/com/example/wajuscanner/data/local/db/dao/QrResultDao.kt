package com.example.wajuscanner.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.wajuscanner.data.local.db.entity.QrResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QrResultDao {

    @Insert
    suspend fun insert(result: QrResultEntity): Long

    @Query("SELECT * FROM qr_results ORDER BY timestamp DESC LIMIT 100")
    fun observeHistory(): Flow<List<QrResultEntity>>

    @Query("DELETE FROM qr_results WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM qr_results")
    suspend fun clearAll(): Int
}
