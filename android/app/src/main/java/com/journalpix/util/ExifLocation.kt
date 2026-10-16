package com.journalpix.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream

data class PhotoGps(
    val latitude: Double,
    val longitude: Double,
    val takenAt: String? = null
)

object ExifLocation {

    /** Read GPS and date from image EXIF. Works offline from local/content URIs. */
    fun read(context: Context, uri: Uri): PhotoGps? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                readFromStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun readFromStream(stream: InputStream): PhotoGps? {
        return try {
            val exif = ExifInterface(stream)
            val latLong = FloatArray(2)
            val hasGps = exif.getLatLong(latLong)
            val takenAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            if (hasGps) {
                PhotoGps(
                    latitude = latLong[0].toDouble(),
                    longitude = latLong[1].toDouble(),
                    takenAt = takenAt
                )
            } else if (takenAt != null) {
                PhotoGps(latitude = 0.0, longitude = 0.0, takenAt = takenAt).takeIf { false }
                    ?: PhotoGps(0.0, 0.0, takenAt).let {
                        // No GPS — return null coords via nullable fields in caller
                        null
                    }.let {
                        // Re-structure: return object with nulls not possible on PhotoGps
                        // Use separate path
                        null
                    }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Returns lat/lon if present; takenAt may still be set when GPS is missing. */
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
                } else null
                gps to takenAt
            } ?: (null to null)
        } catch (_: Exception) {
            null to null
        }
    }
}
