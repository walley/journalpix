package com.journalpix.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = Place::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Photo(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val placeId: String? = null,
    val userId: String,
    val filePath: String,
    val thumbnailPath: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val takenAt: String? = null,
    val caption: String? = null,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

// API Response models
data class PhotoResponse(
    val id: String,
    val placeId: String? = null,
    val userId: String,
    val filePath: String,
    val thumbnailPath: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val takenAt: String? = null,
    val caption: String? = null,
    val orderIndex: Int,
    val createdAt: String,
    val place: PlaceResponse? = null
)

data class CreatePhotoRequest(
    val placeId: String? = null,
    val filePath: String,
    val thumbnailPath: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val takenAt: String? = null,
    val caption: String? = null,
    val orderIndex: Int? = null
)

data class UpdatePhotoRequest(
    val placeId: String? = null,
    val caption: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val takenAt: String? = null,
    val orderIndex: Int? = null
)

data class PhotoMetadata(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val takenAt: String? = null
)

data class UploadResult(
    val photoId: String,
    val filePath: String,
    val thumbnailPath: String,
    val metadata: PhotoMetadata
)
