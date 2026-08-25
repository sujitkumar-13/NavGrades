package com.example.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EvaluationStatus
import com.example.data.model.QuestionEvaluation
import com.example.ui.components.OptionBubble
import com.example.ui.components.StatusBadge
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedContainer
import com.example.ui.theme.MultiplePurple
import com.example.ui.theme.MultiplePurpleContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.ui.viewmodel.OmrViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperDetailScreen(
  paperId: String,
  viewModel: OmrViewModel,
  onNavigateBack: () -> Unit
) {
  val context = LocalContext.current
  val paper by viewModel.selectedPaper.collectAsState()
  val quiz by viewModel.selectedQuiz.collectAsState()
  val evaluations by viewModel.paperEvaluations.collectAsState()

  var showDeleteDialog by remember { mutableStateOf(false) }
  var editingQuestion by remember { mutableStateOf<QuestionEvaluation?>(null) }
  var showImageModal by remember { mutableStateOf(false) }

  LaunchedEffect(paperId) {
    viewModel.loadPaper(paperId)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Paper Review",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
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
        actions = {
          IconButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.testTag("delete_paper_button")
          ) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = "Delete Paper",
              tint = MaterialTheme.colorScheme.error
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    }
  ) { paddingValues ->
    if (paper == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        Text("Loading paper details...")
      }
    } else {
      val currentPaper = paper!!
      val currentQuiz = quiz

      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Student Info & Score Overview Hero Card
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("paper_hero_card")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              // Student Name & ID Row
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = currentPaper.studentName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  if (currentQuiz != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = "Quiz: ${currentQuiz.name} (${currentQuiz.date})",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                // View Scanned Sheet Image Button if available
                if (!currentPaper.imagePath.isNullOrEmpty()) {
                  OutlinedButton(
                    onClick = { showImageModal = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("view_scanned_image_button")
                  ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View Scan", fontSize = 12.sp)
                  }
                }
              }

              // Large Score Display Banner
              Surface(
                color = if (currentPaper.percentage >= 60f) SuccessGreenContainer else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column {
                    Text(
                      text = "Score",
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = if (currentPaper.percentage >= 60f) SuccessGreen else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                      text = "${"%.1f".format(currentPaper.score)} / ${"%.1f".format(currentPaper.totalPossibleMarks)}",
                      style = MaterialTheme.typography.headlineMedium,
                      fontWeight = FontWeight.ExtraBold,
                      color = if (currentPaper.percentage >= 60f) SuccessGreen else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                  }

                  Column(horizontalAlignment = Alignment.End) {
                    Text(
                      text = "Percentage",
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = if (currentPaper.percentage >= 60f) SuccessGreen else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                      text = "${"%.1f".format(currentPaper.percentage)}%",
                      style = MaterialTheme.typography.headlineMedium,
                      fontWeight = FontWeight.ExtraBold,
                      color = if (currentPaper.percentage >= 60f) SuccessGreen else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                  }
                }
              }

              // Stat breakdown row (Correct, Wrong, Blank, Multiple)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                // Correct
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = SuccessGreenContainer,
                  modifier = Modifier.weight(1f)
                ) {
                  Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    Text(
                      text = "${currentPaper.correctCount}",
                      fontWeight = FontWeight.Bold,
                      color = SuccessGreen,
                      fontSize = 16.sp
                    )
                    Text(
                      text = "Correct",
                      style = MaterialTheme.typography.labelSmall,
                      color = SuccessGreen
                    )
                  }
                }

                // Wrong
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = ErrorRedContainer,
                  modifier = Modifier.weight(1f)
                ) {
                  Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    Text(
                      text = "${currentPaper.wrongCount}",
                      fontWeight = FontWeight.Bold,
                      color = ErrorRed,
                      fontSize = 16.sp
                    )
                    Text(
                      text = "Wrong",
                      style = MaterialTheme.typography.labelSmall,
                      color = ErrorRed
                    )
                  }
                }

                // Blank
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  modifier = Modifier.weight(1f)
                ) {
                  Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    Text(
                      text = "${currentPaper.blankCount}",
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      fontSize = 16.sp
                    )
                    Text(
                      text = "Blank",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                // Multiple / Review
                if (currentPaper.multipleCount > 0 || currentPaper.reviewRequiredCount > 0) {
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = WarningAmberContainer,
                    modifier = Modifier.weight(1f)
                  ) {
                    Column(
                      modifier = Modifier.padding(8.dp),
                      horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                      Text(
                        text = "${currentPaper.multipleCount + currentPaper.reviewRequiredCount}",
                        fontWeight = FontWeight.Bold,
                        color = WarningAmber,
                        fontSize = 16.sp
                      )
                      Text(
                        text = "Review",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber
                      )
                    }
                  }
                }
              }
            }
          }
        }

        // Scanned OMR Sheet Image Section (Embedded directly on screen)
        item {
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("scanned_omr_sheet_card")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Image,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(22.dp)
                )
                Text(
                  text = "Scanned OMR Sheet",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }

              val imagePath = currentPaper.imagePath
              val scannedBitmap = remember(imagePath) {
                if (!imagePath.isNullOrEmpty()) {
                  val file = File(imagePath)
                  if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                } else null
              }

              if (scannedBitmap != null) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .clickable { showImageModal = true }
                    .padding(6.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Image(
                    bitmap = scannedBitmap.asImageBitmap(),
                    contentDescription = "Scanned OMR Sheet",
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(10.dp))
                  )
                }
                Text(
                  text = "Tap sheet above to view full-screen zoom",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.align(Alignment.CenterHorizontally)
                )
              } else {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                  contentAlignment = Alignment.Center
                ) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Image,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(36.dp)
                    )
                    Text(
                      text = "Scanned OMR image processing",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }
        }

        item {
          Spacer(modifier = Modifier.height(24.dp))
        }
      }
    }
  }

  // Manual Correction Bottom Sheet / Dialog
  editingQuestion?.let { item ->
    var chosenAnswer by remember { mutableStateOf(item.studentAnswer) }

    AlertDialog(
      onDismissRequest = { editingQuestion = null },
      title = {
        Text("Manual Correction - Q${item.questionNumber}")
      },
      text = {
        Column(
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "Detected Student Answer: ${item.studentAnswer}   |   Correct Key: ${item.correctAnswer}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Text(
            text = "Select corrected student answer:",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
          )

          // Bubble row for A, B, C, D
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
          ) {
            listOf("A", "B", "C", "D").forEach { opt ->
              OptionBubble(
                text = opt,
                isSelected = chosenAnswer == opt,
                onClick = { chosenAnswer = opt },
                size = 44.dp,
                modifier = Modifier.testTag("dialog_bubble_$opt")
              )
            }
          }

          // Extra options: Blank & Multiple
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedButton(
              onClick = { chosenAnswer = "BLANK" },
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (chosenAnswer == "BLANK") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
              )
            ) {
              Text("Mark Blank")
            }
            OutlinedButton(
              onClick = { chosenAnswer = "MULTIPLE" },
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (chosenAnswer == "MULTIPLE") MultiplePurpleContainer else Color.Transparent
              )
            ) {
              Text("Multiple")
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.updateStudentAnswer(paperId, item.questionNumber, chosenAnswer)
            editingQuestion = null
            Toast.makeText(context, "Question ${item.questionNumber} updated & score recalculated!", Toast.LENGTH_SHORT).show()
          },
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.testTag("save_correction_button")
        ) {
          Text("Save Correction", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { editingQuestion = null }) {
          Text("Cancel")
        }
      }
    )
  }

  // Scanned Image Viewer Dialog
  if (showImageModal && paper?.imagePath != null) {
    val file = File(paper!!.imagePath!!)
    val bitmap = remember(file.absolutePath) {
      if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    AlertDialog(
      onDismissRequest = { showImageModal = false },
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Scanned OMR Sheet")
          IconButton(onClick = { showImageModal = false }) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }
      },
      text = {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f / 1.414f)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
          contentAlignment = Alignment.Center
        ) {
          if (bitmap != null) {
            Image(
              bitmap = bitmap.asImageBitmap(),
              contentDescription = "Scanned OMR Sheet",
              modifier = Modifier.fillMaxSize()
            )
          } else {
            Text("Image not available")
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showImageModal = false }) {
          Text("Close")
        }
      }
    )
  }

  // Delete Paper Dialog
  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text("Delete Scanned Paper?") },
      text = { Text("Are you sure you want to delete this student paper? This action cannot be undone.") },
      confirmButton = {
        TextButton(
          onClick = {
            showDeleteDialog = false
            viewModel.deletePaper(paperId) {
              onNavigateBack()
            }
          }
        ) {
          Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
fun QuestionEvaluationCard(
  evaluation: QuestionEvaluation,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .testTag("evaluation_card_${evaluation.questionNumber}"),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Question Number
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Q${evaluation.questionNumber}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
        }

        Column {
          Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Student: ${evaluation.studentAnswer}",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
              color = when (evaluation.status) {
                EvaluationStatus.CORRECT -> SuccessGreen
                EvaluationStatus.WRONG -> ErrorRed
                EvaluationStatus.REVIEW_REQUIRED -> WarningAmber
                EvaluationStatus.MULTIPLE -> MultiplePurple
                EvaluationStatus.BLANK -> MaterialTheme.colorScheme.onSurfaceVariant
              }
            )

            Text(
              text = "Key: ${evaluation.correctAnswer}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // Status Badge and Edit pencil
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        StatusBadge(status = evaluation.status)

        Icon(
          imageVector = Icons.Default.Edit,
          contentDescription = "Edit answer",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp)
        )
      }
    }
  }
}
