package com.journalpix.data

import com.journalpix.models.PhotoResponse
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.models.TimelineItem
import com.journalpix.models.TripResponse
import com.journalpix.models.TripStatus
import com.journalpix.models.TripTimeline
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory offline store for demo mode (no backend).
 * Hierarchy: Trip → Days → Places → Photos. Places ordered by time.
 */
object OfflineStore {

    private val photosByPlace = ConcurrentHashMap<String, MutableList<PhotoResponse>>()
    private val placesByTrip = ConcurrentHashMap<String, MutableList<PlaceWithPhotos>>()
    /** photoId -> reverse-geocoded address */
    val addresses = ConcurrentHashMap<String, String>()

    private val exifDateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

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
                    visitDate = LocalDate.now().toString(),
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
            startDate = LocalDate.now().toString(),
            endDate = LocalDate.now().plusDays(2).toString(),
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
            val photos = photosByPlace[place.id].orEmpty()
                .sortedWith(compareBy({ photoSortKey(it) }, { it.orderIndex }))
                .toList()
            place.copy(photos = photos)
        }

        // Group by day, sort days ascending; places within day by time
        val grouped = places.groupBy { placeDay(it) }
        val items = grouped.entries
            .sortedBy { it.key }
            .map { (date, list) ->
                val ordered = list.sortedWith(
                    compareBy(
                        { placeSortKey(it) },
                        { it.orderIndex },
                        { it.name }
                    )
                )
                TimelineItem(date = date, places = ordered)
            }
        return TripTimeline(trip = trip, timeline = items)
    }

    fun addPlace(tripId: String, name: String, visitDate: String? = null): PlaceWithPhotos {
        ensureDemoTrip(tripId)
        val day = visitDate ?: LocalDate.now().toString()
        val place = PlaceWithPhotos(
            id = "place-${UUID.randomUUID()}",
            tripId = tripId,
            name = name,
            description = null,
            latitude = null,
            longitude = null,
            address = null,
            visitDate = day,
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
        photosByPlace.getOrPut(placeId) { mutableListOf() }.add(photo)

        // Adopt photo GPS / visit day from EXIF when place lacks them
        placesByTrip.values.forEach { list ->
            val idx = list.indexOfFirst { it.id == placeId }
            if (idx >= 0) {
                var p = list[idx]
                if (p.latitude == null && latitude != null && longitude != null) {
                    p = p.copy(latitude = latitude, longitude = longitude)
                }
                val dayFromExif = takenAt?.let { parseExifDay(it) }
                if (dayFromExif != null) {
                    p = p.copy(visitDate = dayFromExif)
                }
                list[idx] = p
            }
        }
        return photo
    }

    fun photosFor(placeId: String): List<PhotoResponse> =
        photosByPlace[placeId].orEmpty()
            .sortedWith(compareBy({ photoSortKey(it) }, { it.orderIndex }))
            .toList()

    // --- ordering helpers ---

    private fun placeDay(place: PlaceWithPhotos): String {
        // Prefer visitDate; else earliest photo EXIF day; else today
        place.visitDate?.takeIf { it.isNotBlank() }?.let { return it }
        val photos = photosByPlace[place.id].orEmpty()
        photos.mapNotNull { it.takenAt?.let { t -> parseExifDay(t) } }
            .minOrNull()
            ?.let { return it }
        return LocalDate.now().toString()
    }

    /** Sort key: earliest photo time, else createdAt, else orderIndex proxy. */
    private fun placeSortKey(place: PlaceWithPhotos): Long {
        val photos = photosByPlace[place.id].orEmpty()
        val fromPhotos = photos.mapNotNull { photoSortKey(it).takeIf { k -> k != Long.MAX_VALUE } }
        if (fromPhotos.isNotEmpty()) return fromPhotos.minOrNull()!!
        return parseInstantMillis(place.createdAt) ?: place.orderIndex.toLong()
    }

    private fun photoSortKey(photo: PhotoResponse): Long {
        photo.takenAt?.let { t ->
            parseExifMillis(t)?.let { return it }
        }
        return parseInstantMillis(photo.createdAt) ?: photo.orderIndex.toLong()
    }

    private fun parseExifDay(takenAt: String): String? {
        return try {
            LocalDateTime.parse(takenAt.trim(), exifDateTime).toLocalDate().toString()
        } catch (_: Exception) {
            try {
                // ISO or yyyy-MM-dd
                if (takenAt.length >= 10) LocalDate.parse(takenAt.substring(0, 10)).toString() else null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun parseExifMillis(takenAt: String): Long? {
        return try {
            LocalDateTime.parse(takenAt.trim(), exifDateTime)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            parseInstantMillis(takenAt)
        }
    }

    private fun parseInstantMillis(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return try {
            java.time.Instant.parse(s).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
