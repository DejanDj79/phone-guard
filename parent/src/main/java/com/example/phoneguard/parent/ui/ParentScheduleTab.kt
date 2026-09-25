package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

@Composable
internal fun ParentScheduleTab(
  device: ChildDevice,
  scheduleLoading: Boolean,
  commandInProgress: Boolean,
  dailyLimitSaving: Boolean,
  dailyLimitNotice: String?,
  dailyLimitError: String?,
  scheduleNotice: String?,
  onEditSchedule: () -> Unit,
  onSetDailyLimit: (Int) -> Unit,
  onDisableDailyLimit: () -> Unit,
) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Lock schedule",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )

      Text(
        text = "Set the days and times when the Child phone will lock automatically.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedButton(
        onClick = onEditSchedule,
        enabled = !scheduleLoading && !commandInProgress,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (scheduleLoading) "LOADING…" else "EDIT SCHEDULE")
      }
    }
  }

  val dailyScreenTime = device.dailyScreenTime
  val dailyLimitMinutes = dailyScreenTime.limitMinutes

  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Daily screen time",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )

      if (dailyLimitMinutes == null) {
        Text(
          text = "No daily limit is set.",
          style = MaterialTheme.typography.bodyLarge,
        )
      } else {
        Text(
          text = "Daily limit: " + formatDurationMinutes(dailyLimitMinutes),
          style = MaterialTheme.typography.bodyLarge,
        )
      }

      Text(
        text =
          "Used today: " +
            formatUsageSeconds(dailyScreenTime.usedSeconds),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      dailyScreenTime.remainingMinutes?.let { remaining ->
        Text(
          text =
            if (dailyScreenTime.limitReached) {
              "Daily limit reached."
            } else {
              "Remaining: " + formatDurationMinutes(remaining)
            },
          style = MaterialTheme.typography.bodyMedium,
          color =
            if (dailyScreenTime.limitReached) {
              MaterialTheme.colorScheme.error
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
      }

      Text(
        text =
          "Only normal unlocked use counts. Allowed apps used while the phone is locked do not consume this limit.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Button(
        onClick = { onSetDailyLimit(dailyLimitMinutes ?: 120) },
        enabled = !dailyLimitSaving,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          if (dailyLimitSaving) {
            "SAVING…"
          } else if (dailyLimitMinutes == null) {
            "SET DAILY LIMIT"
          } else {
            "CHANGE DAILY LIMIT"
          },
        )
      }

      if (dailyLimitMinutes != null) {
        OutlinedButton(
          onClick = onDisableDailyLimit,
          enabled = !dailyLimitSaving,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("DISABLE DAILY LIMIT")
        }
      }

      dailyLimitNotice?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      dailyLimitError?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }

  scheduleNotice?.let { message ->
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
