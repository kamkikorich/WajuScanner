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
    const val MAX_THUMBNAIL_DIMENSION = 512
    const val JPEG_QUALITY_MAX = 95
    const val JPEG_QUALITY_BALANCED = 85

    // Work tags
    const val OCR_WORK_TAG = "ocr_worker"
}
