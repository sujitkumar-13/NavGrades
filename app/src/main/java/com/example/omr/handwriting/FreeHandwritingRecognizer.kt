package com.example.omr.handwriting

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Recognizer for Track B freehand fields (City / School).
 *
 * Slices printed labels, removes horizontal ruling lines, isolates ink,
 * and passes normalized handwriting to ML Kit Text Recognition.
 */
object FreeHandwritingRecognizer : FreehandOcrProvider {

  private const val TAG = "FREE_HANDWRITING"

  /**
   * Recognizes freehand text from a raw field crop.
   */
  override suspend fun recognize(
    crop: Bitmap,
    fieldType: FreeFieldType
  ): RecognitionResult {
    // 1. Preprocess: label strip, underline suppression, contrast normalization
    val preproc = HandwritingPreprocessor.preprocessFreeHandwriting(crop, fieldType)

    if (!preproc.hasInk && preproc.inkPixelCount < 15) {
      return RecognitionResult(
        text = "",
        confidence = 1.0f,
        status = RecognitionStatus.EMPTY,
        rawInkCount = preproc.inkPixelCount
      )
    }

    // 2. ML Kit OCR on isolated handwriting bitmap
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val rawText = try {
      recognizeBitmap(preproc.cleanedBitmap, recognizer)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to run ML Kit on freehand field ${fieldType.name}: ${e.message}")
      ""
    } finally {
      recognizer.close()
    }

    // 3. Post-recognition cleaning
    val clean = HandwritingValidation.cleanFreeText(rawText)

    val status = when {
      clean.isBlank() -> RecognitionStatus.EMPTY
      clean.length >= 3 -> RecognitionStatus.HIGH_CONFIDENCE
      else -> RecognitionStatus.LOW_CONFIDENCE
    }

    val confidence = when (status) {
      RecognitionStatus.HIGH_CONFIDENCE -> 0.85f
      RecognitionStatus.LOW_CONFIDENCE -> 0.50f
      RecognitionStatus.EMPTY -> 1.0f
      RecognitionStatus.INVALID -> 0.0f
    }

    return RecognitionResult(
      text = clean,
      confidence = confidence,
      status = status,
      rawInkCount = preproc.inkPixelCount
    )
  }

  private suspend fun recognizeBitmap(
    bitmap: Bitmap,
    recognizer: TextRecognizer
  ): String = suspendCancellableCoroutine { cont ->
    try {
      val inputImage = InputImage.fromBitmap(bitmap, 0)
      recognizer.process(inputImage)
        .addOnSuccessListener { visionText ->
          cont.resume(visionText.text.trim())
        }
        .addOnFailureListener { e ->
          Log.w(TAG, "ML Kit OCR failed: ${e.message}")
          cont.resume("")
        }
    } catch (e: Exception) {
      Log.w(TAG, "Exception creating InputImage: ${e.message}")
      cont.resume("")
    }
  }
}
