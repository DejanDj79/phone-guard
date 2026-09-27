package com.example.phoneguard.core

data class InstalledAppInfo(
  val packageName: String,
  val label: String,
  val iconBase64: String? = null,
) {
  init {
    require(packageName.isNotBlank()) { "packageName must not be blank." }
    require(label.isNotBlank()) { "label must not be blank." }
    require(iconBase64 == null || iconBase64.length <= 16_384) {
      "iconBase64 is too large."
    }
  }
}

data class AllowedAppsSnapshot(
  val installedApps: List<InstalledAppInfo>,
  val allowedPackages: Set<String>,
  val version: Long,
) {
  init {
    require(version >= 0L) { "version must not be negative." }
  }
}
