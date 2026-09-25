package com.example.phoneguard.parent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.RemoteDaySchedule
import com.example.phoneguard.core.RemoteWeeklySchedule
import com.example.phoneguard.core.ScheduleDay
import com.example.phoneguard.core.parseScheduleTimeToMinutes

private data class ParentScheduleRowDraft(
  val start: String,
  val end: String,
)

private fun ParentScheduleRowDraft.isActive(): Boolean =
  start != "00:00" || end != "00:00"

@OptIn(ExperimentalMaterial3Api::class)
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

        if (daySchedule.enabled) {
          ParentScheduleRowDraft(
            start = daySchedule.startLabel,
            end = daySchedule.endLabel,
          )
        } else {
          ParentScheduleRowDraft(
            start = "00:00",
            end = "00:00",
          )
        }
      },
    )
  }
  var selectedDay by remember { mutableStateOf(ScheduleDay.MONDAY) }
  var editingStart by remember { mutableStateOf(true) }
  var pickerRevision by remember { mutableIntStateOf(0) }
  var validationError by remember { mutableStateOf<String?>(null) }

  val selectedRow = rows.getValue(selectedDay)

  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
    ) {
      ParentDetailHeader(
        title = "Lock schedule",
        onBack = onCancel,
      )

      Column(
        modifier =
          Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
        text = "Choose a day and set its lock period. 00:00 – 00:00 means the day is inactive.",
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
                row.isActive() -> MaterialTheme.colorScheme.primaryContainer
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
                  row.isActive() -> MaterialTheme.colorScheme.onPrimaryContainer
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
                  if (selectedRow.isActive()) {
                    selectedRow.start + " – " + selectedRow.end
                  } else {
                    "Inactive"
                  },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            if (!selectedRow.isActive()) {
              Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
              ) {
                Text(
                  text = "OFF",
                  modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

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

          key(selectedDay, editingStart, pickerRevision) {
            val initialValue =
              if (editingStart) {
                selectedRow.start
              } else {
                selectedRow.end
              }
            val initialMinutes =
              parseScheduleTimeToMinutes(initialValue) ?: 0
            val pickerState =
              rememberTimePickerState(
                initialHour = initialMinutes / 60,
                initialMinute = initialMinutes % 60,
                is24Hour = true,
              )

            LaunchedEffect(pickerState.hour, pickerState.minute) {
              val value =
                pickerState.hour.toString().padStart(2, '0') +
                  ":" +
                  pickerState.minute.toString().padStart(2, '0')
              val current = rows.getValue(selectedDay)

              val updated =
                if (editingStart) {
                  current.copy(start = value)
                } else {
                  current.copy(end = value)
                }

              if (updated != current) {
                rows = rows + (selectedDay to updated)
                validationError = null
              }
            }

            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(vertical = 2.dp),
              contentAlignment = Alignment.Center,
            ) {
              TimePicker(
                state = pickerState,
                modifier = Modifier.scale(0.78f),
              )
            }
          }

          if (selectedRow.isActive()) {
            TextButton(
              onClick = {
                rows =
                  rows +
                    (
                      selectedDay to
                        ParentScheduleRowDraft(
                          start = "00:00",
                          end = "00:00",
                        )
                    )
                editingStart = true
                pickerRevision += 1
                validationError = null
              },
              enabled = !saving,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("CLEAR DAY")
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

            val inactive = start == 0 && end == 0

            if (!inactive && start == end) {
              error = day.displayName + ": start and end times cannot be the same."
              break
            }

            parsedDays[day] =
              RemoteDaySchedule(
                enabled = !inactive,
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

        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }
}
