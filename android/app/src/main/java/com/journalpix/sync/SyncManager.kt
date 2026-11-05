package com.journalpix.sync

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import java.lang.ref.WeakReference

object SyncManager {
    const val ACTION_SYNC = "com.journalpix.ACTION_SYNC"
    private const val TAG = "JPXSM"

    @Volatile
    private var appContextRef: WeakReference<Context>? = null

    /** Extract the server's error message from an HttpException response body,
     *  falling back to the status code if the body has no usable message. */
    private fun serverError(e: HttpException): String {
        val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val bodyMsg = raw?.let {
            runCatching { JSONObject(it).optString("error") }.getOrNull()
                ?: runCatching { JSONObject(it).optString("message") }.getOrNull()
        }
        return if (!bodyMsg.isNullOrBlank()) "$bodyMsg (HTTP ${e.code()})"
        else "HTTP ${e.code()}"
    }

    fun triggerSync(context: Context?) {
        context?.let { ctx ->
            appContextRef = WeakReference(ctx.applicationContext)
            // Global gate: when synchronization is OFF in Settings, do nothing — no network access
            if (!com.journalpix.ui.screen.isSyncEnabled(ctx)) {
                Log.i(TAG, "triggerSync: synchronization is OFF in Settings — skipping sync, no network access")
                return@let
            }
            Log.d(TAG, "Triggering synchronization intent: $ACTION_SYNC")
            val intent = Intent(ACTION_SYNC).apply {
                setPackage(ctx.packageName)
            }
            ctx.sendBroadcast(intent)

            performSync(ctx)
        }
    }

    private fun toast(message: String) {
        val ctx = appContextRef?.get() ?: return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun performSync(context: Context) {
        appContextRef = WeakReference(context.applicationContext)
        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val token = AppSession.currentUser.value?.token
            ?: sharedPrefs.getString("auth_token", null)
            ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            performSyncSuspend()
        }
    }

    /**
     * The auth token is read HERE and only here — nowhere else in the app
     * may touch it. All network access goes through this function.
     */
    suspend fun performSyncSuspend() {
        // Global gate: synchronization OFF in Settings means no sync, no network access at all
        val ctx = appContextRef?.get()
        if (ctx != null && !com.journalpix.ui.screen.isSyncEnabled(ctx)) {
            Log.i(TAG, "performSyncSuspend: synchronization is OFF in Settings — aborting, no network access")
            return
        }
        val token = AppSession.currentUser.value?.token
            ?: ctx?.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)?.getString("auth_token", null)
            ?: ""
        val bearerToken = if (token.isBlank()) "" else "Bearer $token"
        val errors = mutableListOf<String>()

        Log.d(TAG, "Sync started")

        // 1. Push pending local changes to backend
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
                            startTimezone = trip.startTimezone,
                            endDate = trip.endDate,
                            endTimezone = trip.endTimezone,
                            status = trip.status
                        )
                        var updateOk = false
                        try {
                            val updateResp =
                                ApiService.apiClient.updateTrip(bearerToken, trip.id, updateReq)
                            if (updateResp.success) {
                                updateOk = true
                                OfflineStore.clearPending(id)
                                Log.d(TAG, "Updated trip: $id")
                            } else {
                                Log.d(TAG, "Update trip returned success=false for $id: ${updateResp.error} — will try create")
                            }
                        } catch (e: HttpException) {
                            if (e.code() == 404) {
                                Log.d(TAG, "Update trip 404 for local id $id — trip not on server yet, will create")
                            } else {
                                Log.e(TAG, "Update trip HTTP ${e.code()} for $id: ${serverError(e)}")
                            }
                        }
                        if (!updateOk) {
                            val createReq = CreateTripRequest(
                                title = trip.title,
                                description = trip.description,
                                startDate = trip.startDate,
                                startTimezone = trip.startTimezone,
                                endDate = trip.endDate,
                                endTimezone = trip.endTimezone,
                                status = trip.status
                            )
                            val createResp = ApiService.apiClient.createTrip(bearerToken, createReq)
                            val createData = createResp.data
                            if (createResp.success && createData != null) {
                                OfflineStore.updateTripServerId(id, createData)
                                OfflineStore.clearPending(id)
                                Log.d(TAG, "Created and mapped trip: $id -> ${createData.id}")
                            } else {
                                val msg = createResp.error ?: "trip sync failed"
                                errors.add("Trip $id: $msg")
                                Log.e(TAG, "Trip create failed for $id: $msg")
                            }
                        }
                        continue
                    }

                    val placePair = OfflineStore.findPlace(id)
                    if (placePair != null) {
                        val (place, tripId) = placePair
                        if (tripId.startsWith("demo")) {
                            // Local-only demo trip fallback — nothing to push to the server
                            Log.i(TAG, "Skipping pending place $id: belongs to local demo trip '$tripId', clearing pending flag")
                            OfflineStore.clearPending(id)
                            continue
                        }
                        val updateReq = UpdatePlaceRequest(
                            name = place.name,
                            description = place.description,
                            latitude = place.latitude,
                            longitude = place.longitude,
                            address = place.address,
                            visitDate = place.visitDate?.toInput(),
                            orderIndex = place.orderIndex,
                            transportFromPrevious = place.transportFromPrevious
                        )
                        // Try update first; on 404 (local UUID not on server) fall through to create
                        var updateOk = false
                        var updateError: String? = null
                        try {
                            val updateResp =
                                ApiService.apiClient.updatePlace(bearerToken, place.id, updateReq)
                            if (updateResp.success) {
                                updateOk = true
                                OfflineStore.clearPending(id)
                                Log.d(TAG, "Updated place: $id")
                            } else {
                                updateError = updateResp.error
                                Log.d(TAG, "Update place returned success=false for $id: $updateError — will try create")
                            }
                        } catch (e: HttpException) {
                            if (e.code() == 404) {
                                Log.d(TAG, "Update place 404 for local id $id — place not on server yet, will create")
                            } else {
                                updateError = serverError(e)
                                Log.e(TAG, "Update place HTTP ${e.code()} for $id: $updateError")
                            }
                        }
                        if (!updateOk) {
                            val createReq = CreatePlaceRequest(
                                tripId = tripId,
                                name = place.name,
                                description = place.description,
                                latitude = place.latitude,
                                longitude = place.longitude,
                                address = place.address,
                                visitDate = place.visitDate?.toInput(),
                                orderIndex = place.orderIndex,
                                transportFromPrevious = place.transportFromPrevious
                            )
                            try {
                                val createResp =
                                    ApiService.apiClient.createPlace(bearerToken, tripId, createReq)
                                val createData = createResp.data
                                if (createResp.success && createData != null) {
                                    OfflineStore.updatePlaceServerId(id, createData)
                                    OfflineStore.clearPending(id)
                                    Log.d(TAG, "Created and mapped place: $id -> ${createData.id} (tripId=$tripId)")
                                } else {
                                    val msg = createResp.error ?: updateError ?: "place sync failed"
                                    errors.add("Place $id: $msg")
                                    Log.e(TAG, "Create place failed for $id (tripId=$tripId): $msg")
                                }
                            } catch (e: HttpException) {
                                val msg = serverError(e)
                                errors.add("Place $id: $msg")
                                Log.e(TAG, "Create place HTTP ${e.code()} for $id (tripId=$tripId): $msg", e)
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
                            var response =
                                ApiService.apiClient.uploadPhoto(bearerToken, placeId, body)
                            if (!response.success) {
                                // If the place doesn't exist on the server yet, create it,
                                // then retry the upload.
                                val placeRes = OfflineStore.findPlace(photo.placeId ?: "")
                                if (placeRes != null && response.error?.contains("not found") == true) {
                                    val (place, tripId) = placeRes
                                    val createReq = CreatePlaceRequest(
                                        tripId = tripId,
                                        name = place.name,
                                        description = place.description,
                                        latitude = place.latitude,
                                        longitude = place.longitude,
                                        address = place.address,
                                        visitDate = place.visitDate?.toInput(),
                                        orderIndex = place.orderIndex,
                                        transportFromPrevious = place.transportFromPrevious
                                    )
                                    val createResp =
                                        ApiService.apiClient.createPlace(bearerToken, tripId, createReq)
                                    if (createResp.success && createResp.data != null) {
                                        OfflineStore.updatePlaceServerId(photo.placeId ?: "", createResp.data)
                                        // Retry upload with the server place id
                                        response = ApiService.apiClient.uploadPhoto(
                                            bearerToken, createResp.data.id, body
                                        )
                                    }
                                }
                            }
                            if (response.success) {
                                OfflineStore.clearPending(id)
                                Log.d(TAG, "Synced photo: $id")
                            } else {
                                val msg = response.error ?: "photo upload failed"
                                errors.add("Photo: $msg")
                                Log.e(TAG, "Photo upload failed for $id: $msg")
                            }
                        } else {
                            val msg = "photo file missing: ${photo.filePath}"
                            errors.add("Photo: $msg")
                            Log.e(TAG, "Photo file missing for $id: ${photo.filePath}")
                            // keep pending so a later copy can retry
                        }
                        continue
                    }

                    OfflineStore.clearPending(id)
                } catch (e: Exception) {
                    val msg = if (e is HttpException) serverError(e) else "${e.message ?: e.javaClass.simpleName}"
                    Log.e(TAG, "Failed to sync item $id: $msg", e)
                    errors.add("Item $id: $msg")
                }
            }
        }

        // 2. Pull remote items from backend
        try {
            Log.d(TAG, "Pulling remote items from backend...")
            val tripsResponse = ApiService.apiClient.getTrips(bearerToken)
            val tripsData = tripsResponse.data
            if (tripsResponse.success && tripsData != null) {
                Log.d(TAG, "Pull trips OK: ${tripsData.size} trips")
                for (trip in tripsData) {
                    try {
                        val timelineResponse = ApiService.apiClient.getTimeline(bearerToken, trip.id)
                        val timelineData = timelineResponse.data
                        if (timelineResponse.success && timelineData != null) {
                            OfflineStore.mergeBackendTimeline(timelineData)
                            Log.d(TAG, "Pulled and merged timeline for trip: ${trip.id} (${timelineData.timeline.size} days)")
                        } else {
                            val msg = timelineResponse.error ?: "timeline pull failed (success=false)"
                            Log.e(TAG, "Timeline pull failed for trip ${trip.id}: $msg")
                            errors.add("Timeline ${trip.id}: $msg")
                        }
                    } catch (e: Exception) {
                        val msg = if (e is HttpException) serverError(e) else e.message ?: e.javaClass.simpleName
                        Log.e(TAG, "Timeline pull exception for trip ${trip.id}: $msg", e)
                        errors.add("Timeline ${trip.id}: $msg")
                    }
                }
            } else if (!tripsResponse.success) {
                val msg = tripsResponse.error ?: "failed to pull trips"
                errors.add(msg)
                Log.e(TAG, "Pull trips failed: $msg")
            }
        } catch (e: Exception) {
            val msg = if (e is HttpException) serverError(e) else e.message ?: "pull failed"
            Log.e(TAG, "Failed to pull remote data from backend: $msg", e)
            errors.add(msg)
        }

        Log.d(TAG, "Sync complete, errors=${errors.size}")
        errors.forEachIndexed { i, err -> Log.e(TAG, "Sync error[$i]: $err") }
        if (errors.isNotEmpty()) {
            val summary = errors.distinct().take(3).joinToString(" · ")
            Log.e(TAG, "Showing sync failure toast: $summary")
            toast("Sync failed: $summary")
        }
    }
}
