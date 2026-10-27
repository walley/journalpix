package com.journalpix.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.UUID

enum class TripStatus {
    @SerializedName("planned") PLANNED,
    @SerializedName("in_progress") IN_PROGRESS,
    @SerializedName("completed") COMPLETED,
    @SerializedName("cancelled") CANCELLED
}

@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Trip(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val title: String,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: TripStatus = TripStatus.PLANNED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// API Response models
data class TripResponse(
    val id: String,
    val userId: String,
    val title: String,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: TripStatus,
    val createdAt: String,
    val updatedAt: String,
    val placesCount: Int? = null,
    val photosCount: Int? = null
)

data class CreateTripRequest(
    val title: String,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: TripStatus? = null
)

data class UpdateTripRequest(
    val title: String? = null,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: TripStatus? = null
)

data class TripWithPlaces(
    val trip: TripResponse,
    val places: List<PlaceResponse>
)

data class TripTimeline(
    val trip: TripResponse,
    val timeline: List<TimelineItem>
)

data class TimelineItem(
    val date: String,
    val places: List<PlaceWithPhotos>
)
