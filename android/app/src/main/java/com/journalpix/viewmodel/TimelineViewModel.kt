package com.journalpix.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.api.ApiService
import com.journalpix.data.OfflineStore
import com.journalpix.models.TimelineItem
import com.journalpix.models.TransportMode
import com.journalpix.models.TripTimeline
import com.journalpix.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class TimelineViewModel : ViewModel() {
    val isLoading = mutableStateOf(false)
    val isSyncing = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val timeline = mutableStateOf<TripTimeline?>(null)

    fun getTimeline(tripId: String) {
        val validId = AppSession.validateTripId(tripId)
        timeline.value = validId?.let { OfflineStore.timelineFor(it) }
        isLoading.value = false
        errorMessage.value = AppSession.tripIdError.value
    }

    fun refreshOffline(tripId: String) {
        val validId = AppSession.validateTripId(tripId)
        timeline.value = validId?.let { OfflineStore.timelineFor(it) }
        if (validId == null) errorMessage.value = AppSession.tripIdError.value
    }

    fun syncAndRefresh(tripId: String) {
        viewModelScope.launch {
            isSyncing.value = true
            try {
                // Network I/O must not run on the main thread (viewModelScope = Main) — causes ANR
                // Token lives only inside SyncManager; it no-ops when logged out or sync is off
                withContext(Dispatchers.IO) {
                    SyncManager.performSyncSuspend()
                }
            } catch (_: Exception) {
            } finally {
                isSyncing.value = false
                refreshOffline(tripId)
            }
        }
    }

    fun updateTripDates(tripId: String, startDate: String?, endDate: String?) {
        OfflineStore.updateTripDates(tripId, startDate, endDate)
        refreshOffline(tripId)
    }

    fun addNewDay(tripId: String) {
        val currentTrip = timeline.value?.trip
        val lastDateStr = currentTrip?.endDate ?: currentTrip?.startDate ?: LocalDate.now()
          .toString()
        val nextDate = try {
            LocalDate.parse(lastDateStr)
              .plusDays(1)
        } catch (_: Exception) {
            LocalDate.now()
        }
        val newStartDate = currentTrip?.startDate ?: nextDate.toString()
        updateTripDates(tripId, newStartDate, nextDate.toString())
    }

    fun updateTripTitle(tripId: String, title: String) {
        OfflineStore.updateTripTitle(tripId, title)
        refreshOffline(tripId)
    }

    fun updateTripDescription(tripId: String, description: String?) {
        OfflineStore.updateTripDescription(tripId, description)
        refreshOffline(tripId)
    }

    fun setTransport(placeId: String, mode: TransportMode?, tripId: String) {
        OfflineStore.setTransport(placeId, mode)
        refreshOffline(tripId)
    }

    fun reorderTimeline(tripId: String, newOrder: List<String>, onSuccess: () -> Unit) {
        OfflineStore.reorderPlaces(tripId, newOrder)
        getTimeline(tripId)
        onSuccess()
    }

    fun reorderPlaces(tripId: String, placeIds: List<String>, onSuccess: () -> Unit) {
        // Local-first; server sync happens via SyncManager (token never leaves it)
        OfflineStore.reorderPlaces(tripId, placeIds)
        refreshOffline(tripId)
        onSuccess()
    }

    fun getTimelineItems(): List<TimelineItem> {
        return timeline.value?.timeline ?: emptyList()
    }
}
