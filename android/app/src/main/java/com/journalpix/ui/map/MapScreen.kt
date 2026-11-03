package com.journalpix.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.journalpix.models.MapPlace
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.viewmodel.AppSession
import com.journalpix.viewmodel.MapViewModel
import de.afarber.openmapview.CameraPosition
import de.afarber.openmapview.LatLng
import de.afarber.openmapview.MapType
import de.afarber.openmapview.compose.MapProperties
import de.afarber.openmapview.compose.MapUiSettings
import de.afarber.openmapview.compose.MarkerComposable
import de.afarber.openmapview.compose.OpenMap
import de.afarber.openmapview.compose.Polyline
import de.afarber.openmapview.compose.rememberCameraPositionState
import de.afarber.openmapview.compose.rememberMarkerState

private const val TAG_MAP = "JPXMap"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavController,
    tripId: String = "",
    places: List<PlaceWithPhotos> = emptyList(),
    addPlaceMode: Boolean = false,
    placeIdForUpdate: String = "",
    onPlaceAdded: ((Double, Double, String) -> Unit)? = null
) {
    val mapViewModel: MapViewModel = remember { MapViewModel() }
    val context = LocalContext.current
    var newPlaceName by remember { mutableStateOf("") }
    var showAddPlaceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(tripId) {
        if (tripId.isNotEmpty()) {
            mapViewModel.loadTripRoute(tripId, places)
        }
    }

    val viewport = mapViewModel.viewportState.value
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition(
            LatLng(viewport.centerLatitude, viewport.centerLongitude),
            viewport.zoom
        )
    }

    // Keep camera in sync when ViewModel viewport changes (e.g. fit-to-route)
    LaunchedEffect(viewport.centerLatitude, viewport.centerLongitude, viewport.zoom) {
        cameraPositionState.position = CameraPosition(
            LatLng(viewport.centerLatitude, viewport.centerLongitude),
            viewport.zoom
        )
    }

    // Map tap-to-select: works regardless of addPlaceMode
    LaunchedEffect(mapViewModel.mapSelectionPoint.value) {
        val point = mapViewModel.mapSelectionPoint.value
        if (point != null) {
            Log.d(TAG_MAP, "Map selection updated: lat=${point.latitude}, lon=${point.longitude}")
        }
    }

    // Handle map tap in add-place mode
    if (addPlaceMode && mapViewModel.mapTapPoint.value != null) {
        val tapPoint = mapViewModel.mapTapPoint.value!!
        Log.d(TAG_MAP, "Map tap point received: lat=${tapPoint.latitude}, lon=${tapPoint.longitude}")
        LaunchedEffect(tapPoint) {
            Log.i(TAG_MAP, "Opening add-place dialog for tap at lat=${tapPoint.latitude}, lon=${tapPoint.longitude}")
            showAddPlaceDialog = true
            newPlaceName = ""
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        mapViewModel.mapRoute.value?.tripTitle ?: "Map View"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { mapViewModel.fitToRoute() }) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Fit to route")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (addPlaceMode && onPlaceAdded != null) {
                    FloatingActionButton(
                        onClick = {
                            val tapPoint = mapViewModel.mapTapPoint.value
                            Log.w(TAG_MAP, "Cancel add place (tapPoint=${if (tapPoint == null) "none" else "lat=${tapPoint.latitude}, lon=${tapPoint.longitude}"})")
                            mapViewModel.clearMapTapPoint()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel add place")
                    }
                }
                FloatingActionButton(
                    onClick = {
                        val current = cameraPositionState.position
                        val newZoom = current.zoom + 1f
                        cameraPositionState.position = CameraPosition(current.target, newZoom)
                        mapViewModel.updateViewport(
                            current.target.latitude,
                            current.target.longitude,
                            newZoom
                        )
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
                }
                FloatingActionButton(
                    onClick = {
                        val current = cameraPositionState.position
                        val newZoom = current.zoom - 1f
                        cameraPositionState.position = CameraPosition(current.target, newZoom)
                        mapViewModel.updateViewport(
                            current.target.latitude,
                            current.target.longitude,
                            newZoom
                        )
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
                }
                if (addPlaceMode && onPlaceAdded != null) {
                    FloatingActionButton(
                        onClick = {
                            val current = cameraPositionState.position
                            val lat = current.target.latitude
                            val lon = current.target.longitude
                            Log.i(TAG_MAP, "Add place at current location: lat=$lat, lon=$lon")
                            mapViewModel.setMapTapPoint(lat, lon)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add place at location")
                    }
                }
                // Apply selected map coordinates
                mapViewModel.mapSelectionPoint.value?.let { point ->
                    FloatingActionButton(
                        onClick = {
                            Log.i(TAG_MAP, "Apply selected location: lat=${point.latitude} lon=${point.longitude}")
                            if (placeIdForUpdate.isNotBlank()) {
                                AppSession.pendingMapPlaceUpdate.value = Triple(
                                    point.latitude,
                                    point.longitude,
                                    mapViewModel.mapRoute.value?.tripTitle ?: ""
                                )
                            } else {
                                AppSession.pendingMapPlace.value = Triple(
                                    point.latitude,
                                    point.longitude,
                                    ""
                                )
                            }
                            navController.popBackStack()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Apply selected location")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(paddingValues)
        ) {
            // NOTE: OpenMapView has built-in two-level tile caching enabled by
            // default — no app-side configuration needed:
            //   Level 1: Memory LRU cache (~1/8 of heap)
            //   Level 2: Disk cache (50 MB max, context.cacheDir/tiles2/,
            //            persists across restarts, 7-day expiry)
            // Tiles are cached automatically on download; stale tiles are shown
            // as offline fallback if re-download fails.
            OpenMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    mapType = MapType.STANDARD,
                    minZoomPreference = 3f,
                    maxZoomPreference = 20f
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false
                ), onMapClick = { latLng ->
                    Log.d(TAG_MAP, "Map clicked: lat=${latLng.latitude}, lon=${latLng.longitude}")
                    mapViewModel.setMapSelectionPoint(latLng.latitude, latLng.longitude)
                    if (addPlaceMode && onPlaceAdded != null) {
                        mapViewModel.setMapTapPoint(latLng.latitude, latLng.longitude)
                    }
                }
            ) {
                // Route polyline
                mapViewModel.mapRoute.value?.let { route ->
                    if (route.routePoints.size >= 2) {
                        val latLngs = route.routePoints.map {
                            LatLng(it.latitude, it.longitude)
                        }
                        Polyline(
                            points = latLngs,
                            strokeColor = Color.Blue,
                            strokeWidth = 8f
                        )
                    }

                    // Place markers
                    route.places.forEach { place ->
                        val isExpanded = mapViewModel.bubbleStates.value[place.id] ?: false
                        val markerState = rememberMarkerState(
                            LatLng(place.latitude, place.longitude)
                        )
                        MarkerComposable(
                            state = markerState,
                            onClick = {
                                mapViewModel.toggleBubble(place.id)
                                Log.i(TAG_MAP, "Marker clicked: placeId=${place.id}, navigating to place detail")
                                navController.navigate("place/${place.id}?tripId=$tripId")
                                true
                            }
                        ) {
                            PlaceMarkerBubble(
                                place = place,
                                isExpanded = isExpanded,
                                onToggle = { mapViewModel.toggleBubble(place.id) }
                            )
                        }
                    }
                }

                // Selection marker at tapped point
                mapViewModel.mapSelectionPoint.value?.let { point ->
                    val markerState = rememberMarkerState(
                        LatLng(point.latitude, point.longitude)
                    )
                    MarkerComposable(
                        state = markerState,
                        onClick = {
                            Log.d(TAG_MAP, "Selection marker clicked: lat=${point.latitude}, lon=${point.longitude}")
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

            if (mapViewModel.isLoading.value) {
                Box(
                    modifier = Modifier
                      .fillMaxSize()
                      .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            mapViewModel.errorMessage.value?.let { error ->
                Snackbar(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(16.dp)
                      .align(Alignment.BottomCenter),
                    action = {
                        Button(onClick = { mapViewModel.errorMessage.value = null }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }

            // Add place dialog
            if (showAddPlaceDialog && mapViewModel.mapTapPoint.value != null) {
                val tapPoint = mapViewModel.mapTapPoint.value!!
                AlertDialog(
                    onDismissRequest = {
                        Log.w(TAG_MAP, "Add place dialog dismissed via back/system (lat=${
                            tapPoint.latitude
                        }, lon=${
                            tapPoint.longitude
                        })")
                        showAddPlaceDialog = false
                        mapViewModel.clearMapTapPoint()
                    },
                    title = { Text("Add place at location") },
                    text = {
                        Column {
                            Text(
                                text = "Lat: ${tapPoint.latitude}, Lon: ${tapPoint.longitude}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
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
                        Button(onClick = {
                            if (newPlaceName.isNotBlank()) {
                                Log.i(TAG_MAP, "Add place confirmed: lat=${
                                    tapPoint.latitude
                                }, lon=${
                                    tapPoint.longitude
                                }, name=${newPlaceName.trim()}")
                                onPlaceAdded!!(
                                    tapPoint.latitude,
                                    tapPoint.longitude,
                                    newPlaceName.trim()
                                )
                                Log.d(TAG_MAP, "onPlaceAdded callback invoked, closing dialog")
                                showAddPlaceDialog = false
                                mapViewModel.clearMapTapPoint()
                                newPlaceName = ""
                            } else {
                                Log.w(TAG_MAP, "Add place attempted with blank name")
                            }
                        }) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            Log.w(TAG_MAP, "Add place cancelled by user")
                            showAddPlaceDialog = false
                            mapViewModel.clearMapTapPoint()
                            newPlaceName = ""
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PlaceMarkerBubble(
    place: MapPlace,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onToggle)
    ) {
        Box(
            modifier = Modifier
              .size(48.dp)
              .background(Color.Blue, RoundedCornerShape(24.dp))
              .clip(RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = place.name.take(1).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.width(200.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    place.description?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    place.thumbnailUrl?.let { url ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(url)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Place thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                              .fillMaxWidth()
                              .height(100.dp)
                              .clip(RoundedCornerShape(4.dp))
                        )
                    }

                    place.thumbnailBitmap?.let { bitmap ->
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Place thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                              .fillMaxWidth()
                              .height(100.dp)
                              .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

