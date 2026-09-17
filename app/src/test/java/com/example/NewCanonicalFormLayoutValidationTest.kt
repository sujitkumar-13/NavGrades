package com.example

import com.example.omr.OmrLayoutDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Validation tests for the NEW CANONICAL OMR FORM layout coordinates and geometry.
 * Verifies that all newly measured fiducials, boxed regions, bubbles, and freehand fields
 * strictly adhere to the calibrated physical form.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewCanonicalFormLayoutValidationTest {

  @Test
  fun testFourCornerMarkersGeometry() {
    // Top-Left and Top-Right
    assertEquals(0.0535f, OmrLayoutDefinition.CORNER_TL_X, 0.001f)
    assertEquals(0.0391f, OmrLayoutDefinition.CORNER_TL_Y, 0.001f)
    assertEquals(0.9465f, OmrLayoutDefinition.CORNER_TR_X, 0.001f)
    assertEquals(0.0391f, OmrLayoutDefinition.CORNER_TR_Y, 0.001f)

    // Bottom-Left and Bottom-Right
    assertEquals(0.0535f, OmrLayoutDefinition.CORNER_BL_X, 0.001f)
    assertEquals(0.9678f, OmrLayoutDefinition.CORNER_BL_Y, 0.001f)
    assertEquals(0.9465f, OmrLayoutDefinition.CORNER_BR_X, 0.001f)
    assertEquals(0.9678f, OmrLayoutDefinition.CORNER_BR_Y, 0.001f)

    // Symmetric alignment
    assertEquals(1.0f, OmrLayoutDefinition.CORNER_TL_X + OmrLayoutDefinition.CORNER_TR_X, 0.001f)
    assertEquals(1.0f, OmrLayoutDefinition.CORNER_BL_X + OmrLayoutDefinition.CORNER_BR_X, 0.001f)
    assertEquals(OmrLayoutDefinition.CORNER_TL_Y, OmrLayoutDefinition.CORNER_TR_Y, 0.0001f)
    assertEquals(OmrLayoutDefinition.CORNER_BL_Y, OmrLayoutDefinition.CORNER_BR_Y, 0.0001f)

    // Standard canvas proportions
    assertEquals(682, OmrLayoutDefinition.STANDARD_WIDTH)
    assertEquals(1024, OmrLayoutDefinition.STANDARD_HEIGHT)
    assertEquals(1.501f, OmrLayoutDefinition.SHEET_ASPECT_RATIO, 0.005f)
  }

  @Test
  fun testSetBubbleCoordinates() {
    // Set A and Set B on the new canonical form
    assertEquals(0.2977f, OmrLayoutDefinition.SET_A_BUBBLE_X, 0.001f)
    assertEquals(0.5137f, OmrLayoutDefinition.SET_A_BUBBLE_Y, 0.001f)

    assertEquals(0.4516f, OmrLayoutDefinition.SET_B_BUBBLE_X, 0.001f)
    assertEquals(0.5137f, OmrLayoutDefinition.SET_B_BUBBLE_Y, 0.001f)

    // Set bubbles share the same horizontal baseline
    assertEquals(OmrLayoutDefinition.SET_A_BUBBLE_Y, OmrLayoutDefinition.SET_B_BUBBLE_Y, 0.0001f)
    assertTrue("Set A must be to the left of Set B", OmrLayoutDefinition.SET_A_BUBBLE_X < OmrLayoutDefinition.SET_B_BUBBLE_X)
  }

  @Test
  fun testCasteCirclesCoordinates() {
    val labels = OmrLayoutDefinition.CASTE_LABELS
    val circles = OmrLayoutDefinition.CASTE_CIRCLES

    assertEquals(5, labels.size)
    assertEquals(5, circles.size)
    assertEquals(listOf("ST", "SC", "OBC", "General", "Other"), labels)

    val expectedX = listOf(0.2639f, 0.3959f, 0.5381f, 0.6818f, 0.8387f)
    val expectedY = 0.3408f

    for (i in circles.indices) {
      val (x, y) = circles[i]
      assertEquals("Caste circle ${labels[i]} X mismatch", expectedX[i], x, 0.001f)
      assertEquals("Caste circle ${labels[i]} Y mismatch", expectedY, y, 0.001f)
      if (i > 0) {
        assertTrue("Caste circles must strictly increase in X", circles[i].first > circles[i - 1].first)
      }
    }
  }

  @Test
  fun testGenderCirclesCoordinates() {
    val labels = OmrLayoutDefinition.GENDER_LABELS
    val circles = OmrLayoutDefinition.GENDER_CIRCLES

    assertEquals(3, labels.size)
    assertEquals(3, circles.size)
    assertEquals(listOf("Female", "Male", "Other"), labels)

    val expectedX = listOf(0.2639f, 0.4560f, 0.6276f)
    val expectedY = 0.3779f

    for (i in circles.indices) {
      val (x, y) = circles[i]
      assertEquals("Gender circle ${labels[i]} X mismatch", expectedX[i], x, 0.001f)
      assertEquals("Gender circle ${labels[i]} Y mismatch", expectedY, y, 0.001f)
      if (i > 0) {
        assertTrue("Gender circles must strictly increase in X", circles[i].first > circles[i - 1].first)
      }
    }
  }

  @Test
  fun testQualificationCirclesCoordinates() {
    val labels = OmrLayoutDefinition.QUALIFICATION_LABELS
    val circles = OmrLayoutDefinition.QUALIFICATION_CIRCLES

    assertEquals(3, labels.size)
    assertEquals(3, circles.size)
    assertEquals(listOf("12th", "Pursuing College", "Graduated"), labels)

    val expectedX = listOf(0.3372f, 0.5425f, 0.7991f)
    val expectedY = 0.4219f

    for (i in circles.indices) {
      val (x, y) = circles[i]
      assertEquals("Qualification circle ${labels[i]} X mismatch", expectedX[i], x, 0.001f)
      assertEquals("Qualification circle ${labels[i]} Y mismatch", expectedY, y, 0.001f)
      if (i > 0) {
        assertTrue("Qualification circles must strictly increase in X", circles[i].first > circles[i - 1].first)
      }
    }
  }

  @Test
  fun testQuestionGridCoordinates() {
    val bubbles = OmrLayoutDefinition.getQuestionBubbleCoordinates(16)
    assertEquals("Must have 16 questions * 4 options = 64 bubbles", 64, bubbles.size)

    val q1Bubbles = bubbles.filter { it.questionNumber == 1 }
    assertEquals(4, q1Bubbles.size)
    assertEquals(0.2185f, q1Bubbles.first { it.option == "A" }.relX, 0.001f)
    assertEquals(0.2970f, q1Bubbles.first { it.option == "B" }.relX, 0.001f)
    assertEquals(0.3755f, q1Bubbles.first { it.option == "C" }.relX, 0.001f)
    assertEquals(0.4540f, q1Bubbles.first { it.option == "D" }.relX, 0.001f)
    assertEquals(0.5889f, q1Bubbles.first().relY, 0.001f)

    val q8Bubbles = bubbles.filter { it.questionNumber == 8 }
    assertEquals(0.7930f, q8Bubbles.first().relY, 0.001f)

    val q9Bubbles = bubbles.filter { it.questionNumber == 9 }
    assertEquals(4, q9Bubbles.size)
    assertEquals(0.6657f, q9Bubbles.first { it.option == "A" }.relX, 0.001f)
    assertEquals(0.7434f, q9Bubbles.first { it.option == "B" }.relX, 0.001f)
    assertEquals(0.8211f, q9Bubbles.first { it.option == "C" }.relX, 0.001f)
    assertEquals(0.8988f, q9Bubbles.first { it.option == "D" }.relX, 0.001f)
    assertEquals(0.5889f, q9Bubbles.first().relY, 0.001f)

    val q16Bubbles = bubbles.filter { it.questionNumber == 16 }
    assertEquals(0.7930f, q16Bubbles.first().relY, 0.001f)
  }

  @Test
  fun testBoxedAndFreehandCropRegionsIntegrity() {
    // 1. Boxed Regions
    val fnBoxes = OmrLayoutDefinition.FIRST_NAME_BOXES_REGION
    val lnBoxes = OmrLayoutDefinition.LAST_NAME_BOXES_REGION
    val phoneBoxes = OmrLayoutDefinition.PHONE_BOXES_REGION
    val waBoxes = OmrLayoutDefinition.WHATSAPP_BOXES_REGION

    assertEquals(23, OmrLayoutDefinition.NAME_BOX_COUNT)
    assertEquals(10, OmrLayoutDefinition.PHONE_BOX_COUNT)

    assertEquals(0.256598f, fnBoxes.left, 0.001f)
    assertEquals(0.945748f, fnBoxes.right, 0.001f)
    assertEquals(0.139648f, fnBoxes.top, 0.001f)
    assertEquals(0.164063f, fnBoxes.bottom, 0.001f)

    assertEquals(0.256598f, lnBoxes.left, 0.001f)
    assertEquals(0.945748f, lnBoxes.right, 0.001f)
    assertEquals(0.175781f, lnBoxes.top, 0.001f)
    assertEquals(0.201172f, lnBoxes.bottom, 0.001f)

    assertEquals(0.278592f, phoneBoxes.left, 0.001f)
    assertEquals(0.589443f, phoneBoxes.right, 0.001f)
    assertEquals(0.215820f, phoneBoxes.top, 0.001f)
    assertEquals(0.240234f, phoneBoxes.bottom, 0.001f)

    assertEquals(0.278592f, waBoxes.left, 0.001f)
    assertEquals(0.589443f, waBoxes.right, 0.001f)
    assertEquals(0.252930f, waBoxes.top, 0.001f)
    assertEquals(0.278320f, waBoxes.bottom, 0.001f)

    // Vertical ordering strictly preserved without overlapping
    assertTrue("FN bottom < LN top", fnBoxes.bottom < lnBoxes.top)
    assertTrue("LN bottom < Phone top", lnBoxes.bottom < phoneBoxes.top)
    assertTrue("Phone bottom < WhatsApp top", phoneBoxes.bottom < waBoxes.top)

    // 2. Freehand Regions
    val city = OmrLayoutDefinition.CITY_REGION
    val school = OmrLayoutDefinition.SCHOOL_REGION

    assertEquals(0.281f, city.top, 0.002f)
    assertEquals(0.318f, city.bottom, 0.002f)
    assertTrue("City must sit below WhatsApp and above Caste", city.top > waBoxes.bottom && city.bottom < 0.3408f)

    assertEquals(0.432f, school.top, 0.002f)
    assertEquals(0.466f, school.bottom, 0.002f)
    assertTrue("School must sit below Qualification and above Set", school.top > 0.4219f && school.bottom < 0.5137f)
  }
}
