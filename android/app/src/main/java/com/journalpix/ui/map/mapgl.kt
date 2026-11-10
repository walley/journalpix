package com.journalpix.ui.map

import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
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
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.annotations.MarkerOptions

private const val TAG_MAP_GL = "JPXMG"

/** Free vector tiles (OpenFreeMap / OSM), no API key required. */
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/** Move the camera to the device's last known location (zoom 17). */
private fun centerOnCurrentLocation(
    map: MapLibreMap?,
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
) {
    if (map == null) return
    try {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                Log.i(TAG_MAP_GL, "Centering on current location: lat=${location.latitude} lon=${location.longitude}")
                map.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(location.latitude, location.longitude))
                            .zoom(17.0)
                            .build()
                    )
                )
            } else {
                Log.w(TAG_MAP_GL, "Last known location is null")
            }
        }
    } catch (e: SecurityException) {
        Log.e(TAG_MAP_GL, "centerOnCurrentLocation: missing permission: ${e.message}")
    }
}

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

    val maplibreMap = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleRef = remember { mutableStateOf<Style?>(null) }
    val editMarker = remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    val homeMarker = remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(effectiveTripId) {
        if (effectiveTripId.isNotEmpty()) {
            // Load places from the offline store (the route param may be empty)
            val tripPlaces = places.ifEmpty { com.journalpix.data.OfflineStore.placesForTrip(effectiveTripId) }
            mapViewModel.loadTripRoute(effectiveTripId, tripPlaces)
            // Fallback: if no GeoJSON was handed over, draw the route from the store directly
            val style = styleRef.value
            if (style != null && AppSession.mapGeoJson.value.isNullOrBlank()) {
                val routeJson = com.journalpix.geo.GeoJson.forTripRoute(tripPlaces)
                runCatching {
                    if (style.getSource("geojson") == null) style.addSource(GeoJsonSource("geojson", routeJson, org.maplibre.android.style.sources.GeoJsonOptions().withMaxZoom(22).withTolerance(0.1f)))
                    else (style.getSource("geojson") as GeoJsonSource).setGeoJson(routeJson)
                    if (style.getLayer("places") == null) {
                        style.addLayer(
                            SymbolLayer("places", "geojson")
                                .withProperties(
                                    PropertyFactory.textField("{label}"),
                                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                                    PropertyFactory.textSize(14f),
                                    PropertyFactory.textColor(android.graphics.Color.BLUE),
                                    PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
                                    PropertyFactory.textHaloWidth(1.5f),
                                    PropertyFactory.textAllowOverlap(true)
                                )
                                .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("place")))
                        )
                    }
                    Log.i(TAG_MAP_GL, "Fallback route source added from store (${tripPlaces.size} places)")
                }.onFailure { Log.e(TAG_MAP_GL, "Fallback route failed: ${it.message}") }
            }
        } else {
            Log.e(TAG_MAP_GL, "MapGlScreen: effectiveTripId is BLANK — invalid trip, nothing to load")
        }
    }

    // Route polyline as an annotation (not a tiled layer) — annotations always
    // render fully across tile boundaries, unlike GeoJSON line layers
    val routePolyline = remember { mutableStateOf<org.maplibre.android.annotations.Polyline?>(null) }
    LaunchedEffect(mapViewModel.mapRoute.value?.routePoints, maplibreMap.value) {
        val map = maplibreMap.value ?: return@LaunchedEffect
        routePolyline.value?.let { map.removeAnnotation(it) }
        routePolyline.value = null
        val pts = mapViewModel.mapRoute.value?.routePoints.orEmpty()
        if (pts.size >= 2) {
            val poly = map.addPolyline(
                org.maplibre.android.annotations.PolylineOptions()
                    .addAll(pts.map { LatLng(it.latitude, it.longitude) })
                    .color(android.graphics.Color.BLUE)
                    .width(6f)
            )
            routePolyline.value = poly
            Log.i(TAG_MAP_GL, "Route polyline annotation drawn with ${pts.size} points")
        }
    }

    // Opened for editing a specific place (from place detail minimap/location icon)?
    val editPlaceId = placeIdForUpdate.ifBlank { AppSession.mapEditPlaceId.value ?: "" }
    val editPlace = editPlaceId.takeIf { it.isNotBlank() }
        ?.let { com.journalpix.data.OfflineStore.findPlaceWithPhotos(it) }
    val hasEditTarget = editPlace?.latitude != null && editPlace.longitude != null

    // "Center on current location" — needs ACCESS_FINE_LOCATION at runtime
    val fusedLocationClient = remember {
        com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(TAG_MAP_GL, "Location permission granted — centering on current location")
            centerOnCurrentLocation(maplibreMap.value, fusedLocationClient)
        } else {
            Log.w(TAG_MAP_GL, "Location permission denied — cannot center on current location")
        }
    }
    fun centerOnMyLocation() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            centerOnCurrentLocation(maplibreMap.value, fusedLocationClient)
        } else {
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

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
                    // Pick-home mode → fixed title; place view → inherit the place name
                    when {
                        AppSession.mapPickHome.value -> "Set home location"
                        else -> {
                            val placeTitle = editPlace?.name?.takeIf { it.isNotBlank() }
                            Text(placeTitle ?: mapViewModel.mapRoute.value?.tripTitle ?: "Map View")
                        }
                    }
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
                FloatingActionButton(
                    onClick = { centerOnMyLocation() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Center on current location")
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
                            // Read the edit target at CLICK time — the composition-time val may be
                            // stale; editing a place must NEVER create a new place
                            val targetEditId = placeIdForUpdate.ifBlank { AppSession.mapEditPlaceId.value ?: "" }
                            Log.i(TAG_MAP_GL, "Apply selected location: lat=${point.latitude} lon=${point.longitude}, editPlaceId='$editPlaceId', targetEditId='$targetEditId', pickHome=${AppSession.mapPickHome.value}")
                            if (AppSession.mapPickHome.value) {
                                AppSession.pendingHomeLocation.value = Pair(point.latitude, point.longitude)
                                AppSession.mapPickHome.value = false
                                navController.popBackStack()
                                return@FloatingActionButton
                            }
                            if (targetEditId.isNotBlank()) {
                                // Name must stay as entered by the user — only lat/lon change here
                                AppSession.pendingMapPlaceUpdate.value = Triple(point.latitude, point.longitude, "")
                                AppSession.mapEditPlaceId.value = null
                                // Defensive: a pending creation must never survive an edit
                                AppSession.clearPendingMapPlace()
                            } else if (AppSession.mapPickForCreateDialog.value) {
                                // Hand the tapped coords to the timeline's open create-place dialog
                                AppSession.pendingMapPlace.value = Triple(point.latitude, point.longitude, "")
                            } else {
                                // View mode: the checkmark never creates a place — creation only
                                // happens via the add-place dialog (onPlaceAdded) or the editor
                                Log.i(TAG_MAP_GL, "Checkmark in non-edit mode — no place created, closing map")
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
                                styleRef.value = style
                                // GeoJSON passed from the calling view (route polyline + place points)
                                val geoJson = AppSession.mapGeoJson.value
                                Log.i(TAG_MAP_GL, "Style loaded: geoJson=${if (geoJson.isNullOrBlank()) "NONE" else "${geoJson.length} chars"}, routePoints=${mapViewModel.mapRoute.value?.routePoints?.size ?: 0}")
                                if (!geoJson.isNullOrBlank()) {
                                    val parsed = com.journalpix.geo.GeoJson.parse(geoJson)
                                    if (parsed != null) {
                                        style.addSource(GeoJsonSource("geojson", parsed.toJson(), org.maplibre.android.style.sources.GeoJsonOptions().withMaxZoom(22).withTolerance(0.1f)))
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
                                                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
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
                                // Read the edit target at EVENT time (listener is registered once)
                                val targetEditId = placeIdForUpdate.ifBlank { AppSession.mapEditPlaceId.value ?: "" }
                                Log.d(TAG_MAP_GL, "Map clicked: lat=${latLng.latitude} lon=${latLng.longitude}, targetEditId='$targetEditId'")
                                if (targetEditId.isNotBlank()) {
                                    val m = editMarker.value
                                    if (m != null) {
                                        Log.i(TAG_MAP_GL, "Moving edit pin for place $targetEditId to lat=${latLng.latitude} lon=${latLng.longitude}")
                                        m.position = latLng
                                    }
                                    mapViewModel.setMapSelectionPoint(latLng.latitude, latLng.longitude)
                                } else {
                                    mapViewModel.setMapSelectionPoint(latLng.latitude, latLng.longitude)
                                    // Pick-home mode: drop/move a marker at the tapped point
                                    if (AppSession.mapPickHome.value) {
                                        val existing = homeMarker.value
                                        if (existing != null) {
                                            existing.position = latLng
                                        } else {
                                            homeMarker.value = map.addMarker(
                                                MarkerOptions()
                                                    .position(latLng)
                                                    .title("Home")
                                            )
                                        }
                                    }
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
                    AppSession.mapPickHome.value = false
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
