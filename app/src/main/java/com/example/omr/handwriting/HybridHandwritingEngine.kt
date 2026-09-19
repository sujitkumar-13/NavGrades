package com.example.omr.handwriting

import android.graphics.Bitmap

/**
 * Clean on-device implementation of HandwritingRecognitionEngine.
 *
 * - Track A (Boxed Fields): Deterministic box geometry + frozen TFLite baseline models
 *   for First Name, Last Name, Phone, and WhatsApp.
 * - Track B (Freehand Fields): ML Kit (MlKitFreehandOcrProvider)
 *   for City/Block/Village and School/College.
 */
class HybridHandwritingEngine(
  private val freehandProvider: FreehandOcrProvider = MlKitFreehandOcrProvider()
) : HandwritingRecognitionEngine {

  override suspend fun recognizeStudentInfo(crops: OmrFieldCrops): HandwritingScanResult {
    val startTime = System.currentTimeMillis()
    val rectifiedSheet = crops.rectifiedSheet

    // 1. Boxed Fields via TFLite models
    val tfliteFn = BoxedFieldRecognizer.recognizeFromSheet(rectifiedSheet, BoxedFieldType.FIRST_NAME)
    val tfliteLn = BoxedFieldRecognizer.recognizeFromSheet(rectifiedSheet, BoxedFieldType.LAST_NAME)
    val tflitePhone = BoxedFieldRecognizer.recognizeFromSheet(rectifiedSheet, BoxedFieldType.PHONE)
    val tfliteWa = BoxedFieldRecognizer.recognizeFromSheet(rectifiedSheet, BoxedFieldType.WHATSAPP)

    // 2. Freehand Fields via ML Kit
    val (cityResult, schoolResult) = freehandProvider.recognizeFreehandFields(crops.cityCrop, crops.schoolCrop)

    val finalFirstName = tfliteFn.text
    val finalLastName = tfliteLn.text
    val finalPhone = tflitePhone.text
    val finalWa = tfliteWa.text.ifBlank { tflitePhone.text }

    // 3. Course Code: Static header "SOB" is not an active student input field; no OCR performed
    val courseCodeClean = "SOB"

    val totalTime = (System.currentTimeMillis() - startTime).toFloat()

    val runAudit = HandwritingRunAudit(
      modelMode = OmrHandwritingConfig.currentMode,
      letterModelName = OmrHandwritingEngine.LETTER_MODEL_ASSET,
      digitModelName = OmrHandwritingEngine.DIGIT_MODEL_ASSET,
      firstNameAudit = tfliteFn.fieldAudit,
      lastNameAudit = tfliteLn.fieldAudit,
      phoneAudit = tflitePhone.fieldAudit,
      whatsappAudit = tfliteWa.fieldAudit,
      totalInferenceTimeMs = totalTime
    )
    OmrHandwritingConfig.lastRunAudit = runAudit

    // Optional debug export
    if (OmrHandwritingConfig.isDebugExportEnabled) {
      DebugImageExporter.exportAll(
        sheet = rectifiedSheet,
        fnCrop = crops.cityCrop,
        fnResult = tfliteFn,
        lnResult = tfliteLn,
        phoneResult = tflitePhone,
        waResult = tfliteWa,
        cityResult = cityResult,
        schoolResult = schoolResult
      )
    }

    val combinedName = listOf(finalFirstName, finalLastName)
      .filter { it.isNotBlank() }
      .joinToString(" ")
      .trim()

    val fieldMap = mapOf(
      "firstName" to RecognitionResult(tfliteFn.text, tfliteFn.avgConfidence, tfliteFn.status),
      "lastName" to RecognitionResult(tfliteLn.text, tfliteLn.avgConfidence, tfliteLn.status),
      "phone" to RecognitionResult(tflitePhone.text, tflitePhone.avgConfidence, tflitePhone.status),
      "whatsapp" to RecognitionResult(tfliteWa.text, tfliteWa.avgConfidence, tfliteWa.status),
      "city" to cityResult,
      "school" to schoolResult
    )

    return HandwritingScanResult(
      firstName = finalFirstName,
      lastName = finalLastName,
      studentName = combinedName,
      phone = finalPhone,
      whatsapp = finalWa,
      city = cityResult.text,
      school = schoolResult.text,
      courseCode = courseCodeClean,
      runAudit = runAudit,
      fieldResults = fieldMap,
      provider = "TFLITE_MLKIT",
      fallbackUsed = false,
      reviewRequired = cityResult.reviewRequired || schoolResult.reviewRequired
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
