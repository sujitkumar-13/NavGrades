package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class TFLiteBoxedBenchmarkTest {

  companion object {
    private const val TAG = "TFLITE_BENCHMARK"

    // EMNIST Balanced 47 classes
    val EMNIST_CLASSES = arrayOf(
      "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
      "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
      "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
      "a", "b", "d", "e", "f", "g", "h", "n", "q", "r", "t"
    )

    // Mapping of lowercase EMNIST classes to uppercase
    val LOWER_TO_UPPER = mapOf(
      "a" to "A", "b" to "B", "d" to "D", "e" to "E", "f" to "F",
      "g" to "G", "h" to "H", "n" to "N", "q" to "Q", "r" to "R", "t" to "T"
    )
  }

  private fun loadModelBuffer(assetPath: String): ByteBuffer {
    val context = InstrumentationRegistry.getInstrumentation().context
    val inputStream: InputStream = context.assets.open(assetPath)
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

  private fun loadBitmap(name: String): Bitmap {
    val context = InstrumentationRegistry.getInstrumentation().context
    return context.assets.open("benchmark/$name").use { stream ->
      BitmapFactory.decodeStream(stream)
    }
  }

  /**
   * Preprocesses an individual box crop to 28x28 normalized tensor input.
   * Centers the ink bounding box into a 20x20 area within 28x28 with 4px border,
   * matching standard MNIST / EMNIST dataset formatting.
   */
  /**
   * Preprocesses an individual box cell to 28x28 normalized tensor input.
   * Background is strictly 0.0f (MNIST/EMNIST black).
   * Only ink strokes (gray < inkThreshold) are mapped to positive intensities (0.0 to 1.0).
   * Uses native bilinear scaling to smooth stroke anti-aliasing into a 20x20 area within 28x28.
   */
  private fun preprocessBoxTo28x28(
    boxCrop: Bitmap,
    transposeForEmnist: Boolean = false,
    inkThreshold: Int = 138
  ): Pair<ByteBuffer?, Int> {
    val w = boxCrop.width
    val h = boxCrop.height

    // Inset 4px horizontally, 5px vertically to completely exclude printed box ruling lines
    val insetX = 4.coerceAtMost(w / 4)
    val insetY = 5.coerceAtMost(h / 4)
    val innerW = w - (2 * insetX)
    val innerH = h - (2 * insetY)

    if (innerW <= 0 || innerH <= 0) {
      return Pair(null, 0)
    }

    val pixels = IntArray(innerW * innerH)
    boxCrop.getPixels(pixels, 0, innerW, insetX, insetY, innerW, innerH)


    // 1. Check if cell is an empty box
    var rawInkCount = 0
    for (y in 0 until innerH) {
      for (x in 0 until innerW) {
        val p = pixels[y * innerW + x]
        val gray = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
        if (gray < inkThreshold) {
          rawInkCount++
        }
      }
    }

    if (rawInkCount < 6) {
      return Pair(null, rawInkCount)
    }

    // 2. 1-pixel morphological dilation to connect broken strokes and achieve standard 2-3px MNIST stroke width
    val dilated = FloatArray(innerW * innerH)
    var minX = innerW
    var minY = innerH
    var maxX = -1
    var maxY = -1

    for (y in 0 until innerH) {
      for (x in 0 until innerW) {
        var maxIntensity = 0.0f
        for (dy in -1..1) {
          for (dx in -1..1) {
            val ny = y + dy
            val nx = x + dx
            if (ny in 0 until innerH && nx in 0 until innerW) {
              val p = pixels[ny * innerW + nx]
              val gray = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
              if (gray < inkThreshold) {
                val intensity = ((145 - gray).toFloat() / 85.0f).coerceIn(0.5f, 1.0f)
                if (intensity > maxIntensity) maxIntensity = intensity
              }
            }
          }
        }
        dilated[y * innerW + x] = maxIntensity
        if (maxIntensity > 0.0f) {
          if (x < minX) minX = x
          if (x > maxX) maxX = x
          if (y < minY) minY = y
          if (y > maxY) maxY = y
        }
      }
    }

    if (minX > maxX || minY > maxY) {
      return Pair(null, rawInkCount)
    }

    val inkW = maxX - minX + 1
    val inkH = maxY - minY + 1

    // 3. Create isolated ink mask bitmap
    val inkBmp = Bitmap.createBitmap(inkW, inkH, Bitmap.Config.ARGB_8888)
    val inkPixels = IntArray(inkW * inkH)

    for (y in 0 until inkH) {
      for (x in 0 until inkW) {
        val intensity = dilated[(minY + y) * innerW + (minX + x)]
        if (intensity > 0.0f) {
          val byteVal = (intensity * 255.0f).toInt().coerceIn(60, 255)
          inkPixels[y * inkW + x] = Color.rgb(byteVal, byteVal, byteVal)
        } else {
          inkPixels[y * inkW + x] = Color.BLACK
        }
      }
    }
    inkBmp.setPixels(inkPixels, 0, inkW, 0, 0, inkW, inkH)

    // 4. Scale to fit inside 20x20 keeping aspect ratio with bilinear filtering
    val scale = 20.0f / maxOf(inkW, inkH).toFloat()
    val scaledW = (inkW * scale).toInt().coerceIn(1, 20)
    val scaledH = (inkH * scale).toInt().coerceIn(1, 20)
    val scaledBmp = Bitmap.createScaledBitmap(inkBmp, scaledW, scaledH, true)

    // 5. Center in 28x28 black canvas
    val canvasBmp = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(canvasBmp)
    canvas.drawColor(Color.BLACK)

    val offsetX = (28 - scaledW) / 2
    val offsetY = (28 - scaledH) / 2
    canvas.drawBitmap(scaledBmp, offsetX.toFloat(), offsetY.toFloat(), null)

    // 6. Convert to ByteBuffer (1 x 28 x 28 x 1, Float32)
    val byteBuffer = ByteBuffer.allocateDirect(4 * 28 * 28)
    byteBuffer.order(ByteOrder.nativeOrder())

    val targetPixels = IntArray(28 * 28)
    canvasBmp.getPixels(targetPixels, 0, 28, 0, 0, 28, 28)

    for (y in 0 until 28) {
      for (x in 0 until 28) {
        val srcX = if (transposeForEmnist) y else x
        val srcY = if (transposeForEmnist) x else y
        val p = targetPixels[srcY * 28 + srcX]
        val grayVal = p and 0xFF
        val v = grayVal.toFloat() / 255.0f
        byteBuffer.putFloat(v)
      }
    }

    return Pair(byteBuffer, rawInkCount)
  }

  data class BoxClassification(
    val boxIndex: Int,
    val recognizedChar: String,
    val confidence: Float,
    val inkRatio: Float,
    val isEmpty: Boolean
  )

  enum class CenteringMethod {
    BOUNDING_BOX,
    CENTER_OF_MASS_INT,
    CENTER_OF_MASS_FLOAT
  }

  data class PreprocessConfig(
    val id: String,
    val name: String,
    val centering: CenteringMethod,
    val dilationRadius: Int,
    val targetCom: Float = 13.5f,
    val inkThreshold: Int = 138,
    val insetX: Int = 4,
    val insetY: Int = 5
  )

  data class CellDetail(
    val boxIndex: Int,
    val expected: String,
    val recognized: String,
    val isCorrect: Boolean,
    val confidence: Float,
    val inkCount: Int,
    val isEmpty: Boolean,
    val top3: String
  )

  data class FieldEvaluationResult(
    val fieldName: String,
    val configId: String,
    val groundTruth: String,
    val reconstructed: String,
    val isFullMatch: Boolean,
    val correctChars: Int,
    val totalChars: Int,
    val charAccuracy: Float,
    val correctEmpty: Int,
    val totalEmpty: Int,
    val emptyAccuracy: Float,
    val cells: List<CellDetail>,
    val confusions: List<String>,
    val avgLatencyMs: Float = 0.0f
  )

  private fun preprocessBoxTo28x28WithConfig(
    boxCrop: Bitmap,
    config: PreprocessConfig,
    transposeForEmnist: Boolean = false
  ): Pair<ByteBuffer?, Int> {
    val w = boxCrop.width
    val h = boxCrop.height

    val insetX = config.insetX.coerceAtMost(w / 4)
    val insetY = config.insetY.coerceAtMost(h / 4)
    val innerW = w - (2 * insetX)
    val innerH = h - (2 * insetY)

    if (innerW <= 0 || innerH <= 0) {
      return Pair(null, 0)
    }

    val pixels = IntArray(innerW * innerH)
    boxCrop.getPixels(pixels, 0, innerW, insetX, insetY, innerW, innerH)

    // 1. Empty box detection using raw ink threshold
    var rawInkCount = 0
    for (y in 0 until innerH) {
      for (x in 0 until innerW) {
        val p = pixels[y * innerW + x]
        val gray = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
        if (gray < config.inkThreshold) {
          rawInkCount++
        }
      }
    }

    if (rawInkCount < 6) {
      return Pair(null, rawInkCount)
    }

    // 2. Morphological dilation
    val dilated = FloatArray(innerW * innerH)
    var minX = innerW
    var minY = innerH
    var maxX = -1
    var maxY = -1

    val r = config.dilationRadius
    for (y in 0 until innerH) {
      for (x in 0 until innerW) {
        var maxIntensity = 0.0f
        if (r == 0) {
          val p = pixels[y * innerW + x]
          val gray = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
          if (gray < config.inkThreshold) {
            maxIntensity = ((145 - gray).toFloat() / 85.0f).coerceIn(0.5f, 1.0f)
          }
        } else {
          for (dy in -r..r) {
            for (dx in -r..r) {
              val ny = y + dy
              val nx = x + dx
              if (ny in 0 until innerH && nx in 0 until innerW) {
                val p = pixels[ny * innerW + nx]
                val gray = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
                if (gray < config.inkThreshold) {
                  val intensity = ((145 - gray).toFloat() / 85.0f).coerceIn(0.5f, 1.0f)
                  if (intensity > maxIntensity) maxIntensity = intensity
                }
              }
            }
          }
        }
        dilated[y * innerW + x] = maxIntensity
        if (maxIntensity > 0.0f) {
          if (x < minX) minX = x
          if (x > maxX) maxX = x
          if (y < minY) minY = y
          if (y > maxY) maxY = y
        }
      }
    }

    if (minX > maxX || minY > maxY) {
      return Pair(null, rawInkCount)
    }

    val inkW = maxX - minX + 1
    val inkH = maxY - minY + 1

    // 3. Create isolated ink mask bitmap
    val inkBmp = Bitmap.createBitmap(inkW, inkH, Bitmap.Config.ARGB_8888)
    val inkPixels = IntArray(inkW * inkH)

    for (y in 0 until inkH) {
      for (x in 0 until inkW) {
        val intensity = dilated[(minY + y) * innerW + (minX + x)]
        if (intensity > 0.0f) {
          val byteVal = (intensity * 255.0f).toInt().coerceIn(60, 255)
          inkPixels[y * inkW + x] = Color.rgb(byteVal, byteVal, byteVal)
        } else {
          inkPixels[y * inkW + x] = Color.BLACK
        }
      }
    }
    inkBmp.setPixels(inkPixels, 0, inkW, 0, 0, inkW, inkH)

    // 4. Scale to fit inside 20x20 keeping aspect ratio with bilinear filtering
    val scale = 20.0f / maxOf(inkW, inkH).toFloat()
    val scaledW = (inkW * scale).toInt().coerceIn(1, 20)
    val scaledH = (inkH * scale).toInt().coerceIn(1, 20)
    val scaledBmp = Bitmap.createScaledBitmap(inkBmp, scaledW, scaledH, true)

    // 5. Centering onto 28x28 black canvas
    val canvasBmp = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(canvasBmp)
    canvas.drawColor(Color.BLACK)

    val paint = Paint().apply {
      isFilterBitmap = true
      isAntiAlias = true
    }

    val (offsetX, offsetY) = when (config.centering) {
      CenteringMethod.BOUNDING_BOX -> {
        val ox = ((28 - scaledW) / 2).toFloat()
        val oy = ((28 - scaledH) / 2).toFloat()
        Pair(ox, oy)
      }
      CenteringMethod.CENTER_OF_MASS_INT, CenteringMethod.CENTER_OF_MASS_FLOAT -> {
        val sPixels = IntArray(scaledW * scaledH)
        scaledBmp.getPixels(sPixels, 0, scaledW, 0, 0, scaledW, scaledH)

        var totalMass = 0.0f
        var sumX = 0.0f
        var sumY = 0.0f
        for (sy in 0 until scaledH) {
          for (sx in 0 until scaledW) {
            val p = sPixels[sy * scaledW + sx]
            val mass = (p and 0xFF).toFloat() / 255.0f
            if (mass > 0f) {
              totalMass += mass
              sumX += sx * mass
              sumY += sy * mass
            }
          }
        }

        if (totalMass > 0f) {
          val comX = sumX / totalMass
          val comY = sumY / totalMass

          val rawOx = config.targetCom - comX
          val rawOy = config.targetCom - comY

          val clampedOx = rawOx.coerceIn(0.0f, (28 - scaledW).toFloat())
          val clampedOy = rawOy.coerceIn(0.0f, (28 - scaledH).toFloat())

          if (config.centering == CenteringMethod.CENTER_OF_MASS_INT) {
            Pair(Math.round(clampedOx).toFloat(), Math.round(clampedOy).toFloat())
          } else {
            Pair(clampedOx, clampedOy)
          }
        } else {
          Pair(((28 - scaledW) / 2).toFloat(), ((28 - scaledH) / 2).toFloat())
        }
      }
    }

    canvas.drawBitmap(scaledBmp, offsetX, offsetY, paint)

    // 6. Convert to ByteBuffer (1 x 28 x 28 x 1, Float32)
    val byteBuffer = ByteBuffer.allocateDirect(4 * 28 * 28)
    byteBuffer.order(ByteOrder.nativeOrder())

    val targetPixels = IntArray(28 * 28)
    canvasBmp.getPixels(targetPixels, 0, 28, 0, 0, 28, 28)

    for (y in 0 until 28) {
      for (x in 0 until 28) {
        val srcX = if (transposeForEmnist) y else x
        val srcY = if (transposeForEmnist) x else y
        val p = targetPixels[srcY * 28 + srcX]
        val grayVal = p and 0xFF
        val v = grayVal.toFloat() / 255.0f
        byteBuffer.putFloat(v)
      }
    }

    return Pair(byteBuffer, rawInkCount)
  }

  private fun evaluateBoxedFieldWithConfig(
    fieldName: String,
    bitmap: Bitmap,
    numBoxes: Int,
    expectedNonEmpty: String,
    restrictToDigits: Boolean,
    interpreter: Interpreter,
    config: PreprocessConfig,
    transposeForEmnist: Boolean = false
  ): FieldEvaluationResult {
    val bW = bitmap.width
    val bH = bitmap.height
    val boxW = bW.toFloat() / numBoxes.toFloat()
    val numOutputs = interpreter.getOutputTensor(0).shape()[1]

    val cells = mutableListOf<CellDetail>()
    val confusions = mutableListOf<String>()
    val reconstructed = StringBuilder()

    var correctNonEmptyChars = 0
    var correctEmptyBoxes = 0
    val totalExpectedEmpty = numBoxes - expectedNonEmpty.length
    var totalInferenceNanos = 0L
    var inferenceCount = 0

    for (i in 0 until numBoxes) {
      val left = (i * boxW).toInt().coerceIn(0, bW - 1)
      val right = ((i + 1) * boxW).toInt().coerceIn(left + 1, bW)
      val w = right - left

      val cellCrop = Bitmap.createBitmap(bitmap, left, 0, w, bH)

      val (inputBuffer, inkCount) = preprocessBoxTo28x28WithConfig(
        cellCrop,
        config,
        transposeForEmnist = transposeForEmnist
      )

      val isEmpty = (inputBuffer == null)
      val recognizedChar: String
      val confidence: Float
      val top3Info = StringBuilder()

      if (isEmpty) {
        recognizedChar = " "
        confidence = 1.0f
        if (i >= expectedNonEmpty.length) {
          correctEmptyBoxes++
        }
      } else {
        val output = Array(1) { FloatArray(numOutputs) }
        val startNano = System.nanoTime()
        interpreter.run(inputBuffer, output)
        val endNano = System.nanoTime()
        totalInferenceNanos += (endNano - startNano)
        inferenceCount++

        val probs = output[0]

        if (numOutputs == 10) {
          val sortedIndices = (0 until 10).sortedByDescending { probs[it] }
          val bestIdx = sortedIndices[0]
          recognizedChar = bestIdx.toString()
          confidence = probs[bestIdx]
          for (k in 0 until minOf(3, sortedIndices.size)) {
            val idx = sortedIndices[k]
            top3Info.append("${idx}(${String.format("%.1f%%", probs[idx] * 100)}) ")
          }
        } else if (numOutputs == 36) {
          // Custom 36-Class OMR Model: 0..9 (digits 0-9), 10..35 (letters A-Z)
          if (restrictToDigits) {
            val digitIndices = (0..9).sortedByDescending { probs[it] }
            val bestIdx = digitIndices[0]
            recognizedChar = bestIdx.toString()
            confidence = probs[bestIdx]
            for (k in 0 until minOf(3, digitIndices.size)) {
              val idx = digitIndices[k]
              top3Info.append("${idx}(${String.format("%.1f%%", probs[idx] * 100)}) ")
            }
          } else {
            val letterIndices = (10..35).sortedByDescending { probs[it] }
            val bestIdx = letterIndices[0]
            val letterChar = ('A'.code + (bestIdx - 10)).toChar().toString()
            recognizedChar = letterChar
            confidence = probs[bestIdx]
            for (k in 0 until minOf(3, letterIndices.size)) {
              val idx = letterIndices[k]
              val ch = ('A'.code + (idx - 10)).toChar()
              top3Info.append("${ch}(${String.format("%.1f%%", probs[idx] * 100)}) ")
            }
          }
        } else {
          if (restrictToDigits) {
            val digitIndices = (0..9).sortedByDescending { probs[it] }
            val bestIdx = digitIndices[0]
            recognizedChar = EMNIST_CLASSES[bestIdx]
            confidence = probs[bestIdx]
            for (k in 0 until minOf(3, digitIndices.size)) {
              val idx = digitIndices[k]
              top3Info.append("${EMNIST_CLASSES[idx]}(${String.format("%.1f%%", probs[idx] * 100)}) ")
            }
          } else {
            val letterIndices = (10 until 47).sortedByDescending { probs[it] }
            val bestIdx = letterIndices[0]
            val rawChar = EMNIST_CLASSES[bestIdx]
            recognizedChar = LOWER_TO_UPPER[rawChar] ?: rawChar.uppercase()
            confidence = probs[bestIdx]
            for (k in 0 until minOf(3, letterIndices.size)) {
              val idx = letterIndices[k]
              val c = EMNIST_CLASSES[idx]
              val uc = LOWER_TO_UPPER[c] ?: c.uppercase()
              top3Info.append("${uc}(${String.format("%.1f%%", probs[idx] * 100)}) ")
            }
          }
        }

        reconstructed.append(recognizedChar)

        if (i < expectedNonEmpty.length) {
          val expectedChar = expectedNonEmpty[i].toString()
          if (recognizedChar.equals(expectedChar, ignoreCase = true)) {
            correctNonEmptyChars++
          } else {
            confusions.add("Box ${i + 1}: expected '$expectedChar', got '$recognizedChar' (${String.format("%.1f%%", confidence * 100)}) top3=[${top3Info.toString().trim()}]")
          }
        }
      }

      val expectedCharForCell = if (i < expectedNonEmpty.length) expectedNonEmpty[i].toString() else " "
      val isCellCorrect = if (i < expectedNonEmpty.length) {
        recognizedChar.equals(expectedCharForCell, ignoreCase = true)
      } else {
        isEmpty
      }

      cells.add(
        CellDetail(
          boxIndex = i,
          expected = expectedCharForCell,
          recognized = recognizedChar,
          isCorrect = isCellCorrect,
          confidence = confidence,
          inkCount = inkCount,
          isEmpty = isEmpty,
          top3 = top3Info.toString().trim()
        )
      )
    }

    val finalString = reconstructed.toString().trim()
    val charAccuracy = if (expectedNonEmpty.isNotEmpty()) {
      (correctNonEmptyChars.toFloat() / expectedNonEmpty.length.toFloat()) * 100f
    } else 100f

    val emptyAccuracy = if (totalExpectedEmpty > 0) {
      (correctEmptyBoxes.toFloat() / totalExpectedEmpty.toFloat()) * 100f
    } else 100f

    val isFullMatch = finalString.equals(expectedNonEmpty, ignoreCase = true)
    val avgLatencyMs = if (inferenceCount > 0) {
      (totalInferenceNanos.toFloat() / inferenceCount.toFloat()) / 1_000_000.0f
    } else 0.0f

    return FieldEvaluationResult(
      fieldName = fieldName,
      configId = config.id,
      groundTruth = expectedNonEmpty,
      reconstructed = finalString,
      isFullMatch = isFullMatch,
      correctChars = correctNonEmptyChars,
      totalChars = expectedNonEmpty.length,
      charAccuracy = charAccuracy,
      correctEmpty = correctEmptyBoxes,
      totalEmpty = totalExpectedEmpty,
      emptyAccuracy = emptyAccuracy,
      cells = cells,
      confusions = confusions,
      avgLatencyMs = avgLatencyMs
    )
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Helper: build a raw ByteBuffer directly from a 28x28 float grid
  // Bypasses all of our inference preprocessing to isolate model behaviour.
  // ─────────────────────────────────────────────────────────────────────────
  private fun gridToBuffer(grid: Array<FloatArray>, transpose: Boolean = false): ByteBuffer {
    val buf = ByteBuffer.allocateDirect(4 * 28 * 28)
    buf.order(ByteOrder.nativeOrder())
    for (y in 0 until 28) {
      for (x in 0 until 28) {
        val v = if (transpose) grid[x][y] else grid[y][x]
        buf.putFloat(v)
      }
    }
    return buf
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Synthetic glyph builders.  All return grid[y][x] with ink=1.0, bg=0.0
  // ─────────────────────────────────────────────────────────────────────────

  /** Digit '1' — thin 3-pixel vertical bar centred, with small top-serif */
  private fun syntheticOne(): Array<FloatArray> {
    val g = Array(28) { FloatArray(28) }
    for (y in 5..22) {
      g[y][13] = 1.0f; g[y][14] = 1.0f
    }
    // top-left serif
    g[5][11] = 0.8f; g[5][12] = 1.0f
    return g
  }

  /** Digit '0' — thick oval ring */
  private fun syntheticZero(): Array<FloatArray> {
    val g = Array(28) { FloatArray(28) }
    for (y in 5..22) {
      g[y][9] = 1.0f; g[y][10] = 1.0f
      g[y][17] = 1.0f; g[y][18] = 1.0f
    }
    for (x in 10..16) {
      g[5][x] = 1.0f; g[6][x] = 1.0f
      g[21][x] = 1.0f; g[22][x] = 1.0f
    }
    return g
  }

  /** Letter 'I' — identical structure to syntheticOne but letter class */
  private fun syntheticI(): Array<FloatArray> = syntheticOne()

  /** Letter 'O' — identical structure to syntheticZero */
  private fun syntheticO(): Array<FloatArray> = syntheticZero()

  /** All-background (empty) — 0.0 everywhere */
  private fun syntheticEmpty(): Array<FloatArray> = Array(28) { FloatArray(28) }

  /** All-foreground (fully filled) — 1.0 everywhere */
  private fun syntheticFull(): Array<FloatArray> = Array(28) { FloatArray(28) { 1.0f } }

  /** Inverted '1' — ink=0.0, background=1.0 (polarity test) */
  private fun syntheticOneInverted(): Array<FloatArray> {
    val base = syntheticOne()
    return Array(28) { y -> FloatArray(28) { x -> 1.0f - base[y][x] } }
  }

  /** Helper: log top-5 classes from EMNIST 47-class output */
  private fun logEmnistTop5(tag: String, probs: FloatArray): Pair<String, Float> {
    val indexed = probs.mapIndexed { i, v -> i to v }.sortedByDescending { it.second }
    Log.i(TAG, "  $tag top-5:")
    for (k in 0 until minOf(5, indexed.size)) {
      val (idx, conf) = indexed[k]
      val label = if (idx < EMNIST_CLASSES.size) EMNIST_CLASSES[idx] else "?"
      val displayLabel = LOWER_TO_UPPER[label] ?: label
      Log.i(TAG, "    #${k + 1}: idx=$idx label='$displayLabel' conf=${String.format("%.2f%%", conf * 100)}")
    }
    val best = indexed[0]
    val bestLabel = if (best.first < EMNIST_CLASSES.size) EMNIST_CLASSES[best.first] else "?"
    return Pair(LOWER_TO_UPPER[bestLabel] ?: bestLabel, best.second)
  }

  /** Helper: log top-5 classes from MNIST 10-class output */
  private fun logMnistTop5(tag: String, probs: FloatArray): Pair<String, Float> {
    val indexed = probs.mapIndexed { i, v -> i to v }.sortedByDescending { it.second }
    Log.i(TAG, "  $tag top-5:")
    for (k in 0 until minOf(5, indexed.size)) {
      val (idx, conf) = indexed[k]
      Log.i(TAG, "    #${k + 1}: digit='$idx' conf=${String.format("%.2f%%", conf * 100)}")
    }
    val best = indexed[0]
    return Pair(best.first.toString(), best.second)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // STEP 1: Preprocessing & Model Compatibility Audit
  // Resolves Q1 (normalization), Q2 (polarity), Q3 (centering),
  // Q4 (stroke thickness), Q5 (label mapping)
  // ─────────────────────────────────────────────────────────────────────────
  @Test
  fun testPreprocessingCompatibility() {
    Log.i(TAG, "╔═══════════════════════════════════════════════════════╗")
    Log.i(TAG, "║   STEP 1: PREPROCESSING COMPATIBILITY AUDIT (Q1-Q5)  ║")
    Log.i(TAG, "╚═══════════════════════════════════════════════════════╝")

    val emnistInterp = Interpreter(loadModelBuffer("models/emnist.tflite"))
    val mnistInterp  = Interpreter(loadModelBuffer("models/mnist.tflite"))

    val emnistOut = Array(1) { FloatArray(47) }
    val mnistOut  = Array(1) { FloatArray(10) }

    // ─────────────────────────────────────────────────────────────────────
    // Q1: Normalization formula — does /255.0 produce the right answer for
    //     a synthetic MNIST-style digit?
    //     PASS criterion: MNIST model confidence for digit '1' >= 70%
    // ─────────────────────────────────────────────────────────────────────
    Log.i(TAG, "")
    Log.i(TAG, "── Q1: Normalization (input range [0,1] via /255.0) ──")
    Log.i(TAG, "   Testing synthetic digit '1' (ink=1.0, bg=0.0)")

    val one = syntheticOne()
    mnistInterp.run(gridToBuffer(one), mnistOut)
    val (q1MnistPred, q1MnistConf) = logMnistTop5("MNIST synthetic '1'", mnistOut[0])
    val q1MnistPass = q1MnistConf >= 0.70f && q1MnistPred == "1"
    Log.i(TAG, "   Q1 MNIST result: pred='$q1MnistPred' conf=${String.format("%.1f%%", q1MnistConf * 100)} → ${if (q1MnistPass) "✓ PASS (normalization /255 is compatible)" else "✗ FAIL (normalization may be wrong)"}")

    emnistInterp.run(gridToBuffer(one, transpose = false), emnistOut)
    val (q1EmnistPred, q1EmnistConf) = logEmnistTop5("EMNIST synthetic '1' (normal)", emnistOut[0])
    val q1EmnistPass = q1EmnistConf >= 0.70f && q1EmnistPred == "1"
    Log.i(TAG, "   Q1 EMNIST normal: pred='$q1EmnistPred' conf=${String.format("%.1f%%", q1EmnistConf * 100)} → ${if (q1EmnistPass) "✓ PASS" else "✗ FAIL"}")

    // ─────────────────────────────────────────────────────────────────────
    // Q2: Polarity — does inverting (ink=0, bg=1) break the model?
    //     PASS criterion: Inverted '1' should score poorly for digit '1'
    //     (i.e., the model expects white-on-black / ink=1.0)
    // ─────────────────────────────────────────────────────────────────────
    Log.i(TAG, "")
    Log.i(TAG, "── Q2: Polarity (ink=1.0/bg=0.0 vs inverted ink=0.0/bg=1.0) ──")

    val oneInv = syntheticOneInverted()
    mnistInterp.run(gridToBuffer(oneInv), mnistOut)
    val (q2InvPred, q2InvConf) = logMnistTop5("MNIST inverted '1'", mnistOut[0])
    val q2InvFails = !(q2InvConf >= 0.70f && q2InvPred == "1")
    Log.i(TAG, "   Q2 inverted '1' MNIST: pred='$q2InvPred' conf=${String.format("%.1f%%", q2InvConf * 100)}")
    Log.i(TAG, "   Q2 Polarity check: ${if (q2InvFails) "✓ PASS — inverted polarity scores poorly, confirming ink=1.0 is correct" else "⚠ WARN — inverted polarity also scores high; model may be polarity-insensitive"}")

    // Also test empty and full grids to understand baseline model bias
    mnistInterp.run(gridToBuffer(syntheticEmpty()), mnistOut)
    val (emptyPred, emptyConf) = logMnistTop5("MNIST all-zero (empty)", mnistOut[0])
    Log.i(TAG, "   Q2 All-zero input: pred='$emptyPred' conf=${String.format("%.1f%%", emptyConf * 100)} (shows model bias on blank input)")

    mnistInterp.run(gridToBuffer(syntheticFull()), mnistOut)
    val (fullPred, fullConf) = logMnistTop5("MNIST all-one (solid fill)", mnistOut[0])
    Log.i(TAG, "   Q2 All-one input:  pred='$fullPred' conf=${String.format("%.1f%%", fullConf * 100)} (shows model bias on fully filled input)")

    // ─────────────────────────────────────────────────────────────────────
    // Q3: Label mapping — verify specific class indices for key characters
    //     Feed synthetic '0', '1', and EMNIST 'I' (index 18) vs '1' (index 1)
    //     PASS criterion: The correct class index wins
    // ─────────────────────────────────────────────────────────────────────
    Log.i(TAG, "")
    Log.i(TAG, "── Q3: Label Mapping — verify EMNIST class index ordering ──")

    // Digit '1' should hit EMNIST index 1 (not index 18 which is 'I')
    emnistInterp.run(gridToBuffer(one), emnistOut)
    val digit1ConfAtIdx1  = emnistOut[0][1]
    val digit1ConfAtIdx18 = emnistOut[0][18] // 'I'
    Log.i(TAG, "   Synthetic digit '1': confidence at idx=1(digit'1')=${String.format("%.2f%%", digit1ConfAtIdx1 * 100)}  at idx=18(letter'I')=${String.format("%.2f%%", digit1ConfAtIdx18 * 100)}")
    Log.i(TAG, "   Q3 Digit '1' mapping: ${if (digit1ConfAtIdx1 >= digit1ConfAtIdx18) "✓ PASS — digit index 1 wins over letter I" else "✗ FAIL — digit '1' is being classified as letter 'I'"}")

    // Digit '0' should hit EMNIST index 0 (not index 24 'O')
    emnistInterp.run(gridToBuffer(syntheticZero()), emnistOut)
    val digit0ConfAtIdx0  = emnistOut[0][0]
    val digit0ConfAtIdx24 = emnistOut[0][24] // 'O'
    Log.i(TAG, "   Synthetic digit '0': confidence at idx=0(digit'0')=${String.format("%.2f%%", digit0ConfAtIdx0 * 100)}  at idx=24(letter'O')=${String.format("%.2f%%", digit0ConfAtIdx24 * 100)}")
    Log.i(TAG, "   Q3 Digit '0' vs 'O' mapping: ${if (digit0ConfAtIdx0 >= digit0ConfAtIdx24) "✓ PASS — digit '0' wins over letter 'O'" else "✗ FAIL — digit '0' is being classified as letter 'O'"}")

    // ─────────────────────────────────────────────────────────────────────
    // Q4: Stroke thickness — compare 1px vs 3px stroke for digit '1'
    //     Hypothesis: thicker strokes produce higher MNIST/EMNIST confidence
    // ─────────────────────────────────────────────────────────────────────
    Log.i(TAG, "")
    Log.i(TAG, "── Q4: Stroke Thickness — 1px vs 3px vertical bar ──")

    // 1-pixel stroke (minimum, as produced by thin pen)
    val thin1 = Array(28) { FloatArray(28) }
    for (y in 5..22) thin1[y][14] = 1.0f

    // 3-pixel stroke (MNIST typical)
    val thick3 = Array(28) { FloatArray(28) }
    for (y in 5..22) { thick3[y][13] = 1.0f; thick3[y][14] = 1.0f; thick3[y][15] = 1.0f }

    mnistInterp.run(gridToBuffer(thin1), mnistOut)
    val (thinPred, thinConf) = logMnistTop5("MNIST thin '1' (1px)", mnistOut[0])
    Log.i(TAG, "   Q4 Thin 1px stroke:  pred='$thinPred' conf=${String.format("%.1f%%", thinConf * 100)}")

    mnistInterp.run(gridToBuffer(thick3), mnistOut)
    val (thickPred, thickConf) = logMnistTop5("MNIST thick '1' (3px)", mnistOut[0])
    Log.i(TAG, "   Q4 Thick 3px stroke: pred='$thickPred' conf=${String.format("%.1f%%", thickConf * 100)}")
    Log.i(TAG, "   Q4 Thickness effect: thin=${String.format("%.1f%%", thinConf * 100)} vs thick=${String.format("%.1f%%", thickConf * 100)} → confidence ${if (thickConf > thinConf) "INCREASED with thicker stroke" else if (thinConf > thickConf) "DECREASED with thicker stroke" else "unchanged"}")

    // ─────────────────────────────────────────────────────────────────────
    // Q5: Centering — bounding-box vs center-of-mass
    //     Test: digit '1' placed at top-left corner vs centered
    //     Hypothesis: centered placement produces higher confidence
    // ─────────────────────────────────────────────────────────────────────
    Log.i(TAG, "")
    Log.i(TAG, "── Q5: Centering — bounding-box center vs center-of-mass ──")

    // Centred: bar at columns 13-14, rows 4-23 (centroid ~row 13, col 13)
    val centred = Array(28) { FloatArray(28) }
    for (y in 4..23) { centred[y][13] = 1.0f; centred[y][14] = 1.0f }

    // Top-left shifted: same bar but starting at row 2, column 2
    val topLeft = Array(28) { FloatArray(28) }
    for (y in 2..21) { topLeft[y][2] = 1.0f; topLeft[y][3] = 1.0f }

    // Bottom-right shifted: bar near the bottom-right
    val bottomRight = Array(28) { FloatArray(28) }
    for (y in 6..25) { bottomRight[y][24] = 1.0f; bottomRight[y][25] = 1.0f }

    mnistInterp.run(gridToBuffer(centred), mnistOut)
    val (cPred, cConf) = logMnistTop5("MNIST centred '1'", mnistOut[0])
    Log.i(TAG, "   Q5 Centred bar: pred='$cPred' conf=${String.format("%.1f%%", cConf * 100)}")

    mnistInterp.run(gridToBuffer(topLeft), mnistOut)
    val (tlPred, tlConf) = logMnistTop5("MNIST top-left '1'", mnistOut[0])
    Log.i(TAG, "   Q5 Top-left bar: pred='$tlPred' conf=${String.format("%.1f%%", tlConf * 100)}")

    mnistInterp.run(gridToBuffer(bottomRight), mnistOut)
    val (brPred, brConf) = logMnistTop5("MNIST bottom-right '1'", mnistOut[0])
    Log.i(TAG, "   Q5 Bottom-right bar: pred='$brPred' conf=${String.format("%.1f%%", brConf * 100)}")

    Log.i(TAG, "   Q5 Centering sensitivity: centred=${String.format("%.1f%%", cConf * 100)} | top-left=${String.format("%.1f%%", tlConf * 100)} | bottom-right=${String.format("%.1f%%", brConf * 100)}")
    val centeringMatters = (cConf - tlConf > 0.10f) || (cConf - brConf > 0.10f)
    Log.i(TAG, "   Q5 Centering matters for accuracy: ${if (centeringMatters) "YES — off-center inputs lose >10% confidence" else "NO — model is relatively position-insensitive"}")

    // ─────────────────────────────────────────────────────────────────────
    // EMNIST MNIST-normalisation cross-test (Q1 extension)
    // Does EMNIST '1' (index 1) vs 'I' (index 18) behave correctly for
    // restricted digit inference on phone/WA fields?
    // ─────────────────────────────────────────────────────────────────────
    Log.i(TAG, "")
    Log.i(TAG, "── Q1-EXT: EMNIST digit-restricted ('1' at idx=1 vs letter 'I' at idx=18) ──")
    emnistInterp.run(gridToBuffer(one), emnistOut)
    val emnistAllTop5 = emnistOut[0].mapIndexed { i, v -> i to v }.sortedByDescending { it.second }
    Log.i(TAG, "   EMNIST full 47-class top-5 for synthetic '1':")
    for (k in 0 until 5) {
      val (idx, conf) = emnistAllTop5[k]
      val label = if (idx < EMNIST_CLASSES.size) EMNIST_CLASSES[idx] else "?"
      Log.i(TAG, "     idx=$idx label='${LOWER_TO_UPPER[label] ?: label}' conf=${String.format("%.2f%%", conf * 100)}")
    }
    val digit1BestInDigitRange = (0..9).maxByOrNull { emnistOut[0][it] }!!
    val digit1BestConf = emnistOut[0][digit1BestInDigitRange]
    Log.i(TAG, "   When restricted to digits 0-9: best idx=$digit1BestInDigitRange conf=${String.format("%.2f%%", digit1BestConf * 100)} → ${if (digit1BestInDigitRange == 1) "✓ CORRECT" else "✗ WRONG — digit restriction will pick wrong class"}")

    // ─────────────────────────────────────────────────────────────────────
    // Summary
    // ─────────────────────────────────────────────────────────────────────
    Log.i(TAG, "")
    Log.i(TAG, "╔═══════════════════════════════════════════════════════╗")
    Log.i(TAG, "║   COMPATIBILITY AUDIT SUMMARY                         ║")
    Log.i(TAG, "╚═══════════════════════════════════════════════════════╝")
    Log.i(TAG, "  Q1 Normalization (/255.0): MNIST=${if (q1MnistPass) "COMPATIBLE" else "INVESTIGATE"} | EMNIST=${if (q1EmnistPass) "COMPATIBLE" else "INVESTIGATE"}")
    Log.i(TAG, "  Q2 Polarity (ink=1.0/bg=0.0): ${if (q2InvFails) "CONFIRMED CORRECT" else "INVESTIGATE — model may be polarity-insensitive"}")
    Log.i(TAG, "  Q3 Label mapping: digit'1' idx=1 ${if (digit1ConfAtIdx1 >= digit1ConfAtIdx18) "✓" else "✗"} | digit'0' idx=0 ${if (digit0ConfAtIdx0 >= digit0ConfAtIdx24) "✓" else "✗"}")
    Log.i(TAG, "  Q4 Stroke thickness (3px > 1px confidence): ${if (thickConf > thinConf) "YES" else "NO"} | delta=${String.format("%.1f%%", (thickConf - thinConf) * 100)}")
    Log.i(TAG, "  Q5 Centering sensitivity (>10% drop off-center): ${if (centeringMatters) "YES" else "NO"}")

    emnistInterp.close()
    mnistInterp.close()
  }

  @Test
  fun testTFLiteHandwritingPrototype() {
    Log.i(TAG, "=========================================================")
    Log.i(TAG, "STARTING PHASE 1: ON-DEVICE TFLITE HANDWRITING PROTOTYPE")
    Log.i(TAG, "=========================================================")

    // Load models
    val emnistBuffer = loadModelBuffer("models/emnist.tflite")
    val emnistInterpreter = Interpreter(emnistBuffer)

    val mnistBuffer = loadModelBuffer("models/mnist.tflite")
    val mnistInterpreter = Interpreter(mnistBuffer)

    Log.i(TAG, "EMNIST Model Input: ${emnistInterpreter.getInputTensor(0).shape().contentToString()}, Output: ${emnistInterpreter.getOutputTensor(0).shape().contentToString()}")
    Log.i(TAG, "MNIST Model Input:  ${mnistInterpreter.getInputTensor(0).shape().contentToString()}, Output: ${mnistInterpreter.getOutputTensor(0).shape().contentToString()}")

    val resultsJson = JSONObject()

    // -------------------------------------------------------------
    // TEST CASE 1: First Name (SUJIT) in 23 boxes
    // -------------------------------------------------------------
    val fnBitmap = loadBitmap("bench_fn.png")
    val fnExpected = "SUJIT"
    Log.i(TAG, "--- Evaluating First Name (Normal Orientation) ---")
    val fnResultsNormal = evaluateBoxedField(
      bitmap = fnBitmap,
      numBoxes = 23,
      expectedNonEmpty = fnExpected,
      restrictToDigits = false,
      interpreter = emnistInterpreter,
      transposeForEmnist = false
    )
    Log.i(TAG, "--- Evaluating First Name (Transposed Orientation) ---")
    val fnResultsTransposed = evaluateBoxedField(
      bitmap = fnBitmap,
      numBoxes = 23,
      expectedNonEmpty = fnExpected,
      restrictToDigits = false,
      interpreter = emnistInterpreter,
      transposeForEmnist = true
    )
    resultsJson.put("firstName_normal", fnResultsNormal)
    resultsJson.put("firstName_transposed", fnResultsTransposed)

    // -------------------------------------------------------------
    // TEST CASE 2: Last Name (KUMAR) in 23 boxes
    // -------------------------------------------------------------
    val lnBitmap = loadBitmap("bench_ln.png")
    val lnExpected = "KUMAR"
    Log.i(TAG, "--- Evaluating Last Name (Normal Orientation) ---")
    val lnResultsNormal = evaluateBoxedField(
      bitmap = lnBitmap,
      numBoxes = 23,
      expectedNonEmpty = lnExpected,
      restrictToDigits = false,
      interpreter = emnistInterpreter,
      transposeForEmnist = false
    )
    Log.i(TAG, "--- Evaluating Last Name (Transposed Orientation) ---")
    val lnResultsTransposed = evaluateBoxedField(
      bitmap = lnBitmap,
      numBoxes = 23,
      expectedNonEmpty = lnExpected,
      restrictToDigits = false,
      interpreter = emnistInterpreter,
      transposeForEmnist = true
    )
    resultsJson.put("lastName_normal", lnResultsNormal)
    resultsJson.put("lastName_transposed", lnResultsTransposed)

    // -------------------------------------------------------------
    // TEST CASE 3: Phone (9315429137) in 10 boxes
    // -------------------------------------------------------------
    val phoneBitmap = loadBitmap("bench_phone.png")
    val phoneExpected = "9315429137"
    Log.i(TAG, "--- Evaluating Phone (MNIST) ---")
    val phoneResultsMnist = evaluateBoxedField(
      bitmap = phoneBitmap,
      numBoxes = 10,
      expectedNonEmpty = phoneExpected,
      restrictToDigits = true,
      interpreter = mnistInterpreter,
      transposeForEmnist = false
    )
    Log.i(TAG, "--- Evaluating Phone (EMNIST Normal) ---")
    val phoneResultsEmnist = evaluateBoxedField(
      bitmap = phoneBitmap,
      numBoxes = 10,
      expectedNonEmpty = phoneExpected,
      restrictToDigits = true,
      interpreter = emnistInterpreter,
      transposeForEmnist = false
    )
    Log.i(TAG, "--- Evaluating Phone (EMNIST Transposed) ---")
    val phoneResultsEmnistTransposed = evaluateBoxedField(
      bitmap = phoneBitmap,
      numBoxes = 10,
      expectedNonEmpty = phoneExpected,
      restrictToDigits = true,
      interpreter = emnistInterpreter,
      transposeForEmnist = true
    )
    resultsJson.put("phone_mnist", phoneResultsMnist)
    resultsJson.put("phone_emnist", phoneResultsEmnist)
    resultsJson.put("phone_emnist_transposed", phoneResultsEmnistTransposed)

    // -------------------------------------------------------------
    // TEST CASE 4: WhatsApp (9315429137) in 10 boxes
    // -------------------------------------------------------------
    val waBitmap = loadBitmap("bench_wa.png")
    val waExpected = "9315429137"
    Log.i(TAG, "--- Evaluating WhatsApp (MNIST) ---")
    val waResultsMnist = evaluateBoxedField(
      bitmap = waBitmap,
      numBoxes = 10,
      expectedNonEmpty = waExpected,
      restrictToDigits = true,
      interpreter = mnistInterpreter,
      transposeForEmnist = false
    )
    Log.i(TAG, "--- Evaluating WhatsApp (EMNIST Normal) ---")
    val waResultsEmnist = evaluateBoxedField(
      bitmap = waBitmap,
      numBoxes = 10,
      expectedNonEmpty = waExpected,
      restrictToDigits = true,
      interpreter = emnistInterpreter,
      transposeForEmnist = false
    )
    Log.i(TAG, "--- Evaluating WhatsApp (EMNIST Transposed) ---")
    val waResultsEmnistTransposed = evaluateBoxedField(
      bitmap = waBitmap,
      numBoxes = 10,
      expectedNonEmpty = waExpected,
      restrictToDigits = true,
      interpreter = emnistInterpreter,
      transposeForEmnist = true
    )
    resultsJson.put("whatsapp_mnist", waResultsMnist)
    resultsJson.put("whatsapp_emnist", waResultsEmnist)
    resultsJson.put("whatsapp_emnist_transposed", waResultsEmnistTransposed)

    emnistInterpreter.close()
    mnistInterpreter.close()

    Log.i(TAG, "=========================================================")
    Log.i(TAG, "PHASE 1 TFLITE PROTOTYPE BENCHMARK SUMMARY:")
    Log.i(TAG, resultsJson.toString(2))
    Log.i(TAG, "=========================================================")
  }

  private fun evaluateBoxedField(
    bitmap: Bitmap,
    numBoxes: Int,
    expectedNonEmpty: String,
    restrictToDigits: Boolean,
    interpreter: Interpreter,
    transposeForEmnist: Boolean = false
  ): JSONObject {
    val bW = bitmap.width
    val bH = bitmap.height
    val boxW = bW.toFloat() / numBoxes.toFloat()
    val numOutputs = interpreter.getOutputTensor(0).shape()[1]

    val classifications = mutableListOf<BoxClassification>()
    val reconstructed = StringBuilder()

    var correctNonEmptyChars = 0
    var correctEmptyBoxes = 0
    val totalExpectedEmpty = numBoxes - expectedNonEmpty.length

    for (i in 0 until numBoxes) {
      val left = (i * boxW).toInt().coerceIn(0, bW - 1)
      val right = ((i + 1) * boxW).toInt().coerceIn(left + 1, bW)
      val w = right - left

      val cellCrop = Bitmap.createBitmap(bitmap, left, 0, w, bH)

      val (inputBuffer, inkCount) = preprocessBoxTo28x28(
        cellCrop,
        transposeForEmnist = transposeForEmnist,
        inkThreshold = 138
      )

      val isEmpty = (inputBuffer == null)

      val recognizedChar: String
      val confidence: Float
      val top3Info = StringBuilder()

      if (isEmpty) {
        recognizedChar = " "
        confidence = 1.0f
        if (i >= expectedNonEmpty.length) {
          correctEmptyBoxes++
        }
      } else {
        val output = Array(1) { FloatArray(numOutputs) }
        interpreter.run(inputBuffer, output)
        val probs = output[0]

        if (numOutputs == 10) {
          // MNIST: 0..9
          val sortedIndices = (0 until 10).sortedByDescending { probs[it] }
          val bestIdx = sortedIndices[0]
          recognizedChar = bestIdx.toString()
          confidence = probs[bestIdx]
          for (k in 0 until minOf(3, sortedIndices.size)) {
            val idx = sortedIndices[k]
            top3Info.append("${idx}(${String.format("%.1f%%", probs[idx] * 100)}) ")
          }
        } else {
          // EMNIST 47 classes:
          if (restrictToDigits) {
            val digitIndices = (0..9).sortedByDescending { probs[it] }
            val bestIdx = digitIndices[0]
            recognizedChar = EMNIST_CLASSES[bestIdx]
            confidence = probs[bestIdx]
            for (k in 0 until minOf(3, digitIndices.size)) {
              val idx = digitIndices[k]
              top3Info.append("${EMNIST_CLASSES[idx]}(${String.format("%.1f%%", probs[idx] * 100)}) ")
            }
          } else {
            // Constrain search to letters A-Z (10..35) and lowercase (36..46)
            val letterIndices = (10 until 47).sortedByDescending { probs[it] }
            val bestIdx = letterIndices[0]
            val rawChar = EMNIST_CLASSES[bestIdx]
            recognizedChar = LOWER_TO_UPPER[rawChar] ?: rawChar.uppercase()
            confidence = probs[bestIdx]
            for (k in 0 until minOf(3, letterIndices.size)) {
              val idx = letterIndices[k]
              val c = EMNIST_CLASSES[idx]
              val uc = LOWER_TO_UPPER[c] ?: c.uppercase()
              top3Info.append("${uc}(${String.format("%.1f%%", probs[idx] * 100)}) ")
            }
          }
        }

        reconstructed.append(recognizedChar)

        // Check against ground truth
        if (i < expectedNonEmpty.length) {
          val expectedChar = expectedNonEmpty[i].toString()
          if (recognizedChar.equals(expectedChar, ignoreCase = true)) {
            correctNonEmptyChars++
          }
        }
      }

      val expectedCharForLog = if (i < expectedNonEmpty.length) expectedNonEmpty[i].toString() else " "
      Log.i(TAG, "  Box ${i + 1}/${numBoxes}: expected='$expectedCharForLog', got='$recognizedChar', conf=${String.format("%.1f%%", confidence * 100)}, ink=$inkCount, top3=[$top3Info]")

      classifications.add(
        BoxClassification(
          boxIndex = i,
          recognizedChar = recognizedChar,
          confidence = confidence,
          inkRatio = inkCount.toFloat(),
          isEmpty = isEmpty
        )
      )
    }

    val finalString = reconstructed.toString().trim()
    val charAccuracy = if (expectedNonEmpty.isNotEmpty()) {
      (correctNonEmptyChars.toFloat() / expectedNonEmpty.length.toFloat()) * 100f
    } else 100f

    val emptyAccuracy = if (totalExpectedEmpty > 0) {
      (correctEmptyBoxes.toFloat() / totalExpectedEmpty.toFloat()) * 100f
    } else 100f

    val isFullMatch = finalString.equals(expectedNonEmpty, ignoreCase = true)

    val json = JSONObject().apply {
      put("groundTruth", expectedNonEmpty)
      put("reconstructed", finalString)
      put("isFullMatch", isFullMatch)
      put("charAccuracyPercent", String.format("%.1f%%", charAccuracy))
      put("correctChars", "$correctNonEmptyChars / ${expectedNonEmpty.length}")
      put("emptyBoxAccuracyPercent", String.format("%.1f%%", emptyAccuracy))
      put("correctEmptyBoxes", "$correctEmptyBoxes / $totalExpectedEmpty")

      val cellsArray = JSONArray()
      for (c in classifications) {
        cellsArray.put(JSONObject().apply {
          put("box", c.boxIndex + 1)
          put("char", c.recognizedChar)
          put("conf", String.format("%.1f%%", c.confidence * 100))
          put("inkCount", c.inkRatio.toInt())
          put("empty", c.isEmpty)
        })
      }
      put("cells", cellsArray)
    }

    Log.i(TAG, "  Field Reconstruction: '$finalString' (Expected: '$expectedNonEmpty', Match: $isFullMatch, Chars: $correctNonEmptyChars/${expectedNonEmpty.length}, Empty: $correctEmptyBoxes/$totalExpectedEmpty)")

    return json
  }

  @Test
  fun testArmA_vs_ArmB_Benchmark() {
    Log.i(TAG, "╔═══════════════════════════════════════════════════════════════════════════════════════╗")
    Log.i(TAG, "║   STEP 2: ARM A (BASELINE) vs ARM B (VALIDATED PREPROCESSING) BENCHMARK               ║")
    Log.i(TAG, "╚═══════════════════════════════════════════════════════════════════════════════════════╝")

    val emnistBuffer = loadModelBuffer("models/emnist.tflite")
    val emnistInterpreter = Interpreter(emnistBuffer)

    val mnistBuffer = loadModelBuffer("models/mnist.tflite")
    val mnistInterpreter = Interpreter(mnistBuffer)

    // Benchmark input bitmaps (same for all arms & variants)
    val fnBitmap = loadBitmap("bench_fn.png")
    val lnBitmap = loadBitmap("bench_ln.png")
    val phoneBitmap = loadBitmap("bench_phone.png")
    val waBitmap = loadBitmap("bench_wa.png")

    val fnExpected = "SUJIT"
    val lnExpected = "KUMAR"
    val phoneExpected = "9315429137"
    val waExpected = "9315429137"

    // ─────────────────────────────────────────────────────────────────────────
    // Independent preprocessing variants
    // ─────────────────────────────────────────────────────────────────────────
    val configs = listOf(
      PreprocessConfig("Arm_A", "Arm A: BBox Centering, 1px Dilation [Baseline]", CenteringMethod.BOUNDING_BOX, dilationRadius = 1),
      PreprocessConfig("Var_BBox_2px", "Var 1: BBox Centering, 2px Dilation", CenteringMethod.BOUNDING_BOX, dilationRadius = 2),
      PreprocessConfig("Var_BBox_0px", "Var 2: BBox Centering, 0px Dilation (No Dilation)", CenteringMethod.BOUNDING_BOX, dilationRadius = 0),
      PreprocessConfig("Var_CoM_Int_1px", "Var 3: Center-of-Mass (Int), 1px Dilation", CenteringMethod.CENTER_OF_MASS_INT, dilationRadius = 1),
      PreprocessConfig("Var_CoM_Int_2px", "Var 4: Center-of-Mass (Int), 2px Dilation", CenteringMethod.CENTER_OF_MASS_INT, dilationRadius = 2),
      PreprocessConfig("Var_CoM_Int_0px", "Var 5: Center-of-Mass (Int), 0px Dilation", CenteringMethod.CENTER_OF_MASS_INT, dilationRadius = 0),
      PreprocessConfig("Var_CoM_Float_1px", "Var 6: Center-of-Mass (Float Subpixel), 1px Dilation", CenteringMethod.CENTER_OF_MASS_FLOAT, dilationRadius = 1),
      PreprocessConfig("Var_CoM_Float_2px", "Var 7: Center-of-Mass (Float Subpixel), 2px Dilation", CenteringMethod.CENTER_OF_MASS_FLOAT, dilationRadius = 2)
    )

    data class VariantSummary(
      val config: PreprocessConfig,
      val fnResult: FieldEvaluationResult,
      val lnResult: FieldEvaluationResult,
      val phoneEmnist: FieldEvaluationResult,
      val waEmnist: FieldEvaluationResult,
      val phoneMnist: FieldEvaluationResult,
      val waMnist: FieldEvaluationResult,
      val charCorrect: Int,
      val charTotal: Int,
      val charAcc: Float,
      val digitEmnistCorrect: Int,
      val digitEmnistTotal: Int,
      val digitEmnistAcc: Float,
      val digitMnistCorrect: Int,
      val digitMnistTotal: Int,
      val digitMnistAcc: Float,
      val emptyCorrect: Int,
      val emptyTotal: Int,
      val emptyAcc: Float,
      val exactMatchesEmnist: Int,
      val avgConfidence: Float
    )

    val summaries = mutableListOf<VariantSummary>()

    for (cfg in configs) {
      val fn = evaluateBoxedFieldWithConfig("First Name", fnBitmap, 23, fnExpected, false, emnistInterpreter, cfg)
      val ln = evaluateBoxedFieldWithConfig("Last Name", lnBitmap, 23, lnExpected, false, emnistInterpreter, cfg)
      val phoneEmnist = evaluateBoxedFieldWithConfig("Phone (EMNIST)", phoneBitmap, 10, phoneExpected, true, emnistInterpreter, cfg)
      val waEmnist = evaluateBoxedFieldWithConfig("WhatsApp (EMNIST)", waBitmap, 10, waExpected, true, emnistInterpreter, cfg)
      val phoneMnist = evaluateBoxedFieldWithConfig("Phone (MNIST)", phoneBitmap, 10, phoneExpected, true, mnistInterpreter, cfg)
      val waMnist = evaluateBoxedFieldWithConfig("WhatsApp (MNIST)", waBitmap, 10, waExpected, true, mnistInterpreter, cfg)

      val charCorrect = fn.correctChars + ln.correctChars
      val charTotal = fn.totalChars + ln.totalChars
      val charAcc = (charCorrect.toFloat() / charTotal.toFloat()) * 100f

      val digitEmnistCorrect = phoneEmnist.correctChars + waEmnist.correctChars
      val digitEmnistTotal = phoneEmnist.totalChars + waEmnist.totalChars
      val digitEmnistAcc = (digitEmnistCorrect.toFloat() / digitEmnistTotal.toFloat()) * 100f

      val digitMnistCorrect = phoneMnist.correctChars + waMnist.correctChars
      val digitMnistTotal = phoneMnist.totalChars + waMnist.totalChars
      val digitMnistAcc = (digitMnistCorrect.toFloat() / digitMnistTotal.toFloat()) * 100f

      val emptyCorrect = fn.correctEmpty + ln.correctEmpty
      val emptyTotal = fn.totalEmpty + ln.totalEmpty
      val emptyAcc = (emptyCorrect.toFloat() / emptyTotal.toFloat()) * 100f

      val exactEmnist = (if (fn.isFullMatch) 1 else 0) +
        (if (ln.isFullMatch) 1 else 0) +
        (if (phoneEmnist.isFullMatch) 1 else 0) +
        (if (waEmnist.isFullMatch) 1 else 0)

      val allCells = fn.cells + ln.cells + phoneEmnist.cells + waEmnist.cells
      val avgConf = if (allCells.isNotEmpty()) allCells.map { it.confidence }.average().toFloat() * 100f else 0f

      summaries.add(
        VariantSummary(
          config = cfg,
          fnResult = fn,
          lnResult = ln,
          phoneEmnist = phoneEmnist,
          waEmnist = waEmnist,
          phoneMnist = phoneMnist,
          waMnist = waMnist,
          charCorrect = charCorrect,
          charTotal = charTotal,
          charAcc = charAcc,
          digitEmnistCorrect = digitEmnistCorrect,
          digitEmnistTotal = digitEmnistTotal,
          digitEmnistAcc = digitEmnistAcc,
          digitMnistCorrect = digitMnistCorrect,
          digitMnistTotal = digitMnistTotal,
          digitMnistAcc = digitMnistAcc,
          emptyCorrect = emptyCorrect,
          emptyTotal = emptyTotal,
          emptyAcc = emptyAcc,
          exactMatchesEmnist = exactEmnist,
          avgConfidence = avgConf
        )
      )
    }

    // Identify Arm A (baseline)
    val armA = summaries.first { it.config.id == "Arm_A" }

    // Identify Arm B: best validated non-baseline variant (highest char + digit score on EMNIST, tie-breaker avg confidence)
    val bestVariant = summaries.filter { it.config.id != "Arm_A" }
      .maxWithOrNull(compareBy<VariantSummary> { it.charCorrect + it.digitEmnistCorrect }
        .thenBy { it.exactMatchesEmnist }
        .thenBy { it.avgConfidence }) ?: summaries[1]

    val armB = bestVariant

    Log.i(TAG, "")
    Log.i(TAG, "═════════════════════════════════════════════════════════════════════════════════════════════════")
    Log.i(TAG, "ALL PREPROCESSING VARIANTS COMPARISON TABLE:")
    Log.i(TAG, "═════════════════════════════════════════════════════════════════════════════════════════════════")
    Log.i(TAG, String.format("%-32s | %-12s | %-12s | %-12s | %-12s | %-12s | %-8s",
      "Config Name", "FN (SUJIT)", "LN (KUMAR)", "Phone EMNIST", "WA EMNIST", "A-Z Accuracy", "Empty Box"))
    Log.i(TAG, "-------------------------------------------------------------------------------------------------")
    for (s in summaries) {
      Log.i(TAG, String.format("%-32s | %-12s | %-12s | %-12s | %-12s | %-12s | %-8s",
        s.config.name.take(32),
        "${s.fnResult.reconstructed} (${s.fnResult.correctChars}/5)",
        "${s.lnResult.reconstructed} (${s.lnResult.correctChars}/5)",
        "${s.phoneEmnist.reconstructed} (${s.phoneEmnist.correctChars}/10)",
        "${s.waEmnist.reconstructed} (${s.waEmnist.correctChars}/10)",
        String.format("%.1f%% (%d/%d)", s.charAcc, s.charCorrect, s.charTotal),
        String.format("%.1f%%", s.emptyAcc)
      ))
    }
    Log.i(TAG, "═════════════════════════════════════════════════════════════════════════════════════════════════")

    // Direct comparison: Arm A vs Arm B
    Log.i(TAG, "")
    Log.i(TAG, "╔═══════════════════════════════════════════════════════════════════════════════════════════════╗")
    Log.i(TAG, "║   ARM A (BASELINE) vs ARM B (BEST VALIDATED PREPROCESSING) DIRECT COMPARISON                  ║")
    Log.i(TAG, "╚═══════════════════════════════════════════════════════════════════════════════════════════════╝")
    Log.i(TAG, "Arm A Config: ${armA.config.name}")
    Log.i(TAG, "Arm B Config: ${armB.config.name}")
    Log.i(TAG, "")
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "Metric", "Arm A (Baseline)", "Arm B (${armB.config.id})", "Delta"))
    Log.i(TAG, "-------------------------------------------------------------------------------------------------")
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "First Name (SUJIT)", "${armA.fnResult.reconstructed} (${armA.fnResult.correctChars}/5)", "${armB.fnResult.reconstructed} (${armB.fnResult.correctChars}/5)", "${armB.fnResult.correctChars - armA.fnResult.correctChars}"))
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "Last Name (KUMAR)", "${armA.lnResult.reconstructed} (${armA.lnResult.correctChars}/5)", "${armB.lnResult.reconstructed} (${armB.lnResult.correctChars}/5)", "${armB.lnResult.correctChars - armA.lnResult.correctChars}"))
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "Phone (EMNIST)", "${armA.phoneEmnist.reconstructed} (${armA.phoneEmnist.correctChars}/10)", "${armB.phoneEmnist.reconstructed} (${armB.phoneEmnist.correctChars}/10)", "${armB.phoneEmnist.correctChars - armA.phoneEmnist.correctChars}"))
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "WhatsApp (EMNIST)", "${armA.waEmnist.reconstructed} (${armA.waEmnist.correctChars}/10)", "${armB.waEmnist.reconstructed} (${armB.waEmnist.correctChars}/10)", "${armB.waEmnist.correctChars - armA.waEmnist.correctChars}"))
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "A-Z Char Accuracy (10 chars)", String.format("%.1f%% (%d/10)", armA.charAcc, armA.charCorrect), String.format("%.1f%% (%d/10)", armB.charAcc, armB.charCorrect), String.format("%+.1f%%", armB.charAcc - armA.charAcc)))
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "EMNIST Digit Accuracy (20)", String.format("%.1f%% (%d/20)", armA.digitEmnistAcc, armA.digitEmnistCorrect), String.format("%.1f%% (%d/20)", armB.digitEmnistAcc, armB.digitEmnistCorrect), String.format("%+.1f%%", armB.digitEmnistAcc - armA.digitEmnistAcc)))
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "Phone (MNIST 10-class ref)", "${armA.phoneMnist.reconstructed} (${armA.phoneMnist.correctChars}/10)", "${armB.phoneMnist.reconstructed} (${armB.phoneMnist.correctChars}/10)", "${armB.phoneMnist.correctChars - armA.phoneMnist.correctChars}"))
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "WhatsApp (MNIST 10-class ref)", "${armA.waMnist.reconstructed} (${armA.waMnist.correctChars}/10)", "${armB.waMnist.reconstructed} (${armB.waMnist.correctChars}/10)", "${armB.waMnist.correctChars - armA.waMnist.correctChars}"))
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "MNIST Digit Accuracy (20)", String.format("%.1f%% (%d/20)", armA.digitMnistAcc, armA.digitMnistCorrect), String.format("%.1f%% (%d/20)", armB.digitMnistAcc, armB.digitMnistCorrect), String.format("%+.1f%%", armB.digitMnistAcc - armA.digitMnistAcc)))
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "First Name Exact Match", "${armA.fnResult.isFullMatch}", "${armB.fnResult.isFullMatch}", "${if (armB.fnResult.isFullMatch == armA.fnResult.isFullMatch) "=" else "CHANGED"}"))
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "Last Name Exact Match", "${armA.lnResult.isFullMatch}", "${armB.lnResult.isFullMatch}", "${if (armB.lnResult.isFullMatch == armA.lnResult.isFullMatch) "=" else "CHANGED"}"))
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "Phone 10/10 Exact Match", "${armA.phoneEmnist.isFullMatch}", "${armB.phoneEmnist.isFullMatch}", "${if (armB.phoneEmnist.isFullMatch == armA.phoneEmnist.isFullMatch) "=" else "CHANGED"}"))
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "WhatsApp 10/10 Exact Match", "${armA.waEmnist.isFullMatch}", "${armB.waEmnist.isFullMatch}", "${if (armB.waEmnist.isFullMatch == armA.waEmnist.isFullMatch) "=" else "CHANGED"}"))
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "Empty Box Accuracy (36 boxes)", String.format("%.1f%% (%d/36)", armA.emptyAcc, armA.emptyCorrect), String.format("%.1f%% (%d/36)", armB.emptyAcc, armB.emptyCorrect), String.format("%+.1f%%", armB.emptyAcc - armA.emptyAcc)))
    Log.i(TAG, String.format("%-30s | %-25s | %-25s | %-10s", "Average Confidence", String.format("%.1f%%", armA.avgConfidence), String.format("%.1f%%", armB.avgConfidence), String.format("%+.1f%%", armB.avgConfidence - armA.avgConfidence)))
    Log.i(TAG, "═════════════════════════════════════════════════════════════════════════════════════════════════")

    // Per-box detail for First Name
    Log.i(TAG, "")
    Log.i(TAG, "── PER-BOX DETAIL: FIRST NAME (SUJIT) ──")
    for (i in 0 until 5) {
      val cA = armA.fnResult.cells[i]
      val cB = armB.fnResult.cells[i]
      Log.i(TAG, "  Box ${i + 1}: expected='${cA.expected}' | Arm A: '${cA.recognized}' (${String.format("%.1f%%", cA.confidence * 100)}) [${cA.top3}] | Arm B: '${cB.recognized}' (${String.format("%.1f%%", cB.confidence * 100)}) [${cB.top3}]")
    }

    // Per-box detail for Last Name
    Log.i(TAG, "")
    Log.i(TAG, "── PER-BOX DETAIL: LAST NAME (KUMAR) ──")
    for (i in 0 until 5) {
      val cA = armA.lnResult.cells[i]
      val cB = armB.lnResult.cells[i]
      Log.i(TAG, "  Box ${i + 1}: expected='${cA.expected}' | Arm A: '${cA.recognized}' (${String.format("%.1f%%", cA.confidence * 100)}) [${cA.top3}] | Arm B: '${cB.recognized}' (${String.format("%.1f%%", cB.confidence * 100)}) [${cB.top3}]")
    }

    // Per-box detail for Phone
    Log.i(TAG, "")
    Log.i(TAG, "── PER-BOX DETAIL: PHONE (9315429137) ──")
    for (i in 0 until 10) {
      val cA = armA.phoneEmnist.cells[i]
      val cB = armB.phoneEmnist.cells[i]
      val mA = armA.phoneMnist.cells[i]
      val mB = armB.phoneMnist.cells[i]
      Log.i(TAG, "  Box ${i + 1}: expected='${cA.expected}' | Arm A (EMNIST): '${cA.recognized}' (${String.format("%.1f%%", cA.confidence * 100)}) | Arm B (EMNIST): '${cB.recognized}' (${String.format("%.1f%%", cB.confidence * 100)}) | MNIST ref: A='${mA.recognized}', B='${mB.recognized}'")
    }

    // Per-box detail for WhatsApp
    Log.i(TAG, "")
    Log.i(TAG, "── PER-BOX DETAIL: WHATSAPP (9315429137) ──")
    for (i in 0 until 10) {
      val cA = armA.waEmnist.cells[i]
      val cB = armB.waEmnist.cells[i]
      val mA = armA.waMnist.cells[i]
      val mB = armB.waMnist.cells[i]
      Log.i(TAG, "  Box ${i + 1}: expected='${cA.expected}' | Arm A (EMNIST): '${cA.recognized}' (${String.format("%.1f%%", cA.confidence * 100)}) | Arm B (EMNIST): '${cB.recognized}' (${String.format("%.1f%%", cB.confidence * 100)}) | MNIST ref: A='${mA.recognized}', B='${mB.recognized}'")
    }

    // Confusion breakdown
    Log.i(TAG, "")
    Log.i(TAG, "── CONFUSIONS IN ARM A (BASELINE) ──")
    val confusionsA = armA.fnResult.confusions + armA.lnResult.confusions + armA.phoneEmnist.confusions + armA.waEmnist.confusions
    if (confusionsA.isEmpty()) {
      Log.i(TAG, "  None! All characters correct.")
    } else {
      for (c in confusionsA) {
        Log.i(TAG, "  $c")
      }
    }

    Log.i(TAG, "")
    Log.i(TAG, "── CONFUSIONS IN ARM B (${armB.config.id}) ──")
    val confusionsB = armB.fnResult.confusions + armB.lnResult.confusions + armB.phoneEmnist.confusions + armB.waEmnist.confusions
    if (confusionsB.isEmpty()) {
      Log.i(TAG, "  None! All characters correct.")
    } else {
      for (c in confusionsB) {
        Log.i(TAG, "  $c")
      }
    }

    // Final verdict
    Log.i(TAG, "")
    Log.i(TAG, "╔═══════════════════════════════════════════════════════════════════════════════════════════════╗")
    Log.i(TAG, "║   STEP 2 VERDICT: DID PREPROCESSING PRODUCE A MEANINGFUL IMPROVEMENT?                          ║")
    Log.i(TAG, "╚═══════════════════════════════════════════════════════════════════════════════════════════════╝")
    val totalCorrectA = armA.charCorrect + armA.digitEmnistCorrect
    val totalCorrectB = armB.charCorrect + armB.digitEmnistCorrect
    val deltaCorrect = totalCorrectB - totalCorrectA
    val meaningfulImprovement = deltaCorrect >= 4
    Log.i(TAG, "  Arm A Total Correct (Chars + Digits): $totalCorrectA / 30 (${String.format("%.1f%%", totalCorrectA / 30f * 100f)})")
    Log.i(TAG, "  Arm B Total Correct (Chars + Digits): $totalCorrectB / 30 (${String.format("%.1f%%", totalCorrectB / 30f * 100f)})")
    Log.i(TAG, "  Delta: ${String.format("%+d", deltaCorrect)} characters (${String.format("%+.1f%%", (totalCorrectB - totalCorrectA) / 30f * 100f)})")
    Log.i(TAG, "  Meaningful Improvement: ${if (meaningfulImprovement) "YES — Preprocessing noticeably helped" else "NO — Preprocessing alone CANNOT overcome the model's structural limitations"}")
    Log.i(TAG, "═════════════════════════════════════════════════════════════════════════════════════════════════")

    emnistInterpreter.close()
    mnistInterpreter.close()
  }

  @Test
  fun testThreeArmBenchmark_ArmA_ArmB_ArmC() {
    Log.i(TAG, "╔═══════════════════════════════════════════════════════════════════════════════════════╗")
    Log.i(TAG, "║   PHASE 1.5: THREE-ARM BENCHMARK (ARM A vs ARM B vs ARM C)                            ║")
    Log.i(TAG, "╚═══════════════════════════════════════════════════════════════════════════════════════╝")

    val emnistBuffer = loadModelBuffer("models/emnist.tflite")
    val emnistInterpreter = Interpreter(emnistBuffer)

    val omrPocBuffer = loadModelBuffer("models/omr_poc.tflite")
    val omrPocInterpreter = Interpreter(omrPocBuffer)

    Log.i(TAG, "EMNIST Model Input: ${emnistInterpreter.getInputTensor(0).shape().contentToString()}, Output: ${emnistInterpreter.getOutputTensor(0).shape().contentToString()}")
    Log.i(TAG, "OMR PoC Model Input: ${omrPocInterpreter.getInputTensor(0).shape().contentToString()}, Output: ${omrPocInterpreter.getOutputTensor(0).shape().contentToString()}")

    // Benchmark input bitmaps (100% UNSEEN real physical OMR samples)
    val fnBitmap = loadBitmap("bench_fn.png")
    val lnBitmap = loadBitmap("bench_ln.png")
    val phoneBitmap = loadBitmap("bench_phone.png")
    val waBitmap = loadBitmap("bench_wa.png")

    val fnExpected = "SUJIT"
    val lnExpected = "KUMAR"
    val phoneExpected = "9315429137"
    val waExpected = "9315429137"

    // ─────────────────────────────────────────────────────────────────────────
    // Arm Configurations
    // ─────────────────────────────────────────────────────────────────────────
    val cfgArmA = PreprocessConfig("Arm_A", "Arm A: Current EMNIST [Baseline]", CenteringMethod.BOUNDING_BOX, dilationRadius = 1)
    val cfgArmB = PreprocessConfig("Arm_B", "Arm B: EMNIST + Validated Preproc", CenteringMethod.CENTER_OF_MASS_INT, dilationRadius = 0)
    val cfgArmC = PreprocessConfig("Arm_C", "Arm C: Custom 36-Class OMR Model", CenteringMethod.CENTER_OF_MASS_INT, dilationRadius = 0)

    // ─────────────────────────────────────────────────────────────────────────
    // Run Evaluations
    // ─────────────────────────────────────────────────────────────────────────
    data class ArmResult(
      val name: String,
      val fn: FieldEvaluationResult,
      val ln: FieldEvaluationResult,
      val phone: FieldEvaluationResult,
      val wa: FieldEvaluationResult,
      val charCorrect: Int,
      val charTotal: Int,
      val charAcc: Float,
      val digitCorrect: Int,
      val digitTotal: Int,
      val digitAcc: Float,
      val emptyCorrect: Int,
      val emptyTotal: Int,
      val emptyAcc: Float,
      val exactMatches: Int,
      val avgConfidence: Float,
      val avgLatencyMs: Float
    )

    fun evaluateArm(armName: String, cfg: PreprocessConfig, interp: Interpreter): ArmResult {
      val fn = evaluateBoxedFieldWithConfig("First Name", fnBitmap, 23, fnExpected, false, interp, cfg)
      val ln = evaluateBoxedFieldWithConfig("Last Name", lnBitmap, 23, lnExpected, false, interp, cfg)
      val phone = evaluateBoxedFieldWithConfig("Phone", phoneBitmap, 10, phoneExpected, true, interp, cfg)
      val wa = evaluateBoxedFieldWithConfig("WhatsApp", waBitmap, 10, waExpected, true, interp, cfg)

      val charCorrect = fn.correctChars + ln.correctChars
      val charTotal = fn.totalChars + ln.totalChars
      val charAcc = (charCorrect.toFloat() / charTotal.toFloat()) * 100f

      val digitCorrect = phone.correctChars + wa.correctChars
      val digitTotal = phone.totalChars + wa.totalChars
      val digitAcc = (digitCorrect.toFloat() / digitTotal.toFloat()) * 100f

      val emptyCorrect = fn.correctEmpty + ln.correctEmpty
      val emptyTotal = fn.totalEmpty + ln.totalEmpty
      val emptyAcc = (emptyCorrect.toFloat() / emptyTotal.toFloat()) * 100f

      val exact = (if (fn.isFullMatch) 1 else 0) +
        (if (ln.isFullMatch) 1 else 0) +
        (if (phone.isFullMatch) 1 else 0) +
        (if (wa.isFullMatch) 1 else 0)

      val allCells = fn.cells + ln.cells + phone.cells + wa.cells
      val avgConf = if (allCells.isNotEmpty()) allCells.map { it.confidence }.average().toFloat() * 100f else 0f
      val avgLat = (fn.avgLatencyMs + ln.avgLatencyMs + phone.avgLatencyMs + wa.avgLatencyMs) / 4.0f

      return ArmResult(
        name = armName,
        fn = fn,
        ln = ln,
        phone = phone,
        wa = wa,
        charCorrect = charCorrect,
        charTotal = charTotal,
        charAcc = charAcc,
        digitCorrect = digitCorrect,
        digitTotal = digitTotal,
        digitAcc = digitAcc,
        emptyCorrect = emptyCorrect,
        emptyTotal = emptyTotal,
        emptyAcc = emptyAcc,
        exactMatches = exact,
        avgConfidence = avgConf,
        avgLatencyMs = avgLat
      )
    }

    val resA = evaluateArm("Arm A (Baseline EMNIST)", cfgArmA, emnistInterpreter)
    val resB = evaluateArm("Arm B (EMNIST + Preproc)", cfgArmB, emnistInterpreter)
    val resC = evaluateArm("Arm C (Custom OMR Model)", cfgArmC, omrPocInterpreter)

    // ─────────────────────────────────────────────────────────────────────────
    // Comparison Table
    // ─────────────────────────────────────────────────────────────────────────
    Log.i(TAG, "")
    Log.i(TAG, "═══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════")
    Log.i(TAG, "THREE-ARM BENCHMARK RESULTS (ARM A vs ARM B vs ARM C):")
    Log.i(TAG, "═══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════")
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "Metric", "Arm A (Baseline)", "Arm B (Preproc)", "Arm C (Custom OMR)"))
    Log.i(TAG, "-----------------------------------------------------------------------------------------------------------------------")
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "First Name (SUJIT)", "${resA.fn.reconstructed} (${resA.fn.correctChars}/5)", "${resB.fn.reconstructed} (${resB.fn.correctChars}/5)", "${resC.fn.reconstructed} (${resC.fn.correctChars}/5)"))
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "Last Name (KUMAR)", "${resA.ln.reconstructed} (${resA.ln.correctChars}/5)", "${resB.ln.reconstructed} (${resB.ln.correctChars}/5)", "${resC.ln.reconstructed} (${resC.ln.correctChars}/5)"))
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "Phone (9315429137)", "${resA.phone.reconstructed} (${resA.phone.correctChars}/10)", "${resB.phone.reconstructed} (${resB.phone.correctChars}/10)", "${resC.phone.reconstructed} (${resC.phone.correctChars}/10)"))
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "WhatsApp (9315429137)", "${resA.wa.reconstructed} (${resA.wa.correctChars}/10)", "${resB.wa.reconstructed} (${resB.wa.correctChars}/10)", "${resC.wa.reconstructed} (${resC.wa.correctChars}/10)"))
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "A-Z Char Accuracy (10)", String.format("%.1f%% (%d/10)", resA.charAcc, resA.charCorrect), String.format("%.1f%% (%d/10)", resB.charAcc, resB.charCorrect), String.format("%.1f%% (%d/10)", resC.charAcc, resC.charCorrect)))
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "Digit Accuracy (20)", String.format("%.1f%% (%d/20)", resA.digitAcc, resA.digitCorrect), String.format("%.1f%% (%d/20)", resB.digitAcc, resB.digitCorrect), String.format("%.1f%% (%d/20)", resC.digitAcc, resC.digitCorrect)))
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "Total Chars+Digits (30)", String.format("%d / 30 (%.1f%%)", resA.charCorrect + resA.digitCorrect, (resA.charCorrect + resA.digitCorrect) / 30f * 100f), String.format("%d / 30 (%.1f%%)", resB.charCorrect + resB.digitCorrect, (resB.charCorrect + resB.digitCorrect) / 30f * 100f), String.format("%d / 30 (%.1f%%)", resC.charCorrect + resC.digitCorrect, (resC.charCorrect + resC.digitCorrect) / 30f * 100f)))
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "First Name Exact Match", "${resA.fn.isFullMatch}", "${resB.fn.isFullMatch}", "${resC.fn.isFullMatch}"))
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "Last Name Exact Match", "${resA.ln.isFullMatch}", "${resB.ln.isFullMatch}", "${resC.ln.isFullMatch}"))
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "Phone 10/10 Exact Match", "${resA.phone.isFullMatch}", "${resB.phone.isFullMatch}", "${resC.phone.isFullMatch}"))
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "WhatsApp 10/10 Exact Match", "${resA.wa.isFullMatch}", "${resB.wa.isFullMatch}", "${resC.wa.isFullMatch}"))
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "Total Exact Fields (4)", "${resA.exactMatches} / 4", "${resB.exactMatches} / 4", "${resC.exactMatches} / 4"))
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "Empty Box Accuracy (36)", String.format("%.1f%% (%d/36)", resA.emptyAcc, resA.emptyCorrect), String.format("%.1f%% (%d/36)", resB.emptyAcc, resB.emptyCorrect), String.format("%.1f%% (%d/36)", resC.emptyAcc, resC.emptyCorrect)))
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "Average Confidence", String.format("%.1f%%", resA.avgConfidence), String.format("%.1f%%", resB.avgConfidence), String.format("%.1f%%", resC.avgConfidence)))
    Log.i(TAG, String.format("%-28s | %-25s | %-25s | %-25s", "Inference Latency / Box", String.format("%.2f ms", resA.avgLatencyMs), String.format("%.2f ms", resB.avgLatencyMs), String.format("%.2f ms", resC.avgLatencyMs)))
    Log.i(TAG, "═══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════")

    // Per-box detail for Arm C
    Log.i(TAG, "")
    Log.i(TAG, "── PER-BOX DETAIL: ARM C (CUSTOM OMR MODEL) ──")
    Log.i(TAG, "  FIRST NAME:")
    for (i in 0 until 5) {
      val c = resC.fn.cells[i]
      Log.i(TAG, "    Box ${i + 1}: expected='${c.expected}', got='${c.recognized}', conf=${String.format("%.1f%%", c.confidence * 100)}, match=${c.isCorrect}, top3=[${c.top3}]")
    }
    Log.i(TAG, "  LAST NAME:")
    for (i in 0 until 5) {
      val c = resC.ln.cells[i]
      Log.i(TAG, "    Box ${i + 1}: expected='${c.expected}', got='${c.recognized}', conf=${String.format("%.1f%%", c.confidence * 100)}, match=${c.isCorrect}, top3=[${c.top3}]")
    }
    Log.i(TAG, "  PHONE:")
    for (i in 0 until 10) {
      val c = resC.phone.cells[i]
      Log.i(TAG, "    Box ${i + 1}: expected='${c.expected}', got='${c.recognized}', conf=${String.format("%.1f%%", c.confidence * 100)}, match=${c.isCorrect}, top3=[${c.top3}]")
    }
    Log.i(TAG, "  WHATSAPP:")
    for (i in 0 until 10) {
      val c = resC.wa.cells[i]
      Log.i(TAG, "    Box ${i + 1}: expected='${c.expected}', got='${c.recognized}', conf=${String.format("%.1f%%", c.confidence * 100)}, match=${c.isCorrect}, top3=[${c.top3}]")
    }

    // Confusions in Arm C
    Log.i(TAG, "")
    Log.i(TAG, "── CONFUSIONS IN ARM C (CUSTOM OMR MODEL) ──")
    val confusionsC = resC.fn.confusions + resC.ln.confusions + resC.phone.confusions + resC.wa.confusions
    if (confusionsC.isEmpty()) {
      Log.i(TAG, "  NONE! 100% PERFECT RECOGNITION ACROSS ALL FIELDS!")
    } else {
      for (c in confusionsC) {
        Log.i(TAG, "  $c")
      }
    }

    // Verdict
    Log.i(TAG, "")
    Log.i(TAG, "╔═══════════════════════════════════════════════════════════════════════════════════════╗")
    Log.i(TAG, "║   ARM C BENCHMARK VERDICT                                                             ║")
    Log.i(TAG, "╚═══════════════════════════════════════════════════════════════════════════════════════╝")
    val totalA = resA.charCorrect + resA.digitCorrect
    val totalB = resB.charCorrect + resB.digitCorrect
    val totalC = resC.charCorrect + resC.digitCorrect
    Log.i(TAG, "  Arm A Total Correct: $totalA / 30 (${String.format("%.1f%%", totalA / 30f * 100f)})")
    Log.i(TAG, "  Arm B Total Correct: $totalB / 30 (${String.format("%.1f%%", totalB / 30f * 100f)})")
    Log.i(TAG, "  Arm C Total Correct: $totalC / 30 (${String.format("%.1f%%", totalC / 30f * 100f)})")
    Log.i(TAG, "  Arm C Delta over Arm A: ${String.format("%+d", totalC - totalA)} characters (${String.format("%+.1f%%", (totalC - totalA) / 30f * 100f)})")
    Log.i(TAG, "  Arm C Delta over Arm B: ${String.format("%+d", totalC - totalB)} characters (${String.format("%+.1f%%", (totalC - totalB) / 30f * 100f)})")
    Log.i(TAG, "  Arm C Exact Field Matches: ${resC.exactMatches} / 4 (Arm A: ${resA.exactMatches}/4, Arm B: ${resB.exactMatches}/4)")
    Log.i(TAG, "═══════════════════════════════════════════════════════════════════════════════════════")

    emnistInterpreter.close()
    omrPocInterpreter.close()
  }
}
