package com.journalpix.ui.map

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
import com.journalpix.models.MapRoute
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.models.TripResponse
import com.journalpix.viewmodel.MapViewModel
import de.afarber.openmapview.compose.MapComposable
import de.afarber.openmapview.compose.MapMarker
import de.afarber.openmapview.compose.MapPolyline
import de.afarber.openmapview.compose.MapViewport
import de.afarber.openmapview.compose.rememberMapState
import de.afarber.openmapview.model.LatLng

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavController,
    trip: TripResponse? = null,
    places: List<PlaceWithPhotos> = emptyList()
) {
    val mapViewModel: MapViewModel = remember { MapViewModel() }
    val context = LocalContext.current
    
    // Load trip data
    LaunchedEffect(trip?.id) {
        trip?.id?.let { tripId ->
            mapViewModel.loadTripRoute(tripId, places)
        }
    }
    
    // Map state
    val mapState = rememberMapState(
        center = LatLng(
            mapViewModel.viewportState.value.centerLatitude,
            mapViewModel.viewportState.value.centerLongitude
        ),
        zoom = mapViewModel.viewportState.value.zoom
    )
    
    // Update viewport when state changes
    LaunchedEffect(mapViewModel.viewportState.value) {
        val viewport = mapViewModel.viewportState.value
        mapState.center = LatLng(viewport.centerLatitude, viewport.centerLongitude)
        mapState.zoom = viewport.zoom
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
                        mapState.zoom += 1f
                        mapViewModel.updateViewport(
                            mapState.center.latitude,
                            mapState.center.longitude,
                            mapState.zoom
                        )
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
                }
                FloatingActionButton(
                    onClick = { 
                        mapState.zoom -= 1f
                        mapViewModel.updateViewport(
                            mapState.center.latitude,
                            mapState.center.longitude,
                            mapState.zoom
                        )
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Map View
            MapComposable(
                state = mapState,
                modifier = Modifier.fillMaxSize()
            ) {
                // Draw route line
                mapViewModel.mapRoute.value?.let { route ->
                    if (route.routePoints.size >= 2) {
                        val latLngs = route.routePoints.map {
                            LatLng(it.latitude, it.longitude)
                        }
                        MapPolyline(
                            points = latLngs,
                            color = Color.Blue,
                            width = 4f
                        )
                    }
                }
                
                // Draw place markers
                mapViewModel.mapRoute.value?.let { route ->
                    route.places.forEach { place ->
                        val isExpanded = mapViewModel.bubbleStates.value[place.id] ?: false
                        
                        MapMarker(
                            position = LatLng(place.latitude, place.longitude),
                            anchor = 0.5f to 1.0f,
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
            
            // Loading indicator
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
            
            // Error message
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
            
            // Selected place details
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
        // Marker icon
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
        
        // Bubble with info
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
                    
                    // Thumbnail image
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
                        Image(
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
                
                // Thumbnail image
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
                    Image(
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
