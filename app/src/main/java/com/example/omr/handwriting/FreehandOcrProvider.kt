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

  /**
   * Recognizes all six student information fields (First Name, Last Name, Phone, WhatsApp, City, School)
   * in a single multimodal Gemini Vision request.
   * Returns null if Gemini is unavailable, unauthenticated, or fails, triggering local fallback.
   */
  suspend fun recognizeAllStudentFields(
    crops: OmrFieldCrops
  ): GeminiStudentFieldsResult? {
    try {
      // 1. Prepare non-destructive crops for boxed fields (scaled to 60px height preserving aspect ratio)
      val fnCrop = crops.firstNameCrop?.let { prepareBoxedCrop(it) }
      val lnCrop = crops.lastNameCrop?.let { prepareBoxedCrop(it) }
      val phoneCrop = crops.phoneCrop?.let { prepareBoxedCrop(it) }
      val waCrop = crops.whatsappCrop?.let { prepareBoxedCrop(it) }

      // 2. Preprocess freehand crops using existing calibrated label-strip and ink isolation logic
      val cityPreproc = crops.cityCrop.let { HandwritingPreprocessor.preprocessFreeHandwriting(it, FreeFieldType.CITY) }
      val schoolPreproc = crops.schoolCrop.let { HandwritingPreprocessor.preprocessFreeHandwriting(it, FreeFieldType.SCHOOL) }

      val cityHasInk = cityPreproc.hasInk && cityPreproc.inkPixelCount >= 15
      val schoolHasInk = schoolPreproc.hasInk && schoolPreproc.inkPixelCount >= 15

      val targetCityBmp = if (cityHasInk) cityPreproc.cleanedBitmap else null
      val targetSchoolBmp = if (schoolHasInk) schoolPreproc.cleanedBitmap else null

      val response = ocrClient.recognizeAllFields(
        firstNameCrop = fnCrop,
        lastNameCrop = lnCrop,
        phoneCrop = phoneCrop,
        whatsappCrop = waCrop,
        cityCrop = targetCityBmp,
        schoolCrop = targetSchoolBmp
      )

      if (response.status == "SUCCESS" && !response.fallbackUsed) {
        val cleanFn = HandwritingValidation.validateName(response.firstName)
        val cleanLn = HandwritingValidation.validateName(response.lastName)
        val cleanPhone = HandwritingValidation.validatePhone(response.phone)
        val cleanWa = HandwritingValidation.validatePhone(response.whatsapp).ifBlank { cleanPhone }
        val cleanCity = if (!cityHasInk) "" else response.city.trim()
        val cleanSchool = if (!schoolHasInk) "" else response.school.trim()

        return GeminiStudentFieldsResult(
          firstName = RecognitionResult(
            text = cleanFn,
            confidence = if (cleanFn.isNotBlank()) 0.95f else 1.0f,
            status = if (cleanFn.isNotBlank()) RecognitionStatus.HIGH_CONFIDENCE else RecognitionStatus.EMPTY,
            provider = "GEMINI",
            fallbackUsed = false,
            reviewRequired = false
          ),
          lastName = RecognitionResult(
            text = cleanLn,
            confidence = if (cleanLn.isNotBlank()) 0.95f else 1.0f,
            status = if (cleanLn.isNotBlank()) RecognitionStatus.HIGH_CONFIDENCE else RecognitionStatus.EMPTY,
            provider = "GEMINI",
            fallbackUsed = false,
            reviewRequired = false
          ),
          phone = RecognitionResult(
            text = cleanPhone,
            confidence = if (cleanPhone.isNotBlank()) 0.95f else 1.0f,
            status = if (cleanPhone.isNotBlank()) RecognitionStatus.HIGH_CONFIDENCE else RecognitionStatus.EMPTY,
            provider = "GEMINI",
            fallbackUsed = false,
            reviewRequired = false
          ),
          whatsapp = RecognitionResult(
            text = cleanWa,
            confidence = if (cleanWa.isNotBlank()) 0.95f else 1.0f,
            status = if (cleanWa.isNotBlank()) RecognitionStatus.HIGH_CONFIDENCE else RecognitionStatus.EMPTY,
            provider = "GEMINI",
            fallbackUsed = false,
            reviewRequired = false
          ),
          city = RecognitionResult(
            text = cleanCity,
            confidence = if (cleanCity.isNotBlank()) 0.95f else 1.0f,
            status = if (cleanCity.isNotBlank()) RecognitionStatus.HIGH_CONFIDENCE else RecognitionStatus.EMPTY,
            rawInkCount = cityPreproc.inkPixelCount,
            provider = "GEMINI",
            fallbackUsed = false,
            reviewRequired = false
          ),
          school = RecognitionResult(
            text = cleanSchool,
            confidence = if (cleanSchool.isNotBlank()) 0.95f else 1.0f,
            status = if (cleanSchool.isNotBlank()) RecognitionStatus.HIGH_CONFIDENCE else RecognitionStatus.EMPTY,
            rawInkCount = schoolPreproc.inkPixelCount,
            provider = "GEMINI",
            fallbackUsed = false,
            reviewRequired = false
          ),
          fallbackUsed = false,
          reviewRequired = false,
          provider = "GEMINI"
        )
      }

      Log.w(TAG, "Gemini OCR response indicates fallback: ${response.message}")
      return null
    } catch (t: Throwable) {
      Log.e(TAG, "Exception in recognizeAllStudentFields: ${t.message}. Routing to fallback.", t)
      return null
    }
  }

  private fun prepareBoxedCrop(crop: Bitmap): Bitmap {
    val h = crop.height
    val w = crop.width
    return if (h < 60) {
      val scale = 60.0f / h.toFloat()
      val targetW = (w * scale).toInt().coerceAtLeast(1)
      Bitmap.createScaledBitmap(crop, targetW, 60, true)
    } else {
      crop
    }
  }
}

/**
 * Result of recognizing all six student fields via Gemini Vision.
 */
data class GeminiStudentFieldsResult(
  val firstName: RecognitionResult,
  val lastName: RecognitionResult,
  val phone: RecognitionResult,
  val whatsapp: RecognitionResult,
  val city: RecognitionResult,
  val school: RecognitionResult,
  val fallbackUsed: Boolean = false,
  val reviewRequired: Boolean = false,
  val provider: String = "GEMINI"
)
