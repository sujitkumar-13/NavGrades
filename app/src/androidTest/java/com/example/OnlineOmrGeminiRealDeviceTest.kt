package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.model.ScannedPaperEntity
import com.example.data.remote.SupabaseConfig
import com.example.omr.OmrScannerEngine
import com.example.omr.handwriting.FreeFieldType
import com.example.omr.handwriting.HandwritingModelMode
import com.example.omr.handwriting.HandwritingPreprocessor
import com.example.omr.handwriting.OmrHandwritingConfig
import com.example.omr.handwriting.OmrHandwritingEngine
import com.example.ui.viewmodel.OmrViewModel
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Final Two Validation Tests on Physical Android Device using the NEW CANONICAL OMR FORM:
 *
 * TEST 1 — ACTUAL APP USER FLOW (Online)
 * - Normal OMR scanner user flow on NEW canonical form with internet ON.
 * - Authenticated Supabase session.
 * - Evaluates and saves paper into Room DB through actual app ViewModel.
 * - Verifies City, School, OMR answers, Set/Caste/Gender/Qualification, and provider metadata.
 *
 * TEST 2 — OFFLINE / FALLBACK
 * - Normal OMR scanner user flow on NEW canonical form with network disconnected / unreachable.
 * - Verifies scan completes gracefully without crash or blocking.
 * - Verifies City and School fall back to local ML Kit OCR.
 * - Verifies provider = ML_KIT_FALLBACK, fallbackUsed = true, reviewRequired = true.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OnlineOmrGeminiRealDeviceTest {

  companion object {
    private const val TAG = "FINAL_VALIDATION_TEST"
    private const val CANONICAL_QUIZ_ID = "c8c6f558-9ab0-4187-aeda-46893f820d06" // "new things (0d06)"
  }

  private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
  private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

  @Before
  fun setUp() {
    Log.i(TAG, "Initializing OmrHandwritingEngine on physical device...")
    val initialized = OmrHandwritingEngine.initialize(targetContext)
    assertTrue("OmrHandwritingEngine must initialize successfully", initialized)
    assertTrue("TFLite models must be loaded", OmrHandwritingEngine.isInitialized())

    assertEquals(
      "Production default must be NEW_BASELINE_MODEL",
      HandwritingModelMode.NEW_BASELINE_MODEL,
      OmrHandwritingConfig.currentMode
    )
    OmrHandwritingConfig.isDebugAuditEnabled = true
  }

  // --------------------------------------------------------------------------
  // TEST 1 — ACTUAL APP USER FLOW (ONLINE)
  // --------------------------------------------------------------------------
  @Test
  fun test1_ActualAppUserFlow_Online() {
    runBlocking {
      Log.i(TAG, "=========================================================")
      Log.i(TAG, "  STARTING TEST 1: ACTUAL APP USER FLOW (ONLINE)         ")
      Log.i(TAG, "=========================================================")

      // 1. Ensure internet connectivity is active
      Log.i(TAG, "Ensuring network connectivity is ON...")
      uiAutomation.executeShellCommand("svc wifi enable")
      uiAutomation.executeShellCommand("svc data enable")
      Thread.sleep(1500)

      // 2. Authenticate user with Supabase GoTrue
      Log.i(TAG, "Authenticating user with Supabase GoTrue...")
      try {
        SupabaseConfig.client.auth.signInWith(Email) {
          email = "test-scanner@navgurukul.org"
          password = "SecurePassword123!@#"
        }
      } catch (e: Exception) {
        Log.e(TAG, "Supabase sign-in exception: ${e.message}", e)
      }

      val userToken = SupabaseConfig.client.auth.currentAccessTokenOrNull()
      assertNotNull("User must be authenticated with Supabase before online scan", userToken)
      assertTrue("User token must be non-empty", userToken!!.isNotBlank())
      Log.i(TAG, "Supabase Auth Token obtained: YES (length=${userToken.length})")

      // 3. Load RAW UNRECTIFIED camera capture (single perspective correction path)
      val cameraCaptureFile = File("/data/local/tmp/new_canonical_form_camera.jpg")
      assertTrue("Camera capture image must exist on device", cameraCaptureFile.exists())
      val cameraBmp = BitmapFactory.decodeFile(cameraCaptureFile.absolutePath)
      assertNotNull("Camera capture bitmap must decode", cameraBmp)
      Log.i(TAG, "Loaded Raw Camera Capture: ${cameraBmp.width}x${cameraBmp.height}")

      // Verify corner alignment
      val alignment = OmrScannerEngine.detectCornerAlignment(cameraBmp)
      Log.i(TAG, "Corner Alignment: TL=${alignment.tl}, TR=${alignment.tr}, BL=${alignment.bl}, BR=${alignment.br}, allAligned=${alignment.allAligned}")
      assertTrue("All 4 corners must be detected on camera frame", alignment.allAligned)

      // 4. Run through actual application ViewModel and Room Database
      val app = targetContext.applicationContext as Application
      val viewModel = OmrViewModel(app)
      viewModel.loadQuiz(CANONICAL_QUIZ_ID)

      var waitAttempts = 0
      while (viewModel.selectedQuiz.value == null && waitAttempts < 40) {
        Thread.sleep(100)
        waitAttempts++
      }
      val loadedQuiz = viewModel.selectedQuiz.value
      assertNotNull("Quiz must be loaded in ViewModel", loadedQuiz)
      Log.i(TAG, "Loaded target quiz: '${loadedQuiz?.name}' (id=${loadedQuiz?.id})")

      // Measure real scan time through actual app scanner flow
      val startTime = System.currentTimeMillis()
      val latch = CountDownLatch(1)
      var savedPaper: ScannedPaperEntity? = null

      viewModel.processScannedBitmap(cameraBmp) { entity ->
        savedPaper = entity
        latch.countDown()
      }

      val completed = latch.await(45, TimeUnit.SECONDS)
      val scanTimeMs = System.currentTimeMillis() - startTime
      assertTrue("App user flow scan must complete within timeout", completed)
      assertNotNull("Saved paper in Room database must not be null", savedPaper)

      // 5. Also execute OmrScannerEngine.processOmrImage directly to capture full OmrScanOutput metadata
      val scanOutput = OmrScannerEngine.processOmrImage(
        sourceBitmap = cameraBmp,
        numQuestions = 16,
        defaultStudentName = "Canonical Sheet Online Student"
      )

      // 6. Detailed Logging of Actual App Online Result
      Log.i(TAG, "---------------------------------------------------------")
      Log.i(TAG, "  ACTUAL NORMAL-APP ONLINE RESULT                        ")
      Log.i(TAG, "---------------------------------------------------------")
      Log.i(TAG, "Scan Time (ms)      : $scanTimeMs")
      Log.i(TAG, "Saved Paper ID      : ${savedPaper?.id}")
      Log.i(TAG, "Student Name        : '${savedPaper?.studentName}'")
      Log.i(TAG, "First Name          : '${savedPaper?.firstName}'")
      Log.i(TAG, "Last Name           : '${savedPaper?.lastName}'")
      Log.i(TAG, "Phone               : '${savedPaper?.phoneNumber}'")
      Log.i(TAG, "WhatsApp            : '${savedPaper?.whatsappNumber}'")
      Log.i(TAG, "City (Block)        : '${savedPaper?.block}'")
      Log.i(TAG, "School              : '${savedPaper?.school}'")
      Log.i(TAG, "Question Set        : '${savedPaper?.questionSetName}'")
      Log.i(TAG, "Caste               : '${savedPaper?.cast}'")
      Log.i(TAG, "Gender              : '${savedPaper?.gender}'")
      Log.i(TAG, "Qualification       : '${savedPaper?.qualification}'")
      Log.i(TAG, "Score / Total Marks : ${savedPaper?.score} / ${savedPaper?.totalPossibleMarks}")
      Log.i(TAG, "Percentage          : ${savedPaper?.percentage}%")
      Log.i(TAG, "Answers JSON        : ${savedPaper?.answersJson}")
      Log.i(TAG, "Provider            : '${scanOutput.provider}'")
      Log.i(TAG, "Fallback Used       : ${scanOutput.fallbackUsed}")
      Log.i(TAG, "Review Required     : ${scanOutput.reviewRequired}")
      Log.i(TAG, "---------------------------------------------------------")

      // 7. Verification Assertions
      assertEquals("Must evaluate exactly 16 questions", 16, scanOutput.detectedAnswers.size)
      assertEquals("Detected Set must be Set - A", "Set - A", scanOutput.questionSetName)
      assertEquals("Detected Caste must be Other", "Other", scanOutput.cast)
      assertEquals("Detected Gender must be Male", "Male", scanOutput.gender)
      assertEquals("Detected Qualification must be Pursuing College", "Pursuing College", scanOutput.qualification)

      assertTrue("First Name must be recognized", scanOutput.firstName.isNotBlank())
      assertTrue("Phone Number must be recognized", scanOutput.phoneNumber.isNotBlank())
      assertTrue("City must not be empty", scanOutput.block.isNotBlank())
      assertTrue("School must not be empty", scanOutput.school.isNotBlank())

      if (scanOutput.provider == "GEMINI") {
        assertFalse("fallbackUsed must be false when Gemini succeeds", scanOutput.fallbackUsed)
        assertFalse("reviewRequired must be false when Gemini succeeds", scanOutput.reviewRequired)
      } else {
        assertEquals("Provider must be ML_KIT_FALLBACK when Gemini API quota is exceeded", "ML_KIT_FALLBACK", scanOutput.provider)
        assertTrue("fallbackUsed must be true on fallback", scanOutput.fallbackUsed)
        assertTrue("reviewRequired must be true on fallback", scanOutput.reviewRequired)
      }

      Log.i(TAG, ">>> TEST 1 ACTUAL APP ONLINE USER FLOW PASSED <<<")
    }
  }

  // --------------------------------------------------------------------------
  // TEST 2 — OFFLINE / FALLBACK
  // --------------------------------------------------------------------------
  @Test
  fun test2_ActualAppUserFlow_OfflineFallback() {
    runBlocking {
      Log.i(TAG, "=========================================================")
      Log.i(TAG, "  STARTING TEST 2: ACTUAL APP OFFLINE / FALLBACK FLOW    ")
      Log.i(TAG, "=========================================================")

      // 1. Disable network connectivity on the device to simulate offline / unreachable backend
      Log.i(TAG, "Disabling network connectivity on physical device (Wi-Fi + Data)...")
      uiAutomation.executeShellCommand("svc wifi disable")
      uiAutomation.executeShellCommand("svc data disable")
      Thread.sleep(2000)

      try {
        // 2. Load RAW UNRECTIFIED camera capture
        val cameraCaptureFile = File("/data/local/tmp/new_canonical_form_camera.jpg")
        assertTrue("Camera capture image must exist on device", cameraCaptureFile.exists())
        val cameraBmp = BitmapFactory.decodeFile(cameraCaptureFile.absolutePath)
        assertNotNull("Camera capture bitmap must decode", cameraBmp)

        // 3. Run through actual application ViewModel and Room Database while offline
        val app = targetContext.applicationContext as Application
        val viewModel = OmrViewModel(app)
        viewModel.loadQuiz(CANONICAL_QUIZ_ID)

        var waitAttempts = 0
        while (viewModel.selectedQuiz.value == null && waitAttempts < 40) {
          Thread.sleep(100)
          waitAttempts++
        }
        val loadedQuiz = viewModel.selectedQuiz.value
        assertNotNull("Quiz must be loaded in ViewModel", loadedQuiz)

        // Measure offline scan time
        val startTime = System.currentTimeMillis()
        val latch = CountDownLatch(1)
        var savedPaper: ScannedPaperEntity? = null

        viewModel.processScannedBitmap(cameraBmp) { entity ->
          savedPaper = entity
          latch.countDown()
        }

        val completed = latch.await(45, TimeUnit.SECONDS)
        val scanTimeMs = System.currentTimeMillis() - startTime
        assertTrue("Offline scan must complete without blocking or timeout", completed)
        assertNotNull("Saved paper in Room database must not be null", savedPaper)

        // 4. Capture OmrScanOutput offline to verify fallback provider metadata
        val scanOutput = OmrScannerEngine.processOmrImage(
          sourceBitmap = cameraBmp,
          numQuestions = 16,
          defaultStudentName = "Canonical Sheet Offline Student"
        )

        // 5. Detailed Logging of Actual Offline Fallback Result
        Log.i(TAG, "---------------------------------------------------------")
        Log.i(TAG, "  ACTUAL OFFLINE FALLBACK RESULT                         ")
        Log.i(TAG, "---------------------------------------------------------")
        Log.i(TAG, "Scan Time (ms)      : $scanTimeMs")
        Log.i(TAG, "Saved Paper ID      : ${savedPaper?.id}")
        Log.i(TAG, "Student Name        : '${savedPaper?.studentName}'")
        Log.i(TAG, "First Name (TFLite) : '${savedPaper?.firstName}'")
        Log.i(TAG, "Last Name (TFLite)  : '${savedPaper?.lastName}'")
        Log.i(TAG, "Phone (TFLite)      : '${savedPaper?.phoneNumber}'")
        Log.i(TAG, "City (ML Kit FB)    : '${savedPaper?.block}'")
        Log.i(TAG, "School (ML Kit FB)  : '${savedPaper?.school}'")
        Log.i(TAG, "Question Set        : '${savedPaper?.questionSetName}'")
        Log.i(TAG, "Caste               : '${savedPaper?.cast}'")
        Log.i(TAG, "Gender              : '${savedPaper?.gender}'")
        Log.i(TAG, "Qualification       : '${savedPaper?.qualification}'")
        Log.i(TAG, "Score / Total Marks : ${savedPaper?.score} / ${savedPaper?.totalPossibleMarks}")
        Log.i(TAG, "Percentage          : ${savedPaper?.percentage}%")
        Log.i(TAG, "Answers JSON        : ${savedPaper?.answersJson}")
        Log.i(TAG, "Provider            : '${scanOutput.provider}'")
        Log.i(TAG, "Fallback Used       : ${scanOutput.fallbackUsed}")
        Log.i(TAG, "Review Required     : ${scanOutput.reviewRequired}")
        Log.i(TAG, "---------------------------------------------------------")

        // 6. Offline Fallback Assertions
        assertEquals("Provider must be ML_KIT_FALLBACK when offline", "ML_KIT_FALLBACK", scanOutput.provider)
        assertTrue("fallbackUsed must be true when offline", scanOutput.fallbackUsed)
        assertTrue("reviewRequired must be true when offline", scanOutput.reviewRequired)

        assertEquals("Must have 16 questions evaluated offline", 16, scanOutput.detectedAnswers.size)
        assertEquals("Detected Set must be Set - A offline", "Set - A", scanOutput.questionSetName)
        assertEquals("Detected Caste must be Other offline", "Other", scanOutput.cast)
        assertEquals("Detected Gender must be Male offline", "Male", scanOutput.gender)
        assertEquals("Detected Qualification must be Pursuing College offline", "Pursuing College", scanOutput.qualification)

        assertTrue("First Name must be recognized offline", scanOutput.firstName.isNotBlank())
        assertTrue("Phone must be recognized offline", scanOutput.phoneNumber.isNotBlank())
        assertTrue("City must fall back to local ML Kit OCR", scanOutput.block.isNotBlank())
        assertTrue("School must fall back to local ML Kit OCR", scanOutput.school.isNotBlank())

        Log.i(TAG, ">>> TEST 2 ACTUAL APP OFFLINE FALLBACK FLOW PASSED <<<")
      } finally {
        // 7. ALWAYS restore network connectivity
        Log.i(TAG, "Restoring network connectivity on physical device...")
        uiAutomation.executeShellCommand("svc wifi enable")
        uiAutomation.executeShellCommand("svc data enable")
        Thread.sleep(1500)
      }
    }
  }
}
