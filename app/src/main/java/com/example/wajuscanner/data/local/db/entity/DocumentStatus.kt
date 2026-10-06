package com.example.wajuscanner.data.local.db.entity

/**
 * Lifecycle state of a document.
 *
 * - [DRAFT]: created during a scan session but not yet exported as PDF.
 * - [EXPORTED]: a PDF has been generated and saved to the user.
 */
enum class DocumentStatus(val value: Int) {
    DRAFT(0),
    EXPORTED(1);

    companion object {
        fun fromInt(value: Int): DocumentStatus =
            entries.firstOrNull { it.value == value } ?: DRAFT
    }
}