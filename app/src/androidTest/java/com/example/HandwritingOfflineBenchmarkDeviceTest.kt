package com.example

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.omr.handwriting.BenchmarkRunner
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HandwritingOfflineBenchmarkDeviceTest {

  companion object {
    private const val TAG = "BENCHMARK_DEVICE_TEST"
  }

  @Test
  fun testRunOfflineHandwritingBenchmarkOnDevice() {
    runBlocking {
      val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
      Log.i(TAG, "Starting offline handwriting benchmark on physical device...")
      val report = BenchmarkRunner.runBenchmark(targetContext)
      val formatted = report.toFormattedString()
      Log.i(TAG, "\n$formatted")
      println(formatted)

      assertTrue("Benchmark must run at least 4 fields", report.totalFields >= 4)
    }
  }

  @Test
  fun testFreehandAndW02Diagnostics() {
    runBlocking {
      val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
      com.example.omr.handwriting.OmrHandwritingEngine.initialize(targetContext)

      // 1. Test W02 middle box skip (RAMESH)
      val w02Bmp = targetContext.assets.open("benchmark/bench_w02_fn.png").use {
        android.graphics.BitmapFactory.decodeStream(it)
      }
      val w02Result = com.example.omr.handwriting.BoxedFieldRecognizer.recognizeFromStrip(
        w02Bmp,
        com.example.omr.handwriting.BoxedFieldType.FIRST_NAME
      )
      Log.i(TAG, "W02 First Name: text='${w02Result.text}', raw='${w02Result.rawText}', emptyBoxes=${w02Result.fieldAudit.emptyBoxCount}")
      for (i in 0..7) {
        val c = w02Result.fieldAudit.cells[i]
        Log.i(TAG, "  W02 Cell B$i: empty=${c.isEmpty}, char='${c.recognizedChar}', rawInk=${c.rawInkCount}, conf=${c.confidence}")
      }

      // 2. Test ML Kit on City crops
      val cityBmp = targetContext.assets.open("benchmark/bench_city.png").use {
        android.graphics.BitmapFactory.decodeStream(it)
      }
      val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
        com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
      )

      // Test raw city
      val rawCityText = testOcr(cityBmp, recognizer)
      Log.i(TAG, "City Raw OCR: '$rawCityText'")

      // Test cropped city (x=0..120)
      val croppedCity = android.graphics.Bitmap.createBitmap(cityBmp, 0, 0, 120, cityBmp.height)
      val scaledCity = android.graphics.Bitmap.createScaledBitmap(croppedCity, 120 * 2, cityBmp.height * 2, true)
      val croppedCityText = testOcr(scaledCity, recognizer)
      Log.i(TAG, "City Cropped+Scaled OCR: '$croppedCityText'")

      // 3. Test ML Kit on School crops
      val schoolBmp = targetContext.assets.open("benchmark/bench_school.png").use {
        android.graphics.BitmapFactory.decodeStream(it)
      }
      val rawSchoolText = testOcr(schoolBmp, recognizer)
      Log.i(TAG, "School Raw OCR: '$rawSchoolText'")

      val croppedSchool = android.graphics.Bitmap.createBitmap(schoolBmp, 0, 0, 160, schoolBmp.height)
      val scaledSchool = android.graphics.Bitmap.createScaledBitmap(croppedSchool, 160 * 2, schoolBmp.height * 2, true)
      val croppedSchoolText = testOcr(scaledSchool, recognizer)
      Log.i(TAG, "School Cropped+Scaled OCR: '$croppedSchoolText'")

      recognizer.close()
    }
  }

  private suspend fun testOcr(
    bitmap: android.graphics.Bitmap,
    recognizer: com.google.mlkit.vision.text.TextRecognizer
  ): String = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    val img = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
    recognizer.process(img)
      .addOnSuccessListener { cont.resume(it.text.trim()) }
      .addOnFailureListener { cont.resume("ERROR: ${it.message}") }
  }
}
