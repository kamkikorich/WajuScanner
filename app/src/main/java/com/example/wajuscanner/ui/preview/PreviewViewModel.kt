package com.example.wajuscanner.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.domain.model.Page
import com.example.wajuscanner.domain.repository.DocumentRepository
import com.example.wajuscanner.domain.usecase.ManagePagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val managePagesUseCase: ManagePagesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PreviewUiState>(PreviewUiState.Loading)
    val uiState: StateFlow<PreviewUiState> = _uiState

    private var documentId: Long = -1L

    fun loadDocument(documentId: Long) {
        this.documentId = documentId
        viewModelScope.launch {
            val document = documentRepository.getDocumentById(documentId)
            if (document == null) {
                _uiState.value = PreviewUiState.Error("Document not found")
                return@launch
            }
            val pages = documentRepository.getPagesForDocument(documentId)
            _uiState.value = PreviewUiState.Success(document, pages)
        }
    }

    fun reorderPages(orderedPageIds: List<Long>) {
        val currentDocumentId = documentId
        if (currentDocumentId == -1L) return

        viewModelScope.launch {
            val result = managePagesUseCase.reorderPages(currentDocumentId, orderedPageIds)
            result.fold(
                onSuccess = { pages ->
                    refresh(currentDocumentId, pages)
                },
                onFailure = { e ->
                    _uiState.value = PreviewUiState.Error(e.localizedMessage ?: "Failed to reorder pages")
                }
            )
        }
    }

    fun deletePage(pageId: Long) {
        val currentDocumentId = documentId
        if (currentDocumentId == -1L) return

        viewModelScope.launch {
            val result = managePagesUseCase.deletePage(currentDocumentId, pageId)
            result.fold(
                onSuccess = { pages ->
                    refresh(currentDocumentId, pages)
                },
                onFailure = { e ->
                    _uiState.value = PreviewUiState.Error(e.localizedMessage ?: "Failed to delete page")
                }
            )
        }
    }

    fun duplicatePage(pageId: Long) {
        val currentDocumentId = documentId
        if (currentDocumentId == -1L) return

        viewModelScope.launch {
            val result = managePagesUseCase.duplicatePage(currentDocumentId, pageId)
            result.fold(
                onSuccess = { pages ->
                    refresh(currentDocumentId, pages)
                },
                onFailure = { e ->
                    _uiState.value = PreviewUiState.Error(e.localizedMessage ?: "Failed to duplicate page")
                }
            )
        }
    }

    private suspend fun refresh(documentId: Long, pages: List<Page>) {
        val document = documentRepository.getDocumentById(documentId)
        if (document != null) {
            _uiState.value = PreviewUiState.Success(document, pages)
        }
    }
}
