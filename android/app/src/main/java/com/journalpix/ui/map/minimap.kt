package com.journalpix.ui.map

import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.journalpix.geo.GeoJson
import com.journalpix.models.PlaceWithPhotos
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

private const val TAG_MINIMAP = "JPXMM"

/** Same free vector-tile style as the big map (mapgl.kt). */
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/**
 * Small embedded trip map rendered with the SAME MapLibre GL stack as the big map
 * (mapgl.kt). The previous openmapview minimap crashed with "Composed into the View
 * which doesn't propagate ViewTreeLifecycleOwner" inside its MarkerComposable.
 * All gestures are disabled here — tapping the minimap is handled by the parent.
 */
@Composable
fun TripMinimap(
    places: List<PlaceWithPhotos>,
    modifier: Modifier = Modifier
) {
    Log.i(TAG_MINIMAP, "TripMinimap: ${places.size} places")
    val lifecycleOwner = LocalLifecycleOwner.current
    val maplibreMap = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleRef = remember { mutableStateOf<Style?>(null) }
    val polyline = remember { mutableStateOf<org.maplibre.android.annotations.Polyline?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    // Signature of the last applied places — the AndroidView update lambda runs on every
    // recomposition, so without this gate forTripRoute+parse would run in a hot loop
    val lastAppliedSig = remember { mutableStateOf("") }

    fun applyPlaces(map: MapLibreMap, style: Style, allPlaces: List<PlaceWithPhotos>) {
        val sig = allPlaces.joinToString("|") { "${it.id}:${it.latitude}:${it.longitude}" }
        if (sig == lastAppliedSig.value) return
        lastAppliedSig.value = sig
        Log.i(TAG_MINIMAP, "applyPlaces: applying ${allPlaces.size} places (sig changed)")
        val positioned = allPlaces.filter { it.latitude != null && it.longitude != null }
        // Place points with their 1-based timeline position as label
        val fc = GeoJson.parse(GeoJson.forTripRoute(allPlaces))
        if (fc != null) {
            val source = style.getSource("minimap-geojson") as? GeoJsonSource
            if (source == null) {
                style.addSource(
                    GeoJsonSource(
                        "minimap-geojson", fc.toJson(),
                        org.maplibre.android.style.sources.GeoJsonOptions().withMaxZoom(22).withTolerance(0.1f)
                    )
                )
                style.addLayer(
                    SymbolLayer("minimap-places", "minimap-geojson")
                        .withProperties(
                            PropertyFactory.textField("{position}"),
                            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                            PropertyFactory.textSize(12f),
                            PropertyFactory.textColor(android.graphics.Color.BLUE),
                            PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
                            PropertyFactory.textHaloWidth(1.5f),
                            PropertyFactory.textAllowOverlap(true)
                        )
                        .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("place")))
                )
            } else {
                source.setGeoJson(fc.toJson())
            }
        }
        // Route polyline as an annotation (annotations render across tile boundaries,
        // unlike tiled GeoJSON line layers)
        polyline.value?.let { map.removeAnnotation(it) }
        polyline.value = null
        if (positioned.size >= 2) {
            polyline.value = map.addPolyline(
                org.maplibre.android.annotations.PolylineOptions()
                    .addAll(positioned.map { LatLng(it.latitude!!, it.longitude!!) })
                    .color(android.graphics.Color.BLUE)
                    .width(4f)
            )
        }
        // Fit the camera to all points (fall back to average center if bounds fail pre-layout)
        if (positioned.isNotEmpty()) {
            val builder = LatLngBounds.Builder()
            positioned.forEach { builder.include(LatLng(it.latitude!!, it.longitude!!)) }
            runCatching {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 48))
            }.onFailure {
                val avgLat = positioned.map { it.latitude!! }.average()
                val avgLon = positioned.map { it.longitude!! }.average()
                map.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        org.maplibre.android.camera.CameraPosition.Builder()
                            .target(LatLng(avgLat, avgLon))
                            .zoom(11.0)
                            .build()
                    )
                )
            }
        }
    }

    AndroidView(
        modifier = modifier,
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
                    Log.i(TAG_MINIMAP, "Minimap MapLibre map ready")
                    // Non-interactive: the parent Box handles taps (opens the big map)
                    map.uiSettings.apply {
                        isScrollGesturesEnabled = false
                        isZoomGesturesEnabled = false
                        isRotateGesturesEnabled = false
                        isTiltGesturesEnabled = false
                        isDoubleTapGesturesEnabled = false
                        isQuickZoomGesturesEnabled = false
                        isCompassEnabled = false
                        isLogoEnabled = false
                        isAttributionEnabled = false
                    }
                    map.setStyle(MAP_STYLE_URL) { style ->
                        styleRef.value = style
                        applyPlaces(map, style, places)
                    }
                }
            }
        },
        update = { view ->
            // Re-apply markers/route when the places list changes and the style is loaded
            val map = maplibreMap.value
            val style = styleRef.value
            if (map != null && style != null && view.isLaidOut) applyPlaces(map, style, places)
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
            mapView?.onDestroy()
        }
    }
}
