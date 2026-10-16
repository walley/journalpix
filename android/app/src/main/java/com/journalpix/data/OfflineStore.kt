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
 * Hierarchy: Trip → Days → Places → Photos. Places & photos ordered by time.
 */
object OfflineStore {

    private val photosByPlace = ConcurrentHashMap<String, MutableList<PhotoResponse>>()
    private val placesByTrip = ConcurrentHashMap<String, MutableList<PlaceWithPhotos>>()
    private val trips = ConcurrentHashMap<String, TripResponse>()
    /** photoId -> reverse-geocoded address */
    val addresses = ConcurrentHashMap<String, String>()

    private val exifDateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    fun ensureDemoTrip(tripId: String): TripResponse {
        val id = tripId.ifBlank { "demo-trip-1" }
        placesByTrip.putIfAbsent(
            id,
            mutableListOf(
                PlaceWithPhotos(
                    id = "demo-place-1",
                    tripId = id,
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
        return trips.getOrPut(id) {
            TripResponse(
                id = id,
                userId = "00000000-0000-0000-0000-000000000001",
                title = if (id.startsWith("demo")) "Demo Alps Weekend" else "Trip $id",
                description = "Offline timeline — photos stay on device",
                startDate = LocalDate.now().toString(),
                endDate = LocalDate.now().plusDays(2).toString(),
                status = TripStatus.PLANNED,
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-01-01T00:00:00Z",
                placesCount = placesByTrip[id]?.size,
                photosCount = photosByPlace.values.sumOf { it.size }
            )
        }
    }

    fun updateTripDates(tripId: String, startDate: String?, endDate: String?): TripResponse {
        val current = ensureDemoTrip(tripId)
        val updated = current.copy(
            startDate = startDate ?: current.startDate,
            endDate = endDate ?: current.endDate,
            updatedAt = java.time.Instant.now().toString()
        )
        trips[current.id] = updated
        return updated
    }

    fun timelineFor(tripId: String): TripTimeline {
        val trip = ensureDemoTrip(tripId)
        val places = placesByTrip[trip.id].orEmpty()
            .filter { photosByPlace[it.id].orEmpty().isNotEmpty() || true } // keep empty places too
            .map { place ->
                val photos = photosByPlace[place.id].orEmpty()
                    .sortedWith(compareBy({ photoSortKey(it) }, { it.orderIndex }))
                    .toList()
                place.copy(photos = photos)
            }

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
        return TripTimeline(
            trip = trip.copy(
                placesCount = places.size,
                photosCount = places.sumOf { it.photos.size }
            ),
            timeline = items
        )
    }

    fun addPlace(
        tripId: String,
        name: String,
        visitDate: String? = null,
        address: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): PlaceWithPhotos {
        val trip = ensureDemoTrip(tripId)
        val day = visitDate ?: LocalDate.now().toString()
        val place = PlaceWithPhotos(
            id = "place-${UUID.randomUUID()}",
            tripId = trip.id,
            name = name,
            description = null,
            latitude = latitude,
            longitude = longitude,
            address = address,
            visitDate = day,
            orderIndex = placesByTrip[trip.id]?.size ?: 0,
            createdAt = java.time.Instant.now().toString(),
            photos = emptyList()
        )
        placesByTrip.getOrPut(trip.id) { mutableListOf() }.add(place)
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
        val dayFromExif = takenAt?.let { parseExifDay(it) }

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

        // Move place onto the photo's EXIF day so timeline shows correct day
        placesByTrip.values.forEach { list ->
            val idx = list.indexOfFirst { it.id == placeId }
            if (idx >= 0) {
                var p = list[idx]
                if (p.latitude == null && latitude != null && longitude != null) {
                    p = p.copy(latitude = latitude, longitude = longitude)
                }
                if (dayFromExif != null) {
                    p = p.copy(visitDate = dayFromExif)
                }
                list[idx] = p
            }
        }

        // Keep photos of this place sorted by time
        sortPhotosInPlace(placeId)
        return photo
    }

    fun photosFor(placeId: String): List<PhotoResponse> {
        sortPhotosInPlace(placeId)
        return photosByPlace[placeId].orEmpty().toList()
    }

    private fun sortPhotosInPlace(placeId: String) {
        val list = photosByPlace[placeId] ?: return
        list.sortWith(compareBy({ photoSortKey(it) }, { it.orderIndex }))
        list.forEachIndexed { i, p -> list[i] = p.copy(orderIndex = i) }
    }

    /**
     * After reverse geocode: find existing place with same address (or nearby name),
     * or create a new place on the photo's day. Move photo there and re-sort.
     */
    fun assignPhotoToGeocodedPlace(
        tripId: String,
        photoId: String,
        address: String,
        latitude: Double?,
        longitude: Double?
    ): PlaceWithPhotos {
        val trip = ensureDemoTrip(tripId)
        addresses[photoId] = address

        val shortName = shortPlaceName(address)
        val photo = findPhoto(photoId)
            ?: return addPlace(trip.id, shortName, address = address, latitude = latitude, longitude = longitude)

        val day = photo.takenAt?.let { parseExifDay(it) }
            ?: LocalDate.now().toString()

        // Match existing place by address or short name on same trip
        val existing = placesByTrip[trip.id].orEmpty().firstOrNull { place ->
            val sameAddress = !place.address.isNullOrBlank() &&
                place.address.equals(address, ignoreCase = true)
            val sameName = place.name.equals(shortName, ignoreCase = true)
            val near = latitude != null && longitude != null &&
                place.latitude != null && place.longitude != null &&
                haversineMeters(latitude, longitude, place.latitude!!, place.longitude!!) < 80.0
            sameAddress || sameName || near
        }

        val target = if (existing != null) {
            val list = placesByTrip[trip.id]!!
            val idx = list.indexOfFirst { it.id == existing.id }
            val updated = existing.copy(
                name = if (existing.name == "Demo Place") shortName else existing.name,
                address = address,
                latitude = latitude ?: existing.latitude,
                longitude = longitude ?: existing.longitude,
                visitDate = day
            )
            if (idx >= 0) list[idx] = updated
            updated
        } else {
            addPlace(
                tripId = trip.id,
                name = shortName,
                visitDate = day,
                address = address,
                latitude = latitude,
                longitude = longitude
            )
        }

        movePhoto(photoId, fromPlaceId = photo.placeId, toPlaceId = target.id)
        sortPhotosInPlace(target.id)
        return target
    }

    private fun movePhoto(photoId: String, fromPlaceId: String?, toPlaceId: String) {
        if (fromPlaceId == toPlaceId) {
            sortPhotosInPlace(toPlaceId)
            return
        }
        var moved: PhotoResponse? = null
        if (fromPlaceId != null) {
            val src = photosByPlace[fromPlaceId]
            val idx = src?.indexOfFirst { it.id == photoId } ?: -1
            if (idx >= 0) {
                moved = src!!.removeAt(idx).copy(placeId = toPlaceId)
            }
        }
        if (moved == null) {
            // search all places
            for ((pid, list) in photosByPlace) {
                val idx = list.indexOfFirst { it.id == photoId }
                if (idx >= 0) {
                    moved = list.removeAt(idx).copy(placeId = toPlaceId)
                    break
                }
            }
        }
        if (moved != null) {
            photosByPlace.getOrPut(toPlaceId) { mutableListOf() }.add(moved)
            sortPhotosInPlace(toPlaceId)
            fromPlaceId?.let { sortPhotosInPlace(it) }
        }
    }

    private fun findPhoto(photoId: String): PhotoResponse? {
        photosByPlace.values.forEach { list ->
            list.find { it.id == photoId }?.let { return it }
        }
        return null
    }

    private fun shortPlaceName(address: String): String {
        val first = address.split(",").firstOrNull()?.trim().orEmpty()
        return first.ifBlank { address.take(48) }.take(64)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    // --- ordering helpers ---

    private fun placeDay(place: PlaceWithPhotos): String {
        place.visitDate?.takeIf { it.isNotBlank() }?.let { return it }
        val photos = photosByPlace[place.id].orEmpty()
        photos.mapNotNull { it.takenAt?.let { t -> parseExifDay(t) } }
            .minOrNull()
            ?.let { return it }
        return LocalDate.now().toString()
    }

    private fun placeSortKey(place: PlaceWithPhotos): Long {
        val photos = photosByPlace[place.id].orEmpty()
        val fromPhotos = photos.map { photoSortKey(it) }
        if (fromPhotos.isNotEmpty()) return fromPhotos.minOrNull()!!
        return parseInstantMillis(place.createdAt) ?: place.orderIndex.toLong()
    }

    private fun photoSortKey(photo: PhotoResponse): Long {
        photo.takenAt?.let { t -> parseExifMillis(t)?.let { return it } }
        return parseInstantMillis(photo.createdAt) ?: photo.orderIndex.toLong()
    }

    private fun parseExifDay(takenAt: String): String? {
        return try {
            LocalDateTime.parse(takenAt.trim(), exifDateTime).toLocalDate().toString()
        } catch (_: Exception) {
            try {
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
