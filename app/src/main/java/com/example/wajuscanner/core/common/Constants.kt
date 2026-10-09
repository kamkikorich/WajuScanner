package com.example.wajuscanner.core.common

object Constants {
    const val APP_TAG = "SmartScanner"

    // Storage directories (relative to app-private files dir)
    const val DOCUMENTS_DIR = "documents"
    const val THUMBNAILS_DIR = "thumbnails"
    const val TEMP_DIR = "temp"

    // Filename defaults
    const val DEFAULT_FILE_PREFIX = "Scan"
    const val PDF_EXTENSION = "pdf"
    const val IMAGE_EXTENSION = "jpg"
    const val THUMBNAIL_EXTENSION = "jpg"

    // PDF page sizes (points, 72 dpi)
    const val A4_WIDTH_PTS = 595
    const val A4_HEIGHT_PTS = 842
    const val LETTER_WIDTH_PTS = 612
    const val LETTER_HEIGHT_PTS = 792

    // Bitmap memory safety
    const val MAX_PREVIEW_DIMENSION = 2048
    /** Halaman yang disimpan untuk fail dokumen: kekalkan lebih butiran. */
    const val MAX_STORED_PAGE_DIMENSION = 2560
    const val MAX_THUMBNAIL_DIMENSION = 512
    const val JPEG_QUALITY_MAX = 95
    const val JPEG_QUALITY_BALANCED = 85
    /** Kualiti JPEG untuk halaman yang disimpan (teks perlu lebih tajam). */
    const val JPEG_QUALITY_PAGE = 92

    // Kad ID dicetak pada saiz fizikal sebenar (ISO/IEC 7810 ID-1) atas helaian A4.
    // Standard cetakan kad = 300 dpi → A4 = 2480 × 3508 px, kad = 1011 × 638 px.
    const val ID_CARD_WIDTH_MM = 85.6f
    const val ID_CARD_HEIGHT_MM = 54f
    const val A4_WIDTH_MM = 210f
    const val ID_CARD_LABEL_MM = 6f
    const val ID_CARD_GAP_MM = 6f
    /** Helaian A4 pada 300 dpi — standard cetakan kad ID. */
    const val ID_SHEET_WIDTH_PX = 2480
    const val ID_SHEET_HEIGHT_PX = 3508
    /** Had muat semasa eksport PDF (A4 @300 dpi) — kekalkan kad ID pada 300 dpi. */
    const val MAX_EXPORT_DIMENSION = 3508
}
