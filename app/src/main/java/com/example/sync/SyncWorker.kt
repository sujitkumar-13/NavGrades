package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.remote.RemoteNavGradeRepository
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.model.ScannedPaperRemote
import com.example.data.repository.OmrRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.concurrent.TimeUnit

class SyncWorker(
  context: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

  private val tag = "SyncWorker"

  override suspend fun doWork(): Result {
    Log.i(tag, "SyncWorker triggered. Checking for unsynced papers...")

    if (!SupabaseConfig.isConfigured()) {
      Log.i(tag, "Supabase is not configured yet (local mock mode). Skipping cloud sync.")
      return Result.success()
    }

    return try {
      val localRepo = OmrRepository.getInstance(applicationContext, CoroutineScope(Dispatchers.IO))
      val remoteRepo = RemoteNavGradeRepository()

      val unsyncedPapers = localRepo.getUnsyncedPapers()
      Log.i(tag, "Found ${unsyncedPapers.size} unsynced paper(s).")

      for (paper in unsyncedPapers) {
        var cloudImageUrl = paper.imagePath

        // 1. Upload local sheet image to Supabase Storage if present
        if (!paper.imagePath.isNullOrBlank()) {
          val imageFile = File(paper.imagePath)
          if (imageFile.exists()) {
            val bytes = imageFile.readBytes()
            val fileName = "scanned_${paper.quizId}_${paper.id}.jpg"
            val uploadedUrl = remoteRepo.uploadPaperImage(bytes, fileName)
            if (uploadedUrl != null) {
              cloudImageUrl = uploadedUrl
            }
          }
        }

        // 2. Insert/Upsert paper record in Supabase PostgreSQL
        val remotePaper = ScannedPaperRemote(
          id = paper.id,
          quizId = paper.quizId,
          studentId = paper.studentId,
          studentName = paper.studentName,
          answers = paper.answersJson,
          score = paper.score,
          totalPossibleMarks = paper.totalPossibleMarks,
          percentage = paper.percentage,
          correctCount = paper.correctCount,
          wrongCount = paper.wrongCount,
          blankCount = paper.blankCount,
          multipleCount = paper.multipleCount,
          reviewRequiredCount = paper.reviewRequiredCount,
          imageUrl = cloudImageUrl,
          whatsappNumber = paper.whatsappNumber,
          block = paper.block,
          caste = paper.cast,
          gender = paper.gender,
          qualification = paper.qualification,
          questionSetName = paper.questionSetName
        )

        val inserted = remoteRepo.insertScannedPaper(remotePaper)
        if (inserted) {
          localRepo.markPaperSynced(paper.id)
          Log.i(tag, "Successfully synced paper ${paper.id} to Supabase.")
        } else {
          Log.w(tag, "Failed to sync paper ${paper.id} to Supabase. Will retry.")
        }
      }

      Result.success()
    } catch (e: Exception) {
      Log.e(tag, "Error during SyncWorker execution: ${e.message}", e)
      Result.retry()
    }
  }

  companion object {
    private const val WORK_NAME_PERIODIC = "NavGradePeriodicSyncWork"
    private const val WORK_NAME_ONE_TIME = "NavGradeOneTimeSyncWork"

    /**
     * Schedules periodic background sync every 15 minutes when device has active internet.
     */
    fun schedulePeriodicSync(context: Context) {
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()

      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME_PERIODIC,
        ExistingPeriodicWorkPolicy.KEEP,
        periodicRequest
      )
    }

    /**
     * Triggers an immediate one-time sync attempt when internet is detected or user taps Sync.
     */
    fun triggerImmediateSync(context: Context) {
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val oneTimeRequest = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(constraints)
        .build()

      WorkManager.getInstance(context).enqueueUniqueWork(
        WORK_NAME_ONE_TIME,
        ExistingWorkPolicy.REPLACE,
        oneTimeRequest
      )
    }
  }
}
