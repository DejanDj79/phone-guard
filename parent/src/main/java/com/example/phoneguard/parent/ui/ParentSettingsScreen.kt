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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.phoneguard.parent.auth.ParentSupabase
import com.example.phoneguard.parent.data.ParentAccountScopeStore
import com.example.phoneguard.parent.data.ParentNotificationSettingsStore
import com.example.phoneguard.parent.data.ParentSecurityStore
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun ParentSettingsScreen(
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val accountScopeStore =
    remember(context) {
      ParentAccountScopeStore(context.applicationContext)
    }
  val securityStore =
    remember(context) {
      ParentSecurityStore(context.applicationContext)
    }
  val notificationSettingsStore =
    remember(context) {
      ParentNotificationSettingsStore(context.applicationContext)
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
  var timeRequestNotificationsEnabled by remember {
    mutableStateOf(
      notificationSettingsStore.timeRequestNotificationsEnabled(),
    )
  }
  var protectionAlertNotificationsEnabled by remember {
    mutableStateOf(
      notificationSettingsStore.protectionAlertNotificationsEnabled(),
    )
  }

  var currentPin by remember { mutableStateOf("") }
  var newPin by remember { mutableStateOf("") }
  var confirmPin by remember { mutableStateOf("") }
  var pinNotice by remember { mutableStateOf<String?>(null) }
  var pinError by remember { mutableStateOf<String?>(null) }
  var signOutInProgress by remember { mutableStateOf(false) }
  var accountError by remember { mutableStateOf<String?>(null) }

  val parentEmail =
    ParentSupabase.client.auth.currentUserOrNull()?.email
      ?: "Signed in Parent account"

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
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Parent account",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = parentEmail,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        accountError?.let { message ->
          Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        TextButton(
          onClick = {
            if (!signOutInProgress) {
              scope.launch {
                signOutInProgress = true
                accountError = null

                runCatching {
                  ParentSupabase.client.auth.signOut()
                }.onSuccess {
                  accountScopeStore.clearActiveAccount()
                }.onFailure { error ->
                  accountError =
                    error.message ?: "Could not sign out. Please try again."
                }

                signOutInProgress = false
              }
            }
          },
          enabled = !signOutInProgress,
          modifier = Modifier.fillMaxWidth(),
        ) {
          if (signOutInProgress) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
            )
          } else {
            Text("SIGN OUT")
          }
        }
      }
    }

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "Change Parent PIN",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
        }

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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        pinNotice?.let { message ->
          Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        OutlinedButton(
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

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "App lock",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
        }

        SettingSwitchRow(
          title =
            if (biometricAvailable) {
              "Biometric unlock"
            } else {
              "Biometrics unavailable"
            },
          description =
            if (biometricAvailable) {
              "Use fingerprint or face unlock. PIN remains available."
            } else {
              "No supported biometric method is enrolled on this phone."
            },
          checked = biometricEnabled,
          enabled = biometricAvailable,
          onCheckedChange = { enabled ->
            biometricEnabled = enabled
            securityStore.setBiometricEnabled(enabled)
          },
        )

        Text(
          text = "LOCK AFTER BACKGROUND",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        listOf(
          0L to "Immediately",
          30_000L to "30 sec",
          60_000L to "1 min",
          300_000L to "5 min",
        ).chunked(2).forEach { row ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            row.forEach { (delay, label) ->
              if (relockDelay == delay) {
                Button(
                  onClick = {
                    relockDelay = delay
                    securityStore.setRelockAfterBackgroundMillis(delay)
                  },
                  modifier = Modifier.weight(1f),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = ParentAccentColor,
                      contentColor = Color.White,
                    ),
                ) {
                  Text(label)
                }
              } else {
                OutlinedButton(
                  onClick = {
                    relockDelay = delay
                    securityStore.setRelockAfterBackgroundMillis(delay)
                  },
                  modifier = Modifier.weight(1f),
                ) {
                  Text(label)
                }
              }
            }
          }
        }
      }
    }

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Notifications",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text =
                if (notificationsAllowed) {
                  "Notifications allowed"
                } else {
                  "Notifications blocked by Android"
                },
              style = MaterialTheme.typography.bodySmall,
              color =
                if (notificationsAllowed) {
                  MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                  MaterialTheme.colorScheme.error
                },
            )
          }
        }

        SettingSwitchRow(
          title = "Time requests",
          description = "When a Child asks for more screen time.",
          checked = timeRequestNotificationsEnabled,
          onCheckedChange = { enabled ->
            timeRequestNotificationsEnabled = enabled
            notificationSettingsStore
              .setTimeRequestNotificationsEnabled(enabled)
          },
        )

        SettingSwitchRow(
          title = "Protection alerts",
          description = "Protection changes and bypass attempts.",
          checked = protectionAlertNotificationsEnabled,
          onCheckedChange = { enabled ->
            protectionAlertNotificationsEnabled = enabled
            notificationSettingsStore
              .setProtectionAlertNotificationsEnabled(enabled)
          },
        )

        TextButton(
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
          Text("ANDROID NOTIFICATION SETTINGS")
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))
  }
}

@Composable
private fun SettingSwitchRow(
  title: String,
  description: String,
  checked: Boolean,
  enabled: Boolean = true,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = Color.White,
          checkedTrackColor = ParentAccentColor,
        ),
    )
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
