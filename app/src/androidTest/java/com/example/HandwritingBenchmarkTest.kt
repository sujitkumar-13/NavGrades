package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class HandwritingBenchmarkTest {

  companion object {
    private const val TAG = "OMR_BENCHMARK"
  }

  private fun loadBitmap(name: String): Bitmap? {
    val context = InstrumentationRegistry.getInstrumentation().context
    return try {
      context.assets.open("benchmark/$name").use { stream ->
        BitmapFactory.decodeStream(stream)
      }
    } catch (e: Exception) {
      // Fallback to /data/local/tmp/omr_benchmark/
      val file = File("/data/local/tmp/omr_benchmark/$name")
      if (file.exists()) {
        BitmapFactory.decodeFile(file.absolutePath)
      } else {
        null
      }
    }
  }

  private suspend fun runMlKit(bitmap: Bitmap, recognizer: TextRecognizer): String =
    suspendCancellableCoroutine { cont ->
      val inputImage = InputImage.fromBitmap(bitmap, 0)
      recognizer.process(inputImage)
        .addOnSuccessListener { visionText ->
          cont.resume(visionText.text.trim())
        }
        .addOnFailureListener {
          cont.resume("")
        }
    }

  private fun padAndScaleBitmap(src: Bitmap, padPx: Int = 8, scale: Float = 2.0f): Bitmap {
    val paddedW = src.width + (padPx * 2)
    val paddedH = src.height + (padPx * 2)
    val padded = Bitmap.createBitmap(paddedW, paddedH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(padded)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(src, padPx.toFloat(), padPx.toFloat(), null)

    return if (scale != 1.0f) {
      val targetW = (paddedW * scale).toInt()
      val targetH = (paddedH * scale).toInt()
      Bitmap.createScaledBitmap(padded, targetW, targetH, true)
    } else {
      padded
    }
  }

  private fun binarizeAndEnhance(src: Bitmap, thresholdOffset: Int = 10): Bitmap {
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)

    // Calculate Otsu or mean threshold
    var sum = 0L
    for (p in pixels) {
      val r = (p shr 16) and 0xFF
      val g = (p shr 8) and 0xFF
      val b = p and 0xFF
      sum += (r + g + b) / 3
    }
    val mean = (sum / pixels.size).toInt()
    val threshold = (mean - thresholdOffset).coerceIn(40, 220)

    val outPixels = IntArray(w * h)
    for (i in pixels.indices) {
      val p = pixels[i]
      val r = (p shr 16) and 0xFF
      val g = (p shr 8) and 0xFF
      val b = p and 0xFF
      val gray = (r + g + b) / 3
      outPixels[i] = if (gray < threshold) Color.BLACK else Color.WHITE
    }

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(outPixels, 0, w, 0, 0, w, h)
    return out
  }

  private fun suppressHorizontalLine(src: Bitmap, maxLineHeight: Int = 4): Bitmap {
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)

    // Find row darkness profile to detect long continuous horizontal lines
    val darkPerRow = IntArray(h)
    for (y in 0 until h) {
      var count = 0
      for (x in 0 until w) {
        val p = pixels[y * w + x]
        val gray = ((p shr 16) and 0xFF + (p shr 8) and 0xFF + (p and 0xFF)) / 3
        if (gray < 160) count++
      }
      darkPerRow[y] = count
    }

    // Rows with > 40% dark pixels are likely the underline baseline
    val lineThreshold = (w * 0.40).toInt()
    val outPixels = pixels.clone()
    for (y in 0 until h) {
      if (darkPerRow[y] >= lineThreshold) {
        // Clear this row and adjacent 1 row to white
        for (dy in -1..1) {
          val targetY = y + dy
          if (targetY in 0 until h) {
            for (x in 0 until w) {
              outPixels[targetY * w + x] = Color.WHITE
            }
          }
        }
      }
    }

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(outPixels, 0, w, 0, 0, w, h)
    return out
  }

  private suspend fun recognizeBoxedCells(
    bitmap: Bitmap,
    numBoxes: Int,
    recognizer: TextRecognizer
  ): Pair<String, List<String>> {
    val bW = bitmap.width
    val bH = bitmap.height
    val boxW = bW.toFloat() / numBoxes.toFloat()

    val recognizedChars = mutableListOf<String>()
    val resultBuilder = StringBuilder()

    for (i in 0 until numBoxes) {
      val left = (i * boxW).toInt().coerceIn(0, bW - 1)
      val right = ((i + 1) * boxW).toInt().coerceIn(left + 1, bW)
      val w = right - left
      val h = bH

      // Inset by 12% to avoid the printed box border
      val insetX = (w * 0.12f).toInt().coerceAtLeast(1)
      val insetY = (h * 0.12f).toInt().coerceAtLeast(1)
      val cellX = (left + insetX).coerceIn(0, bW - 1)
      val cellY = insetY.coerceIn(0, bH - 1)
      val cellW = (w - (2 * insetX)).coerceIn(1, bW - cellX)
      val cellH = (h - (2 * insetY)).coerceIn(1, bH - cellY)

      val cellCrop = Bitmap.createBitmap(bitmap, cellX, cellY, cellW, cellH)

      // Count ink pixels to detect empty box
      var darkCount = 0
      val totalPixels = cellW * cellH
      for (y in 0 until cellH) {
        for (x in 0 until cellW) {
          val p = cellCrop.getPixel(x, y)
          val gray = ((p shr 16) and 0xFF + (p shr 8) and 0xFF + (p and 0xFF)) / 3
          if (gray < 165) darkCount++
        }
      }

      val inkRatio = darkCount.toFloat() / totalPixels.toFloat()
      if (inkRatio > 0.04f) {
        // Has ink -> enhance, pad, and scale up
        val enhanced = binarizeAndEnhance(cellCrop)
        val ready = padAndScaleBitmap(enhanced, padPx = 12, scale = 3.0f)
        val text = runMlKit(ready, recognizer)
        val clean = text.replace(Regex("""[^A-Za-z0-9]"""), "").trim()
        val char = if (clean.isNotEmpty()) clean.take(1) else "?"
        recognizedChars.add(char)
        resultBuilder.append(char)
      } else {
        recognizedChars.add(" ")
      }
    }

    return Pair(resultBuilder.toString().trim(), recognizedChars)
  }

  @Test
  fun runHandwritingBenchmark() {
    runBlocking {
      val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
      Log.i(TAG, "=========================================================")
    Log.i(TAG, "STARTING HANDWRITING RECOGNITION POC BENCHMARK")
    Log.i(TAG, "=========================================================")

    val testCases = listOf(
      TestCase("First Name", "fn", "SUJIT", 18, isBoxed = true),
      TestCase("Last Name", "ln", "KUMAR", 18, isBoxed = true),
      TestCase("Phone Number", "phone", "9315429137", 10, isBoxed = true),
      TestCase("WhatsApp Number", "wa", "9315429137", 10, isBoxed = true),
      TestCase("City / Village", "city", "Jashpur", 0, isBoxed = false),
      TestCase("School / College", "school", "Vidya & Child", 0, isBoxed = false)
    )

    val resultsJson = JSONObject()

    for (tc in testCases) {
      Log.i(TAG, "---------------------------------------------------------")
      Log.i(TAG, "TEST CASE: ${tc.field} (Ground Truth: '${tc.groundTruth}')")

      // 1. Baseline: Uncalibrated crop directly into ML Kit (Continuous)
      val baselineBmp = loadBitmap("baseline_${tc.id}.png")
      val baselineRaw = if (baselineBmp != null) runMlKit(baselineBmp, recognizer) else "ERROR_LOADING"
      Log.i(TAG, "  [Baseline Raw OCR]              -> '$baselineRaw'")

      // 2. Calibrated Crop: Exact ROI directly into ML Kit (Continuous)
      val benchBmp = loadBitmap("bench_${tc.id}.png")
      val calibratedContinuous = if (benchBmp != null) runMlKit(benchBmp, recognizer) else "ERROR_LOADING"
      val scaledContinuous = if (benchBmp != null) {
        val scaled = padAndScaleBitmap(benchBmp, padPx = 16, scale = 2.5f)
        runMlKit(scaled, recognizer)
      } else "ERROR_LOADING"
      Log.i(TAG, "  [Calibrated 1x Continuous]      -> '$calibratedContinuous'")
      Log.i(TAG, "  [Calibrated 2.5x Continuous]    -> '$scaledContinuous'")

      // 3. Preprocessed & Segmented Pipeline
      val proposedResult: String
      val details: String
      if (benchBmp != null) {
        if (tc.isBoxed) {
          val (text, cells) = recognizeBoxedCells(benchBmp, tc.numBoxes, recognizer)
          proposedResult = text
          details = "Cells: ${cells.filter { it.isNotBlank() }.joinToString(",")}"
        } else {
          val lineSuppressed = suppressHorizontalLine(benchBmp)
          val scaled = padAndScaleBitmap(lineSuppressed, padPx = 10, scale = 2.0f)
          proposedResult = runMlKit(scaled, recognizer)
          details = "LineSuppressed + Scaled"
        }
      } else {
        proposedResult = "ERROR_LOADING"
        details = "Image missing"
      }
      Log.i(TAG, "  [Preprocessed & Segmented OCR]  -> '$proposedResult' ($details)")

      val fieldResult = JSONObject().apply {
        put("field", tc.field)
        put("groundTruth", tc.groundTruth)
        put("baselineRaw", baselineRaw)
        put("calibratedContinuous", calibratedContinuous)
        put("scaledContinuous", scaledContinuous)
        put("proposedResult", proposedResult)
        put("details", details)
      }
      resultsJson.put(tc.id, fieldResult)
    }

    recognizer.close()
    Log.i(TAG, "=========================================================")
    Log.i(TAG, "BENCHMARK SUMMARY JSON:")
    Log.i(TAG, resultsJson.toString(2))
    Log.i(TAG, "=========================================================")

    // Save JSON to /data/local/tmp/omr_benchmark_results.json
    try {
      val outFile = File("/data/local/tmp/omr_benchmark_results.json")
      FileOutputStream(outFile).use { fos ->
        fos.write(resultsJson.toString(2).toByteArray())
      }
      Log.i(TAG, "Benchmark results written to /data/local/tmp/omr_benchmark_results.json")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to write results file", e)
    }
    }
  }

  data class TestCase(
    val field: String,
    val id: String,
    val groundTruth: String,
    val numBoxes: Int,
    val isBoxed: Boolean
  )
}
