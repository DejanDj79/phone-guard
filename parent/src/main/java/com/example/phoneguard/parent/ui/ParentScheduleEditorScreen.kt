package com.example.phoneguard.parent.ui

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.RemoteDaySchedule
import com.example.phoneguard.core.RemoteWeeklySchedule
import com.example.phoneguard.core.ScheduleDay
import com.example.phoneguard.core.parseScheduleTimeToMinutes

private data class ParentScheduleRowDraft(
  val enabled: Boolean,
  val start: String,
  val end: String,
)

@Composable
fun ParentScheduleEditorScreen(
  schedule: RemoteWeeklySchedule,
  saving: Boolean,
  saveError: String?,
  onSave: (RemoteWeeklySchedule) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  var rows by remember(schedule) {
    mutableStateOf(
      ScheduleDay.entries.associateWith { day ->
        val daySchedule = schedule.scheduleFor(day)
        ParentScheduleRowDraft(
          enabled = daySchedule.enabled,
          start = daySchedule.startLabel,
          end = daySchedule.endLabel,
        )
      },
    )
  }
  var validationError by remember { mutableStateOf<String?>(null) }

  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = "Lock schedule",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )

      Text(
        text = "Choose the days and hours when this Child phone should lock automatically.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      ScheduleDay.entries.forEach { day ->
        val row = rows.getValue(day)

        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(22.dp),
          color =
            if (row.enabled) {
              MaterialTheme.colorScheme.surface
            } else {
              MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            },
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = day.displayName,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text =
                    if (row.enabled) {
                      row.start + " – " + row.end
                    } else {
                      "No automatic lock"
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              Switch(
                checked = row.enabled,
                enabled = !saving,
                onCheckedChange = { enabled ->
                  rows = rows + (day to row.copy(enabled = enabled))
                  validationError = null
                },
              )
            }

            if (row.enabled) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                OutlinedButton(
                  onClick = {
                    showTimePicker(
                      context = context,
                      currentValue = row.start,
                    ) { value ->
                      rows = rows + (day to row.copy(start = value))
                      validationError = null
                    }
                  },
                  enabled = !saving,
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                  )
                  Text("  " + row.start)
                }

                OutlinedButton(
                  onClick = {
                    showTimePicker(
                      context = context,
                      currentValue = row.end,
                    ) { value ->
                      rows = rows + (day to row.copy(end = value))
                      validationError = null
                    }
                  },
                  enabled = !saving,
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                  )
                  Text("  " + row.end)
                }
              }
            }
          }
        }
      }

      val visibleError = validationError ?: saveError
      visibleError?.let {
        Text(
          text = it,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Button(
        onClick = {
          val parsedDays = mutableMapOf<ScheduleDay, RemoteDaySchedule>()
          var error: String? = null

          for (day in ScheduleDay.entries) {
            val row = rows.getValue(day)
            val start = parseScheduleTimeToMinutes(row.start)
            val end = parseScheduleTimeToMinutes(row.end)

            if (start == null || end == null) {
              error = day.displayName + ": time must use HH:mm format."
              break
            }

            if (row.enabled && start == end) {
              error = day.displayName + ": start and end times cannot be the same."
              break
            }

            parsedDays[day] =
              RemoteDaySchedule(
                enabled = row.enabled,
                startMinutes = start,
                endMinutes = end,
              )
          }

          if (error != null) {
            validationError = error
          } else {
            validationError = null
            onSave(RemoteWeeklySchedule(parsedDays))
          }
        },
        enabled = !saving,
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (saving) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
        } else {
          Text("SAVE SCHEDULE")
        }
      }

      TextButton(
        onClick = onCancel,
        enabled = !saving,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("CANCEL")
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

private fun showTimePicker(
  context: Context,
  currentValue: String,
  onSelected: (String) -> Unit,
) {
  val minutes = parseScheduleTimeToMinutes(currentValue) ?: 0
  val initialHour = minutes / 60
  val initialMinute = minutes % 60

  TimePickerDialog(
    context,
    { _, hourOfDay, minute ->
      onSelected(
        hourOfDay.toString().padStart(2, '0') +
          ":" +
          minute.toString().padStart(2, '0'),
      )
    },
    initialHour,
    initialMinute,
    true,
  ).show()
}
