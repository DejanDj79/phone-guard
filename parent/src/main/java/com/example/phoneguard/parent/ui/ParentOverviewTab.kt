package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState
import com.example.phoneguard.parent.data.AppUsageDay
import com.example.phoneguard.parent.data.PendingTimeRequest

@Composable
internal fun ParentOverviewTab(
  device: ChildDevice,
  pendingTimeRequest: PendingTimeRequest?,
  timeRequestResponding: Boolean,
  timeRequestNotice: String?,
  timeRequestError: String?,
  commandInProgress: Boolean,
  commandProgressMessage: String?,
  commandNotice: String?,
  connectionTestInProgress: Boolean,
  connectionTestMessage: String?,
  connectionTestError: String?,
  appUsageDays: List<AppUsageDay>,
  appUsageLoading: Boolean,
  appUsageError: String?,
  appUsageView: Int,
  onRespondToTimeRequest: (PendingTimeRequest, Boolean) -> Unit,
  onLock: () -> Unit,
  onUnlock: () -> Unit,
  onAddBonusTime: () -> Unit,
  onTestConnection: () -> Unit,
  onOpenDevice: () -> Unit,
  onAppUsageViewChange: (Int) -> Unit,
) {
  val presence = devicePresenceState(device.lastSeenAt)
  val protection = device.protectionStatus
  val protectionKnown =
    listOf(
      protection.accessibilityEnabled,
      protection.preciseTimingEnabled,
      protection.batteryUnrestricted,
    ).all { it != null }
  val protectionComplete =
    protectionKnown &&
      protection.accessibilityEnabled == true &&
      protection.preciseTimingEnabled == true &&
      protection.batteryUnrestricted == true

  pendingTimeRequest?.let { request ->
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.primaryContainer,
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Time request",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        Text(
          text =
            request.displayName +
              " wants " +
              request.requestedMinutes +
              " more minutes.",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            onClick = { onRespondToTimeRequest(request, true) },
            enabled = !timeRequestResponding,
            modifier = Modifier.weight(1f),
          ) {
            Text(
              if (timeRequestResponding) {
                "WAIT…"
              } else {
                "APPROVE"
              },
            )
          }

          OutlinedButton(
            onClick = { onRespondToTimeRequest(request, false) },
            enabled = !timeRequestResponding,
            modifier = Modifier.weight(1f),
          ) {
            Text("DENY")
          }
        }
      }
    }
  }

  timeRequestNotice?.let { message ->
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  timeRequestError?.let { message ->
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.error,
    )
  }

  val isLocked = device.state == DeviceAccessState.LOCKED
  val stateTitle =
    when (device.state) {
      DeviceAccessState.ALLOWED -> "Phone is available"
      DeviceAccessState.LOCKED -> "Phone is locked"
      DeviceAccessState.TEMPORARILY_ALLOWED -> "Bonus time is active"
      DeviceAccessState.OFFLINE -> "Status unavailable"
    }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = device.displayName,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )

      Text(
        text = stateTitle,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )

      Text(
        text = devicePresenceSummary(device.lastSeenAt),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )

      if (
        device.state == DeviceAccessState.TEMPORARILY_ALLOWED &&
        device.temporaryAccessMinutesRemaining != null
      ) {
        Text(
          text =
            device.temporaryAccessMinutesRemaining.toString() +
              " min bonus time remaining",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = if (isLocked) onUnlock else onLock,
          enabled = !commandInProgress && !connectionTestInProgress,
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector =
              if (isLocked) {
                Icons.Default.LockOpen
              } else {
                Icons.Default.Lock
              },
            contentDescription = null,
          )
          Text(
            if (isLocked) {
              " UNLOCK"
            } else {
              " LOCK NOW"
            },
          )
        }

        OutlinedButton(
          onClick = onAddBonusTime,
          enabled = !commandInProgress && !connectionTestInProgress,
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Default.AddCircle,
            contentDescription = null,
          )
          Text(" BONUS")
        }
      }

      commandProgressMessage?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }

      commandNotice?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
    }
  }

  val remainingMinutes = device.dailyScreenTime.remainingMinutes
  val remainingLabel =
    when {
      device.dailyScreenTime.limitMinutes == null ->
        "No limit"
      device.dailyScreenTime.limitReached ->
        "Limit reached"
      remainingMinutes != null ->
        formatDurationMinutes(remainingMinutes)
      else ->
        "—"
    }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    HomeMetric(
      label = "Screen time",
      value = formatUsageSeconds(device.dailyScreenTime.usedSeconds),
      modifier = Modifier.weight(1f),
    )

    HomeMetric(
      label = "Remaining",
      value = remainingLabel,
      modifier = Modifier.weight(1f),
    )
  }

  if (
    presence == DevicePresenceState.POSSIBLE_SHUTDOWN ||
    (protectionKnown && !protectionComplete)
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.errorContainer,
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
          )
          Text(
            text = "Needs attention",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onErrorContainer,
          )
        }

        Text(
          text =
            when {
              presence == DevicePresenceState.POSSIBLE_SHUTDOWN ->
                "PhoneGuard has not checked in for 30+ minutes. The Child phone may be offline or powered off."
              protection.accessibilityEnabled == false ->
                "Screen protection is disabled on the Child phone."
              protection.batteryUnrestricted == false ->
                "Android is restricting PhoneGuard in the background."
              protection.preciseTimingEnabled == false ->
                "Precise timing is not available on the Child phone."
              else ->
                "One or more protection checks need attention."
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onErrorContainer,
        )

        TextButton(onClick = onOpenDevice) {
          Text("VIEW DEVICE STATUS")
        }
      }
    }
  }

  Text(
    text = "Activity",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.SemiBold,
  )

  val referenceDate =
    device.dailyScreenTime.usageDate
      ?: appUsageDays.firstOrNull()?.usageDate
  val todayUsage =
    referenceDate?.let { date ->
      appUsageDays.firstOrNull { it.usageDate == date }
    } ?: appUsageDays.firstOrNull()
  val yesterdayDate = shiftUsageDate(referenceDate, -1)
  val yesterdayUsage =
    yesterdayDate?.let { date ->
      appUsageDays.firstOrNull { it.usageDate == date }
    }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    tonalElevation = 1.dp,
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = appUsageView == APP_USAGE_VIEW_TODAY,
          onClick = { onAppUsageViewChange(APP_USAGE_VIEW_TODAY) },
          label = { Text("Today") },
          modifier = Modifier.weight(1f),
        )
        FilterChip(
          selected = appUsageView == APP_USAGE_VIEW_YESTERDAY,
          onClick = { onAppUsageViewChange(APP_USAGE_VIEW_YESTERDAY) },
          label = { Text("Yesterday") },
          modifier = Modifier.weight(1f),
        )
        FilterChip(
          selected = appUsageView == APP_USAGE_VIEW_WEEK,
          onClick = { onAppUsageViewChange(APP_USAGE_VIEW_WEEK) },
          label = { Text("7 days") },
          modifier = Modifier.weight(1f),
        )
      }

      when {
        appUsageLoading && appUsageDays.isEmpty() -> {
          Text(
            text = "Loading activity…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        appUsageView == APP_USAGE_VIEW_TODAY -> {
          AppUsageDayContent(
            day = todayUsage,
            emptyMessage = "No app usage has been recorded today.",
          )
        }

        appUsageView == APP_USAGE_VIEW_YESTERDAY -> {
          AppUsageDayContent(
            day = yesterdayUsage,
            emptyMessage = "No app usage was recorded yesterday.",
          )
        }

        else -> {
          AppUsageWeekContent(
            days = appUsageDays,
            referenceDate = referenceDate,
          )
        }
      }

      appUsageError?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "Connection",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text =
          if (presence == DevicePresenceState.ONLINE) {
            "Child is checking in normally."
          } else {
            devicePresenceSummary(device.lastSeenAt)
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    TextButton(
      onClick = onTestConnection,
      enabled = !commandInProgress && !connectionTestInProgress,
    ) {
      Text(
        if (connectionTestInProgress) {
          "TESTING…"
        } else {
          "TEST"
        },
      )
    }
  }

  connectionTestMessage?.let { message ->
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  connectionTestError?.let { message ->
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
    )
  }
}

@Composable
private fun HomeMetric(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(20.dp),
    tonalElevation = 1.dp,
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}
