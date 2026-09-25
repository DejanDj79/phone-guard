package com.example.phoneguard.parent.ui

import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState
import com.example.phoneguard.core.PairingResult
import com.example.phoneguard.core.RemoteCommand
import com.example.phoneguard.parent.data.AppUsageDay
import com.example.phoneguard.parent.data.AppUsageEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun DailyLimitWheelDialog(
  initialMinutes: Int,
  onDismiss: () -> Unit,
  onConfirm: (Int) -> Unit,
) {
  var selectedMinutes by remember(initialMinutes) {
    mutableStateOf(initialMinutes.coerceIn(1, 1440))
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Daily screen time limit",
        fontWeight = FontWeight.SemiBold,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Choose the total normal screen time allowed each day.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AndroidView(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(190.dp),
          factory = { context ->
            NumberPicker(context).apply {
              minValue = 1
              maxValue = 1440
              value = selectedMinutes
              wrapSelectorWheel = false
              descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
              setOnValueChangedListener { _, _, newValue ->
                selectedMinutes = newValue
              }
            }
          },
          update = { picker ->
            if (picker.value != selectedMinutes) {
              picker.value = selectedMinutes
            }
          },
        )

        Text(
          text = formatDurationMinutes(selectedMinutes),
          modifier = Modifier.fillMaxWidth(),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
        )
      }
    },
    confirmButton = {
      Button(onClick = { onConfirm(selectedMinutes) }) {
        Text("SAVE")
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) {
        Text("CANCEL")
      }
    },
  )
}

@Composable
internal fun BonusTimeWheelDialog(
  initialMinutes: Int,
  onDismiss: () -> Unit,
  onConfirm: (Int) -> Unit,
) {
  var selectedMinutes by remember(initialMinutes) {
    mutableStateOf(initialMinutes.coerceIn(1, 60))
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Bonus time",
        fontWeight = FontWeight.SemiBold,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Choose a duration from 1 to 60 minutes.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AndroidView(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(190.dp),
          factory = { context ->
            NumberPicker(context).apply {
              minValue = 1
              maxValue = 60
              value = selectedMinutes
              wrapSelectorWheel = false
              descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
              setFormatter { value -> value.toString().padStart(2, '0') }
              setOnValueChangedListener { _, _, newValue ->
                selectedMinutes = newValue
              }
            }
          },
          update = { picker ->
            if (picker.value != selectedMinutes) {
              picker.value = selectedMinutes
            }
          },
        )

        Text(
          text =
            selectedMinutes.toString() +
              if (selectedMinutes == 1) " minute" else " minutes",
          modifier = Modifier.fillMaxWidth(),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(selectedMinutes) },
      ) {
        Text("ADD")
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) {
        Text("CANCEL")
      }
    },
  )
}

@Composable
internal fun PairDeviceScreen(
  onPair: suspend (String) -> PairingResult,
  onSwitchAccount: suspend () -> String?,
  onCancel: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()

  var pairingCode by remember { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var pairingInProgress by remember { mutableStateOf(false) }
  var switchingAccount by remember { mutableStateOf(false) }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 32.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = "PhoneGuard Parent",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "Pair a Child phone",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.SemiBold,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Open PhoneGuard on the Child phone and enter its 6-character pairing code.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedTextField(
      value = pairingCode,
      onValueChange = { value ->
        pairingCode =
          value
            .uppercase()
            .filter(Char::isLetterOrDigit)
            .take(6)
        errorMessage = null
      },
      label = { Text("Pairing code") },
      singleLine = true,
      enabled = !pairingInProgress && !switchingAccount,
      keyboardOptions =
        KeyboardOptions(
          capitalization = KeyboardCapitalization.Characters,
          keyboardType = KeyboardType.Ascii,
        ),
      modifier = Modifier.fillMaxWidth(),
    )

    errorMessage?.let {
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = it,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
      onClick = {
        scope.launch {
          pairingInProgress = true
          errorMessage = null

          when (val result = onPair(pairingCode)) {
            is PairingResult.Success -> Unit
            is PairingResult.InvalidCode ->
              errorMessage = result.message
            is PairingResult.Error ->
              errorMessage = result.message
          }

          pairingInProgress = false
        }
      },
      enabled = pairingCode.length == 6 && !pairingInProgress,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        if (pairingInProgress) {
          "PAIRING…"
        } else {
          "PAIR DEVICE"
        },
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    onCancel?.let {
      OutlinedButton(
        onClick = it,
        enabled = !pairingInProgress && !switchingAccount,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("CANCEL")
      }

      Spacer(modifier = Modifier.height(16.dp))
    }

    OutlinedButton(
      onClick = {
        scope.launch {
          switchingAccount = true
          errorMessage = null
          errorMessage = onSwitchAccount()
          switchingAccount = false
        }
      },
      enabled = !pairingInProgress && !switchingAccount,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        if (switchingAccount) {
          "SWITCHING…"
        } else {
          "SWITCH ACCOUNT"
        },
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "The code is verified by the PhoneGuard backend and can only be used while it is active.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

internal fun protectionHistoryLabel(eventType: String): String =
  when (eventType) {
    "ACCESSIBILITY_DISABLED" ->
      "⚠ Accessibility protection disabled"
    "ACCESSIBILITY_RESTORED" ->
      "✓ Accessibility protection restored"
    "PRECISE_TIMING_DISABLED" ->
      "⚠ Precise timing disabled"
    "PRECISE_TIMING_RESTORED" ->
      "✓ Precise timing restored"
    "BATTERY_UNRESTRICTED_DISABLED" ->
      "⚠ Background protection restricted"
    "BATTERY_UNRESTRICTED_RESTORED" ->
      "✓ Background protection restored"
    "APP_INFO_OPENED" ->
      "⚠ PhoneGuard app info opened"
    "UNINSTALL_SCREEN_OPENED" ->
      "⚠ Uninstall screen opened"
    "FORCE_STOP_ATTEMPT" ->
      "⚠ Force stop selected"
    "CLEAR_DATA_ATTEMPT" ->
      "⚠ Clear app data selected"
    else ->
      eventType.replace('_', ' ').lowercase()
  }

@Composable
internal fun ProtectionStatusLine(
  label: String,
  enabled: Boolean?,
  enabledText: String,
  disabledText: String,
) {
  val statusText =
    when (enabled) {
      true -> "✓ " + enabledText
      false -> "⚠ " + disabledText
      null -> "… Unknown"
    }

  Text(
    text = label + ": " + statusText,
    style = MaterialTheme.typography.bodyMedium,
    color =
      if (enabled == false) {
        MaterialTheme.colorScheme.error
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
  )
}

@Composable
internal fun AppUsageDayContent(
  day: AppUsageDay?,
  emptyMessage: String,
) {
  if (day == null) {
    Text(
      text = emptyMessage,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }

  Text(
    text = "Tracked app time: " + formatUsageSeconds(day.totalSeconds),
    style = MaterialTheme.typography.bodyLarge,
    fontWeight = FontWeight.SemiBold,
  )

  Text(
    text = formatUsageDate(day.usageDate),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )

  val topApps =
    day.apps
      .filter { it.seconds > 0 }
      .take(APP_USAGE_PREVIEW_COUNT)

  if (topApps.isEmpty()) {
    Text(
      text = "No launcher app has accumulated usage for this day.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  } else {
    Text(
      text = "Top apps",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
    )

    topApps.forEachIndexed { index, app ->
      Text(
        text =
          (index + 1).toString() +
            ". " +
            app.label +
            " · " +
            formatUsageSeconds(app.seconds),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@Composable
internal fun AppUsageWeekContent(
  days: List<AppUsageDay>,
  referenceDate: String?,
) {
  val dateKeys = usageWeekDateKeys(referenceDate ?: days.firstOrNull()?.usageDate)
  if (dateKeys.isEmpty()) {
    Text(
      text = "No app usage has been recorded yet.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }

  val byDate = days.associateBy { it.usageDate }
  val dailySeconds =
    dateKeys.map { dateKey ->
      dateKey to (byDate[dateKey]?.totalSeconds ?: 0)
    }
  val totalSeconds = dailySeconds.sumOf { it.second }
  val averageSeconds = totalSeconds / 7
  val maxSeconds =
    dailySeconds.maxOfOrNull { it.second }
      ?.coerceAtLeast(1)
      ?: 1

  Text(
    text = "7-day total: " + formatUsageSeconds(totalSeconds),
    style = MaterialTheme.typography.bodyLarge,
    fontWeight = FontWeight.SemiBold,
  )

  Text(
    text = "Daily average: " + formatUsageSeconds(averageSeconds),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )

  Text(
    text = "Daily usage",
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.SemiBold,
  )

  dailySeconds.forEach { (dateKey, seconds) ->
    Text(
      text =
        formatUsageDayLabel(dateKey) +
          "  " +
          usageBar(seconds, maxSeconds) +
          "  " +
          formatUsageSeconds(seconds),
      style = MaterialTheme.typography.bodyMedium,
    )
  }

  val weekDays =
    dateKeys.mapNotNull(byDate::get)
  val topApps =
    aggregateAppUsage(weekDays)
      .take(APP_USAGE_PREVIEW_COUNT)

  Text(
    text = "Top apps · 7 days",
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.SemiBold,
  )

  if (topApps.isEmpty()) {
    Text(
      text = "No app usage recorded in this 7-day period.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  } else {
    topApps.forEachIndexed { index, app ->
      Text(
        text =
          (index + 1).toString() +
            ". " +
            app.label +
            " · " +
            formatUsageSeconds(app.seconds),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

internal fun aggregateAppUsage(days: List<AppUsageDay>): List<AppUsageEntry> {
  val totals = linkedMapOf<String, Pair<String, Int>>()

  days.forEach { day ->
    day.apps.forEach { app ->
      val previous = totals[app.packageName]
      totals[app.packageName] =
        app.label to ((previous?.second ?: 0) + app.seconds)
    }
  }

  return totals
    .map { (packageName, value) ->
      AppUsageEntry(
        packageName = packageName,
        label = value.first,
        seconds = value.second,
      )
    }
    .sortedByDescending { it.seconds }
}

internal fun usageWeekDateKeys(referenceDate: String?): List<String> {
  if (referenceDate.isNullOrBlank()) return emptyList()

  return (6 downTo 0)
    .mapNotNull { daysBack ->
      shiftUsageDate(referenceDate, -daysBack)
    }
}

internal fun shiftUsageDate(
  dateKey: String?,
  days: Int,
): String? {
  if (dateKey.isNullOrBlank()) return null

  val formatter =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
      isLenient = false
    }
  val parsed =
    runCatching { formatter.parse(dateKey) }
      .getOrNull()
      ?: return null

  val calendar =
    Calendar.getInstance().apply {
      time = parsed
      add(Calendar.DAY_OF_YEAR, days)
    }

  return formatter.format(calendar.time)
}

internal fun formatUsageDate(dateKey: String): String {
  val input =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
      isLenient = false
    }
  val parsed =
    runCatching { input.parse(dateKey) }
      .getOrNull()
      ?: return dateKey

  return SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(parsed)
}

internal fun formatUsageDayLabel(dateKey: String): String {
  val input =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
      isLenient = false
    }
  val parsed =
    runCatching { input.parse(dateKey) }
      .getOrNull()
      ?: return dateKey

  return SimpleDateFormat("EEE", Locale.getDefault())
    .format(parsed)
    .take(3)
}

internal fun usageBar(
  seconds: Int,
  maxSeconds: Int,
): String {
  val safeMax = maxSeconds.coerceAtLeast(1)
  val safeSeconds = seconds.coerceAtLeast(0)
  val filled =
    if (safeSeconds == 0) {
      0
    } else {
      ((safeSeconds.toDouble() / safeMax.toDouble()) * USAGE_BAR_WIDTH)
        .toInt()
        .coerceIn(1, USAGE_BAR_WIDTH)
    }

  return "█".repeat(filled) + "░".repeat(USAGE_BAR_WIDTH - filled)
}

internal fun formatDurationMinutes(minutes: Int): String {
  val safeMinutes = minutes.coerceAtLeast(0)
  val hours = safeMinutes / 60
  val remainingMinutes = safeMinutes % 60

  return when {
    hours == 0 -> safeMinutes.toString() + " min"
    remainingMinutes == 0 -> hours.toString() + " h"
    else -> hours.toString() + " h " + remainingMinutes + " min"
  }
}

internal fun formatUsageSeconds(seconds: Int): String {
  val safeSeconds = seconds.coerceAtLeast(0)
  val totalMinutes = safeSeconds / 60
  val remainingSeconds = safeSeconds % 60

  return if (totalMinutes == 0) {
    remainingSeconds.toString() + " sec"
  } else {
    formatDurationMinutes(totalMinutes)
  }
}

internal const val PROTECTION_HISTORY_PREVIEW_COUNT = 8
internal const val APP_USAGE_PREVIEW_COUNT = 5
internal const val APP_USAGE_VIEW_TODAY = 0
internal const val APP_USAGE_VIEW_YESTERDAY = 1
internal const val APP_USAGE_VIEW_WEEK = 2
internal const val USAGE_BAR_WIDTH = 10

internal fun commandMatchesDeviceState(
  command: RemoteCommand,
  device: ChildDevice,
): Boolean =
  when (command.type) {
    com.example.phoneguard.core.RemoteCommandType.LOCK ->
      device.state == DeviceAccessState.LOCKED
    com.example.phoneguard.core.RemoteCommandType.UNLOCK ->
      device.state == DeviceAccessState.ALLOWED
    com.example.phoneguard.core.RemoteCommandType.BONUS_TIME ->
      device.state == DeviceAccessState.TEMPORARILY_ALLOWED
    com.example.phoneguard.core.RemoteCommandType.SYNC_SCHEDULE,
    com.example.phoneguard.core.RemoteCommandType.SYNC_ALLOWED_APPS,
    com.example.phoneguard.core.RemoteCommandType.SYNC_DAILY_LIMIT ->
      false
  }

internal fun commandSendingLabel(
  command: RemoteCommand,
  deviceOnline: Boolean,
): String =
  if (!deviceOnline) {
    "PhoneGuard has not checked in recently — the command will wait for the next check-in."
  } else {
    when (command.type) {
      com.example.phoneguard.core.RemoteCommandType.LOCK ->
        "Sending lock command…"
      com.example.phoneguard.core.RemoteCommandType.UNLOCK ->
        "Sending unlock command…"
      com.example.phoneguard.core.RemoteCommandType.BONUS_TIME ->
        "Adding " + command.bonusMinutes + " min…"
      com.example.phoneguard.core.RemoteCommandType.SYNC_SCHEDULE ->
        "Syncing schedule…"
      com.example.phoneguard.core.RemoteCommandType.SYNC_ALLOWED_APPS ->
        "Syncing allowed apps…"
      com.example.phoneguard.core.RemoteCommandType.SYNC_DAILY_LIMIT ->
        "Syncing daily limit…"
    }
  }

internal fun commandAppliedLabel(command: RemoteCommand): String =
  when (command.type) {
    com.example.phoneguard.core.RemoteCommandType.LOCK ->
      "Phone locked ✓"
    com.example.phoneguard.core.RemoteCommandType.UNLOCK ->
      "Phone unlocked ✓"
    com.example.phoneguard.core.RemoteCommandType.BONUS_TIME ->
      "Added " + command.bonusMinutes + " min ✓"
    com.example.phoneguard.core.RemoteCommandType.SYNC_SCHEDULE ->
      "Schedule applied ✓"
    com.example.phoneguard.core.RemoteCommandType.SYNC_ALLOWED_APPS ->
      "Allowed apps updated ✓"
    com.example.phoneguard.core.RemoteCommandType.SYNC_DAILY_LIMIT ->
      "Daily limit updated ✓"
  }

internal fun commandQueuedLabel(
  command: RemoteCommand,
  deviceOnline: Boolean,
): String =
  if (!deviceOnline) {
    when (command.type) {
      com.example.phoneguard.core.RemoteCommandType.LOCK ->
        "Lock command queued and will be applied when PhoneGuard checks in again."
      com.example.phoneguard.core.RemoteCommandType.UNLOCK ->
        "Unlock command queued and will be applied when PhoneGuard checks in again."
      com.example.phoneguard.core.RemoteCommandType.BONUS_TIME ->
        "Bonus time queued and will be applied when PhoneGuard checks in again."
      com.example.phoneguard.core.RemoteCommandType.SYNC_SCHEDULE ->
        "The schedule will be applied when PhoneGuard checks in again."
      com.example.phoneguard.core.RemoteCommandType.SYNC_ALLOWED_APPS ->
        "Allowed apps will be applied when PhoneGuard checks in again."
      com.example.phoneguard.core.RemoteCommandType.SYNC_DAILY_LIMIT ->
        "Daily limit will be applied when PhoneGuard checks in again."
    }
  } else {
    when (command.type) {
      com.example.phoneguard.core.RemoteCommandType.LOCK ->
        "Lock command sent. Waiting for the Child device to confirm."
      com.example.phoneguard.core.RemoteCommandType.UNLOCK ->
        "Unlock command sent. Waiting for the Child device to confirm."
      com.example.phoneguard.core.RemoteCommandType.BONUS_TIME ->
        "Bonus time sent. Waiting for the Child device to confirm."
      com.example.phoneguard.core.RemoteCommandType.SYNC_SCHEDULE ->
        "Schedule sent. Waiting for the Child device to confirm."
      com.example.phoneguard.core.RemoteCommandType.SYNC_ALLOWED_APPS ->
        "Allowed apps sent. Waiting for the Child device to confirm."
      com.example.phoneguard.core.RemoteCommandType.SYNC_DAILY_LIMIT ->
        "Daily limit sent. Waiting for the Child device to confirm."
    }
  }

internal fun deviceStateLabel(device: ChildDevice): String =
  when (device.state) {
    DeviceAccessState.ALLOWED -> "● Phone is available"
    DeviceAccessState.LOCKED -> "● Phone is locked"
    DeviceAccessState.TEMPORARILY_ALLOWED ->
      if ((device.temporaryAccessMinutesRemaining ?: 0) > 0) {
        "● Bonus time: " +
          device.temporaryAccessMinutesRemaining +
          " min"
      } else {
        "Bonus time expired — updating status"
      }
    DeviceAccessState.OFFLINE -> "○ Device state is unknown"
  }

