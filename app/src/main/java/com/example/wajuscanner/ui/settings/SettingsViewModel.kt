package com.example.wajuscanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.data.local.storage.DocumentStorage
import com.example.wajuscanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val storageBytes: Long = 0L,
    val documentCount: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val storage: DocumentStorage,
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val count = documentRepository.observeDocuments().first()?.size ?: 0
            val storageBytes = withContext(Dispatchers.IO) { storage.calculateStorageUsage() }
            _uiState.value = SettingsUiState(
                storageBytes = storageBytes,
                documentCount = count
            )
        }
    }
}