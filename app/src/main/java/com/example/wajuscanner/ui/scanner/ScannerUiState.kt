package com.example.wajuscanner.ui.scanner

sealed interface ScannerUiState {
    data object Idle : ScannerUiState
    data object CheckingAvailability : ScannerUiState
    data object Launching : ScannerUiState
    data object Processing : ScannerUiState
    data class Error(val message: String) : ScannerUiState
    data class Success(val documentId: Long) : ScannerUiState
}
