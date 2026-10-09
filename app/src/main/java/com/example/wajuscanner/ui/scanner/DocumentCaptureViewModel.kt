package com.example.wajuscanner.ui.scanner

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.core.common.Constants
import com.example.wajuscanner.core.util.FileUtils
import com.example.wajuscanner.domain.usecase.ScanDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Kamera dokumen sendiri (CameraX + penstabilan) — memintas ML Kit supaya sumber
 * lurus kekal lurus dan kualiti penuh. Digunakan oleh [DocumentCaptureScreen].
 */
@HiltViewModel
class DocumentCaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanDocumentUseCase: ScanDocumentUseCase,
) : ViewModel() {

    sealed interface CaptureState {
        data object Idle : CaptureState
        data object Saving : CaptureState
        data class Saved(val documentId: Long) : CaptureState
        data class Error(val message: String) : CaptureState
    }

    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state

    fun capture(imageCapture: ImageCapture) {
        if (_state.value is CaptureState.Saving) return
        _state.value = CaptureState.Saving
        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    persist(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    _state.value = CaptureState.Error(
                        exception.localizedMessage ?: "Gagal menangkap gambar."
                    )
                }
            },
        )
    }

    private fun persist(file: File) {
        viewModelScope.launch {
            val name = FileUtils.generateScanFileName(Constants.DEFAULT_FILE_PREFIX)
            scanDocumentUseCase(name, listOf(Uri.fromFile(file).toString())).fold(
                onSuccess = { (documentId, _) -> _state.value = CaptureState.Saved(documentId) },
                onFailure = { e ->
                    _state.value = CaptureState.Error(e.localizedMessage ?: "Gagal menyimpan dokumen.")
                },
            )
        }
    }
}
