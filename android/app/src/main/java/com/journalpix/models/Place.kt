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
    val visitDate: String? = null,
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
    val visitDate: String? = null,
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
    val visitDate: String? = null,
    val orderIndex: Int,
    val transportFromPrevious: String? = null,
    val createdAt: String,
    val photos: List<PhotoResponse>
)

data class CreatePlaceRequest(
    val tripId: String,
    val name: String,
    val description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val visitDate: String? = null,
    val orderIndex: Int? = null,
    val transportFromPrevious: String? = null
)

data class UpdatePlaceRequest(
    val name: String? = null,
    val description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val visitDate: String? = null,
    val orderIndex: Int? = null,
    val transportFromPrevious: String? = null
)

data class PlaceCoordinates(
    val latitude: Double,
    val longitude: Double
)
