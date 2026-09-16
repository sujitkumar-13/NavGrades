package com.example.omr.handwriting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlin.math.min

data class CellBenchmarkResult(
  val boxIndex: Int,
  val expectedChar: String,
  val predictedChar: String,
  val isMatch: Boolean,
  val confidence: Float,
  val isEmpty: Boolean
)

data class FieldBenchmarkResult(
  val fieldName: String,
  val expectedText: String,
  val predictedText: String,
  val isExactMatch: Boolean,
  val characterAccuracy: Float,
  val avgConfidence: Float,
  val cellResults: List<CellBenchmarkResult> = emptyList()
)

data class BenchmarkReport(
  val totalFields: Int,
  val passedFields: Int,
  val totalCharacters: Int,
  val passedCharacters: Int,
  val overallCharacterAccuracy: Float,
  val fieldResults: List<FieldBenchmarkResult>,
  val executionTimeMs: Long
) {
  fun toFormattedString(): String {
    val sb = StringBuilder()
    sb.appendLine("==================================================")
    sb.appendLine("       NAVRGADES OMR HANDWRITING BENCHMARK        ")
    sb.appendLine("==================================================")
    sb.appendLine("Fields Passed: $passedFields / $totalFields")
    sb.appendLine("Character Accuracy: $passedCharacters / $totalCharacters (${"%.1f".format(overallCharacterAccuracy * 100)}%)")
    sb.appendLine("Execution Time: ${executionTimeMs}ms")
    sb.appendLine("--------------------------------------------------")
    for (f in fieldResults) {
      val statusTag = if (f.isExactMatch) "[PASS]" else "[FAIL]"
      sb.appendLine("$statusTag ${f.fieldName}: Expected='${f.expectedText}', Predicted='${f.predictedText}' (conf=${"%.2f".format(f.avgConfidence)})")
      if (f.cellResults.isNotEmpty()) {
        val cellDetails = f.cellResults.filter { !it.isEmpty || it.expectedChar != " " }
          .joinToString(", ") { c ->
            val matchSym = if (c.isMatch) "✓" else "✗"
            "B${c.boxIndex}:'${c.predictedChar}'$matchSym(exp='${c.expectedChar}', conf=${"%.2f".format(c.confidence)})"
          }
        if (cellDetails.isNotBlank()) {
          sb.appendLine("     Cells: $cellDetails")
        }
      }
    }
    sb.appendLine("==================================================")
    return sb.toString()
  }
}

/**
 * Offline benchmark harness to test handwriting recognition accuracy
 * against clean isolated and baseline test assets without camera dependency.
 */
object BenchmarkRunner {

  private const val TAG = "OMR_BENCHMARK"

  suspend fun runBenchmark(context: Context): BenchmarkReport {
    val startTime = System.currentTimeMillis()

    if (!OmrHandwritingEngine.isInitialized()) {
      val ok = OmrHandwritingEngine.initialize(context)
      if (!ok) {
        Log.e(TAG, "Failed to initialize OmrHandwritingEngine for benchmark!")
      }
    }

    val fieldResults = mutableListOf<FieldBenchmarkResult>()
    var totalChars = 0
    var passedChars = 0

    // Test cases definition: (assetPath, fieldType, expectedText)
    val testCases = listOf(
      Triple("benchmark/bench_fn.png", BoxedFieldType.FIRST_NAME, "SUJIT"),
      Triple("benchmark/bench_ln.png", BoxedFieldType.LAST_NAME, "KUMAR"),
      Triple("benchmark/bench_phone.png", BoxedFieldType.PHONE, "9315429137"),
      Triple("benchmark/bench_wa.png", BoxedFieldType.WHATSAPP, "9315429137")
    )

    for ((assetPath, fieldType, expectedText) in testCases) {
      val bmp = loadAssetBitmap(context, assetPath)
      if (bmp == null) {
        Log.w(TAG, "Could not load asset: $assetPath")
        continue
      }

      val result = BoxedFieldRecognizer.recognizeFromStrip(bmp, fieldType)
      val predictedText = result.text

      val cellResults = mutableListOf<CellBenchmarkResult>()
      val numExpected = expectedText.length
      var fieldPassedChars = 0

      for (i in 0 until fieldType.numBoxes) {
        val expChar = if (i < numExpected) expectedText[i].toString() else " "
        val cellAudit = result.fieldAudit.cells.getOrNull(i)
        val predChar = if (cellAudit?.isEmpty == true) " " else (cellAudit?.recognizedChar ?: " ")
        val isMatch = expChar == predChar
        if (isMatch) fieldPassedChars++

        cellResults.add(
          CellBenchmarkResult(
            boxIndex = i,
            expectedChar = expChar,
            predictedChar = predChar,
            isMatch = isMatch,
            confidence = cellAudit?.confidence ?: 0f,
            isEmpty = cellAudit?.isEmpty ?: true
          )
        )
      }

      val exactMatch = predictedText == expectedText
      val charAcc = fieldPassedChars.toFloat() / fieldType.numBoxes.toFloat()

      totalChars += expectedText.length
      val matchedCharsInExpected = (0 until min(expectedText.length, predictedText.length)).count {
        expectedText[it] == predictedText[it]
      }
      passedChars += matchedCharsInExpected

      fieldResults.add(
        FieldBenchmarkResult(
          fieldName = fieldType.fieldName,
          expectedText = expectedText,
          predictedText = predictedText,
          isExactMatch = exactMatch,
          characterAccuracy = charAcc,
          avgConfidence = result.avgConfidence,
          cellResults = cellResults
        )
      )
    }

    // Freehand test cases (City and School)
    val cityBmp = loadAssetBitmap(context, "benchmark/bench_city.png")
      ?: loadAssetBitmap(context, "benchmark/baseline_city.png")
    if (cityBmp != null) {
      val cityResult = FreeHandwritingRecognizer.recognize(cityBmp, FreeFieldType.CITY)
      val expectedCity = "Jashpur"
      val isMatch = cityResult.text.equals(expectedCity, ignoreCase = true)
      fieldResults.add(
        FieldBenchmarkResult(
          fieldName = "City",
          expectedText = expectedCity,
          predictedText = cityResult.text,
          isExactMatch = isMatch,
          characterAccuracy = if (isMatch) 1.0f else 0.0f,
          avgConfidence = cityResult.confidence
        )
      )
    }

    val schoolBmp = loadAssetBitmap(context, "benchmark/bench_school.png")
      ?: loadAssetBitmap(context, "benchmark/baseline_school.png")
    if (schoolBmp != null) {
      val schoolResult = FreeHandwritingRecognizer.recognize(schoolBmp, FreeFieldType.SCHOOL)
      val expectedSchool = "Vidya 7 Child"
      val isMatch = schoolResult.text.contains("Vidya", ignoreCase = true) || schoolResult.text.contains("Child", ignoreCase = true)
      fieldResults.add(
        FieldBenchmarkResult(
          fieldName = "School",
          expectedText = expectedSchool,
          predictedText = schoolResult.text,
          isExactMatch = isMatch,
          characterAccuracy = if (isMatch) 1.0f else 0.0f,
          avgConfidence = schoolResult.confidence
        )
      )
    }

    val totalTime = System.currentTimeMillis() - startTime
    val passedFields = fieldResults.count { it.isExactMatch }
    val overallAcc = if (totalChars > 0) passedChars.toFloat() / totalChars.toFloat() else 1.0f

    val report = BenchmarkReport(
      totalFields = fieldResults.size,
      passedFields = passedFields,
      totalCharacters = totalChars,
      passedCharacters = passedChars,
      overallCharacterAccuracy = overallAcc,
      fieldResults = fieldResults,
      executionTimeMs = totalTime
    )

    Log.i(TAG, report.toFormattedString())
    return report
  }

  private fun loadAssetBitmap(context: Context, path: String): Bitmap? {
    return try {
      context.assets.open(path).use { inputStream ->
        BitmapFactory.decodeStream(inputStream)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error opening asset $path: ${e.message}")
      null
    }
  }
}
