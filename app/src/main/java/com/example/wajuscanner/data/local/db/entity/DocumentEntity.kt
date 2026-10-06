package com.example.wajuscanner.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "documents",
    indices = [Index(value = ["name"])]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val createdAt: Date,
    val updatedAt: Date,
    val isOcrProcessed: Boolean = false
)
