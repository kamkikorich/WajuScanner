package com.example.wajuscanner.core.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider

/**
 * Menghidupkan penstabilan perkakasan (OIS/video) pada [ImageCapture] melalui
 * Camera2 Interop. CameraX belum mendedahkan penstabilan dalam `CameraControl`
 * (isu terbuka Google), jadi ini laluan rasmi.
 *
 * Keselamatan: hanya set mod yang DISOKONG kamera terpilih (elak ralat tangkapan
 * pada kamera yang tiada OIS, cth. kamera depan).
 */
object CameraStabilization {

    @OptIn(markerClass = [ExperimentalCamera2Interop::class])
    fun apply(builder: ImageCapture.Builder, provider: ProcessCameraProvider, selector: CameraSelector) {
        val cameraInfo = runCatching { provider.getCameraInfo(selector) }.getOrNull() ?: return
        val characteristics = runCatching { Camera2CameraInfo.from(cameraInfo) }.getOrNull() ?: return
        val extender = Camera2Interop.Extender(builder)

        val videoModes = characteristics.getCameraCharacteristic(
            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
        )
        if (videoModes?.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON,
            )
        }

        val oisModes = characteristics.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
        )
        if (oisModes?.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON) == true) {
            extender.setCaptureRequestOption(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON,
            )
        }
    }
}
