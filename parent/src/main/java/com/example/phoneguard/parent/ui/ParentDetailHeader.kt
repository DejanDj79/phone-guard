package com.example.phoneguard.parent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val ParentSectionHeaderColor = Color(0xFF2F4FA3)

@Composable
internal fun ParentDetailHeader(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var visible by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    visible = true
  }

  AnimatedVisibility(
    visible = visible,
    enter =
      slideInVertically(
        animationSpec = tween(320),
        initialOffsetY = { -it },
      ) + fadeIn(animationSpec = tween(220)),
    modifier = modifier.fillMaxWidth(),
  ) {
    Surface(
      color = ParentSectionHeaderColor,
      shape =
        RoundedCornerShape(
          bottomStart = 32.dp,
          bottomEnd = 32.dp,
        ),
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 15.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
          modifier =
            Modifier
              .align(Alignment.CenterStart)
              .size(36.dp),
        ) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.size(36.dp),
          ) {
            Icon(
              imageVector = Icons.Default.ArrowBack,
              contentDescription = "Back",
              tint = ParentSectionHeaderColor,
              modifier = Modifier.size(19.dp),
            )
          }
        }

        Text(
          text = title.uppercase(),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.align(Alignment.Center),
        )
      }
    }
  }
}
