package com.journalpix.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.api.ApiService
import com.journalpix.data.OfflineStore
import com.journalpix.models.PhotoResponse
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.models.UpdatePhotoRequest
import com.journalpix.models.ZonedTime
import com.journalpix.util.ExifLocation
import com.journalpix.util.NominatimClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class PhotoViewModel : ViewModel() {
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val photos = mutableStateOf<List<PhotoResponse>>(emptyList())
    val currentPhoto = mutableStateOf<PhotoResponse?>(null)
    val resolvedAddresses = mutableStateMapOf<String, String>()
    val geocodeLoadingIds = mutableStateMapOf<String, Boolean>()
    val geocodeErrors = mutableStateMapOf<String, String>()
    val lastExifNote = mutableStateOf<String?>(null)

    fun getPhotosForPlace(placeId: String) {
      // Local-only; server photos are merged by SyncManager (token never leaves it)
      photos.value = OfflineStore.photosFor(placeId)
      OfflineStore.addresses.forEach { (id, addr) -> resolvedAddresses[id] = addr }
      isLoading.value = false
    }

    fun addPhotoOffline(
        context: Context,
        placeId: String,
        uri: Uri,
        onSuccess: (PhotoResponse) -> Unit = {}
    ) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            lastExifNote.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    ExifLocation.readMeta(context, uri)
                }
                val pathForDisplay = result.localFilePath ?: uri.toString()
                val photo = OfflineStore.addPhoto(
                    placeId = placeId,
                    uriString = pathForDisplay,
                    latitude = result.gps?.latitude,
                    longitude = result.gps?.longitude,
                    takenAt = ZonedTime.parseLegacy(result.gps?.takenAt ?: result.takenAt),
                    caption = null
                )
                if (result.gps == null) {
                    lastExifNote.value = result.note
                        ?: "No GPS found in EXIF. Grant media location permission on Android 10+."
                }
                photos.value = OfflineStore.photosFor(placeId)
                onSuccess(photo)
            } catch (e: Exception) {
                errorMessage.value = "Failed to add photo: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    /**
     * Reverse geocode → create/find place by address → move photo → re-sort.
     */
    fun reverseGeocode(
        tripId: String,
        photo: PhotoResponse,
        onPlaceReady: (PlaceWithPhotos) -> Unit = {}
    ) {
        val lat = photo.latitude
        val lon = photo.longitude
        if (lat == null || lon == null) {
            geocodeErrors[photo.id] = "No GPS in this photo"
            return
        }
        viewModelScope.launch {
            geocodeLoadingIds[photo.id] = true
            geocodeErrors.remove(photo.id)
            val result = withContext(Dispatchers.IO) {
                NominatimClient.reverseGeocode(lat, lon)
            }
            result.onSuccess { address ->
                resolvedAddresses[photo.id] = address
                OfflineStore.addresses[photo.id] = address
                val place = OfflineStore.assignPhotoToGeocodedPlace(
                    tripId = tripId,
                    photoId = photo.id,
                    address = address,
                    latitude = lat,
                    longitude = lon
                )
                photos.value = OfflineStore.photosFor(place.id)
                onPlaceReady(place)
            }.onFailure { e ->
                geocodeErrors[photo.id] = e.message ?: "Geocode failed"
            }
            geocodeLoadingIds[photo.id] = false
        }
    }

    /**
     * Mass-upload helper: read EXIF (GPS + taken-at) and store the picture as an
     * UNASSIGNED photo (shown in the pool under the timeline minimap) until the user
     * moves it to a day.
     */
    fun addUnassignedPhotoOffline(
        context: Context,
        uri: Uri,
        onDone: (PhotoResponse?) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ExifLocation.readMeta(context, uri)
                }
                val pathForDisplay = result.localFilePath ?: uri.toString()
                val photo = OfflineStore.addUnassignedPhoto(
                    uriString = pathForDisplay,
                    latitude = result.gps?.latitude,
                    longitude = result.gps?.longitude,
                    takenAt = ZonedTime.parseLegacy(result.gps?.takenAt ?: result.takenAt),
                    caption = null
                )
                if (result.gps == null) {
                    lastExifNote.value = result.note
                }
                onDone(photo)
            } catch (e: Exception) {
                errorMessage.value = "Failed to add photo: ${e.message}"
                onDone(null)
            }
        }
    }

    /**
     * Create a new place at the photo's GPS for a day-pool photo and move the photo into it.
     * The place name comes from a reverse-geocode of the coordinates when available.
     */
    fun createPlaceForPhoto(tripId: String, photo: PhotoResponse, onDone: (PlaceWithPhotos?) -> Unit = {}) {
        viewModelScope.launch {
            Log.i("JPXPV", "createPlaceForPhoto: photo ${photo.id} (trip=$tripId)")
            val lat = photo.latitude
            val lon = photo.longitude
            if (lat == null || lon == null) {
                Log.e("JPXPV", "createPlaceForPhoto: photo ${photo.id} has no GPS — cannot create place")
                errorMessage.value = "No GPS in this photo"
                onDone(null)
                return@launch
            }
            val address = withContext(Dispatchers.IO) {
                NominatimClient.reverseGeocode(lat, lon).getOrNull()
            }
            if (address != null) {
                resolvedAddresses[photo.id] = address
                OfflineStore.addresses[photo.id] = address
            }
            val place = OfflineStore.createPlaceForPhoto(tripId, photo.id, address)
            if (place != null) {
                Log.i("JPXPV", "createPlaceForPhoto: created place ${place.id} ('${place.name}') for photo ${photo.id}")
            } else {
                Log.e("JPXPV", "createPlaceForPhoto: FAILED for photo ${photo.id}")
                errorMessage.value = "Could not create place for photo"
            }
            onDone(place)
        }
    }

    /** Move a photo into a trip day's pool (assigns it to that day's place). */
    fun assignPhotoToDay(tripId: String, photoId: String, dayIso: String, onDone: () -> Unit = {}) {
        val target = OfflineStore.assignPhotoToDay(tripId, photoId, dayIso)
        if (target != null) {
            android.util.Log.i("JPXPV", "assignPhotoToDay: photo $photoId moved to place ${target.id} for day $dayIso")
        } else {
            android.util.Log.e("JPXPV", "assignPhotoToDay: FAILED — photo $photoId not moved to day '$dayIso' (trip=$tripId)")
            errorMessage.value = "Could not move photo to day"
        }
        onDone()
    }

    fun uploadPhoto(placeId: String, bitmap: Bitmap, onSuccess: () -> Unit) {
      // Local-only: photos are stored offline; SyncManager uploads them when sync is enabled
      errorMessage.value = "Use gallery picker for photos"
    }

    fun getPhoto(photoId: String) {
        currentPhoto.value = OfflineStore.findPhotoPublic(photoId)
        isLoading.value = false
    }

    fun updatePhoto(photoId: String, request: UpdatePhotoRequest, onSuccess: () -> Unit) {
        OfflineStore.updatePhotoLocal(photoId, request.caption)
        onSuccess()
    }

    fun deletePhoto(photoId: String, onSuccess: () -> Unit) {
        OfflineStore.deletePhotoLocal(photoId)
        onSuccess()
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        return outputStream.toByteArray()
    }
}
