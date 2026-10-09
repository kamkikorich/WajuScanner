package com.example.wajuscanner.data.local.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wajuscanner.data.local.db.entity.DocumentStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ujian migrasi Room: binakan DB pada versi lama dengan DDL sebenar, kemudian
 * buka melalui [Room] dengan migration sebenar. Room mengesahkan skema hasil
 * migrasi terhadap entiti semasa dalam `onUpgrade` — jadi ujian ini gagal jika
 * migration tidak menghasilkan skema yang betul, atau data hilang.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "wajuscanner_migration_test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrateFromV1_toCurrent_preservesDataAndSchema() {
        createDatabaseAtV1()

        val db = openWithMigrations()
        val readable = db.openHelper.readableDatabase

        readable.query("SELECT `name`, `status` FROM `documents` WHERE `id` = 1").use { cursor ->
            assertTrue("Dokumen lama mesti kekal selepas migrasi", cursor.moveToFirst())
            assertEquals("Legacy doc", cursor.getString(0))
            // MIGRATION_1_2 menandakan dokumen lama sebagai EXPORTED.
            assertEquals(DocumentStatus.EXPORTED.value, cursor.getInt(1))
        }

        assertTrue("qr_results mesti wujud selepas MIGRATION_2_3", tableExists(readable, "qr_results"))
        db.close()
    }

    @Test
    fun migrateFromV2_toCurrent_preservesDataAndSchema() {
        createDatabaseAtV2()

        val db = openWithMigrations()
        val readable = db.openHelper.readableDatabase

        readable.query("SELECT `name`, `status` FROM `documents` WHERE `id` = 1").use { cursor ->
            assertTrue("Dokumen lama mesti kekal selepas migrasi", cursor.moveToFirst())
            assertEquals("Legacy doc", cursor.getString(0))
            assertEquals(DocumentStatus.DRAFT.value, cursor.getInt(1))
        }

        assertTrue("qr_results mesti wujud selepas MIGRATION_2_3", tableExists(readable, "qr_results"))
        db.close()
    }

    private fun tableExists(db: androidx.sqlite.db.SupportSQLiteDatabase, name: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    /** Buka DB mentah dengan Room + migration sebenar; Room mengesahkan skema. */
    private fun openWithMigrations(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
            .also { it.openHelper.writableDatabase }

    private fun createDatabaseAtV1() = createRawDatabase(version = 1) { db ->
        createDocuments(db, withStatus = false)
        createPages(db)
        createOcrResults(db)
        db.execSQL(
            "INSERT INTO `documents` (`name`, `createdAt`, `updatedAt`, `isOcrProcessed`) " +
                "VALUES ('Legacy doc', 0, 0, 0)"
        )
    }

    private fun createDatabaseAtV2() = createRawDatabase(version = 2) { db ->
        createDocuments(db, withStatus = true)
        createPages(db)
        createOcrResults(db)
        db.execSQL(
            "INSERT INTO `documents` (`name`, `createdAt`, `updatedAt`, `isOcrProcessed`, `status`) " +
                "VALUES ('Legacy doc', 0, 0, 0, 0)"
        )
    }

    private fun createRawDatabase(version: Int, block: (SQLiteDatabase) -> Unit) {
        context.deleteDatabase(dbName)
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            block(db)
            db.version = version
        } finally {
            db.close()
        }
    }

    private fun createDocuments(db: SQLiteDatabase, withStatus: Boolean) {
        val statusColumn = if (withStatus) ", `status` INTEGER NOT NULL DEFAULT 0" else ""
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `documents` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`isOcrProcessed` INTEGER NOT NULL$statusColumn)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_name` ON `documents` (`name`)")
    }

    private fun createPages(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pages` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`documentId` INTEGER NOT NULL, " +
                "`pageOrder` INTEGER NOT NULL, " +
                "`imagePath` TEXT NOT NULL, " +
                "`thumbnailPath` TEXT, " +
                "`width` INTEGER NOT NULL, " +
                "`height` INTEGER NOT NULL, " +
                "`ocrText` TEXT, " +
                "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pages_documentId_pageOrder` " +
                "ON `pages` (`documentId`, `pageOrder`)"
        )
    }

    private fun createOcrResults(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ocr_results` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`pageId` INTEGER NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`processedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`pageId`) REFERENCES `pages`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_ocr_results_pageId` " +
                "ON `ocr_results` (`pageId`)"
        )
    }
}
