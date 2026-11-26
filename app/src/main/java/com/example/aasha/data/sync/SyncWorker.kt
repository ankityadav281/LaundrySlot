package com.example.aasha.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aasha.data.repository.LaundryRepository
import com.example.aasha.data.repository.SyncErrorClassifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val laundryRepository: LaundryRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Background sync started (attempt ${runAttemptCount + 1})")

        return try {
            laundryRepository.syncPendingChangesOrThrow()
            Log.d(TAG, "Background sync completed")
            Result.success()
        } catch (e: Exception) {
            if (SyncErrorClassifier.isNonRetryable(e) || runAttemptCount >= MAX_RETRY_ATTEMPTS) {
                Log.w(TAG, "Background sync failed permanently: ${e.message}")
                Result.failure()
            } else {
                Log.w(TAG, "Background sync failed transiently; scheduling retry: ${e.message}")
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "LaundrySyncWorker"
        const val MAX_RETRY_ATTEMPTS = 5
    }
}
