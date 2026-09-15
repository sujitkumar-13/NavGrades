package com.example.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApprovedUserRemote(
  val id: String = "",
  val email: String,
  val name: String = "",
  val role: String = "member", // "admin" or "member"
  @SerialName("approved_at") val approvedAt: String? = null
)


@Serializable
data class QuizRemote(
  val id: String,
  val name: String,
  val date: String,
  @SerialName("num_questions") val numQuestions: Int = 16,
  @SerialName("answer_key_id") val answerKeyId: String? = null,
  @SerialName("template_type") val templateType: String = "standard_16",
  @SerialName("default_marks") val defaultMarks: Float = 1.0f,
  @SerialName("created_by") val createdBy: String? = null,
  @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AnswerKeySetRemote(
  val id: String,
  val name: String,
  @SerialName("num_questions") val numQuestions: Int = 16,
  @SerialName("default_marks") val defaultMarks: Float = 1.0f,
  @SerialName("created_at") val createdAt: String? = null,
  @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class AnswerKeyItemRemote(
  @SerialName("key_id") val keyId: String,
  @SerialName("question_index") val questionIndex: Int,
  @SerialName("correct_option") val correctOption: String,
  val marks: Float = 1.0f
)

@Serializable
data class ScannedPaperRemote(
  val id: String,
  @SerialName("quiz_id") val quizId: String,
  @SerialName("student_id") val studentId: String,
  @SerialName("student_name") val studentName: String,
  val answers: String, // JSON string representation or map of answers
  val score: Float = 0f,
  @SerialName("total_possible_marks") val totalPossibleMarks: Float = 0f,
  val percentage: Float = 0f,
  @SerialName("correct_count") val correctCount: Int = 0,
  @SerialName("wrong_count") val wrongCount: Int = 0,
  @SerialName("blank_count") val blankCount: Int = 0,
  @SerialName("multiple_count") val multipleCount: Int = 0,
  @SerialName("review_required_count") val reviewRequiredCount: Int = 0,
  @SerialName("image_url") val imageUrl: String? = null,
  @SerialName("whatsapp_number") val whatsappNumber: String = "",
  val block: String = "",
  val caste: String = "",
  val gender: String = "",
  val qualification: String = "",
  @SerialName("question_set_name") val questionSetName: String = "",
  @SerialName("scanned_by") val scannedBy: String? = null,
  @SerialName("scanned_at") val scannedAt: String? = null
)
