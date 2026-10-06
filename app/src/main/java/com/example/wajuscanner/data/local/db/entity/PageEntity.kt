package com.example.wajuscanner.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId", "pageOrder"])]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val documentId: Long,
    val pageOrder: Int,
    val imagePath: String,
    val thumbnailPath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val ocrText: String? = null
)
