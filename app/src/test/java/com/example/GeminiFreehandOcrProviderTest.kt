package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.omr.handwriting.FreeFieldType
import com.example.omr.handwriting.FreehandOcrProvider
import com.example.omr.handwriting.GeminiFreehandOcrProvider
import com.example.omr.handwriting.GeminiOcrResponse
import com.example.omr.handwriting.HybridHandwritingEngine
import com.example.omr.handwriting.OmrFieldCrops
import com.example.omr.handwriting.RecognitionResult
import com.example.omr.handwriting.RecognitionStatus
import com.example.omr.handwriting.SupabaseOcrClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiFreehandOcrProviderTest {

  private lateinit var context: Context
  private lateinit var cityBmp: Bitmap
  private lateinit var schoolBmp: Bitmap

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext<Context>()
    try {
      com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(context)
    } catch (_: Throwable) {}
    cityBmp = loadAssetBitmap("bench_city.png")
    schoolBmp = loadAssetBitmap("bench_school.png")
  }

  private fun loadAssetBitmap(name: String): Bitmap {
    val inputStream: InputStream = context.assets.open("benchmark/$name")
    return BitmapFactory.decodeStream(inputStream)
  }

  private fun createBlankBitmap(): Bitmap {
    val bmp = Bitmap.createBitmap(100, 40, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.WHITE)
    return bmp
  }

  @Test
  fun testGeminiProviderSuccessPreservesRawVisualTextAndAmpersand() = runBlocking {
    val fakeClient = object : SupabaseOcrClient() {
      override suspend fun recognizeFreehand(cityCrop: Bitmap?, schoolCrop: Bitmap?): GeminiOcrResponse {
        return GeminiOcrResponse(
          status = "SUCCESS",
          provider = "GEMINI",
          city = "Jashpur",
          school = "Vidya & Child",
          fallbackUsed = false,
          reviewRequired = false
        )
      }
    }

    val provider = GeminiFreehandOcrProvider(ocrClient = fakeClient)
    val (cityResult, schoolResult) = provider.recognizeFreehandFields(cityBmp, schoolBmp)

    // City assertion
    assertEquals("Jashpur", cityResult.text)
    assertEquals("GEMINI", cityResult.provider)
    assertFalse("fallbackUsed must be false", cityResult.fallbackUsed)
    assertFalse("reviewRequired must be false", cityResult.reviewRequired)
    assertEquals(RecognitionStatus.HIGH_CONFIDENCE, cityResult.status)

    // School assertion - preserves '&' exactly as returned by Gemini
    assertEquals("Vidya & Child", schoolResult.text)
    assertTrue("Must preserve ampersand symbol without converting to 4 or 7", schoolResult.text.contains("&"))
    assertEquals("GEMINI", schoolResult.provider)
    assertFalse("fallbackUsed must be false", schoolResult.fallbackUsed)
    assertFalse("reviewRequired must be false", schoolResult.reviewRequired)
    assertEquals(RecognitionStatus.HIGH_CONFIDENCE, schoolResult.status)
  }

  @Test
  fun testGeminiProviderFallbackOnEdgeFunctionError() = runBlocking {
    val fakeClient = object : SupabaseOcrClient() {
      override suspend fun recognizeFreehand(cityCrop: Bitmap?, schoolCrop: Bitmap?): GeminiOcrResponse {
        return GeminiOcrResponse(
          status = "ERROR",
          provider = "GEMINI",
          fallbackUsed = true,
          reviewRequired = true,
          message = "HTTP 500: Server configuration error"
        )
      }
    }

    val fakeFallback = object : FreehandOcrProvider {
      override suspend fun recognize(crop: Bitmap, fieldType: FreeFieldType): RecognitionResult {
        return if (fieldType == FreeFieldType.CITY) {
          RecognitionResult("Jashpux", 0.85f, RecognitionStatus.HIGH_CONFIDENCE)
        } else {
          RecognitionResult("Vidya 4 Child", 0.85f, RecognitionStatus.HIGH_CONFIDENCE)
        }
      }
    }

    val provider = GeminiFreehandOcrProvider(ocrClient = fakeClient, fallbackProvider = fakeFallback)
    val (cityResult, schoolResult) = provider.recognizeFreehandFields(cityBmp, schoolBmp)

    // Verify fallback values and flags
    assertEquals("Jashpux", cityResult.text)
    assertEquals("ML_KIT_FALLBACK", cityResult.provider)
    assertTrue("fallbackUsed must be true on Edge Function error", cityResult.fallbackUsed)
    assertTrue("reviewRequired must be true on Edge Function error", cityResult.reviewRequired)

    assertEquals("Vidya 4 Child", schoolResult.text)
    assertEquals("ML_KIT_FALLBACK", schoolResult.provider)
    assertTrue("fallbackUsed must be true on Edge Function error", schoolResult.fallbackUsed)
    assertTrue("reviewRequired must be true on Edge Function error", schoolResult.reviewRequired)
  }

  @Test
  fun testGeminiProviderFallbackOnUnauthenticatedUser() = runBlocking {
    // SupabaseOcrClient with null token returns auth error response
    val clientWithNoToken = SupabaseOcrClient(
      endpointUrl = "https://fake.supabase.co/functions/v1/gemini-ocr",
      anonKey = "fake-key",
      tokenProvider = { null }
    )

    val fakeFallback = object : FreehandOcrProvider {
      override suspend fun recognize(crop: Bitmap, fieldType: FreeFieldType): RecognitionResult {
        return RecognitionResult("LocalText", 0.80f, RecognitionStatus.HIGH_CONFIDENCE)
      }
    }

    val provider = GeminiFreehandOcrProvider(ocrClient = clientWithNoToken, fallbackProvider = fakeFallback)
    val (cityResult, schoolResult) = provider.recognizeFreehandFields(cityBmp, schoolBmp)

    assertEquals("LocalText", cityResult.text)
    assertEquals("ML_KIT_FALLBACK", cityResult.provider)
    assertTrue("Must flag fallback when user JWT is missing", cityResult.fallbackUsed)
    assertTrue("Must require review when user JWT is missing", cityResult.reviewRequired)
  }

  @Test
  fun testGeminiProviderFallbackOnTimeoutOrExceptionNeverCrashes() = runBlocking {
    val throwingClient = object : SupabaseOcrClient() {
      override suspend fun recognizeFreehand(cityCrop: Bitmap?, schoolCrop: Bitmap?): GeminiOcrResponse {
        throw java.net.SocketTimeoutException("Connection timed out after 5000ms")
      }
    }

    val fakeFallback = object : FreehandOcrProvider {
      override suspend fun recognize(crop: Bitmap, fieldType: FreeFieldType): RecognitionResult {
        return RecognitionResult("RecoveredFromTimeout", 0.70f, RecognitionStatus.HIGH_CONFIDENCE)
      }
    }

    val provider = GeminiFreehandOcrProvider(ocrClient = throwingClient, fallbackProvider = fakeFallback)
    
    // Crucial requirement: never crash or throw exception to caller
    val (cityResult, schoolResult) = provider.recognizeFreehandFields(cityBmp, schoolBmp)

    assertNotNull(cityResult)
    assertEquals("RecoveredFromTimeout", cityResult.text)
    assertEquals("ML_KIT_FALLBACK", cityResult.provider)
    assertTrue(cityResult.fallbackUsed)
    assertTrue(cityResult.reviewRequired)
  }

  @Test
  fun testEmptyFieldsBypassNetworkCalls() = runBlocking {
    var networkCalled = false
    val spyClient = object : SupabaseOcrClient() {
      override suspend fun recognizeFreehand(cityCrop: Bitmap?, schoolCrop: Bitmap?): GeminiOcrResponse {
        networkCalled = true
        return GeminiOcrResponse(status = "SUCCESS", city = "ShouldNotBeCalled", school = "ShouldNotBeCalled")
      }
    }

    val blankCity = createBlankBitmap()
    val blankSchool = createBlankBitmap()

    val provider = GeminiFreehandOcrProvider(ocrClient = spyClient)
    val (cityResult, schoolResult) = provider.recognizeFreehandFields(blankCity, blankSchool)

    assertFalse("Network should not be called when both fields are blank", networkCalled)
    assertEquals("", cityResult.text)
    assertEquals(RecognitionStatus.EMPTY, cityResult.status)
    assertEquals("", schoolResult.text)
    assertEquals(RecognitionStatus.EMPTY, schoolResult.status)
  }

  @Test
  fun testHybridEngineAggregatesProviderMetadata() = runBlocking {
    val fakeProvider = object : FreehandOcrProvider {
      override suspend fun recognize(crop: Bitmap, fieldType: FreeFieldType): RecognitionResult {
        return if (fieldType == FreeFieldType.CITY) {
          RecognitionResult("Jashpur", 0.95f, RecognitionStatus.HIGH_CONFIDENCE, provider = "GEMINI")
        } else {
          RecognitionResult("Vidya & Child", 0.95f, RecognitionStatus.HIGH_CONFIDENCE, provider = "GEMINI")
        }
      }

      override suspend fun recognizeFreehandFields(
        cityCrop: Bitmap,
        schoolCrop: Bitmap
      ): Pair<RecognitionResult, RecognitionResult> {
        return Pair(
          RecognitionResult("Jashpur", 0.95f, RecognitionStatus.HIGH_CONFIDENCE, provider = "GEMINI", fallbackUsed = false, reviewRequired = false),
          RecognitionResult("Vidya & Child", 0.95f, RecognitionStatus.HIGH_CONFIDENCE, provider = "GEMINI", fallbackUsed = false, reviewRequired = false)
        )
      }
    }

    val engine = HybridHandwritingEngine(freehandProvider = fakeProvider)

    // Build synthetic rectified sheet
    val syntheticSheet = Bitmap.createBitmap(682, 1024, Bitmap.Config.ARGB_8888)
    syntheticSheet.eraseColor(Color.WHITE)
    val crops = OmrFieldCrops.fromRectifiedSheet(syntheticSheet)

    val result = engine.recognizeStudentInfo(crops)

    assertEquals("Jashpur", result.city)
    assertEquals("Vidya & Child", result.school)
    assertEquals("GEMINI", result.provider)
    assertFalse("fallbackUsed should be false when provider succeeds", result.fallbackUsed)
    assertFalse("reviewRequired should be false when provider succeeds", result.reviewRequired)
  }
}
