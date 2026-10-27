package com.journalpix.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.api.ApiService
import com.journalpix.data.OfflineStore
import com.journalpix.models.*
import kotlinx.coroutines.launch

class PlaceViewModel : ViewModel() {
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val places = mutableStateOf<List<PlaceResponse>>(emptyList())
    val currentPlace = mutableStateOf<PlaceWithPhotos?>(null)

    /**
     * The backend stores visit dates as NaiveDateTime ("YYYY-MM-DDTHH:MM:SS");
     * the UI/offline store uses plain dates, so expand them for API requests.
     */
    private fun normalizeVisitDate(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return if (Regex("""^\d{4}-\d{2}-\d{2}$""").matches(value)) {
            "${value}T00:00:00"
        } else {
            value
        }
    }

    fun getPlaces(token: String, tripId: String) {
        if (AuthViewModel.isDemoToken(token) || token.isBlank()) {
            isLoading.value = false
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val response = ApiService.apiClient.getPlaces("Bearer $token", tripId)
                if (response.success) places.value = response.data ?: emptyList()
                else errorMessage.value = response.error ?: "Failed to load places"
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun createPlace(token: String, tripId: String, request: CreatePlaceRequest, onSuccess: () -> Unit) {
        if (AuthViewModel.isDemoToken(token) || token.isBlank() || token == "Bearer token") {
            OfflineStore.addPlace(tripId, request.name, visitDate = request.visitDate)
            isLoading.value = false
            errorMessage.value = null
            onSuccess()
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val apiRequest = request.copy(visitDate = normalizeVisitDate(request.visitDate))
                val response = ApiService.apiClient.createPlace("Bearer $token", tripId, apiRequest)
                if (response.success) onSuccess()
                else errorMessage.value = response.error ?: "Failed to create place"
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun getPlace(token: String, placeId: String) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = ApiService.apiClient.getPlace("Bearer $token", placeId)
                if (response.success) currentPlace.value = response.data
                else errorMessage.value = response.error ?: "Failed to load place"
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun updatePlace(token: String, placeId: String, request: UpdatePlaceRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val apiRequest = request.copy(visitDate = normalizeVisitDate(request.visitDate))
                val response = ApiService.apiClient.updatePlace("Bearer $token", placeId, apiRequest)
                if (response.success) onSuccess()
                else errorMessage.value = response.error ?: "Failed to update place"
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun deletePlace(token: String, placeId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = ApiService.apiClient.deletePlace("Bearer $token", placeId)
                if (response.success) onSuccess()
                else errorMessage.value = response.error ?: "Failed to delete place"
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }
}
