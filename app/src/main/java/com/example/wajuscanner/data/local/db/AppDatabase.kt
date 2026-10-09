package com.example.wajuscanner.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.wajuscanner.data.local.db.converter.DateConverter
import com.example.wajuscanner.data.local.db.dao.DocumentDao
import com.example.wajuscanner.data.local.db.dao.OcrResultDao
import com.example.wajuscanner.data.local.db.dao.PageDao
import com.example.wajuscanner.data.local.db.dao.QrResultDao
import com.example.wajuscanner.data.local.db.entity.DocumentEntity
import com.example.wajuscanner.data.local.db.entity.OcrResultEntity
import com.example.wajuscanner.data.local.db.entity.PageEntity
import com.example.wajuscanner.data.local.db.entity.QrResultEntity

@Database(
    entities = [DocumentEntity::class, PageEntity::class, OcrResultEntity::class, QrResultEntity::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun ocrResultDao(): OcrResultDao
    abstract fun qrResultDao(): QrResultDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE documents ADD COLUMN status INTEGER NOT NULL DEFAULT 0")
        // Dokumen lama (pra-ciri ini) sudah siap diexport — tanda sebagai EXPORTED
        // supaya banner "Sambung imbasan" tidak memapar dokumen lama.
        db.execSQL("UPDATE documents SET status = 1")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Sejarah imbasan QR (CamScanner parity: HASIL TIDAK hilang lagi dahulu).
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS qr_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                content TEXT NOT NULL,
                format INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}