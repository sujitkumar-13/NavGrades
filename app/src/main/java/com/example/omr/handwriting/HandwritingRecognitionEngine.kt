package com.example.omr.handwriting

import android.graphics.Bitmap

/**
 * Top-level contract for student handwriting recognition.
 * Decouples handwriting OCR completely from the OMR bubble detection engine.
 */
interface HandwritingRecognitionEngine {
  /**
   * Recognizes student information from structured OMR field crops.
   */
  suspend fun recognizeStudentInfo(crops: OmrFieldCrops): HandwritingScanResult

  /**
   * Direct overload accepting the full rectified sheet bitmap.
   */
  suspend fun recognizeFromSheet(rectifiedSheet: Bitmap): HandwritingScanResult
}
