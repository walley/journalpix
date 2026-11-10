package com.journalpix.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.journalpix.api.ApiService
import com.journalpix.models.PhotoResponse
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.models.UpdatePlaceRequest
import com.journalpix.models.ZonedTimeInput
import com.journalpix.ui.theme.JournalPixTheme
import com.journalpix.viewmodel.AppSession
import com.journalpix.viewmodel.AuthViewModel
import com.journalpix.viewmodel.PhotoViewModel
import com.journalpix.viewmodel.PlaceViewModel
import de.afarber.openmapview.CameraPosition
import de.afarber.openmapview.LatLng
import de.afarber.openmapview.MapType
import de.afarber.openmapview.compose.MapProperties
import de.afarber.openmapview.compose.MapUiSettings
import de.afarber.openmapview.compose.MarkerComposable
import de.afarber.openmapview.compose.OpenMap
import de.afarber.openmapview.compose.rememberCameraPositionState
import de.afarber.openmapview.compose.rememberMarkerState
import java.time.ZoneId

private const val TAG_PLACE_DETAIL = "JPXPDS"

/** Shimmer skeleton box that animates while content is loading. */
@Composable
private fun SkeletonBox(
  modifier: Modifier = Modifier,
  shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
  val transition = rememberInfiniteTransition(label = "shimmer")
  val alpha by transition.animateFloat(
    initialValue = 0.3f,
    targetValue = 0.7f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1000, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "shimmerAlpha"
  )
  Box(
    modifier = modifier
      .background(
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha),
        shape
      )
  )
}

/** Full skeleton layout shown while place data is loading (also renders in @Preview). */
@Composable
private fun PlaceDetailSkeleton() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Title skeleton
    SkeletonBox(
      modifier = Modifier
        .fillMaxWidth(0.6f)
        .height(32.dp)
    )
    // Address row skeleton
    SkeletonBox(
      modifier = Modifier
        .fillMaxWidth(0.8f)
        .height(20.dp)
    )
    // Lat/lon row skeleton
    SkeletonBox(
      modifier = Modifier
        .fillMaxWidth(0.5f)
        .height(20.dp)
    )
    // Map skeleton
    SkeletonBox(
      modifier = Modifier
        .fillMaxWidth()
        .height(200.dp),
      shape = RoundedCornerShape(12.dp)
    )
    // Photos header skeleton
    SkeletonBox(
      modifier = Modifier
        .fillMaxWidth(0.4f)
        .height(24.dp)
    )
    // Photos grid skeleton
    LazyVerticalGrid(
      columns = GridCells.Fixed(3),
      modifier = Modifier
        .fillMaxWidth()
        .height(220.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      userScrollEnabled = false
    ) {
      items(6) {
        SkeletonBox(
          modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
  navController: NavController,
  placeId: String,
  tripId: String = "",
  newPlaceLat: Double? = null,
  newPlaceLon: Double? = null,
  newPlaceDay: String? = null
) {
  val context = LocalContext.current
  // No token here — offline-first; SyncManager is the only place that uses the auth token

  // The nav route may not carry tripId — fall back to the currently open trip
  val effectiveTripId = tripId.ifBlank { AppSession.currentTripId.value }

  // Create mode: opened via the place_new route (no place id yet) — from the map with
  // picked coords, or straight from the timeline. This screen asks for a name, creates
  // the place, then stays open for editing
  val isCreateMode = placeId.isBlank()
  Log.i(TAG_PLACE_DETAIL, "PlaceDetailScreen: placeId='$placeId', tripId='$effectiveTripId', isCreateMode=$isCreateMode, newLat=$newPlaceLat, newLon=$newPlaceLon")

  val placeViewModel: PlaceViewModel = remember { PlaceViewModel() }
  val photoViewModel: PhotoViewModel = remember { PhotoViewModel() }

  val placeState = placeViewModel.currentPlace
  val currentPlace = placeState.value
  // Show skeleton when no data yet (also in @Preview where currentPlace is null)
  val showSkeleton = currentPlace == null

  var editMode by remember { mutableStateOf(false) }
  var editName by remember { mutableStateOf("") }
  var editAddress by remember { mutableStateOf("") }

  var showDeleteDialog by remember { mutableStateOf(false) }

  // Create-mode name prompt
  var showNameDialog by remember { mutableStateOf(false) }
  var newPlaceName by remember { mutableStateOf("") }

  // Entering create mode → ask for the place name right away
  LaunchedEffect(isCreateMode) {
    if (isCreateMode) {
      Log.i(TAG_PLACE_DETAIL, "Create mode entered with lat=$newPlaceLat lon=$newPlaceLon — asking for a name")
      showNameDialog = true
    }
  }

  var showImageViewer by remember { mutableStateOf(false) }
  var currentImageUrl by remember { mutableStateOf("") }
  var currentCaption by remember { mutableStateOf("") }

  var mapSelection by remember { mutableStateOf<Pair<Double, Double>?>(null) }
  var mapSelectionAddress by remember { mutableStateOf<String?>(null) }

  var placeRefreshKey by remember { mutableStateOf(0) }

  LaunchedEffect(placeState.value) {
    placeRefreshKey += 1
  }

  LaunchedEffect(currentPlace) {
    if (currentPlace != null) {
      editName = currentPlace.name
      editAddress = AppSession.effectiveAddress(currentPlace.name, currentPlace.address) ?: ""
    }
  }

  LaunchedEffect(AppSession.pendingMapPlaceUpdate.value) {
    if (isCreateMode) return@LaunchedEffect // create mode never applies location updates
    val pending = AppSession.pendingMapPlaceUpdate.value
    Log.i(
      TAG_PLACE_DETAIL,
      "pendingMapPlaceUpdate effect fired: pending=$pending, placeId=$placeId, currentPlace lat=${currentPlace?.latitude} lon=${currentPlace?.longitude}"
    )
    if (pending != null) {
      val (lat, lon, name) = pending
      Log.i(
        TAG_PLACE_DETAIL,
        "Pending map place update: lat=$lat, lon=$lon, name='$name' for placeId=$placeId"
      )
      AppSession.clearPendingMapPlaceUpdate()
      // Defensive: an edit consumes any stale pending creation so no duplicate place appears
      AppSession.clearPendingMapPlace()
      AppSession.mapEditPlaceId.value = null
      editMode = true
      // A map location update must NEVER change the place name — keep the user-entered name
      editName = currentPlace?.name ?: ""
      editAddress = ""
      placeViewModel.updatePlace(
        placeId = placeId,
        request = UpdatePlaceRequest(
          name = null,
          description = null,
          latitude = lat,
          longitude = lon,
          address = null,
          day = null,
          visitDate = null,
          orderIndex = null,
          transportFromPrevious = null
        ),
        onSuccess = {
          Log.i(TAG_PLACE_DETAIL, "Place updated from map selection")
          placeRefreshKey += 1
          editMode = false
          placeViewModel.refreshLocalPlace(placeId)
        }
      )
      photoViewModel.reverseGeocode(
        effectiveTripId, PhotoResponse(
          id = "temp",
          placeId = placeId,
          userId = "",
          filePath = "",
          thumbnailPath = "",
          latitude = lat,
          longitude = lon,
          takenAt = null,
          caption = null,
          orderIndex = 0,
          createdAt = ""
        )
      ) {
        Log.i(TAG_PLACE_DETAIL, "Reverse geocode complete for map selection")
      }
    }
  }

  LaunchedEffect(photoViewModel.resolvedAddresses["temp"]) {
    val addr = photoViewModel.resolvedAddresses["temp"]
    if (addr != null && addr.isNotBlank()) {
      Log.i(TAG_PLACE_DETAIL, "Reverse geocoded address after map move: $addr")
      editAddress = addr
      // Persist the new address on the place (map move changed lat/lon → new address)
      placeViewModel.updatePlace(
        placeId = placeId,
        request = UpdatePlaceRequest(
          name = null,
          description = null,
          latitude = currentPlace?.latitude,
          longitude = currentPlace?.longitude,
          address = addr,
          day = null,
          visitDate = null,
          orderIndex = null,
          transportFromPrevious = null
        ),
        onSuccess = {
          Log.i(TAG_PLACE_DETAIL, "Address saved to place after map move: $addr")
          placeViewModel.refreshLocalPlace(placeId)
        }
      )
    }
    photoViewModel.resolvedAddresses.remove("temp")
  }

  LaunchedEffect(placeId) {
    if (placeId.isBlank()) return@LaunchedEffect // create mode — nothing to load yet
    Log.i(
      TAG_PLACE_DETAIL,
      "Loading place: placeId=$placeId, tripId='$effectiveTripId', pendingUpdate=${AppSession.pendingMapPlaceUpdate.value != null}"
    )
    placeViewModel.getPlace(placeId)
    photoViewModel.getPhotosForPlace(placeId)
  }

  placeViewModel.errorMessage.value?.let { error ->
    Log.e(TAG_PLACE_DETAIL, "Place load error: $error")
  }

  val pickImageLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        context.contentResolver.takePersistableUriPermission(
          uri,
          android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      } catch (_: Exception) {
      }
      photoViewModel.addPhotoOffline(context, placeId, uri) {
        Log.i(TAG_PLACE_DETAIL, "Photo added to place $placeId")
        photoViewModel.getPhotosForPlace(placeId)
      }
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { _ -> pickImageLauncher.launch("image/*") }

  fun startAddPhoto() {
    val needed = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      if (ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.ACCESS_MEDIA_LOCATION
        )
        != PackageManager.PERMISSION_GRANTED
      ) needed.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
        != PackageManager.PERMISSION_GRANTED
      ) needed.add(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
      if (ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.READ_EXTERNAL_STORAGE
        )
        != PackageManager.PERMISSION_GRANTED
      ) needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    else pickImageLauncher.launch("image/*")
  }

  fun deletePhoto(photoId: String) {
    Log.w(TAG_PLACE_DETAIL, "Deleting photo: photoId=$photoId from place=$placeId")
    photoViewModel.deletePhoto(photoId) {
      Log.i(TAG_PLACE_DETAIL, "Photo deleted: photoId=$photoId")
      photoViewModel.getPhotosForPlace(placeId)
      placeViewModel.getPlace(placeId)
    }
  }

  fun deletePlace() {
    Log.w(TAG_PLACE_DETAIL, "Deleting whole place: placeId=$placeId")
    placeViewModel.deletePlace(placeId) {
      Log.i(TAG_PLACE_DETAIL, "Place deleted: placeId=$placeId")
      navController.popBackStack()
    }
  }

  val visitLabel = currentPlace?.visitLabel()
  val visitDateText = if (visitLabel != null) "Visit: $visitLabel" else null

  val mapCameraState = rememberCameraPositionState {
    position = if (currentPlace?.latitude != null && currentPlace?.longitude != null) {
      CameraPosition(
        LatLng(currentPlace?.latitude ?: 0.0, currentPlace?.longitude ?: 0.0),
        17f
      )
    } else {
      CameraPosition(LatLng(0.0, 0.0), 3f)
    }
  }

  // Center + zoom the minimap on the place's coordinates once they are known (async load)
  LaunchedEffect(currentPlace?.latitude, currentPlace?.longitude) {
    val lat = currentPlace?.latitude
    val lon = currentPlace?.longitude
    if (lat != null && lon != null) {
      Log.i(TAG_PLACE_DETAIL, "Minimap: centering on place lat=$lat lon=$lon zoom=17")
      mapCameraState.position = CameraPosition(LatLng(lat, lon), 17f)
    }
  }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(if (isCreateMode) "New place" else currentPlace?.name ?: "Place") },
        navigationIcon = {
          IconButton(onClick = { navController.popBackStack() }) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (!isCreateMode) {
            IconButton(onClick = {
              Log.i(TAG_PLACE_DETAIL, "Delete place button tapped: placeId=$placeId — showing confirm dialog")
              showDeleteDialog = true
            }) {
              Icon(Icons.Default.Delete, contentDescription = "Delete place")
            }
          }
        }
      )
    },
    floatingActionButton = {
      if (!isCreateMode) {
        FloatingActionButton(
          onClick = { startAddPhoto() },
          modifier = Modifier.size(48.dp)
        ) {
          Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add photo")
        }
      }
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      if (isCreateMode) {
        // Create mode: just show the picked location and ask for a name
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text("New place", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
          if (newPlaceLat != null && newPlaceLon != null) {
            Text(
              text = "Location: $newPlaceLat, $newPlaceLon",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          if (newPlaceDay != null) {
            Text(
              text = "Day: $newPlaceDay",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Button(onClick = { showNameDialog = true }) { Text("Set name") }
        }
      } else if (showSkeleton) {
        PlaceDetailSkeleton()
      } else {
        key(placeRefreshKey) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
              if (editMode) {
                OutlinedTextField(
                  value = editName,
                  onValueChange = { editName = it },
                  label = { Text("Place name") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                  value = editAddress,
                  onValueChange = { editAddress = it },
                  label = { Text("Address") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End
                ) {
                  TextButton(
                    onClick = {
                      editName = currentPlace?.name ?: ""
                      editAddress = AppSession.effectiveAddress(currentPlace?.name, currentPlace?.address) ?: ""
                      editMode = false
                    }
                  ) { Text("Cancel") }
                  Spacer(Modifier.width(8.dp))
                  Button(
                    onClick = {
                      Log.i(
                        TAG_PLACE_DETAIL,
                        "Saving place updates: name='$editName', address='$editAddress', lat=${currentPlace?.latitude}, lon=${currentPlace?.longitude}"
                      )
                      placeViewModel.updatePlace(
                        placeId = placeId,
                        request = UpdatePlaceRequest(
                          name = editName.takeIf { it.isNotBlank() },
                          description = null,
                          latitude = currentPlace.latitude,
                          longitude = currentPlace.longitude,
                          address = editAddress.takeIf { it.isNotBlank() }
                            .takeUnless { it == "" },
                          day = null,
                          visitDate = null,
                          orderIndex = null,
                          transportFromPrevious = null
                        ),
                        onSuccess = {
                          Log.i(
                            TAG_PLACE_DETAIL,
                            "Place updated successfully"
                          )
                          placeRefreshKey += 1
                          editMode = false
                          placeViewModel.getPlace(placeId)
                          photoViewModel.getPhotosForPlace(placeId)
                        }
                      )
                    },
                    enabled = editName.isNotBlank()
                  ) {
                    Text("Save")
                  }
                }
              } else {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = currentPlace.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                  )
                  IconButton(
                    onClick = {
                      Log.i(
                        TAG_PLACE_DETAIL,
                        "Edit mode enabled for place $placeId"
                      )
                      editMode = true
                    },
                    modifier = Modifier.size(32.dp)
                  ) {
                    Icon(
                      Icons.Default.Edit,
                      contentDescription = "Edit place details",
                      tint = MaterialTheme.colorScheme.primary
                    )
                  }
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    Icons.Default.Place,
                    contentDescription = "Address",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                  )
                  Spacer(Modifier.width(4.dp))
                  Text(
                    text = AppSession.effectiveAddress(currentPlace.name, currentPlace.address) ?: "unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Location",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                  )
                  Spacer(Modifier.width(4.dp))
                  Text(
                    text = if (currentPlace.latitude != null && currentPlace.longitude != null) {
                      "Lat: %.6f, Lon: %.6f".format(
                        currentPlace.latitude,
                        currentPlace.longitude
                      )
                    } else {
                      "latlon unknown"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                visitDateText?.let {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      Icons.Default.Schedule,
                      contentDescription = "Visit time",
                      modifier = Modifier.size(16.dp),
                      tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                      text = it,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
                if (currentPlace.transportFromPrevious != null && currentPlace.transportFromPrevious.isNotBlank()) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "Transport: ${currentPlace.transportFromPrevious}",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                  onClick = {
                    Log.i(
                      TAG_PLACE_DETAIL,
                      "Select on map for place $placeId, tripId='$effectiveTripId'"
                    )
                    val targetTripId = AppSession.validateTripId(effectiveTripId)
                    if (targetTripId != null) {
                      AppSession.mapEditPlaceId.value = placeId
                      AppSession.mapGeoJson.value = currentPlace?.let { com.journalpix.geo.GeoJson.forPlace(it) }
                      // Carry the edit target in the route too — deterministic, not just session state
                      navController.navigate("map/$targetTripId?addPlaceMode=true&placeIdForUpdate=$placeId")
                    } else {
                      Log.e(TAG_PLACE_DETAIL, "Select on map blocked: tripId is BLANK — invalid trip")
                      android.widget.Toast.makeText(
                        context,
                        AppSession.tripIdError.value ?: "Invalid trip",
                        android.widget.Toast.LENGTH_LONG
                      ).show()
                    }
                  },
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(
                    Icons.Default.Map,
                    contentDescription = "Open place on big map",
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
              }
            }

            if (currentPlace.latitude != null && currentPlace.longitude != null) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text(
                  text = "Location",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier
                    .padding(start = 16.dp, top = 8.dp)
                    .align(Alignment.Start)
                )
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    // Minimap is read-only: tapping it opens the big map for editing
                    .clickable {
                      Log.i(TAG_PLACE_DETAIL, "Minimap tapped — opening big map for place $placeId")
                      val targetTripId = AppSession.validateTripId(effectiveTripId)
                      if (targetTripId != null) {
                        AppSession.mapEditPlaceId.value = placeId
                        AppSession.mapGeoJson.value = currentPlace?.let { com.journalpix.geo.GeoJson.forPlace(it) }
                        // Carry the edit target in the route too — deterministic, not just session state
                        navController.navigate("map/$targetTripId?addPlaceMode=true&placeIdForUpdate=$placeId")
                      } else {
                        android.widget.Toast.makeText(
                          context,
                          AppSession.tripIdError.value ?: "Invalid trip",
                          android.widget.Toast.LENGTH_LONG
                        ).show()
                      }
                    }
                ) {
                  OpenMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = mapCameraState,
                    properties = MapProperties(
                      mapType = MapType.STANDARD,
                      minZoomPreference = 3f,
                      maxZoomPreference = 20f
                    ),
                    uiSettings = MapUiSettings(
                      zoomControlsEnabled = false
                    )
                  ) {
                    MarkerComposable(
                      state = rememberMarkerState(
                        LatLng(
                          currentPlace.latitude!!,
                          currentPlace.longitude!!
                        )
                      )
                    ) {
                      Box(
                        modifier = Modifier
                          .size(32.dp)
                          .background(
                            Color(0xFF3B82F6), RoundedCornerShape(16.dp)
                          )
                          .clip(RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                      ) {
                        Text(
                          text = currentPlace.name.take(1).uppercase(),
                          color = Color.White,
                          style = MaterialTheme.typography.titleSmall
                        )
                      }
                    }
                  }

                  mapSelection?.let { point ->
                    val markerState = rememberMarkerState(
                      LatLng(point.first, point.second)
                    )
                    MarkerComposable(
                      state = markerState,
                      onClick = {
                        Log.d(
                          TAG_PLACE_DETAIL,
                          "Selection marker clicked: lat=${point.first}, lon=${point.second}"
                        )
                        true
                      }
                    ) {
                      Box(
                        modifier = Modifier
                          .size(32.dp)
                          .background(
                            Color.Red,
                            RoundedCornerShape(16.dp)
                          )
                          .clip(RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                      ) {
                        Text(
                          text = "?",
                          color = Color.White,
                          style = MaterialTheme.typography.titleMedium
                        )
                      }
                    }
                  }
                }
              }

              mapSelection?.let { (lat, lon) ->
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  Text(
                    text = "Selected: %.6f, %.6f".format(lat, lon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                  )
                  Spacer(Modifier.height(8.dp))
                  Button(
                    onClick = {
                      Log.i(
                        TAG_PLACE_DETAIL,
                        "Applying map selection: lat=$lat, lon=$lon"
                      )
                      placeViewModel.updatePlace(
                        placeId = placeId,
                        request = UpdatePlaceRequest(
                          name = null,
                          description = null,
                          latitude = lat,
                          longitude = lon,
                          address = mapSelectionAddress.takeIf { it?.isNotBlank() == true },
                          day = null,
                          visitDate = null,
                          orderIndex = null,
                          transportFromPrevious = null
                        ),
                        onSuccess = {
                          Log.i(
                            TAG_PLACE_DETAIL,
                            "Place updated from map selection"
                          )
                          placeRefreshKey += 1
                          mapSelection = null
                          mapSelectionAddress = null
                          // Refresh from local store (already updated by updatePlace)
                          placeViewModel.refreshLocalPlace(placeId)
                        }
                      )
                    },
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp)
                  ) {
                    Text("Apply location")
                  }
                }
              }

              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 8.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "Photos (${currentPlace.photos.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                  )
                }
                if (currentPlace.photos.isEmpty()) {
                  Text(
                    text = "No photos yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                  )
                } else {
                  LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                  ) {
                    items(
                      items = currentPlace.photos,
                      key = { it.id }
                    ) { photo ->
                      PhotoDetailItem(
                        photo = photo,
                        placeId = placeId,
                        onDelete = { deletePhoto(photo.id) },
                        onReverseGeocode = {
                          photoViewModel.reverseGeocode(effectiveTripId, photo) {
                            photoViewModel.getPhotosForPlace(placeId)
                          }
                        },
                        resolvedAddress = photoViewModel.resolvedAddresses[photo.id]
                          ?: AppSession.effectiveAddress(currentPlace?.name, currentPlace?.address),
                        isGeocoding = photoViewModel.geocodeLoadingIds[photo.id] == true,
                        geocodeError = photoViewModel.geocodeErrors[photo.id],
                        onImageClick = {
                          currentImageUrl = photoImageUrl(photo)
                          currentCaption = photo.caption ?: "Photo"
                          showImageViewer = true
                        }
                      )
                    }
                  }
                }
              }
            }
          }
        }

        photoViewModel.errorMessage.value?.let { error ->
          Snackbar(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp)
              .align(Alignment.BottomCenter),
            action = {
              Button(onClick = { photoViewModel.errorMessage.value = null }) {
                Text("Dismiss")
              }
            }
          ) {
            Text(error)
          }
        }
      }
    }

    // Create mode: ask for the place name, then create and open the new place for editing
    if (showNameDialog && isCreateMode) {
      AlertDialog(
        onDismissRequest = { showNameDialog = false },
        title = { Text("Name the place") },
        text = {
          Column {
            if (newPlaceLat != null && newPlaceLon != null) {
              Text(
                text = "Location: $newPlaceLat, $newPlaceLon",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
              value = newPlaceName,
              onValueChange = { newPlaceName = it },
              label = { Text("Place name") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              val name = newPlaceName.trim()
              if (name.isBlank()) return@Button
              Log.i(TAG_PLACE_DETAIL, "Creating place: name='$name', lat=$newPlaceLat, lon=$newPlaceLon, day=$newPlaceDay, tripId='$effectiveTripId'")
              showNameDialog = false
              placeViewModel.createPlace(
                effectiveTripId,
                com.journalpix.models.CreatePlaceRequest(
                  tripId = effectiveTripId,
                  name = name,
                  latitude = newPlaceLat,
                  longitude = newPlaceLon,
                  visitDate = newPlaceDay?.let { d ->
                    com.journalpix.models.ZonedTimeInput(
                      local = if (d.contains("T")) d else "${d}T00:00:00",
                      timezone = java.time.ZoneId.systemDefault().id
                    )
                  }
                )
              ) { created ->
                Log.i(TAG_PLACE_DETAIL, "Place created: id=${created.id} — opening it for editing")
                // Land on the new place's detail with the timeline below (drop map + place_new)
                runCatching {
                  navController.navigate("place/${created.id}?tripId=$effectiveTripId") {
                    popUpTo("timeline/$effectiveTripId") { inclusive = false }
                  }
                }.onFailure {
                  navController.navigate("place/${created.id}?tripId=$effectiveTripId")
                }
              }
            }
          ) {
            Text("Create")
          }
        },
        dismissButton = {
          TextButton(onClick = { showNameDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }

    if (showDeleteDialog) {
      AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("Delete place?") },
        text = {
          Text("This will delete \"${currentPlace?.name ?: ""}\" and all its photos. This cannot be undone.")
        },
        confirmButton = {
          Button(
            onClick = {
              showDeleteDialog = false
              deletePlace()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
          ) {
            Text("Delete")
          }
        },
        dismissButton = {
          TextButton(onClick = { showDeleteDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }

    if (showImageViewer) {
      PhotoViewerScreen(
        imageUrl = currentImageUrl,
        caption = currentCaption,
        onDismiss = { showImageViewer = false }
      )
    }
  }
}

@Composable
private fun PhotoDetailItem(
  photo: PhotoResponse,
  placeId: String,
  onDelete: () -> Unit,
  onReverseGeocode: () -> Unit,
  resolvedAddress: String? = null,
  isGeocoding: Boolean = false,
  geocodeError: String? = null,
  onImageClick: () -> Unit = {}
) {
  Column(
    modifier = Modifier
      .size(110.dp)
      .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(8.dp))
        .background(Color.Black.copy(alpha = 0.1f))
    ) {
      AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
          .data(photoImageUrl(photo))
          .crossfade(true)
          .listener(
            onError = { _, result ->
              Log.e(
                "JPXPDS",
                "Image load ERROR: ${result.throwable.message}",
                result.throwable
              )
            },
            onSuccess = { _, result ->
              Log.i("JPXPDS", "Image load OK: ${photoImageUrl(photo)}")
            }
          )
          .build(),
        contentDescription = photo.caption ?: "Photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .clickable { onImageClick() }
      )
      IconButton(
        onClick = { onDelete() },
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(4.dp)
          .background(Color.White.copy(alpha = 0.85f), CircleShape)
      ) {
        Icon(
          Icons.Default.Delete,
          contentDescription = "Delete photo",
          tint = MaterialTheme.colorScheme.error
        )
      }
    }
    Spacer(Modifier.height(4.dp))
    Column(
      modifier = Modifier
        .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
      photo.takenAt?.let {
        Text(
          text = "Taken: ${it.timeLabel()}",
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1
        )
      }
      if (photo.latitude != null && photo.longitude != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "GPS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
          )
          if (isGeocoding) {
            CircularProgressIndicator(
              modifier = Modifier.size(12.dp),
              strokeWidth = 2.dp
            )
          } else {
            IconButton(
              onClick = onReverseGeocode,
              modifier = Modifier.size(20.dp)
            ) {
              Icon(
                Icons.Default.Place,
                contentDescription = "Look up address",
                tint = MaterialTheme.colorScheme.primary
              )
            }
          }
        }
        resolvedAddress?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
          )
        }
        geocodeError?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1
          )
        }
      }
    }
  }
}

@Composable
private fun PhotoViewerScreen(
  imageUrl: String,
  caption: String,
  onDismiss: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      IconButton(
        onClick = { onDismiss() },
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(16.dp)
      ) {
        Icon(
          Icons.Default.Close,
          contentDescription = "Close",
          tint = Color.White
        )
      }

      AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
          .data(imageUrl)
          .build(),
        contentDescription = caption,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
      )

      caption?.let { cap ->
        if (cap.isNotBlank()) {
          Box(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(24.dp)
              .background(Color.Black.copy(alpha = 0.6f))
          ) {
            Text(
              text = cap,
              style = MaterialTheme.typography.bodyMedium,
              color = Color.White,
              modifier = Modifier.padding(8.dp)
            )
          }
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun PlaceDetailScreenPreview() {
  JournalPixTheme {
    PlaceDetailScreen(
      navController = rememberNavController(),
      placeId = "demo-place-1",
      tripId = "demo-trip-1"
    )
  }
}
