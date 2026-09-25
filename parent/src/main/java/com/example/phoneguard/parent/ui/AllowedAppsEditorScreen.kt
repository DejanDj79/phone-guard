package com.example.phoneguard.parent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.AllowedAppsSnapshot

@Composable
fun AllowedAppsEditorScreen(
  snapshot: AllowedAppsSnapshot,
  saving: Boolean,
  saveError: String?,
  onSave: (Set<String>) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedPackages by remember(snapshot) {
    mutableStateOf(snapshot.allowedPackages)
  }

  Surface(modifier = modifier.fillMaxSize()) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
    ) {
      Text(
        text = "Allowed apps",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text =
          "Choose which apps the Child can use while the phone is locked. " +
            "System settings and other sensitive system apps are excluded.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = selectedPackages.size.toString() + " selected",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(16.dp))

      if (snapshot.installedApps.isEmpty()) {
        Text(
          text =
            "No app inventory is available yet. Open PhoneGuard on the Child device " +
              "while it is online, then return here and try again.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        snapshot.installedApps.forEachIndexed { index, app ->
          val selected = app.packageName in selectedPackages

          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clickable(enabled = !saving) {
                  selectedPackages =
                    if (selected) {
                      selectedPackages - app.packageName
                    } else {
                      selectedPackages + app.packageName
                    }
                }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Checkbox(
              checked = selected,
              onCheckedChange = { checked ->
                selectedPackages =
                  if (checked) {
                    selectedPackages + app.packageName
                  } else {
                    selectedPackages - app.packageName
                  }
              },
              enabled = !saving,
            )

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          if (index < snapshot.installedApps.lastIndex) {
            HorizontalDivider()
          }
        }
      }

      saveError?.let { message ->
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Button(
        onClick = { onSave(selectedPackages) },
        enabled = !saving && snapshot.installedApps.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (saving) "SAVING…" else "SAVE ALLOWED APPS")
      }

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedButton(
        onClick = onCancel,
        enabled = !saving,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("CANCEL")
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
