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

  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Quick actions",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )

      val isLocked = device.state == DeviceAccessState.LOCKED
      val isUnlocked =
        device.state == DeviceAccessState.ALLOWED ||
          device.state == DeviceAccessState.TEMPORARILY_ALLOWED

      Button(
        onClick = onLock,
        enabled = !commandInProgress && !connectionTestInProgress && !isLocked,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (isLocked) "LOCKED" else "LOCK NOW")
      }

      OutlinedButton(
        onClick = onUnlock,
        enabled = !commandInProgress && !connectionTestInProgress && !isUnlocked,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (isUnlocked) "UNLOCKED" else "UNLOCK")
      }

      Text(
        text = "Bonus time",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (
        device.state == DeviceAccessState.TEMPORARILY_ALLOWED &&
        device.temporaryAccessMinutesRemaining != null
      ) {
        Text(
          text =
            "Remaining: " +
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
        Text("ADD TIME")
      }

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

      if (
        devicePresenceState(device.lastSeenAt) ==
          DevicePresenceState.POSSIBLE_SHUTDOWN &&
        commandProgressMessage == null &&
        commandNotice == null
      ) {
        Text(
          text = "PhoneGuard has not checked in for 30+ minutes. Commands will wait for its next check-in.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
