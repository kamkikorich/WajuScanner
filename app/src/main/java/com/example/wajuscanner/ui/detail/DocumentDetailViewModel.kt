package com.example.wajuscanner.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.domain.model.PdfOptions
import com.example.wajuscanner.domain.model.PdfOrientation
import com.example.wajuscanner.domain.model.PdfPageSize
import com.example.wajuscanner.domain.usecase.DocumentExportState
import com.example.wajuscanner.domain.usecase.ExportDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val exportDocumentUseCase: ExportDocumentUseCase
) : ViewModel() {

    private val _exportState = MutableStateFlow<DocumentExportState>(DocumentExportState.Idle)
    val exportState: StateFlow<DocumentExportState> = _exportState

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

    fun consumeExportEvent() {
        _exportState.value = DocumentExportState.Idle
    }
}