package com.example.phoneguard.parent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.AllowedAppsSnapshot
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.InstalledAppInfo
import com.example.phoneguard.core.DeviceAccessState
import com.example.phoneguard.core.RemoteCommandType
import com.example.phoneguard.parent.data.AppUsageDay
import com.example.phoneguard.parent.data.PendingTimeRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ParentOverviewTab(
  device: ChildDevice,
  pendingTimeRequest: PendingTimeRequest?,
  timeRequestResponding: Boolean,
  timeRequestNotice: String?,
  timeRequestError: String?,
  commandInProgress: Boolean,
  activeCommandType: RemoteCommandType?,
  connectionTestInProgress: Boolean,
  connectionTestMessage: String?,
  connectionTestError: String?,
  appUsageDays: List<AppUsageDay>,
  appInventorySnapshot: AllowedAppsSnapshot?,
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
          OutlinedButton(
            onClick = { onRespondToTimeRequest(request, true) },
            enabled = !timeRequestResponding,
            modifier = Modifier.weight(1f),
          ) {
            if (timeRequestResponding) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
              )
            } else {
              Text("APPROVE")
            }
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
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  timeRequestError?.let { message ->
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
    )
  }

  val isLocked = device.state == DeviceAccessState.LOCKED
  val mainCommandLoading =
    commandInProgress &&
      (
        activeCommandType == RemoteCommandType.LOCK ||
          activeCommandType == RemoteCommandType.UNLOCK
      )
  val bonusLoading =
    commandInProgress &&
      activeCommandType == RemoteCommandType.BONUS_TIME

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(30.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
  ) {
    Column(
      modifier = Modifier.padding(22.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Text(
          text = device.displayName,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.weight(1f),
        )

        Column(
          horizontalAlignment = Alignment.End,
          modifier = Modifier.weight(1f),
        ) {
          Text(
            text = "LAST SEEN",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.68f),
          )
          Text(
            text =
              formatLastSeen(device.lastSeenAt)
                .removePrefix("Last seen: "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.End,
          )
        }
      }

      if (
        device.state == DeviceAccessState.TEMPORARILY_ALLOWED &&
        device.temporaryAccessMinutesRemaining != null
      ) {
        Surface(
          shape = RoundedCornerShape(50),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        ) {
          Text(
            text =
              "Bonus time · " +
                device.temporaryAccessMinutesRemaining +
                " min",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = if (isLocked) onUnlock else onLock,
          enabled = !commandInProgress && !connectionTestInProgress,
          modifier = Modifier.weight(1f),
        ) {
          if (mainCommandLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(19.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          } else {
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
                "  UNLOCK"
              } else {
                "  LOCK"
              },
            )
          }
        }

        OutlinedButton(
          onClick = onAddBonusTime,
          enabled = !commandInProgress && !connectionTestInProgress,
          modifier = Modifier.weight(1f),
        ) {
          if (bonusLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(19.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.primary,
            )
          } else {
            Icon(
              imageVector = Icons.Default.AddCircle,
              contentDescription = null,
            )
            Text("  BONUS")
          }
        }
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
      label = "SCREEN TIME",
      value = formatUsageSeconds(device.dailyScreenTime.usedSeconds),
      modifier = Modifier.weight(1f),
    )

    HomeMetric(
      label = "REMAINING",
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
          verticalAlignment = Alignment.CenterVertically,
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
                "PhoneGuard has not checked in for 30+ minutes."
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
    text = "Recent activity",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.SemiBold,
  )

  RecentUnlockedActivityTimeline(
    day =
      device.dailyScreenTime.usageDate
        ?.let { date ->
          appUsageDays.firstOrNull { it.usageDate == date }
        }
        ?: appUsageDays.firstOrNull(),
    inventory = appInventorySnapshot,
    loading = appUsageLoading,
  )

  Text(
    text = "Usage overview",
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
    color = MaterialTheme.colorScheme.surface,
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
            inventory = appInventorySnapshot,
          )
        }

        appUsageView == APP_USAGE_VIEW_YESTERDAY -> {
          AppUsageDayContent(
            day = yesterdayUsage,
            emptyMessage = "No app usage was recorded yesterday.",
            inventory = appInventorySnapshot,
          )
        }

        else -> {
          AppUsageWeekContent(
            days = appUsageDays,
            referenceDate = referenceDate,
            inventory = appInventorySnapshot,
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
    horizontalArrangement = Arrangement.End,
  ) {
    TextButton(
      onClick = onTestConnection,
      enabled = !commandInProgress && !connectionTestInProgress,
    ) {
      if (connectionTestInProgress) {
        CircularProgressIndicator(
          modifier = Modifier.size(16.dp),
          strokeWidth = 2.dp,
        )
        Text("  TESTING CONNECTION")
      } else {
        Text("TEST CONNECTION")
      }
    }
  }

  connectionTestMessage?.let { message ->
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.fillMaxWidth(),
      textAlign = TextAlign.End,
    )
  }

  connectionTestError?.let { message ->
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
      modifier = Modifier.fillMaxWidth(),
      textAlign = TextAlign.End,
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
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}


private data class HomeRecentActivityItem(
  val packageName: String,
  val label: String,
  val startedAtMillis: Long,
  val seconds: Int,
)

@Composable
private fun RecentUnlockedActivityTimeline(
  day: AppUsageDay?,
  inventory: AllowedAppsSnapshot?,
  loading: Boolean,
) {
  val recentItems =
    day
      ?.apps
      .orEmpty()
      .flatMap { app ->
        app.sessions.map { session ->
          HomeRecentActivityItem(
            packageName = app.packageName,
            label = app.label,
            startedAtMillis = session.startedAtMillis,
            seconds = session.seconds,
          )
        }
      }
      .sortedByDescending { it.startedAtMillis }
      .take(6)

  val installedByPackage =
    inventory
      ?.installedApps
      ?.associateBy { it.packageName }
      .orEmpty()

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "UNLOCKED PHONE",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )

        Text(
          text = "TODAY",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      when {
        loading && recentItems.isEmpty() -> {
          Text(
            text = "Loading recent activity…",
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        recentItems.isEmpty() -> {
          Text(
            text = "No unlocked app activity has been recorded yet.",
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        else -> {
          recentItems.forEachIndexed { index, item ->
            val app =
              installedByPackage[item.packageName]
                ?: InstalledAppInfo(
                  packageName = item.packageName,
                  label = item.label,
                )

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.Top,
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                ParentAppIcon(
                  app = app,
                  modifier = Modifier.size(38.dp),
                )

                if (index < recentItems.lastIndex) {
                  Box(
                    modifier =
                      Modifier
                        .width(2.dp)
                        .height(24.dp)
                        .background(
                          MaterialTheme.colorScheme.outlineVariant,
                          CircleShape,
                        ),
                  )
                }
              }

              Column(
                modifier =
                  Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 2.dp),
              ) {
                Text(
                  text = item.label,
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                )
                Text(
                  text = formatRecentActivityTime(item.startedAtMillis),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              Text(
                text = formatUsageSeconds(item.seconds),
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
              )
            }
          }
        }
      }
    }
  }
}

private fun formatRecentActivityTime(epochMillis: Long): String =
  runCatching {
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))
  }.getOrDefault("—")
