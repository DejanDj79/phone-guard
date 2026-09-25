package com.example.phoneguard.remote

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.example.phoneguard.core.InstalledAppInfo
import com.example.phoneguard.data.AllowedAppsPolicy
import com.example.phoneguard.data.ChildSettingsStore

class AppInventorySyncer(context: Context) {
  private val appContext = context.applicationContext
  private val packageManager = appContext.packageManager
  private val settingsStore = ChildSettingsStore(appContext)
  private val backendClient = ChildBackendClient()

  fun sync(): Boolean {
    val apps = discoverLauncherApps()
    val signature =
      apps.joinToString("\n") { it.packageName + "\t" + it.label }

    if (signature == settingsStore.appInventorySignature()) {
      return true
    }

    val identity = settingsStore.getOrCreatePairingIdentity()

    return when (
      val result =
        backendClient.syncAppInventory(
          deviceId = identity.deviceId,
          deviceSecret = settingsStore.getOrCreateDeviceSecret(),
          apps = apps,
        )
    ) {
      ChildAppInventorySyncResult.Success -> {
        settingsStore.saveAppInventorySignature(signature)
        Log.i(TAG, "App inventory synced: " + apps.size + " launcher app(s)")
        true
      }

      is ChildAppInventorySyncResult.Failure -> {
        Log.w(TAG, "App inventory sync failed: " + result.message)
        false
      }
    }
  }

  fun discoverLauncherApps(): List<InstalledAppInfo> {
    val launcherIntent =
      Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    return packageManager
      .queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
      .mapNotNull { resolveInfo ->
        val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
        val packageName = activityInfo.packageName?.trim().orEmpty()

        if (
          packageName.isBlank() ||
          packageName == appContext.packageName ||
          AllowedAppsPolicy.isNeverAllowed(packageName)
        ) {
          return@mapNotNull null
        }

        val label =
          runCatching {
            resolveInfo.loadLabel(packageManager).toString().trim()
          }.getOrDefault("")

        if (label.isBlank()) {
          null
        } else {
          InstalledAppInfo(packageName = packageName, label = label)
        }
      }
      .distinctBy { it.packageName }
      .sortedWith(
        compareBy<InstalledAppInfo> { it.label.lowercase() }
          .thenBy { it.packageName },
      )
  }

  private companion object {
    const val TAG = "PhoneGuardAppInventory"
  }
}
