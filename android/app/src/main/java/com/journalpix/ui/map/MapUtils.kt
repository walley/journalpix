package com.journalpix.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import de.afarber.openmapview.model.LatLng

object MapUtils {
    
    // Create a marker icon with text
    fun createMarkerIcon(context: Context, text: String, color: Int = Color.BLUE): Bitmap {
        val paint = Paint().apply {
            this.color = color
            textSize = 40f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        val textWidth = paint.measureText(text)
        val width = (textWidth + 32).toInt().coerceAtLeast(48)
        val height = 48
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw circle background
        val circlePaint = Paint().apply {
            this.color = color
            isAntiAlias = true
        }
        canvas.drawCircle(width / 2f, height / 2f, width / 2f, circlePaint)
        
        // Draw text
        canvas.drawText(
            text,
            width / 2f,
            height / 2f + paint.textSize / 3,
            paint.apply { this.color = Color.WHITE }
        )
        
        return bitmap
    }
    
    // Calculate distance between two points in meters
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = (Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2))
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distance = 6371000 * c // Earth radius in meters
        
        return distance
    }
    
    // Calculate bounds that contain all points
    fun calculateBounds(points: List<LatLng>): MapBounds {
        if (points.isEmpty()) {
            return MapBounds(0.0, 0.0, 0.0, 0.0)
        }
        
        var minLat = points[0].latitude
        var maxLat = points[0].latitude
        var minLon = points[0].longitude
        var maxLon = points[0].longitude
        
        points.forEach { point ->
            minLat = minOf(minLat, point.latitude)
            maxLat = maxOf(maxLat, point.latitude)
            minLon = minOf(minLon, point.longitude)
            maxLon = maxOf(maxLon, point.longitude)
        }
        
        return MapBounds(
            minLatitude = minLat,
            maxLatitude = maxLat,
            minLongitude = minLon,
            maxLongitude = maxLon
        )
    }
    
    // Calculate center from bounds
    fun calculateCenter(bounds: MapBounds): LatLng {
        return LatLng(
            (bounds.minLatitude + bounds.maxLatitude) / 2,
            (bounds.minLongitude + bounds.maxLongitude) / 2
        )
    }
    
    // Calculate zoom level to fit bounds
    fun calculateZoomLevel(bounds: MapBounds, mapWidth: Int, mapHeight: Int): Float {
        val worldWidth = 256.0 // World width in pixels at zoom level 0
        val latDiff = bounds.maxLatitude - bounds.minLatitude
        val lonDiff = bounds.maxLongitude - bounds.minLongitude
        
        // Calculate the required zoom level
        val latZoom = Math.log(worldWidth * mapHeight / (256 * latDiff)) / Math.log(2.0)
        val lonZoom = Math.log(worldWidth * mapWidth / (256 * lonDiff)) / Math.log(2.0)
        
        return minOf(latZoom, lonZoom).toFloat() - 1f // Subtract 1 for padding
    }
}

data class MapBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
)
