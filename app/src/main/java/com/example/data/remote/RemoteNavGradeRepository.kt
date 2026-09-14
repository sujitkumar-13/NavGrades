package com.example.data.remote

import android.util.Log
import com.example.data.remote.model.AnswerKeyItemRemote
import com.example.data.remote.model.AnswerKeySetRemote
import com.example.data.remote.model.ApprovedUserRemote
import com.example.data.remote.model.QuizRemote
import com.example.data.remote.model.ScannedPaperRemote
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage

class RemoteNavGradeRepository(
  private val client: SupabaseClient = SupabaseConfig.client
) {
  private val tag = "RemoteNavGradeRepo"

  // -------------------------------------------------------------
  // Quizzes
  // -------------------------------------------------------------
  suspend fun getAllQuizzes(): List<QuizRemote> {
    if (!SupabaseConfig.isConfigured()) return emptyList()
    return try {
      client.postgrest["quizzes"].select().decodeList<QuizRemote>()
    } catch (e: Exception) {
      Log.e(tag, "Error fetching quizzes from Supabase: ${e.message}", e)
      emptyList()
    }
  }

  suspend fun getQuizById(quizId: String): QuizRemote? {
    if (!SupabaseConfig.isConfigured()) return null
    return try {
      client.postgrest["quizzes"].select {
        filter { eq("id", quizId) }
      }.decodeSingleOrNull<QuizRemote>()
    } catch (e: Exception) {
      Log.e(tag, "Error fetching quiz $quizId: ${e.message}", e)
      null
    }
  }

  suspend fun insertQuiz(quiz: QuizRemote): Boolean {
    if (!SupabaseConfig.isConfigured()) return false
    return try {
      client.postgrest["quizzes"].insert(quiz)
      true
    } catch (e: Exception) {
      Log.e(tag, "Error inserting quiz: ${e.message}", e)
      false
    }
  }

  suspend fun deleteQuiz(quizId: String): Boolean {
    if (!SupabaseConfig.isConfigured()) return false
    return try {
      client.postgrest["quizzes"].delete {
        filter { eq("id", quizId) }
      }
      true
    } catch (e: Exception) {
      Log.e(tag, "Error deleting quiz $quizId: ${e.message}", e)
      false
    }
  }

  // -------------------------------------------------------------
  // Answer Keys
  // -------------------------------------------------------------
  suspend fun getAllAnswerKeySets(): List<AnswerKeySetRemote> {
    if (!SupabaseConfig.isConfigured()) return emptyList()
    return try {
      client.postgrest["answer_key_sets"].select().decodeList<AnswerKeySetRemote>()
    } catch (e: Exception) {
      Log.e(tag, "Error fetching answer keys: ${e.message}", e)
      emptyList()
    }
  }

  suspend fun insertAnswerKeySet(set: AnswerKeySetRemote): Boolean {
    if (!SupabaseConfig.isConfigured()) return false
    return try {
      client.postgrest["answer_key_sets"].insert(set)
      true
    } catch (e: Exception) {
      Log.e(tag, "Error inserting answer key set: ${e.message}", e)
      false
    }
  }

  suspend fun getAnswerKeyItems(keyId: String): List<AnswerKeyItemRemote> {
    if (!SupabaseConfig.isConfigured()) return emptyList()
    return try {
      client.postgrest["answer_key_items"].select {
        filter { eq("key_id", keyId) }
      }.decodeList<AnswerKeyItemRemote>()
    } catch (e: Exception) {
      Log.e(tag, "Error fetching answer key items: ${e.message}", e)
      emptyList()
    }
  }

  suspend fun insertAnswerKeyItems(items: List<AnswerKeyItemRemote>): Boolean {
    if (!SupabaseConfig.isConfigured() || items.isEmpty()) return false
    return try {
      client.postgrest["answer_key_items"].insert(items)
      true
    } catch (e: Exception) {
      Log.e(tag, "Error inserting answer key items: ${e.message}", e)
      false
    }
  }

  // -------------------------------------------------------------
  // Scanned Papers
  // -------------------------------------------------------------
  suspend fun getPapersForQuiz(quizId: String): List<ScannedPaperRemote> {
    if (!SupabaseConfig.isConfigured()) return emptyList()
    return try {
      client.postgrest["scanned_papers"].select {
        filter { eq("quiz_id", quizId) }
      }.decodeList<ScannedPaperRemote>()
    } catch (e: Exception) {
      Log.e(tag, "Error fetching papers for quiz $quizId: ${e.message}", e)
      emptyList()
    }
  }

  suspend fun insertScannedPaper(paper: ScannedPaperRemote): Boolean {
    if (!SupabaseConfig.isConfigured()) return false
    return try {
      client.postgrest["scanned_papers"].insert(paper)
      true
    } catch (e: Exception) {
      Log.e(tag, "Error inserting scanned paper: ${e.message}", e)
      false
    }
  }

  suspend fun uploadPaperImage(imageBytes: ByteArray, fileName: String): String? {
    if (!SupabaseConfig.isConfigured()) return null
    return try {
      val bucket = client.storage[SupabaseConfig.IMAGES_BUCKET]
      bucket.upload(fileName, imageBytes, upsert = true)
      bucket.publicUrl(fileName)
    } catch (e: Exception) {
      Log.e(tag, "Error uploading paper image: ${e.message}", e)
      null
    }
  }

  // -------------------------------------------------------------
  // Auth & Admin Workflow
  // -------------------------------------------------------------
  suspend fun checkApprovedUser(email: String): ApprovedUserRemote? {
    if (!SupabaseConfig.isConfigured()) return null
    return try {
      client.postgrest["approved_users"].select {
        filter { eq("email", email.trim().lowercase()) }
      }.decodeSingleOrNull<ApprovedUserRemote>()
    } catch (e: Exception) {
      Log.e(tag, "Error checking approved user $email: ${e.message}", e)
      null
    }
  }

  suspend fun addApprovedUser(email: String, name: String, role: String = "team"): Boolean {
    if (!SupabaseConfig.isConfigured()) return false
    return try {
      val existing = checkApprovedUser(email)
      if (existing == null) {
        val user = ApprovedUserRemote(
          email = email.trim().lowercase(),
          name = name,
          role = role
        )
        client.postgrest["approved_users"].insert(user)
      }
      true
    } catch (e: Exception) {
      Log.e(tag, "Error adding approved user $email: ${e.message}", e)
      false
    }
  }

  suspend fun getAllApprovedUsers(): List<ApprovedUserRemote> {
    if (!SupabaseConfig.isConfigured()) return emptyList()
    return try {
      client.postgrest["approved_users"].select().decodeList<ApprovedUserRemote>()
    } catch (e: Exception) {
      Log.e(tag, "Error fetching approved users: ${e.message}", e)
      emptyList()
    }
  }

  suspend fun updateUserRole(email: String, newRole: String): Boolean {
    if (!SupabaseConfig.isConfigured()) return false
    return try {
      client.postgrest["approved_users"].update({
        set("role", newRole)
      }) {
        filter { eq("email", email.trim().lowercase()) }
      }
      true
    } catch (e: Exception) {
      Log.e(tag, "Error updating user role for $email: ${e.message}", e)
      false
    }
  }

  suspend fun revokeUserAccess(email: String): Boolean {
    if (!SupabaseConfig.isConfigured()) return false
    return try {
      client.postgrest["approved_users"].delete {
        filter { eq("email", email.trim().lowercase()) }
      }
      true
    } catch (e: Exception) {
      Log.e(tag, "Error revoking access for $email: ${e.message}", e)
      false
    }
  }
}
