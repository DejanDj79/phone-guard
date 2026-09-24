package com.example.phoneguard.parent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.compose.material3.Surface
import com.example.phoneguard.parent.data.ParentSettingsStore
import com.example.phoneguard.parent.push.ParentMessagingService
import com.example.phoneguard.parent.push.ParentPushRegistrar
import com.example.phoneguard.parent.ui.ParentDashboardScreen
import com.example.phoneguard.parent.ui.ParentSecurityGate
import com.google.firebase.FirebaseApp

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    selectRequestedDevice(intent)
    enableEdgeToEdge()
    setContent {
      MaterialTheme {
        Surface {
          ParentSecurityGate {
            ParentDashboardScreen()
          }
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()

    requestNotificationPermissionIfNeeded()

    if (FirebaseApp.getApps(this).isNotEmpty()) {
      ParentPushRegistrar(applicationContext).registerCurrentToken()
    }
  }

  private fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    if (
      ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
    ) {
      return
    }

    requestPermissions(
      arrayOf(Manifest.permission.POST_NOTIFICATIONS),
      NOTIFICATION_PERMISSION_REQUEST_CODE,
    )
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)

    if (selectRequestedDevice(intent)) {
      recreate()
    }
  }

  private fun selectRequestedDevice(intent: Intent?): Boolean {
    val deviceId =
      intent
        ?.getStringExtra(ParentMessagingService.EXTRA_TIME_REQUEST_DEVICE_ID)
        ?.takeIf { it.isNotBlank() }
        ?: return false

    return ParentSettingsStore(applicationContext).selectDevice(deviceId)
  }

  private companion object {
    const val NOTIFICATION_PERMISSION_REQUEST_CODE = 4101
  }
}
