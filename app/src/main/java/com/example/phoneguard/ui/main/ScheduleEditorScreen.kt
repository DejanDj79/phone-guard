package com.example.phoneguard.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.phoneguard.data.DaySchedule
import com.example.phoneguard.data.GuardDay
import com.example.phoneguard.data.WeeklySchedule
import com.example.phoneguard.data.parseTimeToMinutes
import com.example.phoneguard.theme.PhoneGuardTheme

private data class ScheduleRowDraft(
  val enabled: Boolean,
  val start: String,
  val end: String,
)

@Composable
fun ScheduleEditorScreen(
  schedule: WeeklySchedule,
  onSave: (WeeklySchedule) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var rows by remember(schedule) {
    mutableStateOf(
      GuardDay.entries.associateWith { day ->
        val daySchedule = schedule.scheduleFor(day)
        ScheduleRowDraft(
          enabled = daySchedule.enabled,
          start = daySchedule.startLabel,
          end = daySchedule.endLabel,
        )
      },
    )
  }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  Surface(modifier = modifier.fillMaxSize()) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
    ) {
      Text(
        text = "Raspored zaključavanja",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Za noćnu blokadu koristi, na primer, 22:00–07:00. Raspored može biti različit za svaki dan.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(20.dp))

      GuardDay.entries.forEachIndexed { index, day ->
        val row = rows.getValue(day)

        Column(
          modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = day.displayName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.weight(1f),
            )

            Switch(
              checked = row.enabled,
              onCheckedChange = { enabled ->
                rows = rows + (day to row.copy(enabled = enabled))
                errorMessage = null
              },
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            OutlinedTextField(
              value = row.start,
              onValueChange = { value ->
                rows = rows + (day to row.copy(start = value.take(5)))
                errorMessage = null
              },
              enabled = row.enabled,
              label = { Text("Od") },
              placeholder = { Text("22:00") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f),
            )

            OutlinedTextField(
              value = row.end,
              onValueChange = { value ->
                rows = rows + (day to row.copy(end = value.take(5)))
                errorMessage = null
              },
              enabled = row.enabled,
              label = { Text("Do") },
              placeholder = { Text("07:00") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f),
            )
          }
        }

        if (index < GuardDay.entries.lastIndex) {
          HorizontalDivider()
        }
      }

      errorMessage?.let {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = it,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Button(
        onClick = {
          val parsedDays = mutableMapOf<GuardDay, DaySchedule>()
          var validationError: String? = null

          GuardDay.entries.forEach { day ->
            val row = rows.getValue(day)
            val start = parseTimeToMinutes(row.start)
            val end = parseTimeToMinutes(row.end)

            if (start == null || end == null) {
              validationError = day.displayName + ": vreme mora biti u formatu HH:mm."
              return@forEach
            }

            if (row.enabled && start == end) {
              validationError = day.displayName + ": početak i kraj ne mogu biti isti."
              return@forEach
            }

            parsedDays[day] =
              DaySchedule(
                enabled = row.enabled,
                startMinutes = start,
                endMinutes = end,
              )
          }

          if (validationError != null) {
            errorMessage = validationError
          } else {
            onSave(WeeklySchedule(parsedDays))
          }
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("SAČUVAJ RASPORED")
      }

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("ODUSTANI")
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleEditorPreview() {
  PhoneGuardTheme {
    ScheduleEditorScreen(
      schedule =
        WeeklySchedule(
          GuardDay.entries.associateWith {
            DaySchedule(
              enabled = it !in listOf(GuardDay.FRIDAY, GuardDay.SATURDAY),
              startMinutes = 22 * 60,
              endMinutes = 7 * 60,
            )
          },
        ),
      onSave = {},
      onCancel = {},
    )
  }
}
