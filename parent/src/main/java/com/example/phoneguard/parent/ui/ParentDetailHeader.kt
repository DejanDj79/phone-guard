package com.example.phoneguard.parent.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.phoneguard.parent.R

internal val ParentSectionHeaderColor = ParentAccentColor

@Composable
internal fun ParentDetailHeader(
  title: String,
  @DrawableRes iconRes: Int,
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
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .height(104.dp)
          .drawBehind {
            val shoulderY = size.height * 0.72f
            val leftShoulder = size.width * 0.34f
            val rightShoulder = size.width * 0.66f

            val path =
              Path().apply {
                moveTo(0f, shoulderY)
                lineTo(leftShoulder, shoulderY)
                cubicTo(
                  size.width * 0.40f,
                  shoulderY,
                  size.width * 0.41f,
                  size.height,
                  size.width * 0.50f,
                  size.height,
                )
                cubicTo(
                  size.width * 0.59f,
                  size.height,
                  size.width * 0.60f,
                  shoulderY,
                  rightShoulder,
                  shoulderY,
                )
                lineTo(size.width, shoulderY)
              }

            drawPath(
              path = path,
              color = ParentBorderColor,
              style = Stroke(width = 1.dp.toPx()),
            )
          },
    ) {
      IconButton(
        onClick = onBack,
        modifier =
          Modifier
            .align(Alignment.TopStart)
            .padding(start = 10.dp, top = 10.dp)
            .size(38.dp),
      ) {
        Icon(
          painter = painterResource(R.drawable.pg_icon_back),
          contentDescription = "Back",
          tint = ParentHeaderIconColor,
          modifier = Modifier.size(20.dp),
        )
      }

      Text(
        text = title.uppercase(),
        modifier =
          Modifier
            .align(Alignment.TopCenter)
            .padding(top = 18.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Icon(
        painter = painterResource(iconRes),
        contentDescription = title,
        tint = ParentHeaderIconColor,
        modifier =
          Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 8.dp)
            .size(24.dp),
      )
    }
  }
}
