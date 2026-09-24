package com.example.phoneguard.ui.main

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.navigation3.runtime.NavKey
import com.example.phoneguard.accessibility.PhoneGuardAccessibilityStatus
import com.example.phoneguard.core.PairingIdentity
import com.example.phoneguard.data.ChildSettingsStore
import com.example.phoneguard.protection.BackgroundProtectionStatus
import com.example.phoneguard.remote.ChildBackendClient
import com.example.phoneguard.remote.AppInventorySyncer
import com.example.phoneguard.remote.ChildHeartbeatSender
import com.example.phoneguard.remote.ChildPairingResetResult
import com.example.phoneguard.remote.ChildRegistrationResult
import com.example.phoneguard.remote.ChildTimeRequestResult
import com.example.phoneguard.remote.FcmTokenProvider
import com.example.phoneguard.schedule.ScheduleAlarmScheduler
import com.example.phoneguard.theme.PhoneGuardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val settingsStore = remember(context) { ChildSettingsStore(context.applicationContext) }
  val alarmScheduler = remember(context) { ScheduleAlarmScheduler(context.applicationContext) }
  val backendClient = remember { ChildBackendClient() }

  var hasParentPin by remember { mutableStateOf(settingsStore.hasParentPin()) }
  var setupWizardCompleted by remember {
    mutableStateOf(settingsStore.isSetupWizardCompleted())
  }
  var accessibilityEnabled by remember {
    mutableStateOf(PhoneGuardAccessibilityStatus.isEnabled(context))
  }
  var exactAlarmAccess by remember {
    mutableStateOf(alarmScheduler.hasExactAlarmAccess())
  }
  var batteryOptimizationIgnored by remember {
    mutableStateOf(BackgroundProtectionStatus.isBatteryOptimizationIgnored(context))
  }
  var isLocked by remember { mutableStateOf(settingsStore.isEffectivelyLocked()) }
  var allowedPackages by remember { mutableStateOf(settingsStore.allowedPackages()) }
  var timeRequestFeedback by remember {
    mutableStateOf(settingsStore.timeRequestFeedback())
  }
  var pairingIdentity by remember {
    mutableStateOf(settingsStore.getOrCreatePairingIdentity())
  }
  var registrationResult by remember {
    mutableStateOf<ChildRegistrationResult?>(null)
  }
  var registrationInProgress by remember { mutableStateOf(false) }
  var registrationRetryKey by remember { mutableStateOf(0) }

  DisposableEffect(lifecycleOwner, context) {
    var firstResume = true
    val mainHandler = Handler(Looper.getMainLooper())

    val refreshProtectionStatus =
      Runnable {
        accessibilityEnabled = PhoneGuardAccessibilityStatus.isEnabled(context)
        exactAlarmAccess = alarmScheduler.hasExactAlarmAccess()
        batteryOptimizationIgnored =
          BackgroundProtectionStatus.isBatteryOptimizationIgnored(context)

        Thread {
          AppInventorySyncer(context.applicationContext).sync()
          ChildHeartbeatSender(context.applicationContext).send()
        }.start()
      }

    val observer =
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          if (firstResume) {
            firstResume = false
          } else {
            registrationRetryKey += 1
          }

          mainHandler.removeCallbacks(refreshProtectionStatus)
          mainHandler.postDelayed(
            refreshProtectionStatus,
            PROTECTION_STATUS_REFRESH_DELAY_MS,
          )
        }
      }

    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose {
      mainHandler.removeCallbacks(refreshProtectionStatus)
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  DisposableEffect(settingsStore) {
    val listener =
      settingsStore.registerLockStateListener {
        isLocked = settingsStore.isEffectivelyLocked()
        allowedPackages = settingsStore.allowedPackages()
        timeRequestFeedback = settingsStore.timeRequestFeedback()
      }

    onDispose {
      settingsStore.unregisterLockStateListener(listener)
    }
  }

  LaunchedEffect(Unit) {
    alarmScheduler.syncCurrentStateAndScheduleNext()
    isLocked = settingsStore.isEffectivelyLocked()
    allowedPackages = settingsStore.allowedPackages()
  }

  LaunchedEffect(pairingIdentity, registrationRetryKey) {
    registrationInProgress = true

    registrationResult =
      withContext(Dispatchers.IO) {
        val fcmToken = FcmTokenProvider.currentToken()

        backendClient.register(
          identity = pairingIdentity,
          displayName = Build.MODEL.ifBlank { "Child device" },
          deviceSecret = settingsStore.getOrCreateDeviceSecret(),
          fcmToken = fcmToken,
        )
      }

    registrationInProgress = false
  }

  when {
    !hasParentPin -> {
      ParentPinSetupScreen(
        onPinSet = { pin ->
          settingsStore.setParentPin(pin)
          hasParentPin = true
        },
      )
    }

    isLocked -> {
      BackHandler(enabled = true) {
        // Intentionally consume Back while the child device is locked.
      }

      LockScreen(
        allowedPackages = allowedPackages,
        timeRequestFeedback = timeRequestFeedback,
        unlockTimeLabel =
          if (settingsStore.isScheduleLockActive()) {
            settingsStore.currentScheduledUnlockLabel()
          } else {
            null
          },
        dailyLimitReached = settingsStore.isDailyLimitLockActive(),
        onRequestMoreTime = { minutes ->
          settingsStore.setTimeRequestFeedback(null)
          timeRequestFeedback = null
          withContext(Dispatchers.IO) {
            backendClient.requestMoreTime(
              deviceId = pairingIdentity.deviceId,
              deviceSecret = settingsStore.getOrCreateDeviceSecret(),
              requestedMinutes = minutes,
            )
          }
        },
        onUnlock = { pin ->
          val accepted = settingsStore.verifyParentPin(pin)
          if (accepted) {
            settingsStore.clearAllLocks()
            alarmScheduler.scheduleNext()
            isLocked = settingsStore.isEffectivelyLocked()
          }
          accepted
        },
      )
    }

    else -> {
      val registrationSuccess =
        registrationResult as? ChildRegistrationResult.Success
      val paired = registrationSuccess?.paired == true
      val protectionReady =
        accessibilityEnabled &&
          exactAlarmAccess &&
          batteryOptimizationIgnored

      if (!setupWizardCompleted) {
        ChildSetupWizard(
          modifier = modifier,
          pairingIdentity = pairingIdentity,
          registrationResult = registrationResult,
          registrationInProgress = registrationInProgress,
          accessibilityEnabled = accessibilityEnabled,
          exactAlarmAccess = exactAlarmAccess,
          batteryOptimizationIgnored = batteryOptimizationIgnored,
          onEnableAccessibility = {
            context.startActivity(PhoneGuardAccessibilityStatus.settingsIntent())
          },
          onOpenBatterySettings = {
            context.startActivity(
              BackgroundProtectionStatus.batteryOptimizationSettingsIntent(context),
            )
          },
          onRequestExactAlarmAccess = {
            alarmScheduler.exactAlarmPermissionIntent()?.let { intent ->
              context.startActivity(intent)
            }
          },
          onRegeneratePairingCode = {
            registrationResult = null
            pairingIdentity = settingsStore.regeneratePairingCode()
          },
          onRetryRegistration = {
            registrationResult = null
            registrationRetryKey += 1
          },
          onFinish = {
            if (protectionReady && paired) {
              settingsStore.setSetupWizardCompleted(true)
              setupWizardCompleted = true
            }
          },
        )
      } else {
        ChildDashboard(
          modifier = modifier,
          pairingIdentity = pairingIdentity,
          registrationResult = registrationResult,
          registrationInProgress = registrationInProgress,
          accessibilityEnabled = accessibilityEnabled,
          exactAlarmAccess = exactAlarmAccess,
          batteryOptimizationIgnored = batteryOptimizationIgnored,
          onEnableAccessibility = {
            context.startActivity(PhoneGuardAccessibilityStatus.settingsIntent())
          },
          onOpenBatterySettings = {
            context.startActivity(
              BackgroundProtectionStatus.batteryOptimizationSettingsIntent(context),
            )
          },
          onRequestExactAlarmAccess = {
            alarmScheduler.exactAlarmPermissionIntent()?.let { intent ->
              context.startActivity(intent)
            }
          },
          onRegeneratePairingCode = {
            registrationResult = null
            pairingIdentity = settingsStore.regeneratePairingCode()
          },
          onRetryRegistration = {
            registrationResult = null
            registrationRetryKey += 1
          },
          onRunSetupCheck = {
            settingsStore.setSetupWizardCompleted(false)
            setupWizardCompleted = false
          },
          onResetPairing = { pin ->
            if (!settingsStore.verifyParentPin(pin)) {
              ChildPairingResetResult.Failure("Incorrect parent PIN.")
            } else {
              val refreshedIdentity = settingsStore.regeneratePairingCode()
              val result =
                withContext(Dispatchers.IO) {
                  backendClient.resetPairing(
                    identity = refreshedIdentity,
                    deviceSecret = settingsStore.getOrCreateDeviceSecret(),
                  )
                }

              if (result is ChildPairingResetResult.Success) {
                pairingIdentity = refreshedIdentity
                registrationResult =
                  ChildRegistrationResult.Success(
                    pairingExpiresAt = result.pairingExpiresAt,
                    paired = false,
                  )
              }

              result
            }
          },
        )
      }
    }
  }
}

@Composable
private fun ParentPinSetupScreen(
  onPinSet: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var pin by remember { mutableStateOf("") }
  var confirmation by remember { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  Surface(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier.fillMaxSize().padding(32.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = "PhoneGuard",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "Set parent PIN",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "PIN must contain 4 to 6 digits. It is used for local unlocking of the Child phone.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(24.dp))

      PinField(
        value = pin,
        onValueChange = {
          pin = it.onlyPinDigits()
          errorMessage = null
        },
        label = "Parent PIN",
      )

      Spacer(modifier = Modifier.height(12.dp))

      PinField(
        value = confirmation,
        onValueChange = {
          confirmation = it.onlyPinDigits()
          errorMessage = null
        },
        label = "Confirm PIN",
      )

      errorMessage?.let {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = it,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Button(
        onClick = {
          errorMessage =
            when {
              pin.length !in 4..6 -> "PIN must contain 4 to 6 digits."
              pin != confirmation -> "PINs do not match."
              else -> {
                onPinSet(pin)
                null
              }
            }
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("SAVE PIN")
      }
    }
  }
}

@Composable
private fun PinField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun ChildSetupWizard(
  pairingIdentity: PairingIdentity,
  registrationResult: ChildRegistrationResult?,
  registrationInProgress: Boolean,
  accessibilityEnabled: Boolean,
  exactAlarmAccess: Boolean,
  batteryOptimizationIgnored: Boolean,
  onEnableAccessibility: () -> Unit,
  onOpenBatterySettings: () -> Unit,
  onRequestExactAlarmAccess: () -> Unit,
  onRegeneratePairingCode: () -> Unit,
  onRetryRegistration: () -> Unit,
  onFinish: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val registrationFailure =
    registrationResult as? ChildRegistrationResult.Failure
  val registrationSuccess =
    registrationResult as? ChildRegistrationResult.Success
  val paired = registrationSuccess?.paired == true
  val completedSteps =
    listOf(
      accessibilityEnabled,
      batteryOptimizationIgnored,
      exactAlarmAccess,
      paired,
    ).count { it }

  val currentStep =
    when {
      !accessibilityEnabled -> 1
      !batteryOptimizationIgnored -> 2
      !exactAlarmAccess -> 3
      !paired -> 4
      else -> 5
    }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(
      text = "PhoneGuard setup",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )

    Text(
      text =
        if (currentStep == 5) {
          "Protection ready"
        } else {
          "Step " + currentStep + " of 4"
        },
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text(
      text =
        completedSteps.toString() +
          " of 4 setup checks complete.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SetupStatusLine(
      label = "Screen protection",
      complete = accessibilityEnabled,
    )
    SetupStatusLine(
      label = "Background protection",
      complete = batteryOptimizationIgnored,
    )
    SetupStatusLine(
      label = "Exact timing",
      complete = exactAlarmAccess,
    )
    SetupStatusLine(
      label = "Parent connection",
      complete = paired,
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        when (currentStep) {
          1 -> {
            Text(
              text = "Enable screen protection",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text =
                "PhoneGuard needs its Accessibility service so the parental lock can stay above other apps.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = onEnableAccessibility,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("OPEN ACCESSIBILITY SETTINGS")
            }
          }

          2 -> {
            Text(
              text = "Allow background protection",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text =
                "Remove battery optimization for PhoneGuard so Android is less likely to suspend protection and background checks.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = onOpenBatterySettings,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("ALLOW BACKGROUND PROTECTION")
            }
          }

          3 -> {
            Text(
              text = "Allow exact timing",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text =
                "Exact alarm access keeps scheduled lock and unlock transitions as close to their configured time as Android allows.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = onRequestExactAlarmAccess,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("ALLOW EXACT TIMING")
            }
          }

          4 -> {
            Text(
              text = "Connect the Parent app",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )

            Text(
              text =
                when {
                  registrationInProgress -> "Connecting this Child device…"
                  registrationFailure != null ->
                    "Connection error: " + registrationFailure.message
                  else ->
                    "Enter this code in the PhoneGuard Parent app."
                },
              style = MaterialTheme.typography.bodyMedium,
              color =
                if (registrationFailure != null) {
                  MaterialTheme.colorScheme.error
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Text(
              text = pairingIdentity.pairingCode,
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold,
            )

            OutlinedButton(
              onClick = onRetryRegistration,
              enabled = !registrationInProgress,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                if (registrationInProgress) {
                  "CHECKING…"
                } else {
                  "CHECK CONNECTION"
                },
              )
            }

            OutlinedButton(
              onClick = onRegeneratePairingCode,
              enabled = !registrationInProgress,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("GENERATE NEW CODE")
            }
          }

          else -> {
            Text(
              text = "✓ Protection ready",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text =
                "Screen protection, background protection, precise timing and the Parent connection are all ready.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = onFinish,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("FINISH SETUP")
            }
          }
        }
      }
    }

    Text(
      text =
        "You can run this setup check again later from the Child dashboard.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SetupStatusLine(
  label: String,
  complete: Boolean,
) {
  Text(
    text =
      if (complete) {
        "✓ " + label
      } else {
        "○ " + label
      },
    style = MaterialTheme.typography.bodyMedium,
    color =
      if (complete) {
        MaterialTheme.colorScheme.onSurfaceVariant
      } else {
        MaterialTheme.colorScheme.error
      },
  )
}

@Composable
private fun ChildDashboard(
  pairingIdentity: PairingIdentity,
  registrationResult: ChildRegistrationResult?,
  registrationInProgress: Boolean,
  accessibilityEnabled: Boolean,
  exactAlarmAccess: Boolean,
  batteryOptimizationIgnored: Boolean,
  onEnableAccessibility: () -> Unit,
  onOpenBatterySettings: () -> Unit,
  onRequestExactAlarmAccess: () -> Unit,
  onRegeneratePairingCode: () -> Unit,
  onRetryRegistration: () -> Unit,
  onRunSetupCheck: () -> Unit,
  onResetPairing: suspend (String) -> ChildPairingResetResult,
  modifier: Modifier = Modifier,
) {
  val registrationFailure =
    registrationResult as? ChildRegistrationResult.Failure
  val registrationSuccess =
    registrationResult as? ChildRegistrationResult.Success
  val paired = registrationSuccess?.paired == true
  val pairingScope = rememberCoroutineScope()
  var showPairNewParent by remember { mutableStateOf(false) }
  var resetPin by remember { mutableStateOf("") }
  var resetError by remember { mutableStateOf<String?>(null) }
  var resetInProgress by remember { mutableStateOf(false) }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    Text(
      text = "PhoneGuard",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )

    Text(
      text = "Protected device",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Protection status",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val protectionReady =
          accessibilityEnabled &&
            exactAlarmAccess

        Text(
          text =
            if (protectionReady) {
              "● Protection is active"
            } else {
              "○ Protection is incomplete"
            },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )

        Text(
          text =
            if (accessibilityEnabled) {
              "✓ Screen protection is enabled"
            } else {
              "✕ Screen protection is disabled"
            },
          style = MaterialTheme.typography.bodyMedium,
        )

        Text(
          text =
            if (exactAlarmAccess) {
              "✓ Exact lock timing is enabled"
            } else {
              "△ Exact lock timing is not allowed"
            },
          style = MaterialTheme.typography.bodyMedium,
        )

        Text(
          text =
            if (batteryOptimizationIgnored) {
              "✓ Battery optimization exemption is enabled"
            } else {
              "△ Battery optimization may suspend PhoneGuard"
            },
          style = MaterialTheme.typography.bodyMedium,
        )

        Text(
          text = "PhoneGuard uses Accessibility only to keep the parental lock screen above other apps while protection is active.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!accessibilityEnabled) {
          OutlinedButton(
            onClick = onEnableAccessibility,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("ENABLE SCREEN PROTECTION")
          }
        }

        if (!batteryOptimizationIgnored) {
          OutlinedButton(
            onClick = onOpenBatterySettings,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("ALLOW BACKGROUND PROTECTION")
          }
        }

        if (!exactAlarmAccess) {
          OutlinedButton(
            onClick = onRequestExactAlarmAccess,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("ALLOW EXACT TIMING")
          }
        }

      }

        OutlinedButton(
          onClick = onRunSetupCheck,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("RUN SETUP CHECK")
        }
      }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Parent connection",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
          text =
            when {
              registrationInProgress -> "Connecting device…"
              paired -> "● Connected to Parent app"
              registrationSuccess != null -> "Ready to pair"
              registrationFailure != null ->
                "Connection error: " + registrationFailure.message
              else -> "Connection is not complete yet"
            },
          style = MaterialTheme.typography.bodyMedium,
          color =
            if (registrationFailure != null) {
              MaterialTheme.colorScheme.error
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        if (paired) {
          Text(
            text = "This device is currently managed by the paired Parent app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          if (!showPairNewParent) {
            OutlinedButton(
              onClick = {
                showPairNewParent = true
                resetPin = ""
                resetError = null
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("PAIR NEW PARENT")
            }
          } else {
            Text(
              text = "This will revoke access for the previously paired Parent app.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )

            PinField(
              value = resetPin,
              onValueChange = {
                resetPin = it.onlyPinDigits()
                resetError = null
              },
              label = "Parent PIN",
            )

            resetError?.let { message ->
              Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }

            Button(
              onClick = {
                pairingScope.launch {
                  resetInProgress = true
                  resetError = null

                  when (val result = onResetPairing(resetPin)) {
                    is ChildPairingResetResult.Success -> {
                      resetPin = ""
                      showPairNewParent = false
                    }

                    is ChildPairingResetResult.Failure -> {
                      resetError = result.message
                      resetPin = ""
                    }
                  }

                  resetInProgress = false
                }
              },
              enabled = resetPin.length in 4..6 && !resetInProgress,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                if (resetInProgress) {
                  "UPDATING…"
                } else {
                  "CONFIRM NEW PAIRING"
                },
              )
            }

            OutlinedButton(
              onClick = {
                showPairNewParent = false
                resetPin = ""
                resetError = null
              },
              enabled = !resetInProgress,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("CANCEL")
            }
          }
        } else {
          Text(
            text = pairingIdentity.pairingCode,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
          )

          Text(
            text = "Enter this code in the PhoneGuard Parent app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          if (registrationSuccess != null) {
            Text(
              text = "The pairing code is valid for about 15 minutes.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          OutlinedButton(
            onClick = onRegeneratePairingCode,
            enabled = !registrationInProgress,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("GENERATE NEW CODE")
          }
        }

        if (registrationFailure != null) {
          OutlinedButton(
            onClick = onRetryRegistration,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("TRY AGAIN")
          }
        }

      }
    }

  }
}

@Composable
private fun LockScreen(
  allowedPackages: Set<String>,
  timeRequestFeedback: String?,
  unlockTimeLabel: String?,
  dailyLimitReached: Boolean,
  onRequestMoreTime: suspend (Int) -> ChildTimeRequestResult,
  onUnlock: (String) -> Boolean,
  modifier: Modifier = Modifier,
) {
  var pin by remember { mutableStateOf("") }
  var showPinEntry by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var showTimeRequest by remember { mutableStateOf(false) }
  var timeRequestInProgress by remember { mutableStateOf(false) }
  var timeRequestMessage by remember { mutableStateOf<String?>(null) }
  val requestScope = rememberCoroutineScope()

  val context = LocalContext.current
  val packageManager = context.packageManager
  val audioManager =
    remember(context) { context.getSystemService(AudioManager::class.java) }
  val cameraManager =
    remember(context) { context.getSystemService(CameraManager::class.java) }
  val torchCameraId =
    remember(cameraManager) {
      runCatching {
        cameraManager.cameraIdList.firstOrNull { cameraId ->
          cameraManager
            .getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
      }.getOrNull()
    }
  var flashlightEnabled by remember { mutableStateOf(false) }
  var systemMessage by remember {
    mutableStateOf(ringerModeLabel(audioManager.ringerMode))
  }

  val allowedApps =
    remember(allowedPackages) {
      allowedPackages
        .mapNotNull { packageName ->
          val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)
              ?: return@mapNotNull null
          val label =
            runCatching {
              val applicationInfo =
                packageManager.getApplicationInfo(packageName, 0)
              packageManager.getApplicationLabel(applicationInfo).toString()
            }.getOrDefault(packageName)

          Triple(packageName, label, launchIntent)
        }
        .sortedBy { it.second.lowercase() }
    }

  Surface(modifier = modifier.fillMaxSize()) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 32.dp, vertical = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = "🔒",
        fontSize = 56.sp,
      )

      Spacer(modifier = Modifier.height(28.dp))

      Text(
        text = "Phone is currently locked",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(16.dp))

      when {
        unlockTimeLabel != null -> {
          Text(
            text = "Available again at",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Text(
            text = unlockTimeLabel,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
          )
        }

        dailyLimitReached -> {
          Text(
            text = "Daily limit reached",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        else -> {
          Text(
            text = "Locked manually",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(28.dp))

      if (allowedApps.isNotEmpty()) {
        Text(
          text = "Allowed apps",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        allowedApps.forEach { (_, label, launchIntent) ->
          OutlinedButton(
            onClick = {
              runCatching {
                context.startActivity(
                  Intent(launchIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
              }.onFailure {
                systemMessage = "This app could not be opened."
              }
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(label)
          }

          Spacer(modifier = Modifier.height(6.dp))
        }
      }

      Text(
        text = "Sound",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = {
            runCatching {
              audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }.onSuccess {
              systemMessage = ringerModeLabel(audioManager.ringerMode)
            }.onFailure {
              systemMessage = "Android did not allow this sound change."
            }
          },
          modifier = Modifier.weight(1f),
        ) {
          Text("SOUND")
        }

        Button(
          onClick = {
            runCatching {
              audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }.onSuccess {
              systemMessage = ringerModeLabel(audioManager.ringerMode)
            }.onFailure {
              systemMessage = "Android did not allow this sound change."
            }
          },
          modifier = Modifier.weight(1f),
        ) {
          Text("VIBRATE")
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Button(
        onClick = {
          val cameraId = torchCameraId
          if (cameraId == null) {
            systemMessage = "Flashlight is unavailable on this device."
          } else {
            val nextEnabled = !flashlightEnabled
            runCatching {
              cameraManager.setTorchMode(cameraId, nextEnabled)
            }.onSuccess {
              flashlightEnabled = nextEnabled
              systemMessage =
                if (flashlightEnabled) {
                  "Flashlight is on"
                } else {
                  "Flashlight is off"
                }
            }.onFailure {
              systemMessage = "Flashlight is currently unavailable."
            }
          }
        },
        enabled = torchCameraId != null,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (flashlightEnabled) "FLASHLIGHT OFF" else "FLASHLIGHT ON")
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = systemMessage,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(28.dp))

      Button(
        onClick = {
          showTimeRequest = true
          timeRequestMessage = null
        },
        enabled = !timeRequestInProgress,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (timeRequestInProgress) "SENDING REQUEST…" else "REQUEST MORE TIME")
      }

      timeRequestMessage?.let { message ->
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }

      timeRequestFeedback?.let { message ->
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      if (!showPinEntry) {
        OutlinedButton(
          onClick = {
            showPinEntry = true
            errorMessage = null
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Unlock with parent PIN")
        }
      } else {
        PinField(
          value = pin,
          onValueChange = {
            pin = it.onlyPinDigits()
            errorMessage = null
          },
          label = "Parent PIN",
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
            if (onUnlock(pin)) {
              pin = ""
              errorMessage = null
            } else {
              errorMessage = "Incorrect PIN."
              pin = ""
            }
          },
          enabled = pin.length in 4..6,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("UNLOCK")
        }
      }
    }
  }

  if (showTimeRequest) {
    RequestMoreTimeDialog(
      onDismiss = { showTimeRequest = false },
      onConfirm = { minutes ->
        showTimeRequest = false
        requestScope.launch {
          timeRequestInProgress = true
          timeRequestMessage = null

          timeRequestMessage =
            when (val result = onRequestMoreTime(minutes)) {
              is ChildTimeRequestResult.Success ->
                when {
                  result.alreadyPending ->
                    "A request is already waiting for Parent approval."
                  result.pushSent ->
                    "Request sent to Parent."
                  else ->
                    "Request sent. Parent will see it in PhoneGuard."
                }

              is ChildTimeRequestResult.Failure ->
                result.message
            }

          timeRequestInProgress = false
        }
      },
    )
  }
}

@Composable
private fun RequestMoreTimeDialog(
  onDismiss: () -> Unit,
  onConfirm: (Int) -> Unit,
) {
  var selectedMinutes by remember { mutableStateOf(15) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Request more time",
        fontWeight = FontWeight.SemiBold,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "How much extra time would you like to ask for?",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(5, 15, 30).forEach { minutes ->
            if (selectedMinutes == minutes) {
              Button(
                onClick = { selectedMinutes = minutes },
                modifier = Modifier.weight(1f),
              ) {
                Text(minutes.toString() + " min")
              }
            } else {
              OutlinedButton(
                onClick = { selectedMinutes = minutes },
                modifier = Modifier.weight(1f),
              ) {
                Text(minutes.toString() + " min")
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = { onConfirm(selectedMinutes) }) {
        Text("SEND REQUEST")
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) {
        Text("CANCEL")
      }
    },
  )
}

private fun ringerModeLabel(mode: Int): String =
  when (mode) {
    AudioManager.RINGER_MODE_NORMAL -> "Current mode: Sound"
    AudioManager.RINGER_MODE_VIBRATE -> "Current mode: Vibrate"
    else -> "Current sound mode"
  }

private fun String.onlyPinDigits(): String =
  filter(Char::isDigit).take(6)

@Preview(showBackground = true)
@Composable
private fun ParentPinSetupPreview() {
  PhoneGuardTheme {
    ParentPinSetupScreen(onPinSet = {})
  }
}

@Preview(showBackground = true)
@Composable
private fun ChildDashboardPreview() {
  PhoneGuardTheme {
    ChildDashboard(
      pairingIdentity =
        PairingIdentity(
          deviceId = "preview-device-id",
          pairingCode = "AB12CD",
        ),
      registrationResult =
        ChildRegistrationResult.Success(
          pairingExpiresAt = "preview-expiry",
          paired = false,
        ),
      registrationInProgress = false,
      accessibilityEnabled = false,
      exactAlarmAccess = false,
      batteryOptimizationIgnored = false,
      onEnableAccessibility = {},
      onOpenBatterySettings = {},
      onRequestExactAlarmAccess = {},
      onRegeneratePairingCode = {},
      onRetryRegistration = {},
      onRunSetupCheck = {},
      onResetPairing = { ChildPairingResetResult.Success("preview-expiry") },
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun LockScreenPreview() {
  PhoneGuardTheme {
    LockScreen(
      allowedPackages = emptySet(),
      timeRequestFeedback = "Your request for more time was denied by Parent.",
      unlockTimeLabel = "07:00",
      dailyLimitReached = false,
      onRequestMoreTime = {
        ChildTimeRequestResult.Success(
          requestId = "preview-request",
          requestedMinutes = it,
          alreadyPending = false,
          pushSent = true,
        )
      },
      onUnlock = { false },
    )
  }
}

private const val PROTECTION_STATUS_REFRESH_DELAY_MS = 1_000L
