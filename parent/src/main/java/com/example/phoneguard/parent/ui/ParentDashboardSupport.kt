package com.example.phoneguard.parent.ui

import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.phoneguard.core.AllowedAppsSnapshot
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.InstalledAppInfo
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
      OutlinedButton(onClick = { onConfirm(selectedMinutes) }) {
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
      OutlinedButton(
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
  parentEmail: String?,
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
        .verticalScroll(rememberScrollState())
        .statusBarsPadding()
        .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Text(
      text = "Pair a Child phone",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )

    Text(
      text =
        "Connect a Child phone to this Parent account. Existing Child devices on this account will stay connected.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Parent account",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
        )

        Text(
          text =
            parentEmail
              ?.takeIf { it.isNotBlank() }
              ?: "Signed in Parent account",
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
        )

        Text(
          text = "The paired Child phone will belong to this account.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
      }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        PairingStep(
          number = "1",
          title = "Open PhoneGuard on the Child phone",
          description = "Keep the Child pairing screen open.",
        )

        PairingStep(
          number = "2",
          title = "Find the 6-character code",
          description = "The code is temporary and is verified by the PhoneGuard backend.",
        )

        PairingStep(
          number = "3",
          title = "Enter the code here",
          description = "Letters and numbers are accepted.",
        )
      }
    }

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
      label = { Text("6-character pairing code") },
      supportingText = {
        Text(
          if (pairingCode.isEmpty()) {
            "Example: AB12CD"
          } else {
            pairingCode.length.toString() + " / 6"
          },
        )
      },
      singleLine = true,
      enabled = !pairingInProgress && !switchingAccount,
      keyboardOptions =
        KeyboardOptions(
          capitalization = KeyboardCapitalization.Characters,
          keyboardType = KeyboardType.Ascii,
        ),
      modifier = Modifier.fillMaxWidth(),
    )

    errorMessage?.let { message ->
      Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
      )
    }

    OutlinedButton(
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
      enabled =
        pairingCode.length == 6 &&
          !pairingInProgress &&
          !switchingAccount,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        if (pairingInProgress) {
          "PAIRING…"
        } else {
          "PAIR CHILD PHONE"
        },
      )
    }

    onCancel?.let {
      OutlinedButton(
        onClick = it,
        enabled = !pairingInProgress && !switchingAccount,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("BACK TO DEVICES")
      }
    }

    Text(
      text =
        "Pairing a new Child does not remove devices already connected to this Parent account.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun PairingStep(
  number: String,
  title: String,
  description: String,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Text(
      text = "Step " + number + " · " + title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = description,
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
  inventory: AllowedAppsSnapshot?,
) {
  if (day == null) {
    Text(
      text = emptyMessage,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "Tracked app time",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = formatUsageSeconds(day.totalSeconds),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    }

    Text(
      text = formatUsageDate(day.usageDate),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  val topApps =
    day.apps
      .filter { it.seconds > 0 }
      .sortedByDescending { it.seconds }
      .take(APP_USAGE_PREVIEW_COUNT)

  if (topApps.isEmpty()) {
    Text(
      text = "No launcher app has accumulated usage for this day.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  } else {
    topApps.forEach { app ->
      UsageAppCard(
        app = app,
        inventory = inventory,
      )
    }
  }
}

@Composable
internal fun AppUsageWeekContent(
  days: List<AppUsageDay>,
  referenceDate: String?,
  inventory: AllowedAppsSnapshot?,
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

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    UsageSummaryCard(
      label = "7-DAY TOTAL",
      value = formatUsageSeconds(totalSeconds),
      modifier = Modifier.weight(1f),
    )
    UsageSummaryCard(
      label = "DAILY AVG",
      value = formatUsageSeconds(averageSeconds),
      modifier = Modifier.weight(1f),
    )
  }

  Text(
    text = "Daily usage",
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.Bold,
  )

  UsageWeekBarChart(
    dailySeconds = dailySeconds,
    maxSeconds = maxSeconds,
  )

  val weekDays =
    dateKeys.mapNotNull(byDate::get)
  val topApps =
    aggregateAppUsage(weekDays)
      .take(APP_USAGE_PREVIEW_COUNT)

  Text(
    text = "Top 5 apps",
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.Bold,
  )

  if (topApps.isEmpty()) {
    Text(
      text = "No app usage recorded in this 7-day period.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  } else {
    topApps.forEach { app ->
      UsageAppCard(
        app = app,
        inventory = inventory,
      )
    }
  }
}

@Composable
private fun UsageAppCard(
  app: AppUsageEntry,
  inventory: AllowedAppsSnapshot?,
) {
  val installedApp =
    inventory
      ?.installedApps
      ?.firstOrNull { it.packageName == app.packageName }
      ?: InstalledAppInfo(
        packageName = app.packageName,
        label = app.label,
      )

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      ParentAppIcon(
        app = installedApp,
        modifier = Modifier.size(38.dp),
      )

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = app.label,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
        )
        Text(
          text = "App usage",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Text(
        text = formatUsageSeconds(app.seconds),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = ParentAccentColor,
        textAlign = TextAlign.End,
      )
    }
  }
}

@Composable
private fun UsageSummaryCard(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun UsageWeekBarChart(
  dailySeconds: List<Pair<String, Int>>,
  maxSeconds: Int,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(5.dp),
      verticalAlignment = Alignment.Bottom,
    ) {
      dailySeconds.forEach { (dateKey, seconds) ->
        val ratio =
          (seconds.toFloat() / maxSeconds.coerceAtLeast(1).toFloat())
            .coerceIn(0f, 1f)
        val barHeight =
          if (seconds <= 0) {
            4.dp
          } else {
            (96.dp * ratio).coerceAtLeast(10.dp)
          }

        Column(
          modifier = Modifier.weight(1f),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .height(100.dp),
            contentAlignment = Alignment.BottomCenter,
          ) {
            Surface(
              modifier =
                Modifier
                  .width(18.dp)
                  .height(barHeight),
              shape = RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp),
              color =
                if (seconds > 0) {
                  ParentAccentColor
                } else {
                  MaterialTheme.colorScheme.outlineVariant
                },
            ) {}
          }

          Text(
            text = formatUsageDayLabel(dateKey),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
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

