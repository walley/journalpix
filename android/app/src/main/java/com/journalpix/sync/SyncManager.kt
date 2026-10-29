package com.journalpix.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.journalpix.api.ApiService
import com.journalpix.data.OfflineStore
import com.journalpix.models.CreatePlaceRequest
import com.journalpix.models.CreateTripRequest
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
    val pendingIds = OfflineStore.pendingSyncIds()
    if (pendingIds.isEmpty()) return

    Log.d(TAG, "Syncing ${pendingIds.size} pending items to backend...")
    val bearerToken = "Bearer $token"

    for (id in pendingIds) {
      try {
        // 1. Check if it's a Trip
        val trip = OfflineStore.getTrip(id)
        if (trip != null) {
          val request = CreateTripRequest(
            title = trip.title,
            description = trip.description,
            startDate = trip.startDate,
            endDate = trip.endDate,
            status = trip.status
          )
          val response = ApiService.apiClient.createTrip(bearerToken, request)
          if (response.success) {
            OfflineStore.clearPending(id)
            Log.d(TAG, "Synced trip: $id")
          }
          continue
        }

        // 2. Check if it's a Place
        val placePair = OfflineStore.findPlace(id)
        if (placePair != null) {
          val (place, tripId) = placePair
          val request = CreatePlaceRequest(
            tripId = tripId,
            name = place.name,
            description = place.description,
            latitude = place.latitude,
            longitude = place.longitude,
            address = place.address,
            visitDate = place.visitDate,
            orderIndex = place.orderIndex
          )
          val response = ApiService.apiClient.createPlace(bearerToken, tripId, request)
          if (response.success) {
            OfflineStore.clearPending(id)
            Log.d(TAG, "Synced place: $id")
          }
          continue
        }

        // 3. Check if it's a Photo
        val photoPair = OfflineStore.findPhotoWithPlace(id)
        if (photoPair != null) {
          val (photo, placeId) = photoPair
          val file = File(photo.filePath)
          if (file.exists()) {
            val requestFile = file.asRequestBody("image/jpeg".toMediaType())
            val body = MultipartBody.Part.createFormData("photo", file.name, requestFile)
            val response = ApiService.apiClient.uploadPhoto(bearerToken, placeId, body)
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
}
