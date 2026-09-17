package com.example.omr.handwriting

import android.graphics.Bitmap
import android.graphics.RectF
import com.example.omr.OmrLayoutDefinition

/**
 * Geometric field crops extracted from a rectified OMR sheet.
 * Decouples physical sheet geometry and cropping from OCR recognition logic.
 */
data class OmrFieldCrops(
  val rectifiedSheet: Bitmap,
  val cityCrop: Bitmap,
  val schoolCrop: Bitmap,
  val courseCodeCrop: Bitmap? = null,
  val firstNameCrop: Bitmap? = null,
  val lastNameCrop: Bitmap? = null,
  val phoneCrop: Bitmap? = null,
  val whatsappCrop: Bitmap? = null
) {
  companion object {
    /**
     * Deterministically extracts all text/handwriting field crops from a rectified
     * 682x1024 OMR sheet using the calibrated regions defined in OmrLayoutDefinition.
     */
    fun fromRectifiedSheet(sheet: Bitmap): OmrFieldCrops {
      return OmrFieldCrops(
        rectifiedSheet = sheet,
        cityCrop = cropRegion(sheet, OmrLayoutDefinition.CITY_REGION),
        schoolCrop = cropRegion(sheet, OmrLayoutDefinition.SCHOOL_REGION),
        courseCodeCrop = null,
        firstNameCrop = cropRegion(sheet, OmrLayoutDefinition.FIRST_NAME_BOXES_REGION),
        lastNameCrop = cropRegion(sheet, OmrLayoutDefinition.LAST_NAME_BOXES_REGION),
        phoneCrop = cropRegion(sheet, OmrLayoutDefinition.PHONE_BOXES_REGION),
        whatsappCrop = cropRegion(sheet, OmrLayoutDefinition.WHATSAPP_BOXES_REGION)
      )
    }

    private fun cropRegion(bitmap: Bitmap, region: RectF): Bitmap {
      val bW = bitmap.width
      val bH = bitmap.height
      val cropX = (region.left * bW).toInt().coerceIn(0, bW - 1)
      val cropY = (region.top * bH).toInt().coerceIn(0, bH - 1)
      val cropW = ((region.right - region.left) * bW).toInt().coerceIn(1, bW - cropX)
      val cropH = ((region.bottom - region.top) * bH).toInt().coerceIn(1, bH - cropY)
      return Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
    }
  }
}
