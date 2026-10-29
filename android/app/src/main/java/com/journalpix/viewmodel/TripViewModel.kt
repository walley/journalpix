package com.journalpix.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.api.ApiService
import com.journalpix.models.*
import kotlinx.coroutines.launch

class TripViewModel : ViewModel() {
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val trips = mutableStateOf<List<TripResponse>>(emptyList())
    val currentTrip = mutableStateOf<TripResponse?>(null)

    fun getTrips(token: String, onSuccess: (List<TripResponse>) -> Unit = {}) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                val response = ApiService.apiClient.getTrips("Bearer $token")

                if (response.success) {
                    trips.value = response.data ?: emptyList()
                    onSuccess(trips.value)
                } else {
                    errorMessage.value = response.error ?: "Failed to load trips"
                }
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun createTrip(token: String, request: CreateTripRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                val response = ApiService.apiClient.createTrip("Bearer $token", request)

                if (response.success) {
                    response.data?.let {
                        onSuccess()
                    }
                } else {
                    errorMessage.value = response.error ?: "Failed to create trip"
                }
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun getTrip(token: String, tripId: String) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                val response = ApiService.apiClient.getTrip("Bearer $token", tripId)

                if (response.success) {
                    currentTrip.value = response.data
                } else {
                    errorMessage.value = response.error ?: "Failed to load trip"
                }
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun updateTrip(token: String, tripId: String, request: UpdateTripRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                val response = ApiService.apiClient.updateTrip("Bearer $token", tripId, request)

                if (response.success) {
                    onSuccess()
                } else {
                    errorMessage.value = response.error ?: "Failed to update trip"
                }
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun deleteTrip(token: String, tripId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                val response = ApiService.apiClient.deleteTrip("Bearer $token", tripId)

                if (response.success) {
                    onSuccess()
                } else {
                    errorMessage.value = response.error ?: "Failed to delete trip"
                }
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }
}
