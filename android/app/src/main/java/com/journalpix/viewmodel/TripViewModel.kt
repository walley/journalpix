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
        // Demo mode: never touch the network
        if (AuthViewModel.isDemoToken(token)) {
            trips.value = demoTrips()
            isLoading.value = false
            errorMessage.value = null
            onSuccess(trips.value)
            return
        }

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
        if (AuthViewModel.isDemoToken(token)) {
            val newTrip = TripResponse(
                id = "demo-trip-${System.currentTimeMillis()}",
                userId = "00000000-0000-0000-0000-000000000001",
                title = request.title,
                description = request.description,
                startDate = request.startDate,
                endDate = request.endDate,
                status = request.status ?: TripStatus.PLANNED,
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-01-01T00:00:00Z",
                placesCount = 0,
                photosCount = 0
            )
            trips.value = listOf(newTrip) + trips.value
            isLoading.value = false
            errorMessage.value = null
            onSuccess()
            return
        }

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
        if (AuthViewModel.isDemoToken(token)) {
            currentTrip.value = demoTrips().find { it.id == tripId } ?: demoTrips().firstOrNull()
            isLoading.value = false
            errorMessage.value = null
            return
        }

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
        if (AuthViewModel.isDemoToken(token)) {
            trips.value = trips.value.map { trip ->
                if (trip.id == tripId) {
                    trip.copy(
                        title = request.title ?: trip.title,
                        description = request.description ?: trip.description,
                        startDate = request.startDate ?: trip.startDate,
                        endDate = request.endDate ?: trip.endDate,
                        status = request.status ?: trip.status
                    )
                } else trip
            }
            isLoading.value = false
            onSuccess()
            return
        }

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
        if (AuthViewModel.isDemoToken(token)) {
            trips.value = trips.value.filter { it.id != tripId }
            isLoading.value = false
            onSuccess()
            return
        }

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

    private fun demoTrips(): List<TripResponse> {
        return listOf(
            TripResponse(
                id = "demo-trip-1",
                userId = "00000000-0000-0000-0000-000000000001",
                title = "Demo Alps Weekend",
                description = "Sample offline trip for local testing",
                startDate = "2024-06-01",
                endDate = "2024-06-03",
                status = TripStatus.PLANNED,
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-01-01T00:00:00Z",
                placesCount = 2,
                photosCount = 0
            ),
            TripResponse(
                id = "demo-trip-2",
                userId = "00000000-0000-0000-0000-000000000001",
                title = "Coast Road Trip",
                description = "Another demo trip — no backend required",
                startDate = "2024-08-10",
                endDate = "2024-08-15",
                status = TripStatus.IN_PROGRESS,
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-01-01T00:00:00Z",
                placesCount = 1,
                photosCount = 0
            )
        )
    }
}
