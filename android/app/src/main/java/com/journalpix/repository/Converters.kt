package com.journalpix.repository

import androidx.room.TypeConverter
import com.journalpix.models.TripStatus

class Converters {
    @TypeConverter
    fun fromTripStatus(status: TripStatus): String {
        return when (status) {
            TripStatus.PLANNED -> "planned"
            TripStatus.IN_PROGRESS -> "in_progress"
            TripStatus.COMPLETED -> "completed"
            TripStatus.CANCELLED -> "cancelled"
        }
    }
    
    @TypeConverter
    fun toTripStatus(status: String): TripStatus {
        return when (status) {
            "in_progress" -> TripStatus.IN_PROGRESS
            "completed" -> TripStatus.COMPLETED
            "cancelled" -> TripStatus.CANCELLED
            else -> TripStatus.PLANNED
        }
    }
}
