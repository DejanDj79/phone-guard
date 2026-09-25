package com.example.phoneguard.parent.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.ChildDevice

internal val parentDashboardSections =
  listOf(
    ParentDashboardSection("Home", Icons.Default.Home),
    ParentDashboardSection("Schedule", Icons.Default.Schedule),
    ParentDashboardSection("Apps", Icons.Default.Apps),
    ParentDashboardSection("Device", Icons.Default.PhoneAndroid),
    ParentDashboardSection("Settings", Icons.Default.Settings),
  )

private val parentBottomSections = parentDashboardSections.take(4)

internal data class ParentDashboardSection(
  val label: String,
  val icon: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParentDashboardShell(
  device: ChildDevice,
  selectedSection: Int,
  actionsBusy: Boolean,
  parentEmail: String?,
  onChangeDevice: () -> Unit,
  onSectionSelected: (Int) -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  var accountMenuExpanded by remember { mutableStateOf(false) }
  val showBottomNavigation = selectedSection in parentBottomSections.indices

  Scaffold(
    modifier = modifier.fillMaxSize(),
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
        val headerContainerColor =
          if (isHome) {
            MaterialTheme.colorScheme.background
          } else {
            ParentSectionHeaderColor
          }
        val headerContentColor =
          if (isHome) {
            MaterialTheme.colorScheme.onBackground
          } else {
            MaterialTheme.colorScheme.onPrimary
          }

        Surface(
          color = headerContainerColor,
          shape =
            if (isHome) {
              RoundedCornerShape(0.dp)
            } else {
              RoundedCornerShape(
                bottomStart = 32.dp,
                bottomEnd = 32.dp,
              )
            },
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
                  color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
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
                      imageVector = Icons.Default.ArrowBack,
                      contentDescription = "Back to Home",
                      tint = ParentSectionHeaderColor,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
              }
            },
            title = {
              Text(
                text =
                  parentDashboardSections
                    .getOrNull(sectionIndex)
                    ?.label
                    ?: "PhoneGuard",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = headerContentColor,
              )
            },
            actions = {
              if (isHome) {
                Spacer(modifier = Modifier.size(48.dp))
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
    bottomBar = {
      if (showBottomNavigation) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp),
        ) {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = ParentSectionHeaderColor,
            shadowElevation = 10.dp,
          ) {
            NavigationBar(
              containerColor = Color.Transparent,
              tonalElevation = 0.dp,
            ) {
              parentBottomSections.forEachIndexed { index, section ->
                NavigationBarItem(
                  selected = selectedSection == index,
                  onClick = { onSectionSelected(index) },
                  icon = {
                    Icon(
                      imageVector = section.icon,
                      contentDescription = section.label,
                    )
                  },
                  label = {
                    Text(
                      text = section.label,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight =
                        if (selectedSection == index) {
                          FontWeight.Bold
                        } else {
                          FontWeight.Medium
                        },
                    )
                  },
                  colors =
                    NavigationBarItemDefaults.colors(
                      selectedIconColor = ParentSectionHeaderColor,
                      selectedTextColor = Color.White,
                      indicatorColor = Color.White,
                      unselectedIconColor = Color.White.copy(alpha = 0.72f),
                      unselectedTextColor = Color.White.copy(alpha = 0.72f),
                    ),
                )
              }
            }
          }
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
          MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        } else {
          ParentSectionHeaderColor
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
        onClick = { onExpandedChange(true) },
        modifier = Modifier.size(38.dp),
      ) {
        Icon(
          imageVector = Icons.Default.AccountCircle,
          contentDescription = "Parent menu",
          tint =
            if (coloredHeader) {
              ParentSectionHeaderColor
            } else {
              Color.White
            },
          modifier = Modifier.size(20.dp),
        )
      }
    }

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { onExpandedChange(false) },
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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

      DropdownMenuItem(
        text = { Text("Manage children") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Devices,
            contentDescription = null,
          )
        },
        enabled = !actionsBusy,
        onClick = {
          onExpandedChange(false)
          onChangeDevice()
        },
      )

      DropdownMenuItem(
        text = { Text("Settings") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
          )
        },
        onClick = {
          onExpandedChange(false)
          onOpenSettings()
        },
      )

      Divider()

      DropdownMenuItem(
        text = { Text("Sign out") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Logout,
            contentDescription = null,
          )
        },
        onClick = {
          onExpandedChange(false)
          onSignOut()
        },
      )
    }
  }
}
