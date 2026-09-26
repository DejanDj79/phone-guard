package com.example.phoneguard.parent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.parent.R
import com.example.phoneguard.parent.data.ProtectionHistoryEvent

private const val DEVICE_HISTORY_PREVIEW_COUNT = 5

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
  protectionHistoryClearing: Boolean,
  onShowDevices: () -> Unit,
  onRefreshStatus: () -> Unit,
  onManageDevice: () -> Unit,
  onOpenProtectionHistory: () -> Unit,
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

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Surface(
          modifier = Modifier.size(54.dp),
          shape = CircleShape,
          color = ParentSectionHeaderColor,
        ) {
          Icon(
            painter = painterResource(R.drawable.pg_icon_device),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(14.dp),
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = device.displayName,
            style = MaterialTheme.typography.titleLarge,
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
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = ParentSectionHeaderColor,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        DeviceActionTile(
          label = "DEVICES",
          detail = pairedDeviceCount.toString(),
          enabled = !actionsBusy,
          onClick = onShowDevices,
          modifier = Modifier.weight(1f),
        )

        DeviceActionTile(
          label = "REFRESH",
          detail = if (refreshInProgress) "…" else "STATUS",
          enabled = !refreshInProgress,
          onClick = onRefreshStatus,
          modifier = Modifier.weight(1f),
        )

        DeviceActionTile(
          label = "MANAGE",
          detail = "DEVICE",
          enabled = !actionsBusy,
          onClick = onManageDevice,
          modifier = Modifier.weight(1f),
        )
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
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          painter =
            painterResource(
              if (protectionComplete) {
                R.drawable.pg_icon_shield
              } else {
                R.drawable.pg_icon_warning
              },
            ),
          contentDescription = null,
          tint =
            if (protectionKnown && !protectionComplete) {
              MaterialTheme.colorScheme.onErrorContainer
            } else {
              ParentSectionHeaderColor
            },
        )

        Column {
          Text(
            text =
              when {
                protectionComplete -> "Protection active"
                protectionKnown -> "Protection needs attention"
                else -> "Protection status"
              },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text =
              if (protectionComplete) {
                "All required Child protections are enabled."
              } else {
                "Current protection state reported by the Child device."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ProtectionStatusTile(
          label = "SCREEN",
          value = protection.accessibilityEnabled,
          enabledText = "ON",
          disabledText = "OFF",
          modifier = Modifier.weight(1f),
        )
        ProtectionStatusTile(
          label = "BACKGROUND",
          value = protection.batteryUnrestricted,
          enabledText = "ON",
          disabledText = "LIMITED",
          modifier = Modifier.weight(1f),
        )
        ProtectionStatusTile(
          label = "TIMING",
          value = protection.preciseTimingEnabled,
          enabledText = "ON",
          disabledText = "OFF",
          modifier = Modifier.weight(1f),
        )
      }

      if (
        devicePresenceState(device.lastSeenAt) ==
          DevicePresenceState.POSSIBLE_SHUTDOWN
      ) {
        Text(
          text = "The Child phone has not checked in recently. These are the last known settings.",
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
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          modifier = Modifier.size(36.dp),
          shape = CircleShape,
          color = ParentSectionHeaderColor.copy(alpha = 0.12f),
        ) {
          Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = ParentSectionHeaderColor,
            modifier = Modifier.padding(8.dp),
          )
        }

        Text(
          text = "Protection history",
          modifier = Modifier
            .weight(1f)
            .padding(start = 10.dp),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )

        if (protectionHistoryLoading && protectionHistory.isNotEmpty()) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
          )
        }
      }

      when {
        protectionHistoryLoading && protectionHistory.isEmpty() -> {
          Row(
            modifier = Modifier.padding(vertical = 8.dp),
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
            protectionHistory.take(DEVICE_HISTORY_PREVIEW_COUNT)

          visibleEvents.forEachIndexed { index, event ->
            if (index > 0) {
              Divider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
              )
            }
            ProtectionHistoryRow(event)
          }

          if (protectionHistory.size > DEVICE_HISTORY_PREVIEW_COUNT) {
            TextButton(
              onClick = onOpenProtectionHistory,
              modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
              Text("VIEW FULL HISTORY (" + protectionHistory.size + ")")
            }
          }
        }
      }

      if (protectionHistory.isNotEmpty()) {
        TextButton(
          onClick = onRequestClearProtectionHistory,
          enabled = !protectionHistoryClearing,
          modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).heightIn(min = 52.dp),
      shape = ParentActionShape,
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
private fun DeviceActionTile(
  label: String,
  detail: String,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier =
      modifier
        .clickable(enabled = enabled, onClick = onClick),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
      )

      Text(
        text = detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun ProtectionStatusTile(
  label: String,
  value: Boolean?,
  enabledText: String,
  disabledText: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    color =
      when (value) {
        true -> ParentSectionHeaderColor.copy(alpha = 0.10f)
        false -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
        null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
      },
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
      )

      Text(
        text =
          when (value) {
            true -> enabledText
            false -> disabledText
            null -> "—"
          },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color =
          when (value) {
            true -> ParentSectionHeaderColor
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.onSurfaceVariant
          },
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
internal fun ProtectionHistoryRow(
  event: ProtectionHistoryEvent,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(vertical = 7.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
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
