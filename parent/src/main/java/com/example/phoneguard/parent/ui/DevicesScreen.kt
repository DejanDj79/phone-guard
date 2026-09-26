package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
  Surface(
    modifier = modifier.fillMaxSize(),
    color = Color.Transparent,
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
    ) {
      ParentDetailHeader(
        title = "Devices",
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
        text = "Choose which Child device you want to manage.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      devices.forEach { device ->
        val selected = device.deviceId == selectedDeviceId

        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
          ),
        ) {
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
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
                Text("SELECT DEVICE")
              }
            }
          }
        }
      }

      OutlinedButton(
        onClick = onAddDevice,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
        Text("+ ADD DEVICE")
      }

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
