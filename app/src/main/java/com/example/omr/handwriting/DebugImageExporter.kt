package com.example.omr.handwriting

import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Exports named debug PNG artifacts for inspection during development and tuning.
 * Fully disabled by default via OmrHandwritingConfig.isDebugExportEnabled.
 */
object DebugImageExporter {

  private const val TAG = "DEBUG_EXPORTER"

  var debugOutputDir: File? = null

  fun exportAll(
    sheet: Bitmap,
    fnCrop: Bitmap?,
    fnResult: BoxedFieldResult?,
    lnResult: BoxedFieldResult?,
    phoneResult: BoxedFieldResult?,
    waResult: BoxedFieldResult?,
    cityResult: RecognitionResult?,
    schoolResult: RecognitionResult?
  ) {
    val dir = debugOutputDir ?: return
    try {
      if (!dir.exists()) dir.mkdirs()

      saveBitmap(sheet, File(dir, "01_rectified_sheet.png"))
      fnCrop?.let { saveBitmap(it, File(dir, "02_fn_crop.png")) }
      Log.i(TAG, "Successfully exported debug images to ${dir.absolutePath}")
    } catch (e: Exception) {
      Log.w(TAG, "Error exporting debug images: ${e.message}")
    }
  }

  fun saveBitmap(bitmap: Bitmap, targetFile: File) {
    try {
      FileOutputStream(targetFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to save debug bitmap to ${targetFile.name}: ${e.message}")
    }
  }
}
