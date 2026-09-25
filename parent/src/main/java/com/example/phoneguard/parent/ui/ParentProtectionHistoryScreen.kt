package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.phoneguard.parent.data.ProtectionHistoryEvent

@Composable
internal fun ParentProtectionHistoryScreen(
  events: List<ProtectionHistoryEvent>,
  loading: Boolean,
  clearing: Boolean,
  errorMessage: String?,
  onBack: () -> Unit,
  onRequestClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showClearConfirmation by remember { mutableStateOf(false) }

  Surface(
    modifier = modifier.fillMaxSize(),
    color = Color.Transparent,
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
    ) {
      ParentDetailHeader(
        title = "Protection history",
        onBack = onBack,
      )

      LazyColumn(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          Surface(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "ALL EVENTS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = events.size.toString() + " recorded",
                style = MaterialTheme.typography.titleMedium,
              )
            }
          }
        }

        if (loading && events.isEmpty()) {
          item {
            CircularProgressIndicator(
              modifier = Modifier.padding(vertical = 24.dp),
              strokeWidth = 2.dp,
            )
          }
        } else if (events.isEmpty() && errorMessage == null) {
          item {
            Text(
              text = "No protection events recorded.",
              modifier = Modifier.padding(vertical = 18.dp),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          itemsIndexed(events) { index, event ->
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(18.dp),
              color = MaterialTheme.colorScheme.surface,
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
              ) {
                if (index > 0) {
                  Divider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                  )
                }
                ProtectionHistoryRow(event)
              }
            }
          }
        }

        errorMessage?.let { message ->
          item {
            Text(
              text = message,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }

        if (events.isNotEmpty()) {
          item {
            OutlinedButton(
              onClick = { showClearConfirmation = true },
              enabled = !clearing,
              modifier = Modifier.heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
              if (clearing) {
                CircularProgressIndicator(
                  strokeWidth = 2.dp,
                )
              } else {
                Text("CLEAR HISTORY")
              }
            }
          }
        }

        item {
          androidx.compose.foundation.layout.Spacer(
            modifier = Modifier.padding(bottom = 24.dp),
          )
        }
      }
    }
  }

  if (showClearConfirmation) {
    AlertDialog(
      onDismissRequest = {
        if (!clearing) {
          showClearConfirmation = false
        }
      },
      title = {
        Text("Clear protection history?")
      },
      text = {
        Text("This permanently deletes all protection history for this Child device.")
      },
      confirmButton = {
        OutlinedButton(
          onClick = {
            showClearConfirmation = false
            onRequestClear()
          },
          enabled = !clearing,
        modifier = Modifier.heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
          Text("CLEAR")
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = { showClearConfirmation = false },
          enabled = !clearing,
        modifier = Modifier.heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
          Text("CANCEL")
        }
      },
    )
  }
}
