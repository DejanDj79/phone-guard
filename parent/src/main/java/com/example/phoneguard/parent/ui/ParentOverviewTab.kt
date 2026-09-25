package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
  pendingTimeRequest?.let { request ->
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "More time requested",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )

        Text(
          text =
            request.displayName +
              " is asking for " +
              request.requestedMinutes +
              " more minutes.",
          style = MaterialTheme.typography.bodyLarge,
        )

        Button(
          onClick = { onRespondToTimeRequest(request, true) },
          enabled = !timeRequestResponding,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            if (timeRequestResponding) {
              "RESPONDING…"
            } else {
              "APPROVE " + request.requestedMinutes + " MIN"
            },
          )
        }

        OutlinedButton(
          onClick = { onRespondToTimeRequest(request, false) },
          enabled = !timeRequestResponding,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("DENY")
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

  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Today",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Screen time",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = formatUsageSeconds(device.dailyScreenTime.usedSeconds),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Daily limit",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text =
              device.dailyScreenTime.limitMinutes
                ?.let(::formatDurationMinutes)
                ?: "Not set",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      Text(
        text = deviceStateLabel(device),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
      )

      Text(
        text = devicePresenceSummary(device.lastSeenAt),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (
        device.dailyScreenTime.limitMinutes != null &&
        device.dailyScreenTime.remainingMinutes != null
      ) {
        Text(
          text =
            if (device.dailyScreenTime.limitReached) {
              "Daily screen time limit reached."
            } else {
              formatDurationMinutes(device.dailyScreenTime.remainingMinutes) +
                " remaining today."
            },
          style = MaterialTheme.typography.bodyMedium,
          color =
            if (device.dailyScreenTime.limitReached) {
              MaterialTheme.colorScheme.error
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
      }
    }
  }

  if (
    presence == DevicePresenceState.POSSIBLE_SHUTDOWN ||
    (protectionKnown && !protectionComplete)
  ) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Needs attention",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.error,
        )

        Text(
          text =
            when {
              presence == DevicePresenceState.POSSIBLE_SHUTDOWN ->
                "PhoneGuard has not checked in for 30+ minutes. The Child phone may be offline or powered off."
              protection.accessibilityEnabled == false ->
                "Screen protection is disabled on the Child phone."
              protection.batteryUnrestricted == false ->
                "Background protection is restricted by Android battery settings."
              protection.preciseTimingEnabled == false ->
                "Precise timing is not allowed on the Child phone."
              else ->
                "One or more protection checks need attention."
            },
          style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedButton(
          onClick = onOpenDevice,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("VIEW DEVICE STATUS")
        }
      }
    }
  }

  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Quick controls",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )

      val isLocked = device.state == DeviceAccessState.LOCKED
      val isUnlocked =
        device.state == DeviceAccessState.ALLOWED ||
          device.state == DeviceAccessState.TEMPORARILY_ALLOWED

      if (isLocked) {
        Button(
          onClick = onUnlock,
          enabled = !commandInProgress && !connectionTestInProgress,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("UNLOCK")
        }
      } else {
        Button(
          onClick = onLock,
          enabled = !commandInProgress && !connectionTestInProgress,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("LOCK NOW")
        }
      }

      if (
        device.state == DeviceAccessState.TEMPORARILY_ALLOWED &&
        device.temporaryAccessMinutesRemaining != null
      ) {
        Text(
          text =
            "Bonus time remaining: " +
              device.temporaryAccessMinutesRemaining +
              " min",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }

      OutlinedButton(
        onClick = onAddBonusTime,
        enabled = !commandInProgress && !connectionTestInProgress,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("ADD BONUS TIME")
      }

      commandProgressMessage?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      commandNotice?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Connection",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )

      Text(
        text =
          if (presence == DevicePresenceState.ONLINE) {
            "PhoneGuard is checking in normally."
          } else {
            devicePresenceSummary(device.lastSeenAt)
          },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedButton(
        onClick = onTestConnection,
        enabled = !commandInProgress && !connectionTestInProgress,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          if (connectionTestInProgress) {
            "TESTING CONNECTION…"
          } else {
            "TEST CONNECTION"
          },
        )
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
  }

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

  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "App usage",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )

      Text(
        text = "Screen time by app for " + device.displayName + ".",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (appUsageView == APP_USAGE_VIEW_TODAY) {
          Button(
            onClick = { onAppUsageViewChange(APP_USAGE_VIEW_TODAY) },
            modifier = Modifier.weight(1f),
          ) {
            Text("TODAY")
          }
        } else {
          OutlinedButton(
            onClick = { onAppUsageViewChange(APP_USAGE_VIEW_TODAY) },
            modifier = Modifier.weight(1f),
          ) {
            Text("TODAY")
          }
        }

        if (appUsageView == APP_USAGE_VIEW_YESTERDAY) {
          Button(
            onClick = { onAppUsageViewChange(APP_USAGE_VIEW_YESTERDAY) },
            modifier = Modifier.weight(1f),
          ) {
            Text("YESTERDAY")
          }
        } else {
          OutlinedButton(
            onClick = { onAppUsageViewChange(APP_USAGE_VIEW_YESTERDAY) },
            modifier = Modifier.weight(1f),
          ) {
            Text("YESTERDAY")
          }
        }

        if (appUsageView == APP_USAGE_VIEW_WEEK) {
          Button(
            onClick = { onAppUsageViewChange(APP_USAGE_VIEW_WEEK) },
            modifier = Modifier.weight(1f),
          ) {
            Text("7 DAYS")
          }
        } else {
          OutlinedButton(
            onClick = { onAppUsageViewChange(APP_USAGE_VIEW_WEEK) },
            modifier = Modifier.weight(1f),
          ) {
            Text("7 DAYS")
          }
        }
      }

      when {
        appUsageLoading && appUsageDays.isEmpty() -> {
          Text(
            text = "Loading app usage…",
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

      Text(
        text =
          "System screens and the launcher are excluded. Allowed apps used while PhoneGuard is locked are included.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      appUsageError?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}
