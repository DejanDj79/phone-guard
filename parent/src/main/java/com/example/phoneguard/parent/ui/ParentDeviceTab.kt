package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "Device details",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Text(
        text = device.displayName,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
      )

      Text(
        text = deviceStateLabel(device),
        style = MaterialTheme.typography.bodyLarge,
      )

      Text(
        text = devicePresenceSummary(device.lastSeenAt),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedButton(
        onClick = onShowDevices,
        enabled =
          !refreshInProgress &&
            !commandInProgress &&
            !deviceRenaming &&
            !deviceUnpairing,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("DEVICES (" + pairedDeviceCount + ")")
      }

      OutlinedButton(
        onClick = onRefreshStatus,
        enabled = !refreshInProgress,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (refreshInProgress) "REFRESHING…" else "REFRESH STATUS")
      }

      OutlinedButton(
        onClick = onManageDevice,
        enabled =
          !refreshInProgress &&
            !commandInProgress &&
            !deviceRenaming &&
            !deviceUnpairing,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("MANAGE DEVICE")
      }

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

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "Protection status",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )

      Text(
        text =
          when {
            devicePresenceState(device.lastSeenAt) ==
              DevicePresenceState.POSSIBLE_SHUTDOWN ->
              "⚠ Possible shutdown · showing last known protection state"
            devicePresenceState(device.lastSeenAt) ==
                DevicePresenceState.LAST_SEEN &&
              protectionKnown &&
              !protectionComplete ->
              "⚠ Last known protection state needs attention"
            devicePresenceState(device.lastSeenAt) ==
              DevicePresenceState.LAST_SEEN ->
              "Showing last known protection state"
            protectionComplete ->
              "✓ All protection checks are active"
            protectionKnown ->
              "⚠ Protection needs attention"
            else ->
              "Checking protection status…"
          },
        style = MaterialTheme.typography.bodyMedium,
        color =
          if (protectionKnown && !protectionComplete) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
      )

      ProtectionStatusLine(
        label = "Accessibility",
        enabled = protection.accessibilityEnabled,
        enabledText = "Enabled",
        disabledText = "Disabled",
      )

      ProtectionStatusLine(
        label = "Precise timing",
        enabled = protection.preciseTimingEnabled,
        enabledText = "Allowed",
        disabledText = "Not allowed",
      )

      ProtectionStatusLine(
        label = "Background protection",
        enabled = protection.batteryUnrestricted,
        enabledText = "Unrestricted",
        disabledText = "Battery restricted",
      )

      Text(
        text =
          if (
            devicePresenceState(device.lastSeenAt) ==
              DevicePresenceState.ONLINE
          ) {
            "Heartbeat: active"
          } else {
            formatLastSeen(device.lastSeenAt)
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "Protection history",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )

      Text(
        text = "Recent protection changes and bypass attempts for " +
          device.displayName + ".",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      when {
        protectionHistoryLoading && protectionHistory.isEmpty() -> {
          Text(
            text = "Loading protection history…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        protectionHistory.isEmpty() && protectionHistoryError == null -> {
          Text(
            text = "No protection events recorded yet.",
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
            Text(
              text =
                formatProtectionEventTime(event.createdAt) +
                  " · " +
                  protectionHistoryLabel(event.eventType),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (protectionHistory.size > PROTECTION_HISTORY_PREVIEW_COUNT) {
            OutlinedButton(
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
        OutlinedButton(
          onClick = onRequestClearProtectionHistory,
          enabled = !protectionHistoryClearing,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            if (protectionHistoryClearing) {
              "CLEARING…"
            } else {
              "CLEAR HISTORY"
            },
          )
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
