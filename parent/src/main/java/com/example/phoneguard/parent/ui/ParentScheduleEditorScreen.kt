package com.example.phoneguard.parent.ui

import android.widget.TimePicker
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
  var selectedDay by remember { mutableStateOf(ScheduleDay.MONDAY) }
  var editingStart by remember { mutableStateOf(true) }
  var validationError by remember { mutableStateOf<String?>(null) }

  val selectedRow = rows.getValue(selectedDay)
  val selectedTime =
    if (editingStart) {
      selectedRow.start
    } else {
      selectedRow.end
    }
  val selectedMinutes = parseScheduleTimeToMinutes(selectedTime) ?: 0

  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = "Lock schedule",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )

      Text(
        text = "Select a day, enable automatic lock and choose the start and end times.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        ScheduleDay.entries.forEach { day ->
          val row = rows.getValue(day)
          val selected = day == selectedDay

          Surface(
            modifier =
              Modifier
                .weight(1f)
                .clickable(enabled = !saving) {
                  selectedDay = day
                  editingStart = true
                  validationError = null
                },
            shape = RoundedCornerShape(14.dp),
            color =
              when {
                selected -> MaterialTheme.colorScheme.primary
                row.enabled -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
              },
          ) {
            Text(
              text = day.displayName.take(3),
              modifier = Modifier.padding(vertical = 11.dp),
              style = MaterialTheme.typography.labelMedium,
              fontWeight =
                if (selected) {
                  FontWeight.Bold
                } else {
                  FontWeight.SemiBold
                },
              color =
                when {
                  selected -> MaterialTheme.colorScheme.onPrimary
                  row.enabled -> MaterialTheme.colorScheme.onPrimaryContainer
                  else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
              textAlign = TextAlign.Center,
              maxLines = 1,
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
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = selectedDay.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text =
                  if (selectedRow.enabled) {
                    selectedRow.start + " – " + selectedRow.end
                  } else {
                    "Automatic lock is off"
                  },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Switch(
              checked = selectedRow.enabled,
              enabled = !saving,
              onCheckedChange = { enabled ->
                rows =
                  rows +
                    (
                      selectedDay to
                        selectedRow.copy(enabled = enabled)
                    )
                validationError = null
              },
            )
          }

          if (selectedRow.enabled) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (editingStart) {
                Button(
                  onClick = { editingStart = true },
                  enabled = !saving,
                  modifier = Modifier.weight(1f),
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                      text = "FROM",
                      style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                      text = selectedRow.start,
                      style = MaterialTheme.typography.titleMedium,
                    )
                  }
                }
              } else {
                OutlinedButton(
                  onClick = { editingStart = true },
                  enabled = !saving,
                  modifier = Modifier.weight(1f),
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                      text = "FROM",
                      style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                      text = selectedRow.start,
                      style = MaterialTheme.typography.titleMedium,
                    )
                  }
                }
              }

              if (!editingStart) {
                Button(
                  onClick = { editingStart = false },
                  enabled = !saving,
                  modifier = Modifier.weight(1f),
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                      text = "TO",
                      style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                      text = selectedRow.end,
                      style = MaterialTheme.typography.titleMedium,
                    )
                  }
                }
              } else {
                OutlinedButton(
                  onClick = { editingStart = false },
                  enabled = !saving,
                  modifier = Modifier.weight(1f),
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                      text = "TO",
                      style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                      text = selectedRow.end,
                      style = MaterialTheme.typography.titleMedium,
                    )
                  }
                }
              }
            }

            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(22.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
              AndroidView(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                factory = { context ->
                  TimePicker(context).apply {
                    setIs24HourView(true)
                  }
                },
                update = { picker ->
                  picker.setOnTimeChangedListener(null)
                  picker.hour = selectedMinutes / 60
                  picker.minute = selectedMinutes % 60
                  picker.isEnabled = !saving

                  picker.setOnTimeChangedListener { _, hourOfDay, minute ->
                    val value =
                      hourOfDay.toString().padStart(2, '0') +
                        ":" +
                        minute.toString().padStart(2, '0')
                    val current = rows.getValue(selectedDay)

                    rows =
                      rows +
                        (
                          selectedDay to
                            if (editingStart) {
                              current.copy(start = value)
                            } else {
                              current.copy(end = value)
                            }
                        )
                    validationError = null
                  }
                },
              )
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
