package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState

@Composable
fun DevicesScreen(
  devices: List<ChildDevice>,
  selectedDeviceId: String,
  busy: Boolean,
  onSelectDevice: (ChildDevice) -> Unit,
  onAddDevice: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(modifier = modifier.fillMaxSize()) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .statusBarsPadding()
          .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = "Devices",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )

      Text(
        text = "Choose which Child device you want to manage.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      devices.forEach { device ->
        val selected = device.deviceId == selectedDeviceId

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = if (selected) "✓ " + device.displayName else device.displayName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )

            Text(
              text =
                devicePresenceSummary(device.lastSeenAt) +
                  " · " +
                  devicesStateLabel(device),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (selected) {
              Text(
                text = "Currently selected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            } else {
              OutlinedButton(
                onClick = { onSelectDevice(device) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("SELECT DEVICE")
              }
            }
          }
        }
      }

      Button(
        onClick = onAddDevice,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("+ ADD DEVICE")
      }

      OutlinedButton(
        onClick = onBack,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("BACK")
      }
    }
  }
}


private fun devicesStateLabel(device: ChildDevice): String =
  when (device.state) {
    DeviceAccessState.ALLOWED -> "Phone is available"
    DeviceAccessState.LOCKED -> "Phone is locked"
    DeviceAccessState.TEMPORARILY_ALLOWED ->
      if ((device.temporaryAccessMinutesRemaining ?: 0) > 0) {
        "Bonus time: " + device.temporaryAccessMinutesRemaining + " min"
      } else {
        "Bonus time expired"
      }
    DeviceAccessState.OFFLINE -> "Device state is unknown"
  }
