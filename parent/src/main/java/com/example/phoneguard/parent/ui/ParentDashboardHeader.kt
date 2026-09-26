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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.parent.R

internal val parentDashboardSections =
  listOf(
    ParentDashboardSection("Home", R.drawable.pg_icon_home),
    ParentDashboardSection("Schedule", R.drawable.pg_icon_timer),
    ParentDashboardSection("Apps", R.drawable.pg_icon_apps),
    ParentDashboardSection("Device", R.drawable.pg_icon_device),
    ParentDashboardSection("Settings", R.drawable.pg_icon_settings),
  )

private val parentBottomSections = parentDashboardSections.take(4)

internal data class ParentDashboardSection(
  val label: String,
  @DrawableRes val iconRes: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
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
  val headerShape =
    RoundedCornerShape(
      bottomStart = 28.dp,
      bottomEnd = 28.dp,
    )

  Box(
    modifier = modifier.fillMaxSize(),
  ) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = Color.Transparent,
      topBar = {
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

          Surface(
            color = Color.Transparent,
            shape = headerShape,
            border =
              BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.58f),
              ),
          ) {
            CenterAlignedTopAppBar(
              modifier =
                if (isHome) {
                  Modifier
                } else {
                  Modifier.padding(bottom = 8.dp)
                },
              navigationIcon = {
                if (isHome) {
                  ParentAccountMenuButton(
                    expanded = accountMenuExpanded,
                    onExpandedChange = { accountMenuExpanded = it },
                    parentEmail = parentEmail,
                    childName = device.displayName,
                    actionsBusy = actionsBusy,
                    coloredHeader = false,
                    onChangeDevice = onChangeDevice,
                    onOpenSettings = { onSectionSelected(4) },
                    onSignOut = onSignOut,
                  )
                } else {
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    modifier =
                      Modifier
                        .padding(start = 10.dp)
                        .size(38.dp),
                  ) {
                    IconButton(
                      onClick = { onSectionSelected(0) },
                      modifier = Modifier.size(38.dp),
                    ) {
                      Icon(
                        painter = painterResource(R.drawable.pg_icon_back),
                        contentDescription = "Back to Home",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                      )
                    }
                  }
                }
              },
              title = {
                Text(
                  text =
                    (
                      parentDashboardSections
                        .getOrNull(sectionIndex)
                        ?.label
                        ?: "PhoneGuard"
                    ).uppercase(),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = headerContentColor,
                )
              },
              actions = {
                if (isHome) {
                  ParentConnectionTestAction(
                    inProgress = connectionTestInProgress,
                    succeeded = connectionTestSucceeded,
                    enabled =
                      !actionsBusy &&
                        !connectionTestInProgress &&
                        connectionTestSucceeded == null,
                    onClick = onTestConnection,
                  )
                } else {
                  ParentAccountMenuButton(
                    expanded = accountMenuExpanded,
                    onExpandedChange = { accountMenuExpanded = it },
                    parentEmail = parentEmail,
                    childName = device.displayName,
                    actionsBusy = actionsBusy,
                    coloredHeader = true,
                    onChangeDevice = onChangeDevice,
                    onOpenSettings = { onSectionSelected(4) },
                    onSignOut = onSignOut,
                  )
                }
              },
              colors =
                TopAppBarDefaults.centerAlignedTopAppBarColors(
                  containerColor = Color.Transparent,
                  navigationIconContentColor = headerContentColor,
                  titleContentColor = headerContentColor,
                  actionIconContentColor = headerContentColor,
                ),
            )
          }
        }
      },
    ) { innerPadding ->
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(innerPadding),
      ) {
        content()
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
          onSectionSelected(4)
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
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
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
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(30.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
    shadowElevation = 14.dp,
    tonalElevation = 0.dp,
  ) {
    BoxWithConstraints(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
      val itemWidth = maxWidth / parentBottomSections.size.toFloat()
      val indicatorTarget =
        (itemWidth * selectedSection.toFloat()) +
          ((itemWidth - 44.dp) / 2f)
      val indicatorOffset by
        animateDpAsState(
          targetValue = indicatorTarget,
          animationSpec =
            spring(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessMediumLow,
            ),
          label = "BottomNavIndicator",
        )

      Surface(
        modifier =
          Modifier
            .offset(x = indicatorOffset)
            .size(44.dp),
        shape = CircleShape,
        color = ParentAccentColor,
        shadowElevation = 2.dp,
      ) {}

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        parentBottomSections.forEachIndexed { index, section ->
          val selected = selectedSection == index

          Box(
            modifier =
              Modifier
                .weight(1f)
                .height(44.dp)
                .clickable { onSectionSelected(index) },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              painter = painterResource(section.iconRes),
              contentDescription = section.label,
              tint =
                if (selected) {
                  Color.White
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
              modifier = Modifier.size(22.dp),
            )
          }
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
) {
  Surface(
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    modifier =
      Modifier
        .padding(end = 10.dp)
        .size(38.dp),
  ) {
    IconButton(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.size(38.dp),
    ) {
      when {
        inProgress -> {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        succeeded == true -> {
          Icon(
            painter = painterResource(R.drawable.pg_icon_check),
            contentDescription = "Connection OK",
            tint = Color(0xFF3E7C4E),
            modifier = Modifier.size(21.dp),
          )
        }

        succeeded == false -> {
          Icon(
            painter = painterResource(R.drawable.pg_icon_close),
            contentDescription = "Connection failed",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(21.dp),
          )
        }

        else -> {
          Icon(
            painter = painterResource(R.drawable.pg_icon_test),
            contentDescription = "Test connection",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
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
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
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
  Box {
    Surface(
      shape = CircleShape,
      color =
        if (coloredHeader) {
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        } else {
          MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        },
      modifier =
        if (coloredHeader) {
          Modifier
            .padding(end = 10.dp)
            .size(38.dp)
        } else {
          Modifier
            .padding(start = 10.dp)
            .size(38.dp)
        },
    ) {
      IconButton(
        onClick = { onExpandedChange(!expanded) },
        modifier = Modifier.size(38.dp),
      ) {
        Icon(
          painter = painterResource(R.drawable.pg_icon_user),
          contentDescription = "Parent menu",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}
