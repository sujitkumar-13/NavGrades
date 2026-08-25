package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.QuizEntity
import com.example.data.model.ScannedPaperEntity
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat

object QuizCsvExporter {

  // CSV Column Headers in exact requested order
  val CSV_HEADERS = listOf(
    "FirstName",
    "MiddleName",
    "LastName",
    "Gender",
    "DOB",
    "Email",
    "PhoneNumber",
    "WhatsappNumber",
    "State",
    "City",
    "District",
    "Block",
    "PinCode",
    "Qualification",
    "CurrentStatus",
    "Cast",
    "School",
    "Campus",
    "QuestionSetName",
    "ExamCentre",
    "DateOfTest",
    "ObtainedMarks",
    "ExamStatus"
  )

  data class CsvRowData(
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val gender: String = "",
    val dob: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val whatsappNumber: String = "",
    val state: String = "",
    val city: String = "",
    val district: String = "",
    val block: String = "",
    val pinCode: String = "",
    val qualification: String = "",
    val currentStatus: String = "",
    val cast: String = "",
    val school: String = "",
    val campus: String = "",
    val questionSetName: String = "",
    val examCentre: String = "",
    val dateOfTest: String = "",
    val obtainedMarks: String = "",
    val examStatus: String = ""
  )

  fun mapPaperToCsvRow(
    paper: ScannedPaperEntity,
    quiz: QuizEntity?,
    defaultKeyName: String
  ): CsvRowData {
    val setName = if (paper.questionSetName.isNotBlank()) {
      formatSetName(paper.questionSetName)
    } else {
      formatSetName(defaultKeyName)
    }

    // Format obtained marks cleanly (e.g. 24 instead of 24.0)
    val marksStr = if (paper.score % 1.0f == 0.0f) {
      paper.score.toInt().toString()
    } else {
      DecimalFormat("#.##").format(paper.score)
    }

    return CsvRowData(
      firstName = paper.studentName.trim(),
      middleName = "",
      lastName = "",
      gender = paper.gender.trim(),
      dob = "",
      email = "",
      phoneNumber = "",
      whatsappNumber = paper.whatsappNumber.trim(),
      state = "",
      city = "",
      district = "",
      block = paper.block.trim(),
      pinCode = "",
      qualification = paper.qualification.trim(),
      currentStatus = "",
      cast = paper.cast.trim(),
      school = "",
      campus = "",
      questionSetName = setName,
      examCentre = "",
      dateOfTest = "",
      obtainedMarks = marksStr,
      examStatus = ""
    )
  }

  fun formatSetName(raw: String): String {
    val upper = raw.uppercase()
    return when {
      upper.contains("SET - A") || upper.contains("SET-A") || upper.contains("SET A") || upper.contains("SET 1") || upper.contains("SET1") || upper.contains("KEY A") -> "Set - A"
      upper.contains("SET - B") || upper.contains("SET-B") || upper.contains("SET B") || upper.contains("SET 2") || upper.contains("SET2") || upper.contains("KEY B") -> "Set - B"
      raw.isNotBlank() -> raw
      else -> "Set - A"
    }
  }

  fun generateCsvContent(
    papers: List<ScannedPaperEntity>,
    quiz: QuizEntity?,
    defaultKeyName: String = "Set - A"
  ): String {
    val sb = StringBuilder()
    // 1. Header row
    sb.append(CSV_HEADERS.joinToString(",")).append("\n")

    // 2. Data rows
    papers.forEach { paper ->
      val row = mapPaperToCsvRow(paper, quiz, defaultKeyName)
      val values = listOf(
        row.firstName,
        row.middleName,
        row.lastName,
        row.gender,
        row.dob,
        row.email,
        row.phoneNumber,
        row.whatsappNumber,
        row.state,
        row.city,
        row.district,
        row.block,
        row.pinCode,
        row.qualification,
        row.currentStatus,
        row.cast,
        row.school,
        row.campus,
        row.questionSetName,
        row.examCentre,
        row.dateOfTest,
        row.obtainedMarks,
        row.examStatus
      )
      val escapedLine = values.joinToString(",") { escapeCsvCell(it) }
      sb.append(escapedLine).append("\n")
    }

    return sb.toString()
  }

  private fun escapeCsvCell(cell: String): String {
    if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
      return "\"${cell.replace("\"", "\"\"")}\""
    }
    return cell
  }

  fun exportCsvToFile(context: Context, quizName: String, csvContent: String): File {
    val cleanName = quizName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
    val exportDir = File(context.cacheDir, "omr_exports").apply { mkdirs() }
    val file = File(exportDir, "Quiz_${cleanName}_Scanned_Results.csv")
    FileOutputStream(file).use { out ->
      out.write(csvContent.toByteArray(Charsets.UTF_8))
    }
    return file
  }

  fun shareCsvFile(context: Context, file: File, quizName: String) {
    try {
      val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
      )
      val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "OMR Scanned Papers Export - $quizName")
        putExtra(Intent.EXTRA_TEXT, "Exported OMR evaluation data for quiz: $quizName (${file.name})")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val chooser = Intent.createChooser(sendIntent, "Export CSV to...")
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(chooser)
    } catch (e: Exception) {
      e.printStackTrace()
      Toast.makeText(context, "Could not open share dialog: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
  }

  fun copyToClipboard(context: Context, csvContent: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("OMR Quiz CSV", csvContent)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "CSV copied to clipboard!", Toast.LENGTH_SHORT).show()
  }
}
