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
import com.journalpix.models.UpdatePhotoRequest
import com.journalpix.util.ExifLocation
import com.journalpix.util.NominatimClient
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class PhotoViewModel : ViewModel() {
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val photos = mutableStateOf<List<PhotoResponse>>(emptyList())
    val currentPhoto = mutableStateOf<PhotoResponse?>(null)
    /** photoId -> reverse-geocoded address (Nominatim) */
    val resolvedAddresses = mutableStateMapOf<String, String>()
    val geocodeLoadingIds = mutableStateMapOf<String, Boolean>()
    val geocodeErrors = mutableStateMapOf<String, String>()

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
                if (response.success) {
                    photos.value = response.data ?: emptyList()
                } else {
                    errorMessage.value = response.error ?: "Failed to load photos"
                }
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    /**
     * Offline-first upload: copy URI into OfflineStore, read EXIF GPS locally.
     * No backend required.
     */
    fun addPhotoOffline(
        context: Context,
        placeId: String,
        uri: Uri,
        onSuccess: (PhotoResponse) -> Unit = {}
    ) {
        isLoading.value = true
        errorMessage.value = null
        try {
            val (gps, takenAtFallback) = ExifLocation.readMeta(context, uri)
            val photo = OfflineStore.addPhoto(
                placeId = placeId,
                uriString = uri.toString(),
                latitude = gps?.latitude,
                longitude = gps?.longitude,
                takenAt = gps?.takenAt ?: takenAtFallback,
                caption = null
            )
            photos.value = OfflineStore.photosFor(placeId)
            onSuccess(photo)
        } catch (e: Exception) {
            errorMessage.value = "Failed to add photo: ${e.message}"
        } finally {
            isLoading.value = false
        }
    }

    fun reverseGeocode(photo: PhotoResponse) {
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
            }.onFailure { e ->
                geocodeErrors[photo.id] = e.message ?: "Geocode failed"
            }
            geocodeLoadingIds[photo.id] = false
        }
    }

    fun uploadPhoto(token: String, placeId: String, bitmap: Bitmap, onSuccess: () -> Unit) {
        if (AuthViewModel.isDemoToken(token) || token.isBlank()) {
            // Bitmap path without URI — not used in offline picker flow
            errorMessage.value = "Use gallery picker for offline photos"
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
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
