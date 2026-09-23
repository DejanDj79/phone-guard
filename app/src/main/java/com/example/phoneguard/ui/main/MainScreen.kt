package com.example.phoneguard.ui.main

import androidx.activity.compose.BackHandler
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
import android.os.Build
import androidx.navigation3.runtime.NavKey
import com.example.phoneguard.accessibility.PhoneGuardAccessibilityStatus
import com.example.phoneguard.core.PairingIdentity
import com.example.phoneguard.data.ChildSettingsStore
import com.example.phoneguard.protection.BackgroundProtectionStatus
import com.example.phoneguard.remote.ChildBackendClient
import com.example.phoneguard.remote.ChildPairingResetResult
import com.example.phoneguard.remote.ChildRegistrationResult
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
  var pairingIdentity by remember {
    mutableStateOf(settingsStore.getOrCreatePairingIdentity())
  }
  var registrationResult by remember {
    mutableStateOf<ChildRegistrationResult?>(null)
  }
  var registrationInProgress by remember { mutableStateOf(false) }
  var registrationRetryKey by remember { mutableStateOf(0) }

  DisposableEffect(lifecycleOwner, context) {
    val observer =
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          accessibilityEnabled = PhoneGuardAccessibilityStatus.isEnabled(context)
          exactAlarmAccess = alarmScheduler.hasExactAlarmAccess()
          batteryOptimizationIgnored =
            BackgroundProtectionStatus.isBatteryOptimizationIgnored(context)
        }
      }

    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  DisposableEffect(settingsStore) {
    val listener =
      settingsStore.registerLockStateListener {
        isLocked = settingsStore.isEffectivelyLocked()
      }

    onDispose {
      settingsStore.unregisterLockStateListener(listener)
    }
  }

  LaunchedEffect(Unit) {
    alarmScheduler.syncCurrentStateAndScheduleNext()
    isLocked = settingsStore.isEffectivelyLocked()
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
        unlockTimeLabel =
          if (settingsStore.isScheduleLockActive()) {
            settingsStore.currentScheduledUnlockLabel()
          } else {
            null
          },
        onUnlock = { pin ->
          val accepted = settingsStore.verifyParentPin(pin)
          if (accepted) {
            settingsStore.clearAllLocks()
            alarmScheduler.scheduleNext()
            isLocked = false
          }
          accepted
        },
      )
    }

    else -> {
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
            BackgroundProtectionStatus.batteryOptimizationSettingsIntent(),
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
        onResetPairing = { pin ->
          if (!settingsStore.verifyParentPin(pin)) {
            ChildPairingResetResult.Failure("Pogrešan roditeljski PIN.")
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
        text = "Postavi roditeljski PIN",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "PIN mora imati 4 do 6 cifara. Koristiće se za lokalno otključavanje dečjeg telefona.",
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
        label = "Roditeljski PIN",
      )

      Spacer(modifier = Modifier.height(12.dp))

      PinField(
        value = confirmation,
        onValueChange = {
          confirmation = it.onlyPinDigits()
          errorMessage = null
        },
        label = "Ponovi PIN",
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
              pin.length !in 4..6 -> "PIN mora imati 4 do 6 cifara."
              pin != confirmation -> "PIN-ovi se ne poklapaju."
              else -> {
                onPinSet(pin)
                null
              }
            }
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("SAČUVAJ PIN")
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
      text = "Zaštićeni uređaj",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Status zaštite",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val protectionReady =
          accessibilityEnabled &&
            exactAlarmAccess

        Text(
          text =
            if (protectionReady) {
              "● Zaštita je aktivna"
            } else {
              "○ Zaštita nije kompletna"
            },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )

        Text(
          text =
            if (accessibilityEnabled) {
              "✓ Zaštita ekrana je uključena"
            } else {
              "✕ Zaštita ekrana nije uključena"
            },
          style = MaterialTheme.typography.bodyMedium,
        )

        Text(
          text =
            if (exactAlarmAccess) {
              "✓ Precizno vreme zaključavanja"
            } else {
              "△ Precizno vreme zaključavanja nije dozvoljeno"
            },
          style = MaterialTheme.typography.bodyMedium,
        )

        Text(
          text =
            if (batteryOptimizationIgnored) {
              "✓ Rad u pozadini je dozvoljen"
            } else {
              "△ Android može ograničiti rad u pozadini"
            },
          style = MaterialTheme.typography.bodyMedium,
        )

        Text(
          text = "PhoneGuard koristi Accessibility samo da bi ekran roditeljske zaštite ostao iznad drugih aplikacija dok je zaključavanje aktivno.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!accessibilityEnabled) {
          OutlinedButton(
            onClick = onEnableAccessibility,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("UKLJUČI ZAŠTITU EKRANA")
          }
        }

        if (!batteryOptimizationIgnored) {
          OutlinedButton(
            onClick = onOpenBatterySettings,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("PODEŠAVANJA BATERIJE")
          }
        }

        if (!exactAlarmAccess) {
          OutlinedButton(
            onClick = onRequestExactAlarmAccess,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("DOZVOLI PRECIZNO VREME")
          }
        }
      }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Povezivanje sa roditeljem",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
          text =
            when {
              registrationInProgress -> "Povezujem uređaj…"
              paired -> "● Povezan sa Parent aplikacijom"
              registrationSuccess != null -> "Spreman za uparivanje"
              registrationFailure != null ->
                "Greška pri povezivanju: " + registrationFailure.message
              else -> "Povezivanje još nije završeno"
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
            text = "Ovim uređajem trenutno upravlja uparena Parent aplikacija.",
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
              Text("POVEŽI NOVOG RODITELJA")
            }
          } else {
            Text(
              text = "Ovo će poništiti pristup prethodno uparenoj Parent aplikaciji.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )

            PinField(
              value = resetPin,
              onValueChange = {
                resetPin = it.onlyPinDigits()
                resetError = null
              },
              label = "Roditeljski PIN",
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
                  "MENJAM…"
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
              Text("OTKAŽI")
            }
          }
        } else {
          Text(
            text = pairingIdentity.pairingCode,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
          )

          Text(
            text = "Unesi ovaj kod u PhoneGuard Parent aplikaciji.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          if (registrationSuccess != null) {
            Text(
              text = "Kod za uparivanje važi približno 15 minuta.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          OutlinedButton(
            onClick = onRegeneratePairingCode,
            enabled = !registrationInProgress,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("GENERIŠI NOVI KOD")
          }
        }

        if (registrationFailure != null) {
          OutlinedButton(
            onClick = onRetryRegistration,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("POKUŠAJ PONOVO")
          }
        }

      }
    }

  }
}

@Composable
private fun LockScreen(
  unlockTimeLabel: String?,
  onUnlock: (String) -> Boolean,
  modifier: Modifier = Modifier,
) {
  var pin by remember { mutableStateOf("") }
  var showPinEntry by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  Surface(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = "🔒",
        fontSize = 56.sp,
      )

      Spacer(modifier = Modifier.height(28.dp))

      Text(
        text = "Telefon je trenutno zaključan",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(16.dp))

      if (unlockTimeLabel != null) {
        Text(
          text = "Ponovo dostupno u",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
          text = unlockTimeLabel,
          style = MaterialTheme.typography.displaySmall,
          fontWeight = FontWeight.Bold,
        )
      } else {
        Text(
          text = "Ručno zaključano",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(modifier = Modifier.height(40.dp))

      if (!showPinEntry) {
        OutlinedButton(
          onClick = {
            showPinEntry = true
            errorMessage = null
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Otključaj roditeljskim PIN-om")
        }
      } else {
        PinField(
          value = pin,
          onValueChange = {
            pin = it.onlyPinDigits()
            errorMessage = null
          },
          label = "Roditeljski PIN",
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
              errorMessage = "Pogrešan PIN."
              pin = ""
            }
          },
          enabled = pin.length in 4..6,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("OTKLJUČAJ")
        }
      }
    }
  }
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
      onResetPairing = { ChildPairingResetResult.Success("preview-expiry") },
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun LockScreenPreview() {
  PhoneGuardTheme {
    LockScreen(
      unlockTimeLabel = "07:00",
      onUnlock = { false },
    )
  }
}
