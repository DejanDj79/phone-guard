package com.example.phoneguard.parent.ui

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.phoneguard.parent.data.ParentSecurityStore

@Composable
fun ParentSecurityGate(
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val securityStore =
    remember(context) {
      ParentSecurityStore(context.applicationContext)
    }

  var hasPin by remember { mutableStateOf(securityStore.hasPin()) }
  var unlocked by remember { mutableStateOf(false) }
  var backgroundedAt by remember { mutableStateOf<Long?>(null) }
  var biometricEnabled by remember {
    mutableStateOf(securityStore.biometricEnabled())
  }
  var relockAfterBackgroundMs by remember {
    mutableStateOf(securityStore.relockAfterBackgroundMillis())
  }

  val fragmentActivity =
    remember(context) {
      context.findFragmentActivity()
    }
  val biometricAuthenticators =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
      BiometricManager.Authenticators.BIOMETRIC_WEAK
  val biometricAvailable =
    remember(context, fragmentActivity) {
      fragmentActivity != null &&
        BiometricManager.from(context)
          .canAuthenticate(biometricAuthenticators) ==
        BiometricManager.BIOMETRIC_SUCCESS
    }
  val biometricPrompt =
    remember(fragmentActivity) {
      fragmentActivity?.let { activity ->
        BiometricPrompt(
          activity,
          ContextCompat.getMainExecutor(activity),
          object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
              result: BiometricPrompt.AuthenticationResult,
            ) {
              super.onAuthenticationSucceeded(result)
              unlocked = true
            }
          },
        )
      }
    }
  val biometricPromptInfo =
    remember {
      BiometricPrompt.PromptInfo.Builder()
        .setTitle("PhoneGuard Parent")
        .setSubtitle("Confirm your identity to unlock")
        .setAllowedAuthenticators(biometricAuthenticators)
        .setNegativeButtonText("Use PIN")
        .build()
    }

  fun showBiometricPrompt() {
    biometricPrompt?.authenticate(biometricPromptInfo)
  }

  DisposableEffect(lifecycleOwner) {
    val observer =
      LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_STOP -> {
            backgroundedAt = SystemClock.elapsedRealtime()
          }

          Lifecycle.Event.ON_START -> {
            biometricEnabled = securityStore.biometricEnabled()
            relockAfterBackgroundMs = securityStore.relockAfterBackgroundMillis()

            val leftAt = backgroundedAt
            if (
              hasPin &&
              leftAt != null &&
              SystemClock.elapsedRealtime() - leftAt >= relockAfterBackgroundMs
            ) {
              unlocked = false
            }
            backgroundedAt = null
          }

          else -> Unit
        }
      }

    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  when {
    !hasPin -> {
      ParentPinSetupScreen(
        onPinSet = { pin ->
          securityStore.setPin(pin)
          hasPin = true
          unlocked = true
        },
      )
    }

    !unlocked -> {
      ParentPinUnlockScreen(
        onUnlock = securityStore::verifyPin,
        onUnlocked = { unlocked = true },
        biometricAvailable = biometricEnabled && biometricAvailable,
        onBiometricUnlock = {
          showBiometricPrompt()
        },
      )
    }

    else -> content()
  }
}

@Composable
private fun ParentPinSetupScreen(
  onPinSet: (String) -> Unit,
) {
  var pin by remember { mutableStateOf("") }
  var confirmation by remember { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  ParentSecuritySurface(
    title = "Protect Parent app",
    subtitle =
      "Create a 4–6 digit PIN. It protects access to Child controls on this phone.",
  ) {
    ParentPinField(
      value = pin,
      onValueChange = {
        pin = it.parentPinDigits()
        errorMessage = null
      },
      label = "Parent app PIN",
    )

    Spacer(modifier = Modifier.height(12.dp))

    ParentPinField(
      value = confirmation,
      onValueChange = {
        confirmation = it.parentPinDigits()
        errorMessage = null
      },
      label = "Confirm PIN",
    )

    errorMessage?.let { message ->
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }

    Spacer(modifier = Modifier.height(20.dp))

    OutlinedButton(
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
      enabled = pin.length in 4..6 && confirmation.length in 4..6,
      modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
      Text("SAVE PIN")
    }
  }
}

@Composable
private fun ParentPinUnlockScreen(
  onUnlock: (String) -> Boolean,
  onUnlocked: () -> Unit,
  biometricAvailable: Boolean,
  onBiometricUnlock: () -> Unit,
) {
  var pin by remember { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  ParentSecuritySurface(
    title = if (biometricAvailable) "Welcome back" else "Parent access",
    subtitle =
      if (biometricAvailable) {
        "Unlock PhoneGuard Parent to continue."
      } else {
        "Enter your Parent PIN to continue."
      },
    leadingContent = {
      Surface(
        modifier = Modifier.size(78.dp),
        shape = CircleShape,
        color =
          if (biometricAvailable) {
            ParentAccentColor.copy(alpha = 0.10f)
          } else {
            MaterialTheme.colorScheme.surfaceVariant
          },
        border =
          BorderStroke(
            width = 1.dp,
            color =
              if (biometricAvailable) {
                ParentAccentColor.copy(alpha = 0.30f)
              } else {
                MaterialTheme.colorScheme.outlineVariant
              },
          ),
      ) {
        Box(
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector =
              if (biometricAvailable) {
                Icons.Rounded.Fingerprint
              } else {
                Icons.Rounded.Lock
              },
            contentDescription = null,
            tint =
              if (biometricAvailable) {
                ParentAccentColor
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
            modifier = Modifier.size(36.dp),
          )
        }
      }
    },
  ) {
    if (biometricAvailable) {
      OutlinedButton(
        onClick = onBiometricUnlock,
        modifier =
          Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .heightIn(min = 52.dp),
        shape = ParentActionShape,
        border =
          BorderStroke(
            width = 1.dp,
            color = ParentAccentColor,
          ),
      ) {
        Icon(
          imageVector = Icons.Rounded.Fingerprint,
          contentDescription = null,
          tint = ParentAccentColor,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = "  UNLOCK WITH BIOMETRICS",
          color = ParentAccentColor,
        )
      }

      Spacer(modifier = Modifier.height(22.dp))

      Text(
        text = "OR USE PARENT PIN",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
          Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally),
      )

      Spacer(modifier = Modifier.height(14.dp))
    }

    ParentPinField(
      value = pin,
      onValueChange = {
        pin = it.parentPinDigits()
        errorMessage = null
      },
      label = "Parent PIN",
    )

    errorMessage?.let { message ->
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }

    Spacer(modifier = Modifier.height(18.dp))

    OutlinedButton(
      onClick = {
        if (onUnlock(pin)) {
          pin = ""
          errorMessage = null
          onUnlocked()
        } else {
          pin = ""
          errorMessage = "Incorrect PIN."
        }
      },
      enabled = pin.length in 4..6,
      modifier =
        Modifier
          .fillMaxWidth()
          .wrapContentWidth(Alignment.CenterHorizontally)
          .heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
      Text(if (biometricAvailable) "UNLOCK WITH PIN" else "UNLOCK")
    }
  }
}

@Composable
private fun ParentSecuritySurface(
  title: String,
  subtitle: String,
  leadingContent: (@Composable () -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = Color.Transparent,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(horizontal = 22.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = "PHONEGUARD",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(18.dp))

      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 10.dp,
        tonalElevation = 0.dp,
        border =
          BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
          ),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          leadingContent?.invoke()

          if (leadingContent != null) {
            Spacer(modifier = Modifier.height(20.dp))
          }

          Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Spacer(modifier = Modifier.height(24.dp))

          Column(
            modifier = Modifier.fillMaxWidth(),
          ) {
            content()
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Protected Parent access",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ParentPinField(
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

private fun String.parentPinDigits(): String =
  filter(Char::isDigit).take(6)


private fun Context.findFragmentActivity(): FragmentActivity? {
  var current = this

  while (current is ContextWrapper) {
    if (current is FragmentActivity) return current
    current = current.baseContext
  }

  return current as? FragmentActivity
}
