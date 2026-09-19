package com.example.omr.handwriting

import android.graphics.Bitmap

/**
 * Strategy interface for recognizing freehand/unboxed text regions (e.g. City, School).
 * Implemented on-device via ML Kit (MlKitFreehandOcrProvider).
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
 */
class MlKitFreehandOcrProvider : FreehandOcrProvider {
  override suspend fun recognize(crop: Bitmap, fieldType: FreeFieldType): RecognitionResult {
    return FreeHandwritingRecognizer.recognize(crop, fieldType)
  }
}
