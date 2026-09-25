package com.example.phoneguard.parent.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.ChildDevice
import kotlinx.coroutines.launch

internal val parentDashboardSections =
  listOf(
    ParentDashboardSection("Home", Icons.Default.Home),
    ParentDashboardSection("Schedule", Icons.Default.Schedule),
    ParentDashboardSection("Apps", Icons.Default.Apps),
    ParentDashboardSection("Device", Icons.Default.PhoneAndroid),
    ParentDashboardSection("Settings", Icons.Default.Settings),
  )

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
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  var accountMenuExpanded by remember { mutableStateOf(false) }

  ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled = true,
    drawerContent = {
      ModalDrawerSheet {
        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(horizontal = 16.dp, vertical = 20.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "PhoneGuard",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp),
          )

          Spacer(modifier = Modifier.height(20.dp))

          Text(
            text = "MANAGING",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
          )

          Text(
            text = device.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
          )

          Text(
            text = devicePresenceSummary(device.lastSeenAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
          )

          TextButton(
            onClick = {
              scope.launch { drawerState.close() }
              onChangeDevice()
            },
            enabled = !actionsBusy,
          ) {
            Text("CHANGE CHILD")
          }

          Divider(modifier = Modifier.padding(vertical = 10.dp))

          parentDashboardSections.forEachIndexed { index, section ->
            NavigationDrawerItem(
              label = { Text(section.label) },
              selected = selectedSection == index,
              onClick = {
                onSectionSelected(index)
                scope.launch { drawerState.close() }
              },
              icon = {
                Icon(
                  imageVector = section.icon,
                  contentDescription = null,
                )
              },
              modifier = Modifier.padding(vertical = 2.dp),
            )
          }

          Spacer(modifier = Modifier.weight(1f))

          parentEmail
            ?.takeIf { it.isNotBlank() }
            ?.let { email ->
              Divider(modifier = Modifier.padding(vertical = 10.dp))
              Text(
                text = email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
              )
            }
        }
      }
    },
    modifier = modifier.fillMaxSize(),
  ) {
    Scaffold(
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
                IconButton(
                  onClick = {
                    scope.launch { drawerState.open() }
                  },
                ) {
                  Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open navigation",
                    tint = headerContentColor,
                  )
                }
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
              Box {
                Surface(
                  shape = CircleShape,
                  color =
                    if (isHome) {
                      Color.Transparent
                    } else {
                      MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    },
                  modifier =
                    if (isHome) {
                      Modifier.padding(end = 8.dp)
                    } else {
                      Modifier
                        .padding(end = 10.dp)
                        .size(38.dp)
                    },
                ) {
                  IconButton(
                    onClick = { accountMenuExpanded = true },
                    modifier =
                      if (isHome) {
                        Modifier
                      } else {
                        Modifier.size(38.dp)
                      },
                  ) {
                    Icon(
                      imageVector = Icons.Default.AccountCircle,
                      contentDescription = "Parent account",
                      tint =
                        if (isHome) {
                          headerContentColor
                        } else {
                          ParentSectionHeaderColor
                        },
                      modifier =
                        if (isHome) {
                          Modifier
                        } else {
                          Modifier.size(20.dp)
                        },
                    )
                  }
                }

                DropdownMenu(
                  expanded = accountMenuExpanded,
                  onDismissRequest = { accountMenuExpanded = false },
                ) {
                  parentEmail
                    ?.takeIf { it.isNotBlank() }
                    ?.let { email ->
                      DropdownMenuItem(
                        text = {
                          Column {
                            Text(
                              text = "Parent account",
                              style = MaterialTheme.typography.labelSmall,
                              color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                              text = email,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis,
                            )
                          }
                        },
                        onClick = {},
                        enabled = false,
                      )
                      Divider()
                    }

                  DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                      )
                    },
                    onClick = {
                      accountMenuExpanded = false
                      onSectionSelected(4)
                    },
                  )

                  DropdownMenuItem(
                    text = { Text("Sign out") },
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                      )
                    },
                    onClick = {
                      accountMenuExpanded = false
                      onSignOut()
                    },
                  )
                }
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
  }
}
