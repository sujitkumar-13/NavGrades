package com.example.omr.handwriting

import android.graphics.Bitmap
import android.util.Log

/**
 * Strategy interface for recognizing freehand/unboxed text regions (e.g. City, School).
 * Allows swappable implementations (e.g. on-device ML Kit, cloud Gemini backend proxy, hybrid fallback).
 */
interface FreehandOcrProvider {
  /**
   * Recognizes freehand text from an individual cropped field bitmap.
   */
  suspend fun recognize(crop: Bitmap, fieldType: FreeFieldType): RecognitionResult

  /**
   * Recognizes both City and School freehand fields.
   * Default implementation delegates to single-field [recognize] calls for backward compatibility.
   */
  suspend fun recognizeFreehandFields(
    cityCrop: Bitmap,
    schoolCrop: Bitmap
  ): Pair<RecognitionResult, RecognitionResult> {
    return Pair(
      recognize(cityCrop, FreeFieldType.CITY),
      recognize(schoolCrop, FreeFieldType.SCHOOL)
    )
  }
}

/**
 * On-device implementation of FreehandOcrProvider delegating to FreeHandwritingRecognizer (ML Kit).
 * Serves as the reliable offline fallback provider.
 */
class MlKitFreehandOcrProvider : FreehandOcrProvider {
  override suspend fun recognize(crop: Bitmap, fieldType: FreeFieldType): RecognitionResult {
    return FreeHandwritingRecognizer.recognize(crop, fieldType)
  }
}

/**
 * Clean Hybrid Cloud Provider delegating City & School freehand recognition to the
 * Supabase Edge Function (which proxies to Gemini API without exposing credentials).
 *
 * RELIABILITY & FALLBACK:
 * - If online and authenticated: Uses high-accuracy Gemini model.
 * - If offline, unauthenticated, network times out, or Edge Function fails:
 *   Gracefully falls back to [MlKitFreehandOcrProvider].
 * - Explicitly marks fallback results with [fallbackUsed] = true and [reviewRequired] = true.
 * - Under no circumstances will a handwriting failure halt or crash the OMR scan.
 */
class GeminiFreehandOcrProvider(
  private val ocrClient: SupabaseOcrClient = SupabaseOcrClient(),
  private val fallbackProvider: FreehandOcrProvider = MlKitFreehandOcrProvider()
) : FreehandOcrProvider {

  companion object {
    private const val TAG = "GeminiFreehandOcr"
  }

  override suspend fun recognize(crop: Bitmap, fieldType: FreeFieldType): RecognitionResult {
    val (cityRes, schoolRes) = recognizeFreehandInternal(
      cityCrop = if (fieldType == FreeFieldType.CITY) crop else null,
      schoolCrop = if (fieldType == FreeFieldType.SCHOOL) crop else null
    )
    return if (fieldType == FreeFieldType.CITY) cityRes else schoolRes
  }

  override suspend fun recognizeFreehandFields(
    cityCrop: Bitmap,
    schoolCrop: Bitmap
  ): Pair<RecognitionResult, RecognitionResult> {
    return recognizeFreehandInternal(cityCrop, schoolCrop)
  }

  private suspend fun recognizeFreehandInternal(
    cityCrop: Bitmap?,
    schoolCrop: Bitmap?
  ): Pair<RecognitionResult, RecognitionResult> {
    try {
      // 1. Preprocess crops using existing calibrated label-strip and ink isolation logic
      val cityPreproc = cityCrop?.let { HandwritingPreprocessor.preprocessFreeHandwriting(it, FreeFieldType.CITY) }
      val schoolPreproc = schoolCrop?.let { HandwritingPreprocessor.preprocessFreeHandwriting(it, FreeFieldType.SCHOOL) }

      val cityHasInk = cityPreproc != null && cityPreproc.hasInk && cityPreproc.inkPixelCount >= 15
      val schoolHasInk = schoolPreproc != null && schoolPreproc.hasInk && schoolPreproc.inkPixelCount >= 15

      // If neither field has ink, both are genuinely empty fields on the sheet
      if (!cityHasInk && !schoolHasInk) {
        val emptyCity = RecognitionResult(
          text = "",
          confidence = 1.0f,
          status = RecognitionStatus.EMPTY,
          rawInkCount = cityPreproc?.inkPixelCount ?: 0,
          provider = "GEMINI",
          fallbackUsed = false,
          reviewRequired = false
        )
        val emptySchool = RecognitionResult(
          text = "",
          confidence = 1.0f,
          status = RecognitionStatus.EMPTY,
          rawInkCount = schoolPreproc?.inkPixelCount ?: 0,
          provider = "GEMINI",
          fallbackUsed = false,
          reviewRequired = false
        )
        return Pair(emptyCity, emptySchool)
      }

      // 2. Delegate to Supabase Edge Function with existing cleaned / label-stripped crops
      val targetCityBmp = if (cityHasInk) cityPreproc?.cleanedBitmap else null
      val targetSchoolBmp = if (schoolHasInk) schoolPreproc?.cleanedBitmap else null

      val response = ocrClient.recognizeFreehand(targetCityBmp, targetSchoolBmp)

      if (response.status == "SUCCESS" && !response.fallbackUsed) {
        val cityResult = if (!cityHasInk) {
          RecognitionResult(
            text = "",
            confidence = 1.0f,
            status = RecognitionStatus.EMPTY,
            rawInkCount = cityPreproc?.inkPixelCount ?: 0,
            provider = "GEMINI",
            fallbackUsed = false,
            reviewRequired = false
          )
        } else {
          // Preserve raw visual OCR text exactly as returned without hardcoded semantic corrections
          val cleanCity = response.city.trim()
          RecognitionResult(
            text = cleanCity,
            confidence = if (cleanCity.isNotBlank()) 0.95f else 0.0f,
            status = if (cleanCity.isNotBlank()) RecognitionStatus.HIGH_CONFIDENCE else RecognitionStatus.EMPTY,
            rawInkCount = cityPreproc?.inkPixelCount ?: 0,
            provider = "GEMINI",
            fallbackUsed = false,
            reviewRequired = false
          )
        }

        val schoolResult = if (!schoolHasInk) {
          RecognitionResult(
            text = "",
            confidence = 1.0f,
            status = RecognitionStatus.EMPTY,
            rawInkCount = schoolPreproc?.inkPixelCount ?: 0,
            provider = "GEMINI",
            fallbackUsed = false,
            reviewRequired = false
          )
        } else {
          // Preserve raw visual OCR text exactly as returned without hardcoded semantic corrections
          val cleanSchool = response.school.trim()
          RecognitionResult(
            text = cleanSchool,
            confidence = if (cleanSchool.isNotBlank()) 0.95f else 0.0f,
            status = if (cleanSchool.isNotBlank()) RecognitionStatus.HIGH_CONFIDENCE else RecognitionStatus.EMPTY,
            rawInkCount = schoolPreproc?.inkPixelCount ?: 0,
            provider = "GEMINI",
            fallbackUsed = false,
            reviewRequired = false
          )
        }

        return Pair(cityResult, schoolResult)
      }

      // Edge Function error or explicit fallback requested
      Log.w(TAG, "Gemini OCR response indicates fallback (${response.message}). Routing to ML Kit.")
      return executeFallback(cityCrop, schoolCrop)
    } catch (t: Throwable) {
      Log.e(TAG, "Unexpected exception in GeminiFreehandOcrProvider: ${t.message}. Routing to ML Kit.", t)
      return executeFallback(cityCrop, schoolCrop)
    }
  }

  private suspend fun executeFallback(
    cityCrop: Bitmap?,
    schoolCrop: Bitmap?
  ): Pair<RecognitionResult, RecognitionResult> {
    val localCity = try {
      cityCrop?.let { fallbackProvider.recognize(it, FreeFieldType.CITY) }
        ?: RecognitionResult("", 1.0f, RecognitionStatus.EMPTY)
    } catch (e: Throwable) {
      Log.w(TAG, "Local fallback OCR failed on City: ${e.message}")
      RecognitionResult("", 0.0f, RecognitionStatus.EMPTY)
    }

    val localSchool = try {
      schoolCrop?.let { fallbackProvider.recognize(it, FreeFieldType.SCHOOL) }
        ?: RecognitionResult("", 1.0f, RecognitionStatus.EMPTY)
    } catch (e: Throwable) {
      Log.w(TAG, "Local fallback OCR failed on School: ${e.message}")
      RecognitionResult("", 0.0f, RecognitionStatus.EMPTY)
    }

    val flaggedCity = localCity.copy(
      provider = "ML_KIT_FALLBACK",
      fallbackUsed = true,
      reviewRequired = true,
      status = if (localCity.status == RecognitionStatus.HIGH_CONFIDENCE) RecognitionStatus.LOW_CONFIDENCE else localCity.status
    )
    val flaggedSchool = localSchool.copy(
      provider = "ML_KIT_FALLBACK",
      fallbackUsed = true,
      reviewRequired = true,
      status = if (localSchool.status == RecognitionStatus.HIGH_CONFIDENCE) RecognitionStatus.LOW_CONFIDENCE else localSchool.status
    )

    return Pair(flaggedCity, flaggedSchool)
  }
}
