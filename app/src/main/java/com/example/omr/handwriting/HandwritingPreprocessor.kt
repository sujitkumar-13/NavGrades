package com.example.omr.handwriting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Result of preprocessing an isolated cell for TFLite inference.
 */
data class PreprocessCellResult(
  val tensorBuffer: ByteBuffer?,
  val processedBitmap28x28: Bitmap?,
  val rawInkCount: Int,
  val isEmpty: Boolean,
  val minVal: Float = 0.0f,
  val maxVal: Float = 0.0f,
  val fgPixelRatio: Float = 0.0f,
  val bboxW: Int = 0,
  val bboxH: Int = 0,
  val comOffsetX: Float = 0.0f,
  val comOffsetY: Float = 0.0f,
  val borderContamination: Float = 0.0f
)

/**
 * Result of preprocessing free handwriting (City / School) for OCR.
 */
data class FreeHandwritingResult(
  val cleanedBitmap: Bitmap,
  val hasInk: Boolean,
  val inkPixelCount: Int
)

enum class FreeFieldType {
  CITY,
  SCHOOL
}

/**
 * High-performance, robust handwriting preprocessor.
 * Handles both boxed cell isolation (Track A) and freehand strip normalization (Track B).
 */
object HandwritingPreprocessor {

  const val DEFAULT_INK_THRESHOLD = 145
  const val EMPTY_CELL_INK_THRESHOLD = 8
  const val MIN_STROKE_WIDTH = 2
  const val MIN_STROKE_HEIGHT = 5

  /**
   * Preprocesses a single boxed cell into a standardized 28x28 tensor [1, 28, 28, 1].
   */
  fun preprocessCell(
    boxCrop: Bitmap,
    isDigitField: Boolean,
    inkThreshold: Int = DEFAULT_INK_THRESHOLD
  ): PreprocessCellResult {
    val w = boxCrop.width
    val h = boxCrop.height

    if (w < 4 || h < 4) {
      return PreprocessCellResult(null, null, 0, isEmpty = true)
    }

    val pixels = IntArray(w * h)
    boxCrop.getPixels(pixels, 0, w, 0, 0, w, h)

    val grayArr = IntArray(w * h)
    val inkMask = BooleanArray(w * h)
    var initialInkCount = 0

    for (i in pixels.indices) {
      val p = pixels[i]
      val gray = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
      grayArr[i] = gray
      if (gray < inkThreshold) {
        inkMask[i] = true
        initialInkCount++
      }
    }

    if (initialInkCount < EMPTY_CELL_INK_THRESHOLD) {
      return PreprocessCellResult(null, null, initialInkCount, isEmpty = true)
    }

    // Check for presence of real ink in cell interior (ruling/border lines on paper are faint gray ~130-145)
    // Real handwriting always contains dark core ink pixels
    val interiorLeft = (w * 0.12f).toInt().coerceAtLeast(2)
    val interiorRight = (w * 0.88f).toInt().coerceAtMost(w - 2)
    val interiorTop = (h * 0.12f).toInt().coerceAtLeast(2)
    val interiorBottom = (h * 0.88f).toInt().coerceAtMost(h - 2)

    var hasCoreInk = false
    var interiorDarkCount = 0
    for (y in interiorTop until interiorBottom) {
      for (x in interiorLeft until interiorRight) {
        val g = grayArr[y * w + x]
        if (g < 125) {
          interiorDarkCount++
          if (interiorDarkCount >= 4) {
            hasCoreInk = true
            break
          }
        }
      }
      if (hasCoreInk) break
    }

    if (!hasCoreInk) {
      return PreprocessCellResult(null, null, initialInkCount, isEmpty = true)
    }

    val cleanedMask = inkMask.clone()

    // 1. Horizontal Ruling Suppression in margin bands
    val topBandH = min(4, max(2, (h * 0.12f).toInt()))
    val botBandY = max(h - 4, min(h - 2, (h * 0.88f).toInt()))
    val minHorizontalRun = (w * 0.50f).toInt().coerceAtLeast(5)

    for (y in 0 until h) {
      if (y < topBandH || y > botBandY) {
        var maxRun = 0
        var currRun = 0
        for (x in 0 until w) {
          if (cleanedMask[y * w + x]) {
            currRun++
            if (currRun > maxRun) maxRun = currRun
          } else {
            currRun = 0
          }
        }
        if (maxRun >= minHorizontalRun) {
          val yStart = max(0, y - 1)
          val yEnd = min(h - 1, y + 1)
          for (adjY in yStart..yEnd) {
            for (x in 0 until w) {
              cleanedMask[adjY * w + x] = false
            }
          }
        }
      }
    }

    // Always clear extreme outer top/bottom 1 row
    for (x in 0 until w) {
      cleanedMask[0 * w + x] = false
      if (h > 1) cleanedMask[(h - 1) * w + x] = false
      if (h > 2) cleanedMask[(h - 2) * w + x] = false // bottom box ruling margin
    }

    // 2. Vertical Ruling Suppression on outer borders
    val outerLeftColumns = listOf(0, 1, 2).filter { it in 0 until w }
    for (x in outerLeftColumns) {
      var colInk = 0
      for (y in 0 until h) {
        if (cleanedMask[y * w + x]) colInk++
      }
      if (colInk >= (h * 0.60f).toInt().coerceAtLeast(4)) {
        for (y in 0 until h) cleanedMask[y * w + x] = false
      }
    }

    val outerRightColumns = listOf(w - 3, w - 2, w - 1).filter { it in 0 until w }
    for (x in outerRightColumns) {
      var colInk = 0
      for (y in 0 until h) {
        if (cleanedMask[y * w + x]) colInk++
      }
      if (colInk >= (h * 0.60f).toInt().coerceAtLeast(4)) {
        for (y in 0 until h) cleanedMask[y * w + x] = false
      }
    }

    // 3. 8-Connected Component Analysis
    val visited = BooleanArray(w * h)
    data class Component(
      val points: List<Pair<Int, Int>>,
      val minX: Int,
      val maxX: Int,
      val minY: Int,
      val maxY: Int
    ) {
      val size: Int get() = points.size
      val width: Int get() = maxX - minX + 1
      val height: Int get() = maxY - minY + 1
    }

    val components = mutableListOf<Component>()
    val qX = IntArray(w * h)
    val qY = IntArray(w * h)

    for (y in 0 until h) {
      for (x in 0 until w) {
        val idx = y * w + x
        if (cleanedMask[idx] && !visited[idx]) {
          var head = 0
          var tail = 0
          qX[tail] = x
          qY[tail] = y
          tail++
          visited[idx] = true

          val compPoints = mutableListOf<Pair<Int, Int>>()
          var cMinX = x
          var cMaxX = x
          var cMinY = y
          var cMaxY = y

          while (head < tail) {
            val cx = qX[head]
            val cy = qY[head]
            head++
            compPoints.add(Pair(cx, cy))

            if (cx < cMinX) cMinX = cx
            if (cx > cMaxX) cMaxX = cx
            if (cy < cMinY) cMinY = cy
            if (cy > cMaxY) cMaxY = cy

            for (dy in -1..1) {
              for (dx in -1..1) {
                if (dy == 0 && dx == 0) continue
                val nx = cx + dx
                val ny = cy + dy
                if (nx in 0 until w && ny in 0 until h) {
                  val nIdx = ny * w + nx
                  if (cleanedMask[nIdx] && !visited[nIdx]) {
                    visited[nIdx] = true
                    qX[tail] = nx
                    qY[tail] = ny
                    tail++
                  }
                }
              }
            }
          }
          components.add(Component(compPoints, cMinX, cMaxX, cMinY, cMaxY))
        }
      }
    }

    if (components.isEmpty()) {
      return PreprocessCellResult(null, null, 0, isEmpty = true)
    }

    // 4. Component Selection & Border Noise Filtering
    val mainComp = components.maxByOrNull { it.size } ?: components[0]
    val maxCompSize = mainComp.size
    val mainMinX = mainComp.minX
    val mainMaxX = mainComp.maxX

    val retainedPoints = mutableListOf<Pair<Int, Int>>()

    val borderMargin = (w * 0.20f).toInt().coerceIn(3, 5)

    for (c in components) {
      val isAtLeftBorder = c.maxX <= borderMargin
      val isAtRightBorder = c.minX >= (w - 1 - borderMargin)
      val isAtBottomBorder = c.minY >= h - 4

      if (c !== mainComp) {
        val gapToMain = if (c.maxX < mainMinX) {
          mainMinX - c.maxX
        } else if (c.minX > mainMaxX) {
          c.minX - mainMaxX
        } else {
          0
        }

        // Check if component is predominantly in outer cell borders (e.g. printed box lines)
        val borderPixels = c.points.count { (px, py) ->
          px <= 3 || px >= w - 4 || py <= 2 || py >= h - 3
        }
        val borderRatio = borderPixels.toFloat() / c.size.toFloat()
        if (borderRatio >= 0.70f) {
          continue
        }

        // Drop isolated border line fragments separated from main handwriting
        if ((isAtLeftBorder || isAtRightBorder || isAtBottomBorder) && gapToMain >= 2 && c.size < (maxCompSize * 0.85f)) {
          continue
        }

        // Drop boundary line speckle
        if ((isAtLeftBorder || isAtRightBorder || isAtBottomBorder) && (c.width <= 3 || c.height <= 3)) {
          continue
        }
      } else {
        // Even if main component, if it's strictly a border artifact touching edge with no interior mass
        val isBorderLine = (isAtLeftBorder && c.maxX < (w * 0.40f).toInt()) ||
                           (isAtRightBorder && c.minX > (w * 0.60f).toInt())
        if (isBorderLine && c.width <= 4) {
          continue
        }
        if (isAtBottomBorder && c.height <= 3) {
          continue
        }
      }

      // Retain components with substantial mass
      if (c.size >= 6 || c.size >= (maxCompSize * 0.20f).toInt()) {
        retainedPoints.addAll(c.points)
      }
    }

    if (retainedPoints.size < EMPTY_CELL_INK_THRESHOLD) {
      return PreprocessCellResult(null, null, retainedPoints.size, isEmpty = true)
    }

    var minX = w
    var maxX = -1
    var minY = h
    var maxY = -1

    for ((px, py) in retainedPoints) {
      if (px < minX) minX = px
      if (px > maxX) maxX = px
      if (py < minY) minY = py
      if (py > maxY) maxY = py
    }

    val inkW = maxX - minX + 1
    val inkH = maxY - minY + 1

    if (inkW < MIN_STROKE_WIDTH || inkH < MIN_STROKE_HEIGHT) {
      return PreprocessCellResult(null, null, retainedPoints.size, isEmpty = true)
    }

    // 5. Extract normalized stroke intensity onto tight bitmap
    val inkBmp = Bitmap.createBitmap(inkW, inkH, Bitmap.Config.ARGB_8888)
    val inkPixels = IntArray(inkW * inkH) { Color.BLACK }
    var sumX = 0L
    var sumY = 0L
    var sumWeight = 0.0f

    for ((px, py) in retainedPoints) {
      val localX = px - minX
      val localY = py - minY
      val gray = grayArr[py * w + px]
      val intensity = ((inkThreshold - gray).toFloat() / 85.0f).coerceIn(0.45f, 1.0f)
      val byteVal = (intensity * 255.0f).toInt().coerceIn(60, 255)
      inkPixels[localY * inkW + localX] = Color.rgb(byteVal, byteVal, byteVal)

      sumX += (localX * intensity * 1000).toLong()
      sumY += (localY * intensity * 1000).toLong()
      sumWeight += intensity
    }
    inkBmp.setPixels(inkPixels, 0, inkW, 0, 0, inkW, inkH)

    // 6. Scale into 20x20 bounding box (preserving aspect ratio)
    val scale = 20.0f / max(inkW, inkH).toFloat()
    val scaledW = (inkW * scale).toInt().coerceIn(1, 20)
    val scaledH = (inkH * scale).toInt().coerceIn(1, 20)
    val scaledBmp = Bitmap.createScaledBitmap(inkBmp, scaledW, scaledH, true)

    // 7. Center-of-Mass alignment in 28x28
    val canvasBmp = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(canvasBmp)
    canvas.drawColor(Color.BLACK)

    val rawComX = if (sumWeight > 0f) (sumX.toFloat() / (sumWeight * 1000f)) * scale else scaledW / 2f
    val rawComY = if (sumWeight > 0f) (sumY.toFloat() / (sumWeight * 1000f)) * scale else scaledH / 2f

    val targetCom = 13.5f
    val optimalOffsetX = (targetCom - rawComX).toInt().coerceIn(0, 28 - scaledW)
    val optimalOffsetY = (targetCom - rawComY).toInt().coerceIn(0, 28 - scaledH)

    canvas.drawBitmap(scaledBmp, optimalOffsetX.toFloat(), optimalOffsetY.toFloat(), null)

    // 8. Build Float32 ByteBuffer [1, 28, 28, 1] & calculate telemetry
    val byteBuffer = ByteBuffer.allocateDirect(4 * 28 * 28)
    byteBuffer.order(ByteOrder.nativeOrder())

    val targetPixels = IntArray(28 * 28)
    canvasBmp.getPixels(targetPixels, 0, 28, 0, 0, 28, 28)

    var minVal = 1.0f
    var maxVal = 0.0f
    var nonZeroCount = 0
    var borderInkCount = 0
    var finalSumX = 0.0f
    var finalSumY = 0.0f
    var finalSumWeight = 0.0f

    for (targetY in 0 until 28) {
      for (targetX in 0 until 28) {
        val p = targetPixels[targetY * 28 + targetX]
        val grayVal = p and 0xFF
        val v = grayVal.toFloat() / 255.0f
        byteBuffer.putFloat(v)

        if (v < minVal) minVal = v
        if (v > maxVal) maxVal = v
        if (v > 0.05f) {
          nonZeroCount++
          finalSumX += targetX * v
          finalSumY += targetY * v
          finalSumWeight += v
        }

        if (v > 0.10f && (targetX == 0 || targetX == 27 || targetY == 0 || targetY == 27)) {
          borderInkCount++
        }
      }
    }
    byteBuffer.rewind()

    val fgPixelRatio = nonZeroCount.toFloat() / (28f * 28f)
    val borderContamination = borderInkCount.toFloat() / (4f * 28f)

    val finalComX = if (finalSumWeight > 0f) finalSumX / finalSumWeight else 14.0f
    val finalComY = if (finalSumWeight > 0f) finalSumY / finalSumWeight else 14.0f

    val comOffsetX = (finalComX - 14.0f) / 14.0f
    val comOffsetY = (finalComY - 14.0f) / 14.0f

    return PreprocessCellResult(
      tensorBuffer = byteBuffer,
      processedBitmap28x28 = canvasBmp,
      rawInkCount = retainedPoints.size,
      isEmpty = false,
      minVal = if (minVal > maxVal) 0.0f else minVal,
      maxVal = maxVal,
      fgPixelRatio = fgPixelRatio,
      bboxW = inkW,
      bboxH = inkH,
      comOffsetX = comOffsetX,
      comOffsetY = comOffsetY,
      borderContamination = borderContamination
    )
  }

  /**
   * Preprocesses a freehand field strip (City / School):
   * 1. Removes printed field label on the left.
   * 2. Trims qualification circles or printed borders on the right.
   * 3. Suppresses horizontal ruling / underline at the bottom.
   * 4. Normalizes contrast for high-accuracy OCR.
   */
  fun preprocessFreeHandwriting(
    stripBitmap: Bitmap,
    fieldType: FreeFieldType,
    inkThreshold: Int = DEFAULT_INK_THRESHOLD
  ): FreeHandwritingResult {
    val bW = stripBitmap.width
    val bH = stripBitmap.height

    if (bW < 10 || bH < 10) {
      return FreeHandwritingResult(stripBitmap, false, 0)
    }

    // Strip the printed field label from the left of the crop.
    // The CITY region (0.055–0.950 of 682px sheet = ~610px) always triggers,
    // but the SCHOOL region (0.055–0.720 = ~454px) was previously blocked by
    // the bW >= 500 guard, causing the printed "School / College" label to be
    // fed verbatim to ML Kit.  The fix: apply the ratio based on field type
    // regardless of absolute pixel width. Pre-cropped benchmark strips that
    // contain no label receive an innocuous cut through blank space on the left.
    val labelCutoffRatio = when (fieldType) {
      FreeFieldType.CITY   -> 0.20f
      FreeFieldType.SCHOOL -> 0.25f
    }

    val startX = (bW * labelCutoffRatio).toInt().coerceIn(0, bW - 1)
    val endX = bW - 1

    val subW = endX - startX + 1
    val subH = bH

    val pixels = IntArray(subW * subH)
    stripBitmap.getPixels(pixels, 0, subW, startX, 0, subW, subH)

    // 1. Identify ink and ruling lines
    val inkMask = BooleanArray(subW * subH)
    var inkCount = 0

    for (y in 0 until subH) {
      for (x in 0 until subW) {
        val p = pixels[y * subW + x]
        val gray = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
        if (gray < inkThreshold) {
          inkMask[y * subW + x] = true
          inkCount++
        }
      }
    }

    // Suppress underline in bottom 25% of height
    val botBandY = (subH * 0.75f).toInt()
    for (y in botBandY until subH) {
      var rowInk = 0
      for (x in 0 until subW) {
        if (inkMask[y * subW + x]) rowInk++
      }
      if (rowInk >= (subW * 0.45f).toInt()) {
        val y0 = max(0, y - 1)
        val y1 = min(subH - 1, y + 1)
        for (cy in y0..y1) {
          for (cx in 0 until subW) {
            inkMask[cy * subW + cx] = false
          }
        }
      }
    }

    // Find bounding box of remaining ink
    var minX = subW
    var maxX = -1
    var minY = subH
    var maxY = -1
    var validInkCount = 0

    for (y in 0 until subH) {
      for (x in 0 until subW) {
        if (inkMask[y * subW + x]) {
          validInkCount++
          if (x < minX) minX = x
          if (x > maxX) maxX = x
          if (y < minY) minY = y
          if (y > maxY) maxY = y
        }
      }
    }

    if (validInkCount < 15 || minX >= maxX || minY >= maxY) {
      // Return label-trimmed strip if no distinct ink found
      val trimmed = Bitmap.createBitmap(stripBitmap, startX, 0, subW, subH)
      return FreeHandwritingResult(trimmed, false, validInkCount)
    }

    // Pad bounding box
    val pad = 6
    val cropX = max(0, minX - pad)
    val cropY = max(0, minY - pad)
    val cropW = min(subW - cropX, (maxX - minX + 1) + 2 * pad)
    val cropH = min(subH - cropY, (maxY - minY + 1) + 2 * pad)

    val cropped = Bitmap.createBitmap(stripBitmap, startX + cropX, cropY, cropW, cropH)

    // Scale up for OCR clarity if too small (minimum height 60px)
    val finalBitmap = if (cropH < 60) {
      val scale = 60.0f / cropH.toFloat()
      val targetW = (cropW * scale).toInt().coerceAtLeast(1)
      val targetH = 60
      Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
    } else {
      cropped
    }

    return FreeHandwritingResult(finalBitmap, true, validInkCount)
  }
}
