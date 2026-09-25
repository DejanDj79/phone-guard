package com.example.phoneguard.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.phoneguard.core.PairingRequest
import com.example.phoneguard.core.PairingResult
import com.example.phoneguard.core.RemoteCommand
import com.example.phoneguard.parent.data.AllowedAppsFetchResult
import com.example.phoneguard.parent.data.AllowedAppsSaveResult
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
import com.example.phoneguard.parent.data.ProtectionHistoryResult
import com.example.phoneguard.parent.auth.ParentSupabase
import com.example.phoneguard.parent.data.ParentAccountScopeStore
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
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.withContext

@Composable
fun ParentDashboardScreen(
  modifier: Modifier = Modifier,
  pairingGateway: PairingGateway? = null,
) {
  val context = LocalContext.current
  val accountScopeStore =
    remember(context) {
      ParentAccountScopeStore(context.applicationContext)
    }
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

  val uiState =
    remember(settingsStore) {
      ParentDashboardUiState(
        initialPairedDevice = settingsStore.loadPairedDevice(),
        initialPairedDevices = settingsStore.loadPairedDevices(),
      )
    }

  fun removeInvalidPairing(
    deviceId: String,
    displayName: String,
  ) {
    settingsStore.removePairing(deviceId)
    uiState.pairedDevices = settingsStore.loadPairedDevices()
    uiState.pairedDevice = settingsStore.loadPairedDevice()
    uiState.commandProgressMessage = null
    uiState.pendingCommandFeedback = null
    uiState.connectionTestInProgress = false
    uiState.connectionTestMessage = null
    uiState.connectionTestError = null
    uiState.scheduleEditorSchedule = null
    uiState.scheduleError = null
    uiState.scheduleNotice = null
    uiState.allowedAppsEditorSnapshot = null
    uiState.allowedAppsError = null
    uiState.allowedAppsNotice = null
    uiState.showBonusTimePicker = false
    uiState.showDeviceManagement = false
    uiState.deviceManagementError = null
    uiState.pendingTimeRequest = null
    uiState.timeRequestError = null
    uiState.timeRequestNotice = null
    uiState.showDailyLimitPicker = false
    uiState.dailyLimitError = null
    uiState.dailyLimitNotice = null
    uiState.protectionHistory = emptyList()
    uiState.protectionHistoryLoading = false
    uiState.protectionHistoryError = null
    uiState.showAllProtectionHistory = false
    uiState.protectionHistoryClearing = false
    uiState.showClearProtectionHistoryConfirm = false
    uiState.appUsageDays = emptyList()
    uiState.appUsageLoading = false
    uiState.appUsageError = null
    uiState.appUsageView = APP_USAGE_VIEW_TODAY
    uiState.showDevices = false
    uiState.selectedTab = 0

    if (uiState.pairedDevice == null) {
      uiState.commandNotice = null
      uiState.commandError = null
      uiState.showPairDevice = true
    } else {
      uiState.commandNotice =
        displayName + " was removed because its Parent pairing is no longer valid."
      uiState.commandError = null
      uiState.showPairDevice = false
    }
  }

  if (uiState.showPairDevice || uiState.pairedDevice == null) {
    PairDeviceScreen(
      parentEmail =
        ParentSupabase.client.auth.currentUserOrNull()?.email,
      modifier = modifier,
      onSwitchAccount = {
        runCatching {
          ParentSupabase.client.auth.signOut()
          accountScopeStore.clearActiveAccount()
        }.fold(
          onSuccess = { null },
          onFailure = { error ->
            error.message ?: "Could not switch Parent account. Please try again."
          },
        )
      },
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
              settingsStore.markBackendOwnershipConfirmed(
                result.device.deviceId,
              )
              settingsStore.selectDevice(result.device.deviceId)
              uiState.pairedDevices = settingsStore.loadPairedDevices()
              uiState.pairedDevice = result.device
              ParentPushRegistrar(context.applicationContext)
                .registerCurrentToken()
              uiState.showPairDevice = false
              result
            }
          } else {
            result
          }
        }
      },
      onCancel =
        if (uiState.pairedDevices.isNotEmpty()) {
          { uiState.showPairDevice = false }
        } else {
          null
        },
    )
    return
  }

  val device = uiState.pairedDevice!!

  if (uiState.showDevices) {
    DevicesScreen(
      devices = uiState.pairedDevices,
      selectedDeviceId = device.deviceId,
      busy =
        uiState.commandInProgress ||
          uiState.connectionTestInProgress ||
          uiState.refreshInProgress ||
          uiState.scheduleLoading ||
          uiState.scheduleSaving ||
          uiState.allowedAppsLoading ||
          uiState.allowedAppsSaving ||
          uiState.dailyLimitSaving ||
          uiState.deviceRenaming ||
          uiState.deviceUnpairing,
      onSelectDevice = { selected ->
        if (settingsStore.selectDevice(selected.deviceId)) {
          uiState.pairedDevice = settingsStore.loadPairedDevice()
          uiState.commandNotice = null
          uiState.commandProgressMessage = null
          uiState.commandError = null
          uiState.pendingCommandFeedback = null
          uiState.connectionTestInProgress = false
          uiState.connectionTestMessage = null
          uiState.connectionTestError = null
          uiState.scheduleEditorSchedule = null
          uiState.scheduleError = null
          uiState.scheduleNotice = null
          uiState.allowedAppsEditorSnapshot = null
          uiState.allowedAppsError = null
          uiState.allowedAppsNotice = null
          uiState.showBonusTimePicker = false
          uiState.showDeviceManagement = false
          uiState.deviceManagementError = null
          uiState.pendingTimeRequest = null
          uiState.timeRequestError = null
          uiState.timeRequestNotice = null
          uiState.showDailyLimitPicker = false
          uiState.selectedDailyLimitMinutes =
            uiState.pairedDevice?.dailyScreenTime?.limitMinutes ?: 120
          uiState.dailyLimitError = null
          uiState.dailyLimitNotice = null
          uiState.protectionHistory = emptyList()
          uiState.protectionHistoryLoading = false
          uiState.protectionHistoryError = null
          uiState.showAllProtectionHistory = false
          uiState.protectionHistoryClearing = false
          uiState.showClearProtectionHistoryConfirm = false
          uiState.appUsageDays = emptyList()
          uiState.appUsageLoading = false
          uiState.appUsageError = null
          uiState.appUsageView = APP_USAGE_VIEW_TODAY
          uiState.showDevices = false
          uiState.selectedTab = 0
        }
      },
      onAddDevice = {
        uiState.showDevices = false
        uiState.showPairDevice = true
      },
      onBack = { uiState.showDevices = false },
      modifier = modifier,
    )
    return
  }

  if (uiState.showDeviceManagement) {
    DeviceManagementScreen(
      currentName = device.displayName,
      renaming = uiState.deviceRenaming,
      unpairing = uiState.deviceUnpairing,
      errorMessage = uiState.deviceManagementError,
      onRename = { displayName ->
        if (!uiState.deviceRenaming && !uiState.deviceUnpairing) {
          val controlToken = settingsStore.controlToken(device.deviceId)
          if (controlToken.isNullOrBlank()) {
            uiState.deviceManagementError =
              "Control token is missing. Re-pairing is required."
          } else {
            scope.launch {
              uiState.deviceRenaming = true
              uiState.deviceManagementError = null

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
                  uiState.pairedDevice = updatedDevice
                  settingsStore.savePairing(
                    device = updatedDevice,
                    controlToken = controlToken,
                  )
                  uiState.pairedDevices = settingsStore.loadPairedDevices()
                  uiState.showDeviceManagement = false
                  uiState.commandNotice = "Device renamed to " + result.displayName + "."
                }

                is RenameDeviceResult.Error -> {
                  uiState.deviceManagementError = result.message
                }
              }

              uiState.deviceRenaming = false
            }
          }
        }
      },
      onUnpair = {
        if (!uiState.deviceRenaming && !uiState.deviceUnpairing) {
          val controlToken = settingsStore.controlToken(device.deviceId)
          if (controlToken.isNullOrBlank()) {
            uiState.deviceManagementError =
              "Control token is missing. Re-pairing is required."
          } else {
            scope.launch {
              uiState.deviceUnpairing = true
              uiState.deviceManagementError = null

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
                  uiState.pairedDevices = settingsStore.loadPairedDevices()
                  uiState.pairedDevice = settingsStore.loadPairedDevice()
                  uiState.showPairDevice = uiState.pairedDevice == null
                  uiState.showDeviceManagement = false
                  uiState.commandNotice = null
                  uiState.pendingCommandFeedback = null
                }

                is UnpairDeviceResult.Error -> {
                  uiState.deviceManagementError = result.message
                }
              }

              uiState.deviceUnpairing = false
            }
          }
        }
      },
      onBack = {
        if (!uiState.deviceRenaming && !uiState.deviceUnpairing) {
          uiState.showDeviceManagement = false
          uiState.deviceManagementError = null
        }
      },
      modifier = modifier,
    )
    return
  }

  uiState.allowedAppsEditorSnapshot?.let { snapshot ->
    AllowedAppsEditorScreen(
      snapshot = snapshot,
      saving = uiState.allowedAppsSaving,
      saveError = uiState.allowedAppsError,
      onSave = { allowedPackages ->
        if (!uiState.allowedAppsSaving) {
          val controlToken = settingsStore.controlToken(device.deviceId)
          if (controlToken.isNullOrBlank()) {
            uiState.allowedAppsError =
              "Control token is missing. Re-pairing is required."
          } else {
            scope.launch {
              uiState.allowedAppsSaving = true
              uiState.allowedAppsError = null

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
                  uiState.allowedAppsEditorSnapshot = null
                  uiState.allowedAppsNotice =
                    if (device.isOnline) {
                      "Allowed apps saved and sent to the Child device."
                    } else {
                      "Allowed apps saved and will be applied when PhoneGuard checks in again."
                    }
                }

                is AllowedAppsSaveResult.Error -> {
                  uiState.allowedAppsError = result.message
                }
              }

              uiState.allowedAppsSaving = false
            }
          }
        }
      },
      onCancel = {
        if (!uiState.allowedAppsSaving) {
          uiState.allowedAppsEditorSnapshot = null
          uiState.allowedAppsError = null
        }
      },
      modifier = modifier,
    )
    return
  }

  uiState.scheduleEditorSchedule?.let { schedule ->
    ParentScheduleEditorScreen(
      schedule = schedule,
      saving = uiState.scheduleSaving,
      saveError = uiState.scheduleError,
      onSave = { updatedSchedule ->
        if (!uiState.scheduleSaving) {
          val controlToken = settingsStore.controlToken(device.deviceId)
          if (controlToken.isNullOrBlank()) {
            uiState.scheduleError =
              "Control token is missing. Re-pairing is required."
          } else {
            scope.launch {
              uiState.scheduleSaving = true
              uiState.scheduleError = null

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
                  uiState.scheduleEditorSchedule = null
                  uiState.scheduleNotice =
                    if (device.isOnline) {
                      "Schedule saved and sent to the Child device."
                    } else {
                      "Schedule saved and will be applied when PhoneGuard checks in again."
                    }
                }

                is ScheduleSaveResult.Error -> {
                  uiState.scheduleError = result.message
                }
              }

              uiState.scheduleSaving = false
            }
          }
        }
      },
      onCancel = {
        if (!uiState.scheduleSaving) {
          uiState.scheduleEditorSchedule = null
          uiState.scheduleError = null
        }
      },
      modifier = modifier,
    )
    return
  }

  LaunchedEffect(device.deviceId) {
    val controlToken = settingsStore.controlToken(device.deviceId)
    if (controlToken.isNullOrBlank()) {
      uiState.commandError = "Control token is missing. Re-pairing is required."
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
            uiState.pairedDevice = statusResult.device
            settingsStore.savePairing(
              device = statusResult.device,
              controlToken = controlToken,
            )
            uiState.pairedDevices = settingsStore.loadPairedDevices()

            uiState.pendingCommandFeedback?.let { pendingCommand ->
              if (commandMatchesDeviceState(pendingCommand, statusResult.device)) {
                uiState.commandNotice = commandAppliedLabel(pendingCommand)
                uiState.pendingCommandFeedback = null
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
              uiState.commandError = statusResult.message
            }
          }
        }

        delay(5_000)
      }
    }
  }

  LaunchedEffect(device.deviceId, uiState.selectedTab, "app-usage-poll") {
    if (uiState.selectedTab != 0) return@LaunchedEffect

    val controlToken = settingsStore.controlToken(device.deviceId)
    if (controlToken.isNullOrBlank()) {
      uiState.appUsageError =
        "Control token is missing. Re-pairing is required."
      return@LaunchedEffect
    }

    while (true) {
      if (uiState.appUsageDays.isEmpty()) {
        uiState.appUsageLoading = true
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
          uiState.appUsageDays = result.days
          uiState.appUsageError = null
        }

        is AppUsageResult.Error -> {
          if (result.pairingInvalid) {
            removeInvalidPairing(
              deviceId = device.deviceId,
              displayName = device.displayName,
            )
            return@LaunchedEffect
          } else {
            uiState.appUsageError = result.message
          }
        }
      }

      uiState.appUsageLoading = false
      delay(30_000)
    }
  }

  LaunchedEffect(device.deviceId, uiState.selectedTab, "protection-history-poll") {
    if (uiState.selectedTab != 3) return@LaunchedEffect

    val controlToken = settingsStore.controlToken(device.deviceId)
    if (controlToken.isNullOrBlank()) {
      uiState.protectionHistoryError =
        "Control token is missing. Re-pairing is required."
      return@LaunchedEffect
    }

    while (true) {
      if (uiState.protectionHistory.isEmpty()) {
        uiState.protectionHistoryLoading = true
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
          uiState.protectionHistory = result.events
          uiState.protectionHistoryError = null
        }

        is ProtectionHistoryResult.Error -> {
          if (result.pairingInvalid) {
            removeInvalidPairing(
              deviceId = device.deviceId,
              displayName = device.displayName,
            )
            return@LaunchedEffect
          } else {
            uiState.protectionHistoryError = result.message
          }
        }
      }

      uiState.protectionHistoryLoading = false
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
            uiState.pendingTimeRequest = result.request
            if (result.request == null) {
              uiState.timeRequestError = null
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
              uiState.timeRequestError = result.message
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
    if (uiState.timeRequestResponding) return

    val controlToken = settingsStore.controlToken(device.deviceId)
    if (controlToken.isNullOrBlank()) {
      uiState.timeRequestError = "Control token is missing. Re-pairing is required."
      return
    }

    scope.launch {
      uiState.timeRequestResponding = true
      uiState.timeRequestError = null
      uiState.timeRequestNotice = null

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
          uiState.pendingTimeRequest = null
          uiState.timeRequestNotice =
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
            uiState.timeRequestError = result.message
          }
        }
      }

      uiState.timeRequestResponding = false
    }
  }

  fun clearProtectionHistory() {
    if (uiState.protectionHistoryClearing) return

    val controlToken = settingsStore.controlToken(device.deviceId)
    if (controlToken.isNullOrBlank()) {
      uiState.protectionHistoryError =
        "Control token is missing. Re-pairing is required."
      return
    }

    scope.launch {
      uiState.protectionHistoryClearing = true
      uiState.protectionHistoryError = null

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
          uiState.protectionHistory = emptyList()
          uiState.showAllProtectionHistory = false
          uiState.showClearProtectionHistoryConfirm = false
        }

        is ProtectionHistoryClearResult.Error -> {
          if (result.pairingInvalid) {
            removeInvalidPairing(
              deviceId = device.deviceId,
              displayName = device.displayName,
            )
          } else {
            uiState.protectionHistoryError = result.message
          }
        }
      }

      uiState.protectionHistoryClearing = false
    }
  }

  fun saveDailyLimit(minutes: Int?) {
    if (uiState.dailyLimitSaving) return

    val controlToken = settingsStore.controlToken(device.deviceId)
    if (controlToken.isNullOrBlank()) {
      uiState.dailyLimitError = "Control token is missing. Re-pairing is required."
      return
    }

    scope.launch {
      uiState.dailyLimitSaving = true
      uiState.dailyLimitError = null
      uiState.dailyLimitNotice = null

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
          val currentDevice = uiState.pairedDevice ?: device
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

          uiState.pairedDevice = updatedDevice
          settingsStore.savePairing(
            device = updatedDevice,
            controlToken = controlToken,
          )
          uiState.pairedDevices = settingsStore.loadPairedDevices()
          if (result.minutes != null) {
            uiState.selectedDailyLimitMinutes = result.minutes
          }
          uiState.showDailyLimitPicker = false
          uiState.dailyLimitNotice =
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
            uiState.dailyLimitError = result.message
          }
        }
      }

      uiState.dailyLimitSaving = false
    }
  }

  fun testConnection() {
    if (uiState.connectionTestInProgress || uiState.commandInProgress) return

    val controlToken = settingsStore.controlToken(device.deviceId)
    if (controlToken.isNullOrBlank()) {
      uiState.connectionTestError =
        "Control token is missing. Re-pairing is required."
      return
    }

    scope.launch {
      uiState.connectionTestInProgress = true
      uiState.connectionTestMessage = "Testing Parent → backend → Child connection…"
      uiState.connectionTestError = null

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
                        uiState.pairedDevice?.lastSeenAt ?: device.lastSeenAt,
                    )
                  uiState.pairedDevice = updatedDevice
                  settingsStore.savePairing(
                    device = updatedDevice,
                    controlToken = controlToken,
                  )
                  uiState.pairedDevices = settingsStore.loadPairedDevices()
                }
              }

              is CommandDeliveryResult.Error -> {
                uiState.connectionTestError = statusResult.message
                break
              }
            }
          }

          if (uiState.connectionTestError == null) {
            when (deliveryStatus) {
              "APPLIED" -> {
                uiState.connectionTestMessage =
                  "✓ Connection OK — Child received and confirmed the test."
              }

              "FAILED" -> {
                uiState.connectionTestMessage = null
                uiState.connectionTestError =
                  "Child received the test, but could not complete it."
              }

              else -> {
                uiState.connectionTestMessage =
                  "Backend accepted the test, but Child has not confirmed it yet."
              }
            }
          }
        }

        is CommandResult.Error -> {
          uiState.connectionTestMessage = null
          uiState.connectionTestError = result.message
        }
      }

      uiState.connectionTestInProgress = false
    }
  }

  fun sendCommand(command: RemoteCommand) {
    if (uiState.commandInProgress) return

    val controlToken = settingsStore.controlToken(device.deviceId)
    if (controlToken.isNullOrBlank()) {
      uiState.commandError = "Control token is missing. Re-pairing is required."
      return
    }

    scope.launch {
      uiState.commandInProgress = true
      uiState.activeCommandType = command.type
      uiState.commandProgressMessage = commandSendingLabel(command, device.isOnline)
      uiState.commandNotice = null
      uiState.pendingCommandFeedback = null
      uiState.commandError = null

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
                        uiState.pairedDevice?.lastSeenAt ?: device.lastSeenAt,
                    )
                  uiState.pairedDevice = updatedDevice
                  settingsStore.savePairing(
                    device = updatedDevice,
                    controlToken = controlToken,
                  )
                  uiState.pairedDevices = settingsStore.loadPairedDevices()
                }
              }

              is CommandDeliveryResult.Error -> {
                uiState.commandError = statusResult.message
                break
              }
            }
          }

          if (uiState.commandError == null) {
            when (deliveryStatus) {
              "APPLIED" -> {
                uiState.commandNotice = commandAppliedLabel(command)
                uiState.pendingCommandFeedback = null
              }

              "FAILED" -> {
                uiState.commandNotice = null
                uiState.pendingCommandFeedback = null
                uiState.commandError = "The command could not be applied. Please try again."
              }

              else -> {
                uiState.commandNotice =
                  commandQueuedLabel(
                    command = command,
                    deviceOnline = uiState.pairedDevice?.isOnline ?: device.isOnline,
                  )
                uiState.pendingCommandFeedback = command
              }
            }
          }
        }

        is CommandResult.Error -> {
          uiState.commandError = result.message
        }
      }

      uiState.commandProgressMessage = null
      uiState.activeCommandType = null
      uiState.commandInProgress = false
    }
  }

  ParentDashboardShell(
    device = device,
    selectedSection = uiState.selectedTab,
    actionsBusy =
      uiState.commandInProgress ||
        uiState.connectionTestInProgress ||
        uiState.refreshInProgress,
    parentEmail = ParentSupabase.client.auth.currentUserOrNull()?.email,
    onChangeDevice = { uiState.showDevices = true },
    onSectionSelected = { uiState.selectedTab = it },
    onSignOut = {
      scope.launch {
        runCatching {
          ParentSupabase.client.auth.signOut()
        }.onSuccess {
          accountScopeStore.clearActiveAccount()
        }.onFailure { error ->
          uiState.commandError =
            error.message ?: "Could not sign out. Please try again."
        }
      }
    },
    modifier = modifier,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {

    if (uiState.selectedTab == 3) {
      ParentDeviceTab(
        device = device,
        pairedDeviceCount = uiState.pairedDevices.size,
        refreshInProgress = uiState.refreshInProgress,
        commandInProgress = uiState.commandInProgress,
        deviceRenaming = uiState.deviceRenaming,
        deviceUnpairing = uiState.deviceUnpairing,
        protectionHistory = uiState.protectionHistory,
        protectionHistoryLoading = uiState.protectionHistoryLoading,
        protectionHistoryError = uiState.protectionHistoryError,
        showAllProtectionHistory = uiState.showAllProtectionHistory,
        protectionHistoryClearing = uiState.protectionHistoryClearing,
        onShowDevices = { uiState.showDevices = true },
        onRefreshStatus = {
          if (!uiState.refreshInProgress) {
            val controlToken = settingsStore.controlToken(device.deviceId)
            if (controlToken.isNullOrBlank()) {
              uiState.commandError =
                "Control token is missing. Re-pairing is required."
            } else {
              scope.launch {
                uiState.refreshInProgress = true
                uiState.commandError = null

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
                    uiState.pairedDevice = statusResult.device
                    settingsStore.savePairing(
                      device = statusResult.device,
                      controlToken = controlToken,
                    )
                    uiState.pairedDevices = settingsStore.loadPairedDevices()

                    uiState.pendingCommandFeedback?.let { pendingCommand ->
                      if (
                        commandMatchesDeviceState(
                          pendingCommand,
                          statusResult.device,
                        )
                      ) {
                        uiState.commandNotice = commandAppliedLabel(pendingCommand)
                        uiState.pendingCommandFeedback = null
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
                      uiState.commandError = statusResult.message
                    }
                  }
                }

                uiState.refreshInProgress = false
              }
            }
          }
        },
        onManageDevice = {
          uiState.deviceManagementError = null
          uiState.showDeviceManagement = true
        },
        onToggleProtectionHistory = {
          uiState.showAllProtectionHistory = !uiState.showAllProtectionHistory
        },
        onRequestClearProtectionHistory = {
          uiState.showClearProtectionHistoryConfirm = true
          uiState.protectionHistoryError = null
        },
      )
    }

    if (uiState.selectedTab == 0) {
      ParentOverviewTab(
        device = device,
        pendingTimeRequest = uiState.pendingTimeRequest,
        timeRequestResponding = uiState.timeRequestResponding,
        timeRequestNotice = uiState.timeRequestNotice,
        timeRequestError = uiState.timeRequestError,
        commandInProgress = uiState.commandInProgress,
        activeCommandType = uiState.activeCommandType,
        connectionTestInProgress = uiState.connectionTestInProgress,
        connectionTestMessage = uiState.connectionTestMessage,
        connectionTestError = uiState.connectionTestError,
        appUsageDays = uiState.appUsageDays,
        appUsageLoading = uiState.appUsageLoading,
        appUsageError = uiState.appUsageError,
        appUsageView = uiState.appUsageView,
        onRespondToTimeRequest = { request, approve ->
          respondToTimeRequest(request, approve)
        },
        onLock = { sendCommand(RemoteCommand.lock()) },
        onUnlock = { sendCommand(RemoteCommand.unlock()) },
        onAddBonusTime = { uiState.showBonusTimePicker = true },
        onTestConnection = { testConnection() },
        onOpenDevice = { uiState.selectedTab = 3 },
        onAppUsageViewChange = { uiState.appUsageView = it },
      )
    }

    if (uiState.selectedTab == 2) {
      ParentAppsTab(
        allowedAppsLoading = uiState.allowedAppsLoading,
        allowedAppsSaving = uiState.allowedAppsSaving,
        commandInProgress = uiState.commandInProgress,
        allowedAppsNotice = uiState.allowedAppsNotice,
        onManageAllowedApps = {
          if (!uiState.allowedAppsLoading) {
            val controlToken = settingsStore.controlToken(device.deviceId)
            if (controlToken.isNullOrBlank()) {
              uiState.commandError =
                "Control token is missing. Re-pairing is required."
            } else {
              scope.launch {
                uiState.allowedAppsLoading = true
                uiState.allowedAppsError = null
                uiState.allowedAppsNotice = null
                uiState.commandError = null

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
                    uiState.allowedAppsEditorSnapshot = result.snapshot
                  }

                  is AllowedAppsFetchResult.Error -> {
                    uiState.commandError = result.message
                  }
                }

                uiState.allowedAppsLoading = false
              }
            }
          }
        },
      )
    }

    if (uiState.selectedTab == 1) {
      ParentScheduleTab(
        device = device,
        scheduleLoading = uiState.scheduleLoading,
        commandInProgress = uiState.commandInProgress,
        dailyLimitSaving = uiState.dailyLimitSaving,
        dailyLimitNotice = uiState.dailyLimitNotice,
        dailyLimitError = uiState.dailyLimitError,
        scheduleNotice = uiState.scheduleNotice,
        onEditSchedule = {
          if (!uiState.scheduleLoading) {
            val controlToken = settingsStore.controlToken(device.deviceId)
            if (controlToken.isNullOrBlank()) {
              uiState.commandError =
                "Control token is missing. Re-pairing is required."
            } else {
              scope.launch {
                uiState.scheduleLoading = true
                uiState.scheduleError = null
                uiState.commandError = null
                uiState.scheduleNotice = null

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
                    uiState.scheduleEditorSchedule = result.schedule
                  }

                  is ScheduleFetchResult.Error -> {
                    uiState.commandError = result.message
                  }
                }

                uiState.scheduleLoading = false
              }
            }
          }
        },
        onSetDailyLimit = { initialMinutes ->
          uiState.selectedDailyLimitMinutes = initialMinutes
          uiState.dailyLimitError = null
          uiState.dailyLimitNotice = null
          uiState.showDailyLimitPicker = true
        },
        onDisableDailyLimit = { saveDailyLimit(null) },
      )
    }

    if (uiState.selectedTab == 4) {
      ParentSettingsScreen()
    }

    uiState.commandError?.let { message ->
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }

  if (uiState.showClearProtectionHistoryConfirm) {
    AlertDialog(
      onDismissRequest = {
        if (!uiState.protectionHistoryClearing) {
          uiState.showClearProtectionHistoryConfirm = false
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
          enabled = !uiState.protectionHistoryClearing,
        ) {
          Text(
            if (uiState.protectionHistoryClearing) {
              "CLEARING…"
            } else {
              "CLEAR"
            },
          )
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = { uiState.showClearProtectionHistoryConfirm = false },
          enabled = !uiState.protectionHistoryClearing,
        ) {
          Text("CANCEL")
        }
      },
    )
  }

  if (uiState.showDailyLimitPicker) {
    DailyLimitWheelDialog(
      initialMinutes = uiState.selectedDailyLimitMinutes,
      onDismiss = {
        if (!uiState.dailyLimitSaving) {
          uiState.showDailyLimitPicker = false
        }
      },
      onConfirm = { minutes ->
        uiState.selectedDailyLimitMinutes = minutes
        saveDailyLimit(minutes)
      },
    )
  }

  if (uiState.showBonusTimePicker) {
    BonusTimeWheelDialog(
      initialMinutes = uiState.selectedBonusMinutes,
      onDismiss = { uiState.showBonusTimePicker = false },
      onConfirm = { minutes ->
        uiState.selectedBonusMinutes = minutes
        uiState.showBonusTimePicker = false
        sendCommand(RemoteCommand.bonusTime(minutes))
      },
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun ParentDashboardPreview() {
  MaterialTheme {
    ParentDashboardScreen()
  }
}
