package com.example.wajuscanner.ui.preview

import com.example.wajuscanner.domain.model.Document
import com.example.wajuscanner.domain.model.Page

sealed interface PreviewUiState {
    data object Loading : PreviewUiState
    data class Error(val message: String) : PreviewUiState
    data class Success(
        val document: Document,
        val pages: List<Page>
    ) : PreviewUiState
}
