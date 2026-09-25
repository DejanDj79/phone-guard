package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
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
  Text(
    text = "Automatic controls",
    style = MaterialTheme.typography.headlineSmall,
    fontWeight = FontWeight.Bold,
  )

  Text(
    text = "Choose when the phone locks and how much normal screen time is available each day.",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        ) {
          Icon(
            imageVector = Icons.Default.EditCalendar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(12.dp),
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Lock schedule",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Automatically lock the Child phone during selected hours.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      OutlinedButton(
        onClick = onEditSchedule,
        enabled = !scheduleLoading && !commandInProgress,
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (scheduleLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Text("EDIT SCHEDULE")
        }
      }

      scheduleNotice?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  val dailyScreenTime = device.dailyScreenTime
  val dailyLimitMinutes = dailyScreenTime.limitMinutes
  val remainingMinutes = dailyScreenTime.remainingMinutes

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.padding(22.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(12.dp),
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Daily screen time",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text =
              dailyLimitMinutes
                ?.let { formatDurationMinutes(it) + " daily limit" }
                ?: "No daily limit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        ScheduleMetric(
          label = "USED TODAY",
          value = formatUsageSeconds(dailyScreenTime.usedSeconds),
          modifier = Modifier.weight(1f),
        )

        ScheduleMetric(
          label = "REMAINING",
          value =
            when {
              dailyLimitMinutes == null -> "No limit"
              dailyScreenTime.limitReached -> "Reached"
              remainingMinutes != null -> formatDurationMinutes(remainingMinutes)
              else -> "—"
            },
          modifier = Modifier.weight(1f),
        )
      }

      OutlinedButton(
        onClick = { onSetDailyLimit(dailyLimitMinutes ?: 120) },
        enabled = !dailyLimitSaving,
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (dailyLimitSaving) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Text(
            if (dailyLimitMinutes == null) {
              "SET DAILY LIMIT"
            } else {
              "CHANGE DAILY LIMIT"
            },
          )
        }
      }

      if (dailyLimitMinutes != null) {
        TextButton(
          onClick = onDisableDailyLimit,
          enabled = !dailyLimitSaving,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("REMOVE DAILY LIMIT")
        }
      }

      Text(
        text = "Allowed apps used while the phone is locked do not count toward this total.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

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
}

@Composable
private fun ScheduleMetric(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
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
