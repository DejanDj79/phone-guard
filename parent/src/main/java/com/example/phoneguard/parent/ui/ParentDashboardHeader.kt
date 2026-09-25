package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.ui.Modifier
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
        CenterAlignedTopAppBar(
          navigationIcon = {
            IconButton(
              onClick = {
                scope.launch { drawerState.open() }
              },
            ) {
              Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open navigation",
              )
            }
          },
          title = {
            Text(
              text =
                parentDashboardSections
                  .getOrNull(selectedSection)
                  ?.label
                  ?: "PhoneGuard",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
            )
          },
          actions = {
            Box {
              IconButton(onClick = { accountMenuExpanded = true }) {
                Icon(
                  imageVector = Icons.Default.AccountCircle,
                  contentDescription = "Parent account",
                )
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
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
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
