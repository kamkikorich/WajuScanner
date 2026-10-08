package com.example.wajuscanner.domain.model

enum class DocumentFilter(val displayName: String) {
    ORIGINAL("Original"),
    AUTO("Auto"),
    DOCUMENT("Document"),
    WHITEN("Buang Gelap"),
    BLACK_AND_WHITE("Black \u0026 White"),
    GRAYSCALE("Grayscale"),
    HIGH_CONTRAST("High Contrast")
}
