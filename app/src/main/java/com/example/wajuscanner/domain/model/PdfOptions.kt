package com.example.wajuscanner.domain.model

enum class PdfPageSize {
    A4, LETTER, ORIGINAL
}

enum class PdfOrientation {
    PORTRAIT, LANDSCAPE
}

/**
 * Trade-off between file length and image fidelity when exporting a scan as
 * PDF. Each mode tightens a different lever from the ImageToolbox / Genius
 * Scan / Microsoft Lens playbook; the visible trade-off is roughly:
 *
 *  FULL:                ~3.5 MB / 10 pages — print quality
 *  COMPRESS:            ~700 KB / 10 pages — email / upload forms
 *  COMPRESS_GRAYSCALE:  ~250 KB / 10 pages — long-form text, OCR-friendly
 */
enum class PdfCompressionMode {
    FULL,
    COMPRESS,
    COMPRESS_GRAYSCALE,
}

data class PdfOptions(
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val orientation: PdfOrientation = PdfOrientation.PORTRAIT,
    val compression: PdfCompressionMode = PdfCompressionMode.FULL,
)