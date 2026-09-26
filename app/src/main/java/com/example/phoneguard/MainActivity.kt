package com.example.phoneguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.example.phoneguard.remote.RemoteCommandSyncer
import com.example.phoneguard.theme.ChildGradientCenter
import com.example.phoneguard.theme.ChildGradientEdge
import com.example.phoneguard.theme.PhoneGuardTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    Thread {
      RemoteCommandSyncer(applicationContext).sync()
    }.start()

    enableEdgeToEdge()
    setContent {
      PhoneGuardTheme {
        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .background(
                Brush.horizontalGradient(
                  colors =
                    listOf(
                      ChildGradientEdge,
                      ChildGradientCenter,
                      ChildGradientEdge,
                    ),
                ),
              ),
        ) {
          Box(
            modifier =
              Modifier
                .fillMaxSize()
                .statusBarsPadding(),
          ) {
            MainNavigation()
          }
        }
      }
    }
  }
}
