package com.example.wajuscanner.domain.model

enum class PdfPageSize {
    A4, LETTER, ORIGINAL
}

enum class PdfOrientation {
    PORTRAIT, LANDSCAPE
}

data class PdfOptions(
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val orientation: PdfOrientation = PdfOrientation.PORTRAIT,
    val quality: Int = 85
)
