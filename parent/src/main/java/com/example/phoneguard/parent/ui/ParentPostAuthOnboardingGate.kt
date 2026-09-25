package com.example.phoneguard.parent.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.phoneguard.parent.data.ParentOnboardingStore

@Composable
fun ParentPostAuthOnboardingGate(
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val store =
    remember(context) {
      ParentOnboardingStore(context.applicationContext)
    }

  var notificationStepCompleted by remember {
    mutableStateOf(store.notificationStepCompleted())
  }

  val notificationsGranted =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED

  LaunchedEffect(notificationsGranted) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      notificationsGranted &&
      !notificationStepCompleted
    ) {
      store.completeNotificationStep()
      notificationStepCompleted = true
    }
  }

  if (notificationStepCompleted) {
    content()
  } else {
    ParentNotificationOnboardingScreen(
      onComplete = {
        store.completeNotificationStep()
        notificationStepCompleted = true
      },
    )
  }
}

@Composable
private fun ParentNotificationOnboardingScreen(
  onComplete: () -> Unit,
) {
  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
    ) {
      // The Parent app remains usable if permission is denied.
      // The setting can be changed later from Parent Settings.
      onComplete()
    }

  Surface(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(horizontal = 28.dp, vertical = 40.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = "Stay informed",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text =
          "PhoneGuard can notify you when your child requests more time or when an important protection setting changes.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text =
          "You can change notification categories later in Parent Settings.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(28.dp))

      OutlinedButton(
        onClick = {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          } else {
            onComplete()
          }
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("ENABLE NOTIFICATIONS")
      }

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedButton(
        onClick = onComplete,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("NOT NOW")
      }
    }
  }
}
