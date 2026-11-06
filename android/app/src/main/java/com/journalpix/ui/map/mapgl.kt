package com.journalpix.ui.map

import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.models.RoutePoint
import com.journalpix.viewmodel.AppSession
import com.journalpix.viewmodel.MapViewModel
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.annotations.MarkerOptions

private const val TAG_MAP_GL = "JPXMG"

/** Free vector tiles (OpenFreeMap / OSM), no API key required. */
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/**
 * Big map implemented with MapLibre GL Native (vector tiles).
 * The old openmapview implementation lives in openmapview.kt (OpenMapViewScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapGlScreen(
    navController: NavController,
    tripId: String = "",
    places: List<PlaceWithPhotos> = emptyList(),
    addPlaceMode: Boolean = false,
    placeIdForUpdate: String = "",
    onPlaceAdded: ((Double, Double, String) -> Unit)? = null
) {
    val mapViewModel: MapViewModel = remember { MapViewModel() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var newPlaceName by remember { mutableStateOf("") }
    var showAddPlaceDialog by remember { mutableStateOf(false) }

    // The route may carry a blank tripId — fall back to the currently open trip
    val effectiveTripId = tripId.ifBlank { AppSession.currentTripId.value }
    Log.i(TAG_MAP_GL, "MapGlScreen opened: route tripId='$tripId', effectiveTripId='$effectiveTripId', addPlaceMode=$addPlaceMode, placeIdForUpdate='$placeIdForUpdate'")

    LaunchedEffect(effectiveTripId) {
        if (effectiveTripId.isNotEmpty()) {
            mapViewModel.loadTripRoute(effectiveTripId, places)
        } else {
            Log.e(TAG_MAP_GL, "MapGlScreen: effectiveTripId is BLANK — invalid trip, nothing to load")
        }
    }

    // Opened for editing a specific place (from place detail minimap/location icon)?
    val editPlaceId = placeIdForUpdate.ifBlank { AppSession.mapEditPlaceId.value ?: "" }
    val editPlace = editPlaceId.takeIf { it.isNotBlank() }
        ?.let { com.journalpix.data.OfflineStore.findPlaceWithPhotos(it) }
    val hasEditTarget = editPlace?.latitude != null && editPlace.longitude != null

    val maplibreMap = remember { mutableStateOf<MapLibreMap?>(null) }
    val editMarker = remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    // Handle map tap in add-place mode (not while editing an existing place)
    if (addPlaceMode && editPlaceId.isBlank() && mapViewModel.mapTapPoint.value != null) {
        val tapPoint = mapViewModel.mapTapPoint.value!!
        LaunchedEffect(tapPoint) {
            Log.i(TAG_MAP_GL, "Opening add-place dialog for tap at lat=${tapPoint.latitude}, lon=${tapPoint.longitude}")
            showAddPlaceDialog = true
            newPlaceName = ""
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // When opened from a place view, inherit the place name as the title
                    val placeTitle = editPlace?.name?.takeIf { it.isNotBlank() }
                    Text(placeTitle ?: mapViewModel.mapRoute.value?.tripTitle ?: "Map View")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val map = maplibreMap.value ?: return@IconButton
                        val pts = mapViewModel.mapRoute.value?.routePoints.orEmpty()
                        if (pts.size >= 2) {
                            val lats = pts.map { it.latitude }
                            val lons = pts.map { it.longitude }
                            val sw = LatLng(lats.min(), lons.min())
                            val ne = LatLng(lats.max(), lons.max())
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngBounds(
                                    org.maplibre.android.geometry.LatLngBounds.Builder()
                                        .include(sw).include(ne).build(),
                                    64
                                )
                            )
                        }
                    }) {
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
                            mapViewModel.clearMapTapPoint()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel add place")
                    }
                }
                FloatingActionButton(
                    onClick = {
                        val map = maplibreMap.value ?: return@FloatingActionButton
                        val pos = map.cameraPosition
                        map.moveCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(pos.target)
                                    .zoom(pos.zoom + 1.0)
                                    .build()
                            )
                        )
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
                }
                FloatingActionButton(
                    onClick = {
                        val map = maplibreMap.value ?: return@FloatingActionButton
                        val pos = map.cameraPosition
                        map.moveCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(pos.target)
                                    .zoom(pos.zoom - 1.0)
                                    .build()
                            )
                        )
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
                }
                if (addPlaceMode && onPlaceAdded != null && editPlaceId.isBlank()) {
                    FloatingActionButton(
                        onClick = {
                            val map = maplibreMap.value ?: return@FloatingActionButton
                            val target = map.cameraPosition.target ?: return@FloatingActionButton
                            Log.i(TAG_MAP_GL, "Add place at current location: lat=${target.latitude}, lon=${target.longitude}")
                            mapViewModel.setMapTapPoint(target.latitude, target.longitude)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add place at location")
                    }
                }
                // Apply selected map coordinates (checkmark)
                mapViewModel.mapSelectionPoint.value?.let { point ->
                    FloatingActionButton(
                        onClick = {
                            Log.i(TAG_MAP_GL, "Apply selected location: lat=${point.latitude} lon=${point.longitude}, editPlaceId='$editPlaceId'")
                            if (editPlaceId.isNotBlank()) {
                                // Name must stay as entered by the user — only lat/lon change here
                                AppSession.pendingMapPlaceUpdate.value = Triple(point.latitude, point.longitude, "")
                                AppSession.mapEditPlaceId.value = null
                            } else {
                                AppSession.pendingMapPlace.value = Triple(point.latitude, point.longitude, "")
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
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        mapView = this
                        onCreate(null)
                        getMapAsync { map ->
                            maplibreMap.value = map
                            Log.i(TAG_MAP_GL, "MapLibre map ready")
                            map.setStyle(MAP_STYLE_URL) { style ->
                                // GeoJSON passed from the calling view (route polyline + place points)
                                val geoJson = AppSession.mapGeoJson.value
                                if (!geoJson.isNullOrBlank()) {
                                    val parsed = com.journalpix.geo.GeoJson.parse(geoJson)
                                    if (parsed != null) {
                                        style.addSource(GeoJsonSource("geojson", parsed.toJson()))
                                        // Route polyline
                                        style.addLayer(
                                            LineLayer("route-line", "geojson")
                                                .withProperties(
                                                    PropertyFactory.lineColor(android.graphics.Color.BLUE),
                                                    PropertyFactory.lineWidth(6f)
                                                )
                                                .withFilter(
                                                    Expression.eq(
                                                        Expression.get("kind"),
                                                        Expression.literal("tripRoute")
                                                    )
                                                )
                                        )
                                        // Place points with "position. name" labels (skip the edited place)
                                        val pointFilter = if (editPlaceId.isNotBlank()) {
                                            Expression.all(
                                                Expression.eq(Expression.get("kind"), Expression.literal("place")),
                                                Expression.neq(Expression.get("id"), Expression.literal(editPlaceId))
                                            )
                                        } else {
                                            Expression.eq(Expression.get("kind"), Expression.literal("place"))
                                        }
                                        style.addLayer(
                                            SymbolLayer("places", "geojson")
                                                .withProperties(
                                                    PropertyFactory.textField("{label}"),
                                                    PropertyFactory.textSize(14f),
                                                    PropertyFactory.textColor(android.graphics.Color.BLUE),
                                                    PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
                                                    PropertyFactory.textHaloWidth(1.5f),
                                                    PropertyFactory.textAllowOverlap(true)
                                                )
                                                .withFilter(pointFilter)
                                        )
                                    }
                                }
                                // Center/zoom on the edited place's coordinates, else on route center
                                if (hasEditTarget) {
                                    map.moveCamera(
                                        CameraUpdateFactory.newCameraPosition(
                                            CameraPosition.Builder()
                                                .target(LatLng(editPlace!!.latitude!!, editPlace.longitude!!))
                                                .zoom(17.0)
                                                .build()
                                        )
                                    )
                                } else {
                                    val pts = mapViewModel.mapRoute.value?.routePoints.orEmpty()
                                    if (pts.isNotEmpty()) {
                                        val lats = pts.map { it.latitude }
                                        val lons = pts.map { it.longitude }
                                        map.moveCamera(
                                            CameraUpdateFactory.newCameraPosition(
                                                CameraPosition.Builder()
                                                    .target(LatLng(lats.average(), lons.average()))
                                                    .zoom(10.0)
                                                    .build()
                                            )
                                        )
                                    }
                                }
                                // Pin for the edited place — moved by tapping the map (MapLibre v11
                                // annotations have no drag support); applied via the checkmark
                                if (editPlaceId.isNotBlank() && hasEditTarget) {
                                    val marker = map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(editPlace.latitude!!, editPlace.longitude!!))
                                            .title(editPlace.name)
                                    )
                                    editMarker.value = marker
                                    // Initial selection = the place's own position
                                    mapViewModel.mapSelectionPoint.value = RoutePoint(
                                        latitude = editPlace.latitude!!,
                                        longitude = editPlace.longitude!!,
                                        placeId = editPlaceId,
                                        orderIndex = 0
                                    )
                                }
                            }
                            // Map taps: move the edit pin, navigate from place labels, or start add-place
                            map.addOnMapClickListener { latLng ->
                                Log.d(TAG_MAP_GL, "Map clicked: lat=${latLng.latitude} lon=${latLng.longitude}")
                                if (editPlaceId.isNotBlank()) {
                                    val m = editMarker.value
                                    if (m != null) {
                                        Log.i(TAG_MAP_GL, "Moving edit pin for place $editPlaceId to lat=${latLng.latitude} lon=${latLng.longitude}")
                                        m.position = latLng
                                    }
                                    mapViewModel.setMapSelectionPoint(latLng.latitude, latLng.longitude)
                                } else {
                                    mapViewModel.setMapSelectionPoint(latLng.latitude, latLng.longitude)
                                    // Tap on a labeled place point → open its detail
                                    val screenPoint = map.projection.toScreenLocation(latLng)
                                    val features = map.queryRenderedFeatures(
                                        android.graphics.PointF(screenPoint.x, screenPoint.y),
                                        "places"
                                    )
                                    val placeId = features.firstOrNull()
                                        ?.getStringProperty("id")
                                    if (!placeId.isNullOrBlank() && effectiveTripId.isNotBlank()) {
                                        Log.i(TAG_MAP_GL, "Place label tapped: placeId=$placeId")
                                        navController.navigate("place/$placeId?tripId=$effectiveTripId")
                                    } else if (addPlaceMode && onPlaceAdded != null) {
                                        mapViewModel.setMapTapPoint(latLng.latitude, latLng.longitude)
                                    }
                                }
                                true
                            }
                        }
                    }
                }
            )

            // Forward lifecycle to MapView
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_START -> mapView?.onStart()
                        androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                        androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                        androidx.lifecycle.Lifecycle.Event.ON_STOP -> mapView?.onStop()
                        androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> mapView?.onDestroy()
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    AppSession.mapGeoJson.value = null
                    mapView?.onDestroy()
                }
            }

            if (mapViewModel.isLoading.value) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Add place dialog
            if (showAddPlaceDialog && mapViewModel.mapTapPoint.value != null) {
                val tapPoint = mapViewModel.mapTapPoint.value!!
                AlertDialog(
                    onDismissRequest = {
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
                                Log.i(TAG_MAP_GL, "Add place confirmed: lat=${tapPoint.latitude}, lon=${tapPoint.longitude}, name=${newPlaceName.trim()}")
                                onPlaceAdded!!(tapPoint.latitude, tapPoint.longitude, newPlaceName.trim())
                                showAddPlaceDialog = false
                                mapViewModel.clearMapTapPoint()
                                newPlaceName = ""
                            }
                        }) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
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
