package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.omr.handwriting.HandwritingModelMode
import com.example.omr.handwriting.OmrHandwritingConfig
import com.example.omr.handwriting.OmrHandwritingEngine
import org.junit.After
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
class OmrHandwritingIntegrationTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext<Context>()
    OmrHandwritingConfig.currentMode = HandwritingModelMode.NEW_BASELINE_MODEL
    OmrHandwritingConfig.isDebugAuditEnabled = true
  }

  @After
  fun tearDown() {
    OmrHandwritingEngine.close()
    OmrHandwritingConfig.currentMode = HandwritingModelMode.NEW_BASELINE_MODEL
    OmrHandwritingConfig.isDebugAuditEnabled = false
  }

  private fun loadAssetBitmap(name: String): Bitmap {
    val inputStream: InputStream = context.assets.open("benchmark/$name")
    return BitmapFactory.decodeStream(inputStream)
  }

  @Test
  fun testFeatureFlagSwitchingBetweenOldAndNew() {
    // 1. Production default is NEW_BASELINE_MODEL
    assertEquals(HandwritingModelMode.NEW_BASELINE_MODEL, OmrHandwritingConfig.currentMode)

    // 2. Rollback switch to OLD_MODEL
    OmrHandwritingConfig.currentMode = HandwritingModelMode.OLD_MODEL
    assertEquals(HandwritingModelMode.OLD_MODEL, OmrHandwritingConfig.currentMode)

    // 3. Switch back to NEW_BASELINE_MODEL
    OmrHandwritingConfig.currentMode = HandwritingModelMode.NEW_BASELINE_MODEL
    assertEquals(HandwritingModelMode.NEW_BASELINE_MODEL, OmrHandwritingConfig.currentMode)
  }

  @Test
  fun testBoxSlicingGeometryOnW01Strips() {
    val fnBmp = loadAssetBitmap("bench_fn.png")
    val lnBmp = loadAssetBitmap("bench_ln.png")
    val phoneBmp = loadAssetBitmap("bench_phone.png")
    val waBmp = loadAssetBitmap("bench_wa.png")

    assertNotNull("First Name strip must load", fnBmp)
    assertNotNull("Last Name strip must load", lnBmp)
    assertNotNull("Phone strip must load", phoneBmp)
    assertNotNull("WhatsApp strip must load", waBmp)

    // 23 boxes for First Name and Last Name
    val fnBoxW = fnBmp.width.toFloat() / 23f
    val lnBoxW = lnBmp.width.toFloat() / 23f
    assertTrue("First Name box width must be ~19.6px", fnBoxW in 18.0f..22.0f)
    assertTrue("Last Name box width must be ~19.6px", lnBoxW in 18.0f..22.0f)

    // 10 boxes for Phone and WhatsApp
    val phoneBoxW = phoneBmp.width.toFloat() / 10f
    val waBoxW = waBmp.width.toFloat() / 10f
    assertTrue("Phone box width must be ~19.4px", phoneBoxW in 18.0f..22.0f)
    assertTrue("WhatsApp box width must be ~19.4px", waBoxW in 18.0f..22.0f)
  }

  @Test
  fun testProductionPreprocessingAndInputVerificationOnW01() {
    val fnBmp = loadAssetBitmap("bench_fn.png")
    val numBoxes = 23
    val boxW = fnBmp.width.toFloat() / numBoxes.toFloat()

    var emptyCount = 0
    var filledCount = 0

    // Boxes 0..4 contain handwritten 'S', 'U', 'J', 'I', 'T'
    // Boxes 5..22 are empty boxes
    for (i in 0 until numBoxes) {
      val left = (i * boxW).toInt().coerceIn(0, fnBmp.width - 1)
      val right = ((i + 1) * boxW).toInt().coerceIn(left + 1, fnBmp.width)
      val cellCrop = Bitmap.createBitmap(fnBmp, left, 0, right - left, fnBmp.height)

      val result = OmrHandwritingEngine.preprocessBoxCell(cellCrop, isDigitField = false)

      if (i < 5) {
        // Non-empty character cells: 'S', 'U', 'J', 'I', 'T'
        assertFalse("Box $i ('SUJIT'[$i]) must NOT be empty", result.isEmpty)
        assertNotNull("Box $i tensorBuffer must be generated", result.tensorBuffer)
        assertEquals("Buffer capacity must equal 28x28x4 bytes", 4 * 28 * 28, result.tensorBuffer?.capacity())

        assertTrue("Min pixel intensity must be >= 0.0", result.minVal >= 0.0f)
        assertTrue("Max pixel intensity must be <= 1.0", result.maxVal <= 1.0f)
        assertTrue("Foreground ratio must be between 0.01 and 0.40", result.fgPixelRatio in 0.01f..0.40f)
        assertTrue("Border contamination must be <= 5%", result.borderContamination <= 0.05f)
        assertTrue("Center-of-mass X offset must be < 2.0px from 13.5", Math.abs(result.comOffsetX) < 2.0f)
        assertTrue("Center-of-mass Y offset must be < 2.0px from 13.5", Math.abs(result.comOffsetY) < 2.0f)
        filledCount++
      } else {
        // Empty boxes: 5..22 (18 boxes)
        assertTrue("Box $i must be detected as empty by energy gating", result.isEmpty)
        assertTrue("Raw ink count in empty box $i must be < 6", result.rawInkCount < 6)
        emptyCount++
      }
    }

    assertEquals("Expected 5 filled letter boxes in 'SUJIT'", 5, filledCount)
    assertEquals("Expected 18 empty letter boxes in First Name", 18, emptyCount)
  }

  @Test
  fun testMiddleBoxSkipDetectionAndReconstructionLogic() {
    // Create a synthetic 7-box strip representing 'R', 'A', EMPTY, 'M', 'E', 'S', 'H'
    val boxW = 20
    val boxH = 36
    val numBoxes = 7
    val stripBmp = Bitmap.createBitmap(boxW * numBoxes, boxH, Bitmap.Config.ARGB_8888)
    stripBmp.eraseColor(Color.WHITE)

    // Fill characters into boxes 0, 1, 3, 4, 5, 6; leave box 2 completely EMPTY
    val filledBoxes = listOf(0, 1, 3, 4, 5, 6)
    for (b in filledBoxes) {
      val left = b * boxW
      for (y in 10..26) {
        stripBmp.setPixel(left + 8, y, Color.rgb(20, 20, 20))
        stripBmp.setPixel(left + 9, y, Color.rgb(20, 20, 20))
      }
    }

    val recognizedChars = mutableListOf<String>()
    var emptyBoxCount = 0

    for (i in 0 until numBoxes) {
      val left = i * boxW
      val cellCrop = Bitmap.createBitmap(stripBmp, left, 0, boxW, boxH)
      val result = OmrHandwritingEngine.preprocessBoxCell(cellCrop, isDigitField = false)

      if (result.isEmpty) {
        emptyBoxCount++
        // Crucial rule: empty cells are NOT appended to recognizedChars
      } else {
        // Simulated character prediction for non-empty cells
        val simulatedChar = when (i) {
          0 -> "R"
          1 -> "A"
          3 -> "M"
          4 -> "E"
          5 -> "S"
          6 -> "H"
          else -> "?"
        }
        recognizedChars.add(simulatedChar)
      }
    }

    val logicalField = recognizedChars.joinToString("")

    assertEquals("Middle box 2 must be detected as empty", 1, emptyBoxCount)
    assertEquals("RAMESH", logicalField)
    assertFalse("Logical field must NOT contain spaces", logicalField.contains(" "))
    assertEquals(6, recognizedChars.size)
  }
}
