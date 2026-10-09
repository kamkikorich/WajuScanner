package com.example.wajuscanner.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.domain.model.DocumentFilter
import com.example.wajuscanner.domain.model.Page
import com.example.wajuscanner.domain.repository.DocumentRepository
import com.example.wajuscanner.domain.usecase.ManagePagesUseCase
import com.example.wajuscanner.domain.usecase.ProcessPageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val managePagesUseCase: ManagePagesUseCase,
    private val processPageUseCase: ProcessPageUseCase
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

    /** Rotates a page 90° clockwise in place (also regenerates its thumbnail). */
    fun rotatePage(pageId: Long) {
        val currentDocumentId = documentId
        if (currentDocumentId == -1L) return

        viewModelScope.launch {
            val result = processPageUseCase.applyEdit(
                pageId = pageId,
                rotationDegrees = 90,
                filter = DocumentFilter.ORIGINAL,
            )
            result.fold(
                onSuccess = {
                    val pages = documentRepository.getPagesForDocument(currentDocumentId)
                    refresh(currentDocumentId, pages)
                },
                onFailure = { e ->
                    _uiState.value = PreviewUiState.Error(e.localizedMessage ?: "Failed to rotate page")
                }
            )
        }
    }

    private var revision = 0L

    private suspend fun refresh(documentId: Long, pages: List<Page>) {
        val document = documentRepository.getDocumentById(documentId)
        if (document != null) {
            _uiState.value = PreviewUiState.Success(document, pages, revision = ++revision)
        }
    }
}
