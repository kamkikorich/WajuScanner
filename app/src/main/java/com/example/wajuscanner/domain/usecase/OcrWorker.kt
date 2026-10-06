package com.example.wajuscanner.domain.usecase

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker that runs OCR against a single page and persists the text.
 * Input: KEY_PAGE_ID (Long).
 */
@HiltWorker
class OcrWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runOcrUseCase: RunOcrUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pageId = inputData.getLong(KEY_PAGE_ID, -1L)
        if (pageId == -1L) return Result.failure()

        return when (runOcrUseCase.recognize(pageId)) {
            is OcrOutcome.Success -> Result.success()
            OcrOutcome.Empty -> Result.success() // page valid, just no text
            is OcrOutcome.Failure -> {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }

    companion object {
        const val KEY_PAGE_ID = "page_id"
    }
}
