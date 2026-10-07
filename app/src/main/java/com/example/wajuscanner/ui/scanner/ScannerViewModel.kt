package com.example.wajuscanner.ui.scanner

import android.app.Activity
import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.camera.ScannerLauncher
import com.example.wajuscanner.camera.ScannerResult
import com.example.wajuscanner.core.common.Constants
import com.example.wajuscanner.core.util.FileUtils
import com.example.wajuscanner.core.util.IdCardNameExtractor
import com.example.wajuscanner.domain.usecase.IdCardLayout
import com.example.wajuscanner.domain.usecase.ScanDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    application: Application,
    private val scannerLauncher: ScannerLauncher,
    private val scanDocumentUseCase: ScanDocumentUseCase,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val uiState: StateFlow<ScannerUiState> = _uiState

    /** When true, the scanner runs in ID-card mode: 2 sides combined into ONE page. */
    private var idCardMode: Boolean = false

    /** Layout used when [idCardMode] is on. Defaults to vertical (top/bottom). */
    private var idCardLayout: IdCardLayout = IdCardLayout.VERTICAL

    fun setIdCardMode(enabled: Boolean) {
        idCardMode = enabled
    }

    fun setIdCardLayout(layout: IdCardLayout) {
        idCardLayout = layout
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
            val baseName = FileUtils.generateScanFileName(
                if (idCardMode) "ID_Card" else Constants.DEFAULT_FILE_PREFIX
            )
            val documentName = if (idCardMode && pageUris.isNotEmpty()) {
                val suggested = withContext(Dispatchers.IO) {
                    runCatching {
                        IdCardNameExtractor.suggest(
                            getApplication(),
                            Uri.parse(pageUris[0])
                        )
                    }.getOrNull()
                }
                if (!suggested.isNullOrBlank()) "ID_$suggested" else baseName
            } else baseName

            val outcome = if (idCardMode) {
                scanDocumentUseCase.invokeIdCardMode(documentName, pageUris, idCardLayout)
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