package com.example.omr.handwriting

import android.graphics.Bitmap
import android.graphics.RectF
import com.example.omr.OmrLayoutDefinition

/**
 * Aggregated recognition output for all student handwriting fields.
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
  val fieldResults: Map<String, RecognitionResult>
)

/**
 * Top-level coordinator for the NavGrades handwriting recognition pipeline.
 * Delegates boxed fields to Track A (BoxedFieldRecognizer)
 * and freehand fields to Track B (FreeHandwritingRecognizer).
 */
object HandwritingEngine {

  /**
   * Processes all student handwriting fields from a rectified 682x1024 sheet.
   */
  suspend fun processSheet(
    rectifiedSheet: Bitmap
  ): HandwritingScanResult {
    val startTime = System.currentTimeMillis()

    // 1. Track A: Boxed Fields (deterministic calibrated physical geometry)
    val fnResult = BoxedFieldRecognizer.recognizeFromSheet(rectifiedSheet, BoxedFieldType.FIRST_NAME)
    val lnResult = BoxedFieldRecognizer.recognizeFromSheet(rectifiedSheet, BoxedFieldType.LAST_NAME)
    val phoneResult = BoxedFieldRecognizer.recognizeFromSheet(rectifiedSheet, BoxedFieldType.PHONE)
    val waResult = BoxedFieldRecognizer.recognizeFromSheet(rectifiedSheet, BoxedFieldType.WHATSAPP)

    // 2. Track B: Freehand Fields (City & School)
    val cityCrop = getCrop(rectifiedSheet, OmrLayoutDefinition.CITY_REGION)
    val schoolCrop = getCrop(rectifiedSheet, OmrLayoutDefinition.SCHOOL_REGION)

    val cityResult = FreeHandwritingRecognizer.recognize(cityCrop, FreeFieldType.CITY)
    val schoolResult = FreeHandwritingRecognizer.recognize(schoolCrop, FreeFieldType.SCHOOL)

    val totalTime = (System.currentTimeMillis() - startTime).toFloat()

    val runAudit = HandwritingRunAudit(
      modelMode = OmrHandwritingConfig.currentMode,
      letterModelName = OmrHandwritingEngine.LETTER_MODEL_ASSET,
      digitModelName = OmrHandwritingEngine.DIGIT_MODEL_ASSET,
      firstNameAudit = fnResult.fieldAudit,
      lastNameAudit = lnResult.fieldAudit,
      phoneAudit = phoneResult.fieldAudit,
      whatsappAudit = waResult.fieldAudit,
      totalInferenceTimeMs = totalTime
    )
    OmrHandwritingConfig.lastRunAudit = runAudit

    // Optional debug export
    if (OmrHandwritingConfig.isDebugExportEnabled) {
      DebugImageExporter.exportAll(
        sheet = rectifiedSheet,
        fnCrop = cityCrop, // Debug exporter will extract what it needs
        fnResult = fnResult,
        lnResult = lnResult,
        phoneResult = phoneResult,
        waResult = waResult,
        cityResult = cityResult,
        schoolResult = schoolResult
      )
    }

    val combinedName = listOf(fnResult.text, lnResult.text)
      .filter { it.isNotBlank() }
      .joinToString(" ")
      .trim()

    val fieldMap = mapOf(
      "firstName" to RecognitionResult(fnResult.text, fnResult.avgConfidence, fnResult.status),
      "lastName" to RecognitionResult(lnResult.text, lnResult.avgConfidence, lnResult.status),
      "phone" to RecognitionResult(phoneResult.text, phoneResult.avgConfidence, phoneResult.status),
      "whatsapp" to RecognitionResult(waResult.text, waResult.avgConfidence, waResult.status),
      "city" to cityResult,
      "school" to schoolResult
    )

    return HandwritingScanResult(
      firstName = fnResult.text,
      lastName = lnResult.text,
      studentName = combinedName,
      phone = phoneResult.text,
      whatsapp = waResult.text.ifBlank { phoneResult.text },
      city = cityResult.text,
      school = schoolResult.text,
      runAudit = runAudit,
      fieldResults = fieldMap
    )
  }

  private fun getCrop(bitmap: Bitmap, region: RectF): Bitmap {
    val bW = bitmap.width
    val bH = bitmap.height
    val cropX = (region.left * bW).toInt().coerceIn(0, bW - 1)
    val cropY = (region.top * bH).toInt().coerceIn(0, bH - 1)
    val cropW = ((region.right - region.left) * bW).toInt().coerceIn(1, bW - cropX)
    val cropH = ((region.bottom - region.top) * bH).toInt().coerceIn(1, bH - cropY)
    return Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
  }
}
