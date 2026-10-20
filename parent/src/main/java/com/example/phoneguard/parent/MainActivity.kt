package com.example.phoneguard.parent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.phoneguard.parent.auth.ParentSupabase
import com.example.phoneguard.parent.data.ParentSettingsStore
import com.example.phoneguard.parent.push.ParentMessagingService
import com.example.phoneguard.parent.push.ParentPushRegistrar
import com.example.phoneguard.parent.ui.ParentAuthGate
import com.example.phoneguard.parent.ui.ParentDashboardScreen
import com.example.phoneguard.parent.ui.ParentSecurityGate
import com.example.phoneguard.parent.ui.ParentPostAuthOnboardingGate
import com.example.phoneguard.parent.ui.ParentGradientCenter
import com.example.phoneguard.parent.ui.ParentGradientEdge
import com.example.phoneguard.parent.ui.PhoneGuardParentTheme
import com.google.firebase.FirebaseApp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : FragmentActivity() {
  private var pendingGeofenceEventId by mutableStateOf<String?>(null)
  private var pendingGeofenceDeviceId by mutableStateOf<String?>(null)

  private val notificationPermissionLauncher =
    registerForActivityResult(
      ActivityResultContracts.RequestPermission(),
    ) { }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    ParentSupabase.client.handleDeeplinks(intent)
    selectRequestedDevice(intent)
    readGeofenceNotification(intent)
    enableEdgeToEdge()
    hideSystemNavigation()
    setContent {
      PhoneGuardParentTheme {
        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .background(
                Brush.horizontalGradient(
                  colorStops =
                    arrayOf(
                      0.0f to ParentGradientEdge,
                      0.50f to ParentGradientCenter,
                      1.0f to ParentGradientEdge,
                    ),
                ),
              ),
        ) {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
          ) {
            ParentAuthGate {
              ParentSecurityGate {
                ParentPostAuthOnboardingGate {
                  ParentDashboardScreen(
                    geofenceNotificationEventId = pendingGeofenceEventId,
                    geofenceNotificationDeviceId = pendingGeofenceDeviceId,
                    onGeofenceNotificationHandled = {
                      pendingGeofenceEventId = null
                      pendingGeofenceDeviceId = null
                      intent?.removeExtra(ParentMessagingService.EXTRA_GEOFENCE_EVENT_ID)
                      intent?.removeExtra(ParentMessagingService.EXTRA_GEOFENCE_DEVICE_ID)
                    },
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()

    requestNotificationPermissionIfNeeded()

    if (
      FirebaseApp.getApps(this).isNotEmpty() &&
      ParentSupabase.client.auth.currentSessionOrNull() != null
    ) {
      ParentPushRegistrar(applicationContext).registerCurrentToken()
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)

    if (hasFocus) {
      hideSystemNavigation()
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    ParentSupabase.client.handleDeeplinks(intent)
    readGeofenceNotification(intent)

    if (selectRequestedDevice(intent)) {
      recreate()
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

    val preferences =
      getSharedPreferences(
        NOTIFICATION_PERMISSION_PREFERENCES,
        MODE_PRIVATE,
      )

    if (preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)) {
      return
    }

    preferences
      .edit()
      .putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true)
      .apply()

    notificationPermissionLauncher.launch(
      Manifest.permission.POST_NOTIFICATIONS,
    )
  }

  private fun hideSystemNavigation() {
    WindowInsetsControllerCompat(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.navigationBars())
      systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
  }

  private fun readGeofenceNotification(intent: Intent?) {
    pendingGeofenceEventId =
      intent
        ?.getStringExtra(ParentMessagingService.EXTRA_GEOFENCE_EVENT_ID)
        ?.takeIf { it.isNotBlank() }
    pendingGeofenceDeviceId =
      intent
        ?.getStringExtra(ParentMessagingService.EXTRA_GEOFENCE_DEVICE_ID)
        ?.takeIf { it.isNotBlank() }
  }

  private fun selectRequestedDevice(intent: Intent?): Boolean {
    val deviceId =
      intent
        ?.getStringExtra(ParentMessagingService.EXTRA_GEOFENCE_DEVICE_ID)
        ?.takeIf { it.isNotBlank() }
        ?: intent
          ?.getStringExtra(ParentMessagingService.EXTRA_TIME_REQUEST_DEVICE_ID)
          ?.takeIf { it.isNotBlank() }
        ?: return false

    return ParentSettingsStore(applicationContext).selectDevice(deviceId)
  }

  private companion object {
    const val NOTIFICATION_PERMISSION_PREFERENCES =
      "phone_guard_parent_permissions"
    const val KEY_NOTIFICATION_PERMISSION_REQUESTED =
      "notification_permission_requested"
  }
}
