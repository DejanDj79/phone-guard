package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
        text = "Podesi periode kada će Child telefon biti zaključan. Noćni period može prelaziti preko ponoći, na primer 22:00–07:00.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(20.dp))

      ScheduleDay.entries.forEachIndexed { index, day ->
        val row = rows.getValue(day)

        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 10.dp),
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
              enabled = !saving,
              onCheckedChange = { enabled ->
                rows = rows + (day to row.copy(enabled = enabled))
                validationError = null
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
                validationError = null
              },
              enabled = row.enabled && !saving,
              label = { Text("Od") },
              placeholder = { Text("22:00") },
              singleLine = true,
              keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f),
            )

            OutlinedTextField(
              value = row.end,
              onValueChange = { value ->
                rows = rows + (day to row.copy(end = value.take(5)))
                validationError = null
              },
              enabled = row.enabled && !saving,
              label = { Text("Do") },
              placeholder = { Text("07:00") },
              singleLine = true,
              keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f),
            )
          }
        }

        if (index < ScheduleDay.entries.lastIndex) {
          HorizontalDivider()
        }
      }

      val visibleError = validationError ?: saveError
      visibleError?.let {
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
          val parsedDays = mutableMapOf<ScheduleDay, RemoteDaySchedule>()
          var error: String? = null

          for (day in ScheduleDay.entries) {
            val row = rows.getValue(day)
            val start = parseScheduleTimeToMinutes(row.start)
            val end = parseScheduleTimeToMinutes(row.end)

            if (start == null || end == null) {
              error = day.displayName + ": vreme mora biti u formatu HH:mm."
              break
            }

            if (row.enabled && start == end) {
              error = day.displayName + ": početak i kraj ne mogu biti isti."
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
        Text(if (saving) "ČUVAM…" else "SAČUVAJ RASPORED")
      }

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedButton(
        onClick = onCancel,
        enabled = !saving,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("ODUSTANI")
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
