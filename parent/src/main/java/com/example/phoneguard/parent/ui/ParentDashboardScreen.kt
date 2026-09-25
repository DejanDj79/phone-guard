package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.example.phoneguard.core.AllowedAppsSnapshot
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState
import com.example.phoneguard.core.PairingRequest
import com.example.phoneguard.core.PairingResult
import com.example.phoneguard.core.RemoteCommand
import com.example.phoneguard.core.RemoteWeeklySchedule
import com.example.phoneguard.parent.data.AllowedAppsFetchResult
import com.example.phoneguard.parent.data.AllowedAppsSaveResult
import com.example.phoneguard.parent.data.AppUsageDay
import com.example.phoneguard.parent.data.AppUsageEntry
import com.example.phoneguard.parent.data.AppUsageResult
import com.example.phoneguard.parent.data.CommandDeliveryResult
import com.example.phoneguard.parent.data.CommandResult
import com.example.phoneguard.parent.data.DeviceStatusResult
import com.example.phoneguard.parent.data.HttpAllowedAppsGateway
import com.example.phoneguard.parent.data.HttpAppUsageGateway
import com.example.phoneguard.parent.data.HttpDeviceManagementGateway
import com.example.phoneguard.parent.data.HttpDeviceStatusGateway
import com.example.phoneguard.parent.data.HttpCommandGateway
import com.example.phoneguard.parent.data.HttpPairingGateway
import com.example.phoneguard.parent.data.HttpProtectionHistoryGateway
import com.example.phoneguard.parent.data.PairingGateway
import com.example.phoneguard.parent.data.ProtectionHistoryClearResult
import com.example.phoneguard.parent.data.ProtectionHistoryEvent
import com.example.phoneguard.parent.data.ProtectionHistoryResult
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
  val appUsageGateway = remember { HttpAppUsageGateway() }
  val deviceManagementGateway = remember { HttpDeviceManagementGateway() }
  val timeRequestGateway = remember { HttpTimeRequestGateway() }
  val dailyLimitGateway = remember { HttpDailyLimitGateway() }
  val protectionHistoryGateway = remember { HttpProtectionHistoryGateway() }
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
  var connectionTestInProgress by remember { mutableStateOf(false) }
  var connectionTestMessage by remember { mutableStateOf<String?>(null) }
  var connectionTestError by remember { mutableStateOf<String?>(null) }
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
  var protectionHistory by remember {
    mutableStateOf<List<ProtectionHistoryEvent>>(emptyList())
  }
  var protectionHistoryLoading by remember { mutableStateOf(false) }
  var protectionHistoryError by remember { mutableStateOf<String?>(null) }
  var showAllProtectionHistory by remember { mutableStateOf(false) }
  var protectionHistoryClearing by remember { mutableStateOf(false) }
  var showClearProtectionHistoryConfirm by remember { mutableStateOf(false) }
  var appUsageDays by remember {
    mutableStateOf<List<AppUsageDay>>(emptyList())
  }
  var appUsageLoading by remember { mutableStateOf(false) }
  var appUsageError by remember { mutableStateOf<String?>(null) }
  var appUsageView by remember { mutableStateOf(APP_USAGE_VIEW_TODAY) }

  fun removeInvalidPairing(
    deviceId: String,
    displayName: String,
  ) {
    settingsStore.removePairing(deviceId)
    pairedDevices = settingsStore.loadPairedDevices()
    pairedDevice = settingsStore.loadPairedDevice()
    commandProgressMessage = null
    pendingCommandFeedback = null
    connectionTestInProgress = false
    connectionTestMessage = null
    connectionTestError = null
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
    protectionHistory = emptyList()
    protectionHistoryLoading = false
    protectionHistoryError = null
    showAllProtectionHistory = false
    protectionHistoryClearing = false
    showClearProtectionHistoryConfirm = false
    appUsageDays = emptyList()
    appUsageLoading = false
    appUsageError = null
    appUsageView = APP_USAGE_VIEW_TODAY
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
          connectionTestInProgress ||
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
          connectionTestInProgress = false
          connectionTestMessage = null
          connectionTestError = null
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
          protectionHistory = emptyList()
          protectionHistoryLoading = false
          protectionHistoryError = null
          showAllProtectionHistory = false
          protectionHistoryClearing = false
          showClearProtectionHistoryConfirm = false
          appUsageDays = emptyList()
          appUsageLoading = false
          appUsageError = null
          appUsageView = APP_USAGE_VIEW_TODAY
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
                      "Allowed apps saved and will be applied when PhoneGuard checks in again."
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
                      "Schedule saved and will be applied when PhoneGuard checks in again."
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

        delay(5_000)
      }
    }
  }

  LaunchedEffect(device.deviceId, selectedTab, "app-usage-poll") {
    if (selectedTab != 0) return@LaunchedEffect

    val controlToken = settingsStore.controlToken(device.deviceId)
    if (controlToken.isNullOrBlank()) {
      appUsageError =
        "Control token is missing. Re-pairing is required."
      return@LaunchedEffect
    }

    while (true) {
      if (appUsageDays.isEmpty()) {
        appUsageLoading = true
      }

      when (
        val result =
          withContext(Dispatchers.IO) {
            appUsageGateway.fetch(
              deviceId = device.deviceId,
              controlToken = controlToken,
            )
          }
      ) {
        is AppUsageResult.Success -> {
          appUsageDays = result.days
          appUsageError = null
        }

        is AppUsageResult.Error -> {
          if (result.pairingInvalid) {
            removeInvalidPairing(
              deviceId = device.deviceId,
              displayName = device.displayName,
            )
            return@LaunchedEffect
          } else {
            appUsageError = result.message
          }
        }
      }

      appUsageLoading = false
      delay(30_000)
    }
  }

  LaunchedEffect(device.deviceId, selectedTab, "protection-history-poll") {
    if (selectedTab != 3) return@LaunchedEffect

    val controlToken = settingsStore.controlToken(device.deviceId)
    if (controlToken.isNullOrBlank()) {
      protectionHistoryError =
        "Control token is missing. Re-pairing is required."
      return@LaunchedEffect
    }

    while (true) {
      if (protectionHistory.isEmpty()) {
        protectionHistoryLoading = true
      }

      when (
        val result =
          withContext(Dispatchers.IO) {
            protectionHistoryGateway.fetch(
              deviceId = device.deviceId,
              controlToken = controlToken,
            )
          }
      ) {
        is ProtectionHistoryResult.Success -> {
          protectionHistory = result.events
          protectionHistoryError = null
        }

        is ProtectionHistoryResult.Error -> {
          if (result.pairingInvalid) {
            removeInvalidPairing(
              deviceId = device.deviceId,
              displayName = device.displayName,
            )
            return@LaunchedEffect
          } else {
            protectionHistoryError = result.message
          }
        }
      }

      protectionHistoryLoading = false
      delay(5_000)
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

  fun clearProtectionHistory() {
    if (protectionHistoryClearing) return

    val controlToken = settingsStore.controlToken(device.deviceId)
    if (controlToken.isNullOrBlank()) {
      protectionHistoryError =
        "Control token is missing. Re-pairing is required."
      return
    }

    scope.launch {
      protectionHistoryClearing = true
      protectionHistoryError = null

      when (
        val result =
          withContext(Dispatchers.IO) {
            protectionHistoryGateway.clear(
              deviceId = device.deviceId,
              controlToken = controlToken,
            )
          }
      ) {
        ProtectionHistoryClearResult.Success -> {
          protectionHistory = emptyList()
          showAllProtectionHistory = false
          showClearProtectionHistoryConfirm = false
        }

        is ProtectionHistoryClearResult.Error -> {
          if (result.pairingInvalid) {
            removeInvalidPairing(
              deviceId = device.deviceId,
              displayName = device.displayName,
            )
          } else {
            protectionHistoryError = result.message
          }
        }
      }

      protectionHistoryClearing = false
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
              "Daily limit saved and will apply when PhoneGuard checks in again."
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

  fun testConnection() {
    if (connectionTestInProgress || commandInProgress) return

    val controlToken = settingsStore.controlToken(device.deviceId)
    if (controlToken.isNullOrBlank()) {
      connectionTestError =
        "Control token is missing. Re-pairing is required."
      return
    }

    scope.launch {
      connectionTestInProgress = true
      connectionTestMessage = "Testing Parent → backend → Child connection…"
      connectionTestError = null

      when (
        val result =
          withContext(Dispatchers.IO) {
            commandGateway.send(
              deviceId = device.deviceId,
              controlToken = controlToken,
              command = RemoteCommand.syncDailyLimit(),
            )
          }
      ) {
        is CommandResult.Success -> {
          var deliveryStatus = result.deliveryStatus

          for (attempt in 1..20) {
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

                if (deliveryStatus == "APPLIED") {
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
                connectionTestError = statusResult.message
                break
              }
            }
          }

          if (connectionTestError == null) {
            when (deliveryStatus) {
              "APPLIED" -> {
                connectionTestMessage =
                  "✓ Connection OK — Child received and confirmed the test."
              }

              "FAILED" -> {
                connectionTestMessage = null
                connectionTestError =
                  "Child received the test, but could not complete it."
              }

              else -> {
                connectionTestMessage =
                  "Backend accepted the test, but Child has not confirmed it yet."
              }
            }
          }
        }

        is CommandResult.Error -> {
          connectionTestMessage = null
          connectionTestError = result.message
        }
      }

      connectionTestInProgress = false
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
            devicePresenceSummary(device.lastSeenAt) +
              " · " +
              deviceStateLabel(device).removePrefix("● ").removePrefix("○ "),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
          onClick = { showDevices = true },
          enabled =
            !commandInProgress &&
              !connectionTestInProgress &&
              !refreshInProgress,
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
      ParentDeviceTab(
        device = device,
        pairedDeviceCount = pairedDevices.size,
        refreshInProgress = refreshInProgress,
        commandInProgress = commandInProgress,
        deviceRenaming = deviceRenaming,
        deviceUnpairing = deviceUnpairing,
        protectionHistory = protectionHistory,
        protectionHistoryLoading = protectionHistoryLoading,
        protectionHistoryError = protectionHistoryError,
        showAllProtectionHistory = showAllProtectionHistory,
        protectionHistoryClearing = protectionHistoryClearing,
        onShowDevices = { showDevices = true },
        onRefreshStatus = {
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
        onManageDevice = {
          deviceManagementError = null
          showDeviceManagement = true
        },
        onToggleProtectionHistory = {
          showAllProtectionHistory = !showAllProtectionHistory
        },
        onRequestClearProtectionHistory = {
          showClearProtectionHistoryConfirm = true
          protectionHistoryError = null
        },
      )
    }

    if (selectedTab == 0) {
      ParentOverviewTab(
        device = device,
        pendingTimeRequest = pendingTimeRequest,
        timeRequestResponding = timeRequestResponding,
        timeRequestNotice = timeRequestNotice,
        timeRequestError = timeRequestError,
        commandInProgress = commandInProgress,
        commandProgressMessage = commandProgressMessage,
        commandNotice = commandNotice,
        connectionTestInProgress = connectionTestInProgress,
        connectionTestMessage = connectionTestMessage,
        connectionTestError = connectionTestError,
        appUsageDays = appUsageDays,
        appUsageLoading = appUsageLoading,
        appUsageError = appUsageError,
        appUsageView = appUsageView,
        onRespondToTimeRequest = { request, approve ->
          respondToTimeRequest(request, approve)
        },
        onLock = { sendCommand(RemoteCommand.lock()) },
        onUnlock = { sendCommand(RemoteCommand.unlock()) },
        onAddBonusTime = { showBonusTimePicker = true },
        onTestConnection = { testConnection() },
        onAppUsageViewChange = { appUsageView = it },
      )
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

  if (showClearProtectionHistoryConfirm) {
    AlertDialog(
      onDismissRequest = {
        if (!protectionHistoryClearing) {
          showClearProtectionHistoryConfirm = false
        }
      },
      title = {
        Text(
          text = "Clear protection history?",
          fontWeight = FontWeight.SemiBold,
        )
      },
      text = {
        Text(
          text =
            "This will permanently delete the protection history for " +
              device.displayName +
              ". Protection settings and alerts will not be changed.",
        )
      },
      confirmButton = {
        Button(
          onClick = { clearProtectionHistory() },
          enabled = !protectionHistoryClearing,
        ) {
          Text(
            if (protectionHistoryClearing) {
              "CLEARING…"
            } else {
              "CLEAR"
            },
          )
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = { showClearProtectionHistoryConfirm = false },
          enabled = !protectionHistoryClearing,
        ) {
          Text("CANCEL")
        }
      },
    )
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
private fun ParentDashboardPreview() {
  MaterialTheme {
    ParentDashboardScreen()
  }
}
