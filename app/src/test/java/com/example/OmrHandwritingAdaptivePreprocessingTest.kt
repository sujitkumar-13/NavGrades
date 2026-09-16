package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.example.omr.handwriting.OmrHandwritingConfig
import com.example.omr.handwriting.OmrHandwritingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OmrHandwritingAdaptivePreprocessingTest {

  // Standard OMR cell dimension from physical sheet captures (e.g. ~20x37)
  private val boxW = 20
  private val boxH = 36

  @Before
  fun setUp() {
    OmrHandwritingConfig.preprocessingMode = OmrHandwritingConfig.PreprocessingMode.ADAPTIVE_RULING_SUPPRESSION
  }

  private fun createBlankCell(): Bitmap {
    val bmp = Bitmap.createBitmap(boxW, boxH, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.WHITE)
    return bmp
  }

  @Test
  fun testCase1_HandwrittenStrokeNearLeftBoundaryPreserved() {
    // A stroke very close to the left border (x=1..2, y=8..26)
    // Fixed 4px insets would completely delete x in 0..3!
    val bmp = createBlankCell()
    for (y in 8..26) {
      bmp.setPixel(1, y, Color.rgb(30, 30, 30))
      bmp.setPixel(2, y, Color.rgb(40, 40, 40))
    }

    val result = OmrHandwritingEngine.adaptivePreprocessBoxCell(bmp, isDigitField = false)
    assertFalse("Left-boundary stroke must NOT be discarded as empty", result.isEmpty)
    assertNotNull("Tensor buffer must be produced", result.tensorBuffer)
    assertTrue("Bounding box width must include stroke (bboxW >= 2)", result.bboxW >= 2)
    assertTrue("Bounding box height must include stroke (bboxH >= 15)", result.bboxH >= 15)
  }

  @Test
  fun testCase2_HandwrittenStrokeNearRightBoundaryPreserved() {
    // A stroke very close to the right border (x = boxW-2..boxW-3, y=8..26)
    // Fixed 4px insets would completely delete x >= boxW-4!
    val bmp = createBlankCell()
    for (y in 8..26) {
      bmp.setPixel(boxW - 2, y, Color.rgb(30, 30, 30))
      bmp.setPixel(boxW - 3, y, Color.rgb(40, 40, 40))
    }

    val result = OmrHandwritingEngine.adaptivePreprocessBoxCell(bmp, isDigitField = false)
    assertFalse("Right-boundary stroke must NOT be discarded as empty", result.isEmpty)
    assertNotNull("Tensor buffer must be produced", result.tensorBuffer)
    assertTrue("Bounding box width must include stroke (bboxW >= 2)", result.bboxW >= 2)
    assertTrue("Bounding box height must include stroke (bboxH >= 15)", result.bboxH >= 15)
  }

  @Test
  fun testCase3_TopHorizontalStrokeOfTPreserved() {
    // 'T' character: horizontal bar at y=5..6, stem down at x=9..10, y=7..28
    // Fixed 5px vertical insets chopped off top of T!
    val bmp = createBlankCell()
    for (x in 3..16) {
      bmp.setPixel(x, 5, Color.rgb(20, 20, 20))
      bmp.setPixel(x, 6, Color.rgb(20, 20, 20))
    }
    for (y in 7..28) {
      bmp.setPixel(9, y, Color.rgb(20, 20, 20))
      bmp.setPixel(10, y, Color.rgb(20, 20, 20))
    }

    val result = OmrHandwritingEngine.adaptivePreprocessBoxCell(bmp, isDigitField = false)
    assertFalse("'T' must NOT be classified as empty", result.isEmpty)
    assertTrue("Top horizontal bar must be retained (bboxW >= 12)", result.bboxW >= 12)
    assertTrue("Full 'T' height must be retained (bboxH >= 20)", result.bboxH >= 20)
  }

  @Test
  fun testCase4_DiagonalLegOfRPreserved() {
    // Draw an 'R' shape with right leg extending towards bottom-right (x=boxW-2, y=boxH-4)
    val bmp = createBlankCell()
    // Left vertical spine
    for (y in 6..30) {
      bmp.setPixel(4, y, Color.rgb(20, 20, 20))
      bmp.setPixel(5, y, Color.rgb(20, 20, 20))
    }
    // Top loop
    for (x in 5..14) {
      bmp.setPixel(x, 6, Color.rgb(20, 20, 20))
      bmp.setPixel(x, 16, Color.rgb(20, 20, 20))
    }
    for (y in 6..16) {
      bmp.setPixel(14, y, Color.rgb(20, 20, 20))
    }
    // Diagonal leg reaching x=boxW-2 (x=18)
    for (i in 0..12) {
      val x = (10 + (i * 8 / 12)).coerceAtMost(boxW - 2)
      val y = (17 + i).coerceAtMost(boxH - 4)
      bmp.setPixel(x, y, Color.rgb(20, 20, 20))
    }

    val result = OmrHandwritingEngine.adaptivePreprocessBoxCell(bmp, isDigitField = false)
    assertFalse("'R' must NOT be empty", result.isEmpty)
    assertTrue("R leg must extend close to boundary (bboxW >= 12)", result.bboxW >= 12)
    assertTrue("R vertical span must be preserved (bboxH >= 22)", result.bboxH >= 22)
  }

  @Test
  fun testCase5_NarrowDigit1NearBorderPreserved() {
    // Narrow digit '1': width 2px (x=2..3), height 20px (y=8..28)
    val bmp = createBlankCell()
    for (y in 8..28) {
      bmp.setPixel(2, y, Color.rgb(25, 25, 25))
      bmp.setPixel(3, y, Color.rgb(25, 25, 25))
    }

    val result = OmrHandwritingEngine.adaptivePreprocessBoxCell(bmp, isDigitField = true)
    assertFalse("Narrow digit '1' must NOT be discarded as border noise", result.isEmpty)
    assertNotNull("Tensor buffer must be generated", result.tensorBuffer)
    assertTrue("Digit '1' height must be preserved (bboxH >= 18)", result.bboxH >= 18)
    assertTrue("Border contamination must be <= 5%", result.borderContamination <= 0.05f)
  }

  @Test
  fun testCase6_Digit7NearUpperRulingPreserved() {
    // Digit '7': horizontal bar at y=5..6, diagonal stroke descending to x=5, y=28
    val bmp = createBlankCell()
    for (x in 5..16) {
      bmp.setPixel(x, 5, Color.rgb(20, 20, 20))
      bmp.setPixel(x, 6, Color.rgb(20, 20, 20))
    }
    for (i in 0..22) {
      val x = (16 - (i * 11 / 22)).coerceIn(5, 16)
      val y = 6 + i
      bmp.setPixel(x, y, Color.rgb(20, 20, 20))
    }

    val result = OmrHandwritingEngine.adaptivePreprocessBoxCell(bmp, isDigitField = true)
    assertFalse("Digit '7' must NOT be empty", result.isEmpty)
    assertTrue("Digit '7' top width must be preserved (bboxW >= 10)", result.bboxW >= 10)
    assertTrue("Digit '7' vertical span must be preserved (bboxH >= 20)", result.bboxH >= 20)
  }

  @Test
  fun testCase7_EmptyBoxWithPrintedRulingLinesSuppressed() {
    // Empty box with dark printed ruling lines:
    // Full horizontal top ruling (y=0..1) across all columns
    // Full horizontal bottom ruling (y=boxH-2..boxH-1) across all columns
    // Full vertical left line (x=0)
    // Full vertical right line (x=boxW-1)
    val bmp = createBlankCell()

    // Horizontal top ruling
    for (x in 0 until boxW) {
      bmp.setPixel(x, 0, Color.rgb(10, 10, 10))
      bmp.setPixel(x, 1, Color.rgb(20, 20, 20))
    }
    // Horizontal bottom ruling
    for (x in 0 until boxW) {
      bmp.setPixel(x, boxH - 2, Color.rgb(20, 20, 20))
      bmp.setPixel(x, boxH - 1, Color.rgb(10, 10, 10))
    }
    // Vertical left & right rulings
    for (y in 0 until boxH) {
      bmp.setPixel(0, y, Color.rgb(15, 15, 15))
      bmp.setPixel(boxW - 1, y, Color.rgb(15, 15, 15))
    }

    val result = OmrHandwritingEngine.adaptivePreprocessBoxCell(bmp, isDigitField = false)
    assertTrue("Empty box with full perimeter ruling lines must be classified as EMPTY", result.isEmpty)
    assertEquals("Tensor buffer must be null for empty box", null, result.tensorBuffer)
  }

  @Test
  fun testCase8_NormalCenteredCharacterPreservedAndCentered() {
    // Centered 'O' shape (x=5..15, y=10..26)
    val bmp = createBlankCell()
    for (x in 5..15) {
      bmp.setPixel(x, 10, Color.rgb(20, 20, 20))
      bmp.setPixel(x, 11, Color.rgb(20, 20, 20))
      bmp.setPixel(x, 25, Color.rgb(20, 20, 20))
      bmp.setPixel(x, 26, Color.rgb(20, 20, 20))
    }
    for (y in 10..26) {
      bmp.setPixel(5, y, Color.rgb(20, 20, 20))
      bmp.setPixel(6, y, Color.rgb(20, 20, 20))
      bmp.setPixel(14, y, Color.rgb(20, 20, 20))
      bmp.setPixel(15, y, Color.rgb(20, 20, 20))
    }

    val result = OmrHandwritingEngine.adaptivePreprocessBoxCell(bmp, isDigitField = false)
    assertFalse("Centered character must NOT be empty", result.isEmpty)
    assertNotNull("Tensor buffer must be generated", result.tensorBuffer)
    assertEquals("Capacity must be 28x28x4 bytes", 4 * 28 * 28, result.tensorBuffer?.capacity())

    // Center of mass must be tightly centered within +/- 1.5px of 13.5
    assertTrue("COM X offset (${result.comOffsetX}) must be within 1.5px", abs(result.comOffsetX) <= 1.5f)
    assertTrue("COM Y offset (${result.comOffsetY}) must be within 1.5px", abs(result.comOffsetY) <= 1.5f)
    assertTrue("Border contamination must be <= 5%", result.borderContamination <= 0.05f)
  }
}
