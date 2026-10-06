package com.example.wajuscanner.ui.resume

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.domain.model.Document
import com.example.wajuscanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResumeScanViewModel @Inject constructor(
    private val repository: DocumentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ResumeScanUiState>(ResumeScanUiState.Loading)
    val uiState: StateFlow<ResumeScanUiState> = _uiState

    fun load(documentId: Long) {
        viewModelScope.launch {
            val doc = repository.getDocumentById(documentId)
            _uiState.update {
                if (doc != null) ResumeScanUiState.Loaded(doc) else ResumeScanUiState.NotFound
                }
        }
    }

    fun discard() {
        val state = _uiState.value
        if (state is ResumeScanUiState.Loaded) {
            viewModelScope.launch { repository.deleteDocument(state.document.id) }
        }
    }
}

sealed interface ResumeScanUiState {
    data object Loading : ResumeScanUiState
    data object NotFound : ResumeScanUiState
    data class Loaded(val document: Document) : ResumeScanUiState
}