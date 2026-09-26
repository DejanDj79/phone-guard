package com.example.phoneguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.phoneguard.remote.RemoteCommandSyncer
import com.example.phoneguard.theme.PhoneGuardTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    Thread {
      RemoteCommandSyncer(applicationContext).sync()
    }.start()

    enableEdgeToEdge()
    setContent {
      PhoneGuardTheme { Surface(modifier = Modifier.fillMaxSize().statusBarsPadding(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }
}
