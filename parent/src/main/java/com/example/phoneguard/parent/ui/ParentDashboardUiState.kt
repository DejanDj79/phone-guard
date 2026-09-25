package com.example.phoneguard.parent.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.phoneguard.core.AllowedAppsSnapshot
import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.RemoteCommand
import com.example.phoneguard.core.RemoteWeeklySchedule
import com.example.phoneguard.parent.data.AppUsageDay
import com.example.phoneguard.parent.data.PendingTimeRequest
import com.example.phoneguard.parent.data.ProtectionHistoryEvent

@Stable
internal class ParentDashboardUiState(
  initialPairedDevice: ChildDevice?,
  initialPairedDevices: List<ChildDevice>,
) {
  var pairedDevice by mutableStateOf(initialPairedDevice)
  var pairedDevices by mutableStateOf(initialPairedDevices)
  var showPairDevice by mutableStateOf(initialPairedDevice == null)
  var showDevices by mutableStateOf(false)
  var selectedTab by mutableStateOf(0)

  var commandInProgress by mutableStateOf(false)
  var commandProgressMessage by mutableStateOf<String?>(null)
  var commandNotice by mutableStateOf<String?>(null)
  var pendingCommandFeedback by mutableStateOf<RemoteCommand?>(null)
  var commandError by mutableStateOf<String?>(null)
  var refreshInProgress by mutableStateOf(false)

  var connectionTestInProgress by mutableStateOf(false)
  var connectionTestMessage by mutableStateOf<String?>(null)
  var connectionTestError by mutableStateOf<String?>(null)

  var showBonusTimePicker by mutableStateOf(false)
  var selectedBonusMinutes by mutableStateOf(15)

  var scheduleEditorSchedule by mutableStateOf<RemoteWeeklySchedule?>(null)
  var scheduleLoading by mutableStateOf(false)
  var scheduleSaving by mutableStateOf(false)
  var scheduleError by mutableStateOf<String?>(null)
  var scheduleNotice by mutableStateOf<String?>(null)

  var allowedAppsEditorSnapshot by mutableStateOf<AllowedAppsSnapshot?>(null)
  var allowedAppsLoading by mutableStateOf(false)
  var allowedAppsSaving by mutableStateOf(false)
  var allowedAppsError by mutableStateOf<String?>(null)
  var allowedAppsNotice by mutableStateOf<String?>(null)

  var showDeviceManagement by mutableStateOf(false)
  var deviceRenaming by mutableStateOf(false)
  var deviceUnpairing by mutableStateOf(false)
  var deviceManagementError by mutableStateOf<String?>(null)

  var pendingTimeRequest by mutableStateOf<PendingTimeRequest?>(null)
  var timeRequestResponding by mutableStateOf(false)
  var timeRequestError by mutableStateOf<String?>(null)
  var timeRequestNotice by mutableStateOf<String?>(null)

  var showDailyLimitPicker by mutableStateOf(false)
  var selectedDailyLimitMinutes by
    mutableStateOf(initialPairedDevice?.dailyScreenTime?.limitMinutes ?: 120)
  var dailyLimitSaving by mutableStateOf(false)
  var dailyLimitError by mutableStateOf<String?>(null)
  var dailyLimitNotice by mutableStateOf<String?>(null)

  var protectionHistory by mutableStateOf<List<ProtectionHistoryEvent>>(emptyList())
  var protectionHistoryLoading by mutableStateOf(false)
  var protectionHistoryError by mutableStateOf<String?>(null)
  var showAllProtectionHistory by mutableStateOf(false)
  var protectionHistoryClearing by mutableStateOf(false)
  var showClearProtectionHistoryConfirm by mutableStateOf(false)

  var appUsageDays by mutableStateOf<List<AppUsageDay>>(emptyList())
  var appUsageLoading by mutableStateOf(false)
  var appUsageError by mutableStateOf<String?>(null)
  var appUsageView by mutableStateOf(APP_USAGE_VIEW_TODAY)
}
