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
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
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
import com.example.phoneguard.core.AllowedAppsSnapshot
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState
import com.example.phoneguard.core.PairingRequest
import com.example.phoneguard.core.PairingResult
import com.example.phoneguard.core.RemoteCommand
import com.example.phoneguard.core.RemoteWeeklySchedule
import com.example.phoneguard.parent.data.AllowedAppsFetchResult
import com.example.phoneguard.parent.data.AllowedAppsSaveResult
import com.example.phoneguard.parent.data.CommandDeliveryResult
import com.example.phoneguard.parent.data.CommandResult
import com.example.phoneguard.parent.data.DeviceStatusResult
import com.example.phoneguard.parent.data.HttpAllowedAppsGateway
import com.example.phoneguard.parent.data.HttpDeviceManagementGateway
import com.example.phoneguard.parent.data.HttpDeviceStatusGateway
import com.example.phoneguard.parent.data.HttpCommandGateway
import com.example.phoneguard.parent.data.HttpPairingGateway
import com.example.phoneguard.parent.data.PairingGateway
import com.example.phoneguard.parent.data.ParentSettingsStore
import com.example.phoneguard.parent.data.HttpScheduleGateway
import com.example.phoneguard.parent.data.HttpTimeRequestGateway
import com.example.phoneguard.parent.data.HttpDailyLimitGateway
import com.example.phoneguard.parent.data.DailyLimitSaveResult
import com.example.phoneguard.parent.data.RenameDeviceResult
import com.example.phoneguard.parent.data.ScheduleFetchResult
import com.example.phoneguard.parent.data.ScheduleSaveResult
import com.example.phoneguard.parent.data.PendingTimeRequest
import com.example.phoneguard.parent.data.TimeRequestFetchResult
import com.example.phoneguard.parent.data.TimeRequestResponseResult
import com.example.phoneguard.parent.data.UnpairDeviceResult
import com.example.phoneguard.parent.push.ParentPushRegistrar
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
  val allowedAppsGateway = remember { HttpAllowedAppsGateway() }
  val deviceManagementGateway = remember { HttpDeviceManagementGateway() }
  val timeRequestGateway = remember { HttpTimeRequestGateway() }
  val dailyLimitGateway = remember { HttpDailyLimitGateway() }
  val scope = rememberCoroutineScope()

  var pairedDevice by remember {
    mutableStateOf(settingsStore.loadPairedDevice())
  }
  var pairedDevices by remember {
    mutableStateOf(settingsStore.loadPairedDevices())
  }
  var showPairDevice by remember {
    mutableStateOf(pairedDevice == null)
  }
  var showDevices by remember { mutableStateOf(false) }
  var selectedTab by remember { mutableStateOf(0) }
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
  var allowedAppsEditorSnapshot by remember {
    mutableStateOf<AllowedAppsSnapshot?>(null)
  }
  var allowedAppsLoading by remember { mutableStateOf(false) }
  var allowedAppsSaving by remember { mutableStateOf(false) }
  var allowedAppsError by remember { mutableStateOf<String?>(null) }
  var allowedAppsNotice by remember { mutableStateOf<String?>(null) }
  var showDeviceManagement by remember { mutableStateOf(false) }
  var deviceRenaming by remember { mutableStateOf(false) }
  var deviceUnpairing by remember { mutableStateOf(false) }
  var deviceManagementError by remember { mutableStateOf<String?>(null) }
  var pendingTimeRequest by remember {
    mutableStateOf<PendingTimeRequest?>(null)
  }
  var timeRequestResponding by remember { mutableStateOf(false) }
  var timeRequestError by remember { mutableStateOf<String?>(null) }
  var timeRequestNotice by remember { mutableStateOf<String?>(null) }
  var showDailyLimitPicker by remember { mutableStateOf(false) }
  var selectedDailyLimitMinutes by remember {
    mutableStateOf(pairedDevice?.dailyScreenTime?.limitMinutes ?: 120)
  }
  var dailyLimitSaving by remember { mutableStateOf(false) }
  var dailyLimitError by remember { mutableStateOf<String?>(null) }
  var dailyLimitNotice by remember { mutableStateOf<String?>(null) }

  fun removeInvalidPairing(
    deviceId: String,
    displayName: String,
  ) {
    settingsStore.removePairing(deviceId)
    pairedDevices = settingsStore.loadPairedDevices()
    pairedDevice = settingsStore.loadPairedDevice()
    commandProgressMessage = null
    pendingCommandFeedback = null
    scheduleEditorSchedule = null
    scheduleError = null
    scheduleNotice = null
    allowedAppsEditorSnapshot = null
    allowedAppsError = null
    allowedAppsNotice = null
    showBonusTimePicker = false
    showDeviceManagement = false
    deviceManagementError = null
    pendingTimeRequest = null
    timeRequestError = null
    timeRequestNotice = null
    showDailyLimitPicker = false
    dailyLimitError = null
    dailyLimitNotice = null
    showDevices = false
    selectedTab = 0

    if (pairedDevice == null) {
      commandNotice = null
      commandError = null
      showPairDevice = true
    } else {
      commandNotice =
        displayName + " was removed because its Parent pairing is no longer valid."
      commandError = null
      showPairDevice = false
    }
  }

  if (showPairDevice || pairedDevice == null) {
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
              settingsStore.selectDevice(result.device.deviceId)
              pairedDevices = settingsStore.loadPairedDevices()
              pairedDevice = result.device
              ParentPushRegistrar(context.applicationContext)
                .registerCurrentToken()
              showPairDevice = false
              result
            }
          } else {
            result
          }
        }
      },
      onCancel =
        if (pairedDevices.isNotEmpty()) {
          { showPairDevice = false }
        } else {
          null
        },
    )
    return
  }

  val device = pairedDevice!!

  if (showDevices) {
    DevicesScreen(
      devices = pairedDevices,
      selectedDeviceId = device.deviceId,
      busy =
        commandInProgress ||
          refreshInProgress ||
          scheduleLoading ||
          scheduleSaving ||
          allowedAppsLoading ||
          allowedAppsSaving ||
          dailyLimitSaving ||
          deviceRenaming ||
          deviceUnpairing,
      onSelectDevice = { selected ->
        if (settingsStore.selectDevice(selected.deviceId)) {
          pairedDevice = settingsStore.loadPairedDevice()
          commandNotice = null
          commandProgressMessage = null
          commandError = null
          pendingCommandFeedback = null
          scheduleEditorSchedule = null
          scheduleError = null
          scheduleNotice = null
          allowedAppsEditorSnapshot = null
          allowedAppsError = null
          allowedAppsNotice = null
          showBonusTimePicker = false
          showDeviceManagement = false
          deviceManagementError = null
          pendingTimeRequest = null
          timeRequestError = null
          timeRequestNotice = null
          showDailyLimitPicker = false
          selectedDailyLimitMinutes =
            pairedDevice?.dailyScreenTime?.limitMinutes ?: 120
          dailyLimitError = null
          dailyLimitNotice = null
          showDevices = false
          selectedTab = 0
        }
      },
      onAddDevice = {
        showDevices = false
        showPairDevice = true
      },
      onBack = { showDevices = false },
      modifier = modifier,
    )
    return
  }

  if (showDeviceManagement) {
    DeviceManagementScreen(
      currentName = device.displayName,
      renaming = deviceRenaming,
      unpairing = deviceUnpairing,
      errorMessage = deviceManagementError,
      onRename = { displayName ->
        if (!deviceRenaming && !deviceUnpairing) {
          val controlToken = settingsStore.controlToken(device.deviceId)
          if (controlToken.isNullOrBlank()) {
            deviceManagementError =
              "Control token is missing. Re-pairing is required."
          } else {
            scope.launch {
              deviceRenaming = true
              deviceManagementError = null

              when (
                val result =
                  withContext(Dispatchers.IO) {
                    deviceManagementGateway.rename(
                      deviceId = device.deviceId,
                      controlToken = controlToken,
                      displayName = displayName,
                    )
                  }
              ) {
                is RenameDeviceResult.Success -> {
                  val updatedDevice =
                    device.copy(displayName = result.displayName)
                  pairedDevice = updatedDevice
                  settingsStore.savePairing(
                    device = updatedDevice,
                    controlToken = controlToken,
                  )
                  pairedDevices = settingsStore.loadPairedDevices()
                  showDeviceManagement = false
                  commandNotice = "Device renamed to " + result.displayName + "."
                }

                is RenameDeviceResult.Error -> {
                  deviceManagementError = result.message
                }
              }

              deviceRenaming = false
            }
          }
        }
      },
      onUnpair = {
        if (!deviceRenaming && !deviceUnpairing) {
          val controlToken = settingsStore.controlToken(device.deviceId)
          if (controlToken.isNullOrBlank()) {
            deviceManagementError =
              "Control token is missing. Re-pairing is required."
          } else {
            scope.launch {
              deviceUnpairing = true
              deviceManagementError = null

              when (
                val result =
                  withContext(Dispatchers.IO) {
                    deviceManagementGateway.unpair(
                      deviceId = device.deviceId,
                      controlToken = controlToken,
                    )
                  }
              ) {
                UnpairDeviceResult.Success -> {
                  settingsStore.removePairing(device.deviceId)
                  pairedDevices = settingsStore.loadPairedDevices()
                  pairedDevice = settingsStore.loadPairedDevice()
                  showPairDevice = pairedDevice == null
                  showDeviceManagement = false
                  commandNotice = null
                  pendingCommandFeedback = null
                }

                is UnpairDeviceResult.Error -> {
                  deviceManagementError = result.message
                }
              }

              deviceUnpairing = false
            }
          }
        }
      },
      onBack = {
        if (!deviceRenaming && !deviceUnpairing) {
          showDeviceManagement = false
          deviceManagementError = null
        }
      },
      modifier = modifier,
    )
    return
  }

  allowedAppsEditorSnapshot?.let { snapshot ->
    AllowedAppsEditorScreen(
      snapshot = snapshot,
      saving = allowedAppsSaving,
      saveError = allowedAppsError,
      onSave = { allowedPackages ->
        if (!allowedAppsSaving) {
          val controlToken = settingsStore.controlToken(device.deviceId)
          if (controlToken.isNullOrBlank()) {
            allowedAppsError =
              "Control token is missing. Re-pairing is required."
          } else {
            scope.launch {
              allowedAppsSaving = true
              allowedAppsError = null

              when (
                val result =
                  withContext(Dispatchers.IO) {
                    allowedAppsGateway.save(
                      deviceId = device.deviceId,
                      controlToken = controlToken,
                      allowedPackages = allowedPackages,
                      expectedVersion = snapshot.version,
                    )
                  }
              ) {
                is AllowedAppsSaveResult.Success -> {
                  allowedAppsEditorSnapshot = null
                  allowedAppsNotice =
                    if (device.isOnline) {
                      "Allowed apps saved and sent to the Child device."
                    } else {
                      "Allowed apps saved and will be applied when the Child reconnects."
                    }
                }

                is AllowedAppsSaveResult.Error -> {
                  allowedAppsError = result.message
                }
              }

              allowedAppsSaving = false
            }
          }
        }
      },
      onCancel = {
        if (!allowedAppsSaving) {
          allowedAppsEditorSnapshot = null
          allowedAppsError = null
        }
      },
      modifier = modifier,
    )
    return
  }

  scheduleEditorSchedule?.let { schedule ->
    ParentScheduleEditorScreen(
      schedule = schedule,
      saving = scheduleSaving,
      saveError = scheduleError,
      onSave = { updatedSchedule ->
        if (!scheduleSaving) {
          val controlToken = settingsStore.controlToken(device.deviceId)
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
    val controlToken = settingsStore.controlToken(device.deviceId)
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
            pairedDevices = settingsStore.loadPairedDevices()

            pendingCommandFeedback?.let { pendingCommand ->
              if (commandMatchesDeviceState(pendingCommand, statusResult.device)) {
                commandNotice = commandAppliedLabel(pendingCommand)
                pendingCommandFeedback = null
              }
            }
          }

          is DeviceStatusResult.Error -> {
            if (statusResult.pairingInvalid) {
              removeInvalidPairing(
                deviceId = device.deviceId,
                displayName = device.displayName,
              )
              return@LaunchedEffect
            } else {
              commandError = statusResult.message
            }
          }
        }

        delay(15_000)
      }
    }
  }

  LaunchedEffect(device.deviceId, "time-request-poll") {
    val controlToken = settingsStore.controlToken(device.deviceId)
    if (!controlToken.isNullOrBlank()) {
      while (true) {
        when (
          val result =
            withContext(Dispatchers.IO) {
              timeRequestGateway.fetch(
                deviceId = device.deviceId,
                controlToken = controlToken,
              )
            }
        ) {
          is TimeRequestFetchResult.Success -> {
            pendingTimeRequest = result.request
            if (result.request == null) {
              timeRequestError = null
            }
          }

          is TimeRequestFetchResult.Error -> {
            if (result.pairingInvalid) {
              removeInvalidPairing(
                deviceId = device.deviceId,
                displayName = device.displayName,
              )
              return@LaunchedEffect
            } else {
              timeRequestError = result.message
            }
          }
        }

        delay(10_000)
      }
    }
  }

  fun respondToTimeRequest(
    request: PendingTimeRequest,
    approve: Boolean,
  ) {
    if (timeRequestResponding) return

    val controlToken = settingsStore.controlToken(device.deviceId)
    if (controlToken.isNullOrBlank()) {
      timeRequestError = "Control token is missing. Re-pairing is required."
      return
    }

    scope.launch {
      timeRequestResponding = true
      timeRequestError = null
      timeRequestNotice = null

      when (
        val result =
          withContext(Dispatchers.IO) {
            timeRequestGateway.respond(
              deviceId = device.deviceId,
              controlToken = controlToken,
              requestId = request.requestId,
              approve = approve,
            )
          }
      ) {
        is TimeRequestResponseResult.Success -> {
          pendingTimeRequest = null
          timeRequestNotice =
            if (result.approved) {
              "Approved " + result.requestedMinutes + " minutes."
            } else {
              "Time request denied."
            }
        }

        is TimeRequestResponseResult.Error -> {
          if (result.pairingInvalid) {
            removeInvalidPairing(
              deviceId = device.deviceId,
              displayName = device.displayName,
            )
          } else {
            timeRequestError = result.message
          }
        }
      }

      timeRequestResponding = false
    }
  }

  fun saveDailyLimit(minutes: Int?) {
    if (dailyLimitSaving) return

    val controlToken = settingsStore.controlToken(device.deviceId)
    if (controlToken.isNullOrBlank()) {
      dailyLimitError = "Control token is missing. Re-pairing is required."
      return
    }

    scope.launch {
      dailyLimitSaving = true
      dailyLimitError = null
      dailyLimitNotice = null

      when (
        val result =
          withContext(Dispatchers.IO) {
            dailyLimitGateway.save(
              deviceId = device.deviceId,
              controlToken = controlToken,
              minutes = minutes,
            )
          }
      ) {
        is DailyLimitSaveResult.Success -> {
          val currentDevice = pairedDevice ?: device
          val usedSeconds = currentDevice.dailyScreenTime.usedSeconds
          val remainingMinutes =
            result.minutes?.let { limit ->
              maxOf(
                0,
                kotlin.math.ceil(
                  (limit * 60 - usedSeconds).coerceAtLeast(0) / 60.0,
                ).toInt(),
              )
            }
          val updatedDevice =
            currentDevice.copy(
              dailyScreenTime =
                currentDevice.dailyScreenTime.copy(
                  limitMinutes = result.minutes,
                  remainingMinutes = remainingMinutes,
                  limitReached =
                    result.minutes != null &&
                      usedSeconds >= result.minutes * 60,
                ),
            )

          pairedDevice = updatedDevice
          settingsStore.savePairing(
            device = updatedDevice,
            controlToken = controlToken,
          )
          pairedDevices = settingsStore.loadPairedDevices()
          if (result.minutes != null) {
            selectedDailyLimitMinutes = result.minutes
          }
          showDailyLimitPicker = false
          dailyLimitNotice =
            if (result.minutes == null) {
              "Daily screen time limit disabled."
            } else if (device.isOnline) {
              "Daily limit set to " + formatDurationMinutes(result.minutes) + "."
            } else {
              "Daily limit saved and will apply when the Child reconnects."
            }
        }

        is DailyLimitSaveResult.Error -> {
          if (result.pairingInvalid) {
            removeInvalidPairing(
              deviceId = device.deviceId,
              displayName = device.displayName,
            )
          } else {
            dailyLimitError = result.message
          }
        }
      }

      dailyLimitSaving = false
    }
  }

  fun sendCommand(command: RemoteCommand) {
    if (commandInProgress) return

    val controlToken = settingsStore.controlToken(device.deviceId)
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
                  pairedDevices = settingsStore.loadPairedDevices()
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
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "Managing",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
          text = device.displayName,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
        )

        Text(
          text =
            (if (device.isOnline) "● Online" else "○ Offline") +
              " · " +
              deviceStateLabel(device).removePrefix("● ").removePrefix("○ "),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
          onClick = { showDevices = true },
          enabled = !commandInProgress && !refreshInProgress,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("CHANGE DEVICE")
        }
      }
    }

    ScrollableTabRow(selectedTabIndex = selectedTab) {
      listOf("Overview", "Schedule", "Apps", "Device", "Settings")
        .forEachIndexed { index, label ->
        Tab(
          selected = selectedTab == index,
          onClick = { selectedTab = index },
          text = { Text(label) },
        )
      }
    }

    if (selectedTab == 3) {
      ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "Device details",
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
            onClick = { showDevices = true },
            enabled =
              !refreshInProgress &&
                !commandInProgress &&
                !deviceRenaming &&
                !deviceUnpairing,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("DEVICES (" + pairedDevices.size + ")")
          }
  
          OutlinedButton(
            onClick = {
              if (!refreshInProgress) {
                val controlToken = settingsStore.controlToken(device.deviceId)
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
                        pairedDevices = settingsStore.loadPairedDevices()
  
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
                        if (statusResult.pairingInvalid) {
                          removeInvalidPairing(
                            deviceId = device.deviceId,
                            displayName = device.displayName,
                          )
                        } else {
                          commandError = statusResult.message
                        }
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
  
          OutlinedButton(
            onClick = {
              deviceManagementError = null
              showDeviceManagement = true
            },
            enabled =
              !refreshInProgress &&
                !commandInProgress &&
                !deviceRenaming &&
                !deviceUnpairing,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("MANAGE DEVICE")
          }
  
          val protection = device.protectionStatus
          val protectionValues =
            listOf(
              protection.accessibilityEnabled,
              protection.preciseTimingEnabled,
              protection.batteryUnrestricted,
            )
          val protectionKnown = protectionValues.all { it != null }
          val protectionComplete =
            protectionKnown && protectionValues.all { it == true }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = "Protection status",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )

          Text(
            text =
              when {
                !device.isOnline ->
                  "○ Offline · showing last known protection state"
                protectionComplete ->
                  "✓ All protection checks are active"
                protectionKnown ->
                  "⚠ Protection needs attention"
                else ->
                  "Checking protection status…"
              },
            style = MaterialTheme.typography.bodyMedium,
            color =
              if (protectionKnown && !protectionComplete) {
                MaterialTheme.colorScheme.error
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
          )

          ProtectionStatusLine(
            label = "Accessibility",
            enabled = protection.accessibilityEnabled,
            enabledText = "Enabled",
            disabledText = "Disabled",
          )

          ProtectionStatusLine(
            label = "Precise timing",
            enabled = protection.preciseTimingEnabled,
            enabledText = "Allowed",
            disabledText = "Not allowed",
          )

          ProtectionStatusLine(
            label = "Background protection",
            enabled = protection.batteryUnrestricted,
            enabledText = "Unrestricted",
            disabledText = "Battery restricted",
          )

          Text(
            text =
              if (device.isOnline) {
                "Heartbeat: online now"
              } else {
                formatLastSeen(device.lastSeenAt)
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    if (selectedTab == 0) {
      pendingTimeRequest?.let { request ->
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "More time requested",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )

            Text(
              text =
                request.displayName +
                  " is asking for " +
                  request.requestedMinutes +
                  " more minutes.",
              style = MaterialTheme.typography.bodyLarge,
            )

            Button(
              onClick = { respondToTimeRequest(request, approve = true) },
              enabled = !timeRequestResponding,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                if (timeRequestResponding) {
                  "RESPONDING…"
                } else {
                  "APPROVE " + request.requestedMinutes + " MIN"
                },
              )
            }

            OutlinedButton(
              onClick = { respondToTimeRequest(request, approve = false) },
              enabled = !timeRequestResponding,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("DENY")
            }
          }
        }
      }

      timeRequestNotice?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      timeRequestError?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
        )
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
    }

    if (selectedTab == 2) {
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
            onClick = {
              if (!allowedAppsLoading) {
                val controlToken = settingsStore.controlToken(device.deviceId)
                if (controlToken.isNullOrBlank()) {
                  commandError =
                    "Control token is missing. Re-pairing is required."
                } else {
                  scope.launch {
                    allowedAppsLoading = true
                    allowedAppsError = null
                    allowedAppsNotice = null
                    commandError = null
  
                    when (
                      val result =
                        withContext(Dispatchers.IO) {
                          allowedAppsGateway.fetch(
                            deviceId = device.deviceId,
                            controlToken = controlToken,
                          )
                        }
                    ) {
                      is AllowedAppsFetchResult.Success -> {
                        allowedAppsEditorSnapshot = result.snapshot
                      }
  
                      is AllowedAppsFetchResult.Error -> {
                        commandError = result.message
                      }
                    }
  
                    allowedAppsLoading = false
                  }
                }
              }
            },
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

    if (selectedTab == 1) {
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
                val controlToken = settingsStore.controlToken(device.deviceId)
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

      val dailyScreenTime = device.dailyScreenTime
      val dailyLimitMinutes = dailyScreenTime.limitMinutes

      ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Daily screen time",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )

          if (dailyLimitMinutes == null) {
            Text(
              text = "No daily limit is set.",
              style = MaterialTheme.typography.bodyLarge,
            )
          } else {
            Text(
              text =
                "Daily limit: " +
                  formatDurationMinutes(dailyLimitMinutes),
              style = MaterialTheme.typography.bodyLarge,
            )
          }

          Text(
            text =
              "Used today: " +
                formatUsageSeconds(dailyScreenTime.usedSeconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          dailyScreenTime.remainingMinutes?.let { remaining ->
            Text(
              text =
                if (dailyScreenTime.limitReached) {
                  "Daily limit reached."
                } else {
                  "Remaining: " + formatDurationMinutes(remaining)
                },
              style = MaterialTheme.typography.bodyMedium,
              color =
                if (dailyScreenTime.limitReached) {
                  MaterialTheme.colorScheme.error
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
          }

          Text(
            text =
              "Only normal unlocked use counts. Allowed apps used while the phone is locked do not consume this limit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Button(
            onClick = {
              selectedDailyLimitMinutes =
                dailyLimitMinutes ?: 120
              dailyLimitError = null
              dailyLimitNotice = null
              showDailyLimitPicker = true
            },
            enabled = !dailyLimitSaving,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              if (dailyLimitSaving) {
                "SAVING…"
              } else if (dailyLimitMinutes == null) {
                "SET DAILY LIMIT"
              } else {
                "CHANGE DAILY LIMIT"
              },
            )
          }

          if (dailyLimitMinutes != null) {
            OutlinedButton(
              onClick = { saveDailyLimit(null) },
              enabled = !dailyLimitSaving,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("DISABLE DAILY LIMIT")
            }
          }

          dailyLimitNotice?.let { message ->
            Text(
              text = message,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          dailyLimitError?.let { message ->
            Text(
              text = message,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }

    if (selectedTab == 1) {
      scheduleNotice?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (selectedTab == 4) {
      ParentSettingsScreen()
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

  if (showDailyLimitPicker) {
    DailyLimitWheelDialog(
      initialMinutes = selectedDailyLimitMinutes,
      onDismiss = {
        if (!dailyLimitSaving) {
          showDailyLimitPicker = false
        }
      },
      onConfirm = { minutes ->
        selectedDailyLimitMinutes = minutes
        saveDailyLimit(minutes)
      },
    )
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
private fun DailyLimitWheelDialog(
  initialMinutes: Int,
  onDismiss: () -> Unit,
  onConfirm: (Int) -> Unit,
) {
  var selectedMinutes by remember(initialMinutes) {
    mutableStateOf(initialMinutes.coerceIn(1, 1440))
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Daily screen time limit",
        fontWeight = FontWeight.SemiBold,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Choose the total normal screen time allowed each day.",
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
              maxValue = 1440
              value = selectedMinutes
              wrapSelectorWheel = false
              descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
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
          text = formatDurationMinutes(selectedMinutes),
          modifier = Modifier.fillMaxWidth(),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
        )
      }
    },
    confirmButton = {
      Button(onClick = { onConfirm(selectedMinutes) }) {
        Text("SAVE")
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
  onCancel: (() -> Unit)? = null,
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

    onCancel?.let {
      OutlinedButton(
        onClick = it,
        enabled = !pairingInProgress,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("CANCEL")
      }

      Spacer(modifier = Modifier.height(16.dp))
    }

    Text(
      text = "The code is verified by the PhoneGuard backend and can only be used while it is active.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ProtectionStatusLine(
  label: String,
  enabled: Boolean?,
  enabledText: String,
  disabledText: String,
) {
  val statusText =
    when (enabled) {
      true -> "✓ " + enabledText
      false -> "⚠ " + disabledText
      null -> "… Unknown"
    }

  Text(
    text = label + ": " + statusText,
    style = MaterialTheme.typography.bodyMedium,
    color =
      if (enabled == false) {
        MaterialTheme.colorScheme.error
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
  )
}

private fun formatDurationMinutes(minutes: Int): String {
  val safeMinutes = minutes.coerceAtLeast(0)
  val hours = safeMinutes / 60
  val remainingMinutes = safeMinutes % 60

  return when {
    hours == 0 -> safeMinutes.toString() + " min"
    remainingMinutes == 0 -> hours.toString() + " h"
    else -> hours.toString() + " h " + remainingMinutes + " min"
  }
}

private fun formatUsageSeconds(seconds: Int): String {
  val safeSeconds = seconds.coerceAtLeast(0)
  val totalMinutes = safeSeconds / 60
  val remainingSeconds = safeSeconds % 60

  return if (totalMinutes == 0) {
    remainingSeconds.toString() + " sec"
  } else {
    formatDurationMinutes(totalMinutes)
  }
}

private fun formatLastSeen(value: String?): String {
  if (value.isNullOrBlank()) return "Last seen: unknown"

  val normalized =
    value.replace(
      Regex("(\\.\\d{3})\\d+"),
      "\$1",
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
    com.example.phoneguard.core.RemoteCommandType.SYNC_SCHEDULE,
    com.example.phoneguard.core.RemoteCommandType.SYNC_ALLOWED_APPS,
    com.example.phoneguard.core.RemoteCommandType.SYNC_DAILY_LIMIT ->
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
      com.example.phoneguard.core.RemoteCommandType.SYNC_ALLOWED_APPS ->
        "Syncing allowed apps…"
      com.example.phoneguard.core.RemoteCommandType.SYNC_DAILY_LIMIT ->
        "Syncing daily limit…"
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
    com.example.phoneguard.core.RemoteCommandType.SYNC_ALLOWED_APPS ->
      "Allowed apps updated ✓"
    com.example.phoneguard.core.RemoteCommandType.SYNC_DAILY_LIMIT ->
      "Daily limit updated ✓"
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
      com.example.phoneguard.core.RemoteCommandType.SYNC_ALLOWED_APPS ->
        "Allowed apps will be applied when the Child reconnects."
      com.example.phoneguard.core.RemoteCommandType.SYNC_DAILY_LIMIT ->
        "Daily limit will be applied when the Child reconnects."
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
      com.example.phoneguard.core.RemoteCommandType.SYNC_ALLOWED_APPS ->
        "Allowed apps sent. Waiting for the Child device to confirm."
      com.example.phoneguard.core.RemoteCommandType.SYNC_DAILY_LIMIT ->
        "Daily limit sent. Waiting for the Child device to confirm."
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
