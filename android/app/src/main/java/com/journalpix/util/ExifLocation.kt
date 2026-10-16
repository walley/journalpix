package com.journalpix.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

data class PhotoGps(
    val latitude: Double,
    val longitude: Double,
    val takenAt: String? = null
)

object ExifLocation {

    /**
     * Read GPS + capture time from image EXIF (fully offline).
     * @return Pair(gpsOrNull, takenAtOrNull)
     */
    fun readMeta(context: Context, uri: Uri): Pair<PhotoGps?, String?> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = FloatArray(2)
                val hasGps = exif.getLatLong(latLong)
                val takenAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                val gps = if (hasGps) {
                    PhotoGps(latLong[0].toDouble(), latLong[1].toDouble(), takenAt)
                } else {
                    null
                }
                gps to takenAt
            } ?: (null to null)
        } catch (_: Exception) {
            null to null
        }
    }
}
