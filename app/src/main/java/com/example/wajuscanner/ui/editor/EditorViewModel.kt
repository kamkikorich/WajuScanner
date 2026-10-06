package com.example.wajuscanner.ui.editor

import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.core.util.ImageUtils
import com.example.wajuscanner.data.repository.DocumentRepositoryImpl
import com.example.wajuscanner.domain.model.DocumentFilter
import com.example.wajuscanner.domain.usecase.ProcessPageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val documentRepository: DocumentRepositoryImpl,
    private val processPageUseCase: ProcessPageUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState

    fun loadPage(pageId: Long) {
        viewModelScope.launch {
            val page = documentRepository.getPageById(pageId)
            if (page == null) {
                _uiState.value = EditorUiState.Error("Page not found")
                return@launch
            }
            val bitmap = ImageUtils.loadBitmapCorrected(page.imagePath)
            if (bitmap == null) {
                _uiState.value = EditorUiState.Error("Failed to load image")
                return@launch
            }
            _uiState.value = EditorUiState.Edit(bitmap = bitmap)
        }
    }

    fun rotateRight() {
        val current = _uiState.value as? EditorUiState.Edit ?: return
        _uiState.value = current.copy(rotationDegrees = (current.rotationDegrees + 90) % 360)
    }

    fun setFilter(filter: DocumentFilter) {
        val current = _uiState.value as? EditorUiState.Edit ?: return
        _uiState.value = current.copy(filter = filter)
    }

    fun saveEdits(
        pageId: Long,
        corners: List<PointF>? = null,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) {
        val current = _uiState.value as? EditorUiState.Edit ?: return
        _uiState.value = current.copy(isSaving = true)

        viewModelScope.launch {
            val result = processPageUseCase.applyEdit(
                pageId = pageId,
                corners = corners,
                rotationDegrees = current.rotationDegrees,
                filter = current.filter
            )
            result.fold(
                onSuccess = { onSaved() },
                onFailure = { e ->
                    _uiState.value = current.copy(isSaving = false)
                    onError(e.localizedMessage ?: "Failed to save edits")
                }
            )
        }
    }
}
