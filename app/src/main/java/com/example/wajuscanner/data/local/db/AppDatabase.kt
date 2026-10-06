package com.example.wajuscanner.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.wajuscanner.data.local.db.converter.DateConverter
import com.example.wajuscanner.data.local.db.dao.DocumentDao
import com.example.wajuscanner.data.local.db.dao.OcrResultDao
import com.example.wajuscanner.data.local.db.dao.PageDao
import com.example.wajuscanner.data.local.db.entity.DocumentEntity
import com.example.wajuscanner.data.local.db.entity.OcrResultEntity
import com.example.wajuscanner.data.local.db.entity.PageEntity

@Database(
    entities = [DocumentEntity::class, PageEntity::class, OcrResultEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun ocrResultDao(): OcrResultDao
}
