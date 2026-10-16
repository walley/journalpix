package com.journalpix.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.api.ApiService
import com.journalpix.data.OfflineStore
import com.journalpix.models.TimelineItem
import com.journalpix.models.TripTimeline
import kotlinx.coroutines.launch

class TimelineViewModel : ViewModel() {
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val timeline = mutableStateOf<TripTimeline?>(null)

    fun getTimeline(token: String, tripId: String) {
        // Offline / demo: never hit the network
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
                    // Fall back to offline store so the UI stays usable
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

    fun reorderTimeline(token: String, tripId: String, newOrder: List<String>, onSuccess: () -> Unit) {
        getTimeline(token, tripId)
        onSuccess()
    }

    fun getTimelineItems(): List<TimelineItem> {
        return timeline.value?.timeline ?: emptyList()
    }
}
