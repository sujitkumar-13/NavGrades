package com.example.omr

import android.graphics.RectF

data class BubbleCoordinate(
  val questionNumber: Int,
  val option: String, // "A", "B", "C", "D"
  val relX: Float, // 0.0 .. 1.0 (relative to sheet width)
  val relY: Float  // 0.0 .. 1.0 (relative to sheet height)
)

data class IdDigitBubble(
  val column: Int,
  val digit: Int,
  val relX: Float,
  val relY: Float
)

object OmrLayoutDefinition {
  // Standard Sheet relative proportions (Width: 682, Height: 1024 -> ~1:1.501 aspect ratio)
  const val SHEET_ASPECT_RATIO = 1.501f
  const val STANDARD_WIDTH = 682
  const val STANDARD_HEIGHT = 1024

  // Relative positions of corner alignment markers (relative center)
  const val CORNER_TL_X = 0.0535f
  const val CORNER_TL_Y = 0.0391f
  const val CORNER_TR_X = 0.9465f
  const val CORNER_TR_Y = 0.0391f
  const val CORNER_BL_X = 0.0535f
  const val CORNER_BL_Y = 0.9678f
  const val CORNER_BR_X = 0.9465f
  const val CORNER_BR_Y = 0.9678f
  const val CORNER_MARKER_SIZE = 0.045f // Size of black marker square

  // Bubble radius relative to width (~10px on 682 width)
  const val BUBBLE_RADIUS = 0.015f

  // Set Section Coordinates (Set A and Set B bubbles)
  const val SET_A_BUBBLE_X = 0.2977f
  const val SET_A_BUBBLE_Y = 0.5137f
  const val SET_B_BUBBLE_X = 0.4516f
  const val SET_B_BUBBLE_Y = 0.5137f

  // Caste Selection Circles: ST, SC, OBC, General, Other
  val CASTE_CIRCLES = listOf(
    Pair(0.2639f, 0.3408f), // ST
    Pair(0.3959f, 0.3408f), // SC
    Pair(0.5381f, 0.3408f), // OBC
    Pair(0.6818f, 0.3408f), // General
    Pair(0.8387f, 0.3408f)  // Other
  )
  val CASTE_LABELS = listOf("ST", "SC", "OBC", "General", "Other")

  // Gender Selection Circles: Female, Male, Other
  val GENDER_CIRCLES = listOf(
    Pair(0.2639f, 0.3779f), // Female
    Pair(0.4560f, 0.3779f), // Male
    Pair(0.6276f, 0.3779f)  // Other
  )
  val GENDER_LABELS = listOf("Female", "Male", "Other")

  // Current Qualification Circles: 12th, Pursuing College, Graduated
  val QUALIFICATION_CIRCLES = listOf(
    Pair(0.3372f, 0.4219f), // 12th
    Pair(0.5425f, 0.4219f), // Pursuing College
    Pair(0.7991f, 0.4219f)  // Graduated
  )
  val QUALIFICATION_LABELS = listOf("12th", "Pursuing College", "Graduated")

  // Box count constants
  const val NAME_BOX_COUNT  = 23
  const val PHONE_BOX_COUNT = 10

  // Targeted OCR Crop Regions (relative rect coordinates: left, top, right, bottom)
  @Deprecated("Obsolete on new canonical form. Header contains static 'SOB' text with no student input field.")
  val COURSE_CODE_REGION = RectF(0.380f, 0.060f, 0.620f, 0.120f)
  val FIRST_NAME_REGION  = RectF(0.050f, 0.135f, 0.950f, 0.170f)
  val LAST_NAME_REGION   = RectF(0.050f, 0.172f, 0.950f, 0.208f)
  val PHONE_REGION       = RectF(0.050f, 0.212f, 0.600f, 0.248f)
  val WHATSAPP_REGION    = RectF(0.050f, 0.250f, 0.600f, 0.288f)
  val CITY_REGION        = RectF(0.050f, 0.281f, 0.950f, 0.318f)
  val SCHOOL_REGION      = RectF(0.050f, 0.432f, 0.950f, 0.466f)

  // Calibrated Physical Box Grid Regions on rectified 682x1024 sheet (excluding printed field labels on left)
  // Measured directly against physical new canonical form:
  // First Name: x in 175..645 (w=470, 23 boxes, ~20.43px/box), y in 143..168 (h=25)
  val FIRST_NAME_BOXES_REGION = RectF(0.256598f, 0.139648f, 0.945748f, 0.164063f)
  // Last Name: x in 175..645 (w=470, 23 boxes, ~20.43px/box), y in 180..206 (h=26)
  val LAST_NAME_BOXES_REGION  = RectF(0.256598f, 0.175781f, 0.945748f, 0.201172f)
  // Phone: x in 190..402 (w=212, 10 boxes, ~21.20px/box), y in 221..246 (h=25)
  val PHONE_BOXES_REGION      = RectF(0.278592f, 0.215820f, 0.589443f, 0.240234f)
  // WhatsApp: x in 190..402 (w=212, 10 boxes, ~21.20px/box), y in 259..285 (h=26)
  val WHATSAPP_BOXES_REGION   = RectF(0.278592f, 0.252930f, 0.589443f, 0.278320f)

  /**
   * Generates relative coordinates for question bubbles matching the standard OMR template.
   * Q1–Q8 on the left column, Q9–Q16 on the right column.
   */
  fun getQuestionBubbleCoordinates(numQuestions: Int = 16): List<BubbleCoordinate> {
    val bubbles = mutableListOf<BubbleCoordinate>()
    val leftOptX = listOf("A" to 0.2185f, "B" to 0.2970f, "C" to 0.3755f, "D" to 0.4540f)
    val rightOptX = listOf("A" to 0.6657f, "B" to 0.7434f, "C" to 0.8211f, "D" to 0.8988f)

    val startY = 0.5889f
    val endY = 0.7930f
    val rowCount = 8
    val stepY = (endY - startY) / (rowCount - 1).coerceAtLeast(1)

    for (q in 1..numQuestions) {
      if (q <= 8) {
        val rowIdx = q - 1
        val qY = startY + (rowIdx * stepY)
        leftOptX.forEach { (opt, optX) ->
          bubbles.add(BubbleCoordinate(q, opt, optX, qY))
        }
      } else if (q <= 16) {
        val rowIdx = q - 9
        val qY = startY + (rowIdx * stepY)
        rightOptX.forEach { (opt, optX) ->
          bubbles.add(BubbleCoordinate(q, opt, optX, qY))
        }
      }
    }
    return bubbles
  }

  /**
   * Maintained for compatibility with template generation.
   */
  fun getStudentIdBubbleCoordinates(): List<IdDigitBubble> {
    return emptyList()
  }
}
