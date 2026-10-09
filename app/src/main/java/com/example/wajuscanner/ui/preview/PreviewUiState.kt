package com.example.wajuscanner.ui.preview

import com.example.wajuscanner.domain.model.Document
import com.example.wajuscanner.domain.model.Page

sealed interface PreviewUiState {
    data object Loading : PreviewUiState
    data class Error(val message: String) : PreviewUiState
    data class Success(
        val document: Document,
        val pages: List<Page>,
        /** Bumped on every refresh so the UI recomposes even when the page list is unchanged (e.g. after rotate). */
        val revision: Long = 0L
    ) : PreviewUiState
}
