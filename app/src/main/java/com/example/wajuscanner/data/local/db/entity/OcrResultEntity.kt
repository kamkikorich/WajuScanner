package com.example.wajuscanner.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ocr_results",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pageId"], unique = true)]
)
data class OcrResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val pageId: Long,
    val text: String,
    val processedAt: Long = System.currentTimeMillis()
)
