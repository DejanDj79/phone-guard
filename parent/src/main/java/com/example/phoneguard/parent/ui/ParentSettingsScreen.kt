package com.example.phoneguard.parent.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.phoneguard.parent.data.ParentSecurityStore

@Composable
fun ParentSettingsScreen(
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val securityStore =
    remember(context) {
      ParentSecurityStore(context.applicationContext)
    }

  val biometricAuthenticators =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
      BiometricManager.Authenticators.BIOMETRIC_WEAK
  val biometricAvailable =
    remember(context) {
      BiometricManager.from(context)
        .canAuthenticate(biometricAuthenticators) ==
        BiometricManager.BIOMETRIC_SUCCESS
    }

  var biometricEnabled by remember {
    mutableStateOf(securityStore.biometricEnabled() && biometricAvailable)
  }
  var relockDelay by remember {
    mutableStateOf(securityStore.relockAfterBackgroundMillis())
  }

  var currentPin by remember { mutableStateOf("") }
  var newPin by remember { mutableStateOf("") }
  var confirmPin by remember { mutableStateOf("") }
  var pinNotice by remember { mutableStateOf<String?>(null) }
  var pinError by remember { mutableStateOf<String?>(null) }

  val notificationsAllowed =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Parent security",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )

        Text(
          text = "Change the PIN used to protect access to Parent controls.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SecurityPinField(
          value = currentPin,
          onValueChange = {
            currentPin = it.parentSettingsPinDigits()
            pinError = null
            pinNotice = null
          },
          label = "Current PIN",
        )

        SecurityPinField(
          value = newPin,
          onValueChange = {
            newPin = it.parentSettingsPinDigits()
            pinError = null
            pinNotice = null
          },
          label = "New PIN",
        )

        SecurityPinField(
          value = confirmPin,
          onValueChange = {
            confirmPin = it.parentSettingsPinDigits()
            pinError = null
            pinNotice = null
          },
          label = "Confirm new PIN",
        )

        pinError?.let { message ->
          Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }

        pinNotice?.let { message ->
          Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Button(
          onClick = {
            pinError =
              when {
                !securityStore.verifyPin(currentPin) ->
                  "Current PIN is incorrect."
                newPin.length !in 4..6 ->
                  "New PIN must contain 4 to 6 digits."
                newPin != confirmPin ->
                  "New PINs do not match."
                else -> {
                  securityStore.setPin(newPin)
                  currentPin = ""
                  newPin = ""
                  confirmPin = ""
                  pinNotice = "Parent PIN changed."
                  null
                }
              }
          },
          enabled =
            currentPin.length in 4..6 &&
              newPin.length in 4..6 &&
              confirmPin.length in 4..6,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("CHANGE PIN")
        }
      }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Biometrics",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text =
                if (biometricAvailable) {
                  "Use fingerprint or face unlock"
                } else {
                  "Biometrics unavailable"
                },
              style = MaterialTheme.typography.bodyLarge,
            )
            Text(
              text =
                if (biometricAvailable) {
                  "PIN remains available as a fallback."
                } else {
                  "This phone has no enrolled supported biometric method."
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Switch(
            checked = biometricEnabled,
            onCheckedChange = { enabled ->
              biometricEnabled = enabled
              securityStore.setBiometricEnabled(enabled)
            },
            enabled = biometricAvailable,
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
          text = "Auto-lock Parent app",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )

        Text(
          text = "Lock the Parent app after it has been in the background for:",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        listOf(
          0L to "Immediately",
          30_000L to "30 seconds",
          60_000L to "1 minute",
          300_000L to "5 minutes",
        ).forEach { (delay, label) ->
          if (relockDelay == delay) {
            Button(
              onClick = {
                relockDelay = delay
                securityStore.setRelockAfterBackgroundMillis(delay)
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(label)
            }
          } else {
            OutlinedButton(
              onClick = {
                relockDelay = delay
                securityStore.setRelockAfterBackgroundMillis(delay)
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(label)
            }
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
          text = "Notifications",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )

        Text(
          text =
            if (notificationsAllowed) {
              "Notifications are allowed."
            } else {
              "Notifications are currently blocked."
            },
          style = MaterialTheme.typography.bodyMedium,
          color =
            if (notificationsAllowed) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else {
              MaterialTheme.colorScheme.error
            },
        )

        Text(
          text = "Time requests from Child devices use these notifications.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
          onClick = {
            val intent =
              Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                data = Uri.parse("package:" + context.packageName)
              }
            context.startActivity(intent)
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("NOTIFICATION SETTINGS")
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))
  }
}

@Composable
private fun SecurityPinField(
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
    keyboardOptions =
      KeyboardOptions(
        keyboardType = KeyboardType.NumberPassword,
      ),
    modifier = Modifier.fillMaxWidth(),
  )
}

private fun String.parentSettingsPinDigits(): String =
  filter(Char::isDigit).take(6)
