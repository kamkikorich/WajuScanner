package com.example.wajuscanner.ui.navigation

import kotlinx.serialization.Serializable

sealed interface SmartScannerDestinations {
    @Serializable
    data object Home : SmartScannerDestinations

    @Serializable
    data object Library : SmartScannerDestinations

    @Serializable
    data object Scanner : SmartScannerDestinations

    @Serializable
    data object DocumentCapture : SmartScannerDestinations

    @Serializable
    data object QrScanner : SmartScannerDestinations

    @Serializable
    data object IdPhoto : SmartScannerDestinations

    @Serializable
    data class Editor(val pageId: Long = -1L) : SmartScannerDestinations

    @Serializable
    data class Detail(val documentId: Long) : SmartScannerDestinations

    @Serializable
    data class ResumeDraft(val documentId: Long) : SmartScannerDestinations

    @Serializable
    data class OcrResult(val pageId: Long) : SmartScannerDestinations

    @Serializable
    data object Settings : SmartScannerDestinations
}
