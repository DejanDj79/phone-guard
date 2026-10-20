package com.example.phoneguard.parent.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.parent.R

internal const val PARENT_HOME_SECTION = 0
internal const val PARENT_SCHEDULE_SECTION = 1
internal const val PARENT_LOCATION_SECTION = 2
internal const val PARENT_APPS_SECTION = 3
internal const val PARENT_DEVICE_SECTION = 4
internal const val PARENT_SETTINGS_SECTION = 5

internal val parentDashboardSections =
  listOf(
    ParentDashboardSection("Home", R.drawable.pg_icon_home),
    ParentDashboardSection("Schedule", R.drawable.pg_icon_timer),
    ParentDashboardSection("Location", R.drawable.pg_icon_location),
    ParentDashboardSection("Apps", R.drawable.pg_icon_apps),
    ParentDashboardSection("Device", R.drawable.pg_icon_device),
    ParentDashboardSection("Settings", R.drawable.pg_icon_settings),
  )

private val parentBottomSections = parentDashboardSections.take(5)

internal data class ParentDashboardSection(
  val label: String,
  @DrawableRes val iconRes: Int,
)

@Composable
internal fun ParentDashboardShell(
  device: ChildDevice,
  selectedSection: Int,
  actionsBusy: Boolean,
  parentEmail: String?,
  connectionTestInProgress: Boolean,
  connectionTestSucceeded: Boolean?,
  onTestConnection: () -> Unit,
  onChangeDevice: () -> Unit,
  onSectionSelected: (Int) -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  var accountMenuExpanded by remember { mutableStateOf(false) }
  val showBottomNavigation = selectedSection in parentBottomSections.indices
  Box(
    modifier = modifier.fillMaxSize(),
  ) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = Color.Transparent,
      topBar = {
        if (selectedSection != PARENT_LOCATION_SECTION) {
          AnimatedContent(
          targetState = selectedSection,
          transitionSpec = {
            if (targetState == 0) {
              slideInVertically(
                animationSpec = tween(260),
                initialOffsetY = { -it / 3 },
              ) + fadeIn(animationSpec = tween(220)) togetherWith
                slideOutVertically(
                  animationSpec = tween(220),
                  targetOffsetY = { -it / 3 },
                ) + fadeOut(animationSpec = tween(180))
            } else {
              slideInVertically(
                animationSpec = tween(300),
                initialOffsetY = { -it },
              ) + fadeIn(animationSpec = tween(220)) togetherWith
                slideOutVertically(
                  animationSpec = tween(180),
                  targetOffsetY = { -it / 2 },
                ) + fadeOut(animationSpec = tween(140))
            }
          },
          label = "ParentSectionHeader",
        ) { sectionIndex ->
          val isHome = sectionIndex == 0
          val headerContentColor = MaterialTheme.colorScheme.onSurface

          val currentSection =
            parentDashboardSections.getOrNull(sectionIndex)
              ?: parentDashboardSections.first()

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

                  val borderPath =
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

                  val maskPath =
                    Path().apply {
                      moveTo(0f, 0f)
                      lineTo(0f, shoulderY)
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
                      lineTo(size.width, 0f)
                      close()
                    }

                  drawPath(
                    path = maskPath,
                    brush =
                      Brush.horizontalGradient(
                        colors =
                          listOf(
                            ParentGradientEdge,
                            ParentGradientCenter,
                            ParentGradientEdge,
                          ),
                      ),
                  )

                  drawPath(
                    path = borderPath,
                    color = ParentBorderColor,
                    style = Stroke(width = 1.dp.toPx()),
                  )
                },
          ) {
            if (isHome) {
              ParentAccountMenuButton(
                expanded = accountMenuExpanded,
                onExpandedChange = { accountMenuExpanded = it },
                parentEmail = parentEmail,
                childName = device.displayName,
                actionsBusy = actionsBusy,
                coloredHeader = false,
                onChangeDevice = onChangeDevice,
                onOpenSettings = { onSectionSelected(PARENT_SETTINGS_SECTION) },
                onSignOut = onSignOut,
              )
            } else {
              IconButton(
                onClick = { onSectionSelected(PARENT_LOCATION_SECTION) },
                modifier =
                  Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 10.dp, top = 10.dp)
                    .size(38.dp),
              ) {
                Icon(
                  painter = painterResource(R.drawable.pg_icon_back),
                  contentDescription = "Back to Location",
                  tint = ParentHeaderIconColor,
                  modifier = Modifier.size(20.dp),
                )
              }
            }

            Text(
              text = currentSection.label.uppercase(),
              modifier =
                Modifier
                  .align(Alignment.TopCenter)
                  .padding(top = 18.dp),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )

            if (sectionIndex in parentBottomSections.indices) {
              Row(
                modifier =
                  Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 50.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                parentBottomSections.indices.forEach { index ->
                  Surface(
                    modifier = Modifier.size(6.dp),
                    shape = CircleShape,
                    color =
                      if (index == sectionIndex) {
                        ParentAccentColor
                      } else {
                        ParentGradientEdge
                      },
                  ) {}
                }
              }
            }

            if (isHome) {
              ParentConnectionTestAction(
                inProgress = connectionTestInProgress,
                succeeded = connectionTestSucceeded,
                enabled =
                  !actionsBusy &&
                    !connectionTestInProgress &&
                    connectionTestSucceeded == null,
                onClick = onTestConnection,
                modifier = Modifier.align(Alignment.TopEnd),
              )
            } else {
              Box(
                modifier = Modifier.align(Alignment.TopEnd),
              ) {
                ParentAccountMenuButton(
                  expanded = accountMenuExpanded,
                  onExpandedChange = { accountMenuExpanded = it },
                  parentEmail = parentEmail,
                  childName = device.displayName,
                  actionsBusy = actionsBusy,
                  coloredHeader = true,
                  onChangeDevice = onChangeDevice,
                  onOpenSettings = { onSectionSelected(PARENT_SETTINGS_SECTION) },
                  onSignOut = onSignOut,
                )
              }
            }

            Icon(
              painter = painterResource(currentSection.iconRes),
              contentDescription = currentSection.label,
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
      },
    ) {
      Box(
        modifier = Modifier.fillMaxSize(),
      ) {
        content()
      }
    }

    if (selectedSection == PARENT_LOCATION_SECTION) {
      Box(
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 8.dp, end = 8.dp),
      ) {
        ParentAccountMenuButton(
          expanded = accountMenuExpanded,
          onExpandedChange = { accountMenuExpanded = it },
          parentEmail = parentEmail,
          childName = device.displayName,
          actionsBusy = actionsBusy,
          coloredHeader = false,
          onChangeDevice = onChangeDevice,
          onOpenSettings = {
            onSectionSelected(PARENT_SETTINGS_SECTION)
          },
          onSignOut = onSignOut,
        )
      }
    }

    if (accountMenuExpanded) {
      BackHandler {
        accountMenuExpanded = false
      }

      ParentAccountMenuOverlay(
        parentEmail = parentEmail,
        childName = device.displayName,
        actionsBusy = actionsBusy,
        onChangeDevice = {
          accountMenuExpanded = false
          onChangeDevice()
        },
        onOpenSettings = {
          accountMenuExpanded = false
          onSectionSelected(PARENT_SETTINGS_SECTION)
        },
        onSignOut = {
          accountMenuExpanded = false
          onSignOut()
        },
        modifier =
          if (selectedSection == 0) {
            Modifier
              .align(Alignment.TopStart)
              .statusBarsPadding()
              .padding(top = 58.dp, start = 12.dp)
          } else {
            Modifier
              .align(Alignment.TopEnd)
              .statusBarsPadding()
              .padding(top = 58.dp, end = 12.dp)
          },
      )
    }

    if (showBottomNavigation) {
      ParentFloatingBottomNavigation(
        selectedSection = selectedSection,
        onSectionSelected = onSectionSelected,
        modifier =
          Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun ParentFloatingBottomNavigation(
  selectedSection: Int,
  onSectionSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
    modifier =
      modifier
        .height(92.dp)
        .drawBehind {
          val shoulderY = 24.dp.toPx()
          val centerX = size.width / 2f
          val notchHalf = 50.dp.toPx()

          val backgroundPath =
            Path().apply {
              moveTo(0f, shoulderY)
              lineTo(centerX - notchHalf, shoulderY)
              cubicTo(
                centerX - 30.dp.toPx(),
                shoulderY,
                centerX - 30.dp.toPx(),
                0f,
                centerX,
                0f,
              )
              cubicTo(
                centerX + 30.dp.toPx(),
                0f,
                centerX + 30.dp.toPx(),
                shoulderY,
                centerX + notchHalf,
                shoulderY,
              )
              lineTo(size.width, shoulderY)
              lineTo(size.width, size.height)
              lineTo(0f, size.height)
              close()
            }

          val borderPath =
            Path().apply {
              moveTo(0f, shoulderY)
              lineTo(centerX - notchHalf, shoulderY)
              cubicTo(
                centerX - 30.dp.toPx(),
                shoulderY,
                centerX - 30.dp.toPx(),
                0f,
                centerX,
                0f,
              )
              cubicTo(
                centerX + 30.dp.toPx(),
                0f,
                centerX + 30.dp.toPx(),
                shoulderY,
                centerX + notchHalf,
                shoulderY,
              )
              lineTo(size.width, shoulderY)
            }

          drawPath(
            path = backgroundPath,
            brush =
              Brush.horizontalGradient(
                colors =
                  listOf(
                    ParentGradientEdge,
                    ParentGradientCenter,
                    ParentGradientEdge,
                  ),
              ),
          )
          drawPath(
            path = borderPath,
            color = ParentBorderColor,
            style = Stroke(width = 1.dp.toPx()),
          )
        },
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .padding(horizontal = 8.dp)
          .padding(bottom = 8.dp),
      verticalAlignment = Alignment.Bottom,
    ) {
      parentBottomSections.forEachIndexed { index, section ->
        val selected = selectedSection == index
        val isLocation = index == PARENT_LOCATION_SECTION

        Column(
          modifier =
            Modifier
              .weight(1f)
              .offset(y = if (isLocation) (-17).dp else 0.dp)
              .clickable { onSectionSelected(index) }
              .padding(vertical = 3.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          if (isLocation) {
            Surface(
              modifier = Modifier.size(52.dp),
              shape = CircleShape,
              color =
                if (selected) {
                  ParentAccentColor
                } else {
                  MaterialTheme.colorScheme.surface
                },
              border =
                BorderStroke(
                  1.dp,
                  if (selected) ParentAccentColor else ParentBorderColor,
                ),
              shadowElevation = 8.dp,
              tonalElevation = 0.dp,
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  painter = painterResource(section.iconRes),
                  contentDescription = section.label,
                  tint =
                    if (selected) {
                      ParentGradientEdge
                    } else {
                      MaterialTheme.colorScheme.onSurface
                    },
                  modifier = Modifier.size(24.dp),
                )
              }
            }
          } else {
            Icon(
              painter = painterResource(section.iconRes),
              contentDescription = section.label,
              tint =
                if (selected) {
                  ParentAccentColor
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
              modifier = Modifier.size(21.dp),
            )
          }

          Text(
            text = section.label,
            style = MaterialTheme.typography.labelSmall,
            color =
              if (selected) {
                ParentAccentColor
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
private fun ParentConnectionTestAction(
  inProgress: Boolean,
  succeeded: Boolean?,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  IconButton(
    onClick = onClick,
    enabled = enabled,
    modifier =
      modifier
        .padding(end = 10.dp, top = 10.dp)
        .size(38.dp),
  ) {
      when {
        inProgress -> {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = ParentHeaderIconColor,
          )
        }

        succeeded == true -> {
          Icon(
            painter = painterResource(R.drawable.pg_icon_check),
            contentDescription = "Connection OK",
            tint = ParentHeaderIconColor,
            modifier = Modifier.size(21.dp),
          )
        }

        succeeded == false -> {
          Icon(
            painter = painterResource(R.drawable.pg_icon_close),
            contentDescription = "Connection failed",
            tint = ParentHeaderIconColor,
            modifier = Modifier.size(21.dp),
          )
        }

        else -> {
          Icon(
            painter = painterResource(R.drawable.pg_icon_test),
            contentDescription = "Test connection",
            tint = ParentHeaderIconColor,
            modifier = Modifier.size(20.dp),
          )
        }
      }
  }
}

@Composable
private fun ParentAccountMenuOverlay(
  parentEmail: String?,
  childName: String,
  actionsBusy: Boolean,
  onChangeDevice: () -> Unit,
  onOpenSettings: () -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.widthIn(min = 230.dp, max = 300.dp),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 12.dp,
    tonalElevation = 0.dp,
    border =
      BorderStroke(
        width = 1.dp,
        color = ParentBorderColor,
      ),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      ) {
        Text(
          text = "PARENT ACCOUNT",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text =
            parentEmail
              ?.takeIf { it.isNotBlank() }
              ?: "Signed in Parent",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "Managing " + childName,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Divider()

      ParentAccountMenuRow(
        iconRes = R.drawable.pg_icon_device,
        label = "Manage children",
        enabled = !actionsBusy,
        onClick = onChangeDevice,
      )
      ParentAccountMenuRow(
        iconRes = R.drawable.pg_icon_settings,
        label = "Settings",
        onClick = onOpenSettings,
      )

      Divider()

      ParentAccountMenuRow(
        iconRes = R.drawable.pg_icon_logout,
        label = "Sign out",
        onClick = onSignOut,
      )
    }
  }
}

@Composable
private fun ParentAccountMenuRow(
  @DrawableRes iconRes: Int,
  label: String,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 13.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      painter = painterResource(iconRes),
      contentDescription = null,
      tint =
        if (enabled) {
          MaterialTheme.colorScheme.onSurfaceVariant
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        },
      modifier = Modifier.size(20.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color =
        if (enabled) {
          MaterialTheme.colorScheme.onSurface
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        },
    )
  }
}

@Composable
private fun ParentAccountMenuButton(
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  parentEmail: String?,
  childName: String,
  actionsBusy: Boolean,
  coloredHeader: Boolean,
  onChangeDevice: () -> Unit,
  onOpenSettings: () -> Unit,
  onSignOut: () -> Unit,
) {
  IconButton(
    onClick = { onExpandedChange(!expanded) },
    modifier =
      if (coloredHeader) {
        Modifier
          .padding(end = 10.dp, top = 10.dp)
          .size(38.dp)
      } else {
        Modifier
          .padding(start = 10.dp, top = 10.dp)
          .size(38.dp)
      },
  ) {
    Icon(
      painter = painterResource(R.drawable.pg_icon_user),
      contentDescription = "Parent menu",
      tint = ParentHeaderIconColor,
      modifier = Modifier.size(20.dp),
    )
  }
}
