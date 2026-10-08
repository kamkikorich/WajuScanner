package com.example.wajuscanner.ui.scanner

sealed interface ScannerUiState {
    data object Idle : ScannerUiState
    data object CheckingAvailability : ScannerUiState
    data object Launching : ScannerUiState
    data object Processing : ScannerUiState

    /**
     * ID-card review: scanner returned exactly the two sides. Nothing is
     * persisted yet — the user can swap, retake one side, or save.
     */
    data class IdCardReview(val front: String, val back: String) : ScannerUiState

    data class Error(val message: String) : ScannerUiState
    data class Success(val documentId: Long) : ScannerUiState
}
