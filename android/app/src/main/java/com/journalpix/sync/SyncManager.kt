package com.journalpix.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.journalpix.api.ApiService
import com.journalpix.data.OfflineStore
import com.journalpix.models.CreatePlaceRequest
import com.journalpix.models.CreateTripRequest
import com.journalpix.models.UpdatePlaceRequest
import com.journalpix.models.UpdateTripRequest
import com.journalpix.viewmodel.AppSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

object SyncManager {
    const val ACTION_SYNC = "com.journalpix.ACTION_SYNC"
    private const val TAG = "SyncManager"

    fun triggerSync(context: Context?) {
        context?.let { ctx ->
            Log.d(TAG, "Triggering synchronization intent: $ACTION_SYNC")
            val intent = Intent(ACTION_SYNC).apply {
                setPackage(ctx.packageName)
            }
            ctx.sendBroadcast(intent)

            performSync(ctx)
        }
    }

    private fun performSync(context: Context) {
        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val token = AppSession.currentUser.value?.token ?: sharedPrefs.getString("auth_token", null)

        if (token.isNullOrBlank() || token.startsWith("demo")) {
            Log.d(TAG, "Skipping backend sync: No valid auth token or is demo token")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            performSyncSuspend(token)
        }
    }

    suspend fun performSyncSuspend(token: String) {
        if (token.isBlank() || token.startsWith("demo")) return
        val bearerToken = "Bearer $token"

        // 1. Push pending local changes to backend (try update first, fallback to create and map server IDs)
        val pendingIds = OfflineStore.pendingSyncIds()
        if (pendingIds.isNotEmpty()) {
            Log.d(TAG, "Syncing ${pendingIds.size} pending local items to backend...")
            for (id in pendingIds) {
                try {
                    val trip = OfflineStore.getTrip(id)
                    if (trip != null) {
                        val updateReq = UpdateTripRequest(
                            title = trip.title,
                            description = trip.description,
                            startDate = trip.startDate,
                            endDate = trip.endDate,
                            status = trip.status
                        )
                        val updateResp =
                            ApiService.apiClient.updateTrip(bearerToken, trip.id, updateReq)
                        if (updateResp.success) {
                            OfflineStore.clearPending(id)
                            Log.d(TAG, "Updated trip: $id")
                        } else {
                            val createReq = CreateTripRequest(
                                title = trip.title,
                                description = trip.description,
                                startDate = trip.startDate,
                                endDate = trip.endDate,
                                status = trip.status
                            )
                            val createResp = ApiService.apiClient.createTrip(bearerToken, createReq)
                            val createData = createResp.data
                            if (createResp.success && createData != null) {
                                OfflineStore.updateTripServerId(id, createData)
                                OfflineStore.clearPending(id)
                                Log.d(TAG, "Created and mapped trip: $id -> ${createData.id}")
                            }
                        }
                        continue
                    }

                    val placePair = OfflineStore.findPlace(id)
                    if (placePair != null) {
                        val (place, tripId) = placePair
                        val updateReq = UpdatePlaceRequest(
                            name = place.name,
                            description = place.description,
                            latitude = place.latitude,
                            longitude = place.longitude,
                            address = place.address,
                            visitDate = place.visitDate,
                            orderIndex = place.orderIndex
                        )
                        val updateResp =
                            ApiService.apiClient.updatePlace(bearerToken, place.id, updateReq)
                        if (updateResp.success) {
                            OfflineStore.clearPending(id)
                            Log.d(TAG, "Updated place: $id")
                        } else {
                            val createReq = CreatePlaceRequest(
                                tripId = tripId,
                                name = place.name,
                                description = place.description,
                                latitude = place.latitude,
                                longitude = place.longitude,
                                address = place.address,
                                visitDate = place.visitDate,
                                orderIndex = place.orderIndex
                            )
                            val createResp =
                                ApiService.apiClient.createPlace(bearerToken, tripId, createReq)
                            val createData = createResp.data
                            if (createResp.success && createData != null) {
                                OfflineStore.updatePlaceServerId(id, createData)
                                OfflineStore.clearPending(id)
                                Log.d(TAG, "Created and mapped place: $id -> ${createData.id}")
                            }
                        }
                        continue
                    }

                    val photoPair = OfflineStore.findPhotoWithPlace(id)
                    if (photoPair != null) {
                        val (photo, placeId) = photoPair
                        val file = File(photo.filePath)
                        if (file.exists()) {
                            val requestFile = file.asRequestBody("image/jpeg".toMediaType())
                            val body =
                                MultipartBody.Part.createFormData("photo", file.name, requestFile)
                            val response =
                                ApiService.apiClient.uploadPhoto(bearerToken, placeId, body)
                            if (response.success) {
                                OfflineStore.clearPending(id)
                                Log.d(TAG, "Synced photo: $id")
                            }
                        } else {
                            OfflineStore.clearPending(id)
                        }
                        continue
                    }

                    OfflineStore.clearPending(id)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync item $id", e)
                }
            }
        }

        // 2. Pull remote items from backend (load things that are on BE and not in app / update app)
        try {
            Log.d(TAG, "Pulling remote items from backend...")
            val tripsResponse = ApiService.apiClient.getTrips(bearerToken)
            val tripsData = tripsResponse.data
            if (tripsResponse.success && tripsData != null) {
                for (trip in tripsData) {
                    val timelineResponse = ApiService.apiClient.getTimeline(bearerToken, trip.id)
                    val timelineData = timelineResponse.data
                    if (timelineResponse.success && timelineData != null) {
                        OfflineStore.mergeBackendTimeline(timelineData)
                        Log.d(TAG, "Pulled and merged timeline for trip: ${trip.id}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pull remote data from backend", e)
        }
    }
}
