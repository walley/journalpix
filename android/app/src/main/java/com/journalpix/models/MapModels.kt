package com.journalpix.models

import android.graphics.Bitmap

// Map route point
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val placeId: String? = null,
    val orderIndex: Int = 0
)

// Map place marker
data class MapPlace(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val thumbnailBitmap: Bitmap? = null
)

// Map route with places
data class MapRoute(
    val tripId: String,
    val tripTitle: String,
    val routePoints: List<RoutePoint>,
    val places: List<MapPlace>
)

// Bubble state for markers
data class MarkerBubbleState(
    val placeId: String,
    val isExpanded: Boolean = false
)

// Map viewport state
data class MapViewportState(
    val centerLatitude: Double,
    val centerLongitude: Double,
    val zoom: Float = 12f,
    val minZoom: Float = 2f,
    val maxZoom: Float = 18f
)

// Geographic bounds with computed center
data class MapBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
    val centerLatitude: Double = (minLatitude + maxLatitude) / 2,
    val centerLongitude: Double = (minLongitude + maxLongitude) / 2
)
