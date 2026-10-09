package com.example.wajuscanner.camera

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScannerLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val options = GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        // JPEG sahaja: PDF dari scanner tidak pernah digunakan (app jana PDF
        // sendiri via PdfGenerator). Dokumen ML Kit menasihati minta hanya
        // format yang digunakan — jana PDF tambahan membuang masa/memori.
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        // setPageLimit default = -1 (unlimited). 0 adalah TIDAK SAH (min 1).
        .setGalleryImportAllowed(true)
        .build()

    private val scanner: GmsDocumentScanner by lazy {
        GmsDocumentScanning.getClient(options)
    }

    fun isAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        return availability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    fun getStartScanIntent(activity: Activity, idCardMode: Boolean = false, pageLimit: Int? = null): Task<IntentSender> {
        // pageLimit eksplisit mengatasi default (null = ikut mod):
        // biasa = unlimited (jangan panggil setPageLimit lihat AGENTS),
        // ID-card pertama = 2, retake satu slot = 1.
        val effectiveOptions = if (idCardMode) {
            // ID-card mode: had 2 muka (depan + belakang). setPageLimit min 1.
            val idOptions = GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setGalleryImportAllowed(true)
                .apply { setPageLimit(pageLimit ?: 2) }
                .build()
            GmsDocumentScanning.getClient(idOptions)
        } else {
            scanner
        }
        return effectiveOptions.getStartScanIntent(activity)
    }

    companion object {
        /**
         * Parse hasil ActivityResult dari ML Kit document scanner.
         * Launcher mesti didaftar melalui rememberLauncherForActivityResult di
         * dalam composable (register pada NavBackStackEntry) — JANGAN panggil
         * Activity.registerForActivityResult dari composable, ia crash bila
         * activity sudah RESUMED.
         */
        fun parseScanResult(activityResult: ActivityResult): ScannerResult {
            if (activityResult.resultCode != Activity.RESULT_OK) {
                return ScannerResult.Cancelled
            }

            val result = activityResult.data?.let { intent ->
                GmsDocumentScanningResult.fromActivityResultIntent(intent)
            } ?: return ScannerResult.Error("Scanner returned no result")

            val pages = result.getPages()?.map { page ->
                page.getImageUri().toString()
            } ?: emptyList()

            return ScannerResult.Success(pages)
        }
    }
}

sealed interface ScannerResult {
    data object Cancelled : ScannerResult
    data class Success(val pageImageUris: List<String>) : ScannerResult
    data class Error(val message: String) : ScannerResult
}
