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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.AuthViewModel
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedContainer
import com.example.ui.theme.NavPrimary
import com.example.ui.theme.OutlineLight
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.SurfaceVariantLight
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.theme.TextSecondaryLight

@Composable
fun AccessDeniedScreen(
  userEmail: String,
  authViewModel: AuthViewModel,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier.fillMaxSize(),
    color = BackgroundLight
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Image(
          painter = painterResource(id = R.drawable.ic_ng_logo),
          contentDescription = "NavGrade Logo",
          modifier = Modifier.size(42.dp, 28.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = SurfaceLight),
          border = BorderStroke(1.dp, OutlineLight),
          elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Box(
              modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(ErrorRedContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "Access Denied",
                tint = ErrorRed,
                modifier = Modifier.size(34.dp)
              )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
              text = "Access Denied",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = TextPrimaryLight
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = "Your access request for NavGrade was declined by the administrator. Please contact your organization lead if you believe this is an error.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondaryLight,
              textAlign = TextAlign.Center,
              lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, OutlineLight, RoundedCornerShape(12.dp)),
              color = SurfaceVariantLight,
              shape = RoundedCornerShape(12.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Email,
                  contentDescription = null,
                  tint = ErrorRed,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = userEmail,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimaryLight
                )
              }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
              onClick = {
                authViewModel.signOut()
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = NavPrimary,
                contentColor = SurfaceLight
              )
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Logout,
                  contentDescription = "Sign Out",
                  modifier = Modifier.size(18.dp)
                )
                Text("Sign Out & Try Another Account", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }
}
