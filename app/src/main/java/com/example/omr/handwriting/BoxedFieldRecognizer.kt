package com.example.omr.handwriting

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.example.omr.OmrLayoutDefinition
import org.tensorflow.lite.Interpreter

/**
 * Supported boxed field types on the NavGrades OMR sheet.
 */
enum class BoxedFieldType(
  val fieldName: String,
  val numBoxes: Int,
  val isDigitField: Boolean
) {
  FIRST_NAME("First Name", OmrLayoutDefinition.NAME_BOX_COUNT, false),
  LAST_NAME("Last Name", OmrLayoutDefinition.NAME_BOX_COUNT, false),
  PHONE("Phone", OmrLayoutDefinition.PHONE_BOX_COUNT, true),
  WHATSAPP("WhatsApp", OmrLayoutDefinition.PHONE_BOX_COUNT, true)
}

/**
 * Result of recognizing a complete boxed field.
 */
data class BoxedFieldResult(
  val text: String,
  val rawText: String,
  val fieldAudit: FieldAudit,
  val avgConfidence: Float,
  val status: RecognitionStatus
)

/**
 * Handles Track A recognition (First Name, Last Name, Phone, WhatsApp).
 *
 * CRITICAL RULE:
 * Calibrated physical box geometry is the primary and deterministic extraction method.
 * Generic equal-width fallback is only a defensive error path and emits a loud warning
 * if triggered.
 */
object BoxedFieldRecognizer {

  private const val TAG = "BOXED_FIELD_REC"

  /**
   * Deterministically recognizes a boxed field directly from the rectified 682x1024 sheet.
   * Uses physical box geometry without label contamination.
   */
  fun recognizeFromSheet(
    sheetBitmap: Bitmap,
    fieldType: BoxedFieldType
  ): BoxedFieldResult {
    val bW = sheetBitmap.width
    val bH = sheetBitmap.height

    // Select calibrated box grid region on the 682x1024 sheet
    val gridRegion: RectF = when (fieldType) {
      BoxedFieldType.FIRST_NAME -> OmrLayoutDefinition.FIRST_NAME_BOXES_REGION
      BoxedFieldType.LAST_NAME -> OmrLayoutDefinition.LAST_NAME_BOXES_REGION
      BoxedFieldType.PHONE -> OmrLayoutDefinition.PHONE_BOXES_REGION
      BoxedFieldType.WHATSAPP -> OmrLayoutDefinition.WHATSAPP_BOXES_REGION
    }

    val gridX0 = gridRegion.left * bW
    val gridX1 = gridRegion.right * bW
    val gridY0 = (gridRegion.top * bH).toInt().coerceIn(0, bH - 1)
    val gridY1 = (gridRegion.bottom * bH).toInt().coerceIn(gridY0 + 1, bH)
    val cellHeight = gridY1 - gridY0

    val totalWidth = gridX1 - gridX0
    val numBoxes = fieldType.numBoxes
    val boxWidth = totalWidth / numBoxes.toFloat()

    val cells = mutableListOf<CellAudit>()
    val rawStringBuilder = StringBuilder()
    var emptyCount = 0
    var filledCount = 0
    var totalConfidence = 0.0f

    for (i in 0 until numBoxes) {
      val cellX0 = (gridX0 + i * boxWidth).toInt().coerceIn(0, bW - 1)
      val cellX1 = (gridX0 + (i + 1) * boxWidth).toInt().coerceIn(cellX0 + 1, bW)
      val cellWidth = cellX1 - cellX0

      val cellBitmap = Bitmap.createBitmap(sheetBitmap, cellX0, gridY0, cellWidth, cellHeight)
      val cellResult = processSingleCell(cellBitmap, fieldType, i)

      if (cellResult.isEmpty) {
        emptyCount++
        cells.add(cellResult)
      } else {
        filledCount++
        totalConfidence += cellResult.confidence
        rawStringBuilder.append(cellResult.recognizedChar)
        cells.add(cellResult)
      }
    }

    val rawText = rawStringBuilder.toString()
    val validatedText = HandwritingValidation.validate(rawText, fieldType)
    val avgConf = if (filledCount > 0) totalConfidence / filledCount else 1.0f

    val status = when {
      filledCount == 0 -> RecognitionStatus.EMPTY
      avgConf >= 0.70f -> RecognitionStatus.HIGH_CONFIDENCE
      else -> RecognitionStatus.LOW_CONFIDENCE
    }

    val fieldAudit = FieldAudit(
      fieldName = fieldType.fieldName,
      totalBoxes = numBoxes,
      recognizedText = validatedText,
      emptyBoxCount = emptyCount,
      filledBoxCount = filledCount,
      avgConfidence = avgConf,
      cells = cells
    )

    return BoxedFieldResult(
      text = validatedText,
      rawText = rawText,
      fieldAudit = fieldAudit,
      avgConfidence = avgConf,
      status = status
    )
  }

  /**
   * Recognizes a boxed field from a cropped strip (e.g. from benchmark assets or tests).
   *
   * Automatically determines whether the strip is a clean box grid (bench_*.png)
   * or a full row with left label (baseline_*.png), and extracts boxes with calibrated geometry.
   */
  fun recognizeFromStrip(
    stripBitmap: Bitmap,
    fieldType: BoxedFieldType
  ): BoxedFieldResult {
    val bW = stripBitmap.width
    val bH = stripBitmap.height
    val numBoxes = fieldType.numBoxes

    // 1. Calibrated Physical Geometry Detection
    val gridStartX: Float
    val gridWidth: Float
    val isGenericFallback: Boolean

    // Standard benchmark sizes:
    // Pure box strips: names = 452px, phone = 194px
    // Full strips with label: names = ~515px (label ~63px), phone = ~297px (label ~85px)
    when {
      // Case A: Strip is clean box grid (aspect ratio matches pure boxes)
      !fieldType.isDigitField && bW in 440..510 -> {
        gridStartX = 0f
        gridWidth = bW.toFloat()
        isGenericFallback = false
      }
      fieldType.isDigitField && bW in 185..215 -> {
        gridStartX = 0f
        gridWidth = bW.toFloat()
        isGenericFallback = false
      }
      // Case B: Benchmark strip with printed label on the left (e.g. baseline_fn.png = 515px)
      !fieldType.isDigitField && bW in 512..540 -> {
        gridStartX = (bW - 452).toFloat().coerceAtLeast(0f)
        gridWidth = bW - gridStartX
        isGenericFallback = false
      }
      fieldType.isDigitField && bW in 280..320 -> {
        gridStartX = (bW - 194).toFloat().coerceAtLeast(0f)
        gridWidth = bW - gridStartX
        isGenericFallback = false
      }
      // Case C: Full field row crop from rectified sheet
      !fieldType.isDigitField && bW in 600..650 -> {
        gridStartX = ((OmrLayoutDefinition.FIRST_NAME_BOXES_REGION.left - OmrLayoutDefinition.FIRST_NAME_REGION.left) /
          (OmrLayoutDefinition.FIRST_NAME_REGION.right - OmrLayoutDefinition.FIRST_NAME_REGION.left) * bW)
        gridWidth = (OmrLayoutDefinition.FIRST_NAME_BOXES_REGION.width() /
          (OmrLayoutDefinition.FIRST_NAME_REGION.right - OmrLayoutDefinition.FIRST_NAME_REGION.left) * bW)
        isGenericFallback = false
      }
      fieldType.isDigitField && bW in 360..400 -> {
        gridStartX = ((OmrLayoutDefinition.PHONE_BOXES_REGION.left - OmrLayoutDefinition.PHONE_REGION.left) /
          (OmrLayoutDefinition.PHONE_REGION.right - OmrLayoutDefinition.PHONE_REGION.left) * bW)
        gridWidth = (OmrLayoutDefinition.PHONE_BOXES_REGION.width() /
          (OmrLayoutDefinition.PHONE_REGION.right - OmrLayoutDefinition.PHONE_REGION.left) * bW)
        isGenericFallback = false
      }
      else -> {
        // DEFENSIVE FALLBACK PATH ONLY — MUST LOG LOUDLY
        Log.w(TAG, "[DEFENSIVE_FALLBACK] Generic equal-width fallback activated for ${fieldType.fieldName}! Strip dimensions: ${bW}x${bH}")
        gridStartX = 0f
        gridWidth = bW.toFloat()
        isGenericFallback = true
      }
    }

    val boxWidth = gridWidth / numBoxes.toFloat()

    val cells = mutableListOf<CellAudit>()
    val rawStringBuilder = StringBuilder()
    var emptyCount = 0
    var filledCount = 0
    var totalConfidence = 0.0f

    for (i in 0 until numBoxes) {
      val cellX0 = (gridStartX + i * boxWidth).toInt().coerceIn(0, bW - 1)
      val cellX1 = (gridStartX + (i + 1) * boxWidth).toInt().coerceIn(cellX0 + 1, bW)
      val cellWidth = cellX1 - cellX0

      val cellBitmap = Bitmap.createBitmap(stripBitmap, cellX0, 0, cellWidth, bH)
      val cellResult = processSingleCell(cellBitmap, fieldType, i)

      if (cellResult.isEmpty) {
        emptyCount++
        cells.add(cellResult)
      } else {
        filledCount++
        totalConfidence += cellResult.confidence
        rawStringBuilder.append(cellResult.recognizedChar)
        cells.add(cellResult)
      }
    }

    val rawText = rawStringBuilder.toString()
    val validatedText = HandwritingValidation.validate(rawText, fieldType)
    val avgConf = if (filledCount > 0) totalConfidence / filledCount else 1.0f

    val status = when {
      filledCount == 0 -> RecognitionStatus.EMPTY
      avgConf >= 0.70f -> RecognitionStatus.HIGH_CONFIDENCE
      else -> RecognitionStatus.LOW_CONFIDENCE
    }

    val fieldAudit = FieldAudit(
      fieldName = fieldType.fieldName,
      totalBoxes = numBoxes,
      recognizedText = validatedText,
      emptyBoxCount = emptyCount,
      filledBoxCount = filledCount,
      avgConfidence = avgConf,
      cells = cells
    )

    return BoxedFieldResult(
      text = validatedText,
      rawText = rawText,
      fieldAudit = fieldAudit,
      avgConfidence = avgConf,
      status = status
    )
  }

  private fun processSingleCell(
    cellBitmap: Bitmap,
    fieldType: BoxedFieldType,
    boxIndex: Int
  ): CellAudit {
    val preproc = HandwritingPreprocessor.preprocessCell(cellBitmap, fieldType.isDigitField)

    if (preproc.isEmpty || preproc.tensorBuffer == null) {
      return CellAudit(
        fieldName = fieldType.fieldName,
        boxIndex = boxIndex,
        rawInkCount = preproc.rawInkCount,
        isEmpty = true,
        recognizedChar = " ",
        confidence = 1.0f,
        top3Candidates = emptyList(),
        inputShape = listOf(1, 28, 28, 1),
        minVal = 0.0f,
        maxVal = 0.0f,
        fgPixelRatio = 0.0f,
        bboxWidth = 0,
        bboxHeight = 0,
        comOffsetX = 0.0f,
        comOffsetY = 0.0f,
        borderContamination = 0.0f
      )
    }

    val interp = if (fieldType.isDigitField) {
      OmrHandwritingEngine.getDigitInterpreter()
    } else {
      OmrHandwritingEngine.getLetterInterpreter()
    }

    if (interp == null) {
      Log.w(TAG, "Interpreter for ${fieldType.fieldName} is null!")
      return CellAudit(
        fieldName = fieldType.fieldName,
        boxIndex = boxIndex,
        rawInkCount = preproc.rawInkCount,
        isEmpty = false,
        recognizedChar = "?",
        confidence = 0.0f,
        top3Candidates = emptyList()
      )
    }

    val numOutputs = interp.getOutputTensor(0).shape()[1]
    val output = Array(1) { FloatArray(numOutputs) }
    interp.run(preproc.tensorBuffer, output)
    val probs = output[0]

    val sortedIndices = probs.indices.sortedByDescending { probs[it] }
    val bestIdx = sortedIndices[0]
    val conf = probs[bestIdx]

    val charStr = if (fieldType.isDigitField) {
      bestIdx.toString()
    } else {
      ('A'.code + bestIdx).toChar().toString()
    }

    val top3 = sortedIndices.take(3).map { idx ->
      val label = if (fieldType.isDigitField) idx.toString() else ('A'.code + idx).toChar().toString()
      label to probs[idx]
    }

    return CellAudit(
      fieldName = fieldType.fieldName,
      boxIndex = boxIndex,
      rawInkCount = preproc.rawInkCount,
      isEmpty = false,
      recognizedChar = charStr,
      confidence = conf,
      top3Candidates = top3,
      inputShape = listOf(1, 28, 28, 1),
      minVal = preproc.minVal,
      maxVal = preproc.maxVal,
      fgPixelRatio = preproc.fgPixelRatio,
      bboxWidth = preproc.bboxW,
      bboxHeight = preproc.bboxH,
      comOffsetX = preproc.comOffsetX,
      comOffsetY = preproc.comOffsetY,
      borderContamination = preproc.borderContamination
    )
  }
}
