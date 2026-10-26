package com.journalpix.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

data class PhotoGps(
    val latitude: Double,
    val longitude: Double,
    val takenAt: String? = null
)

data class ExifReadResult(
    val gps: PhotoGps?,
    val takenAt: String?,
    /** Local file path under app filesDir/photos (durable offline storage). */
    val localFilePath: String?,
    /** Why GPS might be missing — useful in the UI. */
    val note: String?
)

object ExifLocation {

    /**
     * Robust EXIF read for gallery/content URIs.
     *
     * Android often strips location from picker streams. Strategy:
     * 1. MediaStore.setRequireOriginal (API 29+) so location EXIF is not redacted
     * 2. Open a seekable FileDescriptor (ExifInterface needs seek)
     * 3. Copy bytes to filesDir/photos and read from the file path (survives cache clear)
     * 4. Manual GPS tags as last resort
     */
    fun readMeta(context: Context, uri: Uri): ExifReadResult {
        var workingUri = uri
        var note: String? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                workingUri = MediaStore.setRequireOriginal(uri)
            } catch (e: Exception) {
                note = "Location may be restricted (grant media location permission). ${e.message}"
                workingUri = uri
            }
        }

        val localFile = copyToLocalPhotos(context, workingUri) ?: copyToLocalPhotos(context, uri)

        if (localFile != null) {
            val fromFile = readFromPath(localFile.absolutePath)
            if (fromFile.gps != null) {
                return fromFile.copy(localFilePath = localFile.absolutePath, note = null)
            }
            val fromFd = readFromFileDescriptor(context, workingUri)
            if (fromFd.gps != null) {
                return fromFd.copy(
                    localFilePath = localFile.absolutePath,
                    takenAt = fromFd.takenAt ?: fromFile.takenAt
                )
            }
            return ExifReadResult(
                gps = null,
                takenAt = fromFile.takenAt ?: fromFd.takenAt,
                localFilePath = localFile.absolutePath,
                note = note ?: "No GPS tags in this image (camera location may have been off, or the gallery stripped it)."
            )
        }

        val fromFd = readFromFileDescriptor(context, workingUri)
        if (fromFd.gps != null) return fromFd.copy(note = note)

        val fromFd2 = if (workingUri != uri) readFromFileDescriptor(context, uri) else fromFd
        if (fromFd2.gps != null) return fromFd2

        return ExifReadResult(
            gps = null,
            takenAt = fromFd.takenAt ?: fromFd2.takenAt,
            localFilePath = null,
            note = note ?: "Could not read EXIF from this URI."
        )
    }

    private fun readFromPath(path: String): ExifReadResult {
        return try {
            val exif = ExifInterface(path)
            parseExif(exif, localPath = path)
        } catch (e: Exception) {
            ExifReadResult(null, null, path, e.message)
        }
    }

    private fun readFromFileDescriptor(context: Context, uri: Uri): ExifReadResult {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                parseExif(exif, localPath = null)
            } ?: ExifReadResult(null, null, null, "openFileDescriptor returned null")
        } catch (e: Exception) {
            ExifReadResult(null, null, null, e.message)
        }
    }

    private fun parseExif(exif: ExifInterface, localPath: String?): ExifReadResult {
        val takenAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

        val latLong = FloatArray(2)
        if (exif.getLatLong(latLong)) {
            return ExifReadResult(
                gps = PhotoGps(latLong[0].toDouble(), latLong[1].toDouble(), takenAt),
                takenAt = takenAt,
                localFilePath = localPath,
                note = null
            )
        }

        val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
        val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
        val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
        val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
        if (lat != null && latRef != null && lon != null && lonRef != null) {
            val latitude = dmsToDecimal(lat, latRef)
            val longitude = dmsToDecimal(lon, lonRef)
            if (latitude != null && longitude != null) {
                return ExifReadResult(
                    gps = PhotoGps(latitude, longitude, takenAt),
                    takenAt = takenAt,
                    localFilePath = localPath,
                    note = null
                )
            }
        }

        return ExifReadResult(null, takenAt, localPath, null)
    }

    private fun dmsToDecimal(dms: String, ref: String): Double? {
        return try {
            val parts = dms.split(",")
            if (parts.size < 3) return null
            fun ratio(s: String): Double {
                val p = s.trim().split("/")
                val num = p[0].toDouble()
                val den = if (p.size > 1) p[1].toDouble() else 1.0
                return if (den == 0.0) 0.0 else num / den
            }
            var dec = ratio(parts[0]) + ratio(parts[1]) / 60.0 + ratio(parts[2]) / 3600.0
            if (ref.equals("S", true) || ref.equals("W", true)) dec = -dec
            dec
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Copy full image bytes into app filesDir/photos so storage survives cache clears
     * and is ready for later backend sync.
     */
    fun copyToLocalPhotos(context: Context, uri: Uri): File? {
        return try {
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val out = File(dir, "img_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            } ?: return null
            if (out.length() == 0L) {
                out.delete()
                null
            } else out
        } catch (_: Exception) {
            null
        }
    }

    @Deprecated("Use copyToLocalPhotos", ReplaceWith("copyToLocalPhotos(context, uri)"))
    fun copyToCache(context: Context, uri: Uri): File? = copyToLocalPhotos(context, uri)
}
