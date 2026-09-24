package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState
import com.example.phoneguard.core.PairingRequest
import com.example.phoneguard.core.PairingResult
import com.example.phoneguard.core.RemoteCommand
import com.example.phoneguard.core.RemoteWeeklySchedule
import com.example.phoneguard.parent.data.CommandDeliveryResult
import com.example.phoneguard.parent.data.CommandResult
import com.example.phoneguard.parent.data.DeviceStatusResult
import com.example.phoneguard.parent.data.HttpDeviceStatusGateway
import com.example.phoneguard.parent.data.HttpCommandGateway
import com.example.phoneguard.parent.data.HttpPairingGateway
import com.example.phoneguard.parent.data.PairingGateway
import com.example.phoneguard.parent.data.ParentSettingsStore
import com.example.phoneguard.parent.data.HttpScheduleGateway
import com.example.phoneguard.parent.data.ScheduleFetchResult
import com.example.phoneguard.parent.data.ScheduleSaveResult
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
  val deviceStatusGateway = remember { HttpDeviceStatusGateway() }
  val scheduleGateway = remember { HttpScheduleGateway() }
  val scope = rememberCoroutineScope()

  var pairedDevice by remember {
    mutableStateOf(settingsStore.loadPairedDevice())
  }
  var commandInProgress by remember { mutableStateOf(false) }
  var commandProgressMessage by remember { mutableStateOf<String?>(null) }
  var commandNotice by remember { mutableStateOf<String?>(null) }
  var pendingCommandFeedback by remember { mutableStateOf<RemoteCommand?>(null) }
  var commandError by remember { mutableStateOf<String?>(null) }
  var refreshInProgress by remember { mutableStateOf(false) }
  var showBonusTimePicker by remember { mutableStateOf(false) }
  var selectedBonusMinutes by remember { mutableStateOf(15) }
  var scheduleEditorSchedule by remember {
    mutableStateOf<RemoteWeeklySchedule?>(null)
  }
  var scheduleLoading by remember { mutableStateOf(false) }
  var scheduleSaving by remember { mutableStateOf(false) }
  var scheduleError by remember { mutableStateOf<String?>(null) }
  var scheduleNotice by remember { mutableStateOf<String?>(null) }

  if (pairedDevice == null) {
    PairDeviceScreen(
      modifier = modifier,
      onPair = { rawCode ->
        val requestResult =
          runCatching { PairingRequest.fromUserInput(rawCode) }

        if (requestResult.isFailure) {
          PairingResult.InvalidCode(
            "Code must contain exactly 6 letters or digits.",
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
                "Backend did not return a control token.",
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

  scheduleEditorSchedule?.let { schedule ->
    ParentScheduleEditorScreen(
      schedule = schedule,
      saving = scheduleSaving,
      saveError = scheduleError,
      onSave = { updatedSchedule ->
        if (!scheduleSaving) {
          val controlToken = settingsStore.controlToken()
          if (controlToken.isNullOrBlank()) {
            scheduleError =
              "Control token is missing. Re-pairing is required."
          } else {
            scope.launch {
              scheduleSaving = true
              scheduleError = null

              when (
                val result =
                  withContext(Dispatchers.IO) {
                    scheduleGateway.save(
                      deviceId = device.deviceId,
                      controlToken = controlToken,
                      schedule = updatedSchedule,
                    )
                  }
              ) {
                is ScheduleSaveResult.Success -> {
                  scheduleEditorSchedule = null
                  scheduleNotice =
                    if (device.isOnline) {
                      "Schedule saved and sent to the Child device."
                    } else {
                      "Schedule saved and will be applied when the Child reconnects."
                    }
                }

                is ScheduleSaveResult.Error -> {
                  scheduleError = result.message
                }
              }

              scheduleSaving = false
            }
          }
        }
      },
      onCancel = {
        if (!scheduleSaving) {
          scheduleEditorSchedule = null
          scheduleError = null
        }
      },
      modifier = modifier,
    )
    return
  }

  LaunchedEffect(device.deviceId) {
    val controlToken = settingsStore.controlToken()
    if (controlToken.isNullOrBlank()) {
      commandError = "Control token is missing. Re-pairing is required."
    } else {
      while (true) {
        when (
          val statusResult =
            withContext(Dispatchers.IO) {
              deviceStatusGateway.fetch(
                deviceId = device.deviceId,
                controlToken = controlToken,
              )
            }
        ) {
          is DeviceStatusResult.Success -> {
            pairedDevice = statusResult.device
            settingsStore.savePairing(
              device = statusResult.device,
              controlToken = controlToken,
            )

            pendingCommandFeedback?.let { pendingCommand ->
              if (commandMatchesDeviceState(pendingCommand, statusResult.device)) {
                commandNotice = commandAppliedLabel(pendingCommand)
                pendingCommandFeedback = null
              }
            }
          }

          is DeviceStatusResult.Error -> {
            commandError = statusResult.message
          }
        }

        delay(15_000)
      }
    }
  }

  fun sendCommand(command: RemoteCommand) {
    if (commandInProgress) return

    val controlToken = settingsStore.controlToken()
    if (controlToken.isNullOrBlank()) {
      commandError = "Control token is missing. Re-pairing is required."
      return
    }

    scope.launch {
      commandInProgress = true
      commandProgressMessage = commandSendingLabel(command, device.isOnline)
      commandNotice = null
      pendingCommandFeedback = null
      commandError = null

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
          var deliveryStatus = result.deliveryStatus

          for (attempt in 1..12) {
            if (deliveryStatus == "APPLIED" || deliveryStatus == "FAILED") {
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
                deliveryStatus = statusResult.status

                if (statusResult.status == "APPLIED") {
                  val updatedDevice =
                    statusResult.device.copy(
                      lastSeenAt =
                        pairedDevice?.lastSeenAt ?: device.lastSeenAt,
                    )
                  pairedDevice = updatedDevice
                  settingsStore.savePairing(
                    device = updatedDevice,
                    controlToken = controlToken,
                  )
                }
              }

              is CommandDeliveryResult.Error -> {
                commandError = statusResult.message
                break
              }
            }
          }

          if (commandError == null) {
            when (deliveryStatus) {
              "APPLIED" -> {
                commandNotice = commandAppliedLabel(command)
                pendingCommandFeedback = null
              }

              "FAILED" -> {
                commandNotice = null
                pendingCommandFeedback = null
                commandError = "The command could not be applied. Please try again."
              }

              else -> {
                commandNotice =
                  commandQueuedLabel(
                    command = command,
                    deviceOnline = pairedDevice?.isOnline ?: device.isOnline,
                  )
                pendingCommandFeedback = command
              }
            }
          }
        }

        is CommandResult.Error -> {
          commandError = result.message
        }
      }

      commandProgressMessage = null
      commandInProgress = false
    }
  }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 32.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    Text(
      text = "PhoneGuard Parent",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )

    Text(
      text = "Parental control",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Paired device",
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
          text =
            if (device.isOnline) {
              "● Online"
            } else {
              "○ Device is offline"
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
          text = formatLastSeen(device.lastSeenAt),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
          onClick = {
            if (!refreshInProgress) {
              val controlToken = settingsStore.controlToken()
              if (controlToken.isNullOrBlank()) {
                commandError =
                  "Control token is missing. Re-pairing is required."
              } else {
                scope.launch {
                  refreshInProgress = true
                  commandError = null

                  when (
                    val statusResult =
                      withContext(Dispatchers.IO) {
                        deviceStatusGateway.fetch(
                          deviceId = device.deviceId,
                          controlToken = controlToken,
                        )
                      }
                  ) {
                    is DeviceStatusResult.Success -> {
                      pairedDevice = statusResult.device
                      settingsStore.savePairing(
                        device = statusResult.device,
                        controlToken = controlToken,
                      )

                      pendingCommandFeedback?.let { pendingCommand ->
                        if (
                          commandMatchesDeviceState(
                            pendingCommand,
                            statusResult.device,
                          )
                        ) {
                          commandNotice = commandAppliedLabel(pendingCommand)
                          pendingCommandFeedback = null
                        }
                      }
                    }

                    is DeviceStatusResult.Error -> {
                      commandError = statusResult.message
                    }
                  }

                  refreshInProgress = false
                }
              }
            }
          },
          enabled = !refreshInProgress,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (refreshInProgress) "REFRESHING…" else "REFRESH STATUS")
        }

        val protection = device.protectionStatus
        val protectionComplete = protection.criticalProtectionComplete

        Text(
          text =
            when {
              device.isOnline && protectionComplete == true ->
                "✓ Device protection is active"
              device.isOnline && protectionComplete == false ->
                "⚠ Device protection is incomplete"
              device.isOnline ->
                "Checking device protection…"
              protectionComplete == false ->
                "⚠ Last known state: protection is incomplete"
              protectionComplete == true ->
                "Last known state: protection was active"
              else ->
                "Protection status is unknown"
            },
          style = MaterialTheme.typography.bodyMedium,
          color =
            if (protectionComplete == false) {
              MaterialTheme.colorScheme.error
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        if (protection.accessibilityEnabled == false) {
          Text(
            text =
              if (device.isOnline) {
                "Accessibility protection is disabled."
              } else {
                "Last known state: Accessibility protection was disabled."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        if (protection.preciseTimingEnabled == false) {
          Text(
            text =
              if (device.isOnline) {
                "Exact lock timing is not allowed."
              } else {
                "Last known state: exact lock timing was not allowed."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        if (protection.batteryUnrestricted == false) {
          Text(
            text = "Android may restrict PhoneGuard background activity.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Quick actions",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )

        val isLocked = device.state == DeviceAccessState.LOCKED
        val isUnlocked =
          device.state == DeviceAccessState.ALLOWED ||
            device.state == DeviceAccessState.TEMPORARILY_ALLOWED

        Button(
          onClick = { sendCommand(RemoteCommand.lock()) },
          enabled = !commandInProgress && !isLocked,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (isLocked) "LOCKED" else "LOCK NOW")
        }

        OutlinedButton(
          onClick = { sendCommand(RemoteCommand.unlock()) },
          enabled = !commandInProgress && !isUnlocked,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (isUnlocked) "UNLOCKED" else "UNLOCK")
        }

        Text(
          text = "Bonus time",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (
          device.state == DeviceAccessState.TEMPORARILY_ALLOWED &&
          device.temporaryAccessMinutesRemaining != null
        ) {
          Text(
            text =
              "Remaining: " +
                device.temporaryAccessMinutesRemaining +
                " min",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }

        OutlinedButton(
          onClick = { showBonusTimePicker = true },
          enabled = !commandInProgress,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("ADD TIME")
        }

        if (!device.isOnline && commandProgressMessage == null && commandNotice == null) {
          Text(
            text = "Device is offline. Sent commands will be applied when it reconnects.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        commandProgressMessage?.let { message ->
          Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        commandNotice?.let { message ->
          Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Lock schedule",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )

        Text(
          text = "Set the days and times when the Child phone will lock automatically.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
          onClick = {
            if (!scheduleLoading) {
              val controlToken = settingsStore.controlToken()
              if (controlToken.isNullOrBlank()) {
                commandError =
                  "Control token is missing. Re-pairing is required."
              } else {
                scope.launch {
                  scheduleLoading = true
                  scheduleError = null
                  commandError = null
                  scheduleNotice = null

                  when (
                    val result =
                      withContext(Dispatchers.IO) {
                        scheduleGateway.fetch(
                          deviceId = device.deviceId,
                          controlToken = controlToken,
                        )
                      }
                  ) {
                    is ScheduleFetchResult.Success -> {
                      scheduleEditorSchedule = result.schedule
                    }

                    is ScheduleFetchResult.Error -> {
                      commandError = result.message
                    }
                  }

                  scheduleLoading = false
                }
              }
            }
          },
          enabled = !scheduleLoading && !commandInProgress,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (scheduleLoading) "LOADING…" else "EDIT SCHEDULE")
        }
      }
    }

    scheduleNotice?.let { message ->
      Text(
        text = message,
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
        text = "Bonus time",
        fontWeight = FontWeight.SemiBold,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Choose a duration from 1 to 60 minutes.",
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
              if (selectedMinutes == 1) " minute" else " minutes",
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
        Text("ADD")
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) {
        Text("CANCEL")
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
      text = "Pair a Child phone",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.SemiBold,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Open PhoneGuard on the Child phone and enter its 6-character pairing code.",
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
      text = "The code is verified by the PhoneGuard backend and can only be used while it is active.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun formatLastSeen(value: String?): String {
  if (value.isNullOrBlank()) return "Last seen: unknown"

  val normalized =
    value.replace(
      Regex("(\\.\\d{3})\\d+"),
      "$1",
    )
  val patterns =
    listOf(
      "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
      "yyyy-MM-dd'T'HH:mm:ssXXX",
    )

  var parsed: Date? = null
  for (pattern in patterns) {
    parsed =
      runCatching {
        SimpleDateFormat(pattern, Locale.US).parse(normalized)
      }.getOrNull()
    if (parsed != null) break
  }

  val date = parsed ?: return "Last seen: unknown"
  val formatter =
    SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())

  return "Last seen: " + formatter.format(date)
}

private fun commandMatchesDeviceState(
  command: RemoteCommand,
  device: ChildDevice,
): Boolean =
  when (command.type) {
    com.example.phoneguard.core.RemoteCommandType.LOCK ->
      device.state == DeviceAccessState.LOCKED
    com.example.phoneguard.core.RemoteCommandType.UNLOCK ->
      device.state == DeviceAccessState.ALLOWED
    com.example.phoneguard.core.RemoteCommandType.BONUS_TIME ->
      device.state == DeviceAccessState.TEMPORARILY_ALLOWED
    com.example.phoneguard.core.RemoteCommandType.SYNC_SCHEDULE ->
      false
  }

private fun commandSendingLabel(
  command: RemoteCommand,
  deviceOnline: Boolean,
): String =
  if (!deviceOnline) {
    "Device is offline — the command will wait for reconnection."
  } else {
    when (command.type) {
      com.example.phoneguard.core.RemoteCommandType.LOCK ->
        "Sending lock command…"
      com.example.phoneguard.core.RemoteCommandType.UNLOCK ->
        "Sending unlock command…"
      com.example.phoneguard.core.RemoteCommandType.BONUS_TIME ->
        "Adding " + command.bonusMinutes + " min…"
      com.example.phoneguard.core.RemoteCommandType.SYNC_SCHEDULE ->
        "Syncing schedule…"
    }
  }

private fun commandAppliedLabel(command: RemoteCommand): String =
  when (command.type) {
    com.example.phoneguard.core.RemoteCommandType.LOCK ->
      "Phone locked ✓"
    com.example.phoneguard.core.RemoteCommandType.UNLOCK ->
      "Phone unlocked ✓"
    com.example.phoneguard.core.RemoteCommandType.BONUS_TIME ->
      "Added " + command.bonusMinutes + " min ✓"
    com.example.phoneguard.core.RemoteCommandType.SYNC_SCHEDULE ->
      "Schedule applied ✓"
  }

private fun commandQueuedLabel(
  command: RemoteCommand,
  deviceOnline: Boolean,
): String =
  if (!deviceOnline) {
    when (command.type) {
      com.example.phoneguard.core.RemoteCommandType.LOCK ->
        "Lock command queued and will be applied when the Child reconnects."
      com.example.phoneguard.core.RemoteCommandType.UNLOCK ->
        "Unlock command queued and will be applied when the Child reconnects."
      com.example.phoneguard.core.RemoteCommandType.BONUS_TIME ->
        "Bonus time queued and will be applied when the Child reconnects."
      com.example.phoneguard.core.RemoteCommandType.SYNC_SCHEDULE ->
        "The schedule will be applied when the Child reconnects."
    }
  } else {
    when (command.type) {
      com.example.phoneguard.core.RemoteCommandType.LOCK ->
        "Lock command sent. Waiting for the Child device to confirm."
      com.example.phoneguard.core.RemoteCommandType.UNLOCK ->
        "Unlock command sent. Waiting for the Child device to confirm."
      com.example.phoneguard.core.RemoteCommandType.BONUS_TIME ->
        "Bonus time sent. Waiting for the Child device to confirm."
      com.example.phoneguard.core.RemoteCommandType.SYNC_SCHEDULE ->
        "Schedule sent. Waiting for the Child device to confirm."
    }
  }

private fun deviceStateLabel(device: ChildDevice): String =
  when (device.state) {
    DeviceAccessState.ALLOWED -> "● Phone is available"
    DeviceAccessState.LOCKED -> "● Phone is locked"
    DeviceAccessState.TEMPORARILY_ALLOWED ->
      if ((device.temporaryAccessMinutesRemaining ?: 0) > 0) {
        "● Bonus time: " +
          device.temporaryAccessMinutesRemaining +
          " min"
      } else {
        "Bonus time expired — updating status"
      }
    DeviceAccessState.OFFLINE -> "○ Device state is unknown"
  }

@Preview(showBackground = true)
@Composable
private fun ParentDashboardPreview() {
  MaterialTheme {
    ParentDashboardScreen()
  }
}
