package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ParentAppsTab(
  allowedAppsLoading: Boolean,
  allowedAppsSaving: Boolean,
  commandInProgress: Boolean,
  allowedAppsNotice: String?,
  onManageAllowedApps: () -> Unit,
) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Allowed apps",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )

      Text(
        text =
          "Choose which apps can still be used while the Child phone is locked.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedButton(
        onClick = onManageAllowedApps,
        enabled =
          !allowedAppsLoading &&
            !allowedAppsSaving &&
            !commandInProgress,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          if (allowedAppsLoading) {
            "LOADING…"
          } else {
            "MANAGE ALLOWED APPS"
          },
        )
      }

      allowedAppsNotice?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
