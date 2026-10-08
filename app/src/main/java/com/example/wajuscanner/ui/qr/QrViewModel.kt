package com.example.wajuscanner.ui.qr

import android.graphics.Rect
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.data.local.db.dao.QrResultDao
import com.example.wajuscanner.data.local.db.entity.QrResultEntity
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [QrScannerScreen]. Builds the CameraX ImageAnalysis.Analyzer
 * and exposes the UI state as a [StateFlow].
 *
 * The scanner uses ML Kit Barcode Scanning (bundled) restricted to formats
 * useful for document workflows: QR, Aztec, Data Matrix, PDF417, Code 128.
 * Auto-zoom is enabled so the library can request a closer view when the
 * detected barcode is too small to decode reliably. The currently-detected
 * bounding boxes are surfaced through [detectedBoxes] so the overlay can
 * draw a real-time highlight around the code(s) in the frame.
 */
@HiltViewModel
class QrViewModel @Inject constructor(
    private val qrResultDao: QrResultDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow<QrUiState>(QrUiState.Scanning)
    val uiState: StateFlow<QrUiState> = _uiState

    /**
     * Bounding boxes (image-space, NOT screen-space) of every barcode the
     * library is currently tracking. Emitted on the analyzer thread, so the
     * Composable that consumes it is responsible for projecting to screen.
     * Empty list means no detection in the latest frame.
     */
    private val _detectedBoxes = MutableStateFlow<List<Rect>>(emptyList())
    val detectedBoxes: StateFlow<List<Rect>> = _detectedBoxes

    /**
     * Source image dimensions + rotation in degrees, used to project the
     * image-space [Rect]s in [detectedBoxes] into screen space.
     */
    private val _imageInfo = MutableStateFlow(ImageInfo(0, 0, 0))
    val imageInfo: StateFlow<ImageInfo> = _imageInfo

    private val zoomCallback = ZoomSuggestionOptions.ZoomCallback { zoomRatio ->
        // Docs: "this callback will always be called on the main thread."
        // The UI hands the new ratio back via [onZoomSuggestion] so we only
        // surface it here; the actual camera control lives in the Composable.
        _uiState.value = QrUiState.ZoomSuggested(
                ratio = zoomRatio,
            )
        true
    }

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .enableAllPotentialBarcodes()
            .setZoomSuggestionOptions(
                ZoomSuggestionOptions.Builder(zoomCallback)
                    .build()
            )
            .build()
    )

    /**
     * CameraX analyzer. Callbacks run on the analyzer thread; switch back to
     * the main thread only when emitting UI state.
     */
    @ExperimentalGetImage
    fun analyzer(): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { imageProxy ->
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return@Analyzer
        }

        val info = imageProxy.imageInfo
        _imageInfo.value = ImageInfo(
            width = mediaImage.width,
            height = mediaImage.height,
            rotationDegrees = info.rotationDegrees,
        )

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            info.rotationDegrees,
        )

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val boxes = barcodes.mapNotNull { it.boundingBox }
                _detectedBoxes.value = boxes

                val first = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                if (first != null) {
                    _uiState.update { QrUiState.Found(first.rawValue!!) }
                    saveHistory(first.rawValue!!, first.format)
                }
            }
            .addOnCompleteListener {
                // STRATEGY_KEEP_ONLY_LATEST: close the frame so the next one
                // can be delivered (docs requirement for CameraX).
                imageProxy.close()
            }
    }

    /** Called when the user dismisses the "found" dialog. */
    fun resumeScanning() {
        _uiState.value = QrUiState.Scanning
    }

    // ---- Sejarah imbasan ----

    /**
     * Kandungan terakhir yang diimbas. Pengesanan berlaku setiap frame,
     * jadi kandungan sama tidak ditulis dua kali (elak sejarah bertindih).
     */
    private var lastSavedContent: String? = null

    val history: StateFlow<List<QrResultEntity>> =
        qrResultDao.observeHistory()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun saveHistory(content: String, format: Int) {
        if (content == lastSavedContent) return
        lastSavedContent = content
        viewModelScope.launch {
            runCatching { qrResultDao.insert(QrResultEntity(content = content, format = format)) }
        }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch { runCatching { qrResultDao.deleteById(id) } }
    }

    fun clearHistory() {
        viewModelScope.launch { runCatching { qrResultDao.clearAll() } }
    }

    // ---- Imbas dari imej galeri ----

    /**
     * Decode barcode daripada imej galeri (CamScanner parity). Dimangka
     * [Found] bila berjaya (dialog hasil yang sama dipaparkan) dan [DecodeFailed]
     * bila tiada kod boleh dibaca.
     */
    fun decodeFromImage(context: android.content.Context, uri: android.net.Uri) {
        val input = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            _uiState.value = QrUiState.DecodeFailed(
                e.localizedMessage ?: "Gagal membuka imej."
            )
            return
        }
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val first = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                if (first != null) {
                    lastSavedContent = null // imej berbeza: benarkan simpan lagi
                    _uiState.update { QrUiState.Found(first.rawValue!!) }
                    saveHistory(first.rawValue!!, first.format)
                } else {
                    _uiState.value = QrUiState.DecodeFailed(
                        "Tiada kod QR/barcode dijumpai dalam imej ini."
                    )
                }
            }
            .addOnFailureListener { e ->
                _uiState.value = QrUiState.DecodeFailed(
                    e.localizedMessage ?: "Gagal memproses imej."
                )
            }
    }

    /** Called when the UI finishes applying the zoom suggestion. */
    fun consumeZoomSuggestion() {
        if (_uiState.value is QrUiState.ZoomSuggested) {
            _uiState.value = QrUiState.Scanning
        }
    }

    override fun onCleared() {
        scanner.close()
        super.onCleared()
    }
}

sealed interface QrUiState {
    data object Scanning : QrUiState
    data class Found(val value: String) : QrUiState
    data class ZoomSuggested(val ratio: Float) : QrUiState
    data class DecodeFailed(val message: String) : QrUiState
}

/** Source-image dimensions plus rotation, used for bounding-box projection. */
data class ImageInfo(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
)