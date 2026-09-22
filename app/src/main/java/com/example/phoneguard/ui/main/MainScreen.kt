package com.example.phoneguard.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.phoneguard.data.ChildSettingsStore
import com.example.phoneguard.theme.PhoneGuardTheme

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val settingsStore = remember(context) { ChildSettingsStore(context.applicationContext) }

  var hasParentPin by remember { mutableStateOf(settingsStore.hasParentPin()) }
  var isLocked by remember { mutableStateOf(settingsStore.isLocked()) }

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
        onUnlock = { pin ->
          val accepted = settingsStore.verifyParentPin(pin)
          if (accepted) {
            settingsStore.setLocked(false)
            isLocked = false
          }
          accepted
        },
      )
    }

    else -> {
      ChildDashboard(
        modifier = modifier,
        onTestLock = {
          settingsStore.setLocked(true)
          isLocked = true
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
  onTestLock: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    Text(
      text = "PhoneGuard",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
    )

    Text(
      text = "Child device",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Protection",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "● Local prototype ready",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Schedule",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "No schedule configured",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }

    Spacer(modifier = Modifier.weight(1f))

    Button(
      onClick = onTestLock,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("TEST LOCK")
    }
  }
}

@Composable
private fun LockScreen(
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

      Text(
        text = "Ponovo dostupno u",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Text(
        text = "07:00",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
      )

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
    ChildDashboard(onTestLock = {})
  }
}

@Preview(showBackground = true)
@Composable
private fun LockScreenPreview() {
  PhoneGuardTheme {
    LockScreen(onUnlock = { false })
  }
}
