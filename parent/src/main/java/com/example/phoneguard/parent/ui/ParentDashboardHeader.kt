package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.ChildDevice

internal val parentDashboardTabs =
  listOf("Home", "Schedule", "Apps", "Device", "Settings")

@Composable
internal fun ParentDashboardHeader(
  device: ChildDevice,
  selectedTab: Int,
  actionsBusy: Boolean,
  onChangeDevice: () -> Unit,
  onTabSelected: (Int) -> Unit,
) {
  Text(
    text = "PhoneGuard",
    style = MaterialTheme.typography.headlineMedium,
    fontWeight = FontWeight.Bold,
  )

  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = "Managing",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Text(
        text = device.displayName,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
      )

      Text(
        text = devicePresenceSummary(device.lastSeenAt),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedButton(
        onClick = onChangeDevice,
        enabled = !actionsBusy,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("CHANGE DEVICE")
      }
    }
  }

  ScrollableTabRow(selectedTabIndex = selectedTab) {
    parentDashboardTabs.forEachIndexed { index, label ->
      Tab(
        selected = selectedTab == index,
        onClick = { onTabSelected(index) },
        text = { Text(label) },
      )
    }
  }
}
