package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AnswerKeyItemEntity
import com.example.ui.components.OptionBubble
import com.example.ui.theme.OutlineLight
import com.example.ui.viewmodel.OmrViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnswerKeyScreen(
  keyId: String?,
  viewModel: OmrViewModel,
  onNavigateBack: () -> Unit
) {
  val context = LocalContext.current
  val existingKeySet by viewModel.selectedAnswerKeySet.collectAsState()
  val existingItems by viewModel.currentKeyItems.collectAsState()

  var keyName by remember { mutableStateOf("") }
  var numQuestions by remember { mutableIntStateOf(16) } // Default 16 questions!
  val questionPresets = listOf(16, 20, 32, 50)

  val keySelections = remember { mutableStateMapOf<Int, String>() }
  val keyMarks = remember { mutableStateMapOf<Int, Float>() }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  // Load existing key set if editing
  LaunchedEffect(keyId) {
    if (keyId != null && keyId.isNotBlank()) {
      viewModel.loadAnswerKeySet(keyId)
    } else {
      keyName = "Answer Key Set (${numQuestions} Qs)"
      val defaultPattern = listOf("A", "B", "C", "D")
      for (q in 1..numQuestions) {
        keySelections[q] = defaultPattern[(q - 1) % 4]
        keyMarks[q] = 1.0f
      }
    }
  }

  // Sync state when existing key loads
  LaunchedEffect(existingKeySet, existingItems) {
    if (keyId != null && existingKeySet != null) {
      keyName = existingKeySet!!.name
      numQuestions = existingKeySet!!.numQuestions
      for (q in 1..numQuestions) {
        val found = existingItems.find { it.questionIndex == q }
        keySelections[q] = found?.correctOption ?: "A"
        keyMarks[q] = found?.marks ?: 1.0f
      }
    }
  }

  // Ensure map entries exist when numQuestions changes
  fun updateQuestionCount(newCount: Int) {
    numQuestions = newCount
    val defaultPattern = listOf("A", "B", "C", "D")
    for (q in 1..newCount) {
      if (!keySelections.containsKey(q)) {
        keySelections[q] = defaultPattern[(q - 1) % 4]
      }
      if (!keyMarks.containsKey(q)) {
        keyMarks[q] = 1.0f
      }
    }
  }

  val totalPossibleMarks = (1..numQuestions).sumOf { (keyMarks[it] ?: 1.0f).toDouble() }.toFloat()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = if (keyId == null) "Create Answer Key" else "Edit Answer Key",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "$numQuestions Questions  •  Max Marks: ${"%.1f".format(totalPossibleMarks)} pts",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    bottomBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "$numQuestions Questions Set",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "Total: ${"%.1f".format(totalPossibleMarks)} pts",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }

          Button(
            onClick = {
              val trimmedName = keyName.trim()
              if (trimmedName.isEmpty()) {
                errorMessage = "Please enter an answer key name"
                return@Button
              }

              val finalKeyId = keyId ?: UUID.randomUUID().toString()
              val itemsList = (1..numQuestions).map { qIndex ->
                AnswerKeyItemEntity(
                  keyId = finalKeyId,
                  questionIndex = qIndex,
                  correctOption = keySelections[qIndex] ?: "A",
                  marks = keyMarks[qIndex] ?: 1.0f
                )
              }

              if (keyId == null) {
                viewModel.createAnswerKeySet(
                  name = trimmedName,
                  numQuestions = numQuestions,
                  defaultMarks = 1.0f,
                  keyMap = keySelections.toMap(),
                  onSuccess = {
                    Toast.makeText(context, "Answer Key Created Successfully!", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                  }
                )
              } else {
                viewModel.saveAnswerKeySet(
                  keyId = finalKeyId,
                  name = trimmedName,
                  numQuestions = numQuestions,
                  items = itemsList,
                  onSaved = {
                    Toast.makeText(context, "Answer Key Updated Successfully!", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                  }
                )
              }
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("save_answer_key_button")
          ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Save Key", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Configuration Header Card (Name & Question Count)
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            OutlinedTextField(
              value = keyName,
              onValueChange = {
                keyName = it
                errorMessage = null
              },
              label = { Text("Answer Key Name") },
              placeholder = { Text("e.g. Seminar Key Set A") },
              leadingIcon = {
                Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("key_name_input")
            )
          }
        }
      }

      // Question Cards (1 to numQuestions)
      items((1..numQuestions).toList()) { qNum ->
        val selectedOption = keySelections[qNum] ?: "A"
        val marks = keyMarks[qNum] ?: 1.0f

        AnswerKeyQuestionCard(
          questionNumber = qNum,
          selectedOption = selectedOption,
          marks = marks,
          onOptionSelected = { opt -> keySelections[qNum] = opt },
          onMarksChanged = { newMarks -> keyMarks[qNum] = newMarks }
        )
      }

      item {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

@Composable
fun AnswerKeyQuestionCard(
  questionNumber: Int,
  selectedOption: String,
  marks: Float,
  onOptionSelected: (String) -> Unit,
  onMarksChanged: (Float) -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(1.dp, OutlineLight),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("question_key_card_$questionNumber")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 1. Question Number Header
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "$questionNumber",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
        }
        Text(
          text = "Question $questionNumber",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      // 2. Option Section
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "Option",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.width(60.dp)
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          listOf("A", "B", "C", "D").forEach { opt ->
            OptionBubble(
              text = opt,
              isSelected = selectedOption == opt,
              onClick = { onOptionSelected(opt) },
              size = 38.dp,
              modifier = Modifier.testTag("bubble_${questionNumber}_$opt")
            )
          }
        }
      }

      // 3. Marks Section
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "Marks",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.width(60.dp)
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val markOptions = remember(marks) {
            if (marks in listOf(1f, 2f, 3f, 4f)) {
              listOf(1f, 2f, 3f, 4f)
            } else {
              (listOf(1f, 2f, 3f, 4f) + marks).distinct().sorted()
            }
          }
          markOptions.forEach { mVal ->
            val isSelected = marks == mVal
            val mText = if (mVal % 1f == 0f) mVal.toInt().toString() else mVal.toString()
            OptionBubble(
              text = mText,
              isSelected = isSelected,
              onClick = { onMarksChanged(mVal) },
              size = 38.dp,
              modifier = Modifier.testTag("marks_bubble_${questionNumber}_$mText")
            )
          }
        }
      }
    }
  }
}
