package com.journalpix.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.api.ApiService
import com.journalpix.data.OfflineStore
import com.journalpix.models.*
import com.journalpix.models.ZonedTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.util.Log
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private const val TAG_PLACE_VM = "JPXPV"

class PlaceViewModel : ViewModel() {
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val places = mutableStateOf<List<PlaceWithPhotos>>(emptyList())
    val currentPlace = mutableStateOf<PlaceWithPhotos?>(null)

    /** Convert a ZonedTimeInput (from API request) into a ZonedTime for offline storage. */
    private fun toZonedTime(input: ZonedTimeInput?): ZonedTime? {
        return input?.let {
            it.utc?.let { utc ->
                ZonedTime.fromInstant(Instant.parse(utc), it.timezone ?: ZoneId.systemDefault().id)
            } ?: it.local?.let { local ->
                ZonedTime.fromLocal(LocalDateTime.parse(local), it.timezone ?: ZoneId.systemDefault().id)
            } ?: null
        }
    }

    fun getPlaces(tripId: String) {
        // Local-only; server data arrives via SyncManager (token never leaves it)
        places.value = OfflineStore.placesForTrip(tripId)
        isLoading.value = false
    }

    fun createPlace(tripId: String, request: CreatePlaceRequest, onSuccess: (PlaceWithPhotos) -> Unit) {
        Log.i(TAG_PLACE_VM, "createPlace: tripId=$tripId, name='${request.name}', lat=${request.latitude}, lon=${request.longitude}, visitDate=${request.visitDate}")
        // Always create locally first; SyncManager uploads it when sync is enabled
        val place = OfflineStore.addPlace(
            tripId,
            request.name,
            visitDate = toZonedTime(request.visitDate),
            address = request.address,
            latitude = request.latitude,
            longitude = request.longitude
        )
        isLoading.value = false
        errorMessage.value = null
        onSuccess(place)
        Log.d(TAG_PLACE_VM, "createPlace: local place added (id=${place.id}), queued for sync, onSuccess invoked")
    }

    /** Refresh currentPlace from the local OfflineStore only (no network). */
    fun refreshLocalPlace(placeId: String) {
        OfflineStore.findPlaceWithPhotos(placeId)?.let { currentPlace.value = it }
    }

    fun getPlace(placeId: String) {
        // Offline-only: the store is the source of truth; server data is merged by SyncManager
        viewModelScope.launch {
            isLoading.value = true
            Log.i(TAG_PLACE_VM, "getPlace START: placeId=$placeId")
            var offline = OfflineStore.findPlaceWithPhotos(placeId)
            if (offline == null) {
                // Fall back to the snapshot of the row that was clicked (survives sync ID remaps)
                val snapshot = AppSession.openedPlaceSnapshot.value
                if (snapshot != null && snapshot.id == placeId) {
                    Log.i(TAG_PLACE_VM, "getPlace: using clicked-row snapshot for $placeId ('${snapshot.name}')")
                    offline = snapshot
                }
            }
            if (offline != null) {
                Log.i(TAG_PLACE_VM, "getPlace: loaded offline lat=${offline.latitude} lon=${offline.longitude} photos=${offline.photos.size}")
                currentPlace.value = offline
            } else {
                errorMessage.value = "Place not found locally"
            }
            isLoading.value = false
            Log.i(TAG_PLACE_VM, "getPlace END: currentPlace lat=${currentPlace.value?.latitude} lon=${currentPlace.value?.longitude}")
        }
    }

    fun updatePlace(placeId: String, request: UpdatePlaceRequest, onSuccess: () -> Unit) {
        Log.i(TAG_PLACE_VM, "updatePlace START: placeId=$placeId, lat=${request.latitude}, lon=${request.longitude}")
        // Update local store immediately; SyncManager pushes it when sync is enabled
        OfflineStore.updatePlaceLocal(
            placeId = placeId,
            name = request.name,
            description = request.description,
            latitude = request.latitude,
            longitude = request.longitude,
            address = request.address,
            visitDate = request.visitDate?.let { ZonedTime.fromInstant(java.time.Instant.parse(it.utc ?: ""), it.timezone ?: java.time.ZoneId.systemDefault().id) }
        )
        Log.i(TAG_PLACE_VM, "updatePlace: local store updated, refreshing view")
        // Refresh local view immediately
        OfflineStore.findPlaceWithPhotos(placeId)?.let {
            Log.i(TAG_PLACE_VM, "updatePlace: currentPlace now lat=${it.latitude} lon=${it.longitude}")
            currentPlace.value = it
        }
        onSuccess()
    }

    fun deletePlace(placeId: String, onSuccess: () -> Unit) {
        Log.w(TAG_PLACE_VM, "deletePlace: placeId=$placeId")
        OfflineStore.deletePlaceLocal(placeId)
        onSuccess()
        Log.d(TAG_PLACE_VM, "deletePlace: removed locally, queued for sync")
    }

    fun addUnassignedPhoto(context: Context, uriString: String, latitude: Double?, longitude: Double?, takenAt: ZonedTime?, caption: String? = null) {
        Log.i(TAG_PLACE_VM, "addUnassignedPhoto: uri=$uriString, lat=$latitude, lon=$longitude")
        OfflineStore.addUnassignedPhoto(uriString, latitude, longitude, takenAt, caption)
    }

    fun movePlaceUp(tripId: String, placeId: String, onSuccess: () -> Unit) {
        movePlace(tripId, placeId, -1, onSuccess)
    }

    fun movePlaceDown(tripId: String, placeId: String, onSuccess: () -> Unit) {
        movePlace(tripId, placeId, 1, onSuccess)
    }

    private fun movePlace(tripId: String, placeId: String, direction: Int, onSuccess: () -> Unit) {
        // Local-only reorder; SyncManager pushes the new order when sync is enabled
        val allPlaces = OfflineStore.placesForTrip(tripId)
        val idx = allPlaces.indexOfFirst { it.id == placeId }
        if (idx == -1) {
            errorMessage.value = "Place not found in trip"
            return
        }
        val newIdx = idx + direction
        if (newIdx < 0 || newIdx >= allPlaces.size) {
            onSuccess()
            return
        }
        val newOrder = allPlaces.toMutableList()
        newOrder[idx] = allPlaces[newIdx]
        newOrder[newIdx] = allPlaces[idx]
        OfflineStore.reorderPlaces(tripId, newOrder.map { it.id })
        Log.i(TAG_PLACE_VM, "movePlace: $placeId direction=$direction done locally")
        onSuccess()
    }

    fun reorderPlaces(tripId: String, placeIds: List<String>, onSuccess: () -> Unit) {
        // Update local store immediately for instant UI feedback; SyncManager syncs it
        OfflineStore.reorderPlaces(tripId, placeIds)
        onSuccess()
    }
}
