package com.journalpix.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.api.ApiService
import com.journalpix.models.TimelineItem
import com.journalpix.models.TripTimeline
import kotlinx.coroutines.launch

class TimelineViewModel : ViewModel() {
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val timeline = mutableStateOf<TripTimeline?>(null)
    
    fun getTimeline(token: String, tripId: String) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            
            try {
                val response = ApiService.apiClient.getTimeline("Bearer $token", tripId)
                
                if (response.success) {
                    timeline.value = response.data
                } else {
                    errorMessage.value = response.error ?: "Failed to load timeline"
                }
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }
    
    fun reorderTimeline(token: String, tripId: String, newOrder: List<String>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            
            try {
                // API call to reorder timeline
                // For now, just refresh the timeline
                getTimeline(token, tripId)
                onSuccess()
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }
    
    fun getTimelineItems(): List<TimelineItem> {
        return timeline.value?.timeline ?: emptyList()
    }
}
