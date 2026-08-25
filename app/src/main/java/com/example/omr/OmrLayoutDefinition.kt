package com.example.omr

data class BubbleCoordinate(
  val questionNumber: Int,
  val option: String, // "A", "B", "C", "D"
  val relX: Float, // 0.0 .. 1.0 (relative to sheet width)
  val relY: Float  // 0.0 .. 1.0 (relative to sheet height)
)

data class IdDigitBubble(
  val column: Int, // 0..4 (5 digits)
  val digit: Int,  // 0..9
  val relX: Float,
  val relY: Float
)

object OmrLayoutDefinition {
  // Standard Sheet relative proportions (Width: 1000, Height: 1414 -> A4 aspect ratio 1:1.414)
  const val SHEET_ASPECT_RATIO = 1.414f

  // Relative positions of corner alignment markers (relative center)
  const val CORNER_TL_X = 0.06f
  const val CORNER_TL_Y = 0.04f
  const val CORNER_TR_X = 0.94f
  const val CORNER_TR_Y = 0.04f
  const val CORNER_BL_X = 0.06f
  const val CORNER_BL_Y = 0.96f
  const val CORNER_BR_X = 0.94f
  const val CORNER_BR_Y = 0.96f
  const val CORNER_MARKER_SIZE = 0.045f // Size of black marker square

  // Bubble radius relative to width
  const val BUBBLE_RADIUS = 0.018f

  /**
   * Generates relative coordinates for all question bubbles based on total question count.
   */
  fun getQuestionBubbleCoordinates(numQuestions: Int): List<BubbleCoordinate> {
    val bubbles = mutableListOf<BubbleCoordinate>()
    val numColumns = when {
      numQuestions <= 20 -> 2
      numQuestions <= 40 -> 2
      numQuestions <= 60 -> 3
      else -> 4
    }

    val questionsPerCol = (numQuestions + numColumns - 1) / numColumns
    val colWidth = 0.84f / numColumns
    val startX = 0.08f
    val startY = 0.38f // Question grid starts below student info area
    val endY = 0.92f
    val rowHeight = (endY - startY) / questionsPerCol.coerceAtLeast(1)

    for (q in 1..numQuestions) {
      val colIdx = (q - 1) / questionsPerCol
      val rowIdx = (q - 1) % questionsPerCol

      val colCenterX = startX + (colIdx * colWidth)
      val qY = startY + (rowIdx * rowHeight) + (rowHeight * 0.5f)

      // 4 options: A, B, C, D
      val options = listOf("A", "B", "C", "D")
      val optSpacing = colWidth * 0.17f
      val optStartX = colCenterX + (colWidth * 0.28f)

      options.forEachIndexed { optIdx, optStr ->
        val bubbleX = optStartX + (optIdx * optSpacing)
        bubbles.add(BubbleCoordinate(q, optStr, bubbleX, qY))
      }
    }
    return bubbles
  }

  /**
   * Generates coordinates for 5-digit Student ID grid (digits 0..9)
   */
  fun getStudentIdBubbleCoordinates(): List<IdDigitBubble> {
    val bubbles = mutableListOf<IdDigitBubble>()
    val startX = 0.52f
    val startY = 0.16f
    val colSpacing = 0.065f
    val rowSpacing = 0.018f

    for (col in 0..4) {
      for (digit in 0..9) {
        val bX = startX + (col * colSpacing)
        val bY = startY + (digit * rowSpacing)
        bubbles.add(IdDigitBubble(col, digit, bX, bY))
      }
    }
    return bubbles
  }
}
