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
  const val SET_A_BUBBLE_X = 0.2801f
  const val SET_A_BUBBLE_Y = 0.5254f
  const val SET_B_BUBBLE_X = 0.4311f
  const val SET_B_BUBBLE_Y = 0.5254f

  // Caste Selection Circles: ST, SC, OBC, General, Other
  val CASTE_CIRCLES = listOf(
    Pair(0.2067f, 0.3516f), // ST
    Pair(0.3563f, 0.3516f), // SC
    Pair(0.5015f, 0.3516f), // OBC
    Pair(0.6584f, 0.3516f), // General
    Pair(0.8284f, 0.3516f)  // Other
  )
  val CASTE_LABELS = listOf("ST", "SC", "OBC", "General", "Other")

  // Gender Selection Circles: Female, Male, Other
  val GENDER_CIRCLES = listOf(
    Pair(0.2522f, 0.3906f), // Female
    Pair(0.4384f, 0.3906f), // Male
    Pair(0.6056f, 0.3906f)  // Other
  )
  val GENDER_LABELS = listOf("Female", "Male", "Other")

  // Current Qualification Circles: 12th, Pursuing College, Graduated
  val QUALIFICATION_CIRCLES = listOf(
    Pair(0.3460f, 0.4355f), // 12th
    Pair(0.5293f, 0.4355f), // Pursuing College
    Pair(0.7859f, 0.4365f)  // Graduated
  )
  val QUALIFICATION_LABELS = listOf("12th", "Pursuing College", "Graduated")

  // Box count constants
  const val NAME_BOX_COUNT  = 23
  const val PHONE_BOX_COUNT = 10

  // Targeted OCR Crop Regions (relative rect coordinates: left, top, right, bottom)
  val COURSE_CODE_REGION = RectF(0.380f, 0.060f, 0.620f, 0.120f)
  val FIRST_NAME_REGION  = RectF(0.055f, 0.150f, 0.960f, 0.186f)
  val LAST_NAME_REGION   = RectF(0.055f, 0.188f, 0.960f, 0.224f)
  val PHONE_REGION       = RectF(0.055f, 0.225f, 0.600f, 0.262f)
  val WHATSAPP_REGION    = RectF(0.055f, 0.262f, 0.600f, 0.298f)
  val CITY_REGION        = RectF(0.055f, 0.295f, 0.950f, 0.335f)
  val SCHOOL_REGION      = RectF(0.055f, 0.455f, 0.720f, 0.495f)

  // Calibrated Physical Box Grid Regions on rectified 682x1024 sheet (excluding printed field labels on left)
  // Measured directly against physical sheets (W01, W02):
  // First Name: x in 152..655 (w=503, 23 boxes, ~21.87px/box), y in 156..186 (h=30)
  val FIRST_NAME_BOXES_REGION = RectF(0.222874f, 0.152344f, 0.960410f, 0.181641f)
  // Last Name: x in 152..655 (w=503, 23 boxes, ~21.87px/box), y in 193..223 (h=30)
  val LAST_NAME_BOXES_REGION  = RectF(0.222874f, 0.188477f, 0.960410f, 0.217773f)
  // Phone: x in 175..380 (w=205, 10 boxes, ~20.50px/box), y in 231..261 (h=30)
  val PHONE_BOXES_REGION      = RectF(0.256598f, 0.225586f, 0.557185f, 0.254883f)
  // WhatsApp: x in 175..380 (w=205, 10 boxes, ~20.50px/box), y in 268..298 (h=30)
  val WHATSAPP_BOXES_REGION   = RectF(0.256598f, 0.261719f, 0.557185f, 0.291016f)

  /**
   * Generates relative coordinates for question bubbles matching the standard OMR template.
   * Q1–Q8 on the left column, Q9–Q16 on the right column.
   */
  fun getQuestionBubbleCoordinates(numQuestions: Int = 16): List<BubbleCoordinate> {
    val bubbles = mutableListOf<BubbleCoordinate>()
    val leftOptX = listOf("A" to 0.2067f, "B" to 0.2962f, "C" to 0.3724f, "D" to 0.4457f)
    val rightOptX = listOf("A" to 0.6686f, "B" to 0.7478f, "C" to 0.8284f, "D" to 0.9090f)

    val startY = 0.6064f
    val endY = 0.8174f
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
