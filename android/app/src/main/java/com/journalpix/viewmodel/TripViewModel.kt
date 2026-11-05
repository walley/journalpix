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

    // No token parameters anywhere: trips are read from the local OfflineStore,
    // server sync (the only place the token is used) is triggered via SyncManager.

    fun getTrips(onSuccess: (List<TripResponse>) -> Unit = {}) {
        trips.value = com.journalpix.data.OfflineStore.allTrips()
        isLoading.value = false
        errorMessage.value = null
        onSuccess(trips.value)
    }

    fun createTrip(request: CreateTripRequest, onSuccess: () -> Unit) {
        val local = com.journalpix.data.OfflineStore.createLocalTrip(
            request.title, request.startDate, request.endDate
        )
        trips.value = trips.value + local
        isLoading.value = false
        errorMessage.value = null
        onSuccess()
    }

    fun getTrip(tripId: String) {
        currentTrip.value = com.journalpix.data.OfflineStore.getTrip(tripId)
        isLoading.value = false
        errorMessage.value = null
    }

    fun updateTrip(tripId: String, request: UpdateTripRequest, onSuccess: () -> Unit) {
        com.journalpix.data.OfflineStore.updateTripTitle(tripId, request.title ?: "")
        onSuccess()
    }

    fun deleteTrip(tripId: String, onSuccess: () -> Unit) {
        com.journalpix.data.OfflineStore.deleteTripLocal(tripId)
        trips.value = trips.value.filterNot { it.id == tripId }
        onSuccess()
    }
}
