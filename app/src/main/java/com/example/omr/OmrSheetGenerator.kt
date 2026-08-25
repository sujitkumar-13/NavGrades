package com.example.omr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream

object OmrSheetGenerator {

  /**
   * Generates a high resolution printable blank OMR Answer Sheet.
   */
  fun generateBlankSheetBitmap(
    quizName: String,
    date: String,
    numQuestions: Int,
    width: Int = 1200,
    height: Int = 1700
  ): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 1. Background
    canvas.drawColor(Color.WHITE)

    // Paints
    val markerPaint = Paint().apply {
      color = Color.BLACK
      style = Paint.Style.FILL
      isAntiAlias = true
    }

    val linePaint = Paint().apply {
      color = Color.rgb(30, 41, 59)
      strokeWidth = 3f
      style = Paint.Style.STROKE
      isAntiAlias = true
    }

    val thinLinePaint = Paint().apply {
      color = Color.rgb(203, 213, 225)
      strokeWidth = 1.5f
      style = Paint.Style.STROKE
      isAntiAlias = true
    }

    val headerTitlePaint = Paint().apply {
      color = Color.rgb(15, 23, 42)
      textSize = 34f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      isAntiAlias = true
    }

    val headerSubPaint = Paint().apply {
      color = Color.rgb(71, 85, 105)
      textSize = 20f
      isAntiAlias = true
    }

    val labelPaint = Paint().apply {
      color = Color.rgb(30, 41, 59)
      textSize = 18f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      isAntiAlias = true
    }

    val bubbleOutlinePaint = Paint().apply {
      color = Color.rgb(30, 41, 59)
      strokeWidth = 2.5f
      style = Paint.Style.STROKE
      isAntiAlias = true
    }

    val bubbleTextPaint = Paint().apply {
      color = Color.rgb(30, 41, 59)
      textSize = 17f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.CENTER
      isAntiAlias = true
    }

    val qNumPaint = Paint().apply {
      color = Color.rgb(15, 23, 42)
      textSize = 18f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.RIGHT
      isAntiAlias = true
    }

    // 2. Outer Border Frame
    canvas.drawRect(30f, 30f, width - 30f, height - 30f, linePaint)

    // 3. Four Corner Alignment Markers [■]
    val markerSizePx = OmrLayoutDefinition.CORNER_MARKER_SIZE * width
    val halfM = markerSizePx / 2f

    // TL
    canvas.drawRect(
      OmrLayoutDefinition.CORNER_TL_X * width - halfM,
      OmrLayoutDefinition.CORNER_TL_Y * height - halfM,
      OmrLayoutDefinition.CORNER_TL_X * width + halfM,
      OmrLayoutDefinition.CORNER_TL_Y * height + halfM,
      markerPaint
    )
    // TR
    canvas.drawRect(
      OmrLayoutDefinition.CORNER_TR_X * width - halfM,
      OmrLayoutDefinition.CORNER_TR_Y * height - halfM,
      OmrLayoutDefinition.CORNER_TR_X * width + halfM,
      OmrLayoutDefinition.CORNER_TR_Y * height + halfM,
      markerPaint
    )
    // BL
    canvas.drawRect(
      OmrLayoutDefinition.CORNER_BL_X * width - halfM,
      OmrLayoutDefinition.CORNER_BL_Y * height - halfM,
      OmrLayoutDefinition.CORNER_BL_X * width + halfM,
      OmrLayoutDefinition.CORNER_BL_Y * height + halfM,
      markerPaint
    )
    // BR
    canvas.drawRect(
      OmrLayoutDefinition.CORNER_BR_X * width - halfM,
      OmrLayoutDefinition.CORNER_BR_Y * height - halfM,
      OmrLayoutDefinition.CORNER_BR_X * width + halfM,
      OmrLayoutDefinition.CORNER_BR_Y * height + halfM,
      markerPaint
    )

    // 4. Header Details
    canvas.drawText("OMR ANSWER SHEET", 120f, 90f, headerTitlePaint)
    canvas.drawText("Quiz: $quizName   |   Date: $date   |   Questions: $numQuestions", 120f, 125f, headerSubPaint)

    // Header Divider Line
    canvas.drawLine(50f, 150f, width - 50f, 150f, linePaint)

    // 5. Student Details Section Box
    val studentBoxTop = 165f
    val studentBoxBottom = 590f
    canvas.drawRoundRect(RectF(60f, studentBoxTop, width - 60f, studentBoxBottom), 12f, 12f, thinLinePaint)

    // Left Column: Student Name & Reg Box
    canvas.drawText("STUDENT DETAILS", 85f, studentBoxTop + 35f, labelPaint)
    canvas.drawText("Student Name:", 85f, studentBoxTop + 80f, headerSubPaint)
    canvas.drawLine(220f, studentBoxTop + 85f, 550f, studentBoxTop + 85f, thinLinePaint)

    canvas.drawText("Student ID:", 85f, studentBoxTop + 140f, headerSubPaint)
    canvas.drawLine(220f, studentBoxTop + 145f, 550f, studentBoxTop + 145f, thinLinePaint)

    canvas.drawText("Instructions:", 85f, studentBoxTop + 220f, labelPaint)
    val instructText = listOf(
      "1. Use dark blue or black pen to fill circles.",
      "2. Fill the circle completely:  ●  (not ◐ or ✗).",
      "3. Darken one circle per question.",
      "4. Do not fold or crease this sheet."
    )
    instructText.forEachIndexed { idx, txt ->
      canvas.drawText(txt, 85f, studentBoxTop + 260f + (idx * 30f), headerSubPaint)
    }

    // Right Column: 5-digit Student ID Bubble Matrix
    canvas.drawText("ROLL / STUDENT ID", 620f, studentBoxTop + 35f, labelPaint)
    val idBubbleCoords = OmrLayoutDefinition.getStudentIdBubbleCoordinates()
    val idRadius = OmrLayoutDefinition.BUBBLE_RADIUS * width * 0.75f

    idBubbleCoords.forEach { idB ->
      val cx = idB.relX * width
      val cy = idB.relY * height
      canvas.drawCircle(cx, cy, idRadius, bubbleOutlinePaint)
      canvas.drawText(idB.digit.toString(), cx, cy + 6f, bubbleTextPaint)
    }

    // 6. Section Separator
    canvas.drawLine(50f, 615f, width - 50f, 615f, linePaint)
    canvas.drawText("ANSWER MATRIX (SELECT ONE OPTION PER QUESTION)", 80f, 642f, labelPaint)

    // 7. Question Bubbles
    val bubbleRadius = OmrLayoutDefinition.BUBBLE_RADIUS * width
    val questionCoords = OmrLayoutDefinition.getQuestionBubbleCoordinates(numQuestions)

    // Group by question
    val qGroup = questionCoords.groupBy { it.questionNumber }
    qGroup.forEach { (qNum, optList) ->
      val firstOpt = optList.first()
      val qY = firstOpt.relY * height
      val qX = firstOpt.relX * width - (bubbleRadius * 2.2f)

      // Question Number Label
      canvas.drawText("$qNum.", qX, qY + 6f, qNumPaint)

      // Option Bubbles
      optList.forEach { opt ->
        val cx = opt.relX * width
        val cy = opt.relY * height
        canvas.drawCircle(cx, cy, bubbleRadius, bubbleOutlinePaint)
        canvas.drawText(opt.option, cx, cy + 6f, bubbleTextPaint)
      }
    }

    return bitmap
  }

  /**
   * Generates a filled sample sheet (with realistic pencil/pen marks) for demonstration and instant camera testing.
   */
  fun generateFilledSampleSheetBitmap(
    quizName: String,
    date: String,
    numQuestions: Int,
    studentId: String = "NG12345",
    studentName: String = "Rahul Kumar",
    filledAnswers: Map<Int, String> // e.g. 1 -> "B", 2 -> "D", etc.
  ): Bitmap {
    val base = generateBlankSheetBitmap(quizName, date, numQuestions)
    val canvas = Canvas(base)

    val fillPaint = Paint().apply {
      color = Color.rgb(20, 24, 33) // Dark graphite / ink
      style = Paint.Style.FILL
      isAntiAlias = true
    }

    val nameTextPaint = Paint().apply {
      color = Color.rgb(15, 23, 42)
      textSize = 24f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      isAntiAlias = true
    }

    val width = base.width
    val height = base.height

    // Draw Student Name & ID in boxes
    canvas.drawText(studentName, 230f, 240f, nameTextPaint)
    canvas.drawText(studentId, 230f, 300f, nameTextPaint)

    // Fill Student ID bubbles (5 digits from ID e.g. "12345")
    val idDigits = studentId.filter { it.isDigit() }.takeLast(5).padStart(5, '0')
    val idBubbleCoords = OmrLayoutDefinition.getStudentIdBubbleCoordinates()
    val idRadius = OmrLayoutDefinition.BUBBLE_RADIUS * width * 0.72f

    idDigits.forEachIndexed { colIdx, digitChar ->
      val digitVal = digitChar.digitToIntOrNull() ?: 0
      val targetCoord = idBubbleCoords.find { it.column == colIdx && it.digit == digitVal }
      if (targetCoord != null) {
        val cx = targetCoord.relX * width
        val cy = targetCoord.relY * height
        canvas.drawCircle(cx, cy, idRadius, fillPaint)
      }
    }

    // Fill question answers
    val questionCoords = OmrLayoutDefinition.getQuestionBubbleCoordinates(numQuestions)
    val bubbleRadius = OmrLayoutDefinition.BUBBLE_RADIUS * width * 0.90f

    questionCoords.forEach { coord ->
      val targetChoice = filledAnswers[coord.questionNumber]
      if (targetChoice == coord.option) {
        val cx = coord.relX * width
        val cy = coord.relY * height
        canvas.drawCircle(cx, cy, bubbleRadius, fillPaint)
      } else if (targetChoice == "MULTIPLE") {
        // Fill two options (A and B)
        if (coord.option == "A" || coord.option == "B") {
          val cx = coord.relX * width
          val cy = coord.relY * height
          canvas.drawCircle(cx, cy, bubbleRadius, fillPaint)
        }
      }
    }

    return base
  }

  /**
   * Saves a bitmap to the application cache directory and returns the absolute file path.
   */
  fun saveBitmapToFile(context: Context, bitmap: Bitmap, fileName: String): String {
    val dir = File(context.cacheDir, "omr_sheets")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "$fileName.jpg")
    val out = FileOutputStream(file)
    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
    out.flush()
    out.close()
    return file.absolutePath
  }
}
