package com.example.phoneguard.parent.ui

import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.phoneguard.parent.data.ChildLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
internal fun ParentLocationCard(
  deviceName: String,
  location: ChildLocation?,
  loading: Boolean,
  errorMessage: String?,
  notice: String?,
  onRefresh: () -> Unit,
) {
  val context = LocalContext.current
  var resolvedAddress by remember(location?.capturedAt) {
    mutableStateOf<String?>(null)
  }
  var addressLoading by remember(location?.capturedAt) {
    mutableStateOf(location != null)
  }

  LaunchedEffect(location?.capturedAt) {
    if (location == null) {
      resolvedAddress = null
      addressLoading = false
      return@LaunchedEffect
    }

    addressLoading = true
    resolvedAddress =
      withContext(Dispatchers.IO) {
        resolveStreetAddress(
          context = context.applicationContext,
          latitude = location.latitude,
          longitude = location.longitude,
        )
      }
    addressLoading = false
  }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, ParentBorderColor),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(
            text = "Location",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )

          Text(
            text =
              when {
                location == null -> "No location received yet."
                addressLoading -> "Finding street address…"
                resolvedAddress != null -> resolvedAddress!!
                else -> "Street address unavailable."
              },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        if (loading || addressLoading) {
          CircularProgressIndicator(
            modifier = Modifier.padding(start = 12.dp),
            strokeWidth = 2.dp,
            color = ParentAccentColor,
          )
        }
      }

      if (location != null) {
        ChildLocationMap(
          deviceName = deviceName,
          location = location,
        )

        Text(
          text =
            buildString {
              append("Updated ")
              append(formatProtectionEventTime(location.capturedAt))
              location.accuracyMeters?.let {
                append(" · Accuracy ±")
                append(Math.round(it))
                append(" m")
              }
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Surface(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(150.dp),
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          border = BorderStroke(1.dp, ParentBorderColor),
        ) {
          Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Refresh to get the Child phone's current location.",
              modifier = Modifier.padding(20.dp),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      OutlinedButton(
        onClick = onRefresh,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = ParentActionShape,
      ) {
        Text(if (loading) "LOCATING…" else "REFRESH LOCATION")
      }

      notice?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = ParentAccentColor,
        )
      }

      errorMessage?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun ChildLocationMap(
  deviceName: String,
  location: ChildLocation,
) {
  val context = LocalContext.current
  val point =
    LatLng(
      location.latitude,
      location.longitude,
    )

  val mapView =
    remember {
      MapLibre.getInstance(context.applicationContext)

      MapView(context).apply {
        onCreate(null)
      }
    }

  DisposableEffect(mapView) {
    mapView.onStart()
    mapView.onResume()

    onDispose {
      mapView.onPause()
      mapView.onStop()
      mapView.onDestroy()
    }
  }

  LaunchedEffect(
    mapView,
    location.latitude,
    location.longitude,
    deviceName,
  ) {
    mapView.getMapAsync { map ->
      val showLocation = {
        map.clear()

        map.addMarker(
          MarkerOptions()
            .position(point)
            .title(deviceName)
            .snippet("Current location"),
        )

        map.cameraPosition =
          CameraPosition.Builder()
            .target(point)
            .zoom(16.5)
            .build()

        map.uiSettings.apply {
          isCompassEnabled = false
          isAttributionEnabled = true
          isLogoEnabled = true
        }
      }

      if (map.style?.uri == OPEN_FREE_MAP_STYLE) {
        showLocation()
      } else {
        map.setStyle(
          Style.Builder().fromUri(OPEN_FREE_MAP_STYLE),
        ) {
          showLocation()
        }
      }
    }
  }

  AndroidView(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(220.dp)
        .clip(RoundedCornerShape(18.dp)),
    factory = { mapView },
  )
}

@Suppress("DEPRECATION")
private fun resolveStreetAddress(
  context: Context,
  latitude: Double,
  longitude: Double,
): String? {
  if (!Geocoder.isPresent()) return null

  val address =
    runCatching {
      Geocoder(context, Locale.getDefault())
        .getFromLocation(latitude, longitude, 1)
        ?.firstOrNull()
    }.getOrNull()
      ?: return null

  address.getAddressLine(0)
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?.let { return it }

  val street =
    listOfNotNull(
      address.thoroughfare?.trim()?.takeIf(String::isNotBlank),
      address.subThoroughfare?.trim()?.takeIf(String::isNotBlank),
    )
      .distinct()
      .joinToString(" ")

  val locality =
    listOfNotNull(
      address.postalCode?.trim()?.takeIf(String::isNotBlank),
      address.locality?.trim()?.takeIf(String::isNotBlank),
      address.adminArea?.trim()?.takeIf(String::isNotBlank),
    )
      .distinct()
      .joinToString(", ")

  return listOf(street, locality)
    .filter(String::isNotBlank)
    .joinToString(", ")
    .takeIf(String::isNotBlank)
}


private const val OPEN_FREE_MAP_STYLE =
  "https://tiles.openfreemap.org/styles/liberty"
