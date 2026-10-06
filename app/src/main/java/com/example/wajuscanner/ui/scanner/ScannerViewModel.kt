package com.example.wajuscanner.ui.scanner

import android.app.Activity
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.camera.ScannerLauncher
import com.example.wajuscanner.camera.ScannerResult
import com.example.wajuscanner.core.util.FileUtils
import com.example.wajuscanner.domain.usecase.ScanDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val scannerLauncher: ScannerLauncher,
    private val scanDocumentUseCase: ScanDocumentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val uiState: StateFlow<ScannerUiState> = _uiState

    /** When true, the scanner runs in ID-card mode: 2 sides combined into ONE page. */
    private var idCardMode: Boolean = false

    fun setIdCardMode(enabled: Boolean) {
        idCardMode = enabled
    }

    fun startScan(activity: Activity, onIntentReady: (IntentSender) -> Unit) {
        if (_uiState.value is ScannerUiState.Launching) return

        if (!scannerLauncher.isAvailable()) {
            _uiState.value = ScannerUiState.Error("Google Play Services document scanner is not available on this device.")
            return
        }

        _uiState.value = ScannerUiState.Launching
        scannerLauncher.getStartScanIntent(activity, idCardMode)
            .addOnSuccessListener { intentSender ->
                // UI layer melancarkan scanner melalui launcher yang didaftar
                // dengan rememberLauncherForActivityResult.
                onIntentReady(intentSender)
            }
            .addOnFailureListener { e ->
                _uiState.value = ScannerUiState.Error(e.localizedMessage ?: "Failed to start scanner")
            }
    }

    fun onScanResult(result: ScannerResult) {
        when (result) {
            is ScannerResult.Cancelled -> {
                _uiState.value = ScannerUiState.Idle
            }
            is ScannerResult.Error -> {
                _uiState.value = ScannerUiState.Error(result.message)
            }
            is ScannerResult.Success -> {
                if (result.pageImageUris.isEmpty()) {
                    _uiState.value = ScannerUiState.Error("No pages were scanned")
                    return
                }
                if (idCardMode && result.pageImageUris.size < 2) {
                    _uiState.value = ScannerUiState.Error(
                        "ID card mode needs both sides — scan front and back, then save."
                    )
                    return
                }
                persistScannedPages(result.pageImageUris)
            }
        }
    }

    private fun persistScannedPages(pageUris: List<String>) {
        _uiState.value = ScannerUiState.Processing
        viewModelScope.launch {
            val documentName = FileUtils.generateScanFileName()
            val outcome = if (idCardMode) {
                scanDocumentUseCase.invokeIdCardMode(documentName, pageUris)
            } else {
                scanDocumentUseCase(documentName, pageUris)
            }
            outcome.fold(
                onSuccess = { (documentId, _) ->
                    _uiState.value = ScannerUiState.Success(documentId)
                },
                onFailure = { e ->
                    _uiState.value = ScannerUiState.Error(e.localizedMessage ?: "Failed to save scanned document")
                }
            )
        }
    }

    fun consumeEvent() {
        _uiState.value = ScannerUiState.Idle
    }
}
