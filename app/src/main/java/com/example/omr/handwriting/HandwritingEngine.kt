package com.example.omr.handwriting

import android.graphics.Bitmap

/**
 * Aggregated recognition output for all student handwriting and text fields.
 */
data class HandwritingScanResult(
  val firstName: String,
  val lastName: String,
  val studentName: String,
  val phone: String,
  val whatsapp: String,
  val city: String,
  val school: String,
  val runAudit: HandwritingRunAudit,
  val fieldResults: Map<String, RecognitionResult>,
  val courseCode: String = "",
  val provider: String = "LOCAL",
  val fallbackUsed: Boolean = false,
  val reviewRequired: Boolean = false
)

/**
 * Top-level coordinator for the NavGrades handwriting recognition pipeline.
 * Delegates to HybridHandwritingEngine to maintain 100% backward compatibility
 * with all existing callers.
 */
object HandwritingEngine {

  /**
   * Processes all student handwriting fields from a rectified 682x1024 sheet.
   */
  suspend fun processSheet(
    rectifiedSheet: Bitmap
  ): HandwritingScanResult {
    return HybridHandwritingEngine.defaultInstance.recognizeFromSheet(rectifiedSheet)
  }
}
