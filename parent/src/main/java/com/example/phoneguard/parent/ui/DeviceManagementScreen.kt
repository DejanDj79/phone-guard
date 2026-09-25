package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DeviceManagementScreen(
  currentName: String,
  renaming: Boolean,
  unpairing: Boolean,
  errorMessage: String?,
  onRename: (String) -> Unit,
  onUnpair: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var name by remember(currentName) { mutableStateOf(currentName) }
  var showUnpairConfirmation by remember { mutableStateOf(false) }
  val busy = renaming || unpairing
  val normalizedName = name.trim().replace(Regex("\\s+"), " ")

  Surface(
    modifier = modifier.fillMaxSize(),
    color = Color.Transparent,
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
    ) {
      ParentDetailHeader(
        title = "Device management",
        onBack = onBack,
      )

      Column(
        modifier =
          Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          text = "Rename the paired Child device or remove this Parent connection.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = name,
          onValueChange = { value ->
            if (value.length <= 40) {
              name = value
            }
          },
          enabled = !busy,
          label = { Text("Device name") },
          singleLine = true,
          supportingText = { Text("1–40 characters") },
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(
          onClick = { onRename(normalizedName) },
          enabled =
            !busy &&
              normalizedName.isNotBlank() &&
              normalizedName != currentName,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (renaming) "SAVING…" else "SAVE NAME")
        }

        Text(
          text = "Remove Parent connection",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )

        Text(
          text =
            "Unpairing revokes this Parent app immediately. The Child keeps its current lock schedule and allowed-app rules until it is paired again.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
          onClick = { showUnpairConfirmation = true },
          enabled = !busy,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("UNPAIR DEVICE")
        }

        errorMessage?.let { message ->
          Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }

  if (showUnpairConfirmation) {
    AlertDialog(
      onDismissRequest = {
        if (!unpairing) showUnpairConfirmation = false
      },
      title = {
        Text("Unpair device?")
      },
      text = {
        Text(
          "This Parent app will immediately lose control of the Child device. " +
            "The Child's current schedule and allowed apps will remain active.",
        )
      },
      confirmButton = {
        OutlinedButton(
          onClick = {
            showUnpairConfirmation = false
            onUnpair()
          },
          enabled = !unpairing,
        ) {
          Text(if (unpairing) "UNPAIRING…" else "UNPAIR")
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = { showUnpairConfirmation = false },
          enabled = !unpairing,
        ) {
          Text("CANCEL")
        }
      },
    )
  }
}
