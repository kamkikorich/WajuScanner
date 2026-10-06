package com.example.wajuscanner.ui.ocr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.domain.usecase.OcrOutcome
import com.example.wajuscanner.domain.usecase.RunOcrUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OcrUiState {
    data object Running : OcrUiState
    data class Done(val text: String) : OcrUiState
    data class Failed(val message: String) : OcrUiState
}

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val runOcrUseCase: RunOcrUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<OcrUiState>(OcrUiState.Running)
    val uiState: StateFlow<OcrUiState> = _uiState

    fun runOcr(pageId: Long) {
        if (_uiState.value is OcrUiState.Running) return
        _uiState.value = OcrUiState.Running
        viewModelScope.launch { execute(pageId) }
    }

    init {
        // handle the very first run when the VM is created for a page
        // (runOcr is also callable for retries)
    }

    /** First-load variant: invoked with Running preset even at fresh start. */
    fun runOcrInitial(pageId: Long) {
        _uiState.value = OcrUiState.Running
        viewModelScope.launch { execute(pageId) }
    }

    private suspend fun execute(pageId: Long) {
        when (val outcome = runOcrUseCase.recognize(pageId)) {
            is OcrOutcome.Success -> _uiState.value = OcrUiState.Done(outcome.text)
            OcrOutcome.Empty -> _uiState.value = OcrUiState.Done("")
            is OcrOutcome.Failure -> _uiState.value = OcrUiState.Failed(outcome.message)
        }
    }
}