package com.example.wajuscanner.data.local.db.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Read-only projection used when we need document metadata plus page count in one query.
 */
data class DocumentWithPages(
    @Embedded val document: DocumentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "documentId"
    )
    val pages: List<PageEntity> = emptyList()
)
