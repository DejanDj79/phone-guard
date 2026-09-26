package com.example.phoneguard.ui.main

import android.content.Intent
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.phoneguard.remote.ChildTimeRequestResult
import kotlinx.coroutines.launch

private val LockAccent = Color(0xFFE83E1D)
private val LockGradientEdge = Color(0xFFDEDEDA)
private val LockGradientCenter = Color(0xFFF3F3F0)
private val LockActionShape = RoundedCornerShape(12.dp)

private data class AllowedLockApp(
  val label: String,
  val launchIntent: Intent,
  val icon: Drawable,
)

@Composable
internal fun StyledLockScreen(
  allowedPackages: Set<String>,
  timeRequestFeedback: String?,
  unlockTimeLabel: String?,
  dailyLimitReached: Boolean,
  onRequestMoreTime: suspend (Int) -> ChildTimeRequestResult,
  onUnlock: (String) -> Boolean,
  modifier: Modifier = Modifier,
) {
  var pin by remember { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var showTimeRequest by remember { mutableStateOf(false) }
  var timeRequestInProgress by remember { mutableStateOf(false) }
  var timeRequestMessage by remember { mutableStateOf<String?>(null) }
  val requestScope = rememberCoroutineScope()
  val context = LocalContext.current
  val packageManager = context.packageManager
  val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
  val cameraManager = remember(context) { context.getSystemService(CameraManager::class.java) }
  val torchCameraId = remember(cameraManager) {
    runCatching {
      cameraManager.cameraIdList.firstOrNull { id ->
        cameraManager.getCameraCharacteristics(id)
          .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
      }
    }.getOrNull()
  }
  var flashlightEnabled by remember { mutableStateOf(false) }
  var ringerMode by remember { mutableStateOf(audioManager.ringerMode) }
  var systemMessage by remember { mutableStateOf<String?>(null) }

  val allowedApps = remember(allowedPackages) {
    allowedPackages.mapNotNull { packageName ->
      val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return@mapNotNull null
      runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        AllowedLockApp(
          label = packageManager.getApplicationLabel(info).toString(),
          launchIntent = launchIntent,
          icon = packageManager.getApplicationIcon(info),
        )
      }.getOrNull()
    }.sortedBy { it.label.lowercase() }
  }

  Box(
    modifier = modifier.fillMaxSize().background(
      Brush.horizontalGradient(
        colorStops = arrayOf(
          0f to LockGradientEdge,
          0.18f to Color(0xFFE7E7E3),
          0.5f to LockGradientCenter,
          0.82f to Color(0xFFE7E7E3),
          1f to LockGradientEdge,
        ),
      ),
    ),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 36.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(Modifier.height(18.dp))
      Surface(shape = CircleShape, color = Color(0xFFF6F6F3), shadowElevation = 1.dp) {
        Box(Modifier.size(82.dp), contentAlignment = Alignment.Center) {
          Icon(Icons.Outlined.Lock, contentDescription = null, tint = LockAccent, modifier = Modifier.size(38.dp))
        }
      }

      Spacer(Modifier.height(18.dp))
      Text(
        text = "LOCKED",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = Color(0xFF303132),
      )

      if (unlockTimeLabel != null) {
        Spacer(Modifier.height(10.dp))
        Text("AVAILABLE AGAIN AT", style = MaterialTheme.typography.labelMedium, color = Color(0xFF7F8082))
        Text(unlockTimeLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF303132))
      } else if (dailyLimitReached) {
        Spacer(Modifier.height(10.dp))
        Text("DAILY LIMIT REACHED", style = MaterialTheme.typography.labelMedium, color = Color(0xFF7F8082))
      }

      if (allowedApps.isNotEmpty()) {
        Spacer(Modifier.height(28.dp))
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.Center,
        ) {
          allowedApps.forEach { app ->
            Column(
              modifier = Modifier.width(72.dp).clickable {
                runCatching { context.startActivity(Intent(app.launchIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                  .onFailure { systemMessage = "This app could not be opened." }
              },
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Image(
                bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)),
              )
            }
          }
        }
      }

      Spacer(Modifier.height(28.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        LockRoundAction(
          icon = Icons.Outlined.Notifications,
          selected = ringerMode == AudioManager.RINGER_MODE_NORMAL,
          contentDescription = "Sound",
          onClick = {
            runCatching { audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL }
              .onSuccess { ringerMode = audioManager.ringerMode; systemMessage = null }
              .onFailure { systemMessage = "Android did not allow this sound change." }
          },
        )
        LockRoundAction(
          icon = Icons.Outlined.Vibration,
          selected = ringerMode == AudioManager.RINGER_MODE_VIBRATE,
          contentDescription = "Vibrate",
          onClick = {
            runCatching { audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
              .onSuccess { ringerMode = audioManager.ringerMode; systemMessage = null }
              .onFailure { systemMessage = "Android did not allow this sound change." }
          },
        )
        LockRoundAction(
          icon = if (flashlightEnabled) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
          selected = flashlightEnabled,
          enabled = torchCameraId != null,
          contentDescription = "Flashlight",
          onClick = {
            val id = torchCameraId ?: return@LockRoundAction
            val next = !flashlightEnabled
            runCatching { cameraManager.setTorchMode(id, next) }
              .onSuccess { flashlightEnabled = next; systemMessage = null }
              .onFailure { systemMessage = "Flashlight is currently unavailable." }
          },
        )
      }

      systemMessage?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF7F8082), textAlign = TextAlign.Center)
      }

      Spacer(Modifier.height(30.dp))
      Button(
        onClick = { showTimeRequest = true; timeRequestMessage = null },
        enabled = !timeRequestInProgress,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = LockActionShape,
        colors = ButtonDefaults.buttonColors(containerColor = LockAccent, contentColor = Color.White),
      ) {
        Text(if (timeRequestInProgress) "SENDING REQUEST…" else "REQUEST MORE TIME", style = MaterialTheme.typography.labelLarge)
      }

      timeRequestMessage?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF7F8082), textAlign = TextAlign.Center)
      }
      timeRequestFeedback?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
      }

      Spacer(Modifier.height(18.dp))
      OutlinedTextField(
        value = pin,
        onValueChange = { pin = it.filter(Char::isDigit).take(6); errorMessage = null },
        label = { Text("Parent PIN") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
        shape = LockActionShape,
      )

      errorMessage?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
      }

      Spacer(Modifier.height(12.dp))
      OutlinedButton(
        onClick = {
          if (onUnlock(pin)) {
            pin = ""
            errorMessage = null
          } else {
            pin = ""
            errorMessage = "Incorrect PIN."
          }
        },
        enabled = pin.length in 4..6,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = LockActionShape,
      ) {
        Text("UNLOCK", style = MaterialTheme.typography.labelLarge)
      }
      Spacer(Modifier.height(20.dp))
    }
  }

  if (showTimeRequest) {
    StyledRequestMoreTimeDialog(
      onDismiss = { showTimeRequest = false },
      onConfirm = { minutes ->
        showTimeRequest = false
        requestScope.launch {
          timeRequestInProgress = true
          timeRequestMessage = null
          timeRequestMessage = when (val result = onRequestMoreTime(minutes)) {
            is ChildTimeRequestResult.Success -> when {
              result.alreadyPending -> "A request is already waiting for Parent approval."
              result.pushSent -> "Request sent to Parent."
              else -> "Request sent. Parent will see it in PhoneGuard."
            }
            is ChildTimeRequestResult.Failure -> result.message
          }
          timeRequestInProgress = false
        }
      },
    )
  }
}

@Composable
private fun LockRoundAction(
  icon: ImageVector,
  selected: Boolean,
  contentDescription: String,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  IconButton(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.size(56.dp),
    colors = IconButtonDefaults.iconButtonColors(
      containerColor = if (selected) LockAccent else Color(0xFFF6F6F3),
      contentColor = if (selected) Color.White else Color(0xFF3C3C3A),
      disabledContainerColor = Color(0xFFE9E9E5),
      disabledContentColor = Color(0xFFAAAAA6),
    ),
  ) {
    Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(25.dp))
  }
}

@Composable
private fun StyledRequestMoreTimeDialog(
  onDismiss: () -> Unit,
  onConfirm: (Int) -> Unit,
) {
  var selectedMinutes by remember { mutableStateOf(15) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Request more time", fontWeight = FontWeight.SemiBold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How much extra time would you like to ask for?", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(5, 15, 30).forEach { minutes ->
            if (selectedMinutes == minutes) {
              Button(
                onClick = { selectedMinutes = minutes },
                modifier = Modifier.weight(1f),
                shape = LockActionShape,
                colors = ButtonDefaults.buttonColors(containerColor = LockAccent),
              ) { Text("$minutes min") }
            } else {
              OutlinedButton(onClick = { selectedMinutes = minutes }, modifier = Modifier.weight(1f), shape = LockActionShape) {
                Text("$minutes min")
              }
            }
          }
        }
      }
    },
    confirmButton = { Button(onClick = { onConfirm(selectedMinutes) }, shape = LockActionShape, colors = ButtonDefaults.buttonColors(containerColor = LockAccent)) { Text("SEND REQUEST") } },
    dismissButton = { OutlinedButton(onClick = onDismiss, shape = LockActionShape) { Text("CANCEL") } },
  )
}
