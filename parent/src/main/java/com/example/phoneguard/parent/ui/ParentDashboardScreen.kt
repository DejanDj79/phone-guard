package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.widget.NumberPicker
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState
import com.example.phoneguard.core.PairingRequest
import com.example.phoneguard.core.PairingResult
import com.example.phoneguard.core.RemoteCommand
import com.example.phoneguard.core.RemoteCommandType
import com.example.phoneguard.parent.data.CommandDeliveryResult
import com.example.phoneguard.parent.data.CommandResult
import com.example.phoneguard.parent.data.HttpCommandGateway
import com.example.phoneguard.parent.data.HttpPairingGateway
import com.example.phoneguard.parent.data.PairingGateway
import com.example.phoneguard.parent.data.ParentSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ParentDashboardScreen(
  modifier: Modifier = Modifier,
  pairingGateway: PairingGateway? = null,
) {
  val context = LocalContext.current
  val settingsStore =
    remember(context) {
      ParentSettingsStore(context.applicationContext)
    }
  val defaultGateway = remember { HttpPairingGateway() }
  val gateway = pairingGateway ?: defaultGateway
  val commandGateway = remember { HttpCommandGateway() }
  val scope = rememberCoroutineScope()

  var pairedDevice by remember {
    mutableStateOf(settingsStore.loadPairedDevice())
  }
  var lastCommand by remember { mutableStateOf<RemoteCommand?>(null) }
  var commandDeliveryStatus by remember { mutableStateOf<String?>(null) }
  var commandInProgress by remember { mutableStateOf(false) }
  var commandError by remember { mutableStateOf<String?>(null) }
  var showBonusTimePicker by remember { mutableStateOf(false) }
  var selectedBonusMinutes by remember { mutableStateOf(15) }

  if (pairedDevice == null) {
    PairDeviceScreen(
      modifier = modifier,
      onPair = { rawCode ->
        val requestResult =
          runCatching { PairingRequest.fromUserInput(rawCode) }

        if (requestResult.isFailure) {
          PairingResult.InvalidCode(
            "Kod mora imati tačno 6 slova ili cifara.",
          )
        } else {
          val result =
            withContext(Dispatchers.IO) {
              gateway.pair(requestResult.getOrThrow())
            }

          if (result is PairingResult.Success) {
            val token = result.controlToken

            if (token.isNullOrBlank()) {
              PairingResult.Error(
                "Backend nije vratio control token.",
              )
            } else {
              settingsStore.savePairing(
                device = result.device,
                controlToken = token,
              )
              pairedDevice = result.device
              result
            }
          } else {
            result
          }
        }
      },
    )
    return
  }

  val device = pairedDevice!!

  fun sendCommand(command: RemoteCommand) {
    if (commandInProgress) return

    val controlToken = settingsStore.controlToken()
    if (controlToken.isNullOrBlank()) {
      commandError = "Nedostaje control token. Potrebno je ponovno uparivanje."
      return
    }

    scope.launch {
      commandInProgress = true
      commandError = null
      commandDeliveryStatus = null

      when (
        val result =
          withContext(Dispatchers.IO) {
            commandGateway.send(
              deviceId = device.deviceId,
              controlToken = controlToken,
              command = command,
            )
          }
      ) {
        is CommandResult.Success -> {
          pairedDevice = result.device
          settingsStore.savePairing(
            device = result.device,
            controlToken = controlToken,
          )
          lastCommand = command
          commandDeliveryStatus = result.deliveryStatus

          for (attempt in 1..12) {
            if (commandDeliveryStatus == "APPLIED" || commandDeliveryStatus == "FAILED") {
              break
            }

            delay(500)

            when (
              val statusResult =
                withContext(Dispatchers.IO) {
                  commandGateway.status(
                    deviceId = device.deviceId,
                    controlToken = controlToken,
                    commandId = result.commandId,
                  )
                }
            ) {
              is CommandDeliveryResult.Success -> {
                commandDeliveryStatus = statusResult.status
              }

              is CommandDeliveryResult.Error -> {
                commandError = statusResult.message
                break
              }
            }
          }
        }

        is CommandResult.Error -> {
          commandError = result.message
        }
      }

      commandInProgress = false
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
          text = "Upareni uređaj",
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
          text = "✓ Real backend pairing",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
          text = "✓ FCM command transport ready",
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
          onClick = { sendCommand(RemoteCommand.lock()) },
          enabled = !commandInProgress,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("LOCK NOW")
        }

        OutlinedButton(
          onClick = { sendCommand(RemoteCommand.unlock()) },
          enabled = !commandInProgress,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("UNLOCK")
        }

        Text(
          text = "Dodatno vreme",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
          onClick = { showBonusTimePicker = true },
          enabled = !commandInProgress,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("ADD TIME")
        }
      }
    }

    if (commandInProgress) {
      Text(
        text = "Šaljem komandu…",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    commandError?.let { message ->
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
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

        commandDeliveryStatus?.let { status ->
          Text(
            text = deliveryStatusLabel(status),
            style = MaterialTheme.typography.bodyMedium,
            color =
              if (status == "FAILED") {
                MaterialTheme.colorScheme.error
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))
  }

  if (showBonusTimePicker) {
    BonusTimeWheelDialog(
      initialMinutes = selectedBonusMinutes,
      onDismiss = { showBonusTimePicker = false },
      onConfirm = { minutes ->
        selectedBonusMinutes = minutes
        showBonusTimePicker = false
        sendCommand(RemoteCommand.bonusTime(minutes))
      },
    )
  }
}

@Composable
private fun BonusTimeWheelDialog(
  initialMinutes: Int,
  onDismiss: () -> Unit,
  onConfirm: (Int) -> Unit,
) {
  var selectedMinutes by remember(initialMinutes) {
    mutableStateOf(initialMinutes.coerceIn(1, 60))
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Dodatno vreme",
        fontWeight = FontWeight.SemiBold,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Izaberi trajanje od 1 do 60 minuta.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AndroidView(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(190.dp),
          factory = { context ->
            NumberPicker(context).apply {
              minValue = 1
              maxValue = 60
              value = selectedMinutes
              wrapSelectorWheel = false
              descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
              setFormatter { value -> value.toString().padStart(2, '0') }
              setOnValueChangedListener { _, _, newValue ->
                selectedMinutes = newValue
              }
            }
          },
          update = { picker ->
            if (picker.value != selectedMinutes) {
              picker.value = selectedMinutes
            }
          },
        )

        Text(
          text =
            selectedMinutes.toString() +
              if (selectedMinutes == 1) " minut" else " minuta",
          modifier = Modifier.fillMaxWidth(),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(selectedMinutes) },
      ) {
        Text("DODAJ")
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) {
        Text("OTKAŽI")
      }
    },
  )
}

@Composable
private fun PairDeviceScreen(
  onPair: suspend (String) -> PairingResult,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()

  var pairingCode by remember { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var pairingInProgress by remember { mutableStateOf(false) }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 32.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = "PhoneGuard Parent",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "Upari dečji telefon",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.SemiBold,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Na Child telefonu otvori PhoneGuard i unesi njegov šestoznakovni pairing kod.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedTextField(
      value = pairingCode,
      onValueChange = { value ->
        pairingCode =
          value
            .uppercase()
            .filter(Char::isLetterOrDigit)
            .take(6)
        errorMessage = null
      },
      label = { Text("Pairing code") },
      singleLine = true,
      enabled = !pairingInProgress,
      keyboardOptions =
        KeyboardOptions(
          capitalization = KeyboardCapitalization.Characters,
          keyboardType = KeyboardType.Ascii,
        ),
      modifier = Modifier.fillMaxWidth(),
    )

    errorMessage?.let {
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = it,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
      onClick = {
        scope.launch {
          pairingInProgress = true
          errorMessage = null

          when (val result = onPair(pairingCode)) {
            is PairingResult.Success -> Unit
            is PairingResult.InvalidCode ->
              errorMessage = result.message
            is PairingResult.Error ->
              errorMessage = result.message
          }

          pairingInProgress = false
        }
      },
      enabled = pairingCode.length == 6 && !pairingInProgress,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        if (pairingInProgress) {
          "PAIRING…"
        } else {
          "PAIR DEVICE"
        },
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Kod se proverava na PhoneGuard backendu i može se iskoristiti samo dok je aktivan.",
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
      "● Dodatno vreme: " +
        device.temporaryAccessMinutesRemaining +
        " min"
    DeviceAccessState.OFFLINE -> "○ Uređaj je offline"
  }

private fun deliveryStatusLabel(status: String): String =
  when (status) {
    "PENDING" -> "○ Priprema slanja"
    "SENT" -> "◌ Poslato — čeka potvrdu Child uređaja"
    "APPLIED" -> "✓ Primljeno i izvršeno na Child uređaju"
    "FAILED" -> "✕ Komanda nije izvršena"
    else -> status
  }

private fun commandLabel(command: RemoteCommand?): String =
  when (command?.type) {
    null -> "Nema poslatih komandi"
    RemoteCommandType.LOCK -> "LOCK NOW"
    RemoteCommandType.UNLOCK -> "UNLOCK"
    RemoteCommandType.BONUS_TIME ->
      "+" + command.bonusMinutes + " min"
  }

@Preview(showBackground = true)
@Composable
private fun ParentDashboardPreview() {
  MaterialTheme {
    ParentDashboardScreen()
  }
}
