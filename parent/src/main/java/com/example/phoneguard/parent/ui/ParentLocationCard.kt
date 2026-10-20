package com.example.phoneguard.parent.ui

import android.content.Context
import android.location.Geocoder
import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.phoneguard.core.GeofenceZone
import com.example.phoneguard.parent.R
import com.example.phoneguard.parent.data.ChildLocation
import com.example.phoneguard.parent.data.GeofenceEvent
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
import java.text.SimpleDateFormat
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
  geofenceEvents: List<GeofenceEvent>,
  geofenceEventsLoading: Boolean,
  geofenceEventsError: String?,
  onSaveGeofence: (String?, String, Int, Double, Double, Boolean) -> Unit,
  onSetGeofenceEnabled: (GeofenceZone, Boolean) -> Unit,
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
  var editingZoneId by remember { mutableStateOf<String?>(null) }
  var editingZoneEnabled by remember { mutableStateOf(true) }
  var pickerRecenterRequest by remember { mutableStateOf(0) }

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

  BoxWithConstraints(
    modifier = modifier.fillMaxSize(),
  ) {
    val activityPanelVisible =
      panelVisible && !geofencePickerActive
    val locationBarBottomPadding by
      animateDpAsState(
        targetValue =
          if (activityPanelVisible) {
            maxHeight * LOCATION_ACTIVITY_PANEL_FRACTION + 10.dp
          } else {
            102.dp
          },
        animationSpec = tween(280),
        label = "locationBarBottomPadding",
      )
    if (location != null) {
      ChildLocationMap(
        deviceName = deviceName,
        location = location,
        geofenceZones =
          geofenceZones.filter { zone ->
            zone.enabled &&
              !(
                geofencePickerActive &&
                  editingZoneId != null &&
                  zone.zoneId == editingZoneId
              )
          },
        panelVisible = panelVisible && !geofencePickerActive,
        pickerActive = geofencePickerActive,
        pickerLatitude = pickerLatitude,
        pickerLongitude = pickerLongitude,
        pickerRadiusMeters = pickerRadiusMeters,
        recenterRequest = pickerRecenterRequest,
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

    Row(
      modifier =
        Modifier
          .align(Alignment.TopCenter)
          .statusBarsPadding()
          .fillMaxWidth()
          .padding(
            start = 14.dp,
            end = 14.dp,
            top = 12.dp,
          ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Keep both header cards in the same row so they share a top edge and height.
      // The device name can shrink, while the refresh actions retain their full width.
      Surface(
        modifier =
          Modifier
            .weight(1f)
            .height(LOCATION_TOP_CARD_HEIGHT),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, ParentBorderColor),
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
      ) {
        Row(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = deviceName,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
            modifier = Modifier.size(14.dp),
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

      Surface(
        modifier = Modifier.height(LOCATION_TOP_CARD_HEIGHT),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, ParentBorderColor),
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
      ) {
        Row(
          modifier =
            Modifier
              .fillMaxHeight()
              .padding(
                start = 9.dp,
                end = 4.dp,
              ),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text =
              location
                ?.capturedAt
                ?.let(::formatLocationRefreshTime)
                ?: "—",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )

          IconButton(
            onClick = onRefresh,
            enabled = !loading,
            modifier = Modifier.size(34.dp),
          ) {
            if (loading) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = ParentAccentColor,
              )
            } else {
              Icon(
                painter = painterResource(R.drawable.pg_icon_refresh),
                contentDescription = "Refresh location",
                tint = ParentAccentColor,
                modifier = Modifier.size(18.dp),
              )
            }
          }

          IconButton(
            onClick = { showGeofenceManager = true },
            enabled = !geofenceLoading,
            modifier = Modifier.size(34.dp),
          ) {
            if (geofenceLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = ParentAccentColor,
              )
            } else {
              Icon(
                painter = painterResource(R.drawable.pg_icon_location),
                contentDescription = "Location zones",
                tint = ParentAccentColor,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }

    AnimatedVisibility(
      visible = activityPanelVisible,
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
            .fillMaxHeight(LOCATION_ACTIVITY_PANEL_FRACTION),
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
                top = 20.dp,
                bottom = 102.dp,
              ),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Text(
            text = "Recent zone activity",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )

          GeofenceActivityTimeline(
            events = geofenceEvents,
            loading = geofenceEventsLoading,
            errorMessage = geofenceEventsError,
          )
        }
      }
    }

    if (!geofencePickerActive) {
      Surface(
        modifier =
          Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(
              start = 16.dp,
              end = 16.dp,
              bottom = locationBarBottomPadding,
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, ParentBorderColor),
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
      ) {
        Row(
          modifier =
            Modifier.padding(
              horizontal = 14.dp,
              vertical = 11.dp,
            ),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text =
              when {
                location == null -> "Location unavailable"
                addressLoading -> "Finding street address…"
                resolvedAddress != null -> resolvedAddress!!
                else -> "Street address unavailable"
              },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color =
              if (errorMessage != null && location == null) {
                MaterialTheme.colorScheme.error
              } else {
                MaterialTheme.colorScheme.onSurface
              },
          )

          Text(
            text =
              location
                ?.accuracyMeters
                ?.let { "±" + Math.round(it) + " m" }
                ?: "—",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            text =
              if (editingZoneId == null) {
                "Choose zone location"
              } else {
                "Edit zone location"
              },
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
                pickerRecenterRequest += 1
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
                editingZoneId = null
                editingZoneEnabled = true
                pendingZoneName = ""
                showGeofenceManager = true
              },
              enabled = !geofenceSaving,
              modifier = Modifier.weight(1f).heightIn(min = 48.dp),
              shape = ParentActionShape,
            ) {
              Text("CANCEL")
            }

            OutlinedButton(
              onClick = {
                if (editingZoneId == null) {
                  pendingZoneName = ""
                }
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
          editingZoneId = null
          editingZoneEnabled = true
          pendingZoneName = ""
          pickerLatitude = currentLocation.latitude
          pickerLongitude = currentLocation.longitude
          pickerRadiusMeters = 100
          showGeofenceManager = false
          geofencePickerActive = true
          pickerRecenterRequest += 1
        }
      },
      onEdit = { zone ->
        editingZoneId = zone.zoneId
        editingZoneEnabled = zone.enabled
        pendingZoneName = zone.name
        pickerLatitude = zone.latitude
        pickerLongitude = zone.longitude
        pickerRadiusMeters = zone.radiusMeters
        showGeofenceManager = false
        geofencePickerActive = true
        pickerRecenterRequest += 1
      },
      onSetEnabled = onSetGeofenceEnabled,
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
          text =
            if (editingZoneId == null) {
              "Name location zone"
            } else {
              "Edit location zone"
            },
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
              onSaveGeofence(
                editingZoneId,
                pendingZoneName.trim(),
                pickerRadiusMeters,
                latitude,
                longitude,
                editingZoneEnabled,
              )
              showGeofenceNameDialog = false
              geofencePickerActive = false
              pendingZoneName = ""
              pickerLatitude = null
              pickerLongitude = null
              editingZoneId = null
              editingZoneEnabled = true
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
private fun GeofenceActivityTimeline(
  events: List<GeofenceEvent>,
  loading: Boolean,
  errorMessage: String?,
) {
  when {
    loading && events.isEmpty() -> {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          strokeWidth = 2.dp,
          color = ParentAccentColor,
        )
        Text(
          text = "Loading zone activity…",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    errorMessage != null && events.isEmpty() -> {
      Text(
        text = errorMessage,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }

    events.isEmpty() -> {
      Text(
        text = "No zone activity yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    else -> {
      Column(
        modifier = Modifier.fillMaxWidth(),
      ) {
        events.forEachIndexed { index, event ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Box(
              modifier =
                Modifier
                  .width(18.dp)
                  .height(62.dp),
            ) {
              if (index < events.lastIndex) {
                Surface(
                  modifier =
                    Modifier
                      .align(Alignment.TopCenter)
                      .padding(top = 10.dp)
                      .width(2.dp)
                      .height(52.dp),
                  color = ParentBorderColor.copy(alpha = 0.85f),
                ) {}
              }

              Surface(
                modifier =
                  Modifier
                    .align(Alignment.TopCenter)
                    .size(10.dp),
                shape = CircleShape,
                color = ParentAccentColor,
                border =
                  BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.surface,
                  ),
              ) {}
            }

            Column(
              modifier =
                Modifier
                  .weight(1f)
                  .padding(bottom = 14.dp),
              verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Text(
                text =
                  if (event.eventType == "ENTER") {
                    "Arrived at " + event.zoneName
                  } else {
                    "Left " + event.zoneName
                  },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = formatProtectionEventTime(event.occurredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        if (errorMessage != null) {
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
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
  onEdit: (GeofenceZone) -> Unit,
  onSetEnabled: (GeofenceZone, Boolean) -> Unit,
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
              Column(
                modifier =
                  Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 11.dp,
                  ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth(),
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

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                      text = if (zone.enabled) "ACTIVE" else "PAUSED",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color =
                        if (zone.enabled) {
                          ParentAccentColor
                        } else {
                          MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )

                    Switch(
                      checked = zone.enabled,
                      onCheckedChange = { enabled ->
                        onSetEnabled(zone, enabled)
                      },
                      enabled = !saving,
                    )
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedButton(
                    onClick = { onEdit(zone) },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                    shape = ParentActionShape,
                  ) {
                    Text("EDIT")
                  }

                  OutlinedButton(
                    onClick = { onDelete(zone.zoneId) },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                    shape = ParentActionShape,
                  ) {
                    Text("DELETE")
                  }
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
private fun ChildLocationMap(
  deviceName: String,
  location: ChildLocation,
  geofenceZones: List<GeofenceZone>,
  panelVisible: Boolean,
  pickerActive: Boolean,
  pickerLatitude: Double?,
  pickerLongitude: Double?,
  pickerRadiusMeters: Int,
  recenterRequest: Int,
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
    recenterRequest,
  ) {
    if (!pickerActive || recenterRequest <= 0) {
      return@LaunchedEffect
    }

    val latitude = pickerLatitude ?: point.latitude
    val longitude = pickerLongitude ?: point.longitude
    val target = LatLng(latitude, longitude)

    mapView.getMapAsync { map ->
      map.cameraPosition =
        CameraPosition.Builder(map.cameraPosition)
          .target(target)
          .padding(
            0.0,
            0.0,
            0.0,
            0.0,
          )
          .build()

      onPickerCenterChange(
        target.latitude,
        target.longitude,
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
                mapView.height * LOCATION_MAP_BOTTOM_PADDING_FRACTION
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

private fun formatLocationRefreshTime(
  rawTimestamp: String,
): String {
  val parsers =
    listOf(
      "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
      "yyyy-MM-dd'T'HH:mm:ssXXX",
      "yyyy-MM-dd'T'HH:mm:ss.SSSX",
      "yyyy-MM-dd'T'HH:mm:ssX",
    )

  val parsed =
    parsers.firstNotNullOfOrNull { pattern ->
      runCatching {
        SimpleDateFormat(
          pattern,
          Locale.US,
        ).apply {
          isLenient = false
        }.parse(rawTimestamp)
      }.getOrNull()
    }

  return parsed
    ?.let {
      SimpleDateFormat(
        "HH:mm",
        Locale.getDefault(),
      ).format(it)
    }
    ?: "—"
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

  val street =
    address.thoroughfare
      ?.trim()
      ?.takeIf(String::isNotBlank)

  val number =
    address.subThoroughfare
      ?.trim()
      ?.takeIf(String::isNotBlank)

  return listOfNotNull(street, number)
    .distinct()
    .joinToString(" ")
    .takeIf(String::isNotBlank)
}

private val LOCATION_TOP_CARD_HEIGHT = 42.dp

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
private const val LOCATION_ACTIVITY_PANEL_FRACTION = 0.42f
private const val LOCATION_MAP_BOTTOM_PADDING_FRACTION = 0.48
private const val DUPLICATE_MARKER_DISTANCE_METERS = 10f
