package com.example.phoneguard.usage

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.example.phoneguard.data.ChildSettingsStore

class AppUsageTracker(context: Context) {
  private val appContext = context.applicationContext
  private val settingsStore = ChildSettingsStore(appContext)
  private val packageManager = appContext.packageManager
  private val powerManager = appContext.getSystemService(PowerManager::class.java)
  private val homePackage =
    runCatching {
      packageManager
        .resolveActivity(
          Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
          0,
        )
        ?.activityInfo
        ?.packageName
    }.getOrNull()
  private val handler = Handler(Looper.getMainLooper())

  private val trackablePackageCache = mutableMapOf<String, Boolean>()
  private val usageMillis = linkedMapOf<String, Long>()

  private var running = false
  private var usageDate = ""
  private var foregroundPackage: String? = null
  private var lastTickElapsed = 0L
  private var lastPersistElapsed = 0L
  private var wasCountable = false

  private var recentSessionPackage: String? = null
  private var recentSessionStartedAtMillis = 0L

  private val tickRunnable =
    object : Runnable {
      override fun run() {
        update(forcePersist = false)
        if (running) {
          handler.postDelayed(this, TICK_INTERVAL_MS)
        }
      }
    }

  fun start(initialForegroundPackage: String? = null) {
    if (running) return

    val nowMillis = System.currentTimeMillis()
    val nowElapsed = SystemClock.elapsedRealtime()
    usageDate = settingsStore.currentLocalDateKey(nowMillis)
    usageMillis.clear()
    usageMillis.putAll(settingsStore.appUsageMillisForToday(nowMillis))
    foregroundPackage = normalizePackage(initialForegroundPackage)
    lastTickElapsed = nowElapsed
    lastPersistElapsed = nowElapsed
    wasCountable = shouldCount(nowMillis)
    syncRecentUnlockedSession(nowMillis)

    if (usageMillis.isEmpty()) {
      settingsStore.saveAppUsage(usageDate, emptyMap())
    }

    running = true
    handler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
  }

  fun onForegroundPackageChanged(packageName: String?) {
    val normalized = normalizePackage(packageName)

    if (!running) {
      foregroundPackage = normalized
      return
    }

    update(forcePersist = false)
    foregroundPackage = normalized

    val nowMillis = System.currentTimeMillis()
    syncRecentUnlockedSession(nowMillis)
    wasCountable = shouldCount(nowMillis)
  }

  fun flush() {
    if (!running) return
    update(forcePersist = true)
  }

  fun stop() {
    if (!running) return
    update(forcePersist = true)
    finishRecentUnlockedSession(System.currentTimeMillis())
    running = false
    handler.removeCallbacks(tickRunnable)
  }

  private fun update(forcePersist: Boolean) {
    val nowMillis = System.currentTimeMillis()
    val nowElapsed = SystemClock.elapsedRealtime()
    val currentDate = settingsStore.currentLocalDateKey(nowMillis)

    var dateChanged = false
    if (currentDate != usageDate) {
      finishRecentUnlockedSession(nowMillis)
      usageDate = currentDate
      usageMillis.clear()
      dateChanged = true
    } else if (lastTickElapsed > 0L && wasCountable) {
      val packageName = foregroundPackage
      if (packageName != null) {
        val delta = (nowElapsed - lastTickElapsed).coerceIn(0L, MAX_TICK_DELTA_MS)
        if (delta > 0L) {
          usageMillis[packageName] =
            (usageMillis[packageName] ?: 0L) + delta
        }
      }
    }

    syncRecentUnlockedSession(nowMillis)

    if (
      forcePersist ||
      dateChanged ||
      nowElapsed - lastPersistElapsed >= PERSIST_INTERVAL_MS
    ) {
      settingsStore.saveAppUsage(usageDate, usageMillis)
      persistActiveRecentSession(nowMillis)
      lastPersistElapsed = nowElapsed
    }

    lastTickElapsed = nowElapsed
    wasCountable = shouldCount(nowMillis)
  }

  private fun syncRecentUnlockedSession(nowMillis: Long) {
    val desiredPackage =
      foregroundPackage
        ?.takeIf {
          powerManager.isInteractive &&
            !settingsStore.isEffectivelyLocked(nowMillis)
        }

    if (desiredPackage == recentSessionPackage) return

    finishRecentUnlockedSession(nowMillis)

    if (desiredPackage != null) {
      recentSessionPackage = desiredPackage
      recentSessionStartedAtMillis = nowMillis
    }
  }

  private fun persistActiveRecentSession(nowMillis: Long) {
    val packageName = recentSessionPackage ?: return
    val startedAtMillis = recentSessionStartedAtMillis
    if (startedAtMillis <= 0L || nowMillis <= startedAtMillis) return

    settingsStore.upsertRecentAppSession(
      packageName = packageName,
      startedAtMillis = startedAtMillis,
      endedAtMillis = nowMillis,
    )
  }

  private fun finishRecentUnlockedSession(nowMillis: Long) {
    persistActiveRecentSession(nowMillis)
    recentSessionPackage = null
    recentSessionStartedAtMillis = 0L
  }

  private fun shouldCount(nowMillis: Long): Boolean {
    val packageName = foregroundPackage ?: return false
    if (!powerManager.isInteractive) return false

    if (!settingsStore.isEffectivelyLocked(nowMillis)) {
      return true
    }

    return settingsStore.isPackageAllowed(packageName)
  }

  private fun normalizePackage(packageName: String?): String? {
    val normalized = packageName?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return normalized.takeIf(::isTrackablePackage)
  }

  private fun isTrackablePackage(packageName: String): Boolean =
    trackablePackageCache.getOrPut(packageName) {
      if (
        packageName == appContext.packageName ||
        packageName == homePackage ||
        packageName == "com.android.systemui" ||
        packageName == "com.android.settings" ||
        packageName == "com.miui.securitycenter" ||
        packageName.contains("packageinstaller", ignoreCase = true)
      ) {
        false
      } else {
        runCatching {
          packageManager.getLaunchIntentForPackage(packageName) != null
        }.getOrDefault(false)
      }
    }

  private companion object {
    const val TICK_INTERVAL_MS = 5_000L
    const val PERSIST_INTERVAL_MS = 30_000L
    const val MAX_TICK_DELTA_MS = 60_000L
  }
}
