package com.example.omr.handwriting

import android.graphics.Bitmap

/**
 * Clean Hybrid implementation of HandwritingRecognitionEngine.
 *
 * - Track A (Boxed Fields): Deterministic box geometry + frozen TFLite baseline models
 *   for First Name, Last Name, Phone, and WhatsApp.
 * - Track B (Freehand Fields): Pluggable FreehandOcrProvider (defaults to MlKitFreehandOcrProvider
 *   in Phase 1) for City/Block/Village and School/College.
 */
class HybridHandwritingEngine(
  private val freehandProvider: FreehandOcrProvider = GeminiFreehandOcrProvider()
) : HandwritingRecognitionEngine {

  override suspend fun recognizeStudentInfo(crops: OmrFieldCrops): HandwritingScanResult {
    val startTime = System.currentTimeMillis()
    val rectifiedSheet = crops.rectifiedSheet

    // 1. Track A: Boxed Fields (deterministic calibrated physical geometry via TFLite)
    val fnResult = BoxedFieldRecognizer.recognizeFromSheet(rectifiedSheet, BoxedFieldType.FIRST_NAME)
    val lnResult = BoxedFieldRecognizer.recognizeFromSheet(rectifiedSheet, BoxedFieldType.LAST_NAME)
    val phoneResult = BoxedFieldRecognizer.recognizeFromSheet(rectifiedSheet, BoxedFieldType.PHONE)
    val waResult = BoxedFieldRecognizer.recognizeFromSheet(rectifiedSheet, BoxedFieldType.WHATSAPP)

    // 2. Track B: Freehand Fields (City & School) via pluggable FreehandOcrProvider (Gemini with ML Kit fallback)
    val (cityResult, schoolResult) = freehandProvider.recognizeFreehandFields(crops.cityCrop, crops.schoolCrop)

    // 3. Course Code: Static header "SOB" is not an active student input field; no OCR performed
    val courseCodeClean = "SOB"

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
        fnCrop = crops.cityCrop,
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

    val fallbackUsed = cityResult.fallbackUsed || schoolResult.fallbackUsed
    val reviewRequired = cityResult.reviewRequired || schoolResult.reviewRequired
    val provider = if (fallbackUsed) {
      "ML_KIT_FALLBACK"
    } else if (cityResult.provider == "GEMINI" || schoolResult.provider == "GEMINI") {
      "GEMINI"
    } else {
      cityResult.provider
    }

    return HandwritingScanResult(
      firstName = fnResult.text,
      lastName = lnResult.text,
      studentName = combinedName,
      phone = phoneResult.text,
      whatsapp = waResult.text.ifBlank { phoneResult.text },
      city = cityResult.text,
      school = schoolResult.text,
      courseCode = courseCodeClean,
      runAudit = runAudit,
      fieldResults = fieldMap,
      provider = provider,
      fallbackUsed = fallbackUsed,
      reviewRequired = reviewRequired
    )
  }

  override suspend fun recognizeFromSheet(rectifiedSheet: Bitmap): HandwritingScanResult {
    val crops = OmrFieldCrops.fromRectifiedSheet(rectifiedSheet)
    return recognizeStudentInfo(crops)
  }

  companion object {
    val defaultInstance: HybridHandwritingEngine by lazy { HybridHandwritingEngine() }
  }
}
