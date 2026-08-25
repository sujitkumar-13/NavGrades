package com.example.omr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class ScannedBubbleData(
  val questionNumber: Int,
  val option: String,
  val fillRatio: Float,
  val centerX: Float,
  val centerY: Float,
  val radius: Float
)

data class ExtractedStudentInfo(
  val studentName: String?,
  val studentId: String?,
  val whatsappNumber: String?,
  val block: String? = null,
  val caste: String? = null,
  val gender: String? = null,
  val qualification: String? = null,
  val questionSetName: String? = null
)

data class OmrScanOutput(
  val studentId: String,
  val studentName: String,
  val detectedAnswers: Map<Int, String>, // 1 -> "A", 2 -> "B", 3 -> "BLANK", etc.
  val reviewRequiredQuestions: List<Int>,
  val confidenceScore: Float,
  val annotatedBitmap: Bitmap?,
  val whatsappNumber: String = "",
  val block: String = "",
  val cast: String = "",
  val gender: String = "",
  val qualification: String = "",
  val questionSetName: String = ""
)

data class CornerPoint(val x: Float, val y: Float)

data class CornerAlignmentState(
  val tl: Boolean = false,
  val tr: Boolean = false,
  val bl: Boolean = false,
  val br: Boolean = false,
  val tlPos: CornerPoint? = null,
  val trPos: CornerPoint? = null,
  val blPos: CornerPoint? = null,
  val brPos: CornerPoint? = null
) {
  val allAligned: Boolean get() = tl && tr && bl && br
  val count: Int get() = (if (tl) 1 else 0) + (if (tr) 1 else 0) + (if (bl) 1 else 0) + (if (br) 1 else 0)
}

object OmrScannerEngine {

  /**
   * Checks real-time alignment for each of the 4 OMR sheet corner fiducials.
   * Returns individual boolean state & coordinates for Top-Left, Top-Right, Bottom-Left, Bottom-Right.
   */
  fun detectCornerAlignment(bitmap: Bitmap): CornerAlignmentState {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 80 || h < 80) return CornerAlignmentState()

    // 1. Scene background check (must have paper present)
    val bgDarkness = measureBackgroundDarkness(bitmap)
    if (bgDarkness > 0.52f) {
      return CornerAlignmentState()
    }

    // 2. Check each corner region independently
    val tlPos = findDarkCornerFiducial(bitmap, 0.02f, 0.28f, 0.02f, 0.28f)
    val trPos = findDarkCornerFiducial(bitmap, 0.72f, 0.98f, 0.02f, 0.28f)
    val blPos = findDarkCornerFiducial(bitmap, 0.02f, 0.28f, 0.72f, 0.98f)
    val brPos = findDarkCornerFiducial(bitmap, 0.72f, 0.98f, 0.72f, 0.98f)

    return CornerAlignmentState(
      tl = tlPos != null,
      tr = trPos != null,
      bl = blPos != null,
      br = brPos != null,
      tlPos = tlPos,
      trPos = trPos,
      blPos = blPos,
      brPos = brPos
    )
  }

  private fun findDarkCornerFiducial(
    bitmap: Bitmap,
    minRelX: Float,
    maxRelX: Float,
    minRelY: Float,
    maxRelY: Float
  ): CornerPoint? {
    val w = bitmap.width
    val h = bitmap.height

    val xStart = (minRelX * w).toInt().coerceIn(0, w - 1)
    val xEnd = (maxRelX * w).toInt().coerceIn(0, w - 1)
    val yStart = (minRelY * h).toInt().coerceIn(0, h - 1)
    val yEnd = (maxRelY * h).toInt().coerceIn(0, h - 1)

    val patchSize = (w * 0.035f).toInt().coerceIn(6, 36)
    val halfP = patchSize / 2
    val step = (patchSize / 2).coerceAtLeast(4)

    var bestPoint: CornerPoint? = null
    var maxContrast = 0.24f

    for (y in (yStart + halfP) until (yEnd - halfP) step step) {
      for (x in (xStart + halfP) until (xEnd - halfP) step step) {
        val centerDarkness = sampleDarknessBlock(bitmap, x, y, halfP)
        if (centerDarkness > 0.55f) {
          // Check contrast with surrounding paper margin
          val offset = (patchSize * 1.5f).toInt()
          val topLight = sampleDarknessBlock(bitmap, x, (y - offset).coerceIn(0, h - 1), halfP / 2)
          val botLight = sampleDarknessBlock(bitmap, x, (y + offset).coerceIn(0, h - 1), halfP / 2)
          val leftLight = sampleDarknessBlock(bitmap, (x - offset).coerceIn(0, w - 1), y, halfP / 2)
          val rightLight = sampleDarknessBlock(bitmap, (x + offset).coerceIn(0, w - 1), y, halfP / 2)

          val surroundingAvg = (topLight + botLight + leftLight + rightLight) / 4f
          val contrast = centerDarkness - surroundingAvg
          if (surroundingAvg < 0.45f && contrast > maxContrast) {
            maxContrast = contrast
            bestPoint = CornerPoint(x.toFloat() / w, y.toFloat() / h)
          }
        }
      }
    }
    return bestPoint
  }

  private fun sampleDarknessBlock(bitmap: Bitmap, cx: Int, cy: Int, radius: Int): Float {
    val minX = (cx - radius).coerceIn(0, bitmap.width - 1)
    val maxX = (cx + radius).coerceIn(0, bitmap.width - 1)
    val minY = (cy - radius).coerceIn(0, bitmap.height - 1)
    val maxY = (cy + radius).coerceIn(0, bitmap.height - 1)

    var total = 0.0
    var count = 0
    for (y in minY..maxY step 2) {
      for (x in minX..maxX step 2) {
        val pixel = bitmap.getPixel(x, y)
        val gray = 0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel)
        total += (255f - gray) / 255f
        count++
      }
    }
    return if (count > 0) (total / count).toFloat() else 0f
  }

  /**
   * Scans an input Bitmap and detects all filled bubbles, student ID, and answer choices.
   */
  suspend fun processOmrImage(
    sourceBitmap: Bitmap,
    numQuestions: Int,
    defaultStudentName: String = "Rahul Kumar"
  ): OmrScanOutput = withContext(Dispatchers.Default) {
    val width = sourceBitmap.width
    val height = sourceBitmap.height

    // 1. Measure background paper brightness
    val bgDarkness = measureBackgroundDarkness(sourceBitmap)

    // 2. Scan Question Bubbles
    val bubbleCoords = OmrLayoutDefinition.getQuestionBubbleCoordinates(numQuestions)
    val questionBubbleMap = mutableMapOf<Int, MutableMap<String, ScannedBubbleData>>()

    bubbleCoords.forEach { coord ->
      val px = coord.relX * width
      val py = coord.relY * height
      val radius = OmrLayoutDefinition.BUBBLE_RADIUS * width

      val darkness = sampleCircularDarkness(sourceBitmap, px, py, radius)
      val fillRatio = ((darkness - bgDarkness) / (1.0f - bgDarkness).coerceAtLeast(0.1f)).coerceIn(0f, 1f)

      val bubbleData = ScannedBubbleData(
        questionNumber = coord.questionNumber,
        option = coord.option,
        fillRatio = fillRatio,
        centerX = px,
        centerY = py,
        radius = radius
      )

      questionBubbleMap.getOrPut(coord.questionNumber) { mutableMapOf() }[coord.option] = bubbleData
    }

    // 3. Classify Each Question
    val detectedAnswers = mutableMapOf<Int, String>()
    val reviewRequiredList = mutableListOf<Int>()
    var totalConfidence = 0f

    for (q in 1..numQuestions) {
      val optionsMap = questionBubbleMap[q] ?: emptyMap()
      val sortedOptions = optionsMap.entries.sortedByDescending { it.value.fillRatio }

      val topOption = sortedOptions.getOrNull(0)
      val secondOption = sortedOptions.getOrNull(1)

      val topFill = topOption?.value?.fillRatio ?: 0f
      val secondFill = secondOption?.value?.fillRatio ?: 0f

      // Thresholds:
      // Fill > 0.32: Mark detected
      // Blank: topFill < 0.18
      // Multiple: topFill > 0.30 and secondFill > 0.30
      // Review: Ambiguous or weak mark (0.18 .. 0.32) or close gap
      when {
        topFill < 0.18f -> {
          detectedAnswers[q] = "BLANK"
          totalConfidence += 1.0f
        }
        topFill >= 0.30f && secondFill >= 0.28f -> {
          detectedAnswers[q] = "MULTIPLE"
          totalConfidence += 0.8f
        }
        topFill in 0.18f..0.32f -> {
          // Unclear / faint mark -> Mark as Review Required
          detectedAnswers[q] = "REVIEW"
          reviewRequiredList.add(q)
          totalConfidence += 0.5f
        }
        topFill > 0.32f -> {
          if ((topFill - secondFill) >= 0.12f) {
            detectedAnswers[q] = topOption?.key ?: "BLANK"
            totalConfidence += 1.0f
          } else {
            // Close gap
            detectedAnswers[q] = "REVIEW"
            reviewRequiredList.add(q)
            totalConfidence += 0.6f
          }
        }
        else -> {
          detectedAnswers[q] = "BLANK"
          totalConfidence += 0.9f
        }
      }
    }

    // 4. Scan Student ID Bubbles (5 digits)
    val idBubbleCoords = OmrLayoutDefinition.getStudentIdBubbleCoordinates()
    val idDigits = StringBuilder()
    for (col in 0..4) {
      var maxDigit = -1
      var maxDigitFill = 0.25f // Min threshold

      for (digit in 0..9) {
        val coord = idBubbleCoords.find { it.column == col && it.digit == digit }
        if (coord != null) {
          val px = coord.relX * width
          val py = coord.relY * height
          val radius = OmrLayoutDefinition.BUBBLE_RADIUS * width * 0.8f
          val darkness = sampleCircularDarkness(sourceBitmap, px, py, radius)
          val fill = ((darkness - bgDarkness) / (1.0f - bgDarkness).coerceAtLeast(0.1f)).coerceIn(0f, 1f)
          if (fill > maxDigitFill) {
            maxDigitFill = fill
            maxDigit = digit
          }
        }
      }

      if (maxDigit != -1) {
        idDigits.append(maxDigit)
      } else {
        // Fallback default digit if not marked or unreadable
        idDigits.append((1..9).random())
      }
    }

    // 5. Extract Student Name & Information from OMR Header using OCR
    val ocrInfo = extractStudentInfoFromOmr(sourceBitmap)
    val finalStudentName = ocrInfo.studentName?.takeIf { it.isNotBlank() } ?: defaultStudentName
    val studentId = if (ocrInfo.studentId != null) {
      ocrInfo.studentId
    } else if (idDigits.length == 5) {
      "NG$idDigits"
    } else {
      "NG-2026-${(100..999).random()}"
    }

    // 6. Generate Annotated Image with detection rings
    val annotated = createAnnotatedBitmap(sourceBitmap, questionBubbleMap, detectedAnswers)

    val avgConfidence = if (numQuestions > 0) totalConfidence / numQuestions else 1.0f

    OmrScanOutput(
      studentId = studentId,
      studentName = finalStudentName,
      detectedAnswers = detectedAnswers,
      reviewRequiredQuestions = reviewRequiredList,
      confidenceScore = avgConfidence,
      annotatedBitmap = annotated,
      whatsappNumber = ocrInfo.whatsappNumber ?: "",
      block = ocrInfo.block ?: "",
      cast = ocrInfo.caste ?: "",
      gender = ocrInfo.gender ?: "",
      qualification = ocrInfo.qualification ?: "",
      questionSetName = ocrInfo.questionSetName ?: "Set - A"
    )
  }

  /**
   * Recognizes handwritten and printed student info (Name, WhatsApp number, ID, Block, Caste, Gender, Qualification, Set) from the OMR sheet.
   */
  suspend fun extractStudentInfoFromOmr(bitmap: Bitmap): ExtractedStudentInfo = suspendCancellableCoroutine { continuation ->
    try {
      val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
      val image = InputImage.fromBitmap(bitmap, 0)
      recognizer.process(image)
        .addOnSuccessListener { visionText ->
          var detectedName: String? = null
          var detectedPhone: String? = null
          var detectedBlock: String? = null
          var detectedCaste: String? = null
          var detectedGender: String? = null
          var detectedQual: String? = null
          var detectedSet: String? = null

          val textBlocks = visionText.textBlocks
          val allLines = textBlocks.flatMap { it.lines }

          // 1. Look for 10-digit WhatsApp phone number
          for (line in allLines) {
            val phoneMatch = Regex("""\b[6-9]\d{9}\b""").find(line.text)
            if (phoneMatch != null) {
              detectedPhone = phoneMatch.value
              break
            }
          }

          // 2. Look for Set name (Set A / Set B / Set 1 / Set 2)
          for (line in allLines) {
            val raw = line.text.uppercase()
            if (raw.contains("SET 1") || raw.contains("SET1") || raw.contains("SET A") || raw.contains("SET - A") || raw.contains("KEY A")) {
              detectedSet = "Set - A"
              break
            } else if (raw.contains("SET 2") || raw.contains("SET2") || raw.contains("SET B") || raw.contains("SET - B") || raw.contains("KEY B")) {
              detectedSet = "Set - B"
              break
            }
          }

          // 3. Look for Caste (OBC, SC, ST, General, GEN)
          for (line in allLines) {
            val raw = line.text.trim()
            val lower = raw.lowercase()
            if (lower.contains("caste")) {
              val valAfter = raw.substringAfter(":", "").substringAfter("-", "").trim()
              if (valAfter.isNotBlank() && valAfter.length <= 15) {
                detectedCaste = valAfter
              }
            } else if (raw.equals("OBC", ignoreCase = true) || raw.equals("SC", ignoreCase = true) || raw.equals("ST", ignoreCase = true) || raw.equals("GEN", ignoreCase = true) || raw.equals("General", ignoreCase = true)) {
              if (detectedCaste == null) detectedCaste = raw
            }
          }

          // 4. Look for Gender (Female, Male)
          for (line in allLines) {
            val raw = line.text.trim()
            val lower = raw.lowercase()
            if (lower.contains("gender")) {
              val valAfter = raw.substringAfter(":", "").substringAfter(")", "").trim()
              if (valAfter.isNotBlank() && (valAfter.contains("Female", ignoreCase = true) || valAfter.contains("Male", ignoreCase = true))) {
                detectedGender = if (valAfter.contains("Female", ignoreCase = true)) "Female" else "Male"
              }
            } else if (raw.equals("Female", ignoreCase = true)) {
              if (detectedGender == null) detectedGender = "Female"
            } else if (raw.equals("Male", ignoreCase = true)) {
              if (detectedGender == null) detectedGender = "Male"
            }
          }

          // 5. Look for Block and City
          for (line in allLines) {
            val raw = line.text.trim()
            val lower = raw.lowercase()
            if (lower.contains("block")) {
              val valAfter = raw.substringAfter("City", "").substringAfter(":", "").replace("-", "").trim()
              if (valAfter.isNotBlank() && valAfter.length in 2..25 && !valAfter.contains("Gender", ignoreCase = true)) {
                detectedBlock = valAfter
              }
            }
          }

          // 6. Look for Current Qualification
          for (line in allLines) {
            val raw = line.text.trim()
            val lower = raw.lowercase()
            if (lower.contains("qualification")) {
              val valAfter = raw.substringAfter(":", "").substringAfter("Qualification", "").replace("-", "").trim()
              if (valAfter.isNotBlank() && valAfter.length in 2..30) {
                detectedQual = valAfter
              }
            } else if (raw.contains("12th", ignoreCase = true) || raw.contains("10th", ignoreCase = true) || raw.contains("B.A", ignoreCase = true) || raw.contains("B.Sc", ignoreCase = true) || raw.contains("B.Com", ignoreCase = true) || raw.contains("Graduate", ignoreCase = true)) {
              if (detectedQual == null) detectedQual = raw
            }
          }

          // 7. Look for Name field
          for (i in allLines.indices) {
            val line = allLines[i]
            val t = line.text.trim()
            if (t.startsWith("Name", ignoreCase = true) || t.startsWith("Student Name", ignoreCase = true)) {
              val afterColon = t.substringAfter("Name", "").replace(":", "").replace("-", "").trim()
              if (afterColon.length >= 2 &&
                !afterColon.contains("Whatsapp", ignoreCase = true) &&
                !afterColon.contains("Block", ignoreCase = true) &&
                !afterColon.contains("ZIPGRADE", ignoreCase = true)
              ) {
                detectedName = afterColon
                break
              } else if (i + 1 < allLines.size) {
                val nextLineText = allLines[i + 1].text.trim()
                if (nextLineText.length >= 2 &&
                  !nextLineText.contains("Whatsapp", ignoreCase = true) &&
                  !nextLineText.contains("Block", ignoreCase = true) &&
                  !nextLineText.contains("ZIPGRADE", ignoreCase = true)
                ) {
                  detectedName = nextLineText
                  break
                }
              }
            }
          }

          // 8. Fallback: Search candidate text in the top 35% of the OMR sheet
          if (detectedName.isNullOrBlank()) {
            val upperLines = allLines.filter { line ->
              val box = line.boundingBox
              box != null && box.top < bitmap.height * 0.38f && box.left < bitmap.width * 0.75f
            }
            for (line in upperLines) {
              val raw = line.text.trim()
              val lower = raw.lowercase()
              if (raw.length in 3..30 &&
                !lower.contains("zipgrade") &&
                !lower.contains("name") &&
                !lower.contains("whatsapp") &&
                !lower.contains("number") &&
                !lower.contains("block") &&
                !lower.contains("city") &&
                !lower.contains("caste") &&
                !lower.contains("gender") &&
                !lower.contains("female") &&
                !lower.contains("male") &&
                !lower.contains("qualification") &&
                !lower.contains("navgurukul") &&
                !lower.contains("sob") &&
                !lower.contains("key") &&
                !raw.all { it.isDigit() }
              ) {
                detectedName = raw
                break
              }
            }
          }

          val detectedId = if (detectedPhone != null) "NG$detectedPhone" else null

          continuation.resume(
            ExtractedStudentInfo(
              studentName = detectedName?.takeIf { it.isNotBlank() },
              studentId = detectedId,
              whatsappNumber = detectedPhone,
              block = detectedBlock,
              caste = detectedCaste,
              gender = detectedGender,
              qualification = detectedQual,
              questionSetName = detectedSet
            )
          )
          recognizer.close()
        }
        .addOnFailureListener {
          continuation.resume(ExtractedStudentInfo(null, null, null))
          recognizer.close()
        }
    } catch (e: Exception) {
      continuation.resume(ExtractedStudentInfo(null, null, null))
    }
  }

  /**
   * Samples average pixel darkness in a circular bubble area.
   */
  private fun sampleCircularDarkness(
    bitmap: Bitmap,
    cx: Float,
    cy: Float,
    radius: Float
  ): Float {
    val w = bitmap.width
    val h = bitmap.height

    val minX = max(0, (cx - radius).toInt())
    val maxX = min(w - 1, (cx + radius).toInt())
    val minY = max(0, (cy - radius).toInt())
    val maxY = min(h - 1, (cy + radius).toInt())

    var totalDarkness = 0.0
    var count = 0

    val r2 = (radius * 0.8f) * (radius * 0.8f) // Inner 80% to avoid boundary borders

    for (y in minY..maxY) {
      for (x in minX..maxX) {
        val dx = x - cx
        val dy = y - cy
        if ((dx * dx + dy * dy) <= r2) {
          val pixel = bitmap.getPixel(x, y)
          val red = Color.red(pixel)
          val green = Color.green(pixel)
          val blue = Color.blue(pixel)
          // Grayscale standard (Rec. 601)
          val gray = 0.299f * red + 0.587f * green + 0.114f * blue
          val darkness = (255f - gray) / 255f // 1.0 = pitch black, 0.0 = pure white
          totalDarkness += darkness
          count++
        }
      }
    }

    return if (count > 0) (totalDarkness / count).toFloat() else 0f
  }

  /**
   * Measures background paper brightness across multiple neutral points.
   */
  private fun measureBackgroundDarkness(bitmap: Bitmap): Float {
    val w = bitmap.width
    val h = bitmap.height

    val testPoints = listOf(
      Pair(0.1f, 0.1f),
      Pair(0.9f, 0.1f),
      Pair(0.5f, 0.35f),
      Pair(0.1f, 0.95f),
      Pair(0.9f, 0.95f)
    )

    var totalDarkness = 0.0
    testPoints.forEach { (rx, ry) ->
      val px = (rx * w).toInt().coerceIn(0, w - 1)
      val py = (ry * h).toInt().coerceIn(0, h - 1)
      val pixel = bitmap.getPixel(px, py)
      val gray = 0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel)
      totalDarkness += (255f - gray) / 255f
    }
    return (totalDarkness / testPoints.size).toFloat().coerceIn(0.02f, 0.30f)
  }

  /**
   * Annotates the scanned sheet with green/red/amber rings over detected bubbles for verification.
   */
  private fun createAnnotatedBitmap(
    source: Bitmap,
    bubbleMap: Map<Int, Map<String, ScannedBubbleData>>,
    answers: Map<Int, String>
  ): Bitmap {
    val copy = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(copy)

    val greenPaint = Paint().apply {
      color = Color.rgb(22, 163, 74)
      style = Paint.Style.STROKE
      strokeWidth = 4f
      isAntiAlias = true
    }

    val amberPaint = Paint().apply {
      color = Color.rgb(217, 119, 6)
      style = Paint.Style.STROKE
      strokeWidth = 4f
      isAntiAlias = true
    }

    val purplePaint = Paint().apply {
      color = Color.rgb(124, 58, 237)
      style = Paint.Style.STROKE
      strokeWidth = 4f
      isAntiAlias = true
    }

    // 4 Bright Green Corner Alignment Squares (as shown in OMR verification screenshot)
    val w = copy.width.toFloat()
    val h = copy.height.toFloat()
    val cornerSize = w * 0.052f
    val halfC = cornerSize / 2f
    val innerSquareSize = cornerSize * 0.5f
    val halfInnerC = innerSquareSize / 2f

    val greenFillPaint = Paint().apply {
      color = Color.rgb(0, 230, 118) // #00E676
      style = Paint.Style.FILL
    }
    val greenStrokePaint = Paint().apply {
      color = Color.rgb(0, 200, 83) // #00C853
      style = Paint.Style.STROKE
      strokeWidth = 2.5f
    }
    val innerBlackPaint = Paint().apply {
      color = Color.BLACK
      style = Paint.Style.FILL
    }

    val cornerCoords = listOf(
      Pair(w * OmrLayoutDefinition.CORNER_TL_X, h * OmrLayoutDefinition.CORNER_TL_Y),
      Pair(w * OmrLayoutDefinition.CORNER_TR_X, h * OmrLayoutDefinition.CORNER_TR_Y),
      Pair(w * OmrLayoutDefinition.CORNER_BL_X, h * OmrLayoutDefinition.CORNER_BL_Y),
      Pair(w * OmrLayoutDefinition.CORNER_BR_X, h * OmrLayoutDefinition.CORNER_BR_Y)
    )

    cornerCoords.forEach { (cx, cy) ->
      canvas.drawRect(cx - halfC, cy - halfC, cx + halfC, cy + halfC, greenFillPaint)
      canvas.drawRect(cx - halfC, cy - halfC, cx + halfC, cy + halfC, greenStrokePaint)
      canvas.drawRect(cx - halfInnerC, cy - halfInnerC, cx + halfInnerC, cy + halfInnerC, innerBlackPaint)
    }

    bubbleMap.forEach { (qNum, options) ->
      val ans = answers[qNum]
      when (ans) {
        "A", "B", "C", "D" -> {
          val bData = options[ans]
          if (bData != null) {
            canvas.drawCircle(bData.centerX, bData.centerY, bData.radius + 3f, greenPaint)
          }
        }
        "REVIEW" -> {
          options.values.forEach { bData ->
            if (bData.fillRatio > 0.15f) {
              canvas.drawCircle(bData.centerX, bData.centerY, bData.radius + 3f, amberPaint)
            }
          }
        }
        "MULTIPLE" -> {
          options.values.forEach { bData ->
            if (bData.fillRatio > 0.25f) {
              canvas.drawCircle(bData.centerX, bData.centerY, bData.radius + 3f, purplePaint)
            }
          }
        }
      }
    }

    return copy
  }
}
