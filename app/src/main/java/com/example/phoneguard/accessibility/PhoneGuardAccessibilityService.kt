package com.example.phoneguard.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
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
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.phoneguard.data.ChildSettingsStore
import com.example.phoneguard.remote.AppInventorySyncer
import com.example.phoneguard.remote.ChildBackendClient
import com.example.phoneguard.remote.ChildHeartbeatSender
import com.example.phoneguard.remote.ChildTimeRequestResult
import com.example.phoneguard.remote.RemoteCommandSyncer
import com.example.phoneguard.schedule.ScheduleAlarmScheduler
import com.example.phoneguard.usage.AppUsageTracker
import com.example.phoneguard.usage.DailyUsageTracker

class PhoneGuardAccessibilityService : AccessibilityService() {
  private lateinit var settingsStore: ChildSettingsStore
  private lateinit var alarmScheduler: ScheduleAlarmScheduler
  private lateinit var windowManager: WindowManager
  private lateinit var connectivityManager: ConnectivityManager
  private lateinit var heartbeatSender: ChildHeartbeatSender
  private lateinit var dailyUsageTracker: DailyUsageTracker
  private lateinit var appUsageTracker: AppUsageTracker

  private val mainHandler = Handler(Looper.getMainLooper())

  private val heartbeatRunnable =
    object : Runnable {
      override fun run() {
        if (::dailyUsageTracker.isInitialized) {
          dailyUsageTracker.flush()
        }
        if (::appUsageTracker.isInitialized) {
          appUsageTracker.flush()
        }
        sendHeartbeat()
        mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
      }
    }

  private var overlayView: View? = null
  private var overlayTimeRequestFeedbackView: TextView? = null
  private var foregroundPackage: String? = null
  private var lockStateListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
  private val lastProtectionEventAt = mutableMapOf<String, Long>()
  private var phoneGuardAppInfoActive = false
  private var appInfoDialogGraceUntil = 0L

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
    dailyUsageTracker =
      DailyUsageTracker(applicationContext) {
        refreshOverlayOnMainThread()
      }
    appUsageTracker = AppUsageTracker(applicationContext)

    Log.i(TAG, "Accessibility service connected")

    lockStateListener =
      settingsStore.registerLockStateListener {
        Log.i(
          TAG,
          "Lock state changed: effectivelyLocked=" +
            settingsStore.isEffectivelyLocked(),
        )
        if (::appUsageTracker.isInitialized) {
          appUsageTracker.flush()
        }
        refreshOverlayOnMainThread()
      }

    foregroundPackage =
      rootInActiveWindow
        ?.packageName
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    dailyUsageTracker.start()
    appUsageTracker.start(foregroundPackage)
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
        if (::appUsageTracker.isInitialized) {
          appUsageTracker.onForegroundPackageChanged(eventPackage)
        }
        foregroundPackage = eventPackage

        val className = event.className?.toString().orEmpty()
        val isPackageInstaller =
          eventPackage.contains("packageinstaller", ignoreCase = true)
        val isFollowUpUninstallSurface =
          eventPackage == "com.android.systemui" || isPackageInstaller
        val isAppInfoScreen =
          isAppInfoClass(
            eventPackage = eventPackage,
            className = className,
          )

        val keepAppInfoContextForDialog =
          phoneGuardAppInfoActive &&
            System.currentTimeMillis() <= appInfoDialogGraceUntil &&
            isSupportedSettingsPackage(eventPackage)

        if (
          !isAppInfoScreen &&
          !isFollowUpUninstallSurface &&
          !keepAppInfoContextForDialog
        ) {
          phoneGuardAppInfoActive = false
          appInfoDialogGraceUntil = 0L
        }
      }
    }

    if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
      val protectionEvent =
        detectSensitiveActionClick(event)
          ?: detectUninstallActionClick(event)

      protectionEvent?.let(::reportProtectionEvent)
    }

    if (
      event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
      event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    ) {
      val protectionEvent =
        detectSensitiveActionConfirmation(event)
          ?: detectPackageInstallerUninstallScreen(event)
          ?: detectSystemUiUninstallConfirmation(event)
          ?: detectProtectionBypassEvent(event)

      protectionEvent?.let { detectedEvent ->
        if (detectedEvent == PROTECTION_EVENT_APP_INFO_OPENED) {
          val wasAlreadyInAppInfo = phoneGuardAppInfoActive
          phoneGuardAppInfoActive = true
          appInfoDialogGraceUntil = 0L

          if (!wasAlreadyInAppInfo) {
            reportProtectionEvent(detectedEvent)
          }
        } else {
          reportProtectionEvent(detectedEvent)
        }
      }
    }

    refreshOverlayOnMainThread()
  }

  override fun onInterrupt() = Unit

  private fun detectSensitiveActionClick(
    event: AccessibilityEvent,
  ): String? {
    if (!phoneGuardAppInfoActive) return null

    val eventPackage = event.packageName?.toString().orEmpty()
    if (!isSupportedSettingsPackage(eventPackage)) return null

    val sourceText =
      buildString {
        event.source?.text?.let {
          append(it)
          append(' ')
        }
        event.source?.contentDescription?.let {
          append(it)
          append(' ')
        }
        event.contentDescription?.let {
          append(it)
          append(' ')
        }
      }.trim().lowercase()

    val fallbackEventText =
      if (sourceText.isBlank() && event.text.size <= 2) {
        event.text.joinToString(" ").trim().lowercase()
      } else {
        ""
      }

    val clickedText =
      (sourceText + " " + fallbackEventText).trim()
    if (clickedText.isBlank()) return null

    val protectionEvent =
      when {
        clickedText.contains("force stop") ||
          clickedText.contains("force-stop") ->
          PROTECTION_EVENT_FORCE_STOP_ATTEMPT

        clickedText.contains("clear data") ||
          clickedText.contains("clear storage") ||
          clickedText.contains("erase app data") ||
          clickedText.contains("delete app data") ||
          clickedText.contains("obriši podatke") ||
          clickedText.contains("obrisi podatke") ->
          PROTECTION_EVENT_CLEAR_DATA_ATTEMPT

        else -> null
      }

    if (protectionEvent != null) {
      appInfoDialogGraceUntil =
        System.currentTimeMillis() + APP_INFO_DIALOG_GRACE_MS
    }

    return protectionEvent
  }

  private fun detectSensitiveActionConfirmation(
    event: AccessibilityEvent,
  ): String? {
    if (!phoneGuardAppInfoActive) return null
    if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      return null
    }

    val activeText = activeWindowText().lowercase()
    val hasCancel =
      activeText.contains("cancel") ||
        activeText.contains("otka")
    val forceStopConfirmation =
      activeText.contains("force stop") ||
        activeText.contains("force-stop")

    return if (hasCancel && forceStopConfirmation) {
      PROTECTION_EVENT_FORCE_STOP_ATTEMPT
    } else {
      null
    }
  }

  private fun detectPackageInstallerUninstallScreen(
    event: AccessibilityEvent,
  ): String? {
    if (!phoneGuardAppInfoActive) return null

    if (
      event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
      event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    ) {
      return null
    }

    val eventPackage = event.packageName?.toString().orEmpty()
    val className = event.className?.toString().orEmpty()
    val isPackageInstaller =
      eventPackage.contains("packageinstaller", ignoreCase = true)
    val isUninstallClass =
      className.contains("Uninstall", ignoreCase = true) ||
        className.contains("Uninstaller", ignoreCase = true)

    if (!isPackageInstaller && !isUninstallClass) {
      return null
    }

    val phoneGuardLabel =
      runCatching {
        packageManager
          .getApplicationLabel(applicationInfo)
          .toString()
      }.getOrDefault("PhoneGuard")

    val visibleText =
      buildString {
        event.text.forEach { item ->
          append(item)
          append(' ')
        }
        event.contentDescription?.let {
          append(it)
          append(' ')
        }
        append(activeWindowText())
      }

    val normalizedText = visibleText.lowercase()
    val mentionsPhoneGuard =
      visibleText.contains(phoneGuardLabel, ignoreCase = true) ||
        visibleText.contains(packageName, ignoreCase = true)
    val hasUninstallText =
      normalizedText.contains("uninstall") ||
        normalizedText.contains("deinstall") ||
        normalizedText.contains("deinstal") ||
        normalizedText.contains("remove app") ||
        normalizedText.contains("ukloni aplikaciju") ||
        normalizedText.contains("obriši aplikaciju") ||
        normalizedText.contains("obrisi aplikaciju")

    if (!mentionsPhoneGuard || !hasUninstallText) {
      return null
    }

    phoneGuardAppInfoActive = false
    return PROTECTION_EVENT_UNINSTALL_SCREEN_OPENED
  }

  private fun detectSystemUiUninstallConfirmation(
    event: AccessibilityEvent,
  ): String? {
    if (
      event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
      event.packageName?.toString() != "com.android.systemui"
    ) {
      return null
    }

    if (!phoneGuardAppInfoActive) {
      return null
    }

    val activeText = activeWindowText().lowercase()
    val isUninstallConfirmation =
      (
        activeText.contains("uninstall now") ||
          activeText.contains("uninstalling will remove all app data")
      ) &&
        activeText.contains("cancel") &&
        activeText.contains("ok")

    return if (isUninstallConfirmation) {
      phoneGuardAppInfoActive = false
      PROTECTION_EVENT_UNINSTALL_SCREEN_OPENED
    } else {
      null
    }
  }

  private fun detectUninstallActionClick(event: AccessibilityEvent): String? {
    if (!phoneGuardAppInfoActive) return null

    val eventPackage = event.packageName?.toString().orEmpty()

    val clickedText =
      buildString {
        event.text.forEach { item ->
          append(item)
          append(' ')
        }
        event.contentDescription?.let {
          append(it)
          append(' ')
        }
        event.source?.text?.let {
          append(it)
          append(' ')
        }
        event.source?.contentDescription?.let {
          append(it)
          append(' ')
        }
      }.lowercase()

    val activeText = activeWindowText()
    val phoneGuardLabel =
      runCatching {
        packageManager
          .getApplicationLabel(applicationInfo)
          .toString()
      }.getOrDefault("PhoneGuard")

    val mentionsPhoneGuard =
      activeText.contains(phoneGuardLabel, ignoreCase = true) ||
        activeText.contains(packageName, ignoreCase = true)

    if (!mentionsPhoneGuard) return null

    val uninstallClicked =
      clickedText.contains("uninstall") ||
        clickedText.contains("deinstall") ||
        clickedText.contains("deinstal") ||
        clickedText.contains("remove app") ||
        clickedText.contains("remove") ||
        clickedText.contains("delete") ||
        clickedText.contains("ukloni aplikaciju") ||
        clickedText.contains("obriši aplikaciju") ||
        clickedText.contains("obrisi aplikaciju")

    if (!uninstallClicked) return null

    return PROTECTION_EVENT_UNINSTALL_SCREEN_OPENED
  }

  private fun detectProtectionBypassEvent(event: AccessibilityEvent): String? {
    val eventPackage = event.packageName?.toString().orEmpty()
    val className = event.className?.toString().orEmpty()
    val phoneGuardLabel =
      runCatching {
        packageManager
          .getApplicationLabel(applicationInfo)
          .toString()
      }.getOrDefault("PhoneGuard")

    val visibleText =
      buildString {
        event.text.forEach { item ->
          append(item)
          append(' ')
        }
        event.contentDescription?.let {
          append(it)
          append(' ')
        }
        append(activeWindowText())
      }

    val mentionsPhoneGuard =
      visibleText.contains(phoneGuardLabel, ignoreCase = true) ||
        visibleText.contains(packageName, ignoreCase = true)

    if (!mentionsPhoneGuard) return null

    val normalizedText = visibleText.lowercase()

    val isAppInfoScreen =
      isAppInfoClass(
        eventPackage = eventPackage,
        className = className,
      ) ||
        (
          event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            isAppInfoContent(
              eventPackage = eventPackage,
              normalizedText = normalizedText,
            )
        )

    return if (isAppInfoScreen) {
      PROTECTION_EVENT_APP_INFO_OPENED
    } else {
      null
    }
  }

  private fun isSupportedSettingsPackage(eventPackage: String): Boolean =
    eventPackage == "com.android.settings" ||
      eventPackage == "com.miui.securitycenter"

  private fun isAppInfoContent(
    eventPackage: String,
    normalizedText: String,
  ): Boolean {
    if (!isSupportedSettingsPackage(eventPackage)) return false

    val hasForceStop =
      normalizedText.contains("force stop") ||
        normalizedText.contains("force-stop") ||
        normalizedText.contains("prisilno zaust") ||
        normalizedText.contains("prinudno zaust")

    val hasUninstall =
      normalizedText.contains("uninstall") ||
        normalizedText.contains("deinstall") ||
        normalizedText.contains("deinstal") ||
        normalizedText.contains("remove app") ||
        normalizedText.contains("ukloni aplikaciju") ||
        normalizedText.contains("obriši aplikaciju") ||
        normalizedText.contains("obrisi aplikaciju")

    val secondaryMarkers =
      listOf(
        normalizedText.contains("storage") ||
          normalizedText.contains("skladi") ||
          normalizedText.contains("memorij"),
        normalizedText.contains("permission") ||
          normalizedText.contains("dozvol"),
        normalizedText.contains("battery") ||
          normalizedText.contains("baterij"),
        normalizedText.contains("data usage") ||
          normalizedText.contains("mobile data") ||
          normalizedText.contains("upotreba podataka"),
        normalizedText.contains("notification") ||
          normalizedText.contains("obavešten") ||
          normalizedText.contains("obavesten"),
      ).count { it }

    return (hasForceStop || hasUninstall) && secondaryMarkers >= 1
  }

  private fun isAppInfoClass(
    eventPackage: String,
    className: String,
  ): Boolean {
    val isAndroidSettingsAppInfo =
      eventPackage == "com.android.settings" &&
        (
          className.contains("InstalledAppDetails", ignoreCase = true) ||
            className.contains("AppInfoDashboard", ignoreCase = true)
        )

    val isMiuiAppInfo =
      eventPackage == "com.miui.securitycenter" &&
        className.contains("ApplicationsDetailsActivity", ignoreCase = true)

    return isAndroidSettingsAppInfo || isMiuiAppInfo
  }

  private fun activeWindowText(): String {
    val root = rootInActiveWindow ?: return ""
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(root)
    var visited = 0

    return buildString {
      while (queue.isNotEmpty() && visited < MAX_ACCESSIBILITY_NODES_TO_SCAN) {
        val node = queue.removeFirst()
        visited += 1

        node.text?.toString()?.takeIf(String::isNotBlank)?.let {
          append(it)
          append(' ')
        }
        node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let {
          append(it)
          append(' ')
        }
        node.viewIdResourceName?.takeIf(String::isNotBlank)?.let {
          append(it)
          append(' ')
        }

        for (index in 0 until node.childCount) {
          node.getChild(index)?.let(queue::addLast)
        }
      }
    }
  }

  private fun reportProtectionEvent(protectionEvent: String) {
    val now = System.currentTimeMillis()
    val previous = lastProtectionEventAt[protectionEvent] ?: 0L
    if (now - previous < PROTECTION_EVENT_DEBOUNCE_MS) return

    lastProtectionEventAt[protectionEvent] = now
    Log.i(TAG, "Protection bypass event detected: " + protectionEvent)

    Thread {
      heartbeatSender.send(protectionEvent = protectionEvent)
    }.start()
  }

  override fun onUnbind(intent: Intent?): Boolean {
    if (::dailyUsageTracker.isInitialized) {
      dailyUsageTracker.flush()
    }
    if (::appUsageTracker.isInitialized) {
      appUsageTracker.flush()
    }
    if (::alarmScheduler.isInitialized) {
      alarmScheduler.scheduleProtectionStatusCheck()
    }
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    mainHandler.removeCallbacks(heartbeatRunnable)
    if (::dailyUsageTracker.isInitialized) {
      dailyUsageTracker.stop()
    }
    if (::appUsageTracker.isInitialized) {
      appUsageTracker.stop()
    }
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
      overlayTimeRequestFeedbackView?.apply {
        val feedback = settingsStore.timeRequestFeedback()
        text = feedback.orEmpty()
        visibility = if (feedback.isNullOrBlank()) View.GONE else View.VISIBLE
      }
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
          when {
            settingsStore.isScheduleLockActive() ->
              settingsStore.currentScheduledUnlockLabel()?.let {
                "Available again at $it"
              } ?: "Locked by schedule"
            settingsStore.isDailyLimitLockActive() ->
              "Daily limit reached"
            else ->
              "Locked manually"
          }
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

    val audioManager = getSystemService(AudioManager::class.java)

    val soundTitle =
      TextView(this).apply {
        text = "Sound"
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setPadding(0, dp(16), 0, dp(8))
      }

    val soundStatus =
      TextView(this).apply {
        text = ringerModeLabel(audioManager.ringerMode)
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.LTGRAY)
        setPadding(0, 0, 0, dp(8))
      }

    val soundControls =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          )
      }

    listOf(
      "SOUND" to AudioManager.RINGER_MODE_NORMAL,
      "VIBRATE" to AudioManager.RINGER_MODE_VIBRATE,
    ).forEach { (label, mode) ->
      soundControls.addView(
        Button(this).apply {
          text = label
          layoutParams =
            LinearLayout.LayoutParams(
              0,
              LinearLayout.LayoutParams.WRAP_CONTENT,
              1f,
            ).apply {
              marginStart = dp(3)
              marginEnd = dp(3)
            }
          setOnClickListener {
            applyRingerMode(
              audioManager = audioManager,
              mode = mode,
              statusView = soundStatus,
            )
          }
        },
      )
    }

    val cameraManager = getSystemService(CameraManager::class.java)
    val torchCameraId =
      runCatching {
        cameraManager.cameraIdList.firstOrNull { cameraId ->
          cameraManager
            .getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
      }.getOrNull()

    var flashlightEnabled = false

    val flashlightStatus =
      TextView(this).apply {
        text =
          if (torchCameraId == null) {
            "Flashlight is unavailable on this device."
          } else {
            "Flashlight is off"
          }
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.LTGRAY)
        setPadding(0, dp(12), 0, dp(8))
      }

    val flashlightButton =
      Button(this).apply {
        text = "FLASHLIGHT ON"
        isEnabled = torchCameraId != null
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          )
        setOnClickListener {
          val cameraId = torchCameraId ?: return@setOnClickListener
          val nextEnabled = !flashlightEnabled

          runCatching {
            cameraManager.setTorchMode(cameraId, nextEnabled)
          }.onSuccess {
            flashlightEnabled = nextEnabled
            text =
              if (flashlightEnabled) {
                "FLASHLIGHT OFF"
              } else {
                "FLASHLIGHT ON"
              }
            flashlightStatus.text =
              if (flashlightEnabled) {
                "Flashlight is on"
              } else {
                "Flashlight is off"
              }
            flashlightStatus.setTextColor(Color.LTGRAY)
          }.onFailure { error ->
            Log.w(TAG, "Flashlight change failed", error)
            flashlightStatus.text = "Flashlight is currently unavailable."
            flashlightStatus.setTextColor(Color.rgb(255, 170, 100))
          }
        }
      }

    val timeRequestStatus =
      TextView(this).apply {
        val feedback = settingsStore.timeRequestFeedback()
        text = feedback.orEmpty()
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(255, 120, 120))
        setPadding(0, dp(10), 0, dp(10))
        visibility = if (feedback.isNullOrBlank()) View.GONE else View.VISIBLE
      }
    overlayTimeRequestFeedbackView = timeRequestStatus

    val timeRequestOptions =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        visibility = View.GONE
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          )
      }

    val requestMoreTimeButton =
      Button(this).apply {
        text = "REQUEST MORE TIME"
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          ).apply {
            topMargin = dp(16)
          }
        setOnClickListener {
          timeRequestOptions.visibility =
            if (timeRequestOptions.visibility == View.VISIBLE) {
              View.GONE
            } else {
              View.VISIBLE
            }
        }
      }

    listOf(5, 15, 30).forEach { minutes ->
      timeRequestOptions.addView(
        Button(this).apply {
          text = minutes.toString() + " MIN"
          layoutParams =
            LinearLayout.LayoutParams(
              0,
              LinearLayout.LayoutParams.WRAP_CONTENT,
              1f,
            ).apply {
              topMargin = dp(4)
              marginStart = dp(3)
              marginEnd = dp(3)
            }

          setOnClickListener {
            val selectedButton = this
            requestMoreTimeButton.isEnabled = false
            timeRequestOptions.visibility = View.GONE
            settingsStore.setTimeRequestFeedback(null)
            timeRequestStatus.visibility = View.VISIBLE
            timeRequestStatus.setTextColor(Color.LTGRAY)
            timeRequestStatus.text = "Sending request…"

            Thread {
              val identity = settingsStore.getOrCreatePairingIdentity()
              val result =
                ChildBackendClient().requestMoreTime(
                  deviceId = identity.deviceId,
                  deviceSecret = settingsStore.getOrCreateDeviceSecret(),
                  requestedMinutes = minutes,
                )

              mainHandler.post {
                if (overlayView == null) return@post

                requestMoreTimeButton.isEnabled = true
                selectedButton.isEnabled = true
                timeRequestStatus.visibility = View.VISIBLE

                when (result) {
                  is ChildTimeRequestResult.Success -> {
                    timeRequestStatus.setTextColor(Color.LTGRAY)
                    timeRequestStatus.text =
                      if (result.alreadyPending) {
                        "A request is already waiting for Parent approval."
                      } else if (result.pushSent) {
                        "Request sent to Parent."
                      } else {
                        "Request sent. Parent will see it in PhoneGuard."
                      }
                  }

                  is ChildTimeRequestResult.Failure -> {
                    timeRequestStatus.setTextColor(Color.rgb(255, 120, 120))
                    timeRequestStatus.text = result.message
                  }
                }
              }
            }.start()
          }
        },
      )
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
    content.addView(soundTitle)
    content.addView(soundStatus)
    content.addView(soundControls)
    content.addView(flashlightStatus)
    content.addView(flashlightButton)
    content.addView(requestMoreTimeButton)
    content.addView(timeRequestOptions)
    content.addView(timeRequestStatus)
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

  private fun applyRingerMode(
    audioManager: AudioManager,
    mode: Int,
    statusView: TextView,
  ) {
    runCatching {
      audioManager.ringerMode = mode
    }.onSuccess {
      if (audioManager.ringerMode == mode) {
        statusView.text = ringerModeLabel(mode)
        statusView.setTextColor(Color.LTGRAY)
      } else {
        statusView.text = "Android did not allow this sound change."
        statusView.setTextColor(Color.rgb(255, 170, 100))
      }
    }.onFailure { error ->
      Log.w(TAG, "Ringer mode change blocked", error)
      statusView.text = "Android did not allow this sound change."
      statusView.setTextColor(Color.rgb(255, 170, 100))
    }
  }

  private fun ringerModeLabel(mode: Int): String =
    when (mode) {
      AudioManager.RINGER_MODE_NORMAL -> "Current mode: Sound"
      AudioManager.RINGER_MODE_VIBRATE -> "Current mode: Vibrate"
      else -> "Current sound mode"
    }

  private fun hideOverlay() {
    val view = overlayView ?: return
    overlayTimeRequestFeedbackView = null
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
    const val APP_INFO_DIALOG_GRACE_MS = 10_000L
    const val HEARTBEAT_INTERVAL_MS = 30_000L
    const val PROTECTION_EVENT_DEBOUNCE_MS = 30_000L
    const val MAX_ACCESSIBILITY_NODES_TO_SCAN = 250
    const val PROTECTION_EVENT_APP_INFO_OPENED = "APP_INFO_OPENED"
    const val PROTECTION_EVENT_UNINSTALL_SCREEN_OPENED = "UNINSTALL_SCREEN_OPENED"
    const val PROTECTION_EVENT_FORCE_STOP_ATTEMPT = "FORCE_STOP_ATTEMPT"
    const val PROTECTION_EVENT_CLEAR_DATA_ATTEMPT = "CLEAR_DATA_ATTEMPT"
  }
}
