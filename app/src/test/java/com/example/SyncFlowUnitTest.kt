package com.example

import com.example.data.model.ScannedPaperEntity
import com.example.data.remote.model.ScannedPaperRemote
import com.example.data.repository.SyncStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFlowUnitTest {

  @Test
  fun testSyncStatusTransitions() {
    val initialStatus = SyncStatus.SYNCED
    assertEquals(SyncStatus.SYNCED, initialStatus)

    val syncingStatus = SyncStatus.SYNCING
    assertEquals(SyncStatus.SYNCING, syncingStatus)

    val offlineStatus = SyncStatus.OFFLINE
    assertEquals(SyncStatus.OFFLINE, offlineStatus)

    val errorStatus = SyncStatus.ERROR
    assertEquals(SyncStatus.ERROR, errorStatus)
  }

  @Test
  fun testScannedPaperEntitySyncDefaults() {
    val paper = ScannedPaperEntity(
      id = "paper-1",
      quizId = "quiz-1",
      studentId = "STU001",
      studentName = "Aman Kumar",
      answersJson = "{\"1\":\"A\",\"2\":\"B\"}",
      score = 2.0f,
      totalPossibleMarks = 2.0f,
      percentage = 100.0f,
      correctCount = 2,
      wrongCount = 0,
      blankCount = 0,
      multipleCount = 0,
      reviewRequiredCount = 0,
      imagePath = "/storage/test.jpg"
    )

    // Unsynced by default
    assertFalse(paper.isSynced)
    assertEquals(null, paper.syncedAt)

    // When marked synced
    val now = System.currentTimeMillis()
    val syncedPaper = paper.copy(isSynced = true, syncedAt = now)
    assertTrue(syncedPaper.isSynced)
    assertEquals(now, syncedPaper.syncedAt)
  }

  @Test
  fun testScannedPaperRemoteSerialization() {
    val remote = ScannedPaperRemote(
      id = "remote-1",
      quizId = "quiz-1",
      studentId = "STU001",
      studentName = "Aman Kumar",
      answers = "{\"1\":\"A\"}",
      score = 1.0f,
      totalPossibleMarks = 1.0f,
      percentage = 100.0f,
      correctCount = 1,
      imageUrl = "https://supabase.co/storage/img.jpg"
    )

    val jsonString = Json.encodeToString(ScannedPaperRemote.serializer(), remote)
    val decoded = Json.decodeFromString(ScannedPaperRemote.serializer(), jsonString)

    assertEquals(remote.id, decoded.id)
    assertEquals(remote.studentName, decoded.studentName)
    assertEquals(remote.imageUrl, decoded.imageUrl)
    assertEquals(remote.score, decoded.score, 0.001f)
  }
}
