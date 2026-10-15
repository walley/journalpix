package com.journalpix.viewmodel

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.api.ApiService
import com.journalpix.models.*
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
    
    fun getPhotosForPlace(token: String, placeId: String) {
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
    
    fun uploadPhoto(token: String, placeId: String, bitmap: Bitmap, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            
            try {
                val byteArray = bitmapToByteArray(bitmap)
                val requestFile = byteArray.toRequestBody("image/jpeg".toMediaType())
                val body = MultipartBody.Part.createFormData("file", "photo.jpg", requestFile)
                
                val response = ApiService.apiClient.uploadPhoto("Bearer $token", placeId, body)
                
                if (response.success) {
                    onSuccess()
                } else {
                    errorMessage.value = response.error ?: "Failed to upload photo"
                }
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
            errorMessage.value = null
            
            try {
                val response = ApiService.apiClient.getPhoto("Bearer $token", photoId)
                
                if (response.success) {
                    currentPhoto.value = response.data
                } else {
                    errorMessage.value = response.error ?: "Failed to load photo"
                }
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
            errorMessage.value = null
            
            try {
                val response = ApiService.apiClient.updatePhoto("Bearer $token", photoId, request)
                
                if (response.success) {
                    onSuccess()
                } else {
                    errorMessage.value = response.error ?: "Failed to update photo"
                }
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
            errorMessage.value = null
            
            try {
                val response = ApiService.apiClient.deletePhoto("Bearer $token", photoId)
                
                if (response.success) {
                    onSuccess()
                } else {
                    errorMessage.value = response.error ?: "Failed to delete photo"
                }
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
