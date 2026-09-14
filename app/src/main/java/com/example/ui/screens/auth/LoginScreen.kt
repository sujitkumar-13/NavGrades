package com.example.ui.screens.auth

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
          .widthIn(max = 480.dp)
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 20.dp),
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
            contentDescription = "NavGrades Logo",
            modifier = Modifier.size(54.dp, 36.dp)
          )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
          text = "NavGrades",
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
              text = "Sign in with your Google account. Access is restricted to authorized team members only.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondaryLight,
              textAlign = TextAlign.Center,
              lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Google Sign-In Button
            Button(
              onClick = {
                authViewModel.signInWithGoogle(context, com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID)
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
      }
    }
  }
}
