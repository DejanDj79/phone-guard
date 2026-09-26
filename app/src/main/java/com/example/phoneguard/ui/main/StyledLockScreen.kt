package com.example.phoneguard.ui.main

import android.content.Intent
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.phoneguard.R
import com.example.phoneguard.remote.ChildTimeRequestResult
import com.example.phoneguard.theme.ChildAccent
import com.example.phoneguard.theme.ChildDark
import com.example.phoneguard.theme.ChildLight
import com.example.phoneguard.theme.ChildOutline
import com.example.phoneguard.theme.ChildSurfaceMuted
import com.example.phoneguard.theme.ChildTextSecondary
import kotlinx.coroutines.launch

private val LockAccent = ChildAccent
private val LockGradientEdge = Color(0xFFDCDCDC)
private val LockGradientCenter = ChildLight
private val LockActionShape = RoundedCornerShape(12.dp)
private val LockHeadlineFamily = FontFamily(Font(R.font.michroma, FontWeight.Normal))
private val LockBodyFamily = FontFamily(Font(R.font.manrope, FontWeight.Normal))

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
          0.22f to Color(0xFFE8E8E8),
          0.5f to LockGradientCenter,
          0.78f to Color(0xFFE8E8E8),
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
      Surface(shape = CircleShape, color = ChildSurfaceMuted, shadowElevation = 1.dp) {
        Box(Modifier.size(82.dp), contentAlignment = Alignment.Center) {
          Icon(Icons.Outlined.Lock, contentDescription = null, tint = LockAccent, modifier = Modifier.size(38.dp))
        }
      }

      Spacer(Modifier.height(18.dp))
      Text(
        text = "LOCKED",
        fontFamily = LockHeadlineFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 38.sp,
        letterSpacing = 1.1.sp,
        color = ChildDark,
      )

      if (unlockTimeLabel != null) {
        Spacer(Modifier.height(10.dp))
        Text("AVAILABLE AGAIN AT", style = MaterialTheme.typography.labelMedium, color = ChildTextSecondary)
        Text(unlockTimeLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = ChildDark)
      } else if (dailyLimitReached) {
        Spacer(Modifier.height(10.dp))
        Text("DAILY LIMIT REACHED", style = MaterialTheme.typography.labelMedium, color = ChildTextSecondary)
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
        Text(it, style = MaterialTheme.typography.bodySmall, color = ChildTextSecondary, textAlign = TextAlign.Center)
      }

      Spacer(Modifier.height(30.dp))
      OutlinedButton(
        onClick = { showTimeRequest = true; timeRequestMessage = null },
        enabled = !timeRequestInProgress,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = LockActionShape,
        border = BorderStroke(1.dp, ChildOutline),
        colors =
          ButtonDefaults.outlinedButtonColors(
            contentColor = ChildDark,
            disabledContentColor = ChildTextSecondary,
          ),
      ) {
        Text(
          if (timeRequestInProgress) "SENDING REQUEST…" else "REQUEST MORE TIME",
          fontFamily = LockHeadlineFamily,
          fontSize = 11.sp,
        )
      }

      timeRequestMessage?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = ChildTextSecondary, textAlign = TextAlign.Center)
      }
      timeRequestFeedback?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
      }

      Spacer(Modifier.height(18.dp))
      OutlinedTextField(
        value = pin,
        onValueChange = { pin = it.filter(Char::isDigit).take(6); errorMessage = null },
        label = { Text("Parent PIN", fontFamily = LockBodyFamily) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = LockBodyFamily),
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = LockActionShape,
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LockAccent,
            unfocusedBorderColor = ChildOutline,
            cursorColor = LockAccent,
            focusedLabelColor = LockAccent,
            unfocusedLabelColor = ChildTextSecondary,
          ),
      )

      errorMessage?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
      }

      Spacer(Modifier.height(12.dp))
      OutlinedButton(
        onClick = {
          if (pin.length !in 4..6) {
            errorMessage = "Enter Parent PIN."
          } else if (onUnlock(pin)) {
            pin = ""
            errorMessage = null
          } else {
            pin = ""
            errorMessage = "Incorrect PIN."
          }
        },
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = LockActionShape,
        border = BorderStroke(2.dp, LockAccent),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LockAccent),
      ) {
        Text("UNLOCK", fontFamily = LockHeadlineFamily, fontSize = 11.sp)
      }
      Spacer(Modifier.height(20.dp))

      if (allowedApps.isNotEmpty()) {
        Spacer(Modifier.height(36.dp))
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
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(15.dp)),
              )
            }
          }
        }
        Spacer(Modifier.height(20.dp))
      }
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
      containerColor = if (selected) LockAccent else ChildSurfaceMuted,
      contentColor = if (selected) ChildLight else LockAccent,
      disabledContainerColor = ChildSurfaceMuted,
      disabledContentColor = ChildTextSecondary,
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
