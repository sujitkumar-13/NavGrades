package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NavMint
import com.example.ui.theme.NavMintContainer
import com.example.ui.theme.NavOrange
import com.example.ui.theme.NavOrangeContainer
import com.example.ui.theme.NavPurple
import com.example.ui.theme.NavPurpleContainer
import com.example.ui.theme.NavbarBackground
import com.example.ui.theme.NavbarBorder
import com.example.ui.theme.OutlineLight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryCyan
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.ui.viewmodel.OmrViewModel
import com.example.util.QuizCsvExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizDetailsScreen(
  quizId: String,
  viewModel: OmrViewModel,
  onNavigateBack: () -> Unit,
  onNavigateToAnswerKey: (String) -> Unit,
  onNavigateToPrintSheet: ((String) -> Unit)? = null,
  onNavigateToScan: (String) -> Unit,
  onNavigateToReviewPapers: (String) -> Unit
) {
  val quiz by viewModel.selectedQuiz.collectAsState()
  val papers by viewModel.papers.collectAsState()
  val allAnswerKeys by viewModel.allAnswerKeys.collectAsState()

  var showDeleteDialog by remember { mutableStateOf(false) }
  var showChangeKeyDialog by remember { mutableStateOf(false) }
  var showExportDialog by remember { mutableStateOf(false) }

  LaunchedEffect(quizId) {
    viewModel.loadQuiz(quizId)
  }

  val totalPapers = papers.size
  val avgScore = if (papers.isNotEmpty()) papers.map { it.score }.average().toFloat() else 0f
  val avgPercentage = if (papers.isNotEmpty()) papers.map { it.percentage }.average().toFloat() else 0f
  val highestScore = if (papers.isNotEmpty()) papers.maxOf { it.score } else 0f
  val reviewNeededCount = papers.count { it.reviewRequiredCount > 0 }

  val linkedKey = allAnswerKeys.find { it.id == quiz?.answerKeyId }
  val keyName = linkedKey?.name ?: "Default Key (${quiz?.numQuestions ?: 16} Qs)"

  Scaffold(
    topBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(NavbarBackground)
      ) {
        TopAppBar(
          title = {
            Text(
              text = quiz?.name ?: "Quiz Details",
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
              modifier = Modifier.testTag("delete_quiz_button")
            ) {
              Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Quiz",
                tint = MaterialTheme.colorScheme.error
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
          )
        )
        // Border-bottom under navbar
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(NavbarBorder)
        )
      }
    }
  ) { paddingValues ->
    if (quiz == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        Text("Loading quiz...")
      }
    } else {
      val currentQuiz = quiz!!

      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Quiz Overview Hero Card
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.5.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = currentQuiz.name,
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = currentQuiz.date,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            // Stats row (Papers scanned)
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "$totalPapers",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = "Scanned",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        // Primary Action Grid (Workflow Actions in Exact Order: Update Answer Key -> Scan Papers -> Review Papers -> Export)
        Text(
          text = "Workflow Actions",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )

        // 1. Update Answer Key
        WorkflowActionCard(
          title = "Update Answer Key",
          subtitle = "View & customize correct answers and question marks for this quiz",
          icon = Icons.Default.FactCheck,
          iconBgColor = MaterialTheme.colorScheme.primaryContainer,
          iconTint = MaterialTheme.colorScheme.primary,
          onClick = {
            if (currentQuiz.answerKeyId != null) {
              onNavigateToAnswerKey(currentQuiz.answerKeyId!!)
            } else {
              showChangeKeyDialog = true
            }
          },
          testTag = "action_answer_key"
        )

        // 2. Camera Scan Papers
        WorkflowActionCard(
          title = "Scan Papers",
          subtitle = "Point camera at completed student OMR sheets to detect marks instantly",
          icon = Icons.Default.CameraAlt,
          iconBgColor = NavPurpleContainer,
          iconTint = NavPurple,
          onClick = { onNavigateToScan(quizId) },
          testTag = "action_scan_papers"
        )

        // 3. Review Papers & Results
        WorkflowActionCard(
          title = "Review Papers",
          subtitle = "Check scores, question breakdown, and perform manual bubble corrections",
          icon = Icons.Default.RateReview,
          iconBgColor = NavMintContainer,
          iconTint = NavMint,
          badgeText = if (totalPapers > 0) "$totalPapers Papers" else null,
          badgeColor = NavMintContainer,
          badgeTextColor = NavMint,
          onClick = { onNavigateToReviewPapers(quizId) },
          testTag = "action_review_papers"
        )

        // 4. Export Scanned Papers to CSV
        WorkflowActionCard(
          title = "Export",
          subtitle = "Export all scanned student papers to CSV (FirstName, Caste, Marks, etc.)",
          icon = Icons.Default.FileDownload,
          iconBgColor = NavOrangeContainer,
          iconTint = NavOrange,
          badgeText = ".CSV",
          badgeColor = NavOrangeContainer,
          badgeTextColor = NavOrange,
          onClick = { showExportDialog = true },
          testTag = "action_export_csv"
        )

        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }

  // Export CSV Dialog
  if (showExportDialog && quiz != null) {
    val currentQuiz = quiz!!
    val context = LocalContext.current
    val defaultSetName = linkedKey?.name ?: "Set - A"
    val csvContent = remember(papers, currentQuiz, defaultSetName) {
      QuizCsvExporter.generateCsvContent(papers, currentQuiz, defaultSetName)
    }

    AlertDialog(
      onDismissRequest = { showExportDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Default.TableChart,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp)
        )
      },
      title = {
        Text(
          text = "Export Quiz Results",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Text(
            text = "Exporting ${papers.size} scanned paper(s) for '${currentQuiz.name}' in 23-column CSV format.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          // Summary Info Card
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Scanned Papers:",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "${papers.size}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Question Set:",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = QuizCsvExporter.formatSetName(defaultSetName),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Columns:",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "23 Standard Fields",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          // CSV Preview Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "CSV Preview:",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(
              onClick = {
                viewModel.seedSamplePapers(currentQuiz.id) {
                  Toast.makeText(context, "Added 7 sample student test papers!", Toast.LENGTH_SHORT).show()
                }
              },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text(
                text = "+ Add Sample Test Data",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }

          // Horizontal scrollable code container
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .height(130.dp)
              .horizontalScroll(rememberScrollState()),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f)
          ) {
            Text(
              text = if (papers.isNotEmpty()) csvContent else "No papers scanned yet to export.",
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
              ),
              color = MaterialTheme.colorScheme.inverseOnSurface,
              modifier = Modifier.padding(10.dp)
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (papers.isNotEmpty()) {
              val file = QuizCsvExporter.exportCsvToFile(context, currentQuiz.name, csvContent)
              QuizCsvExporter.shareCsvFile(context, file, currentQuiz.name)
            } else {
              Toast.makeText(context, "No scanned papers to export!", Toast.LENGTH_SHORT).show()
            }
          },
          enabled = papers.isNotEmpty(),
          modifier = Modifier.testTag("button_share_csv")
        ) {
          Icon(
            imageVector = Icons.Default.Share,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Share / Send CSV")
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          if (papers.isNotEmpty()) {
            OutlinedButton(
              onClick = {
                QuizCsvExporter.copyToClipboard(context, csvContent)
              },
              modifier = Modifier.testTag("button_copy_csv")
            ) {
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Copy")
            }
          }
          TextButton(onClick = { showExportDialog = false }) {
            Text("Close")
          }
        }
      }
    )
  }

  // Change Answer Key Dialog
  if (showChangeKeyDialog) {
    var chosenKeyId by remember(quiz?.answerKeyId) { mutableStateOf(quiz?.answerKeyId ?: "") }

    AlertDialog(
      onDismissRequest = { showChangeKeyDialog = false },
      title = { Text("Select Answer Key for Quiz") },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = "Choose an answer key. Quizzes will automatically grade papers using this key:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          allAnswerKeys.forEach { keySet ->
            val isSelected = chosenKeyId == keySet.id
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { chosenKeyId = keySet.id },
              shape = RoundedCornerShape(10.dp),
              color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
              border = BorderStroke(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
              )
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                RadioButton(
                  selected = isSelected,
                  onClick = { chosenKeyId = keySet.id }
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = keySet.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "${keySet.numQuestions} Questions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (chosenKeyId.isNotBlank()) {
              viewModel.updateQuizAnswerKey(quizId, chosenKeyId) {
                showChangeKeyDialog = false
              }
            }
          }
        ) {
          Text("Assign Key")
        }
      },
      dismissButton = {
        TextButton(onClick = { showChangeKeyDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // Delete Quiz Dialog
  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text("Delete Quiz?") },
      text = { Text("Are you sure you want to delete '${quiz?.name}' and all scanned student papers? This action cannot be undone.") },
      confirmButton = {
        TextButton(
          onClick = {
            showDeleteDialog = false
            viewModel.deleteQuiz(quizId) {
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
fun WorkflowActionCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  iconBgColor: Color,
  iconTint: Color,
  badgeText: String? = null,
  badgeColor: Color? = null,
  badgeTextColor: Color? = null,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .testTag(testTag),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(1.dp, OutlineLight),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(iconBgColor),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(24.dp)
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )

          if (badgeText != null) {
            Surface(
              color = badgeColor ?: MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = badgeTextColor ?: MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
        }

        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = "Navigate",
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(20.dp)
      )
    }
  }
}
