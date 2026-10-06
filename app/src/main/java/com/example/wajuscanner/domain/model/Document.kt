package com.example.wajuscanner.domain.model

import java.util.Date

data class Document(
    val id: Long = 0L,
    val name: String,
    val createdAt: Date,
    val updatedAt: Date,
    val pageCount: Int = 0,
    val thumbnailPath: String? = null,
    val isOcrProcessed: Boolean = false,
)

data class Page(
    val id: Long = 0L,
    val documentId: Long,
    val pageOrder: Int,
    val imagePath: String,
    val thumbnailPath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val ocrText: String? = null,
)
