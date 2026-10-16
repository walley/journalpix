package com.journalpix.data

import com.journalpix.models.PhotoResponse
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.models.TimelineItem
import com.journalpix.models.TripResponse
import com.journalpix.models.TripStatus
import com.journalpix.models.TripTimeline
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory offline store for demo mode (no backend).
 * Photos keep local content:// or file:// URIs so Coil can display them.
 */
object OfflineStore {

    private val photosByPlace = ConcurrentHashMap<String, MutableList<PhotoResponse>>()
    private val placesByTrip = ConcurrentHashMap<String, MutableList<PlaceWithPhotos>>()
    /** photoId -> reverse-geocoded address */
    val addresses = ConcurrentHashMap<String, String>()

    fun ensureDemoTrip(tripId: String): TripResponse {
        placesByTrip.putIfAbsent(
            tripId,
            mutableListOf(
                PlaceWithPhotos(
                    id = "demo-place-1",
                    tripId = tripId,
                    name = "Demo Place",
                    description = "Add photos offline — GPS is read from EXIF",
                    latitude = null,
                    longitude = null,
                    address = null,
                    visitDate = java.time.LocalDate.now().toString(),
                    orderIndex = 0,
                    createdAt = "2024-01-01T00:00:00Z",
                    photos = emptyList()
                )
            )
        )
        photosByPlace.putIfAbsent("demo-place-1", mutableListOf())
        return TripResponse(
            id = tripId.ifBlank { "demo-trip-1" },
            userId = "00000000-0000-0000-0000-000000000001",
            title = if (tripId.startsWith("demo")) "Demo Alps Weekend" else "Trip $tripId",
            description = "Offline timeline — photos stay on device",
            startDate = "2024-06-01",
            endDate = "2024-06-03",
            status = TripStatus.PLANNED,
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            placesCount = placesByTrip[tripId]?.size,
            photosCount = photosByPlace.values.sumOf { it.size }
        )
    }

    fun timelineFor(tripId: String): TripTimeline {
        val trip = ensureDemoTrip(tripId)
        val places = placesByTrip[tripId].orEmpty().map { place ->
            val photos = photosByPlace[place.id].orEmpty().toList()
            place.copy(photos = photos)
        }
        // Group by visitDate (or today)
        val grouped = places.groupBy { it.visitDate ?: java.time.LocalDate.now().toString() }
        val items = grouped.entries.sortedByDescending { it.key }.map { (date, list) ->
            TimelineItem(date = date, places = list)
        }
        return TripTimeline(trip = trip, timeline = items)
    }

    fun addPlace(tripId: String, name: String): PlaceWithPhotos {
        ensureDemoTrip(tripId)
        val place = PlaceWithPhotos(
            id = "place-${UUID.randomUUID()}",
            tripId = tripId,
            name = name,
            description = null,
            latitude = null,
            longitude = null,
            address = null,
            visitDate = java.time.LocalDate.now().toString(),
            orderIndex = placesByTrip[tripId]?.size ?: 0,
            createdAt = java.time.Instant.now().toString(),
            photos = emptyList()
        )
        placesByTrip.getOrPut(tripId) { mutableListOf() }.add(place)
        photosByPlace.putIfAbsent(place.id, mutableListOf())
        return place
    }

    fun addPhoto(
        placeId: String,
        uriString: String,
        latitude: Double?,
        longitude: Double?,
        takenAt: String?,
        caption: String? = null
    ): PhotoResponse {
        val photo = PhotoResponse(
            id = "photo-${UUID.randomUUID()}",
            placeId = placeId,
            userId = "00000000-0000-0000-0000-000000000001",
            filePath = uriString,
            thumbnailPath = uriString,
            latitude = latitude,
            longitude = longitude,
            takenAt = takenAt,
            caption = caption,
            orderIndex = photosByPlace[placeId]?.size ?: 0,
            createdAt = java.time.Instant.now().toString(),
            place = null
        )
        photosByPlace.getOrPut(placeId) { mutableListOf() }.add(0, photo)

        // If place has no coords yet, adopt photo GPS
        placesByTrip.values.forEach { list ->
            val idx = list.indexOfFirst { it.id == placeId }
            if (idx >= 0 && list[idx].latitude == null && latitude != null && longitude != null) {
                list[idx] = list[idx].copy(latitude = latitude, longitude = longitude)
            }
        }
        return photo
    }

    fun photosFor(placeId: String): List<PhotoResponse> =
        photosByPlace[placeId].orEmpty().toList()
}
