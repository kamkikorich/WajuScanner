package com.example.wajuscanner.ui.scanner

import android.app.Activity
import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.R
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

    /**
     * Slot waiting to be replaced during an ID-card retake. Null means the
     * next scan result starts a fresh review. Slot index: 0 = front, 1 = back.
     */
    private var awaitingRetakeSlot: Int? = null

    fun setIdCardMode(enabled: Boolean) {
        idCardMode = enabled
    }

    fun setIdCardLayout(layout: IdCardLayout) {
        idCardLayout = layout
    }

    private fun str(resId: Int): String = getApplication<Application>().getString(resId)

    /**
     * Draft terakhir semasa review aktif. Dikembalikan bila retake dibatalkan
     * atau scanner gagal, supaya dua-dua sisi tidak hilang.
     */
    private var pendingReview: ScannerUiState.IdCardReview? = null

    fun startScan(activity: Activity, onIntentReady: (IntentSender) -> Unit) {
        startScanWithLimit(activity, null, onIntentReady)
    }

    fun retakeSlot(activity: Activity, slot: Int, onIntentReady: (IntentSender) -> Unit) {
        val review = _uiState.value as? ScannerUiState.IdCardReview ?: return
        pendingReview = review
        awaitingRetakeSlot = slot
        startScanWithLimit(
            activity = activity,
            pageLimit = 1,
            onIntentReady = onIntentReady,
            reviewState = review,
        )
    }

    private fun startScanWithLimit(
        activity: Activity,
        pageLimit: Int?,
        onIntentReady: (IntentSender) -> Unit,
        reviewState: ScannerUiState.IdCardReview? = null,
    ) {
        if (_uiState.value is ScannerUiState.Launching) return

        if (!scannerLauncher.isAvailable()) {
            _uiState.value =
                ScannerUiState.Error(str(R.string.scanner_unavailable))
            return
        }

        _uiState.value = ScannerUiState.Launching
        scannerLauncher.getStartScanIntent(activity, idCardMode, pageLimit)
            .addOnSuccessListener { intentSender ->
                // UI layer melancarkan scanner melalui launcher yang didaftar
                // dengan rememberLauncherForActivityResult.
                onIntentReady(intentSender)
            }
            .addOnFailureListener { e ->
                // Launched dari review: kembalikan draf supaya user tidak
                // buang dua-dua sisi hanya kerana scanner gagal start.
                _uiState.value = reviewState ?: ScannerUiState.Error(
                    e.localizedMessage ?: str(R.string.scanner_start_failed)
                )
            }
    }

    fun onScanResult(result: ScannerResult) {
        val retakeSlot = awaitingRetakeSlot
        awaitingRetakeSlot = null

        when (result) {
            is ScannerResult.Cancelled -> {
                // Retake dibatalkan: kembali ke draf, jangan buang slot yang
                // sudah ada. Scan biasa dibatalkan: ke Idle.
                _uiState.value = pendingReview ?: ScannerUiState.Idle
            }
            is ScannerResult.Error -> {
                val draft = pendingReview
                _uiState.value = draft ?: ScannerUiState.Error(result.message)
            }
            is ScannerResult.Success -> {
                val draft = pendingReview
                pendingReview = null
                if (result.pageImageUris.isEmpty()) {
                    _uiState.value =
                        ScannerUiState.Error(str(R.string.scanner_no_pages))
                    return
                }
                if (idCardMode) {
                    if (retakeSlot != null && draft != null) {
                        _uiState.value =
                            handleRetakeResult(retakeSlot, draft, result.pageImageUris)
                    } else if (result.pageImageUris.size < 2) {
                        _uiState.value = ScannerUiState.Error(
                            str(R.string.scanner_id_need_two_sides)
                        )
                    } else {
                        // Model data: dua slot pegun, bukan list bebas.
                        _uiState.value = ScannerUiState.IdCardReview(
                            front = result.pageImageUris[0],
                            back = result.pageImageUris[1]
                        )
                    }
                } else {
                    persistScannedPages(result.pageImageUris)
                }
            }
        }
    }

    private fun handleRetakeResult(
        retakeSlot: Int,
        draft: ScannerUiState.IdCardReview,
        uris: List<String>,
    ): ScannerUiState.IdCardReview {
        val front = if (retakeSlot == 0) uris[0] else draft.front
        val back = if (retakeSlot == 0) draft.back else uris[0]
        return ScannerUiState.IdCardReview(front = front, back = back)
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
                    _uiState.value =
                        ScannerUiState.Error(e.localizedMessage ?: str(R.string.scanner_save_failed))
                }
            )
        }
    }

    /** Tukar posisi muka ↔ belakang pada draf review. */
    fun swapSides() {
        val review = _uiState.value as? ScannerUiState.IdCardReview ?: return
        _uiState.value = review.copy(front = review.back, back = review.front)
    }

    /** Simpan draf review: dua sisi digabung menjadi satu halaman. */
    fun saveIdCardDocument() {
        if (_uiState.value !is ScannerUiState.IdCardReview) return
        val review = _uiState.value as ScannerUiState.IdCardReview
        persistScannedPages(listOf(review.front, review.back))
    }

    fun consumeEvent() {
        _uiState.value = ScannerUiState.Idle
        pendingReview = null
    }
}