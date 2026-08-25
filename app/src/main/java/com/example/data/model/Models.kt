package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "quizzes")
data class QuizEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val name: String,
  val date: String,
  val numQuestions: Int = 16,
  val answerKeyId: String? = null,
  val templateType: String = "standard_16",
  val defaultMarks: Float = 1.0f,
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "answer_key_sets")
data class AnswerKeySetEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val name: String,
  val numQuestions: Int = 16,
  val defaultMarks: Float = 1.0f,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "answer_key_items", primaryKeys = ["keyId", "questionIndex"])
data class AnswerKeyItemEntity(
  val keyId: String,
  val questionIndex: Int, // 1-indexed (1..numQuestions)
  val correctOption: String, // "A", "B", "C", "D"
  val marks: Float = 1.0f
)

@Entity(tableName = "scanned_papers")
data class ScannedPaperEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val quizId: String,
  val studentId: String,
  val studentName: String,
  val answersJson: String, // JSON: {"1":"A","2":"B","3":"BLANK",...}
  val score: Float,
  val totalPossibleMarks: Float,
  val percentage: Float,
  val correctCount: Int,
  val wrongCount: Int,
  val blankCount: Int,
  val multipleCount: Int,
  val reviewRequiredCount: Int,
  val imagePath: String? = null,
  val whatsappNumber: String = "",
  val block: String = "",
  val cast: String = "",
  val gender: String = "",
  val qualification: String = "",
  val questionSetName: String = "",
  val scannedAt: Long = System.currentTimeMillis()
)

data class QuestionEvaluation(
  val questionNumber: Int,
  val studentAnswer: String, // "A", "B", "C", "D", "BLANK", "MULTIPLE", "REVIEW"
  val correctAnswer: String, // "A", "B", "C", "D"
  val marksAwarded: Float,
  val maxMarks: Float,
  val status: EvaluationStatus
)

enum class EvaluationStatus {
  CORRECT,
  WRONG,
  BLANK,
  MULTIPLE,
  REVIEW_REQUIRED
}
