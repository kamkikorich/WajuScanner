package com.example.wajuscanner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.domain.model.Document
import com.example.wajuscanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Search strategy: each debounced query merges the live name-filtered list
 * with documents matched through their saved OCR text (flatMapLatest keeps
 * only the freshest search in flight). Blank query shows the full live list.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        documentRepository.observeDocuments(),
        searchQuery.debounce(200).distinctUntilChanged()
    ) { documents, query -> documents to query }
        .flatMapLatest { (documents, query) ->
            if (query.isBlank()) {
                flow {
                    emit(
                        when {
                            documents.isEmpty() -> HomeUiState.Empty
                            else -> HomeUiState.Success(documents, query)
                        }
                    )
                }
            } else {
                val nameHits = documents.filter { it.name.contains(query, ignoreCase = true) }
                flow {
                    emit(HomeUiState.Success(nameHits, query))
                    val ocrDocs = documentRepository.searchIncludingOcr(query)
                    emit(HomeUiState.Success((nameHits + ocrDocs).distinctBy { it.id }, query))
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun renameDocument(id: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            documentRepository.renameDocument(id, newName)
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            documentRepository.deleteDocument(id)
        }
    }
}