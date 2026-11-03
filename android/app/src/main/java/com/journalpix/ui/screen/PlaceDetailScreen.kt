package com.journalpix.ui.screen

import android.Manifest
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
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

private const val TAG_PLACE_DETAIL = "JournalPixPlaceDetail"

/**
 * Dedicated place detail screen. Replaces the PlaceDetailSheet dialog.
 *
 * Shows:
 *   - Place name, address, lat/lon
 *   - Visit time (arriving/leaving) if known
 *   - Photos with delete icons, taken time, GPS, address
 *   - Add new photo (gallery picker)
 *   - Minimap centered on the place
 */
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

    // Read directly from view-model state so the UI recomposes when data
    // changes (e.g. after a photo delete refreshes the place).
    val placeState = placeViewModel.currentPlace
    val currentPlace = placeState.value
    val isLoading = placeViewModel.isLoading.value || photoViewModel.isLoading.value

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
            // Refresh both photos and place so the UI reflects the deletion
            photoViewModel.getPhotosForPlace(token, placeId)
            placeViewModel.getPlace(token, placeId)
        }
    }

    val currentPlace = place
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
                        Text(
                            text = currentPlace.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        currentPlace.address?.takeIf { it.isNotBlank() }?.let { address ->
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
                                    text = address,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (currentPlace.latitude != null && currentPlace.longitude != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = "Coordinates",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Lat: %.6f, Lon: %.6f".format(
                                        currentPlace.latitude!!,
                                        currentPlace.longitude!!
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                                    )
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
                            }
                        }
                    }

                    // Photos section
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
                                modifier = Modifier.fillMaxWidth(),
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
