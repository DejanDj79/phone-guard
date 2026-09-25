package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.AllowedAppsSnapshot
import com.example.phoneguard.parent.data.AppUsageDay

@Composable
internal fun ParentAppsTab(
  snapshot: AllowedAppsSnapshot?,
  todayUsage: AppUsageDay?,
  allowedAppsLoading: Boolean,
  appUsageLoading: Boolean,
  allowedAppsSaving: Boolean,
  commandInProgress: Boolean,
  allowedAppsNotice: String?,
  allowedAppsError: String?,
  onManageAllowedApps: () -> Unit,
) {
  val allowedPackages = snapshot?.allowedPackages.orEmpty()
  val allowedApps =
    snapshot
      ?.installedApps
      ?.filter { it.packageName in allowedPackages }
      ?.sortedBy { it.label.lowercase() }
      .orEmpty()

  val usageByPackage =
    todayUsage
      ?.apps
      ?.associateBy { it.packageName }
      .orEmpty()

  Text(
    text = "Apps allowed while the Child phone is locked, with today's usage.",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Allowed apps",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text =
              if (snapshot == null) {
                "Loading app list…"
              } else {
                allowedApps.size.toString() + " allowed"
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Text(
          text = "TODAY",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.End,
        )
      }

      if ((allowedAppsLoading || appUsageLoading) && snapshot == null) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 28.dp),
          horizontalArrangement = Arrangement.Center,
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
          )
        }
      } else if (allowedApps.isEmpty()) {
        Text(
          text =
            if (snapshot == null) {
              "Could not load the app list yet."
            } else {
              "No apps are currently allowed while the phone is locked."
            },
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        allowedApps.forEachIndexed { index, app ->
          if (index > 0) {
            Divider(
              modifier = Modifier.padding(start = 74.dp),
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            )
          }

          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            ParentAppIcon(
              app = app,
              modifier = Modifier.size(42.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
              )
              Text(
                text = "Allowed while locked",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Text(
              text =
                formatUsageSeconds(
                  usageByPackage[app.packageName]?.seconds ?: 0,
                ),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.End,
            )
          }
        }
      }

      Divider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
      )

      OutlinedButton(
        onClick = onManageAllowedApps,
        enabled =
          !allowedAppsLoading &&
            !allowedAppsSaving &&
            !commandInProgress,
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
        if (allowedAppsLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
          )
          Text("  MANAGE APPS")
        }
      }
    }
  }

  allowedAppsNotice?.let { message ->
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  allowedAppsError?.let { message ->
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
    )
  }
}
