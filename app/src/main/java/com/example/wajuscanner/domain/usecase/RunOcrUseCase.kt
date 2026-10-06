package com.example.wajuscanner.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.wajuscanner.core.common.Constants
import com.example.wajuscanner.core.util.BitmapUtils
import com.example.wajuscanner.data.local.db.dao.OcrResultDao
import com.example.wajuscanner.data.local.db.dao.PageDao
import com.example.wajuscanner.data.repository.DocumentRepositoryImpl
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of a single OCR run against a page image.
 */
sealed interface OcrOutcome {
    /** OCR text found and saved. */
    data class Success(val text: String) : OcrOutcome
    /** Page processed fine but no legible text. */
    data object Empty : OcrOutcome
    /** Model or IO error. */
    data class Failure(val message: String) : OcrOutcome
}

@Singleton
class RunOcrUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pageDao: PageDao,
    private val ocrResultDao: OcrResultDao,
    private val documentRepository: DocumentRepositoryImpl
) {

    /**
     * Runs Latin text recognition against the page's stored image and
     * persists the result into both ocr_results and pages.ocrText.
     */
    suspend fun recognize(pageId: Long): OcrOutcome = withContext(Dispatchers.IO) {
        val page = pageDao.getById(pageId)
            ?: return@withContext OcrOutcome.Failure("Page $pageId not found")

        val bitmap = BitmapUtils.decodeSampledBitmap(page.imagePath, maxDim = Constants.MAX_PREVIEW_DIMENSION)
            ?: return@withContext OcrOutcome.Failure("Cannot decode page image: ${page.imagePath}")

        val outcome = try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = Tasks.await(
                recognizer.process(inputImage),
                45, TimeUnit.SECONDS
            )
            recognizer.close()

            val text = visionText.text.trim()
            if (text.isEmpty()) {
                OcrOutcome.Empty
            } else {
                ocrResultDao.upsert(
                    com.example.wajuscanner.data.local.db.entity.OcrResultEntity(
                        pageId = pageId,
                        text = text
                    )
                )
                documentRepository.updatePageOcrText(pageId, text)
                OcrOutcome.Success(text)
            }
        } catch (e: Exception) {
            OcrOutcome.Failure(e.localizedMessage ?: "OCR failed")
        } finally {
            bitmap.recycle()
        }
        outcome
    }

    /**
     * Enqueues a background OCR job for [pageId] via WorkManager.
     */
    fun enqueueBackgroundOcr(pageId: Long) {
        val request = OneTimeWorkRequestBuilder<OcrWorker>()
            .setConstraints(
                Constraints.Builder().setRequiresBatteryNotLow(true).build()
            )
            .setInputData(workDataOf(OcrWorker.KEY_PAGE_ID to pageId))
            .addTag(Constants.OCR_WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "ocr_page_$pageId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Runs OCR against every page of a document sequentially (foreground use).
     */
    suspend fun recognizeDocument(documentId: Long): Map<Long, OcrOutcome> = withContext(Dispatchers.IO) {
        val pages = documentRepository.getPagesForDocument(documentId)
        pages.associate { it.id to recognize(it.id) }
    }
}