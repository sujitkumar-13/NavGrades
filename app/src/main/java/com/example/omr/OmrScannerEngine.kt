package com.example.omr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

data class ScannedBubbleData(
  val questionNumber: Int,
  val option: String,
  val fillRatio: Float,
  val centerX: Float,
  val centerY: Float,
  val radius: Float
)

data class ExtractedStudentInfo(
  val firstName: String? = null,
  val lastName: String? = null,
  val studentName: String? = null,
  val studentId: String? = null,
  val phoneNumber: String? = null,
  val whatsappNumber: String? = null,
  val block: String? = null,
  val caste: String? = null,
  val gender: String? = null,
  val qualification: String? = null,
  val school: String? = null,
  val questionSetName: String? = null,
  val courseCode: String? = null,
  val handwritingAudit: com.example.omr.handwriting.HandwritingRunAudit? = null
)

data class OmrScanOutput(
  val studentId: String,
  val firstName: String = "",
  val lastName: String = "",
  val studentName: String,
  val detectedAnswers: Map<Int, String>, // 1 -> "A", 2 -> "B", 3 -> "BLANK", etc.
  val reviewRequiredQuestions: List<Int>,
  val confidenceScore: Float,
  val annotatedBitmap: Bitmap?,
  val phoneNumber: String = "",
  val whatsappNumber: String = "",
  val block: String = "",
  val cast: String = "",
  val gender: String = "",
  val qualification: String = "",
  val school: String = "",
  val questionSetName: String = "",
  val courseCode: String = "",
  val handwritingAudit: com.example.omr.handwriting.HandwritingRunAudit? = null
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
  val brPos: CornerPoint? = null,
  val isGeometryValid: Boolean = false
) {
  val allAligned: Boolean get() = tl && tr && bl && br
  val isReadyForCapture: Boolean get() = allAligned && isGeometryValid
  val count: Int get() = (if (tl) 1 else 0) + (if (tr) 1 else 0) + (if (bl) 1 else 0) + (if (br) 1 else 0)

  fun isCloseTo(other: CornerAlignmentState, maxDrift: Float = 0.035f): Boolean {
    val p1 = tlPos ?: return false
    val p2 = other.tlPos ?: return false
    val p3 = trPos ?: return false
    val p4 = other.trPos ?: return false
    val p5 = blPos ?: return false
    val p6 = other.blPos ?: return false
    val p7 = brPos ?: return false
    val p8 = other.brPos ?: return false

    val maxDriftSq = maxDrift * maxDrift
    return distSq(p1, p2) < maxDriftSq &&
           distSq(p3, p4) < maxDriftSq &&
           distSq(p5, p6) < maxDriftSq &&
           distSq(p7, p8) < maxDriftSq
  }

  private fun distSq(a: CornerPoint, b: CornerPoint): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
  }
}

object OmrScannerEngine {

  /**
   * Checks real-time alignment for each of the 4 OMR sheet corner fiducials.
   * Searches for solid black square markers in 4 corner zones and validates quadrilateral geometry.
   * Requires all 4 markers to be detected (no 3-corner guessing/extrapolation).
   */
  fun detectCornerAlignment(bitmap: Bitmap): CornerAlignmentState {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 80 || h < 80) return CornerAlignmentState()

    // 1. Scene background check (must have paper present)
    val bgDarkness = measureBackgroundDarkness(bitmap)
    if (bgDarkness > 0.65f) {
      return CornerAlignmentState()
    }

    // Adaptive threshold for dark markers based on ambient lighting
    val darkThreshold = (bgDarkness + 0.28f).coerceIn(0.40f, 0.68f)

    // 2. Check each corner quadrant independently for solid black square fiducial
    val tlPos = findBlackSquareFiducial(bitmap, 0.00f, 0.38f, 0.00f, 0.38f, darkThreshold, bgDarkness, "TL")
    val trPos = findBlackSquareFiducial(bitmap, 0.62f, 1.00f, 0.00f, 0.38f, darkThreshold, bgDarkness, "TR")
    val blPos = findBlackSquareFiducial(bitmap, 0.00f, 0.38f, 0.62f, 1.00f, darkThreshold, bgDarkness, "BL")
    val brPos = findBlackSquareFiducial(bitmap, 0.62f, 1.00f, 0.62f, 1.00f, darkThreshold, bgDarkness, "BR")

    // ABSOLUTELY NO 3-CORNER EXTRAPOLATION! All 4 must be visually detected.
    val allFound = tlPos != null && trPos != null && blPos != null && brPos != null

    // 3. Validate Quadrilateral Geometry
    val geometryValid = if (allFound) {
      validateQuadGeometry(tlPos!!, trPos!!, blPos!!, brPos!!)
    } else {
      false
    }

    return CornerAlignmentState(
      tl = tlPos != null,
      tr = trPos != null,
      bl = blPos != null,
      br = brPos != null,
      tlPos = tlPos,
      trPos = trPos,
      blPos = blPos,
      brPos = brPos,
      isGeometryValid = geometryValid
    )
  }

  /**
   * Applies perspective correction using android.graphics.Matrix setPolyToPoly.
   * Warps the quadrilateral defined by the 4 detected corners to standard template coordinates.
   */
  fun correctPerspective(bitmap: Bitmap, alignment: CornerAlignmentState): Bitmap {
    val tl = alignment.tlPos
    val tr = alignment.trPos
    val bl = alignment.blPos
    val br = alignment.brPos
    if (tl == null || tr == null || bl == null || br == null) {
      return bitmap
    }

    val srcW = bitmap.width.toFloat()
    val srcH = bitmap.height.toFloat()

    val srcPoints = floatArrayOf(
      tl.x * srcW, tl.y * srcH, // Top-Left
      tr.x * srcW, tr.y * srcH, // Top-Right
      br.x * srcW, br.y * srcH, // Bottom-Right
      bl.x * srcW, bl.y * srcH  // Bottom-Left
    )

    val targetW = OmrLayoutDefinition.STANDARD_WIDTH.toFloat()
    val targetH = OmrLayoutDefinition.STANDARD_HEIGHT.toFloat()

    val dstPoints = floatArrayOf(
      OmrLayoutDefinition.CORNER_TL_X * targetW, OmrLayoutDefinition.CORNER_TL_Y * targetH,
      OmrLayoutDefinition.CORNER_TR_X * targetW, OmrLayoutDefinition.CORNER_TR_Y * targetH,
      OmrLayoutDefinition.CORNER_BR_X * targetW, OmrLayoutDefinition.CORNER_BR_Y * targetH,
      OmrLayoutDefinition.CORNER_BL_X * targetW, OmrLayoutDefinition.CORNER_BL_Y * targetH
    )

    val matrix = Matrix()
    val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
    if (!success) {
      return bitmap
    }

    val resultBitmap = Bitmap.createBitmap(targetW.toInt(), targetH.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(resultBitmap)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    canvas.drawBitmap(bitmap, matrix, paint)
    return resultBitmap
  }

  private fun findBlackSquareFiducial(
    bitmap: Bitmap,
    minRelX: Float,
    maxRelX: Float,
    minRelY: Float,
    maxRelY: Float,
    darkThreshold: Float,
    bgDarkness: Float,
    cornerType: String
  ): CornerPoint? {
    val w = bitmap.width
    val h = bitmap.height

    val xStart = (minRelX * w).toInt().coerceIn(0, w - 1)
    val xEnd = (maxRelX * w).toInt().coerceIn(0, w - 1)
    val yStart = (minRelY * h).toInt().coerceIn(0, h - 1)
    val yEnd = (maxRelY * h).toInt().coerceIn(0, h - 1)

    // Expected marker size is between 1.6% and 8.5% of image width
    val minMarkerSize = (w * 0.016f).toInt().coerceAtLeast(6)
    val maxMarkerSize = (w * 0.085f).toInt().coerceAtLeast(18)

    val step = (minMarkerSize / 3).coerceIn(2, 5)
    val qWidth = xEnd - xStart + 1
    val qHeight = yEnd - yStart + 1
    val visited = BooleanArray(qWidth * qHeight)

    fun isVisited(x: Int, y: Int): Boolean {
      val idx = (y - yStart) * qWidth + (x - xStart)
      return if (idx in visited.indices) visited[idx] else true
    }

    fun setVisited(x: Int, y: Int) {
      val idx = (y - yStart) * qWidth + (x - xStart)
      if (idx in visited.indices) visited[idx] = true
    }

    var bestPoint: CornerPoint? = null
    var bestScore = 0f

    val queueX = IntArray(1200)
    val queueY = IntArray(1200)

    for (y in yStart until yEnd step step) {
      for (x in xStart until xEnd step step) {
        if (isVisited(x, y)) continue

        val pixel = bitmap.getPixel(x, y)
        val gray = 0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel)
        val darkness = (255f - gray) / 255f

        if (darkness >= darkThreshold) {
          // Connected component search for solid square candidate
          var head = 0
          var tail = 0

          queueX[tail] = x
          queueY[tail] = y
          tail++
          setVisited(x, y)

          var minX = x
          var maxX = x
          var minY = y
          var maxY = y
          var sumX = 0L
          var sumY = 0L
          var count = 0

          while (head < tail && tail < 1190) {
            val cx = queueX[head]
            val cy = queueY[head]
            head++

            sumX += cx
            sumY += cy
            count++

            if (cx < minX) minX = cx
            if (cx > maxX) maxX = cx
            if (cy < minY) minY = cy
            if (cy > maxY) maxY = cy

            val neighbors = arrayOf(
              cx - step to cy,
              cx + step to cy,
              cx to cy - step,
              cx to cy + step
            )

            for ((nx, ny) in neighbors) {
              if (nx in xStart until xEnd && ny in yStart until yEnd && !isVisited(nx, ny)) {
                val np = bitmap.getPixel(nx, ny)
                val ngray = 0.299f * Color.red(np) + 0.587f * Color.green(np) + 0.114f * Color.blue(np)
                val ndarkness = (255f - ngray) / 255f
                if (ndarkness >= darkThreshold) {
                  setVisited(nx, ny)
                  queueX[tail] = nx
                  queueY[tail] = ny
                  tail++
                }
              }
            }
          }

          val bw = maxX - minX + 1
          val bh = maxY - minY + 1

          // Filter against Square Marker Criteria
          if (bw in minMarkerSize..maxMarkerSize && bh in minMarkerSize..maxMarkerSize) {
            val aspect = bw.toFloat() / bh.toFloat()
            val expGridPts = (bw / step + 1) * (bh / step + 1)
            val density = count.toFloat() / expGridPts.coerceAtLeast(1)

            // Must have square aspect ratio and solid density
            if (aspect in 0.68f..1.45f && density >= 0.45f) {
              val cenX = sumX.toFloat() / count
              val cenY = sumY.toFloat() / count

              // Verify surrounding contrast against paper interior
              val pad = (bw * 0.7f).toInt().coerceAtLeast(4)
              val marginDarkness = sampleMarginDarkness(bitmap, minX, maxX, minY, maxY, pad, cornerType)
              val contrast = darkThreshold - marginDarkness

              if (marginDarkness <= (bgDarkness + 0.22f) && contrast > 0.08f) {
                val aspectScore = 1.0f - kotlin.math.abs(1.0f - aspect)
                val score = aspectScore * 2.0f + density * 2.5f + contrast * 3.0f

                if (score > bestScore) {
                  bestScore = score
                  bestPoint = CornerPoint(cenX / w, cenY / h)
                }
              }
            }
          }
        }
      }
    }

    return bestPoint
  }

  private fun sampleMarginDarkness(
    bitmap: Bitmap,
    minX: Int,
    maxX: Int,
    minY: Int,
    maxY: Int,
    pad: Int,
    cornerType: String
  ): Float {
    val w = bitmap.width
    val h = bitmap.height
    var totalDarkness = 0f
    var count = 0

    // For Top corners, sample below the marker (towards sheet interior)
    if (cornerType.startsWith("T")) {
      val testY = (maxY + pad).coerceIn(0, h - 1)
      for (x in minX..maxX step 3) {
        val p = bitmap.getPixel(x, testY)
        val gray = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
        totalDarkness += (255f - gray) / 255f
        count++
      }
    }
    // For Bottom corners, sample above the marker (towards sheet interior)
    if (cornerType.startsWith("B")) {
      val testY = (minY - pad).coerceIn(0, h - 1)
      for (x in minX..maxX step 3) {
        val p = bitmap.getPixel(x, testY)
        val gray = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
        totalDarkness += (255f - gray) / 255f
        count++
      }
    }
    // For Left corners, sample to the right (towards sheet interior)
    if (cornerType.endsWith("L")) {
      val testX = (maxX + pad).coerceIn(0, w - 1)
      for (y in minY..maxY step 3) {
        val p = bitmap.getPixel(testX, y)
        val gray = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
        totalDarkness += (255f - gray) / 255f
        count++
      }
    }
    // For Right corners, sample to the left (towards sheet interior)
    if (cornerType.endsWith("R")) {
      val testX = (minX - pad).coerceIn(0, w - 1)
      for (y in minY..maxY step 3) {
        val p = bitmap.getPixel(testX, y)
        val gray = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
        totalDarkness += (255f - gray) / 255f
        count++
      }
    }

    return if (count > 0) totalDarkness / count else 0f
  }

  /**
   * Validates quadrilateral geometry of the 4 detected corners.
   * Ensures convexity, correct aspect ratio (~1.50 for OMR), parallel edges, and sufficient size.
   */
  fun validateQuadGeometry(tl: CornerPoint, tr: CornerPoint, bl: CornerPoint, br: CornerPoint): Boolean {
    // 1. Basic relative positions: top above bottom, left to the left of right
    if (tl.x >= tr.x - 0.20f || bl.x >= br.x - 0.20f) return false
    if (tl.y >= bl.y - 0.30f || tr.y >= br.y - 0.30f) return false

    // 2. Check Convexity using cross product of consecutive edge vectors
    fun crossProduct(ax: Float, ay: Float, bx: Float, by: Float): Float {
      return ax * by - ay * bx
    }

    val cp1 = crossProduct(tr.x - tl.x, tr.y - tl.y, br.x - tr.x, br.y - tr.y)
    val cp2 = crossProduct(br.x - tr.x, br.y - tr.y, bl.x - br.x, bl.y - br.y)
    val cp3 = crossProduct(bl.x - br.x, bl.y - br.y, tl.x - bl.x, tl.y - bl.y)
    val cp4 = crossProduct(tl.x - bl.x, tl.y - bl.y, tr.x - tl.x, tr.y - tl.y)

    val allPositive = cp1 > 0f && cp2 > 0f && cp3 > 0f && cp4 > 0f
    val allNegative = cp1 < 0f && cp2 < 0f && cp3 < 0f && cp4 < 0f
    if (!allPositive && !allNegative) return false

    // 3. Edge lengths
    val topW = kotlin.math.hypot(tr.x - tl.x, tr.y - tl.y)
    val botW = kotlin.math.hypot(br.x - bl.x, br.y - bl.y)
    val leftH = kotlin.math.hypot(bl.x - tl.x, bl.y - tl.y)
    val rightH = kotlin.math.hypot(br.x - tr.x, br.y - tr.y)

    val avgW = (topW + botW) / 2f
    val avgH = (leftH + rightH) / 2f
    if (avgW < 0.20f || avgH < 0.30f) return false

    // 4. Aspect ratio (standard OMR template is 1.50)
    val ratio = avgH / avgW
    if (ratio !in 1.15f..1.90f) return false

    // 5. Parallelism (opposite edges within 40% difference)
    val maxW = kotlin.math.max(topW, botW)
    val maxH = kotlin.math.max(leftH, rightH)
    if (kotlin.math.abs(topW - botW) / maxW > 0.40f) return false
    if (kotlin.math.abs(leftH - rightH) / maxH > 0.40f) return false

    // 6. Area check using Shoelace formula
    val quadArea = 0.5f * kotlin.math.abs(
      (tl.x * tr.y - tr.x * tl.y) +
      (tr.x * br.y - br.x * tr.y) +
      (br.x * bl.y - bl.x * br.y) +
      (bl.x * tl.y - tl.x * bl.y)
    )
    if (quadArea < 0.15f) return false

    return true
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
   * Scans an input Bitmap, applies perspective correction, detects all filled bubbles,
   * category selections (Set, Caste, Gender, Qualification), and extracts student info via region OCR.
   */
  suspend fun processOmrImage(
    sourceBitmap: Bitmap,
    numQuestions: Int,
    defaultStudentName: String = "Student",
    handwritingEngine: com.example.omr.handwriting.HandwritingRecognitionEngine = com.example.omr.handwriting.HybridHandwritingEngine.defaultInstance
  ): OmrScanOutput = withContext(Dispatchers.Default) {
    // 1. Perspective alignment and correction
    val alignment = detectCornerAlignment(sourceBitmap)
    val workingBitmap = if (alignment.allAligned) {
      correctPerspective(sourceBitmap, alignment)
    } else {
      sourceBitmap
    }

    val width = workingBitmap.width
    val height = workingBitmap.height

    // 2. Measure adaptive background paper brightness across multiple neutral points
    val bgDarkness = measureBackgroundDarkness(workingBitmap)

    // 3. Scan Question Bubbles (1..numQuestions)
    val bubbleCoords = OmrLayoutDefinition.getQuestionBubbleCoordinates(numQuestions)
    val questionBubbleMap = mutableMapOf<Int, MutableMap<String, ScannedBubbleData>>()

    bubbleCoords.forEach { coord ->
      val px = coord.relX * width
      val py = coord.relY * height
      val radius = OmrLayoutDefinition.BUBBLE_RADIUS * width

      val darkness = sampleCircularDarkness(workingBitmap, px, py, radius)
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

    // 4. Classify Each Question
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

      when {
        topFill < 0.16f -> {
          detectedAnswers[q] = "BLANK"
          totalConfidence += 1.0f
        }
        topFill >= 0.28f && secondFill >= 0.25f -> {
          detectedAnswers[q] = "MULTIPLE"
          totalConfidence += 0.8f
        }
        topFill in 0.16f..0.28f -> {
          detectedAnswers[q] = "REVIEW"
          reviewRequiredList.add(q)
          totalConfidence += 0.5f
        }
        topFill > 0.28f -> {
          if ((topFill - secondFill) >= 0.10f) {
            detectedAnswers[q] = topOption?.key ?: "BLANK"
            totalConfidence += 1.0f
          } else {
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

    // 5. Detect Category Bubbles (Set, Caste, Gender, Qualification)
    val detectedSet = detectSetSelection(workingBitmap, bgDarkness)
    val detectedCaste = detectSelectedOption(
      bitmap = workingBitmap,
      circles = OmrLayoutDefinition.CASTE_CIRCLES,
      labels = OmrLayoutDefinition.CASTE_LABELS,
      bgDarkness = bgDarkness
    )
    val detectedGender = detectSelectedOption(
      bitmap = workingBitmap,
      circles = OmrLayoutDefinition.GENDER_CIRCLES,
      labels = OmrLayoutDefinition.GENDER_LABELS,
      bgDarkness = bgDarkness
    )
    val detectedQual = detectSelectedOption(
      bitmap = workingBitmap,
      circles = OmrLayoutDefinition.QUALIFICATION_CIRCLES,
      labels = OmrLayoutDefinition.QUALIFICATION_LABELS,
      bgDarkness = bgDarkness
    )

    // 6. Extract Student Details via independent HandwritingRecognitionEngine
    val ocrInfo = extractStudentInfoFromOmr(workingBitmap, handwritingEngine)

    val finalFirstName = ocrInfo.firstName ?: ""
    val finalLastName = ocrInfo.lastName ?: ""
    val finalCombinedName = ocrInfo.studentName?.takeIf { it.isNotBlank() } ?: defaultStudentName
    val finalPhoneNumber = ocrInfo.phoneNumber ?: ""
    val finalWhatsapp = ocrInfo.whatsappNumber ?: finalPhoneNumber
    val finalCity = ocrInfo.block ?: ""
    val finalSchool = ocrInfo.school ?: ""
    val finalCourseCode = ocrInfo.courseCode ?: ""

    val studentId = ocrInfo.studentId ?: if (finalPhoneNumber.isNotBlank()) "NG$finalPhoneNumber" else "NG-${(1000..9999).random()}"

    // 7. Generate Annotated Image with detection rings
    val annotated = createAnnotatedBitmap(
      source = workingBitmap,
      bubbleMap = questionBubbleMap,
      answers = detectedAnswers,
      selectedSet = detectedSet,
      selectedCaste = detectedCaste,
      selectedGender = detectedGender,
      selectedQual = detectedQual
    )

    val avgConfidence = if (numQuestions > 0) totalConfidence / numQuestions else 1.0f

    OmrScanOutput(
      studentId = studentId,
      firstName = finalFirstName,
      lastName = finalLastName,
      studentName = finalCombinedName,
      detectedAnswers = detectedAnswers,
      reviewRequiredQuestions = reviewRequiredList,
      confidenceScore = avgConfidence,
      annotatedBitmap = annotated,
      phoneNumber = finalPhoneNumber,
      whatsappNumber = finalWhatsapp,
      block = finalCity,
      cast = detectedCaste.ifBlank { ocrInfo.caste ?: "" },
      gender = detectedGender.ifBlank { ocrInfo.gender ?: "" },
      qualification = detectedQual.ifBlank { ocrInfo.qualification ?: "" },
      school = finalSchool,
      questionSetName = detectedSet,
      courseCode = finalCourseCode,
      handwritingAudit = ocrInfo.handwritingAudit
    )
  }

  /**
   * Detects Set selection (Set A vs Set B).
   */
  fun detectSetSelection(bitmap: Bitmap, bgDarkness: Float): String {
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val radius = OmrLayoutDefinition.BUBBLE_RADIUS * w

    val setADarkness = sampleCircularDarkness(
      bitmap,
      OmrLayoutDefinition.SET_A_BUBBLE_X * w,
      OmrLayoutDefinition.SET_A_BUBBLE_Y * h,
      radius
    )
    val setBDarkness = sampleCircularDarkness(
      bitmap,
      OmrLayoutDefinition.SET_B_BUBBLE_X * w,
      OmrLayoutDefinition.SET_B_BUBBLE_Y * h,
      radius
    )

    return if (setBDarkness > setADarkness && (setBDarkness - bgDarkness) > 0.10f) {
      "Set - B"
    } else {
      "Set - A"
    }
  }

  /**
   * Samples fill darkness across multiple choice circle options (e.g. Caste, Gender, Qualification)
   * and returns the label of the option with highest fill ratio.
   */
  fun detectSelectedOption(
    bitmap: Bitmap,
    circles: List<Pair<Float, Float>>,
    labels: List<String>,
    bgDarkness: Float,
    minContrast: Float = 0.10f
  ): String {
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val radius = OmrLayoutDefinition.BUBBLE_RADIUS * w

    val scores = circles.map { (rx, ry) ->
      sampleCircularDarkness(bitmap, rx * w, ry * h, radius)
    }

    val maxScore = scores.maxOrNull() ?: 0f
    val maxIdx = scores.indexOf(maxScore)
    val contrast = maxScore - bgDarkness

    return if (contrast >= minContrast && maxIdx in labels.indices) {
      labels[maxIdx]
    } else {
      ""
    }
  }

  /**
   * Extracts raw geometric crops for student text and handwriting regions.
   */
  fun extractFieldCrops(rectifiedSheet: Bitmap): com.example.omr.handwriting.OmrFieldCrops {
    return com.example.omr.handwriting.OmrFieldCrops.fromRectifiedSheet(rectifiedSheet)
  }

  /**
   * Recognizes student info from region-cropped areas using the independent HandwritingRecognitionEngine
   * (or legacy full-sheet OCR when OLD_MODEL rollback mode is active).
   */
  suspend fun extractStudentInfoFromOmr(
    bitmap: Bitmap,
    handwritingEngine: com.example.omr.handwriting.HandwritingRecognitionEngine = com.example.omr.handwriting.HybridHandwritingEngine.defaultInstance
  ): ExtractedStudentInfo {
    // Controlled Test Mode check: use new models only when explicitly enabled and initialized
    if (com.example.omr.handwriting.OmrHandwritingConfig.currentMode == com.example.omr.handwriting.HandwritingModelMode.NEW_BASELINE_MODEL &&
        com.example.omr.handwriting.OmrHandwritingEngine.isInitialized()) {
      return extractStudentInfoWithNewModel(bitmap, handwritingEngine)
    }

    return extractLegacyStudentInfo(bitmap)
  }

  /**
   * Integration path delegating to the independent HandwritingRecognitionEngine via OmrFieldCrops.
   */
  private suspend fun extractStudentInfoWithNewModel(
    bitmap: Bitmap,
    handwritingEngine: com.example.omr.handwriting.HandwritingRecognitionEngine = com.example.omr.handwriting.HybridHandwritingEngine.defaultInstance
  ): ExtractedStudentInfo {
    val fieldCrops = extractFieldCrops(bitmap)
    val hwResult = handwritingEngine.recognizeStudentInfo(fieldCrops)

    val studentId = if (hwResult.phone.isNotBlank()) "NG${hwResult.phone}" else "NG-${(1000..9999).random()}"

    return ExtractedStudentInfo(
      firstName = hwResult.firstName.takeIf { it.isNotBlank() },
      lastName = hwResult.lastName.takeIf { it.isNotBlank() },
      studentName = hwResult.studentName.takeIf { it.isNotBlank() },
      studentId = studentId,
      phoneNumber = hwResult.phone.takeIf { it.isNotBlank() },
      whatsappNumber = hwResult.whatsapp.takeIf { it.isNotBlank() } ?: hwResult.phone.takeIf { it.isNotBlank() },
      block = hwResult.city.takeIf { it.isNotBlank() },
      school = hwResult.school.takeIf { it.isNotBlank() },
      courseCode = hwResult.courseCode.takeIf { it.isNotBlank() },
      handwritingAudit = hwResult.runAudit
    )
  }

  /**
   * Legacy rollback path for OLD_MODEL mode.
   */
  private suspend fun extractLegacyStudentInfo(bitmap: Bitmap): ExtractedStudentInfo {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
      val courseCodeRaw = recognizeTextInCrop(bitmap, OmrLayoutDefinition.COURSE_CODE_REGION, recognizer)
      val firstNameRaw = recognizeTextInCrop(bitmap, OmrLayoutDefinition.FIRST_NAME_REGION, recognizer)
      val lastNameRaw = recognizeTextInCrop(bitmap, OmrLayoutDefinition.LAST_NAME_REGION, recognizer)
      val phoneRaw = recognizeTextInCrop(bitmap, OmrLayoutDefinition.PHONE_REGION, recognizer)
      val whatsappRaw = recognizeTextInCrop(bitmap, OmrLayoutDefinition.WHATSAPP_REGION, recognizer)
      val cityRaw = recognizeTextInCrop(bitmap, OmrLayoutDefinition.CITY_REGION, recognizer)
      val schoolRaw = recognizeTextInCrop(bitmap, OmrLayoutDefinition.SCHOOL_REGION, recognizer)

      // Clean Course Code (e.g. "SOB", "MCA")
      val courseCode = courseCodeRaw.replace(Regex("""[^A-Za-z0-9]"""), "").trim().uppercase()

      // Clean First and Last Name
      val firstName = firstNameRaw.replace(Regex("""[^A-Za-z\s]"""), "").replace(Regex("""\s+"""), " ").trim()
      val lastName = lastNameRaw.replace(Regex("""[^A-Za-z\s]"""), "").replace(Regex("""\s+"""), " ").trim()
      val combinedName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").trim()

      // Clean Phone and WhatsApp
      val phoneDigits = phoneRaw.replace(Regex("""\D"""), "")
      val phone10 = if (phoneDigits.length >= 10) phoneDigits.takeLast(10) else phoneDigits

      val whatsappDigits = whatsappRaw.replace(Regex("""\D"""), "")
      val whatsapp10 = if (whatsappDigits.length >= 10) whatsappDigits.takeLast(10) else if (phone10.length == 10) phone10 else whatsappDigits

      // Clean City/Village & School
      val cityClean = cityRaw.replace(Regex("""[|_~`]+"""), "").trim()
      val schoolClean = schoolRaw.replace(Regex("""[|_~`]+"""), "").trim()

      // Fallback: If name or phone were empty, run full sheet OCR
      var finalCombinedName = combinedName
      var finalPhone = phone10
      var finalWhatsapp = whatsapp10

      if (finalCombinedName.isBlank() || finalPhone.length < 10) {
        val fullImage = InputImage.fromBitmap(bitmap, 0)
        val fullVisionText = suspendCancellableCoroutine { cont ->
          recognizer.process(fullImage)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
        }

        if (fullVisionText != null) {
          val lines = fullVisionText.textBlocks.flatMap { it.lines }
          if (finalPhone.length < 10) {
            for (l in lines) {
              val m = Regex("""\b[6-9]\d{9}\b""").find(l.text)
              if (m != null) {
                finalPhone = m.value
                if (finalWhatsapp.length < 10) finalWhatsapp = m.value
                break
              }
            }
          }
          if (finalCombinedName.isBlank()) {
            for (l in lines) {
              val t = l.text.trim()
              if (t.contains("Name", ignoreCase = true) && t.length > 5) {
                finalCombinedName = t.substringAfter("Name", "").replace(":", "").trim()
                break
              }
            }
          }
        }
      }

      val studentId = if (finalPhone.isNotBlank()) "NG$finalPhone" else "NG-${(1000..9999).random()}"

      ExtractedStudentInfo(
        firstName = firstName.takeIf { it.isNotBlank() },
        lastName = lastName.takeIf { it.isNotBlank() },
        studentName = finalCombinedName.takeIf { it.isNotBlank() },
        studentId = studentId,
        phoneNumber = finalPhone.takeIf { it.isNotBlank() },
        whatsappNumber = finalWhatsapp.takeIf { it.isNotBlank() } ?: finalPhone.takeIf { it.isNotBlank() },
        block = cityClean.takeIf { it.isNotBlank() },
        school = schoolClean.takeIf { it.isNotBlank() },
        courseCode = courseCode.takeIf { it.isNotBlank() }
      )
    } finally {
      recognizer.close()
    }
  }

  private fun getCropBitmap(bitmap: Bitmap, region: android.graphics.RectF): Bitmap {
    val bW = bitmap.width
    val bH = bitmap.height
    val cropX = (region.left * bW).toInt().coerceIn(0, bW - 1)
    val cropY = (region.top * bH).toInt().coerceIn(0, bH - 1)
    val cropW = ((region.right - region.left) * bW).toInt().coerceIn(1, bW - cropX)
    val cropH = ((region.bottom - region.top) * bH).toInt().coerceIn(1, bH - cropY)
    return Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
  }

  private suspend fun recognizeTextInCrop(
    bitmap: Bitmap,
    region: RectF,
    recognizer: TextRecognizer
  ): String = suspendCancellableCoroutine { cont ->
    try {
      val bW = bitmap.width
      val bH = bitmap.height
      val cropX = (region.left * bW).toInt().coerceIn(0, bW - 1)
      val cropY = (region.top * bH).toInt().coerceIn(0, bH - 1)
      val cropW = ((region.right - region.left) * bW).toInt().coerceIn(1, bW - cropX)
      val cropH = ((region.bottom - region.top) * bH).toInt().coerceIn(1, bH - cropY)

      val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
      val inputImage = InputImage.fromBitmap(cropped, 0)

      recognizer.process(inputImage)
        .addOnSuccessListener { visionText ->
          cont.resume(visionText.text.trim())
        }
        .addOnFailureListener {
          cont.resume("")
        }
    } catch (e: Exception) {
      cont.resume("")
    }
  }

  /**
   * Samples pixel darkness in a circular bubble area, with center weighting and ring suppression.
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
    var totalWeight = 0.0

    val innerR2 = (radius * 0.80f) * (radius * 0.80f)
    val centerR2 = (radius * 0.45f) * (radius * 0.45f)

    for (y in minY..maxY) {
      for (x in minX..maxX) {
        val dx = x - cx
        val dy = y - cy
        val distSq = dx * dx + dy * dy
        if (distSq <= innerR2) {
          val pixel = bitmap.getPixel(x, y)
          val red = Color.red(pixel)
          val green = Color.green(pixel)
          val blue = Color.blue(pixel)
          val gray = 0.299f * red + 0.587f * green + 0.114f * blue
          val darkness = (255f - gray) / 255f
          val weight = if (distSq <= centerR2) 2.0 else 1.0
          totalDarkness += darkness * weight
          totalWeight += weight
        }
      }
    }

    return if (totalWeight > 0.0) (totalDarkness / totalWeight).toFloat() else 0f
  }

  /**
   * Measures background paper brightness across multiple neutral points and computes the median.
   */
  fun measureBackgroundDarkness(bitmap: Bitmap): Float {
    val w = bitmap.width
    val h = bitmap.height

    val testPoints = listOf(
      Pair(0.12f, 0.04f),
      Pair(0.50f, 0.04f),
      Pair(0.88f, 0.04f),
      Pair(0.12f, 0.12f),
      Pair(0.88f, 0.12f),
      Pair(0.12f, 0.28f),
      Pair(0.88f, 0.28f),
      Pair(0.12f, 0.50f),
      Pair(0.88f, 0.50f),
      Pair(0.50f, 0.50f),
      Pair(0.12f, 0.60f),
      Pair(0.88f, 0.60f),
      Pair(0.12f, 0.85f),
      Pair(0.88f, 0.85f),
      Pair(0.20f, 0.95f),
      Pair(0.50f, 0.95f),
      Pair(0.80f, 0.95f)
    )

    val sampleDarknessList = testPoints.map { (rx, ry) ->
      val px = (rx * w).toInt().coerceIn(0, w - 1)
      val py = (ry * h).toInt().coerceIn(0, h - 1)
      val pixel = bitmap.getPixel(px, py)
      val gray = 0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel)
      (255f - gray) / 255f
    }.sorted()

    val median = sampleDarknessList[sampleDarknessList.size / 2]
    return median.coerceIn(0.02f, 0.40f)
  }

  /**
   * Annotates the scanned sheet with green/red/amber rings over detected bubbles for verification.
   */
  private fun createAnnotatedBitmap(
    source: Bitmap,
    bubbleMap: Map<Int, Map<String, ScannedBubbleData>>,
    answers: Map<Int, String>,
    selectedSet: String = "",
    selectedCaste: String = "",
    selectedGender: String = "",
    selectedQual: String = ""
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

    // 4 Corner Alignment Squares
    val w = copy.width.toFloat()
    val h = copy.height.toFloat()
    val cornerSize = w * 0.045f
    val halfC = cornerSize / 2f
    val innerSquareSize = cornerSize * 0.5f
    val halfInnerC = innerSquareSize / 2f

    val greenFillPaint = Paint().apply {
      color = Color.rgb(0, 230, 118)
      style = Paint.Style.FILL
    }
    val greenStrokePaint = Paint().apply {
      color = Color.rgb(0, 200, 83)
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

    // Annotate Set Selection
    val bubbleR = OmrLayoutDefinition.BUBBLE_RADIUS * w
    if (selectedSet == "Set - A") {
      canvas.drawCircle(w * OmrLayoutDefinition.SET_A_BUBBLE_X, h * OmrLayoutDefinition.SET_A_BUBBLE_Y, bubbleR + 3f, greenPaint)
    } else if (selectedSet == "Set - B") {
      canvas.drawCircle(w * OmrLayoutDefinition.SET_B_BUBBLE_X, h * OmrLayoutDefinition.SET_B_BUBBLE_Y, bubbleR + 3f, greenPaint)
    }

    // Annotate Caste Selection
    val casteIdx = OmrLayoutDefinition.CASTE_LABELS.indexOf(selectedCaste)
    if (casteIdx in OmrLayoutDefinition.CASTE_CIRCLES.indices) {
      val c = OmrLayoutDefinition.CASTE_CIRCLES[casteIdx]
      canvas.drawCircle(w * c.first, h * c.second, bubbleR + 3f, greenPaint)
    }

    // Annotate Gender Selection
    val genderIdx = OmrLayoutDefinition.GENDER_LABELS.indexOf(selectedGender)
    if (genderIdx in OmrLayoutDefinition.GENDER_CIRCLES.indices) {
      val c = OmrLayoutDefinition.GENDER_CIRCLES[genderIdx]
      canvas.drawCircle(w * c.first, h * c.second, bubbleR + 3f, greenPaint)
    }

    // Annotate Qualification Selection
    val qualIdx = OmrLayoutDefinition.QUALIFICATION_LABELS.indexOf(selectedQual)
    if (qualIdx in OmrLayoutDefinition.QUALIFICATION_CIRCLES.indices) {
      val c = OmrLayoutDefinition.QUALIFICATION_CIRCLES[qualIdx]
      canvas.drawCircle(w * c.first, h * c.second, bubbleR + 3f, greenPaint)
    }

    // Annotate Question Bubbles
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
            if (bData.fillRatio > 0.14f) {
              canvas.drawCircle(bData.centerX, bData.centerY, bData.radius + 3f, amberPaint)
            }
          }
        }
        "MULTIPLE" -> {
          options.values.forEach { bData ->
            if (bData.fillRatio > 0.22f) {
              canvas.drawCircle(bData.centerX, bData.centerY, bData.radius + 3f, purplePaint)
            }
          }
        }
      }
    }

    return copy
  }
}
