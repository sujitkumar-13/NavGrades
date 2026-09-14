package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.AuthViewModel
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.NavMint
import com.example.ui.theme.NavOrange
import com.example.ui.theme.NavPrimary
import com.example.ui.theme.NavPrimaryContainer
import com.example.ui.theme.OnNavPrimaryContainer
import com.example.ui.theme.OutlineLight
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.SurfaceVariantLight
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.theme.TextSecondaryLight

@Composable
fun LoginScreen(
  authViewModel: AuthViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val isLoading by authViewModel.isLoading.collectAsState()
  val errorMessage by authViewModel.errorMessage.collectAsState()
  var showDevOptions by remember { mutableStateOf(false) }

  Surface(
    modifier = modifier.fillMaxSize(),
    color = BackgroundLight
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState()),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        // App Logo & Brand Header
        Box(
          modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceLight)
            .border(1.5.dp, OutlineLight, RoundedCornerShape(20.dp)),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_ng_logo),
            contentDescription = "NavGrade Logo",
            modifier = Modifier.size(54.dp, 36.dp)
          )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
          text = "NavGrade",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryLight
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = "Evaluation Platform for NavGurukul Assessments",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondaryLight,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Central Authentication Card
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = SurfaceLight),
          border = BorderStroke(1.dp, OutlineLight),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = NavPrimary,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = "Authorized Team Access Only",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryLight
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = "Sign in with your Google account. If your account is not yet on the approved list, an access request will be sent to the administrator.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondaryLight,
              textAlign = TextAlign.Center,
              lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Google Sign-In Button
            Button(
              onClick = {
                authViewModel.signInWithGoogle(context)
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceLight,
                contentColor = TextPrimaryLight
              ),
              border = BorderStroke(1.5.dp, OutlineLight),
              elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
            ) {
              if (isLoading) {
                CircularProgressIndicator(
                  modifier = Modifier.size(22.dp),
                  strokeWidth = 2.dp,
                  color = NavPrimary
                )
              } else {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center
                ) {
                  // Stylized Google "G" Circle
                  Box(
                    modifier = Modifier
                      .size(26.dp)
                      .clip(CircleShape)
                      .background(SurfaceVariantLight),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = "G",
                      fontWeight = FontWeight.ExtraBold,
                      fontSize = 15.sp,
                      color = NavPrimary
                    )
                  }

                  Spacer(modifier = Modifier.width(12.dp))

                  Text(
                    text = "Sign in with Google",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryLight
                  )
                }
              }
            }

            // Error banner
            if (errorMessage != null) {
              Spacer(modifier = Modifier.height(16.dp))
              Surface(
                color = NavPrimaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = OnNavPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                  )
                  Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnNavPrimaryContainer,
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Developer Quick Test Toggle
        TextButton(
          onClick = { showDevOptions = !showDevOptions }
        ) {
          Text(
            text = if (showDevOptions) "Hide Dev Quick Logins ▲" else "Dev Quick Logins (Click to test roles) ▼",
            style = MaterialTheme.typography.labelMedium,
            color = NavOrange,
            fontWeight = FontWeight.Bold
          )
        }

        AnimatedVisibility(visible = showDevOptions) {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceVariantLight),
            border = BorderStroke(1.dp, OutlineLight)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = "Simulate Login with Test Accounts:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryLight
              )

              // 1. Admin Login
              OutlinedButton(
                onClick = {
                  authViewModel.processUserLogin("admin@navgurukul.org", "NavGrade Admin")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = NavPrimary, modifier = Modifier.size(18.dp))
                  Text("Login as Admin (admin@navgurukul.org)", fontSize = 13.sp, color = TextPrimaryLight)
                }
              }

              // 2. Team Login
              OutlinedButton(
                onClick = {
                  authViewModel.processUserLogin("team@navgurukul.org", "NavGrade Team")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(Icons.Default.Group, contentDescription = null, tint = NavMint, modifier = Modifier.size(18.dp))
                  Text("Login as Team (team@navgurukul.org)", fontSize = 13.sp, color = TextPrimaryLight)
                }
              }
            }
          }
        }
      }
    }
  }
}
