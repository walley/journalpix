package com.journalpix.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "places",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Place(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val tripId: String,
    val name: String,
    val description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    /** 1-based trip day index (not a calendar date). */
    val day: Int = 1,
    /** RFC3339 UTC instant of the visit; zone kept separately. */
    val visitDate: String? = null,
    /** IANA zone where the visit happened. */
    val visitTimezone: String? = null,
    val orderIndex: Int = 0,
    /** How we got here from the previous place (TransportMode name). */
    val transportFromPrevious: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// API Response models
data class PlaceResponse(
    val id: String,
    val tripId: String,
    val name: String,
    val description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    /** 1-based trip day index. */
    val day: Int = 1,
    val visitDate: ZonedTime? = null,
    val orderIndex: Int,
    val transportFromPrevious: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val photosCount: Int? = null
)

data class PlaceWithPhotos(
    val id: String,
    val tripId: String,
    val name: String,
    val description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    /** 1-based trip day index. */
    val day: Int = 1,
    val visitDate: ZonedTime? = null,
    val orderIndex: Int,
    val transportFromPrevious: String? = null,
    val createdAt: String,
    val photos: List<PhotoResponse>
) {
    /** Chronological sort key: earliest photo, else the visit instant. */
    fun sortKey(): String {
        val firstPhoto = photos.mapNotNull { it.takenAt?.sortKey }.minOrNull()
        return firstPhoto ?: visitDate?.sortKey ?: createdAt
    }

    /** Wall-clock label of the visit, e.g. "Jul 1, 2026 10:30 (Tokyo)". */
    fun visitLabel(): String? = visitDate?.dateTimeLabel()
}

data class CreatePlaceRequest(
    val tripId: String,
    val name: String,
    val description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    /** 1-based trip day; defaults to 1 on the server. */
    val day: Int? = null,
    val visitDate: ZonedTimeInput? = null,
    val orderIndex: Int? = null,
    val transportFromPrevious: String? = null
)

data class UpdatePlaceRequest(
    val name: String? = null,
    val description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val day: Int? = null,
    val visitDate: ZonedTimeInput? = null,
    val orderIndex: Int? = null,
    val transportFromPrevious: String? = null
)

data class PlaceCoordinates(
    val latitude: Double,
    val longitude: Double
)
