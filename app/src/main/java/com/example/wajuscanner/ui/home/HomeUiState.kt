package com.example.wajuscanner.ui.home

import com.example.wajuscanner.domain.model.Document

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Empty(val mostRecentDraft: Document? = null) : HomeUiState
    data class Success(
        val documents: List<Document>,
        val query: String = "",
        val mostRecentDraft: Document? = null,
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
