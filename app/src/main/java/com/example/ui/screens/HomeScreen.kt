package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.data.model.AnswerKeySetEntity
import com.example.data.model.QuizEntity
import com.example.data.repository.SyncStatus
import com.example.ui.components.CreateQuizDialog
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedContainer
import com.example.ui.theme.NavCoral
import com.example.ui.theme.NavOrange
import com.example.ui.theme.NavOrangeContainer
import com.example.ui.theme.NavbarBackground
import com.example.ui.theme.NavbarBorder
import com.example.ui.theme.OutlineLight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryContainer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import com.example.auth.AuthViewModel
import com.example.ui.screens.admin.AdminPanelContent
import com.example.ui.theme.SecondaryCyan
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenContainer
import com.example.ui.theme.SurfaceVariantLight
import com.example.ui.theme.TextSecondaryLight
import com.example.ui.viewmodel.OmrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  viewModel: OmrViewModel,
  onNavigateToCreateQuiz: () -> Unit,
  onNavigateToQuizDetails: (String) -> Unit,
  onNavigateToCreateAnswerKey: () -> Unit,
  onNavigateToEditAnswerKey: (String) -> Unit,
  onNavigateToAdminPanel: (() -> Unit)? = null,
  onSignOut: (() -> Unit)? = null,
  isAdmin: Boolean = false,
  userEmail: String = "",
  authViewModel: AuthViewModel? = null
) {
  var selectedTabIndex by remember { mutableIntStateOf(0) }
  val quizzes by viewModel.allQuizzes.collectAsState()
  val answerKeys by viewModel.allAnswerKeys.collectAsState()

  var keyToDelete by remember { mutableStateOf<AnswerKeySetEntity?>(null) }
  var keyToDuplicate by remember { mutableStateOf<AnswerKeySetEntity?>(null) }
  var duplicateNameText by remember { mutableStateOf("") }
  var showCreateQuizDialog by remember { mutableStateOf(false) }
  var showSignOutDialog by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(NavbarBackground)
      ) {
        TopAppBar(
          title = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Image(
                painter = painterResource(id = R.drawable.ic_ng_logo),
                contentDescription = "NavGrade Logo",
                modifier = Modifier.size(30.dp, 20.dp)
              )
              Text(
                text = "NavGrade",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
              )
            }
          },
          actions = {
            // Cloud Sync Indicator Chip
            val syncStatus by viewModel.syncStatus.collectAsState()
            Surface(
              onClick = { viewModel.triggerSync() },
              shape = RoundedCornerShape(16.dp),
              color = when (syncStatus) {
                SyncStatus.SYNCED -> SuccessGreenContainer
                SyncStatus.SYNCING -> NavOrangeContainer
                SyncStatus.OFFLINE -> SurfaceVariantLight
                SyncStatus.ERROR -> ErrorRedContainer
              },
              border = BorderStroke(
                1.dp,
                when (syncStatus) {
                  SyncStatus.SYNCED -> SuccessGreen.copy(alpha = 0.3f)
                  SyncStatus.SYNCING -> NavOrange.copy(alpha = 0.3f)
                  SyncStatus.OFFLINE -> OutlineLight
                  SyncStatus.ERROR -> ErrorRed.copy(alpha = 0.3f)
                }
              ),
              modifier = Modifier.padding(end = 4.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                      when (syncStatus) {
                        SyncStatus.SYNCED -> SuccessGreen
                        SyncStatus.SYNCING -> NavOrange
                        SyncStatus.OFFLINE -> TextSecondaryLight
                        SyncStatus.ERROR -> ErrorRed
                      }
                    )
                )
                Text(
                  text = when (syncStatus) {
                    SyncStatus.SYNCED -> "Synced"
                    SyncStatus.SYNCING -> "Syncing..."
                    SyncStatus.OFFLINE -> "Offline"
                    SyncStatus.ERROR -> "Sync Error"
                  },
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = when (syncStatus) {
                    SyncStatus.SYNCED -> SuccessGreen
                    SyncStatus.SYNCING -> NavOrange
                    SyncStatus.OFFLINE -> TextSecondaryLight
                    SyncStatus.ERROR -> ErrorRed
                  }
                )
              }
            }

            if (onSignOut != null) {
              IconButton(onClick = { showSignOutDialog = true }) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Logout,
                  contentDescription = "Sign Out",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
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
    },
    bottomBar = {
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier
          .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
          .testTag("home_bottom_bar")
      ) {
        NavigationBarItem(
          selected = selectedTabIndex == 0,
          onClick = { selectedTabIndex = 0 },
          icon = {
            Icon(
              imageVector = Icons.Default.FormatListBulleted,
              contentDescription = "Quizzes",
              modifier = Modifier.size(22.dp)
            )
          },
          label = {
            Text(
              text = "Quizzes (${quizzes.size})",
              fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
          ),
          modifier = Modifier.testTag("tab_quizzes")
        )

        NavigationBarItem(
          selected = selectedTabIndex == 1,
          onClick = { selectedTabIndex = 1 },
          icon = {
            Icon(
              imageVector = Icons.Default.FactCheck,
              contentDescription = "Answer Keys",
              modifier = Modifier.size(22.dp)
            )
          },
          label = {
            Text(
              text = "Answer Keys (${answerKeys.size})",
              fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
            )
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
          ),
          modifier = Modifier.testTag("tab_answer_keys")
        )

        if (isAdmin) {
          val pendingRequests = authViewModel?.pendingRequests?.collectAsState()?.value ?: emptyList()
          NavigationBarItem(
            selected = selectedTabIndex == 2,
            onClick = { selectedTabIndex = 2 },
            icon = {
              if (pendingRequests.isNotEmpty()) {
                BadgedBox(
                  badge = {
                    Badge(
                      containerColor = MaterialTheme.colorScheme.primary,
                      contentColor = Color.White
                    ) {
                      Text("${pendingRequests.size}")
                    }
                  }
                ) {
                  Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "Admin Panel",
                    modifier = Modifier.size(22.dp)
                  )
                }
              } else {
                Icon(
                  imageVector = Icons.Default.AdminPanelSettings,
                  contentDescription = "Admin Panel",
                  modifier = Modifier.size(22.dp)
                )
              }
            },
            label = {
              Text(
                text = "Admin",
                fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal
              )
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = MaterialTheme.colorScheme.primary,
              selectedTextColor = MaterialTheme.colorScheme.primary,
              unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
              indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ),
            modifier = Modifier.testTag("tab_admin")
          )
        }
      }
    },
    floatingActionButton = {
      if (selectedTabIndex == 0) {
        ExtendedFloatingActionButton(
          onClick = { showCreateQuizDialog = true },
          icon = { Icon(Icons.Default.Add, contentDescription = "Add Quiz") },
          text = { Text("New Quiz", fontWeight = FontWeight.Bold) },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.testTag("fab_new_quiz")
        )
      } else if (selectedTabIndex == 1) {
        ExtendedFloatingActionButton(
          onClick = onNavigateToCreateAnswerKey,
          icon = { Icon(Icons.Default.Add, contentDescription = "Add Key") },
          text = { Text("Add Answer Key", fontWeight = FontWeight.Bold) },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.testTag("fab_new_answer_key")
        )
      }
    }
  ) { paddingValues ->
    AnimatedContent(
      targetState = selectedTabIndex,
      transitionSpec = { fadeIn() togetherWith fadeOut() },
      label = "HomeTabContent"
    ) { tabIndex ->
      when (tabIndex) {
        0 -> {
          // TAB 1: QUIZZES
          QuizzesTabContent(
            quizzes = quizzes,
            answerKeys = answerKeys,
            paddingValues = paddingValues,
            onNavigateToCreateQuiz = { showCreateQuizDialog = true },
            onNavigateToQuizDetails = onNavigateToQuizDetails
          )
        }
        1 -> {
          // TAB 2: ANSWER KEYS
          AnswerKeysTabContent(
            answerKeys = answerKeys,
            quizzes = quizzes,
            paddingValues = paddingValues,
            onNavigateToCreateAnswerKey = onNavigateToCreateAnswerKey,
            onNavigateToEditAnswerKey = onNavigateToEditAnswerKey,
            onDuplicateKey = { key ->
              keyToDuplicate = key
              duplicateNameText = "${key.name} (Copy)"
            },
            onDeleteKey = { key -> keyToDelete = key }
          )
        }
        2 -> {
          // TAB 3: ADMIN PANEL
          if (authViewModel != null) {
            AdminPanelContent(
              authViewModel = authViewModel,
              modifier = Modifier.padding(paddingValues)
            )
          }
        }
      }
    }
  }

  // Create New Quiz Popup Dialog
  if (showCreateQuizDialog) {
    CreateQuizDialog(
      viewModel = viewModel,
      onDismiss = { showCreateQuizDialog = false },
      onQuizCreated = { newQuizId ->
        showCreateQuizDialog = false
        onNavigateToQuizDetails(newQuizId)
      }
    )
  }

  // Delete Answer Key Dialog
  if (keyToDelete != null) {
    val key = keyToDelete!!
    val linkedCount = quizzes.count { it.answerKeyId == key.id }

    AlertDialog(
      onDismissRequest = { keyToDelete = null },
      title = { Text("Delete Answer Key?") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Are you sure you want to delete '${key.name}'?")
          if (linkedCount > 0) {
            Text(
              text = "Warning: This answer key is currently linked to $linkedCount quiz(zes).",
              color = MaterialTheme.colorScheme.error,
              fontWeight = FontWeight.Bold
            )
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteAnswerKeySet(key.id)
            keyToDelete = null
          }
        ) {
          Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { keyToDelete = null }) {
          Text("Cancel")
        }
      }
    )
  }

  // Duplicate Answer Key Dialog
  if (keyToDuplicate != null) {
    val key = keyToDuplicate!!
    AlertDialog(
      onDismissRequest = { keyToDuplicate = null },
      title = { Text("Duplicate Answer Key") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Enter name for duplicated answer key:")
          OutlinedTextField(
            value = duplicateNameText,
            onValueChange = { duplicateNameText = it },
            label = { Text("Answer Key Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (duplicateNameText.isNotBlank()) {
              viewModel.duplicateAnswerKeySet(key.id, duplicateNameText.trim())
              keyToDuplicate = null
            }
          }
        ) {
          Text("Duplicate")
        }
      },
      dismissButton = {
        TextButton(onClick = { keyToDuplicate = null }) {
          Text("Cancel")
        }
      }
    )
  }

  // Sign Out Confirmation Dialog
  if (showSignOutDialog) {
    AlertDialog(
      onDismissRequest = { showSignOutDialog = false },
      title = { Text("Sign Out", fontWeight = FontWeight.Bold) },
      text = {
        Text(
          if (userEmail.isNotBlank()) "Are you sure you want to sign out from $userEmail?"
          else "Are you sure you want to sign out from NavGrade?"
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showSignOutDialog = false
            onSignOut?.invoke()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
          Text("Sign Out")
        }
      },
      dismissButton = {
        TextButton(onClick = { showSignOutDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
fun QuizzesTabContent(
  quizzes: List<QuizEntity>,
  answerKeys: List<AnswerKeySetEntity>,
  paddingValues: PaddingValues,
  onNavigateToCreateQuiz: () -> Unit,
  onNavigateToQuizDetails: (String) -> Unit
) {
  if (quizzes.isEmpty()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Box(
          modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Assignment,
            contentDescription = "No quizzes",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
          )
        }
        Text(
          text = "No Quizzes Yet",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "Create your 16-question seminar quiz to start printing OMR sheets, scanning with camera, and checking scores.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.fillMaxWidth(0.85f),
          textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Button(
          onClick = onNavigateToCreateQuiz,
          modifier = Modifier.testTag("empty_create_quiz_button")
        ) {
          Icon(Icons.Default.Add, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Create First Quiz (16 Qs)")
        }
      }
    }
  } else {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        Text(
          text = "Active Seminar Quizzes",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
      }

      items(quizzes, key = { it.id }) { quiz ->
        val linkedKey = answerKeys.find { it.id == quiz.answerKeyId }
        val keyLabel = linkedKey?.name ?: "Default Key (${quiz.numQuestions} Qs)"

        QuizCard(
          quiz = quiz,
          answerKeyName = keyLabel,
          onOpen = { onNavigateToQuizDetails(quiz.id) }
        )
      }

      item {
        Spacer(modifier = Modifier.height(64.dp))
      }
    }
  }
}

@Composable
fun AnswerKeysTabContent(
  answerKeys: List<AnswerKeySetEntity>,
  quizzes: List<QuizEntity>,
  paddingValues: PaddingValues,
  onNavigateToCreateAnswerKey: () -> Unit,
  onNavigateToEditAnswerKey: (String) -> Unit,
  onDuplicateKey: (AnswerKeySetEntity) -> Unit,
  onDeleteKey: (AnswerKeySetEntity) -> Unit
) {
  if (answerKeys.isEmpty()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Box(
          modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
          )
        }
        Text(
          text = "No Answer Keys Yet",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "Create named answer keys (e.g. Set A, Set B, 16 Questions) with correct options and custom marks, then link them to any quiz.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.fillMaxWidth(0.85f),
          textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Button(
          onClick = onNavigateToCreateAnswerKey,
          modifier = Modifier.testTag("empty_create_answer_key_button")
        ) {
          Icon(Icons.Default.Add, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add First Answer Key")
        }
      }
    }
  } else {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        Text(
          text = "Available Answer Keys (${answerKeys.size})",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
      }

      items(answerKeys, key = { it.id }) { keySet ->
        val usageCount = quizzes.count { it.answerKeyId == keySet.id }

        AnswerKeyCard(
          keySet = keySet,
          usageCount = usageCount,
          onEdit = { onNavigateToEditAnswerKey(keySet.id) },
          onDuplicate = { onDuplicateKey(keySet) },
          onDelete = { onDeleteKey(keySet) }
        )
      }

      item {
        Spacer(modifier = Modifier.height(64.dp))
      }
    }
  }
}

@Composable
fun QuizCard(
  quiz: QuizEntity,
  answerKeyName: String,
  onOpen: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("quiz_card_${quiz.id}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(1.dp, OutlineLight),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = quiz.name,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Date Chip
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
      ) {
        Icon(
          imageVector = Icons.Default.CalendarMonth,
          contentDescription = "Date",
          tint = NavOrange,
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = quiz.date,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Button(
        onClick = onOpen,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("open_quiz_button_${quiz.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary
        )
      ) {
        Text(
          text = "Open Quiz",
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp
        )
      }
    }
  }
}

@Composable
fun AnswerKeyCard(
  keySet: AnswerKeySetEntity,
  usageCount: Int,
  onEdit: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("answer_key_card_${keySet.id}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(1.dp, OutlineLight),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(PrimaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Key,
              contentDescription = null,
              tint = PrimaryBlue,
              modifier = Modifier.size(20.dp)
            )
          }

          Column {
            Text(
              text = keySet.name,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "${keySet.numQuestions} Questions  •  Used in $usageCount Quiz${if (usageCount == 1) "" else "zes"}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Box {
          IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options")
          }
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
          ) {
            DropdownMenuItem(
              text = { Text("Edit Answer Key") },
              leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onEdit()
              }
            )
            DropdownMenuItem(
              text = { Text("Duplicate") },
              leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onDuplicate()
              }
            )
            DropdownMenuItem(
              text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
              leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
              onClick = {
                menuExpanded = false
                onDelete()
              }
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedButton(
          onClick = onDuplicate,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Duplicate", fontSize = 13.sp)
        }

        Button(
          onClick = onEdit,
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          modifier = Modifier.weight(1.2f)
        ) {
          Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Edit Key", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
      }
    }
  }
}
