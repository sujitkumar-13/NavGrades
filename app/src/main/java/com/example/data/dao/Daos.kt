package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AnswerKeyItemEntity
import com.example.data.model.AnswerKeySetEntity
import com.example.data.model.QuizEntity
import com.example.data.model.ScannedPaperEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {
  @Query("SELECT * FROM quizzes ORDER BY createdAt DESC")
  fun getAllQuizzes(): Flow<List<QuizEntity>>

  @Query("SELECT * FROM quizzes WHERE id = :id LIMIT 1")
  suspend fun getQuizById(id: String): QuizEntity?

  @Query("SELECT * FROM quizzes WHERE id = :id LIMIT 1")
  fun observeQuizById(id: String): Flow<QuizEntity?>

  @Query("SELECT * FROM quizzes WHERE answerKeyId = :answerKeyId")
  suspend fun getQuizzesByAnswerKeyId(answerKeyId: String): List<QuizEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertQuiz(quiz: QuizEntity)

  @Update
  suspend fun updateQuiz(quiz: QuizEntity)

  @Query("DELETE FROM quizzes WHERE id = :id")
  suspend fun deleteQuizById(id: String)
}

@Dao
interface AnswerKeySetDao {
  @Query("SELECT * FROM answer_key_sets ORDER BY updatedAt DESC")
  fun getAllAnswerKeySets(): Flow<List<AnswerKeySetEntity>>

  @Query("SELECT * FROM answer_key_sets WHERE id = :id LIMIT 1")
  suspend fun getAnswerKeySetById(id: String): AnswerKeySetEntity?

  @Query("SELECT * FROM answer_key_sets WHERE id = :id LIMIT 1")
  fun observeAnswerKeySetById(id: String): Flow<AnswerKeySetEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAnswerKeySet(set: AnswerKeySetEntity)

  @Update
  suspend fun updateAnswerKeySet(set: AnswerKeySetEntity)

  @Query("DELETE FROM answer_key_sets WHERE id = :id")
  suspend fun deleteAnswerKeySetById(id: String)
}

@Dao
interface AnswerKeyDao {
  @Query("SELECT * FROM answer_key_items WHERE keyId = :keyId ORDER BY questionIndex ASC")
  fun getItemsForKey(keyId: String): Flow<List<AnswerKeyItemEntity>>

  @Query("SELECT * FROM answer_key_items WHERE keyId = :keyId ORDER BY questionIndex ASC")
  suspend fun getItemsForKeyDirect(keyId: String): List<AnswerKeyItemEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertItems(items: List<AnswerKeyItemEntity>)

  @Query("DELETE FROM answer_key_items WHERE keyId = :keyId")
  suspend fun deleteItemsForKey(keyId: String)
}

@Dao
interface ScannedPaperDao {
  @Query("SELECT * FROM scanned_papers WHERE quizId = :quizId ORDER BY scannedAt DESC")
  fun getPapersForQuiz(quizId: String): Flow<List<ScannedPaperEntity>>

  @Query("SELECT * FROM scanned_papers WHERE id = :id LIMIT 1")
  suspend fun getPaperById(id: String): ScannedPaperEntity?

  @Query("SELECT * FROM scanned_papers WHERE id = :id LIMIT 1")
  fun observePaperById(id: String): Flow<ScannedPaperEntity?>

  @Query("SELECT COUNT(*) FROM scanned_papers WHERE quizId = :quizId")
  fun getPaperCountForQuiz(quizId: String): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPaper(paper: ScannedPaperEntity)

  @Update
  suspend fun updatePaper(paper: ScannedPaperEntity)

  @Query("DELETE FROM scanned_papers WHERE id = :id")
  suspend fun deletePaperById(id: String)

  @Query("DELETE FROM scanned_papers WHERE quizId = :quizId")
  suspend fun deletePapersForQuiz(quizId: String)
}
