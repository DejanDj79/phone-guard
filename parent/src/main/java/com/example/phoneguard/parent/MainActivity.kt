package com.example.phoneguard.parent

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.phoneguard.parent.ui.ParentDashboardScreen
import com.example.phoneguard.parent.ui.ParentSecurityGate

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

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
}
