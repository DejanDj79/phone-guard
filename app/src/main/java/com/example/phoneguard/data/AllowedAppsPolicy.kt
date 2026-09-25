package com.example.phoneguard.data

object AllowedAppsPolicy {
  private val neverAllowedPackages =
    setOf(
      "com.android.settings",
      "com.android.packageinstaller",
      "com.google.android.packageinstaller",
      "com.android.permissioncontroller",
      "com.google.android.permissioncontroller",
      "com.miui.securitycenter",
      "com.miui.packageinstaller",
      "com.miui.home",
    )

  fun isNeverAllowed(packageName: String): Boolean =
    packageName in neverAllowedPackages
}
