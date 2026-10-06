package com.example.wajuscanner.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.wajuscanner.core.common.Constants
import com.example.wajuscanner.core.util.FileUtils
import com.example.wajuscanner.data.repository.DocumentRepositoryImpl
import com.example.wajuscanner.domain.model.PdfOptions
import com.example.wajuscanner.pdf.PdfGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Export states for the PDF pipeline.
 */
sealed interface DocumentExportState {
    data object Idle : DocumentExportState
    data object Exporting : DocumentExportState
    /** PDF saved into MediaStore (Downloads/Documents). [uri] points at the stored file. */
    data class Saved(val uri: Uri, val fileName: String) : DocumentExportState
    /** Share-ready content URI (used for the share sheet). */
    data class ReadyToShare(val intent: android.content.Intent) : DocumentExportState
    data class Error(val message: String) : DocumentExportState
}

class ExportDocumentUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepositoryImpl,
    private val pdfGenerator: PdfGenerator
) {

    /**
     * Generates a PDF into the app cache and returns a shareable content URI
     * (FileProvider). Kept for the existing share-sheet flow.
     */
    suspend fun exportAsPdf(documentId: Long, options: PdfOptions = PdfOptions()): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                val document = documentRepository.getDocumentById(documentId)
                    ?: return@withContext Result.failure(IllegalStateException("Document not found"))
                val pages = documentRepository.getPagesForDocument(documentId)
                if (pages.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("Document has no pages"))
                }

                val pdfName = document.name
                val imagePaths = pages.sortedBy { it.pageOrder }.map { it.imagePath }

                val pdfResult = pdfGenerator.generatePdf(pdfName, imagePaths, options)
                pdfResult.fold(
                    onSuccess = { file ->
                        Result.success(pdfGenerator.getUriForFile(file))
                    },
                    onFailure = { e -> Result.failure(e) }
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Generates a PDF and saves it into the public Documents collection via
     * MediaStore (Q+). Pre-Q falls back to the legacy Downloads directory.
     * Returns the MediaStore Uri of the saved file.
     */
    suspend fun savePdfToDocuments(
        documentId: Long,
        options: PdfOptions = PdfOptions()
    ): Result<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        try {
            val document = documentRepository.getDocumentById(documentId)
                ?: return@withContext Result.failure(IllegalStateException("Document not found"))
            val pages = documentRepository.getPagesForDocument(documentId)
            if (pages.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Document has no pages"))
            }

            val imagePaths = pages.sortedBy { it.pageOrder }.map { it.imagePath }
            val pdf = pdfGenerator.generatePdf(document.name, imagePaths, options)
                .getOrElse { return@withContext Result.failure(it) }

            saveFileToMediaStore(pdf, document.name).fold(
                onSuccess = { (uri, name) ->
                    pdf.delete() // clean cache copy; MediaStore holds the public copy
                    Result.success(uri to name)
                },
                onFailure = { e -> Result.failure(e) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveFileToMediaStore(source: File, displayName: String): Result<Pair<Uri, String>> {
        val folderName = "WajuScanner"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val relativePath = Environment.DIRECTORY_DOCUMENTS + "/" + folderName
                val baseName = source.nameWithoutExtension.ifBlank { Constants.DEFAULT_FILE_PREFIX }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName.${Constants.PDF_EXTENSION}")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Files.getContentUri("external")
                val uri = resolver.insert(collection, values)
                    ?: return Result.failure(IOException("MediaStore rejected the insert"))

                val saved = resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out) }
                }
                if (saved == null) {
                    resolver.delete(uri, null, null)
                    return Result.failure(IOException("Cannot open output stream for $uri"))
                }

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success(uri to "$baseName.${Constants.PDF_EXTENSION}")
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            // Pre-Q: no MediaStore for arbitrary files without WRITE permission beyond Downloads.
            // Save into the public Downloads dir directly (app is allowed to write its own files there).
            try {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val dir = File(downloads, folderName).apply { mkdirs() }
                val target = FileUtils.createUniqueFile(dir, source.nameWithoutExtension, Constants.PDF_EXTENSION)
                source.copyTo(target, overwrite = false)
                Result.success(Uri.fromFile(target) to target.name)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Creates a share Intent for the generated PDF URI.
     */
    fun createShareIntent(uri: Uri): android.content.Intent {
        return android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}