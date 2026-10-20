package com.example.phoneguard.parent.ui

import android.content.Context
import android.location.Geocoder
import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.phoneguard.core.GeofenceZone
import com.example.phoneguard.parent.R
import com.example.phoneguard.parent.data.ChildLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.atan2
import kotlin.math.asin
import org.json.JSONObject
import org.json.JSONArray
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.FillLayer
import java.util.Locale

@Composable
internal fun ParentLocationScreen(
  deviceName: String,
  batteryLevelPercent: Int?,
  location: ChildLocation?,
  loading: Boolean,
  errorMessage: String?,
  notice: String?,
  panelVisible: Boolean,
  geofenceZones: List<GeofenceZone>,
  geofenceLoading: Boolean,
  geofenceSaving: Boolean,
  geofenceError: String?,
  geofenceNotice: String?,
  onAddGeofence: (String, Int, Double, Double) -> Unit,
  onDeleteGeofence: (String) -> Unit,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var resolvedAddress by remember(location?.capturedAt) {
    mutableStateOf<String?>(null)
  }
  var addressLoading by remember(location?.capturedAt) {
    mutableStateOf(location != null)
  }
  var showGeofenceManager by remember { mutableStateOf(false) }
  var geofencePickerActive by remember { mutableStateOf(false) }
  var pickerLatitude by remember { mutableStateOf<Double?>(null) }
  var pickerLongitude by remember { mutableStateOf<Double?>(null) }
  var pickerRadiusMeters by remember { mutableStateOf(100) }
  var showGeofenceNameDialog by remember { mutableStateOf(false) }
  var pendingZoneName by remember { mutableStateOf("") }
  var pickerSnapToChildRequest by remember { mutableStateOf(0) }

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

  Box(
    modifier = modifier.fillMaxSize(),
  ) {
    if (location != null) {
      ChildLocationMap(
        deviceName = deviceName,
        location = location,
        geofenceZones = geofenceZones,
        panelVisible = panelVisible && !geofencePickerActive,
        pickerActive = geofencePickerActive,
        pickerLatitude = pickerLatitude,
        pickerLongitude = pickerLongitude,
        pickerRadiusMeters = pickerRadiusMeters,
        snapToChildRequest = pickerSnapToChildRequest,
        onPickerCenterChange = { latitude, longitude ->
          pickerLatitude = latitude
          pickerLongitude = longitude
        },
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Surface(
        modifier = Modifier.fillMaxSize(),
        color = ParentGradientEdge,
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = "Refresh to locate this Child phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }

    Surface(
      modifier =
        Modifier
          .align(Alignment.TopStart)
          .statusBarsPadding()
          .padding(start = 14.dp, top = 12.dp, end = 76.dp),
      shape = RoundedCornerShape(14.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
      border = BorderStroke(1.dp, ParentBorderColor),
      shadowElevation = 6.dp,
      tonalElevation = 0.dp,
    ) {
      Row(
        modifier =
          Modifier.padding(
            horizontal = 11.dp,
            vertical = 7.dp,
          ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = deviceName,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )

        Surface(
          modifier =
            Modifier
              .height(18.dp)
              .width(1.dp),
          color = ParentBorderColor.copy(alpha = 0.65f),
        ) {}

        Icon(
          painter = painterResource(R.drawable.pg_icon_battery),
          contentDescription = "Battery",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.height(14.dp),
        )

        Text(
          text =
            batteryLevelPercent
              ?.let { it.toString() + "%" }
              ?: "—",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }

    AnimatedVisibility(
      visible = panelVisible && !geofencePickerActive,
      modifier = Modifier.align(Alignment.BottomCenter),
      enter =
        slideInVertically(
          animationSpec = tween(280),
          initialOffsetY = { fullHeight -> fullHeight },
        ) + fadeIn(animationSpec = tween(180)),
      exit =
        slideOutVertically(
          animationSpec = tween(240),
          targetOffsetY = { fullHeight -> fullHeight },
        ) + fadeOut(animationSpec = tween(160)),
    ) {
      Surface(
        modifier =
          Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.56f),
        shape =
          RoundedCornerShape(
            topStart = 32.dp,
            topEnd = 32.dp,
          ),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 18.dp,
        tonalElevation = 0.dp,
        border =
          BorderStroke(
            width = 1.dp,
            color = ParentBorderColor,
          ),
      ) {
        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(
                start = 22.dp,
                end = 22.dp,
                top = 12.dp,
                bottom = 102.dp,
              ),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            modifier =
              Modifier
                .align(Alignment.CenterHorizontally)
                .width(42.dp)
                .height(4.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
          ) {}

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = "Current location",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
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
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              LocationInfoPill(
                label = "UPDATED",
                value = formatProtectionEventTime(location.capturedAt),
                modifier = Modifier.weight(1f),
              )
              LocationInfoPill(
                label = "ACCURACY",
                value =
                  location.accuracyMeters?.let {
                    "±" + Math.round(it) + " m"
                  } ?: "—",
                modifier = Modifier.weight(1f),
              )
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

          OutlinedButton(
            onClick = { showGeofenceManager = true },
            enabled = !geofenceLoading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = ParentActionShape,
          ) {
            Text(
              if (geofenceLoading) {
                "LOADING ZONES…"
              } else {
                "LOCATION ZONES (" + geofenceZones.size + ")"
              },
            )
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

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = "Map © OpenStreetMap contributors · OpenFreeMap",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          )
        }
      }
    }
    if (geofencePickerActive) {
      Surface(
        modifier =
          Modifier
            .align(Alignment.Center)
            .size(36.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(2.dp, ParentAccentColor),
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
      ) {
        Box(contentAlignment = Alignment.Center) {
          Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = ParentAccentColor,
          ) {}
        }
      }

      Surface(
        modifier =
          Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(
              start = 16.dp,
              end = 16.dp,
              bottom = 104.dp,
            ),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, ParentBorderColor),
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Choose zone location",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Move the map until the target is centered on the place you want to use.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          OutlinedButton(
            onClick = {
              val currentLocation = location
              if (currentLocation != null) {
                pickerLatitude = currentLocation.latitude
                pickerLongitude = currentLocation.longitude
                pickerSnapToChildRequest += 1
              }
            },
            enabled = !geofenceSaving && location != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = ParentActionShape,
            border = BorderStroke(1.dp, ParentAccentColor),
          ) {
            Text("USE CHILD LOCATION")
          }

          RadiusChoiceRow(
            values = listOf(50, 100, 200),
            selected = pickerRadiusMeters,
            enabled = !geofenceSaving,
            onSelected = { pickerRadiusMeters = it },
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = {
                geofencePickerActive = false
                pickerLatitude = null
                pickerLongitude = null
              },
              enabled = !geofenceSaving,
              modifier = Modifier.weight(1f).heightIn(min = 48.dp),
              shape = ParentActionShape,
            ) {
              Text("CANCEL")
            }

            OutlinedButton(
              onClick = {
                pendingZoneName = ""
                showGeofenceNameDialog = true
              },
              enabled =
                !geofenceSaving &&
                  pickerLatitude != null &&
                  pickerLongitude != null,
              modifier = Modifier.weight(1f).heightIn(min = 48.dp),
              shape = ParentActionShape,
              border = BorderStroke(1.dp, ParentAccentColor),
            ) {
              Text("NEXT")
            }
          }
        }
      }
    }
  }

  if (showGeofenceManager) {
    GeofenceManagerDialog(
      zones = geofenceZones,
      loading = geofenceLoading,
      saving = geofenceSaving,
      errorMessage = geofenceError,
      notice = geofenceNotice,
      currentLocationAvailable = location != null,
      onStartAdd = {
        val currentLocation = location
        if (currentLocation != null) {
          pickerLatitude = currentLocation.latitude
          pickerLongitude = currentLocation.longitude
          pickerRadiusMeters = 100
          showGeofenceManager = false
          geofencePickerActive = true
        }
      },
      onDelete = onDeleteGeofence,
      onDismiss = { showGeofenceManager = false },
    )
  }

  if (showGeofenceNameDialog) {
    AlertDialog(
      onDismissRequest = {
        if (!geofenceSaving) {
          showGeofenceNameDialog = false
        }
      },
      title = {
        Text(
          text = "Name location zone",
          fontWeight = FontWeight.Bold,
        )
      },
      text = {
        OutlinedTextField(
          value = pendingZoneName,
          onValueChange = { pendingZoneName = it.take(60) },
          label = { Text("Zone name") },
          placeholder = { Text("Home, School…") },
          singleLine = true,
          enabled = !geofenceSaving,
          modifier = Modifier.fillMaxWidth(),
          shape = ParentActionShape,
        )
      },
      confirmButton = {
        OutlinedButton(
          onClick = {
            val latitude = pickerLatitude
            val longitude = pickerLongitude

            if (latitude != null && longitude != null) {
              onAddGeofence(
                pendingZoneName.trim(),
                pickerRadiusMeters,
                latitude,
                longitude,
              )
              showGeofenceNameDialog = false
              geofencePickerActive = false
              pendingZoneName = ""
              pickerLatitude = null
              pickerLongitude = null
            }
          },
          enabled =
            !geofenceSaving &&
              pendingZoneName.trim().isNotEmpty() &&
              pickerLatitude != null &&
              pickerLongitude != null,
          shape = ParentActionShape,
        ) {
          Text(if (geofenceSaving) "SAVING…" else "SAVE")
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = {
            if (!geofenceSaving) {
              showGeofenceNameDialog = false
            }
          },
          enabled = !geofenceSaving,
          shape = ParentActionShape,
        ) {
          Text("BACK")
        }
      },
    )
  }
}

@Composable
private fun GeofenceManagerDialog(
  zones: List<GeofenceZone>,
  loading: Boolean,
  saving: Boolean,
  errorMessage: String?,
  notice: String?,
  currentLocationAvailable: Boolean,
  onStartAdd: () -> Unit,
  onDelete: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = {
      if (!saving) onDismiss()
    },
    title = {
      Text(
        text = "Location zones",
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (loading) {
          CircularProgressIndicator(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = ParentAccentColor,
          )
        } else if (zones.isEmpty()) {
          Text(
            text = "No saved location zones yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          zones.forEach { zone ->
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              border = BorderStroke(1.dp, ParentBorderColor),
            ) {
              Row(
                modifier =
                  Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 11.dp,
                  ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  Text(
                    text = zone.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = "Radius " + zone.radiusMeters + " m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                OutlinedButton(
                  onClick = { onDelete(zone.zoneId) },
                  enabled = !saving,
                  shape = ParentActionShape,
                ) {
                  Text("DELETE")
                }
              }
            }
          }
        }

        if (!currentLocationAvailable) {
          Text(
            text = "Refresh the Child location before choosing a new zone on the map.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        notice?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = ParentAccentColor,
          )
        }

        errorMessage?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      OutlinedButton(
        onClick = onStartAdd,
        enabled = !saving && currentLocationAvailable,
        shape = ParentActionShape,
      ) {
        Text("ADD ZONE")
      }
    },
    dismissButton = {
      OutlinedButton(
        onClick = onDismiss,
        enabled = !saving,
        shape = ParentActionShape,
      ) {
        Text("CLOSE")
      }
    },
  )
}

@Composable
private fun RadiusChoiceRow(
  values: List<Int>,
  selected: Int,
  enabled: Boolean,
  onSelected: (Int) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    values.forEach { radius ->
      OutlinedButton(
        onClick = { onSelected(radius) },
        enabled = enabled,
        modifier = Modifier.weight(1f),
        shape = ParentActionShape,
        border =
          BorderStroke(
            1.dp,
            if (selected == radius) {
              ParentAccentColor
            } else {
              ParentBorderColor
            },
          ),
      ) {
        Text(radius.toString() + " m")
      }
    }
  }
}

@Composable
private fun LocationInfoPill(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, ParentBorderColor),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun ChildLocationMap(
  deviceName: String,
  location: ChildLocation,
  geofenceZones: List<GeofenceZone>,
  panelVisible: Boolean,
  pickerActive: Boolean,
  pickerLatitude: Double?,
  pickerLongitude: Double?,
  pickerRadiusMeters: Int,
  snapToChildRequest: Int,
  onPickerCenterChange: (Double, Double) -> Unit,
  modifier: Modifier = Modifier,
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

  DisposableEffect(
    mapView,
    pickerActive,
  ) {
    var mapRef: MapLibreMap? = null

    val listener =
      MapLibreMap.OnCameraIdleListener {
        if (pickerActive) {
          val target = mapRef?.cameraPosition?.target
          if (target != null) {
            onPickerCenterChange(
              target.latitude,
              target.longitude,
            )
          }
        }
      }

    mapView.getMapAsync { map ->
      mapRef = map
      map.addOnCameraIdleListener(listener)

      if (pickerActive) {
        val target = map.cameraPosition.target
        if (target != null) {
          onPickerCenterChange(
            target.latitude,
            target.longitude,
          )
        }
      }
    }

    onDispose {
      mapRef?.removeOnCameraIdleListener(listener)
    }
  }

  LaunchedEffect(
    mapView,
    pickerActive,
    snapToChildRequest,
  ) {
    if (!pickerActive || snapToChildRequest <= 0) {
      return@LaunchedEffect
    }

    mapView.getMapAsync { map ->
      map.cameraPosition =
        CameraPosition.Builder(map.cameraPosition)
          .target(point)
          .padding(
            0.0,
            0.0,
            0.0,
            0.0,
          )
          .build()

      onPickerCenterChange(
        point.latitude,
        point.longitude,
      )
    }
  }



  LaunchedEffect(
    mapView,
    location.latitude,
    location.longitude,
    deviceName,
    geofenceZones,
    panelVisible,
    pickerActive,
    pickerLatitude,
    pickerLongitude,
    pickerRadiusMeters,
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

        geofenceZones
          .filter { zone ->
            distanceMeters(
              latitude1 = location.latitude,
              longitude1 = location.longitude,
              latitude2 = zone.latitude,
              longitude2 = zone.longitude,
            ) > DUPLICATE_MARKER_DISTANCE_METERS
          }
          .forEach { zone ->
            map.addMarker(
              MarkerOptions()
                .position(
                  LatLng(
                    zone.latitude,
                    zone.longitude,
                  ),
                )
                .title(zone.name)
                .snippet("Location zone · " + zone.radiusMeters + " m"),
            )
          }

        map.style?.let { style ->
          updateGeofenceLayers(
            style = style,
            zones = geofenceZones,
            previewLatitude =
              if (pickerActive) pickerLatitude else null,
            previewLongitude =
              if (pickerActive) pickerLongitude else null,
            previewRadiusMeters =
              if (pickerActive) pickerRadiusMeters else null,
          )
        }

        mapView.post {
          if (pickerActive) {
            val currentCamera = map.cameraPosition
            map.cameraPosition =
              CameraPosition.Builder(currentCamera)
                .padding(
                  0.0,
                  0.0,
                  0.0,
                  0.0,
                )
                .build()
          } else {
            val bottomPadding =
              if (panelVisible) {
                mapView.height * 0.56
              } else {
                0.0
              }

            map.cameraPosition =
              CameraPosition.Builder()
                .target(point)
                .zoom(16.5)
                .padding(
                  0.0,
                  0.0,
                  0.0,
                  bottomPadding,
                )
                .build()
          }
        }

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
    modifier = modifier,
    factory = { mapView },
  )
}

private fun distanceMeters(
  latitude1: Double,
  longitude1: Double,
  latitude2: Double,
  longitude2: Double,
): Float {
  val result = FloatArray(1)
  Location.distanceBetween(
    latitude1,
    longitude1,
    latitude2,
    longitude2,
    result,
  )
  return result[0]
}

private fun updateGeofenceLayers(
  style: Style,
  zones: List<GeofenceZone>,
  previewLatitude: Double? = null,
  previewLongitude: Double? = null,
  previewRadiusMeters: Int? = null,
) {
  style.getLayer(GEOFENCE_FILL_LAYER_ID)?.let {
    style.removeLayer(GEOFENCE_FILL_LAYER_ID)
  }
  style.getLayer(GEOFENCE_LINE_LAYER_ID)?.let {
    style.removeLayer(GEOFENCE_LINE_LAYER_ID)
  }
  style.getSource(GEOFENCE_SOURCE_ID)?.let {
    style.removeSource(GEOFENCE_SOURCE_ID)
  }

  if (
    zones.isEmpty() &&
    (
      previewLatitude == null ||
      previewLongitude == null ||
      previewRadiusMeters == null
    )
  ) {
    return
  }

  style.addSource(
    GeoJsonSource(
      GEOFENCE_SOURCE_ID,
      geofenceGeoJson(
        zones = zones,
        previewLatitude = previewLatitude,
        previewLongitude = previewLongitude,
        previewRadiusMeters = previewRadiusMeters,
      ),
    ),
  )

  style.addLayer(
    FillLayer(
      GEOFENCE_FILL_LAYER_ID,
      GEOFENCE_SOURCE_ID,
    ).withProperties(
      PropertyFactory.fillColor("#69DEFF"),
      PropertyFactory.fillOpacity(0.16f),
      PropertyFactory.fillOutlineColor("#69DEFF"),
    ),
  )

  style.addLayer(
    LineLayer(
      GEOFENCE_LINE_LAYER_ID,
      GEOFENCE_SOURCE_ID,
    ).withProperties(
      PropertyFactory.lineColor("#69DEFF"),
      PropertyFactory.lineWidth(2.2f),
      PropertyFactory.lineOpacity(0.95f),
    ),
  )
}

private fun geofenceGeoJson(
  zones: List<GeofenceZone>,
  previewLatitude: Double? = null,
  previewLongitude: Double? = null,
  previewRadiusMeters: Int? = null,
): String {
  val features = JSONArray()

  zones.forEach { zone ->
    features.put(
      geofenceCircleFeature(
        zoneId = zone.zoneId,
        name = zone.name,
        latitude = zone.latitude,
        longitude = zone.longitude,
        radiusMeters = zone.radiusMeters,
        preview = false,
      ),
    )
  }

  if (
    previewLatitude != null &&
    previewLongitude != null &&
    previewRadiusMeters != null
  ) {
    features.put(
      geofenceCircleFeature(
        zoneId = "preview",
        name = "New zone",
        latitude = previewLatitude,
        longitude = previewLongitude,
        radiusMeters = previewRadiusMeters,
        preview = true,
      ),
    )
  }

  return JSONObject()
    .put("type", "FeatureCollection")
    .put("features", features)
    .toString()
}

private fun geofenceCircleFeature(
  zoneId: String,
  name: String,
  latitude: Double,
  longitude: Double,
  radiusMeters: Int,
  preview: Boolean,
): JSONObject {
  val ring = JSONArray()
  val latitudeRadians = Math.toRadians(latitude)
  val longitudeRadians = Math.toRadians(longitude)
  val angularDistance =
    radiusMeters.toDouble() / EARTH_RADIUS_METERS

  for (step in 0..GEOFENCE_CIRCLE_STEPS) {
    val bearing =
      2.0 * Math.PI * step.toDouble() /
        GEOFENCE_CIRCLE_STEPS.toDouble()

    val destinationLatitude =
      asin(
        sin(latitudeRadians) * cos(angularDistance) +
          cos(latitudeRadians) *
            sin(angularDistance) *
            cos(bearing),
      )

    val destinationLongitude =
      longitudeRadians +
        atan2(
          sin(bearing) *
            sin(angularDistance) *
            cos(latitudeRadians),
          cos(angularDistance) -
            sin(latitudeRadians) *
              sin(destinationLatitude),
        )

    ring.put(
      JSONArray()
        .put(Math.toDegrees(destinationLongitude))
        .put(Math.toDegrees(destinationLatitude)),
    )
  }

  return JSONObject()
    .put("type", "Feature")
    .put(
      "properties",
      JSONObject()
        .put("zoneId", zoneId)
        .put("name", name)
        .put("radiusMeters", radiusMeters)
        .put("preview", preview),
    )
    .put(
      "geometry",
      JSONObject()
        .put("type", "Polygon")
        .put(
          "coordinates",
          JSONArray().put(ring),
        ),
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

private const val GEOFENCE_SOURCE_ID =
  "phoneguard-geofence-zones"
private const val GEOFENCE_FILL_LAYER_ID =
  "phoneguard-geofence-fill"
private const val GEOFENCE_LINE_LAYER_ID =
  "phoneguard-geofence-line"
private const val GEOFENCE_CIRCLE_STEPS = 64
private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val DUPLICATE_MARKER_DISTANCE_METERS = 10f
