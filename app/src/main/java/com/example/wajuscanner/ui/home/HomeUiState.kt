package com.example.wajuscanner.ui.home

import com.example.wajuscanner.domain.model.Document

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Success(val documents: List<Document>, val query: String = "") : HomeUiState
    data class Error(val message: String) : HomeUiState
}
