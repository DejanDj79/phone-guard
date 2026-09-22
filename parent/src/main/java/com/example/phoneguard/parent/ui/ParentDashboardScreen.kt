package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState
import com.example.phoneguard.core.RemoteCommand
import com.example.phoneguard.core.RemoteCommandType

@Composable
fun ParentDashboardScreen(
  modifier: Modifier = Modifier,
) {
  var device by remember {
    mutableStateOf(
      ChildDevice(
        deviceId = "mock-redmi-note-10",
        displayName = "Redmi Note 10",
        state = DeviceAccessState.ALLOWED,
      ),
    )
  }
  var lastCommand by remember { mutableStateOf<RemoteCommand?>(null) }

  fun sendMock(command: RemoteCommand) {
    lastCommand = command

    device =
      when (command.type) {
        RemoteCommandType.LOCK ->
          device.copy(
            state = DeviceAccessState.LOCKED,
            temporaryAccessMinutesRemaining = null,
          )

        RemoteCommandType.UNLOCK ->
          device.copy(
            state = DeviceAccessState.ALLOWED,
            temporaryAccessMinutesRemaining = null,
          )

        RemoteCommandType.BONUS_TIME ->
          device.copy(
            state = DeviceAccessState.TEMPORARILY_ALLOWED,
            temporaryAccessMinutesRemaining = command.bonusMinutes,
          )
      }
  }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 32.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    Text(
      text = "PhoneGuard Parent",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )

    Text(
      text = "Roditeljska kontrola",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Test uređaj",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
          text = device.displayName,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
        )

        Text(
          text = deviceStateLabel(device),
          style = MaterialTheme.typography.bodyLarge,
        )

        Text(
          text = "Device ID: " + device.deviceId,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
          text = "Mock režim — backend još nije povezan",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Brze komande",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )

        Button(
          onClick = { sendMock(RemoteCommand.lock()) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("LOCK NOW")
        }

        OutlinedButton(
          onClick = { sendMock(RemoteCommand.unlock()) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("UNLOCK")
        }

        Text(
          text = "Dodatno vreme",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(15, 30, 60).forEach { minutes ->
            OutlinedButton(
              onClick = { sendMock(RemoteCommand.bonusTime(minutes)) },
              modifier = Modifier.weight(1f),
            ) {
              Text("+" + minutes)
            }
          }
        }
      }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "Poslednja komanda",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = commandLabel(lastCommand),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Sledeći korak: pairing identitet i transport komandi između Parent i Child aplikacije.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun deviceStateLabel(device: ChildDevice): String =
  when (device.state) {
    DeviceAccessState.ALLOWED -> "● Telefon je dostupan"
    DeviceAccessState.LOCKED -> "● Telefon je zaključan"
    DeviceAccessState.TEMPORARILY_ALLOWED ->
      "● Dodatno vreme: " + device.temporaryAccessMinutesRemaining + " min"
    DeviceAccessState.OFFLINE -> "○ Uređaj je offline"
  }

private fun commandLabel(command: RemoteCommand?): String =
  when (command?.type) {
    null -> "Nema poslatih komandi"
    RemoteCommandType.LOCK -> "LOCK NOW"
    RemoteCommandType.UNLOCK -> "UNLOCK"
    RemoteCommandType.BONUS_TIME -> "+" + command.bonusMinutes + " min"
  }

@Preview(showBackground = true)
@Composable
private fun ParentDashboardPreview() {
  MaterialTheme {
    ParentDashboardScreen()
  }
}
