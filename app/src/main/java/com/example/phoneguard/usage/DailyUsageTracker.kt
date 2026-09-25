package com.example.phoneguard.usage

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.example.phoneguard.data.ChildSettingsStore

class DailyUsageTracker(
  context: Context,
  private val onLockStateChanged: () -> Unit,
) {
  private val appContext = context.applicationContext
  private val settingsStore = ChildSettingsStore(appContext)
  private val powerManager = appContext.getSystemService(PowerManager::class.java)
  private val handler = Handler(Looper.getMainLooper())

  private var running = false
  private var usageDate = ""
  private var usedMillis = 0L
  private var lastTickElapsed = 0L
  private var lastPersistElapsed = 0L
  private var wasCountable = false
  private var limitReached = false

  private val tickRunnable =
    object : Runnable {
      override fun run() {
        update(forcePersist = false)
        if (running) {
          handler.postDelayed(this, TICK_INTERVAL_MS)
        }
      }
    }

  fun start() {
    if (running) return

    val nowMillis = System.currentTimeMillis()
    val nowElapsed = SystemClock.elapsedRealtime()
    usageDate = settingsStore.currentLocalDateKey(nowMillis)
    usedMillis = settingsStore.dailyUsageMillisForToday(nowMillis)
    limitReached = isLimitReached()
    lastTickElapsed = nowElapsed
    lastPersistElapsed = nowElapsed
    wasCountable = shouldCount(nowMillis)

    if (settingsStore.dailyUsageMillisForToday(nowMillis) == 0L) {
      settingsStore.saveDailyUsage(usageDate, usedMillis)
    }

    running = true
    handler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
  }

  fun flush() {
    if (!running) return
    update(forcePersist = true)
  }

  fun stop() {
    if (!running) return
    update(forcePersist = true)
    running = false
    handler.removeCallbacks(tickRunnable)
  }

  private fun update(forcePersist: Boolean) {
    val nowMillis = System.currentTimeMillis()
    val nowElapsed = SystemClock.elapsedRealtime()
    val currentDate = settingsStore.currentLocalDateKey(nowMillis)

    var dateChanged = false
    if (currentDate != usageDate) {
      usageDate = currentDate
      usedMillis = 0L
      dateChanged = true
    } else if (lastTickElapsed > 0L && wasCountable) {
      val delta = (nowElapsed - lastTickElapsed).coerceIn(0L, MAX_TICK_DELTA_MS)
      usedMillis += delta
    }

    val reachedNow = isLimitReached()
    val reachedChanged = reachedNow != limitReached

    if (
      forcePersist ||
      dateChanged ||
      reachedChanged ||
      nowElapsed - lastPersistElapsed >= PERSIST_INTERVAL_MS
    ) {
      settingsStore.saveDailyUsage(usageDate, usedMillis)
      lastPersistElapsed = nowElapsed
    }

    limitReached = reachedNow
    lastTickElapsed = nowElapsed
    wasCountable = shouldCount(nowMillis)

    if (dateChanged || reachedChanged) {
      onLockStateChanged()
    }
  }

  private fun shouldCount(nowMillis: Long): Boolean {
    if (!powerManager.isInteractive) return false

    if (settingsStore.isTemporaryAllowanceActive(nowMillis)) {
      return true
    }

    return !settingsStore.isManualLockActive() &&
      !settingsStore.isScheduleLockActive() &&
      !limitReached
  }

  private fun isLimitReached(): Boolean {
    val limitMinutes = settingsStore.dailyLimitMinutes() ?: return false
    return usedMillis >= limitMinutes * 60_000L
  }

  private companion object {
    const val TICK_INTERVAL_MS = 5_000L
    const val PERSIST_INTERVAL_MS = 30_000L
    const val MAX_TICK_DELTA_MS = 60_000L
  }
}
