package com.example.phoneguard.parent.ui

import android.util.Log
import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.phoneguard.parent.auth.ParentSupabase
import com.example.phoneguard.parent.data.HttpParentDeviceOwnershipGateway
import com.example.phoneguard.parent.data.ParentAccountScopeStore
import com.example.phoneguard.parent.data.ParentDeviceClaimResult
import com.example.phoneguard.parent.data.ParentSettingsStore
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ParentAuthGate(
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val accountScopeStore =
    remember(context) {
      ParentAccountScopeStore(context.applicationContext)
    }
  val ownershipGateway =
    remember {
      HttpParentDeviceOwnershipGateway()
    }
  val auth = ParentSupabase.client.auth
  val sessionStatus by auth.sessionStatus.collectAsStateWithLifecycle()

  when (val status = sessionStatus) {
    SessionStatus.Initializing -> {
      ParentAuthLoadingScreen("Restoring your account…")
    }

    is SessionStatus.RefreshFailure -> {
      ParentAuthLoadingScreen("Reconnecting to your account…")
    }

    is SessionStatus.NotAuthenticated -> {
      LaunchedEffect(status.isSignOut) {
        accountScopeStore.clearActiveAccount()
      }

      ParentOnboardingScreen()
    }

    is SessionStatus.Authenticated -> {
      val userId =
        status.session.user?.id
          ?: auth.currentUserOrNull()?.id

      if (userId.isNullOrBlank()) {
        ParentAuthLoadingScreen("Loading your account…")
      } else {
        val accessToken = status.session.accessToken
        var scopeReady by remember(userId) { mutableStateOf(false) }

        LaunchedEffect(userId, accessToken) {
          try {
            accountScopeStore.activate(userId)

            if (accessToken.isNotBlank()) {
              withContext(Dispatchers.IO) {
                val settingsStore =
                  ParentSettingsStore(context.applicationContext)

                settingsStore
                  .loadPairedDevices()
                  .filterNot { device ->
                    settingsStore.backendOwnershipConfirmed(device.deviceId)
                  }
                  .forEach { device ->
                    val controlToken =
                      settingsStore.controlToken(device.deviceId)
                        ?: return@forEach

                    when (
                      val result =
                        ownershipGateway.claim(
                          deviceId = device.deviceId,
                          controlToken = controlToken,
                          accessToken = accessToken,
                        )
                    ) {
                      ParentDeviceClaimResult.Success -> {
                        settingsStore.markBackendOwnershipConfirmed(
                          device.deviceId,
                        )
                      }

                      is ParentDeviceClaimResult.Error -> {
                        Log.w(
                          "PhoneGuardParent",
                          "Backend ownership claim failed for " +
                            device.deviceId +
                            ": " +
                            result.code,
                        )
                      }
                    }
                  }
              }
            }
          } finally {
            scopeReady = true
          }
        }

        if (scopeReady) {
          content()
        } else {
          ParentAuthLoadingScreen("Securing linked devices…")
        }
      }
    }
  }
}

@Composable
private fun ParentOnboardingScreen() {
  var screen by remember { mutableStateOf(ParentAuthScreen.WELCOME) }

  when (screen) {
    ParentAuthScreen.WELCOME -> {
      ParentWelcomeScreen(
        onContinueWithGoogle = {
          screen = ParentAuthScreen.GOOGLE
        },
        onContinueWithEmail = {
          screen = ParentAuthScreen.EMAIL
        },
      )
    }

    ParentAuthScreen.GOOGLE -> {
      ParentGoogleSignInScreen(
        onBack = { screen = ParentAuthScreen.WELCOME },
      )
    }

    ParentAuthScreen.EMAIL -> {
      ParentEmailAuthScreen(
        onBack = { screen = ParentAuthScreen.WELCOME },
      )
    }
  }
}

@Composable
private fun ParentWelcomeScreen(
  onContinueWithGoogle: () -> Unit,
  onContinueWithEmail: () -> Unit,
) {
  ParentAuthSurface {
    Text(
      text = "PhoneGuard",
      style = MaterialTheme.typography.displaySmall,
      fontWeight = FontWeight.Bold,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "Parental control that stays connected.",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.SemiBold,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text =
        "Create or sign in to your Parent account. Child phones are connected later with a secure pairing code.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(
      onClick = onContinueWithGoogle,
      modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
      Text("CONTINUE WITH GOOGLE")
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
      onClick = onContinueWithEmail,
      modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
      Text("CONTINUE WITH EMAIL")
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text =
        "Your Parent account is used to protect access and, later, restore your linked Child devices on another phone.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ParentGoogleSignInScreen(
  onBack: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var inProgress by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  ParentAuthSurface {
    Text(
      text = "Continue with Google",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Google will open securely in your browser. After sign-in, you'll return to PhoneGuard.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    errorMessage?.let { message ->
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
      onClick = {
        if (!inProgress) {
          scope.launch {
            inProgress = true
            errorMessage = null

            runCatching {
              ParentSupabase.client.auth.signInWith(Google)
            }.onFailure { error ->
              errorMessage =
                error.message ?: "Google sign-in could not be started."
            }

            inProgress = false
          }
        }
      },
      enabled = !inProgress,
      modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
      Text(
        if (inProgress) {
          "OPENING GOOGLE…"
        } else {
          "CONTINUE WITH GOOGLE"
        },
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
      onClick = onBack,
      enabled = !inProgress,
      modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
      Text("BACK")
    }
  }
}

@Composable
private fun ParentEmailAuthScreen(
  onBack: () -> Unit,
) {
  val scope = rememberCoroutineScope()

  var mode by remember { mutableStateOf(ParentEmailMode.SIGN_IN) }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmation by remember { mutableStateOf("") }
  var inProgress by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var noticeMessage by remember { mutableStateOf<String?>(null) }

  val emailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
  val passwordValid = password.length >= 6
  val confirmationValid =
    mode == ParentEmailMode.SIGN_IN ||
      (confirmation.length >= 6 && confirmation == password)

  ParentAuthSurface {
    Text(
      text = "Continue with email",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (mode == ParentEmailMode.SIGN_IN) {
        Button(
          onClick = { mode = ParentEmailMode.SIGN_IN },
          modifier = Modifier.weight(1f).heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
          Text("SIGN IN")
        }
      } else {
        OutlinedButton(
          onClick = {
            mode = ParentEmailMode.SIGN_IN
            errorMessage = null
            noticeMessage = null
          },
          modifier = Modifier.weight(1f).heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
          Text("SIGN IN")
        }
      }

      if (mode == ParentEmailMode.CREATE_ACCOUNT) {
        Button(
          onClick = { mode = ParentEmailMode.CREATE_ACCOUNT },
          modifier = Modifier.weight(1f).heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
          Text("CREATE")
        }
      } else {
        OutlinedButton(
          onClick = {
            mode = ParentEmailMode.CREATE_ACCOUNT
            errorMessage = null
            noticeMessage = null
          },
          modifier = Modifier.weight(1f).heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
          Text("CREATE")
        }
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    OutlinedTextField(
      value = email,
      onValueChange = {
        email = it.trim()
        errorMessage = null
        noticeMessage = null
      },
      label = { Text("Email") },
      singleLine = true,
      keyboardOptions =
        KeyboardOptions(
          keyboardType = KeyboardType.Email,
        ),
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
      value = password,
      onValueChange = {
        password = it
        errorMessage = null
        noticeMessage = null
      },
      label = { Text("Password") },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      keyboardOptions =
        KeyboardOptions(
          keyboardType = KeyboardType.Password,
        ),
      modifier = Modifier.fillMaxWidth(),
    )

    if (mode == ParentEmailMode.CREATE_ACCOUNT) {
      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = confirmation,
        onValueChange = {
          confirmation = it
          errorMessage = null
          noticeMessage = null
        },
        label = { Text("Confirm password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
          KeyboardOptions(
            keyboardType = KeyboardType.Password,
          ),
        modifier = Modifier.fillMaxWidth(),
      )
    }

    errorMessage?.let { message ->
      Spacer(modifier = Modifier.height(14.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }

    noticeMessage?.let { message ->
      Spacer(modifier = Modifier.height(14.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Spacer(modifier = Modifier.height(20.dp))

    Button(
      onClick = {
        if (!inProgress) {
          scope.launch {
            inProgress = true
            errorMessage = null
            noticeMessage = null

            val result =
              runCatching {
                when (mode) {
                  ParentEmailMode.SIGN_IN -> {
                    ParentSupabase.client.auth.signInWith(Email) {
                      this.email = email.trim()
                      this.password = password
                    }
                  }

                  ParentEmailMode.CREATE_ACCOUNT -> {
                    ParentSupabase.client.auth.signUpWith(
                      provider = Email,
                      redirectUrl = ParentSupabase.REDIRECT_URL,
                    ) {
                      this.email = email.trim()
                      this.password = password
                    }
                  }
                }
              }

            result.onSuccess {
              if (
                mode == ParentEmailMode.CREATE_ACCOUNT &&
                ParentSupabase.client.auth.currentSessionOrNull() == null
              ) {
                noticeMessage =
                  "Account created. Check your email to confirm it, then return to PhoneGuard."
              }
            }.onFailure { error ->
              errorMessage =
                parentAuthErrorMessage(error)
            }

            inProgress = false
          }
        }
      },
      enabled =
        !inProgress &&
          emailValid &&
          passwordValid &&
          confirmationValid,
      modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
      Text(
        when {
          inProgress -> "PLEASE WAIT…"
          mode == ParentEmailMode.SIGN_IN -> "SIGN IN"
          else -> "CREATE ACCOUNT"
        },
      )
    }

    if (!passwordValid && password.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "Password must contain at least 6 characters.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
      onClick = onBack,
      enabled = !inProgress,
      modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).heightIn(min = 52.dp),
      shape = ParentActionShape,
    ) {
      Text("BACK")
    }
  }
}

@Composable
private fun ParentAuthLoadingScreen(
  message: String,
) {
  ParentAuthSurface {
    CircularProgressIndicator()

    Spacer(modifier = Modifier.height(20.dp))

    Text(
      text = message,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ParentAuthSurface(
  content: @Composable () -> Unit,
) {
  Surface(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(horizontal = 28.dp, vertical = 40.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      content()
    }
  }
}

private fun parentAuthErrorMessage(error: Throwable): String {
  val raw = error.message.orEmpty()

  return when {
    raw.contains("invalid login credentials", ignoreCase = true) ->
      "Email or password is incorrect."
    raw.contains("email not confirmed", ignoreCase = true) ->
      "Confirm your email address before signing in."
    raw.contains("user already registered", ignoreCase = true) ->
      "An account already exists for this email. Try signing in."
    raw.isNotBlank() -> raw
    else -> "Authentication failed. Please try again."
  }
}

private enum class ParentAuthScreen {
  WELCOME,
  GOOGLE,
  EMAIL,
}

private enum class ParentEmailMode {
  SIGN_IN,
  CREATE_ACCOUNT,
}
