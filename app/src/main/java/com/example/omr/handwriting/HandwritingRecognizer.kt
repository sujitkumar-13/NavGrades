package com.example.omr.handwriting

import android.graphics.Bitmap

/**
 * Status of an individual recognition result.
 */
enum class RecognitionStatus {
  HIGH_CONFIDENCE,
  LOW_CONFIDENCE,
  EMPTY,
  INVALID
}

/**
 * Encapsulates the recognized text, confidence score, and candidate classifications.
 */
data class RecognitionResult(
  val text: String,
  val confidence: Float,
  val status: RecognitionStatus,
  val topCandidates: List<Pair<String, Float>> = emptyList(),
  val rawInkCount: Int = 0,
  val provider: String = "LOCAL",
  val fallbackUsed: Boolean = false,
  val reviewRequired: Boolean = false
)

/**
 * Generalized recognizer interface decoupling inference from input extraction and preprocessing.
 */
interface HandwritingRecognizer {
  suspend fun recognizeLetter(cell: Bitmap): RecognitionResult
  suspend fun recognizeDigit(cell: Bitmap): RecognitionResult
  suspend fun recognizeWord(line: Bitmap): RecognitionResult
}
