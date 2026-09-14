package com.example.ui.screens.admin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthViewModel
import com.example.data.remote.model.AccessRequestRemote
import com.example.data.remote.model.ApprovedUserRemote
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedContainer
import com.example.ui.theme.NavMint
import com.example.ui.theme.NavMintContainer
import com.example.ui.theme.NavOrange
import com.example.ui.theme.NavOrangeContainer
import com.example.ui.theme.NavPrimary
import com.example.ui.theme.NavPrimaryContainer
import com.example.ui.theme.NavPurple
import com.example.ui.theme.NavPurpleContainer
import com.example.ui.theme.NavbarBackground
import com.example.ui.theme.NavbarBorder
import com.example.ui.theme.OnNavMintContainer
import com.example.ui.theme.OnNavOrangeContainer
import com.example.ui.theme.OnNavPrimaryContainer
import com.example.ui.theme.OnNavPurpleContainer
import com.example.ui.theme.OutlineLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenContainer
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.SurfaceVariantLight
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.theme.TextSecondaryLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
  authViewModel: AuthViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffold(
    containerColor = BackgroundLight,
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
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = NavPrimary,
                modifier = Modifier.size(24.dp)
              )
              Text(
                text = "Admin Panel",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryLight
              )
            }
          },
          navigationIcon = {
            IconButton(onClick = onNavigateBack) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimaryLight
              )
            }
          },
          actions = {
            IconButton(onClick = { authViewModel.loadAdminData() }) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = TextPrimaryLight
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
        // Border under top bar
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(NavbarBorder)
        )
      }
    }
  ) { paddingValues ->
    AdminPanelContent(
      authViewModel = authViewModel,
      modifier = modifier.padding(paddingValues)
    )
  }
}

@Composable
fun AdminPanelContent(
  authViewModel: AuthViewModel,
  modifier: Modifier = Modifier
) {
  var selectedTabIndex by remember { mutableIntStateOf(0) }
  val pendingRequests by authViewModel.pendingRequests.collectAsState()
  val teamMembers by authViewModel.teamMembers.collectAsState()
  val isLoading by authViewModel.isLoading.collectAsState()

  var showAddUserDialog by remember { mutableStateOf(false) }
  var newEmailText by remember { mutableStateOf("") }
  var newNameText by remember { mutableStateOf("") }
  var searchQuery by remember { mutableStateOf("") }

  LaunchedEffect(Unit) {
    authViewModel.loadAdminData()
  }

  Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Tab Navigation Row with Refresh
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(SurfaceLight),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TabRow(
          selectedTabIndex = selectedTabIndex,
          containerColor = SurfaceLight,
          contentColor = NavPrimary,
          modifier = Modifier.weight(1f),
          indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
              modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
              color = NavPrimary,
              height = 3.dp
            )
          }
        ) {
          Tab(
            selected = selectedTabIndex == 0,
            onClick = { selectedTabIndex = 0 },
            text = {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                  text = "Requests (${pendingRequests.size})",
                  fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                )
              }
            }
          )

          Tab(
            selected = selectedTabIndex == 1,
            onClick = { selectedTabIndex = 1 },
            text = {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                  text = "Team (${teamMembers.size})",
                  fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                )
              }
            }
          )
        }

        IconButton(
          onClick = { authViewModel.loadAdminData() },
          modifier = Modifier.padding(end = 4.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Refresh",
            tint = TextSecondaryLight
          )
        }
      }

      // Border under tabs
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(1.dp)
          .background(NavbarBorder)
      )

      // Tab Content
      AnimatedContent(
        targetState = selectedTabIndex,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "AdminTabs"
      ) { tabIndex ->
        if (tabIndex == 0) {
          // TAB 0: PENDING REQUESTS
          PendingRequestsList(
            requests = pendingRequests,
            isLoading = isLoading,
            onApprove = { req -> authViewModel.approveRequest(req, role = "team") },
            onDeny = { reqId -> authViewModel.denyRequest(reqId) }
          )
        } else {
          // TAB 1: TEAM MEMBERS
          TeamMembersList(
            members = teamMembers.filter {
              it.name.contains(searchQuery, ignoreCase = true) ||
                it.email.contains(searchQuery, ignoreCase = true)
            },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            isLoading = isLoading,
            onUpdateRole = { email, newRole -> authViewModel.updateMemberRole(email, newRole) },
            onRevoke = { email -> authViewModel.revokeMemberAccess(email) }
          )
        }
      }
    }

    // Floating Action Button when on Team tab
    if (selectedTabIndex == 1) {
      FloatingActionButton(
        onClick = { showAddUserDialog = true },
        containerColor = NavPrimary,
        contentColor = SurfaceLight,
        shape = CircleShape,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
      ) {
        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Add User")
      }
    }
  }

  // Dialog: Add Whitelist User Directly
  if (showAddUserDialog) {
    AlertDialog(
      onDismissRequest = { showAddUserDialog = false },
      title = {
        Text("Add Team Member", fontWeight = FontWeight.Bold)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            "Directly whitelist a Google email so they can log in without waiting for approval.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryLight
          )
          OutlinedTextField(
            value = newNameText,
            onValueChange = { newNameText = it },
            label = { Text("Full Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = newEmailText,
            onValueChange = { newEmailText = it },
            label = { Text("Google Account Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newEmailText.isNotBlank()) {
              val fakeReq = AccessRequestRemote(
                id = "direct-${System.currentTimeMillis()}",
                email = newEmailText.trim(),
                name = newNameText.trim().ifBlank { newEmailText.substringBefore("@") },
                status = "approved"
              )
              authViewModel.approveRequest(fakeReq, "team")
              newEmailText = ""
              newNameText = ""
              showAddUserDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = NavPrimary)
        ) {
          Text("Add Member")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddUserDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun PendingRequestsList(
  requests: List<AccessRequestRemote>,
  isLoading: Boolean,
  onApprove: (AccessRequestRemote) -> Unit,
  onDeny: (String) -> Unit
) {
  if (isLoading && requests.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(color = NavPrimary)
    }
    return
  }

  if (requests.isEmpty()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(32.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
          modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(SuccessGreenContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = SuccessGreen,
            modifier = Modifier.size(32.dp)
          )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "No Pending Requests",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryLight
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "All access requests have been reviewed.",
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondaryLight
        )
      }
    }
    return
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    items(requests, key = { it.id }) { request ->
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.dp, OutlineLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(NavOrangeContainer),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = request.name.take(1).uppercase().ifBlank { "U" },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = OnNavOrangeContainer
              )
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = request.name.ifBlank { "New Requester" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryLight
              )
              Text(
                text = request.email,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryLight
              )
              if (!request.requestedAt.isNullOrBlank()) {
                Text(
                  text = "Requested: ${request.requestedAt}",
                  fontSize = 11.sp,
                  color = TextSecondaryLight
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Approve Button
            Button(
              onClick = { onApprove(request) },
              modifier = Modifier
                .weight(1f)
                .height(40.dp),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = SuccessGreen,
                contentColor = SurfaceLight
              )
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(Icons.Default.Check, contentDescription = "Approve", modifier = Modifier.size(16.dp))
                Text("Approve", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            }

            // Deny Button
            OutlinedButton(
              onClick = { onDeny(request.id) },
              modifier = Modifier
                .weight(1f)
                .height(40.dp),
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, ErrorRed),
              colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ErrorRed
              )
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(Icons.Default.Close, contentDescription = "Deny", modifier = Modifier.size(16.dp))
                Text("Deny", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TeamMembersList(
  members: List<ApprovedUserRemote>,
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  isLoading: Boolean,
  onUpdateRole: (String, String) -> Unit,
  onRevoke: (String) -> Unit
) {
  var userToRevoke by remember { mutableStateOf<ApprovedUserRemote?>(null) }

  Column(modifier = Modifier.fillMaxSize()) {
    // Search Bar
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
      color = SurfaceLight,
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, OutlineLight)
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = "Search",
          tint = TextSecondaryLight,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = { Text("Search by name or email...", fontSize = 14.sp) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
          )
        )
      }
    }

    if (isLoading && members.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = NavPrimary)
      }
      return
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(members, key = { it.email }) { member ->
        var showMenu by remember { mutableStateOf(false) }

        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = SurfaceLight),
          border = BorderStroke(1.dp, OutlineLight)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                  if (member.role == "admin") NavPurpleContainer else NavMintContainer
                ),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = member.name.take(1).uppercase().ifBlank { "U" },
                fontWeight = FontWeight.Bold,
                color = if (member.role == "admin") OnNavPurpleContainer else OnNavMintContainer,
                fontSize = 16.sp
              )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  text = member.name.ifBlank { member.email.substringBefore("@") },
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimaryLight,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )

                // Role Badge
                Surface(
                  color = if (member.role == "admin") NavPurpleContainer else NavMintContainer,
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text(
                    text = member.role.uppercase(),
                    color = if (member.role == "admin") OnNavPurpleContainer else OnNavMintContainer,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.height(2.dp))

              Text(
                text = member.email,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            // Options Menu
            Box {
              IconButton(onClick = { showMenu = true }) {
                Icon(
                  imageVector = Icons.Default.MoreVert,
                  contentDescription = "Options",
                  tint = TextSecondaryLight
                )
              }

              DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
              ) {
                if (member.role == "admin") {
                  DropdownMenuItem(
                    text = { Text("Change to Team") },
                    onClick = {
                      onUpdateRole(member.email, "team")
                      showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) }
                  )
                } else {
                  DropdownMenuItem(
                    text = { Text("Promote to Admin") },
                    onClick = {
                      onUpdateRole(member.email, "admin")
                      showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) }
                  )
                }

                DropdownMenuItem(
                  text = { Text("Revoke Access", color = ErrorRed) },
                  onClick = {
                    showMenu = false
                    userToRevoke = member
                  },
                  leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ErrorRed) }
                )
              }
            }
          }
        }
      }
    }
  }

  // Revoke Access Confirmation Dialog
  userToRevoke?.let { member ->
    AlertDialog(
      onDismissRequest = { userToRevoke = null },
      title = { Text("Revoke Access", fontWeight = FontWeight.Bold) },
      text = {
        Text("Are you sure you want to remove ${member.email} from authorized users? They will no longer be able to log in.")
      },
      confirmButton = {
        Button(
          onClick = {
            onRevoke(member.email)
            userToRevoke = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
        ) {
          Text("Revoke Access")
        }
      },
      dismissButton = {
        TextButton(onClick = { userToRevoke = null }) {
          Text("Cancel")
        }
      }
    )
  }
}
