package com.example.phoneguard.parent

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.phoneguard.parent.auth.ParentSupabase
import com.example.phoneguard.parent.data.ParentSettingsStore
import com.example.phoneguard.parent.push.ParentMessagingService
import com.example.phoneguard.parent.push.ParentPushRegistrar
import com.example.phoneguard.parent.ui.ParentAuthGate
import com.example.phoneguard.parent.ui.ParentDashboardScreen
import com.example.phoneguard.parent.ui.ParentSecurityGate
import com.example.phoneguard.parent.ui.ParentPostAuthOnboardingGate
import com.google.firebase.FirebaseApp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    ParentSupabase.client.handleDeeplinks(intent)
    selectRequestedDevice(intent)
    enableEdgeToEdge()
    setContent {
      MaterialTheme {
        Surface {
          ParentAuthGate {
            ParentSecurityGate {
              ParentPostAuthOnboardingGate {
                ParentDashboardScreen()
              }
            }
          }
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()

    if (
      FirebaseApp.getApps(this).isNotEmpty() &&
      ParentSupabase.client.auth.currentSessionOrNull() != null
    ) {
      ParentPushRegistrar(applicationContext).registerCurrentToken()
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    ParentSupabase.client.handleDeeplinks(intent)

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

}
