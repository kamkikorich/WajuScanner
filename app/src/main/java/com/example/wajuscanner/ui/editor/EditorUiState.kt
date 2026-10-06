package com.example.wajuscanner.ui.editor

import android.graphics.Bitmap
import com.example.wajuscanner.domain.model.DocumentFilter

sealed interface EditorUiState {
    data object Loading : EditorUiState
    data class Error(val message: String) : EditorUiState
    data class Edit(
        val bitmap: Bitmap,
        val rotationDegrees: Int = 0,
        val filter: DocumentFilter = DocumentFilter.ORIGINAL,
        val isSaving: Boolean = false
    ) : EditorUiState
}
