package com.example.phoneguard.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.phoneguard.data.ChildSettingsStore
import com.example.phoneguard.remote.AppInventorySyncer
import com.example.phoneguard.remote.ChildHeartbeatSender
import com.example.phoneguard.remote.RemoteCommandSyncer
import com.example.phoneguard.schedule.ScheduleAlarmScheduler

class PhoneGuardAccessibilityService : AccessibilityService() {
  private lateinit var settingsStore: ChildSettingsStore
  private lateinit var alarmScheduler: ScheduleAlarmScheduler
  private lateinit var windowManager: WindowManager
  private lateinit var connectivityManager: ConnectivityManager
  private lateinit var heartbeatSender: ChildHeartbeatSender

  private val mainHandler = Handler(Looper.getMainLooper())

  private val heartbeatRunnable =
    object : Runnable {
      override fun run() {
        sendHeartbeat()
        mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
      }
    }

  private var overlayView: View? = null
  private var foregroundPackage: String? = null
  private var lockStateListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

  private val networkCallback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        Log.i(TAG, "Network available; syncing pending commands")
        syncPendingCommands()
        sendHeartbeat()
      }
    }

  override fun onServiceConnected() {
    super.onServiceConnected()

    settingsStore = ChildSettingsStore(applicationContext)
    alarmScheduler = ScheduleAlarmScheduler(applicationContext)
    windowManager = getSystemService(WindowManager::class.java)
    connectivityManager = getSystemService(ConnectivityManager::class.java)
    heartbeatSender = ChildHeartbeatSender(applicationContext)

    Log.i(TAG, "Accessibility service connected")

    lockStateListener =
      settingsStore.registerLockStateListener {
        Log.i(
          TAG,
          "Lock state changed: effectivelyLocked=" +
            settingsStore.isEffectivelyLocked(),
        )
        refreshOverlayOnMainThread()
      }

    alarmScheduler.syncCurrentStateAndScheduleNext()
    refreshOverlayOnMainThread()

    runCatching {
      connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }.onFailure { error ->
      Log.w(TAG, "Failed to register network callback", error)
    }

    syncPendingCommands()
    mainHandler.removeCallbacks(heartbeatRunnable)
    heartbeatRunnable.run()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (!::settingsStore.isInitialized || event == null) return

    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      val eventPackage = event.packageName?.toString()?.trim()
      if (!eventPackage.isNullOrBlank()) {
        foregroundPackage = eventPackage
      }
    }

    refreshOverlayOnMainThread()
  }

  override fun onInterrupt() = Unit

  override fun onUnbind(intent: Intent?): Boolean {
    if (::heartbeatSender.isInitialized) {
      Thread {
        heartbeatSender.send(accessibilityEnabledOverride = false)
      }.start()
    }
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    mainHandler.removeCallbacks(heartbeatRunnable)
    lockStateListener?.let(settingsStore::unregisterLockStateListener)
    if (::connectivityManager.isInitialized) {
      runCatching {
        connectivityManager.unregisterNetworkCallback(networkCallback)
      }
    }
    hideOverlay()
    super.onDestroy()
  }

  private fun sendHeartbeat() {
    Thread {
      heartbeatSender.send()
    }.start()
  }

  private fun syncPendingCommands() {
    Thread {
      AppInventorySyncer(applicationContext).sync()
      RemoteCommandSyncer(applicationContext).sync()
    }.start()
  }

  private fun refreshOverlayOnMainThread() {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      refreshOverlay()
    } else {
      mainHandler.post {
        if (::settingsStore.isInitialized) {
          refreshOverlay()
        }
      }
    }
  }

  private fun refreshOverlay() {
    val effectivelyLocked = settingsStore.isEffectivelyLocked()
    val foregroundAllowed =
      effectivelyLocked &&
        settingsStore.isPackageAllowed(foregroundPackage)

    Log.i(
      TAG,
      "Refreshing overlay: effectivelyLocked=" +
        effectivelyLocked +
        ", foregroundPackage=" +
        foregroundPackage +
        ", foregroundAllowed=" +
        foregroundAllowed +
        ", overlayVisible=" +
        (overlayView != null),
    )

    if (effectivelyLocked && !foregroundAllowed) {
      showOverlay()
    } else {
      hideOverlay()
    }
  }

  private fun showOverlay() {
    if (overlayView != null) return

    val content =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(32), dp(48), dp(32), dp(48))
        setBackgroundColor(Color.rgb(18, 18, 20))
      }

    val root =
      ScrollView(this).apply {
        isFillViewport = true
        setBackgroundColor(Color.rgb(18, 18, 20))
        addView(
          content,
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
          ),
        )
      }

    val lockIcon =
      TextView(this).apply {
        text = "🔒"
        textSize = 48f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
      }

    val title =
      TextView(this).apply {
        text = "Phone is currently locked"
        textSize = 24f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setPadding(0, dp(20), 0, dp(8))
      }

    val subtitle =
      TextView(this).apply {
        text =
          settingsStore.currentScheduledUnlockLabel()?.let {
            "Available again at $it"
          } ?: "Locked manually"
        textSize = 18f
        gravity = Gravity.CENTER
        setTextColor(Color.LTGRAY)
        setPadding(0, 0, 0, dp(28))
      }

    val allowedPackages = settingsStore.allowedPackages().sorted()
    val allowedTitle =
      if (allowedPackages.isNotEmpty()) {
        TextView(this).apply {
          text = "Allowed apps"
          textSize = 16f
          gravity = Gravity.CENTER
          setTextColor(Color.WHITE)
          setPadding(0, 0, 0, dp(8))
        }
      } else {
        null
      }

    val allowedButtons =
      allowedPackages.mapNotNull { packageName ->
        val launchIntent =
          packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return@mapNotNull null

        val label =
          runCatching {
            val applicationInfo =
              packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
          }.getOrDefault(packageName)

        Button(this).apply {
          text = label
          layoutParams =
            LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT,
              LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
              bottomMargin = dp(6)
            }
          setOnClickListener {
            runCatching {
              startActivity(launchIntent)
            }.onFailure { error ->
              Log.w(TAG, "Failed to launch allowed app: " + packageName, error)
            }
          }
        }
      }

    val disclosure =
      TextView(this).apply {
        text = "Unlocking requires the parent PIN."
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.LTGRAY)
        setPadding(0, 0, 0, dp(16))
      }

    val pinInput =
      EditText(this).apply {
        hint = "Parent PIN"
        inputType =
          InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(6))
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
        gravity = Gravity.CENTER
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          )
      }

    val error =
      TextView(this).apply {
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(255, 120, 120))
        setPadding(0, dp(8), 0, dp(8))
      }

    val unlockButton =
      Button(this).apply {
        text = "UNLOCK"
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          ).apply {
            topMargin = dp(8)
          }

        setOnClickListener {
          val pin = pinInput.text?.toString().orEmpty()
          if (settingsStore.verifyParentPin(pin)) {
            settingsStore.clearAllLocks()
            alarmScheduler.scheduleNext()
            pinInput.setText("")
            error.text = ""
            hideOverlay()
          } else {
            pinInput.setText("")
            error.text = "Incorrect PIN."
          }
        }
      }

    content.addView(lockIcon)
    content.addView(title)
    content.addView(subtitle)
    allowedTitle?.let(content::addView)
    allowedButtons.forEach(content::addView)
    content.addView(disclosure)
    content.addView(pinInput)
    content.addView(error)
    content.addView(unlockButton)

    val params =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.OPAQUE,
      ).apply {
        gravity = Gravity.TOP or Gravity.START
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
      }

    runCatching {
      windowManager.addView(root, params)
      overlayView = root
    }.onSuccess {
      Log.i(TAG, "Lock overlay shown")
    }.onFailure { error ->
      Log.e(TAG, "Failed to show lock overlay", error)
    }
  }

  private fun hideOverlay() {
    val view = overlayView ?: return
    runCatching { windowManager.removeView(view) }
      .onSuccess {
        Log.i(TAG, "Lock overlay hidden")
      }
      .onFailure { error ->
        Log.e(TAG, "Failed to hide lock overlay", error)
      }
    overlayView = null
  }

  private fun dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()

  private companion object {
    const val TAG = "PhoneGuardAccessibility"
    const val HEARTBEAT_INTERVAL_MS = 30_000L
  }
}
