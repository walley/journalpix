package com.journalpix.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.api.ApiService
import com.journalpix.data.OfflineStore
import com.journalpix.models.TimelineItem
import com.journalpix.models.TransportMode
import com.journalpix.models.TripTimeline
import com.journalpix.models.UpdateTripRequest
import kotlinx.coroutines.launch
import java.time.LocalDate

class TimelineViewModel : ViewModel() {
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val timeline = mutableStateOf<TripTimeline?>(null)

    fun getTimeline(token: String, tripId: String) {
        if (AuthViewModel.isDemoToken(token) || token.isBlank() || token == "Bearer token") {
            timeline.value = OfflineStore.timelineFor(tripId.ifBlank { "demo-trip-1" })
            isLoading.value = false
            errorMessage.value = null
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val response = ApiService.apiClient.getTimeline("Bearer $token", tripId)
                if (response.success) {
                    timeline.value = response.data
                } else {
                    timeline.value = OfflineStore.timelineFor(tripId)
                    errorMessage.value = null
                }
            } catch (_: Exception) {
                timeline.value = OfflineStore.timelineFor(tripId)
                errorMessage.value = null
            } finally {
                isLoading.value = false
            }
        }
    }

    fun refreshOffline(tripId: String) {
        timeline.value = OfflineStore.timelineFor(tripId.ifBlank { "demo-trip-1" })
    }

    fun updateTripDates(token: String = "", tripId: String, startDate: String?, endDate: String?) {
        OfflineStore.updateTripDates(tripId, startDate, endDate)
        if (!AuthViewModel.isDemoToken(token) && token.isNotBlank()) {
            viewModelScope.launch {
                try {
                    ApiService.apiClient.updateTrip(
                        "Bearer $token",
                        tripId,
                        UpdateTripRequest(startDate = startDate, endDate = endDate)
                    )
                } catch (_: Exception) {
                }
            }
        }
        refreshOffline(tripId)
    }

    fun addNewDay(token: String = "", tripId: String) {
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
        updateTripDates(token, tripId, newStartDate, nextDate.toString())
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
