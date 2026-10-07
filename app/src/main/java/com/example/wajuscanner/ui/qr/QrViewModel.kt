package com.example.wajuscanner.ui.qr

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel for [QrScannerScreen]. Builds the CameraX ImageAnalysis.Analyzer
 * and exposes the UI state as a [StateFlow].
 *
 * The scanner uses ML Kit Barcode Scanning (bundled) restricted to formats
 * useful for document workflows: QR, Aztec, Data Matrix, PDF417, Code 128.
 * Auto-zoom is enabled so the library can request a closer view when the
 * detected barcode is too small to decode reliably.
 */
@HiltViewModel
class QrViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<QrUiState>(QrUiState.Scanning)
    val uiState: StateFlow<QrUiState> = _uiState

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

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val first = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                if (first != null) {
                    _uiState.update { QrUiState.Found(first.rawValue!!) }
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
}