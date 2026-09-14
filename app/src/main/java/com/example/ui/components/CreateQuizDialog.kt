package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.viewmodel.OmrViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CreateQuizDialog(
  viewModel: OmrViewModel,
  onDismiss: () -> Unit,
  onQuizCreated: (String) -> Unit
) {
  val defaultDate = remember {
    SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
  }

  val answerKeys by viewModel.allAnswerKeys.collectAsState()

  var quizName by remember { mutableStateOf("") }
  val selectedKeyId by remember(answerKeys) {
    mutableStateOf(answerKeys.firstOrNull { it.numQuestions == 16 }?.id ?: answerKeys.firstOrNull()?.id)
  }
  var defaultMarksText by remember { mutableStateOf("1") }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .testTag("create_quiz_dialog"),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Header with title and close button
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Create New Quiz",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(32.dp).testTag("close_quiz_dialog_button")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Quiz Name
        OutlinedTextField(
          value = quizName,
          onValueChange = {
            quizName = it
            errorMessage = null
          },
          label = { Text("Quiz Name") },
          placeholder = { Text("e.g. Sukma SOB 2026") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Assignment,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
          },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("quiz_name_input"),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
          ),
          shape = RoundedCornerShape(12.dp)
        )

        // Default Marks Per Question
        OutlinedTextField(
          value = defaultMarksText,
          onValueChange = {
            if (it.isEmpty() || it.all { char -> char.isDigit() || char == '.' }) {
              defaultMarksText = it
            }
          },
          label = { Text("Default Marks Per Question") },
          placeholder = { Text("1") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("default_marks_input"),
          shape = RoundedCornerShape(12.dp)
        )

        // Error message if any
        if (errorMessage != null) {
          Text(
            text = errorMessage!!,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Create Quiz Button
        Button(
          onClick = {
            if (quizName.isBlank()) {
              errorMessage = "Please enter a quiz name"
              return@Button
            }
            val qCount = 16
            val marks = defaultMarksText.toFloatOrNull() ?: 1.0f

            viewModel.createQuiz(
              name = quizName.trim(),
              date = defaultDate,
              numQuestions = qCount,
              answerKeyId = selectedKeyId,
              defaultMarks = marks,
              onSuccess = { newQuizId ->
                onQuizCreated(newQuizId)
              }
            )
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("create_quiz_submit_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
          )
        ) {
          Text(
            text = "Create Quiz",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
          )
        }
      }
    }
  }
}
