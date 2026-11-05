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
    val places = mutableStateOf<List<PlaceResponse>>(emptyList())
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

    fun getPlaces(token: String, tripId: String) {
        if (token.isBlank()) {
            isLoading.value = false
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiService.apiClient.getPlaces("Bearer $token", tripId)
                }
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
        Log.i(TAG_PLACE_VM, "createPlace: tripId=$tripId, name='${request.name}', lat=${request.latitude}, lon=${request.longitude}, visitDate=${request.visitDate}")
        if (token.isBlank() || token == "Bearer token") {
            Log.w(TAG_PLACE_VM, "createPlace: using offline store (no valid token)")
            OfflineStore.addPlace(tripId, request.name, visitDate = toZonedTime(request.visitDate))
            isLoading.value = false
            errorMessage.value = null
            onSuccess()
            Log.d(TAG_PLACE_VM, "createPlace: offline place added, onSuccess invoked")
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                Log.d(TAG_PLACE_VM, "createPlace: calling API for tripId=$tripId")
                val response = withContext(Dispatchers.IO) {
                    ApiService.apiClient.createPlace("Bearer $token", tripId, request)
                }
                if (response.success && response.data != null) {
                    val placeResp = response.data
                    Log.i(TAG_PLACE_VM, "createPlace: API success, placeId=${placeResp.id}, name='${placeResp.name}', lat=${placeResp.latitude}, lon=${placeResp.longitude}")
                    // Store under the SERVER id so later lookups/clicks resolve correctly
                    OfflineStore.addServerPlace(tripId, placeResp)
                    onSuccess()
                    Log.d(TAG_PLACE_VM, "createPlace: offline store updated, onSuccess invoked")
                } else {
                    Log.e(TAG_PLACE_VM, "createPlace: API failed: ${response.error ?: "unknown error"}")
                    errorMessage.value = response.error ?: "Failed to create place"
                    OfflineStore.addPlace(tripId, request.name, visitDate = toZonedTime(request.visitDate))
                    onSuccess()
                    Log.w(TAG_PLACE_VM, "createPlace: API failed but falling back to offline store, onSuccess invoked")
                }
            } catch (e: Exception) {
                Log.e(TAG_PLACE_VM, "createPlace: network exception: ${e.message}", e)
                errorMessage.value = "Network error: ${e.message}"
                OfflineStore.addPlace(tripId, request.name, visitDate = toZonedTime(request.visitDate))
                onSuccess()
                Log.w(TAG_PLACE_VM, "createPlace: exception caught, falling back to offline store, onSuccess invoked")
            } finally {
                isLoading.value = false
                Log.d(TAG_PLACE_VM, "createPlace: finished, isLoading=false")
            }
        }
    }

    /** Refresh currentPlace from the local OfflineStore only (no network). */
    fun refreshLocalPlace(placeId: String) {
        OfflineStore.findPlaceWithPhotos(placeId)?.let { currentPlace.value = it }
    }

    fun getPlace(token: String, placeId: String) {
        viewModelScope.launch {
            isLoading.value = true
            Log.i(TAG_PLACE_VM, "getPlace START: placeId=$placeId")
            try {
                // Load from offline store first for instant display
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
                }
                val response = withContext(Dispatchers.IO) {
                    ApiService.apiClient.getPlace("Bearer $token", placeId)
                }
                if (response.success && response.data != null) {
                    val serverPlace = response.data!!
                    Log.i(TAG_PLACE_VM, "getPlace: server lat=${serverPlace.latitude} lon=${serverPlace.longitude} photos=${serverPlace.photos.size}")
                    // Always prefer local lat/lon if they were set locally (offline update)
                    // and merge photos from offline if server has none
                    val merged = serverPlace.copy(
                        latitude = offline?.latitude ?: serverPlace.latitude,
                        longitude = offline?.longitude ?: serverPlace.longitude,
                        address = offline?.address ?: serverPlace.address,
                        photos = if (serverPlace.photos.isEmpty() && offline != null) offline.photos else serverPlace.photos
                    )
                    currentPlace.value = merged
                    Log.i(TAG_PLACE_VM, "getPlace: merged lat=${merged.latitude} lon=${merged.longitude} photos=${merged.photos.size}")
                } else if (offline == null) {
                    errorMessage.value = response.error ?: "Failed to load place"
                }
            } catch (e: Exception) {
                Log.e(TAG_PLACE_VM, "getPlace: exception ${e.message}", e)
                var offline = OfflineStore.findPlaceWithPhotos(placeId)
                if (offline == null) {
                    val snapshot = AppSession.openedPlaceSnapshot.value
                    if (snapshot != null && snapshot.id == placeId) offline = snapshot
                }
                if (offline != null) {
                    // Local-only place (not on server yet) — 404 is expected, not an error
                    if (e is retrofit2.HttpException && e.code() == 404) {
                        Log.i(TAG_PLACE_VM, "getPlace: place $placeId is local-only (HTTP 404), using offline data")
                    }
                    currentPlace.value = offline
                } else {
                    errorMessage.value = "Network error: ${e.message}"
                }
            } finally {
                isLoading.value = false
                Log.i(TAG_PLACE_VM, "getPlace END: currentPlace lat=${currentPlace.value?.latitude} lon=${currentPlace.value?.longitude}")
            }
        }
    }

    fun updatePlace(token: String, placeId: String, request: UpdatePlaceRequest, onSuccess: () -> Unit) {
        Log.i(TAG_PLACE_VM, "updatePlace START: placeId=$placeId, lat=${request.latitude}, lon=${request.longitude}")
        // Update local store immediately
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
        // Sync with backend in background
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = ApiService.apiClient.updatePlace("Bearer $token", placeId, request)
                if (response.success) {
                    Log.i(TAG_PLACE_VM, "updatePlace: backend sync success for placeId=$placeId")
                } else {
                    Log.e(TAG_PLACE_VM, "updatePlace: backend sync failed for placeId=$placeId: ${response.error ?: "unknown error"}")
                }
            } catch (e: Exception) {
                Log.e(TAG_PLACE_VM, "updatePlace: backend sync exception for placeId=$placeId: ${e.message}", e)
            }
        }
    }

    fun deletePlace(token: String, placeId: String, onSuccess: () -> Unit) {
        Log.w(TAG_PLACE_VM, "deletePlace: placeId=$placeId")
        viewModelScope.launch {
            isLoading.value = true
            try {
                Log.d(TAG_PLACE_VM, "deletePlace: calling API for placeId=$placeId")
                val response = withContext(Dispatchers.IO) {
                    ApiService.apiClient.deletePlace("Bearer $token", placeId)
                }
                if (response.success) {
                    Log.i(TAG_PLACE_VM, "deletePlace: API success for placeId=$placeId")
                    onSuccess()
                } else {
                    Log.e(TAG_PLACE_VM, "deletePlace: API failed for placeId=$placeId: ${response.error ?: "unknown error"}")
                    errorMessage.value = response.error ?: "Failed to delete place"
                }
            } catch (e: Exception) {
                Log.e(TAG_PLACE_VM, "deletePlace: network exception for placeId=$placeId: ${e.message}", e)
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
                Log.d(TAG_PLACE_VM, "deletePlace: finished for placeId=$placeId")
            }
        }
    }

    fun addUnassignedPhoto(context: Context, uriString: String, latitude: Double?, longitude: Double?, takenAt: ZonedTime?, caption: String? = null) {
        Log.i(TAG_PLACE_VM, "addUnassignedPhoto: uri=$uriString, lat=$latitude, lon=$longitude")
        OfflineStore.addUnassignedPhoto(uriString, latitude, longitude, takenAt, caption)
    }

    fun movePlaceUp(token: String, tripId: String, placeId: String, onSuccess: () -> Unit) {
        movePlace(token, tripId, placeId, -1, onSuccess)
    }

    fun movePlaceDown(token: String, tripId: String, placeId: String, onSuccess: () -> Unit) {
        movePlace(token, tripId, placeId, 1, onSuccess)
    }

    private fun movePlace(token: String, tripId: String, placeId: String, direction: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                // Get all places for the trip to find current index and adjacent
                val placesResp = withContext(Dispatchers.IO) {
                    ApiService.apiClient.getPlaces("Bearer $token", tripId)
                }
                if (!placesResp.success || placesResp.data == null) {
                    errorMessage.value = placesResp.error ?: "Failed to load places"
                    return@launch
                }
                val allPlaces = placesResp.data!!
                val idx = allPlaces.indexOfFirst { it.id == placeId }
                if (idx == -1) {
                    errorMessage.value = "Place not found in trip"
                    return@launch
                }
                val newIdx = idx + direction
                if (newIdx < 0 || newIdx >= allPlaces.size) {
                    // Already at edge, nothing to do
                    onSuccess()
                    return@launch
                }

                // Swap order_index between current and adjacent
                val currentPlace = allPlaces[idx]
                val adjacentPlace = allPlaces[newIdx]

                // Build new order: swap positions
                val newOrder = allPlaces.toMutableList()
                newOrder[idx] = adjacentPlace
                newOrder[newIdx] = currentPlace

                val reorderResp = withContext(Dispatchers.IO) {
                    ApiService.apiClient.reorderPlaces(
                        "Bearer $token",
                        tripId,
                        com.journalpix.models.ReorderPlacesRequest(newOrder.map { it.id })
                    )
                }

                if (reorderResp.success) {
                    Log.i(TAG_PLACE_VM, "Move place $placeId direction=$direction: success")
                    onSuccess()
                } else {
                    Log.e(TAG_PLACE_VM, "Move place $placeId direction=$direction: failed ${reorderResp.error}")
                    errorMessage.value = reorderResp.error ?: "Failed to move place"
                }
            } catch (e: Exception) {
                Log.e(TAG_PLACE_VM, "Move place $placeId direction=$direction: exception ${e.message}", e)
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun reorderPlaces(token: String, tripId: String, placeIds: List<String>, onSuccess: () -> Unit) {
        // Update local store immediately for instant UI feedback
        OfflineStore.reorderPlaces(tripId, placeIds)
        onSuccess()
        // Sync with backend in background
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resp = ApiService.apiClient.reorderPlaces(
                    "Bearer $token",
                    tripId,
                    com.journalpix.models.ReorderPlacesRequest(placeIds)
                )
                if (resp.success) {
                    Log.i(TAG_PLACE_VM, "reorderPlaces: backend sync success with ${placeIds.size} places")
                } else {
                    Log.e(TAG_PLACE_VM, "reorderPlaces: backend sync failed ${resp.error}")
                }
            } catch (e: Exception) {
                Log.e(TAG_PLACE_VM, "reorderPlaces: backend sync exception ${e.message}", e)
            }
        }
    }
}
