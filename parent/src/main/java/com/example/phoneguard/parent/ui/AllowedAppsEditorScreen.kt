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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .statusBarsPadding()
          .padding(horizontal = 20.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = "Allowed apps",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )

      Text(
        text = "Apps enabled here remain available while the Child phone is locked.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
      ) {
        Text(
          text = selectedPackages.size.toString() + " selected",
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

      if (snapshot.installedApps.isEmpty()) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(22.dp),
          color = MaterialTheme.colorScheme.surface,
        ) {
          Text(
            text =
              "No app list is available yet. Keep the Child phone online and open PhoneGuard there, then try again.",
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        snapshot.installedApps.forEach { app ->
          val selected = app.packageName in selectedPackages

          Surface(
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
                },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color =
                  if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                  } else {
                    MaterialTheme.colorScheme.surfaceVariant
                  },
              ) {
                Icon(
                  imageVector = Icons.Default.Apps,
                  contentDescription = null,
                  tint =
                    if (selected) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    },
                  modifier = Modifier.padding(10.dp),
                )
              }

              Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
              )

              Switch(
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
            }
          }
        }
      }

      saveError?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      Button(
        onClick = { onSave(selectedPackages) },
        enabled = !saving && snapshot.installedApps.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (saving) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
        } else {
          Text("SAVE ALLOWED APPS")
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
