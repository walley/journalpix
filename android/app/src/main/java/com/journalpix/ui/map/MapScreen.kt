package com.journalpix.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.journalpix.models.MapPlace
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.models.TripResponse
import com.journalpix.viewmodel.MapViewModel
import de.afarber.openmapview.compose.Marker
import de.afarber.openmapview.compose.MarkerComposable
import de.afarber.openmapview.compose.OpenMap
import de.afarber.openmapview.compose.Polyline
import de.afarber.openmapview.compose.rememberCameraPositionState
import de.afarber.openmapview.compose.rememberMarkerState
import de.afarber.openmapview.model.CameraPosition
import de.afarber.openmapview.model.LatLng
import de.afarber.openmapview.model.MapProperties
import de.afarber.openmapview.model.MapType
import de.afarber.openmapview.model.MapUiSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavController,
    trip: TripResponse? = null,
    places: List<PlaceWithPhotos> = emptyList()
) {
    val mapViewModel: MapViewModel = remember { MapViewModel() }
    val context = LocalContext.current

    LaunchedEffect(trip?.id) {
        trip?.id?.let { tripId ->
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(trip?.title ?: "Map View")
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
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                )
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
                            width = 8f
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
                                mapViewModel.selectPlace(place)
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

            mapViewModel.selectedPlace.value?.let { place ->
                PlaceDetailDialog(
                    place = place,
                    onDismiss = { mapViewModel.clearSelection() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailDialog(
    place: MapPlace,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(place.name) },
        text = {
            Column {
                place.description?.let { description ->
                    Text(description)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                place.thumbnailUrl?.let { url ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Place image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }

                place.thumbnailBitmap?.let { bitmap ->
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Place image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Lat: ${place.latitude}, Lon: ${place.longitude}")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
