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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private const val TAG_PLACE_DETAIL = "JPXPlaceDetail"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
    navController: NavController,
    placeId: String,
    tripId: String = ""
) {
    val context = LocalContext.current
    val token = AppSession.currentUser.value?.token ?: AuthViewModel.DEMO_TOKEN

    val placeViewModel: PlaceViewModel = remember { PlaceViewModel() }
    val photoViewModel: PhotoViewModel = remember { PhotoViewModel() }

    // Read directly from view-model state so the UI recomposes when data changes
    val placeState = placeViewModel.currentPlace
    val currentPlace = placeState.value
    val isLoading = placeViewModel.isLoading.value || photoViewModel.isLoading.value

    // Edit mode for place name / address
    var editMode by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editAddress by remember { mutableStateOf("") }

    // Delete confirmation dialog
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Map tap-to-select state
    var mapSelection by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var mapSelectionAddress by remember { mutableStateOf<String?>(null) }

    // Force recomposition when place data changes (e.g. after map selection update)
    var placeRefreshKey by remember { mutableStateOf(0) }

    // Re-read currentPlace whenever placeState changes
    LaunchedEffect(placeState.value) {
        placeRefreshKey += 1
    }

    // Initialize edit fields from current place
    LaunchedEffect(currentPlace) {
        if (currentPlace != null) {
            editName = currentPlace.name
            editAddress = currentPlace.address ?: ""
        }
    }

    // Handle map selection result for place update
    LaunchedEffect(AppSession.pendingMapPlaceUpdate.value) {
        val pending = AppSession.pendingMapPlaceUpdate.value
        if (pending != null) {
            val (lat, lon, name) = pending
            Log.i(TAG_PLACE_DETAIL, "Pending map place update: lat=$lat, lon=$lon, name='$name'")
            AppSession.clearPendingMapPlaceUpdate()
            editMode = true
            editName = if (name.isNotBlank()) name else currentPlace?.name ?: ""
            editAddress = ""
            // Auto-apply the map selection to the place
            placeViewModel.updatePlace(
                token = token,
                placeId = placeId,
                request = UpdatePlaceRequest(
                    name = if (name.isNotBlank()) name else null,
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
                    placeViewModel.getPlace(token, placeId)
                    photoViewModel.getPhotosForPlace(token, placeId)
                }
            )
            // Reverse geocode to get address
            photoViewModel.reverseGeocode(tripId, PhotoResponse(
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
            )) {
                Log.i(TAG_PLACE_DETAIL, "Reverse geocode complete for map selection")
            }
        }
    }

    // Read resolved address from reverse geocode result (top-level composable scope)
    LaunchedEffect(photoViewModel.resolvedAddresses["temp"]) {
        val addr = photoViewModel.resolvedAddresses["temp"]
        if (addr != null && addr.isNotBlank()) {
            Log.i(TAG_PLACE_DETAIL, "Reverse geocoded address: $addr")
            editAddress = addr
        }
        photoViewModel.resolvedAddresses.remove("temp")
    }

    // Load place + photos
    LaunchedEffect(placeId, token) {
        Log.i(TAG_PLACE_DETAIL, "Loading place: placeId=$placeId, tripId=$tripId")
        placeViewModel.getPlace(token, placeId)
        photoViewModel.getPhotosForPlace(token, placeId)
    }

    placeViewModel.errorMessage.value?.let { error ->
        Log.e(TAG_PLACE_DETAIL, "Place load error: $error")
    }

    // Photo picker launcher
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            photoViewModel.addPhotoOffline(context, placeId, uri) {
                Log.i(TAG_PLACE_DETAIL, "Photo added to place $placeId")
                photoViewModel.getPhotosForPlace(token, placeId)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> pickImageLauncher.launch("image/*") }

    fun startAddPhoto() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        else pickImageLauncher.launch("image/*")
    }

    fun deletePhoto(photoId: String) {
        Log.w(TAG_PLACE_DETAIL, "Deleting photo: photoId=$photoId from place=$placeId")
        photoViewModel.deletePhoto(token, photoId) {
            Log.i(TAG_PLACE_DETAIL, "Photo deleted: photoId=$photoId")
            photoViewModel.getPhotosForPlace(token, placeId)
            placeViewModel.getPlace(token, placeId)
        }
    }

    fun deletePlace() {
        Log.w(TAG_PLACE_DETAIL, "Deleting whole place: placeId=$placeId")
        placeViewModel.deletePlace(token, placeId) {
            Log.i(TAG_PLACE_DETAIL, "Place deleted: placeId=$placeId")
            navController.popBackStack()
        }
    }

    if (currentPlace == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Visit time
    val visitLabel = currentPlace.visitLabel()
    val visitDateText = if (visitLabel != null) "Visit: $visitLabel" else null

    // Minimap camera state
    val mapCameraState = rememberCameraPositionState {
        position = if (currentPlace.latitude != null && currentPlace.longitude != null) {
            CameraPosition(
                LatLng(currentPlace.latitude!!, currentPlace.longitude!!),
                15f
            )
        } else {
            CameraPosition(LatLng(0.0, 0.0), 3f)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(currentPlace.name) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { startAddPhoto() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add photo")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else {
                key(placeRefreshKey) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Place header
                        Column(
                            modifier = Modifier
                              .fillMaxWidth()
                              .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            if (editMode) {
                                // Editable name and address fields
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
                                            editAddress = currentPlace?.address ?: ""
                                            editMode = false
                                        }
                                    ) { Text("Cancel") }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            Log.i(TAG_PLACE_DETAIL, "Saving place updates: name='$editName', address='$editAddress', lat=${currentPlace?.latitude}, lon=${currentPlace?.longitude}")
                                            placeViewModel.updatePlace(
                                                token = token,
                                                placeId = placeId,
                                                request = UpdatePlaceRequest(
                                                    name = editName.takeIf { it.isNotBlank() },
                                                    description = null,
                                                    latitude = currentPlace.latitude,
                                                    longitude = currentPlace.longitude,
                                                    address = editAddress.takeIf { it.isNotBlank() }.takeUnless { it == "" },
                                                    day = null,
                                                    visitDate = null,
                                                    orderIndex = null,
                                                    transportFromPrevious = null
                                                ),
                                                onSuccess = {
                                                    Log.i(TAG_PLACE_DETAIL, "Place updated successfully")
                                                    placeRefreshKey += 1
                                                    editMode = false
                                                    placeViewModel.getPlace(token, placeId)
                                                    photoViewModel.getPhotosForPlace(token, placeId)
                                                }
                                            )
                                        },
                                        enabled = editName.isNotBlank()
                                    ) {
                                        Text("Save")
                                    }
                                }
                            } else {
                                // Read-only display with edit action
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
                                    // Pencil icon to enter edit mode
                                    IconButton(
                                        onClick = {
                                            Log.i(TAG_PLACE_DETAIL, "Edit mode enabled for place $placeId")
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
                                // Address always shown, "unknown" if not set
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
                                        text = currentPlace.address ?: "unknown",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Location (lat/lon) always shown, "unknown" if not set
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
                                                currentPlace.latitude!!,
                                                currentPlace.longitude!!
                                            )
                                        } else {
                                            "unknown"
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
                            // Select on map action
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        Log.i(TAG_PLACE_DETAIL, "Select on map for place $placeId")
                                        navController.navigate("map/$tripId?addPlaceMode=true&placeIdForUpdate=$placeId")
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MyLocation,
                                        contentDescription = "Select place on map",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Minimap
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
                                        ),
                                        onMapClick = { latLng ->
                                            Log.i(TAG_PLACE_DETAIL, "Map tapped: lat=${latLng.latitude}, lon=${latLng.longitude}")
                                            mapSelection = Pair(latLng.latitude, latLng.longitude)
                                        }
                                    ) {
                                        MarkerComposable(
                                            state = rememberMarkerState(
                                                LatLng(currentPlace.latitude!!, currentPlace.longitude!!)
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

                                    // Selection marker at tapped point
                                    mapSelection?.let { point ->
                                        val markerState = rememberMarkerState(
                                            LatLng(point.first, point.second)
                                        )
                                        MarkerComposable(
                                            state = markerState,
                                            onClick = {
                                                Log.d(TAG_PLACE_DETAIL, "Selection marker clicked: lat=${point.first}, lon=${point.second}")
                                                true
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                  .size(32.dp)
                                                  .background(Color.Red, RoundedCornerShape(16.dp))
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
                        }

                        // Apply map selection button
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
                                        Log.i(TAG_PLACE_DETAIL, "Applying map selection: lat=$lat, lon=$lon")
                                        placeViewModel.updatePlace(
                                            token = token,
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
                                                Log.i(TAG_PLACE_DETAIL, "Place updated from map selection")
                                                placeRefreshKey += 1
                                                mapSelection = null
                                                mapSelectionAddress = null
                                                placeViewModel.getPlace(token, placeId)
                                                photoViewModel.getPhotosForPlace(token, placeId)
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                ) {
                                    Text("Apply location")
                                }
                            }
                        }

                        // Photos section
                        Column(
                            modifier = Modifier
                              .fillMaxWidth()
                              .weight(1f)
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
                                      .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                                photoViewModel.reverseGeocode(tripId, photo) {
                                                    photoViewModel.getPhotosForPlace(token, placeId)
                                                }
                                            },
                                            resolvedAddress = photoViewModel.resolvedAddresses[photo.id] ?: currentPlace.address,
                                            isGeocoding = photoViewModel.geocodeLoadingIds[photo.id] == true,
                                            geocodeError = photoViewModel.geocodeErrors[photo.id]
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Delete place button (red bin icon) at bottom of screen
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                      .align(Alignment.CenterHorizontally)
                      .padding(bottom = 24.dp)
                      .background(
                          MaterialTheme.colorScheme.errorContainer, CircleShape
                      )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete place",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Error snackbar
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

    // Delete confirmation dialog
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
}

/**
 * Single photo item inside the place detail grid: image, delete icon, info.
 */
@Composable
private fun PhotoDetailItem(
    photo: PhotoResponse,
    placeId: String,
    onDelete: () -> Unit,
    onReverseGeocode: () -> Unit,
    resolvedAddress: String? = null,
    isGeocoding: Boolean = false,
    geocodeError: String? = null
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
                    .build(),
                contentDescription = photo.caption ?: "Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .clickable {
                        // Open full-screen image viewer
                        PhotoViewerScreen(
                            imageUrl = photoImageUrl(photo),
                            caption = photo.caption ?: "Photo",
                            onDismiss = {}
                        )
                    }
            )
            // Delete button overlay (top-right)
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

/**
 * Full-screen image viewer for a photo.
 */
@Composable
private fun PhotoViewerScreen(
    imageUrl: String,
    caption: String,
    onDismiss: () -> Unit
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Close button
            IconButton(
                onClick = { onDismiss() },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            // Image with pinch zoom
            val scale = remember { mutableFloatStateOf(1f) }
            val offset = remember { mutableStateOf(Offset.Zero) }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .build(),
                contentDescription = caption,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        translationX = offset.value.x
                        translationY = offset.value.y
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                // Single tap to toggle full screen/fit
                                scale.value = if (scale.value == 1f) 2f else 1f
                                offset.value = Offset.Zero
                            },
                            onLongPress = {},
                            onDoubleTap = { offset ->
                                // Double tap to zoom in/out
                                scale.value = if (scale.value == 1f) 2f else 1f
                                offset.value = Offset.Zero
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offset.value = Offset(
                                    offset.value.x + dragAmount.x,
                                    offset.value.y + dragAmount.y
                                )
                                scale.value = 2f // Zoomed in state allows dragging
                            }
                        )
                    }
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

/**
 * Resolves a photo's image source for offline-first loading.
 */
private fun photoImageUrl(photo: PhotoResponse): String {
    val path = photo.thumbnailPath.ifBlank { photo.filePath }
    return when {
        path.startsWith("content://") ||
        path.startsWith("file://") ||
        path.startsWith("http://") ||
        path.startsWith("https://") -> path
        photo.thumbnailPath.isNotBlank() -> ApiService.thumbnailUrl(path)
        else -> ApiService.uploadUrl(path)
    }
}