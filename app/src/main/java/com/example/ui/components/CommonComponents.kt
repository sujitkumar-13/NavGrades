package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EvaluationStatus
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedContainer
import com.example.ui.theme.MultiplePurple
import com.example.ui.theme.MultiplePurpleContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer

@Composable
fun StatusBadge(
  status: EvaluationStatus,
  modifier: Modifier = Modifier
) {
  val (bgColor, textColor, label, icon) = when (status) {
    EvaluationStatus.CORRECT -> Quadruple(
      SuccessGreenContainer,
      SuccessGreen,
      "Correct",
      Icons.Default.Check
    )
    EvaluationStatus.WRONG -> Quadruple(
      ErrorRedContainer,
      ErrorRed,
      "Wrong",
      Icons.Default.Close
    )
    EvaluationStatus.BLANK -> Quadruple(
      MaterialTheme.colorScheme.surfaceVariant,
      MaterialTheme.colorScheme.onSurfaceVariant,
      "Blank",
      Icons.Default.HelpOutline
    )
    EvaluationStatus.MULTIPLE -> Quadruple(
      MultiplePurpleContainer,
      MultiplePurple,
      "Multiple",
      Icons.Default.Layers
    )
    EvaluationStatus.REVIEW_REQUIRED -> Quadruple(
      WarningAmberContainer,
      WarningAmber,
      "Review Required",
      Icons.Default.Warning
    )
  }

  Surface(
    color = bgColor,
    shape = RoundedCornerShape(8.dp),
    modifier = modifier
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = textColor,
        modifier = Modifier.size(14.dp)
      )
      Text(
        text = label,
        color = textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
fun OptionBubble(
  text: String,
  isSelected: Boolean,
  isCorrect: Boolean? = null, // null = neutral selection, true = correct (green), false = wrong (red)
  onClick: (() -> Unit)? = null,
  size: Dp = 40.dp,
  modifier: Modifier = Modifier
) {
  val bgColor = when {
    isCorrect == true -> SuccessGreen
    isCorrect == false && isSelected -> ErrorRed
    isSelected -> MaterialTheme.colorScheme.primary
    else -> Color.Transparent
  }

  val textColor = when {
    isSelected || isCorrect == true -> Color.White
    else -> MaterialTheme.colorScheme.onSurface
  }

  val borderColor = when {
    isCorrect == true -> SuccessGreen
    isCorrect == false && isSelected -> ErrorRed
    isSelected -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.outline
  }

  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .background(bgColor)
      .border(1.5.dp, borderColor, CircleShape)
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = textColor,
      fontSize = 15.sp,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
fun MainHubCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  iconBgColor: Color,
  iconTint: Color,
  onClick: () -> Unit,
  testTag: String,
  badgeText: String? = null,
  modifier: Modifier = Modifier
) {
  Card(
    onClick = onClick,
    modifier = modifier
      .fillMaxWidth()
      .testTag(testTag),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(52.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(iconBgColor),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = iconTint,
          modifier = Modifier.size(28.dp)
        )
      }

      Spacer(modifier = Modifier.width(16.dp))

      Column(
        modifier = Modifier.weight(1f)
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
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
