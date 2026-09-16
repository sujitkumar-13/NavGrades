package com.example.omr.handwriting

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Android-integrated handwriting recognition engine for OMR boxed fields.
 * Supports:
 * - Letter recognition (26 classes, canonical A–Z) via omr_letter_baseline.tflite
 * - Digit recognition (10 classes, 0–9) via omr_digit_baseline.tflite
 * - Controlled Test Mode integration with detailed input verification telemetry
 */
object OmrHandwritingEngine {

  private const val TAG = "OMR_HANDWRITING"

  const val LETTER_MODEL_ASSET = "models/omr_letter_baseline.tflite"
  const val DIGIT_MODEL_ASSET  = "models/omr_digit_baseline.tflite"

  @Volatile
  private var letterInterpreter: Interpreter? = null

  @Volatile
  private var digitInterpreter: Interpreter? = null

  private val initLock = Any()

  /**
   * Initializes or loads the TFLite interpreters from assets.
   */
  fun initialize(context: Context): Boolean {
    return synchronized(initLock) {
      try {
        if (letterInterpreter == null) {
          val buffer = loadAssetBuffer(context.assets, LETTER_MODEL_ASSET)
          val options = Interpreter.Options().apply {
            setNumThreads(2)
          }
          letterInterpreter = Interpreter(buffer, options)
          Log.i(TAG, "Loaded letter model: $LETTER_MODEL_ASSET, input=${letterInterpreter?.getInputTensor(0)?.shape().contentToString()}, output=${letterInterpreter?.getOutputTensor(0)?.shape().contentToString()}")
        }
        if (digitInterpreter == null) {
          val buffer = loadAssetBuffer(context.assets, DIGIT_MODEL_ASSET)
          val options = Interpreter.Options().apply {
            setNumThreads(2)
          }
          digitInterpreter = Interpreter(buffer, options)
          Log.i(TAG, "Loaded digit model: $DIGIT_MODEL_ASSET, input=${digitInterpreter?.getInputTensor(0)?.shape().contentToString()}, output=${digitInterpreter?.getOutputTensor(0)?.shape().contentToString()}")
        }
        true
      } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize handwriting models: ${e.message}", e)
        false
      }
    }
  }

  /**
   * Directly sets custom interpreters (useful for testing and benchmarking).
   */
  fun setInterpreters(letterInterp: Interpreter?, digitInterp: Interpreter?) {
    synchronized(initLock) {
      letterInterpreter = letterInterp
      digitInterpreter = digitInterp
    }
  }

  fun isInitialized(): Boolean {
    return letterInterpreter != null && digitInterpreter != null
  }

  fun close() {
    synchronized(initLock) {
      try {
        letterInterpreter?.close()
        digitInterpreter?.close()
      } catch (e: Exception) {
        Log.w(TAG, "Error closing interpreters: ${e.message}")
      } finally {
        letterInterpreter = null
        digitInterpreter = null
      }
    }
  }

  /**
   * Helper to load asset file into direct ByteBuffer.
   */
  fun loadAssetBuffer(assets: AssetManager, assetPath: String): ByteBuffer {
    val inputStream: InputStream = assets.open(assetPath)
    val baos = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var bytesRead: Int
    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
      baos.write(buffer, 0, bytesRead)
    }
    val bytes = baos.toByteArray()
    val byteBuffer = ByteBuffer.allocateDirect(bytes.size)
    byteBuffer.order(ByteOrder.nativeOrder())
    byteBuffer.put(bytes)
    byteBuffer.rewind()
    return byteBuffer
  }

  /**
   * Slices and recognizes a boxed field (First Name, Last Name, Phone, WhatsApp).
   *
   * @param stripBitmap The cropped bitmap of the entire boxed strip.
   * @param numBoxes Number of boxes in this strip (23 for names, 10 for phones).
   * @param fieldName Name of the field for logging/audit.
   * @param isDigitField True if recognizing digits (0-9), false if letters (A-Z).
   */
  fun recognizeBoxedField(
    stripBitmap: Bitmap,
    numBoxes: Int,
    fieldName: String,
    isDigitField: Boolean
  ): Pair<String, FieldAudit> {
    val interp = if (isDigitField) digitInterpreter else letterInterpreter
    if (interp == null) {
      Log.w(TAG, "Interpreter for $fieldName (isDigit=$isDigitField) is not initialized!")
      return Pair("", FieldAudit(fieldName, numBoxes, "", numBoxes, 0, 0f, emptyList()))
    }

    val bW = stripBitmap.width
    val bH = stripBitmap.height
    val boxW = bW.toFloat() / numBoxes.toFloat()
    val numOutputs = interp.getOutputTensor(0).shape()[1]

    val cells = mutableListOf<CellAudit>()
    val reconstructed = StringBuilder()
    var emptyCount = 0
    var filledCount = 0
    var totalConfidence = 0.0f

    for (i in 0 until numBoxes) {
      val left = (i * boxW).toInt().coerceIn(0, bW - 1)
      val right = ((i + 1) * boxW).toInt().coerceIn(left + 1, bW)
      val w = right - left

      val cellCrop = Bitmap.createBitmap(stripBitmap, left, 0, w, bH)

      // Preprocess & calculate input telemetry
      val preprocResult = preprocessBoxCell(cellCrop, isDigitField = isDigitField)

      val cellAudit: CellAudit
      if (preprocResult.isEmpty || preprocResult.tensorBuffer == null) {
        emptyCount++
        cellAudit = CellAudit(
          fieldName = fieldName,
          boxIndex = i,
          rawInkCount = preprocResult.rawInkCount,
          isEmpty = true,
          recognizedChar = " ",
          confidence = 1.0f,
          top3Candidates = emptyList(),
          inputShape = listOf(1, 28, 28, 1),
          minVal = 0.0f,
          maxVal = 0.0f,
          fgPixelRatio = 0.0f,
          bboxWidth = 0,
          bboxHeight = 0,
          comOffsetX = 0.0f,
          comOffsetY = 0.0f,
          borderContamination = 0.0f
        )
        // Logical reconstruction: do NOT append space or placeholder for empty boxes
      } else {
        filledCount++
        val output = Array(1) { FloatArray(numOutputs) }
        interp.run(preprocResult.tensorBuffer, output)
        val probs = output[0]

        val sortedIndices = probs.indices.sortedByDescending { probs[it] }
        val bestIdx = sortedIndices[0]
        val conf = probs[bestIdx]

        val charStr = if (isDigitField) {
          bestIdx.toString()
        } else {
          ('A'.code + bestIdx).toChar().toString()
        }

        val top3 = sortedIndices.take(3).map { idx ->
          val label = if (isDigitField) idx.toString() else ('A'.code + idx).toChar().toString()
          label to probs[idx]
        }

        totalConfidence += conf
        reconstructed.append(charStr)

        cellAudit = CellAudit(
          fieldName = fieldName,
          boxIndex = i,
          rawInkCount = preprocResult.rawInkCount,
          isEmpty = false,
          recognizedChar = charStr,
          confidence = conf,
          top3Candidates = top3,
          inputShape = listOf(1, 28, 28, 1),
          minVal = preprocResult.minVal,
          maxVal = preprocResult.maxVal,
          fgPixelRatio = preprocResult.fgPixelRatio,
          bboxWidth = preprocResult.bboxW,
          bboxHeight = preprocResult.bboxH,
          comOffsetX = preprocResult.comOffsetX,
          comOffsetY = preprocResult.comOffsetY,
          borderContamination = preprocResult.borderContamination
        )
      }

      cells.add(cellAudit)
    }

    val finalReconstructed = reconstructed.toString().trimEnd()
    val avgConf = if (filledCount > 0) totalConfidence / filledCount else 1.0f

    val fieldAudit = FieldAudit(
      fieldName = fieldName,
      totalBoxes = numBoxes,
      recognizedText = finalReconstructed,
      emptyBoxCount = emptyCount,
      filledBoxCount = filledCount,
      avgConfidence = avgConf,
      cells = cells
    )

    return Pair(finalReconstructed, fieldAudit)
  }

  /**
   * Internal preprocessed box result containing direct tensor buffer and audit metrics.
   */
  data class PreprocessBoxResult(
    val tensorBuffer: ByteBuffer?,
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
   * Preprocesses a single character cell bitmap into a 28x28 normalized tensor.
   * Dispatches to either adaptive ruling-line suppression (default) or fixed-inset cropping.
   */
  fun preprocessBoxCell(
    boxCrop: Bitmap,
    isDigitField: Boolean,
    inkThreshold: Int = OmrHandwritingConfig.INK_GRAY_THRESHOLD,
    insetX: Int = OmrHandwritingConfig.INSET_X,
    insetY: Int = OmrHandwritingConfig.INSET_Y
  ): PreprocessBoxResult {
    return when (OmrHandwritingConfig.preprocessingMode) {
      OmrHandwritingConfig.PreprocessingMode.ADAPTIVE_RULING_SUPPRESSION -> {
        adaptivePreprocessBoxCell(boxCrop, isDigitField, inkThreshold)
      }
      OmrHandwritingConfig.PreprocessingMode.FIXED_INSET -> {
        preprocessBoxCellFixedInsets(boxCrop, isDigitField, inkThreshold, insetX, insetY)
      }
    }
  }

  /**
   * Adaptive ruling-line suppression and connected-component analysis.
   * Eliminates fixed 4px/5px insets, detects box rulings geometrically, and preserves
   * genuine handwriting strokes near cell boundaries.
   */
  fun adaptivePreprocessBoxCell(
    boxCrop: Bitmap,
    isDigitField: Boolean,
    inkThreshold: Int = 145
  ): PreprocessBoxResult {
    val w = boxCrop.width
    val h = boxCrop.height

    if (w < 4 || h < 4) {
      return PreprocessBoxResult(null, 0, isEmpty = true)
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

    if (initialInkCount < OmrHandwritingConfig.EMPTY_BOX_INK_THRESHOLD) {
      return PreprocessBoxResult(null, initialInkCount, isEmpty = true)
    }

    val cleanedMask = inkMask.clone()

    // 1. Detect & Suppress Horizontal Ruling Lines in top/bottom margins (outer 3-4 rows)
    val topBandH = min(4, max(2, (h * 0.12f).toInt()))
    val botBandY = max(h - 4, min(h - 2, (h * 0.88f).toInt()))
    val minHorizontalRun = (w * OmrHandwritingConfig.RULING_HORIZONTAL_RATIO).toInt().coerceAtLeast(6)

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
          // Clear this ruling row and immediate adjacent rows (+/- 1)
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

    // Always clear extreme outer border lines (top/bottom 1 row)
    for (x in 0 until w) {
      cleanedMask[0 * w + x] = false
      if (h > 1) cleanedMask[(h - 1) * w + x] = false
    }

    // 2. Outer Column Border Lines: clear outer columns if continuous vertical ink is significant (>= 70%)
    val outerColumns = listOf(0, 1, w - 2, w - 1).filter { it in 0 until w }.distinct()
    for (x in outerColumns) {
      var colInk = 0
      for (y in 0 until h) {
        if (cleanedMask[y * w + x]) colInk++
      }
      if (colInk >= (h * OmrHandwritingConfig.RULING_VERTICAL_RATIO).toInt().coerceAtLeast(5)) {
        for (y in 0 until h) {
          cleanedMask[y * w + x] = false
        }
      }
    }

    // 3. 8-Connected Component Analysis
    val visited = BooleanArray(w * h)
    data class Component(val points: List<Pair<Int, Int>>, val minX: Int, val maxX: Int, val minY: Int, val maxY: Int) {
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
      return PreprocessBoxResult(null, 0, isEmpty = true)
    }

    // 4. Component Selection & Border Noise Filtering
    val mainComp = components.maxByOrNull { it.size } ?: components[0]
    val maxCompSize = mainComp.size
    val mainMinX = mainComp.minX
    val mainMaxX = mainComp.maxX

    val retainedPoints = mutableListOf<Pair<Int, Int>>()

    for (c in components) {
      if (c !== mainComp) {
        val isAtLeftBorder = c.maxX <= 3
        val isAtRightBorder = c.minX >= w - 4

        // Gap to main component
        val gapToMain = if (c.maxX < mainMinX) {
          mainMinX - c.maxX
        } else if (c.minX > mainMaxX) {
          c.minX - mainMaxX
        } else {
          0
        }

        // Drop isolated border line fragments separated from main handwriting
        if ((isAtLeftBorder || isAtRightBorder) && gapToMain >= 2 && c.size < (maxCompSize * 0.70f)) {
          continue
        }

        // Drop tiny boundary line speckle (width <= 2 and height <= 3 touching edge)
        if ((isAtLeftBorder || isAtRightBorder) && (c.width <= 2 && c.height <= 3)) {
          continue
        }
      }

      // Retain components with substantial mass or interior position
      if (c.size >= 6 || c.size >= (maxCompSize * 0.20f).toInt()) {
        retainedPoints.addAll(c.points)
      }
    }

    if (retainedPoints.size < OmrHandwritingConfig.EMPTY_BOX_INK_THRESHOLD) {
      return PreprocessBoxResult(null, retainedPoints.size, isEmpty = true)
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

    // Minimum handwriting dimension check (reject residual horizontal or vertical rulings)
    if (inkW <= 1 || inkH <= 3) {
      return PreprocessBoxResult(null, retainedPoints.size, isEmpty = true)
    }

    // 5. Extract normalized stroke intensity
    val inkBmp = Bitmap.createBitmap(inkW, inkH, Bitmap.Config.ARGB_8888)
    val inkPixels = IntArray(inkW * inkH) { Color.BLACK }
    var sumX = 0L
    var sumY = 0L
    var sumWeight = 0.0f

    for ((px, py) in retainedPoints) {
      val localX = px - minX
      val localY = py - minY
      val gray = grayArr[py * w + px]
      val intensity = ((145 - gray).toFloat() / 85.0f).coerceIn(0.45f, 1.0f)
      val byteVal = (intensity * 255.0f).toInt().coerceIn(60, 255)
      inkPixels[localY * inkW + localX] = Color.rgb(byteVal, byteVal, byteVal)

      sumX += (localX * intensity * 1000).toLong()
      sumY += (localY * intensity * 1000).toLong()
      sumWeight += intensity
    }
    inkBmp.setPixels(inkPixels, 0, inkW, 0, 0, inkW, inkH)

    // 6. Scale into 20x20 bounding box
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

          if (targetX < 2 || targetX >= 26 || targetY < 2 || targetY >= 26) {
            borderInkCount++
          }
        }
      }
    }
    byteBuffer.rewind()

    val finalComX = if (finalSumWeight > 0f) finalSumX / finalSumWeight else 13.5f
    val finalComY = if (finalSumWeight > 0f) finalSumY / finalSumWeight else 13.5f
    val comOffX = finalComX - 13.5f
    val comOffY = finalComY - 13.5f

    val fgRatio = nonZeroCount.toFloat() / (28f * 28f)
    val borderContam = if (nonZeroCount > 0) borderInkCount.toFloat() / nonZeroCount.toFloat() else 0.0f

    return PreprocessBoxResult(
      tensorBuffer = byteBuffer,
      rawInkCount = retainedPoints.size,
      isEmpty = false,
      minVal = minVal.coerceAtLeast(0.0f),
      maxVal = maxVal,
      fgPixelRatio = fgRatio,
      bboxW = inkW,
      bboxH = inkH,
      comOffsetX = comOffX,
      comOffsetY = comOffY,
      borderContamination = borderContam
    )
  }

  /**
   * Reference fixed-inset preprocessing (used for comparison benchmarks).
   */
  fun preprocessBoxCellFixedInsets(
    boxCrop: Bitmap,
    isDigitField: Boolean,
    inkThreshold: Int = OmrHandwritingConfig.INK_GRAY_THRESHOLD,
    insetX: Int = OmrHandwritingConfig.INSET_X,
    insetY: Int = OmrHandwritingConfig.INSET_Y
  ): PreprocessBoxResult {
    val w = boxCrop.width
    val h = boxCrop.height

    val actualInsetX = insetX.coerceAtMost(w / 4)
    val actualInsetY = insetY.coerceAtMost(h / 4)
    val innerW = w - (2 * actualInsetX)
    val innerH = h - (2 * actualInsetY)

    if (innerW <= 0 || innerH <= 0) {
      return PreprocessBoxResult(null, 0, isEmpty = true)
    }

    val pixels = IntArray(innerW * innerH)
    boxCrop.getPixels(pixels, 0, innerW, actualInsetX, actualInsetY, innerW, innerH)

    var rawInkCount = 0
    var minX = innerW
    var maxX = -1
    var minY = innerH
    var maxY = -1
    var sumX = 0L
    var sumY = 0L
    var sumWeight = 0.0f

    val strokeIntensities = FloatArray(innerW * innerH)

    for (y in 0 until innerH) {
      for (x in 0 until innerW) {
        val p = pixels[y * innerW + x]
        val gray = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
        if (gray < inkThreshold) {
          rawInkCount++
          val intensity = ((145 - gray).toFloat() / 85.0f).coerceIn(0.5f, 1.0f)
          strokeIntensities[y * innerW + x] = intensity

          if (x < minX) minX = x
          if (x > maxX) maxX = x
          if (y < minY) minY = y
          if (y > maxY) maxY = y

          sumX += (x * intensity * 1000).toLong()
          sumY += (y * intensity * 1000).toLong()
          sumWeight += intensity
        }
      }
    }

    if (rawInkCount < OmrHandwritingConfig.EMPTY_BOX_INK_THRESHOLD || minX > maxX || minY > maxY) {
      return PreprocessBoxResult(null, rawInkCount, isEmpty = true)
    }

    val inkW = maxX - minX + 1
    val inkH = maxY - minY + 1

    val inkBmp = Bitmap.createBitmap(inkW, inkH, Bitmap.Config.ARGB_8888)
    val inkPixels = IntArray(inkW * inkH)
    for (y in 0 until inkH) {
      for (x in 0 until inkW) {
        val intensity = strokeIntensities[(minY + y) * innerW + (minX + x)]
        if (intensity > 0.0f) {
          val byteVal = (intensity * 255.0f).toInt().coerceIn(60, 255)
          inkPixels[y * inkW + x] = Color.rgb(byteVal, byteVal, byteVal)
        } else {
          inkPixels[y * inkW + x] = Color.BLACK
        }
      }
    }
    inkBmp.setPixels(inkPixels, 0, inkW, 0, 0, inkW, inkH)

    val scale = 20.0f / max(inkW, inkH).toFloat()
    val scaledW = (inkW * scale).toInt().coerceIn(1, 20)
    val scaledH = (inkH * scale).toInt().coerceIn(1, 20)
    val scaledBmp = Bitmap.createScaledBitmap(inkBmp, scaledW, scaledH, true)

    val canvasBmp = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(canvasBmp)
    canvas.drawColor(Color.BLACK)

    val rawComX = if (sumWeight > 0f) (sumX.toFloat() / (sumWeight * 1000f) - minX) * scale else scaledW / 2f
    val rawComY = if (sumWeight > 0f) (sumY.toFloat() / (sumWeight * 1000f) - minY) * scale else scaledH / 2f

    val targetCom = 13.5f
    val optimalOffsetX = (targetCom - rawComX).toInt().coerceIn(0, 28 - scaledW)
    val optimalOffsetY = (targetCom - rawComY).toInt().coerceIn(0, 28 - scaledH)

    canvas.drawBitmap(scaledBmp, optimalOffsetX.toFloat(), optimalOffsetY.toFloat(), null)

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

    for (y in 0 until 28) {
      for (x in 0 until 28) {
        val p = targetPixels[y * 28 + x]
        val grayVal = p and 0xFF
        val v = grayVal.toFloat() / 255.0f
        byteBuffer.putFloat(v)

        if (v < minVal) minVal = v
        if (v > maxVal) maxVal = v
        if (v > 0.05f) {
          nonZeroCount++
          finalSumX += x * v
          finalSumY += y * v
          finalSumWeight += v

          if (x < 2 || x >= 26 || y < 2 || y >= 26) {
            borderInkCount++
          }
        }
      }
    }
    byteBuffer.rewind()

    val finalComX = if (finalSumWeight > 0f) finalSumX / finalSumWeight else 13.5f
    val finalComY = if (finalSumWeight > 0f) finalSumY / finalSumWeight else 13.5f
    val comOffX = finalComX - 13.5f
    val comOffY = finalComY - 13.5f

    val fgRatio = nonZeroCount.toFloat() / (28f * 28f)
    val borderContam = if (nonZeroCount > 0) borderInkCount.toFloat() / nonZeroCount.toFloat() else 0.0f

    return PreprocessBoxResult(
      tensorBuffer = byteBuffer,
      rawInkCount = rawInkCount,
      isEmpty = false,
      minVal = minVal.coerceAtLeast(0.0f),
      maxVal = maxVal,
      fgPixelRatio = fgRatio,
      bboxW = inkW,
      bboxH = inkH,
      comOffsetX = comOffX,
      comOffsetY = comOffY,
      borderContamination = borderContam
    )
  }
}
