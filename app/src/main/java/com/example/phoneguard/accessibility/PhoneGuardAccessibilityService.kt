package com.example.phoneguard.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.ColorStateList
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.phoneguard.R
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
  private var lastProtectionStatusWatchAt = 0L

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

    if (
      event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
      event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    ) {
      val protectionPackage = event.packageName?.toString().orEmpty()
      if (isSupportedSettingsPackage(protectionPackage)) {
        scheduleProtectionStatusWatch()
      }
    }

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

  private fun scheduleProtectionStatusWatch() {
    val now = System.currentTimeMillis()
    if (now - lastProtectionStatusWatchAt < PROTECTION_STATUS_WATCH_DEBOUNCE_MS) {
      return
    }

    lastProtectionStatusWatchAt = now
    alarmScheduler.scheduleProtectionStatusWatch()
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

    val accent = Color.rgb(232, 62, 29)
    val graphite = Color.rgb(48, 49, 50)
    val secondaryText = Color.rgb(127, 128, 130)
    val surface = Color.rgb(246, 246, 243)
    val surfaceVariant = Color.rgb(233, 233, 229)
    val outline = Color.rgb(211, 211, 206)
    val errorColor = Color.rgb(164, 61, 61)
    val michroma = ResourcesCompat.getFont(this, R.font.michroma) ?: Typeface.DEFAULT
    val manrope = ResourcesCompat.getFont(this, R.font.manrope) ?: Typeface.DEFAULT

    fun roundedBackground(
      fillColor: Int,
      radiusDp: Int = 12,
      strokeColor: Int? = null,
      strokeWidthDp: Int = 1,
    ): GradientDrawable =
      GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(fillColor)
        strokeColor?.let { setStroke(dp(strokeWidthDp), it) }
      }

    fun circleBackground(fillColor: Int): GradientDrawable =
      GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fillColor)
      }

    val content =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = resources.displayMetrics.heightPixels
        setPadding(dp(28), dp(44), dp(28), dp(36))
      }

    val root =
      ScrollView(this).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        background =
          GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
              Color.rgb(222, 222, 218),
              Color.rgb(231, 231, 227),
              Color.rgb(243, 243, 240),
              Color.rgb(231, 231, 227),
              Color.rgb(222, 222, 218),
            ),
          )
        addView(
          content,
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
          ),
        )
      }

    val lockIcon =
      ImageView(this).apply {
        setImageResource(R.drawable.pg_child_lock)
        imageTintList = ColorStateList.valueOf(accent)
        background = circleBackground(surface)
        setPadding(dp(21), dp(21), dp(21), dp(21))
        layoutParams =
          LinearLayout.LayoutParams(dp(82), dp(82)).apply {
            bottomMargin = dp(16)
          }
        contentDescription = "Locked"
      }

    val title =
      TextView(this).apply {
        text = "LOCKED"
        textSize = 38f
        gravity = Gravity.CENTER
        setTextColor(graphite)
        typeface = michroma
        letterSpacing = 0.03f
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          ).apply {
            bottomMargin = dp(26)
          }
      }

    val allowedPackages = settingsStore.allowedPackages().sorted()
    val allowedAppsRow =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
      }

    allowedPackages.forEach { packageName ->
      val launchIntent =
        packageManager.getLaunchIntentForPackage(packageName)
          ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          ?: return@forEach

      val applicationInfo =
        runCatching {
          packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull() ?: return@forEach

      val label =
        runCatching {
          packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(packageName)

      allowedAppsRow.addView(
        ImageButton(this).apply {
          setImageDrawable(packageManager.getApplicationIcon(applicationInfo))
          contentDescription = label
          background = roundedBackground(surface, radiusDp = 14)
          scaleType = ImageView.ScaleType.CENTER_INSIDE
          setPadding(dp(8), dp(8), dp(8), dp(8))
          layoutParams =
            LinearLayout.LayoutParams(dp(58), dp(58)).apply {
              marginStart = dp(6)
              marginEnd = dp(6)
            }
          setOnClickListener {
            runCatching {
              startActivity(launchIntent)
            }.onFailure { error ->
              Log.w(TAG, "Failed to launch allowed app: " + packageName, error)
            }
          }
        },
      )
    }

    val allowedAppsScroller =
      HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        visibility = if (allowedAppsRow.childCount == 0) View.GONE else View.VISIBLE
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          ).apply {
            bottomMargin = dp(24)
          }
        addView(
          allowedAppsRow,
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
          ),
        )
      }

    val audioManager = getSystemService(AudioManager::class.java)
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

    val systemMessage =
      TextView(this).apply {
        textSize = 12f
        gravity = Gravity.CENTER
        typeface = manrope
        setTextColor(secondaryText)
        visibility = View.GONE
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          ).apply {
            topMargin = dp(10)
          }
      }

    fun controlButton(
      iconRes: Int,
      description: String,
    ): ImageButton =
      ImageButton(this).apply {
        setImageResource(iconRes)
        contentDescription = description
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(15), dp(15), dp(15), dp(15))
        layoutParams =
          LinearLayout.LayoutParams(dp(56), dp(56)).apply {
            marginStart = dp(9)
            marginEnd = dp(9)
          }
      }

    val soundButton = controlButton(R.drawable.pg_child_sound, "Sound")
    val vibrateButton = controlButton(R.drawable.pg_child_vibrate, "Vibrate")
    val flashlightButton = controlButton(R.drawable.pg_child_flashlight, "Flashlight")

    fun styleControlButton(
      button: ImageButton,
      selected: Boolean,
      enabled: Boolean = true,
    ) {
      button.isEnabled = enabled
      button.background =
        circleBackground(
          when {
            !enabled -> surfaceVariant
            selected -> accent
            else -> surface
          },
        )
      button.imageTintList =
        ColorStateList.valueOf(
          when {
            !enabled -> Color.rgb(170, 170, 166)
            selected -> Color.WHITE
            else -> graphite
          },
        )
    }

    fun refreshSoundControls() {
      val mode = audioManager.ringerMode
      styleControlButton(
        soundButton,
        selected = mode == AudioManager.RINGER_MODE_NORMAL,
      )
      styleControlButton(
        vibrateButton,
        selected = mode == AudioManager.RINGER_MODE_VIBRATE,
      )
    }

    refreshSoundControls()
    styleControlButton(
      flashlightButton,
      selected = flashlightEnabled,
      enabled = torchCameraId != null,
    )

    soundButton.setOnClickListener {
      runCatching {
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
      }.onSuccess {
        systemMessage.visibility = View.GONE
        refreshSoundControls()
      }.onFailure { error ->
        Log.w(TAG, "Ringer mode change blocked", error)
        systemMessage.text = "Android did not allow this sound change."
        systemMessage.setTextColor(errorColor)
        systemMessage.visibility = View.VISIBLE
      }
    }

    vibrateButton.setOnClickListener {
      runCatching {
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
      }.onSuccess {
        systemMessage.visibility = View.GONE
        refreshSoundControls()
      }.onFailure { error ->
        Log.w(TAG, "Ringer mode change blocked", error)
        systemMessage.text = "Android did not allow this sound change."
        systemMessage.setTextColor(errorColor)
        systemMessage.visibility = View.VISIBLE
      }
    }

    flashlightButton.setOnClickListener {
      val cameraId = torchCameraId ?: return@setOnClickListener
      val nextEnabled = !flashlightEnabled

      runCatching {
        cameraManager.setTorchMode(cameraId, nextEnabled)
      }.onSuccess {
        flashlightEnabled = nextEnabled
        systemMessage.visibility = View.GONE
        styleControlButton(
          flashlightButton,
          selected = flashlightEnabled,
          enabled = true,
        )
      }.onFailure { error ->
        Log.w(TAG, "Flashlight change failed", error)
        systemMessage.text = "Flashlight is currently unavailable."
        systemMessage.setTextColor(errorColor)
        systemMessage.visibility = View.VISIBLE
      }
    }

    val utilityControls =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          ).apply {
            bottomMargin = dp(24)
          }
        addView(soundButton)
        addView(vibrateButton)
        addView(flashlightButton)
      }

    val timeRequestStatus =
      TextView(this).apply {
        val feedback = settingsStore.timeRequestFeedback()
        text = feedback.orEmpty()
        textSize = 12f
        gravity = Gravity.CENTER
        typeface = manrope
        setTextColor(errorColor)
        setPadding(0, dp(8), 0, dp(8))
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
          ).apply {
            topMargin = dp(8)
          }
      }

    val requestMoreTimeButton =
      Button(this).apply {
        text = "REQUEST MORE TIME"
        textSize = 12f
        letterSpacing = 0.04f
        setTextColor(graphite)
        setAllCaps(false)
        typeface = michroma
        backgroundTintList = null
        background = roundedBackground(surface, strokeColor = outline, strokeWidthDp = 1)
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(64),
          )
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
          textSize = 11f
          setTextColor(graphite)
          setAllCaps(false)
          backgroundTintList = null
          background = roundedBackground(surface, strokeColor = outline)
          layoutParams =
            LinearLayout.LayoutParams(
              0,
              dp(44),
              1f,
            ).apply {
              marginStart = dp(4)
              marginEnd = dp(4)
            }

          setOnClickListener {
            val selectedButton = this
            requestMoreTimeButton.isEnabled = false
            timeRequestOptions.visibility = View.GONE
            settingsStore.setTimeRequestFeedback(null)
            timeRequestStatus.visibility = View.VISIBLE
            timeRequestStatus.setTextColor(secondaryText)
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
                    timeRequestStatus.setTextColor(secondaryText)
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
                    timeRequestStatus.setTextColor(errorColor)
                    timeRequestStatus.text = result.message
                  }
                }
              }
            }.start()
          }
        },
      )
    }

    val pinInput =
      EditText(this).apply {
        hint = "Parent PIN"
        textSize = 16f
        inputType =
          InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(6))
        setTextColor(graphite)
        setHintTextColor(secondaryText)
        typeface = manrope
        gravity = Gravity.CENTER
        setSingleLine(true)
        backgroundTintList = null
        background = roundedBackground(surface, strokeColor = outline, strokeWidthDp = 1)
        setPadding(dp(18), 0, dp(18), 0)
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(64),
          ).apply {
            topMargin = dp(18)
          }
        setOnFocusChangeListener { _, hasFocus ->
          background =
            if (hasFocus) {
              roundedBackground(surface, strokeColor = accent, strokeWidthDp = 2)
            } else {
              roundedBackground(surface, strokeColor = outline, strokeWidthDp = 1)
            }
        }
      }

    val error =
      TextView(this).apply {
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(errorColor)
        setPadding(0, dp(8), 0, 0)
      }

    val unlockButton =
      Button(this).apply {
        text = "UNLOCK"
        textSize = 12f
        letterSpacing = 0.04f
        setTextColor(accent)
        setAllCaps(false)
        typeface = michroma
        backgroundTintList = null
        background = roundedBackground(surface, strokeColor = accent, strokeWidthDp = 2)
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(64),
          ).apply {
            topMargin = dp(12)
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
    content.addView(allowedAppsScroller)
    content.addView(utilityControls)
    content.addView(systemMessage)
    content.addView(requestMoreTimeButton)
    content.addView(timeRequestOptions)
    content.addView(timeRequestStatus)
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
    const val PROTECTION_STATUS_WATCH_DEBOUNCE_MS = 2_000L
    const val HEARTBEAT_INTERVAL_MS = 30_000L
    const val PROTECTION_EVENT_DEBOUNCE_MS = 30_000L
    const val MAX_ACCESSIBILITY_NODES_TO_SCAN = 250
    const val PROTECTION_EVENT_APP_INFO_OPENED = "APP_INFO_OPENED"
    const val PROTECTION_EVENT_UNINSTALL_SCREEN_OPENED = "UNINSTALL_SCREEN_OPENED"
    const val PROTECTION_EVENT_FORCE_STOP_ATTEMPT = "FORCE_STOP_ATTEMPT"
    const val PROTECTION_EVENT_CLEAR_DATA_ATTEMPT = "CLEAR_DATA_ATTEMPT"
  }
}
