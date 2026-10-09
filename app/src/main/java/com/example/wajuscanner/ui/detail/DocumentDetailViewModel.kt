package com.example.wajuscanner.ui.detail

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.core.ai.AiAction
import com.example.wajuscanner.core.ai.RunAiUseCase
import com.example.wajuscanner.data.settings.AiProviderType
import com.example.wajuscanner.data.settings.AiSettingsStore
import com.example.wajuscanner.domain.model.PdfCompressionMode
import com.example.wajuscanner.domain.model.PdfOptions
import com.example.wajuscanner.domain.model.PdfOrientation
import com.example.wajuscanner.domain.model.PdfPageSize
import com.example.wajuscanner.domain.repository.DocumentRepository
import com.example.wajuscanner.domain.usecase.DocumentExportState
import com.example.wajuscanner.domain.usecase.ExportDocumentUseCase
import com.example.wajuscanner.domain.usecase.RunOcrUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val exportDocumentUseCase: ExportDocumentUseCase,
    private val aiSettings: AiSettingsStore,
    private val runAi: RunAiUseCase,
    private val documentRepository: DocumentRepository,
    private val runOcrUseCase: RunOcrUseCase,
) : ViewModel() {

    private val _exportState = MutableStateFlow<DocumentExportState>(DocumentExportState.Idle)
    val exportState: StateFlow<DocumentExportState> = _exportState

    /** AI hanya tersedia jika pengguna mengaktifkannya DAN kunci disimpan. */
    val aiAvailable: StateFlow<Boolean> = aiSettings.settings
        .map { it.enabled && it.hasKey }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Input imej (vision) hanya untuk provider OpenAI-compatible buat masa ini. */
    val aiImageSupported: StateFlow<Boolean> = aiSettings.settings
        .map { it.provider == AiProviderType.OPENAI_COMPATIBLE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiState: StateFlow<AiUiState> = _aiState

    private val _pdfOptions = MutableStateFlow(PdfOptions())
    val pdfOptions: StateFlow<PdfOptions> = _pdfOptions

    /** Generates the PDF and fires the system share sheet. */
    fun exportAndShare(documentId: Long) {
        if (_exportState.value is DocumentExportState.Exporting) return
        _exportState.value = DocumentExportState.Exporting

        viewModelScope.launch {
            val result = exportDocumentUseCase.exportAsPdf(documentId, _pdfOptions.value)
            result.fold(
                onSuccess = { uri ->
                    val intent = exportDocumentUseCase.createShareIntent(uri)
                    val chooser = Intent.createChooser(intent, "Share PDF").apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    _exportState.value = DocumentExportState.ReadyToShare(chooser)
                },
                onFailure = { e ->
                    _exportState.value = DocumentExportState.Error(
                        e.localizedMessage ?: "Failed to export PDF"
                    )
                }
            )
        }
    }

    /** Generates the PDF and saves it into the public Documents/WajuScanner folder. */
    fun exportAndSave(documentId: Long) {
        if (_exportState.value is DocumentExportState.Exporting) return
        _exportState.value = DocumentExportState.Exporting

        viewModelScope.launch {
            exportDocumentUseCase.savePdfToDocuments(documentId, _pdfOptions.value).fold(
                onSuccess = { (uri, name) ->
                    _exportState.value = DocumentExportState.Saved(uri, name)
                },
                onFailure = { e ->
                    _exportState.value = DocumentExportState.Error(
                        e.localizedMessage ?: "Failed to save PDF"
                    )
                }
            )
        }
    }

    fun setPageSize(pageSize: PdfPageSize) {
        _pdfOptions.value = _pdfOptions.value.copy(pageSize = pageSize)
    }

    fun setOrientation(orientation: PdfOrientation) {
        _pdfOptions.value = _pdfOptions.value.copy(orientation = orientation)
    }

    fun setCompression(mode: PdfCompressionMode) {
        _pdfOptions.value = _pdfOptions.value.copy(compression = mode)
    }

    fun consumeExportEvent() {
        _exportState.value = DocumentExportState.Idle
    }

    /** Jalankan tindakan AI ke atas teks OCR (+ imej halaman jika [useImage]). */
    fun runAiAction(
        documentId: Long,
        action: AiAction,
        question: String? = null,
        useImage: Boolean = false,
    ) {
        if (_aiState.value is AiUiState.Loading) return
        _aiState.value = AiUiState.Loading
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { gatherDocumentText(documentId) }
            val imagePath = if (useImage) {
                withContext(Dispatchers.IO) {
                    documentRepository.getPagesForDocument(documentId)
                        .minByOrNull { it.pageOrder }?.imagePath
                }
            } else {
                null
            }
            if (text.isBlank() && imagePath == null) {
                _aiState.value = AiUiState.Error("Tiada halaman atau teks untuk diproses.")
                return@launch
            }
            runAi.run(action, text, question, imagePath).fold(
                onSuccess = { _aiState.value = AiUiState.Result(it) },
                onFailure = { _aiState.value = AiUiState.Error(it.localizedMessage ?: "AI gagal.") },
            )
        }
    }

    fun resetAi() {
        _aiState.value = AiUiState.Idle
    }

    private suspend fun gatherDocumentText(documentId: Long): String {
        var pages = documentRepository.getPagesForDocument(documentId)
        var text = pages.sortedBy { it.pageOrder }.mapNotNull { it.ocrText }.filter { it.isNotBlank() }
            .joinToString("\n\n")
        if (text.isBlank() && pages.isNotEmpty()) {
            runOcrUseCase.recognizeDocument(documentId)
            pages = documentRepository.getPagesForDocument(documentId)
            text = pages.sortedBy { it.pageOrder }.mapNotNull { it.ocrText }.filter { it.isNotBlank() }
                .joinToString("\n\n")
        }
        return text
    }
}

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Result(val text: String) : AiUiState
    data class Error(val message: String) : AiUiState
}