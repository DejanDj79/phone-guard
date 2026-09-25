package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.parent.data.ProtectionHistoryEvent

@Composable
internal fun ParentDeviceTab(
  device: ChildDevice,
  pairedDeviceCount: Int,
  refreshInProgress: Boolean,
  commandInProgress: Boolean,
  deviceRenaming: Boolean,
  deviceUnpairing: Boolean,
  protectionHistory: List<ProtectionHistoryEvent>,
  protectionHistoryLoading: Boolean,
  protectionHistoryError: String?,
  showAllProtectionHistory: Boolean,
  protectionHistoryClearing: Boolean,
  onShowDevices: () -> Unit,
  onRefreshStatus: () -> Unit,
  onManageDevice: () -> Unit,
  onToggleProtectionHistory: () -> Unit,
  onRequestClearProtectionHistory: () -> Unit,
) {
  val actionsBusy =
    refreshInProgress ||
      commandInProgress ||
      deviceRenaming ||
      deviceUnpairing

  val protection = device.protectionStatus
  val protectionValues =
    listOf(
      protection.accessibilityEnabled,
      protection.preciseTimingEnabled,
      protection.batteryUnrestricted,
    )
  val protectionKnown = protectionValues.all { it != null }
  val protectionComplete =
    protectionKnown && protectionValues.all { it == true }

  Text(
    text = "Device",
    style = MaterialTheme.typography.headlineSmall,
    fontWeight = FontWeight.Bold,
  )

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
  ) {
    Column(
      modifier = Modifier.padding(22.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = device.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = formatLastSeen(device.lastSeenAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (refreshInProgress) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onShowDevices,
          enabled = !actionsBusy,
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Default.Devices,
            contentDescription = null,
          )
          Text("  " + pairedDeviceCount)
        }

        OutlinedButton(
          onClick = onRefreshStatus,
          enabled = !refreshInProgress,
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
          )
          Text("  REFRESH")
        }

        OutlinedButton(
          onClick = onManageDevice,
          enabled = !actionsBusy,
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
          )
        }
      }
    }
  }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(28.dp),
    color =
      if (protectionKnown && !protectionComplete) {
        MaterialTheme.colorScheme.errorContainer
      } else {
        MaterialTheme.colorScheme.surface
      },
  ) {
    Column(
      modifier = Modifier.padding(22.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector =
            if (protectionComplete) {
              Icons.Default.CheckCircle
            } else {
              Icons.Default.Warning
            },
          contentDescription = null,
          tint =
            if (protectionKnown && !protectionComplete) {
              MaterialTheme.colorScheme.onErrorContainer
            } else {
              MaterialTheme.colorScheme.primary
            },
        )

        Text(
          text =
            when {
              protectionComplete -> "Protection active"
              protectionKnown -> "Protection needs attention"
              else -> "Protection status"
            },
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
      }

      DeviceProtectionRow(
        label = "Screen protection",
        value = protection.accessibilityEnabled,
        enabledText = "On",
        disabledText = "Off",
      )

      DeviceProtectionRow(
        label = "Background protection",
        value = protection.batteryUnrestricted,
        enabledText = "On",
        disabledText = "Restricted",
      )

      DeviceProtectionRow(
        label = "Precise timing",
        value = protection.preciseTimingEnabled,
        enabledText = "On",
        disabledText = "Off",
      )

      if (
        devicePresenceState(device.lastSeenAt) ==
          DevicePresenceState.POSSIBLE_SHUTDOWN
      ) {
        Text(
          text = "The Child phone has not checked in recently. These are the last known protection settings.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.padding(22.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Default.History,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = "Protection history",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
      }

      when {
        protectionHistoryLoading && protectionHistory.isEmpty() -> {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
            )
            Text(
              text = "Loading history",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        protectionHistory.isEmpty() && protectionHistoryError == null -> {
          Text(
            text = "No protection events recorded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        else -> {
          val visibleEvents =
            if (showAllProtectionHistory) {
              protectionHistory
            } else {
              protectionHistory.take(PROTECTION_HISTORY_PREVIEW_COUNT)
            }

          visibleEvents.forEach { event ->
            ProtectionHistoryRow(event)
          }

          if (protectionHistory.size > PROTECTION_HISTORY_PREVIEW_COUNT) {
            TextButton(
              onClick = onToggleProtectionHistory,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                if (showAllProtectionHistory) {
                  "SHOW LESS"
                } else {
                  "SHOW ALL (" + protectionHistory.size + ")"
                },
              )
            }
          }
        }
      }

      if (protectionHistory.isNotEmpty()) {
        TextButton(
          onClick = onRequestClearProtectionHistory,
          enabled = !protectionHistoryClearing,
          modifier = Modifier.fillMaxWidth(),
        ) {
          if (protectionHistoryClearing) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
            )
          } else {
            Text("CLEAR HISTORY")
          }
        }
      }

      protectionHistoryError?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun DeviceProtectionRow(
  label: String,
  value: Boolean?,
  enabledText: String,
  disabledText: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.weight(1f),
    )

    Text(
      text =
        when (value) {
          true -> enabledText
          false -> disabledText
          null -> "—"
        },
      style = MaterialTheme.typography.labelLarge,
      color =
        if (value == false) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
  }
}

@Composable
private fun ProtectionHistoryRow(
  event: ProtectionHistoryEvent,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      text = formatProtectionEventTime(event.createdAt),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text(
      text = protectionHistoryLabel(event.eventType),
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.weight(1f),
    )
  }
}
