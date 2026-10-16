package com.journalpix.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.models.*
import kotlinx.coroutines.launch

class MapViewModel : ViewModel() {
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val mapRoute = mutableStateOf<MapRoute?>(null)
    val selectedPlace = mutableStateOf<MapPlace?>(null)
    val bubbleStates = mutableStateOf<Map<String, Boolean>>(emptyMap())
    val viewportState = mutableStateOf(
        MapViewportState(
            centerLatitude = 0.0,
            centerLongitude = 0.0,
            zoom = 12f
        )
    )

    fun loadTripRoute(tripId: String, places: List<PlaceWithPhotos>) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                val routePoints = mutableListOf<RoutePoint>()
                val mapPlaces = mutableListOf<MapPlace>()

                places.forEachIndexed { index, place ->
                    place.latitude?.let { lat ->
                        place.longitude?.let { lon ->
                            routePoints.add(
                                RoutePoint(
                                    latitude = lat,
                                    longitude = lon,
                                    placeId = place.id,
                                    orderIndex = index
                                )
                            )

                            // Get first photo as thumbnail
                            val thumbnailUrl = place.photos.firstOrNull()?.thumbnailPath

                            mapPlaces.add(
                                MapPlace(
                                    id = place.id,
                                    latitude = lat,
                                    longitude = lon,
                                    name = place.name,
                                    description = place.description,
                                    thumbnailUrl = thumbnailUrl,
                                    thumbnailBitmap = null
                                )
                            )
                        }
                    }
                }

                // Calculate center point
                if (routePoints.isNotEmpty()) {
                    val center = calculateCenter(routePoints)
                    viewportState.value = viewportState.value.copy(
                        centerLatitude = center.latitude,
                        centerLongitude = center.longitude
                    )
                }

                mapRoute.value = MapRoute(
                    tripId = tripId,
                    tripTitle = "Trip ${tripId.take(8)}",
                    routePoints = routePoints,
                    places = mapPlaces
                )

                // Initialize bubble states
                bubbleStates.value = mapPlaces.associate { it.id to false }

            } catch (e: Exception) {
                errorMessage.value = "Failed to load map: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun toggleBubble(placeId: String) {
        bubbleStates.value = bubbleStates.value.toMutableMap().apply {
            put(placeId, !(get(placeId) ?: false))
        }
    }

    fun selectPlace(place: MapPlace) {
        selectedPlace.value = place
    }

    fun clearSelection() {
        selectedPlace.value = null
    }

    fun fitToRoute() {
        mapRoute.value?.let { route ->
            if (route.routePoints.isNotEmpty()) {
                val bounds = calculateBounds(route.routePoints)
                // TODO: Calculate zoom level based on bounds
                viewportState.value = viewportState.value.copy(
                    centerLatitude = bounds.centerLatitude,
                    centerLongitude = bounds.centerLongitude,
                    zoom = 14f
                )
            }
        }
    }

    private fun calculateCenter(points: List<RoutePoint>): RoutePoint {
        if (points.isEmpty()) return RoutePoint(0.0, 0.0)

        val avgLat = points.map { it.latitude }.average()
        val avgLon = points.map { it.longitude }.average()

        return RoutePoint(
            latitude = avgLat,
            longitude = avgLon
        )
    }

    private fun calculateBounds(points: List<RoutePoint>): MapBounds {
        if (points.isEmpty()) {
            return MapBounds(0.0, 0.0, 0.0, 0.0)
        }

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }

        return MapBounds(
            minLatitude = minLat,
            maxLatitude = maxLat,
            minLongitude = minLon,
            maxLongitude = maxLon
        )
    }

    fun updateViewport(latitude: Double, longitude: Double, zoom: Float) {
        viewportState.value = viewportState.value.copy(
            centerLatitude = latitude,
            centerLongitude = longitude,
            zoom = zoom
        )
    }
}
