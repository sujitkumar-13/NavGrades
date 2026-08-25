package com.example.omr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object OmrPrintHelper {

  fun shareOrPrintOmrSheet(context: Context, bitmap: Bitmap, title: String) {
    try {
      val cachePath = File(context.cacheDir, "omr_sheets")
      if (!cachePath.exists()) cachePath.mkdirs()
      val file = File(cachePath, "OMR_Sheet_${System.currentTimeMillis()}.png")
      val stream = FileOutputStream(file)
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
      stream.flush()
      stream.close()

      val contentUri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
      )

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "Printable OMR Answer Sheet for $title")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      context.startActivity(Intent.createChooser(shareIntent, "Print or Share OMR Sheet"))
    } catch (e: Exception) {
      e.printStackTrace()
      Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
  }
}
