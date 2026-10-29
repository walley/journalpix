package com.journalpix.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.data.OfflineStore
import com.journalpix.models.TimelineItem
import com.journalpix.models.TransportMode
import com.journalpix.models.TripTimeline
import com.journalpix.sync.SyncManager
import kotlinx.coroutines.launch
import java.time.LocalDate

class TimelineViewModel : ViewModel() {
    val isLoading = mutableStateOf(false)
    val isSyncing = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val timeline = mutableStateOf<TripTimeline?>(null)

    fun getTimeline(token: String, tripId: String) {
        timeline.value = OfflineStore.timelineFor(tripId.ifBlank { "demo-trip-1" })
        isLoading.value = false
        errorMessage.value = null
    }

    fun refreshOffline(tripId: String) {
        timeline.value = OfflineStore.timelineFor(tripId.ifBlank { "demo-trip-1" })
    }

    fun syncAndRefresh(token: String, tripId: String) {
        viewModelScope.launch {
            isSyncing.value = true
            try {
                SyncManager.performSyncSuspend(token)
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

    fun setTransport(placeId: String, mode: TransportMode?, tripId: String) {
        OfflineStore.setTransport(placeId, mode)
        refreshOffline(tripId)
    }

    fun reorderTimeline(token: String, tripId: String, newOrder: List<String>, onSuccess: () -> Unit) {
        getTimeline(token, tripId)
        onSuccess()
    }

    fun getTimelineItems(): List<TimelineItem> {
        return timeline.value?.timeline ?: emptyList()
    }
}
