package com.journalpix.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.api.ApiService
import com.journalpix.data.OfflineStore
import com.journalpix.models.PhotoResponse
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.models.UpdatePhotoRequest
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

    fun getPhotosForPlace(token: String, placeId: String) {
        if (AuthViewModel.isDemoToken(token) || token.isBlank()) {
            photos.value = OfflineStore.photosFor(placeId)
            OfflineStore.addresses.forEach { (id, addr) -> resolvedAddresses[id] = addr }
            isLoading.value = false
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val response = ApiService.apiClient.getPhotosForPlace("Bearer $token", placeId)
                if (response.success) photos.value = response.data ?: emptyList()
                else errorMessage.value = response.error ?: "Failed to load photos"
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
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
                    takenAt = result.gps?.takenAt ?: result.takenAt,
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
            val result = NominatimClient.reverseGeocode(lat, lon)
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

    fun uploadPhoto(token: String, placeId: String, bitmap: Bitmap, onSuccess: () -> Unit) {
        if (AuthViewModel.isDemoToken(token) || token.isBlank()) {
            errorMessage.value = "Use gallery picker for offline photos"
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            try {
                val byteArray = bitmapToByteArray(bitmap)
                val requestFile = byteArray.toRequestBody("image/jpeg".toMediaType())
                val body = MultipartBody.Part.createFormData("file", "photo.jpg", requestFile)
                val response = ApiService.apiClient.uploadPhoto("Bearer $token", placeId, body)
                if (response.success) onSuccess()
                else errorMessage.value = response.error ?: "Failed to upload photo"
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun getPhoto(token: String, photoId: String) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = ApiService.apiClient.getPhoto("Bearer $token", photoId)
                if (response.success) currentPhoto.value = response.data
                else errorMessage.value = response.error ?: "Failed to load photo"
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun updatePhoto(token: String, photoId: String, request: UpdatePhotoRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = ApiService.apiClient.updatePhoto("Bearer $token", photoId, request)
                if (response.success) onSuccess()
                else errorMessage.value = response.error ?: "Failed to update photo"
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun deletePhoto(token: String, photoId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = ApiService.apiClient.deletePhoto("Bearer $token", photoId)
                if (response.success) onSuccess()
                else errorMessage.value = response.error ?: "Failed to delete photo"
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        return outputStream.toByteArray()
    }
}
