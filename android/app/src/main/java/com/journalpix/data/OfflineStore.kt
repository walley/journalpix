package com.journalpix.data

import android.content.Context
import com.journalpix.models.PhotoResponse
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.models.TimelineItem
import com.journalpix.models.TransportMode
import com.journalpix.models.TripResponse
import com.journalpix.models.TripStatus
import com.journalpix.models.TripTimeline
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline-first store for trip → places → photos.
 *
 * - Photos are copied under filesDir/photos/ (durable, not cache).
 * - Metadata is persisted as JSON under filesDir/offline/store.json.
 * - When a backend becomes available, entries with syncPending=true are uploaded;
 *   sync remains a permanent feature (offline edits always queue).
 *
 * Hierarchy: Trip → Days → Places → Photos. Places & photos ordered by time.
 * Days outside the trip [startDate, endDate] land in the special bucket BEFORE_AFTER_KEY.
 */
object OfflineStore {

    const val BEFORE_KEY = "before"
    const val AFTER_KEY = "after"

    private val photosByPlace = ConcurrentHashMap<String, MutableList<PhotoResponse>>()
    private val placesByTrip = ConcurrentHashMap<String, MutableList<PlaceWithPhotos>>()
    private val trips = ConcurrentHashMap<String, TripResponse>()
    /** photoId -> reverse-geocoded address */
    val addresses = ConcurrentHashMap<String, String>()
    /** IDs that still need to be pushed when online (permanent sync queue). */
    private val syncPending = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var loaded = false

    private val exifDateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /** Call once from Application / first screen so local files are used. */
    fun init(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            appContext = context.applicationContext
            loadFromDisk()
            loaded = true
        }
    }

    fun photosDir(context: Context = requireContext()): File =
        File(context.filesDir, "photos").apply { mkdirs() }

    fun offlineDir(context: Context = requireContext()): File =
        File(context.filesDir, "offline").apply { mkdirs() }

    private fun storeFile(context: Context = requireContext()): File =
        File(offlineDir(context), "store.json")

    private fun requireContext(): Context =
        appContext ?: error("OfflineStore.init(context) must be called before use")

    private fun loadFromDisk() {
        val ctx = appContext ?: return
        val file = storeFile(ctx)
        if (!file.exists()) return
        try {
            trips.clear()
            placesByTrip.clear()
            photosByPlace.clear()
            addresses.clear()
            syncPending.clear()

            val root = JSONObject(file.readText())
            val tripsArr = root.optJSONArray("trips") ?: JSONArray()
            for (i in 0 until tripsArr.length()) {
                val o = tripsArr.getJSONObject(i)
                val t = tripFromJson(o)
                trips[t.id] = t
            }
            val placesArr = root.optJSONArray("places") ?: JSONArray()
            for (i in 0 until placesArr.length()) {
                val o = placesArr.getJSONObject(i)
                val p = placeFromJson(o)
                placesByTrip.getOrPut(p.tripId) { mutableListOf() }.add(p)
            }
            val photosArr = root.optJSONArray("photos") ?: JSONArray()
            for (i in 0 until photosArr.length()) {
                val o = photosArr.getJSONObject(i)
                val ph = photoFromJson(o)
                val pid = ph.placeId ?: continue
                photosByPlace.getOrPut(pid) { mutableListOf() }.add(ph)
            }
            val addr = root.optJSONObject("addresses")
            if (addr != null) {
                addr.keys().forEach { k -> addresses[k] = addr.getString(k) }
            }
            val pending = root.optJSONArray("syncPending")
            if (pending != null) {
                for (i in 0 until pending.length()) syncPending.add(pending.getString(i))
            }
        } catch (_: Exception) {
        }
    }

    fun persist() {
        val ctx = appContext ?: return
        try {
            val root = JSONObject()
            val tripsArr = JSONArray()
            trips.values.forEach { tripsArr.put(tripToJson(it)) }
            root.put("trips", tripsArr)

            val placesArr = JSONArray()
            placesByTrip.values.flatten().forEach { placesArr.put(placeToJson(it)) }
            root.put("places", placesArr)

            val photosArr = JSONArray()
            photosByPlace.values.flatten().forEach { photosArr.put(photoToJson(it)) }
            root.put("photos", photosArr)

            val addr = JSONObject()
            addresses.forEach { (k, v) -> addr.put(k, v) }
            root.put("addresses", addr)

            val pending = JSONArray()
            syncPending.forEach { pending.put(it) }
            root.put("syncPending", pending)

            storeFile(ctx).writeText(root.toString(2))
        } catch (_: Exception) {
        }
    }

    private fun markPending(id: String) {
        syncPending.add(id)
        persist()
        com.journalpix.sync.SyncManager.triggerSync(appContext)
    }

    fun pendingSyncIds(): Set<String> = syncPending.toSet()

    fun clearPending(id: String) {
        syncPending.remove(id)
        persist()
    }

    fun getTrip(id: String): TripResponse? = trips[id]

    fun findPlace(placeId: String): Pair<PlaceWithPhotos, String>? {
        for ((tripId, places) in placesByTrip) {
            val found = places.find { it.id == placeId }
            if (found != null) return Pair(found, tripId)
        }
        return null
    }

    fun findPhotoWithPlace(photoId: String): Pair<PhotoResponse, String>? {
        for ((placeId, photos) in photosByPlace) {
            val found = photos.find { it.id == photoId }
            if (found != null) return Pair(found, placeId)
        }
        return null
    }

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
                    transportFromPrevious = null,
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
        markPending(current.id)
        return updated
    }

    fun updateTripTitle(tripId: String, title: String): TripResponse {
        val current = ensureDemoTrip(tripId)
        val updated = current.copy(
            title = title.trim().ifBlank { current.title },
            updatedAt = java.time.Instant.now().toString()
        )
        trips[current.id] = updated
        markPending(current.id)
        return updated
    }

    fun timelineFor(tripId: String): TripTimeline {
        val trip = ensureDemoTrip(tripId)
        val places = placesByTrip[trip.id].orEmpty()
            .map { place ->
                val photos = photosByPlace[place.id].orEmpty()
                    .sortedWith(compareBy({ photoSortKey(it) }, { it.orderIndex }))
                    .toList()
                place.copy(photos = photos)
            }

        val start = trip.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = trip.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val beforePlaces = mutableListOf<PlaceWithPhotos>()
        val afterPlaces = mutableListOf<PlaceWithPhotos>()
        val inRange = mutableListOf<PlaceWithPhotos>()

        for (p in places) {
            val day = placeDay(p)
            val d = runCatching { LocalDate.parse(day) }.getOrNull()
            if (d != null && start != null && end != null) {
                if (d.isBefore(start)) {
                    beforePlaces.add(p)
                } else if (d.isAfter(end)) {
                    afterPlaces.add(p)
                } else {
                    inRange.add(p)
                }
            } else {
                inRange.add(p)
            }
        }

        val items = mutableListOf<TimelineItem>()

        if (beforePlaces.isNotEmpty()) {
            val ordered = beforePlaces.sortedWith(
                compareBy({ placeSortKey(it) }, { it.orderIndex }, { it.name })
            )
            items.add(TimelineItem(date = BEFORE_KEY, places = ordered))
        }

        val grouped = inRange.groupBy { placeDay(it) }

        if (start != null && end != null && !end.isBefore(start) && java.time.temporal.ChronoUnit.DAYS.between(
                start,
                end
            ) < 365
        ) {
            var curr = start
            while (!curr!!.isAfter(end)) {
                val dateStr = curr.toString()
                val list = grouped[dateStr].orEmpty()
                  .sortedWith(
                      compareBy({ placeSortKey(it) }, { it.orderIndex }, { it.name })
                  )
                items.add(TimelineItem(date = dateStr, places = list))
                curr = curr.plusDays(1)
            }
            grouped.keys.filter { key -> items.none { it.date == key } }
              .forEach { dateStr ->
                  val list = grouped[dateStr].orEmpty()
                    .sortedWith(
                        compareBy({ placeSortKey(it) }, { it.orderIndex }, { it.name })
                    )
                  items.add(TimelineItem(date = dateStr, places = list))
              }
        } else {
            grouped.entries.sortedBy { it.key }
              .forEach { (date, list) ->
                  val ordered = list.sortedWith(
                      compareBy({ placeSortKey(it) }, { it.orderIndex }, { it.name })
                  )
                  items.add(TimelineItem(date = date, places = ordered))
              }
        }

        if (afterPlaces.isNotEmpty()) {
            val ordered = afterPlaces.sortedWith(
                compareBy({ placeSortKey(it) }, { it.orderIndex }, { it.name })
            )
            items.add(TimelineItem(date = AFTER_KEY, places = ordered))
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
        longitude: Double? = null,
        transportFromPrevious: String? = null
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
            transportFromPrevious = transportFromPrevious,
            createdAt = java.time.Instant.now().toString(),
            photos = emptyList()
        )
        placesByTrip.getOrPut(trip.id) { mutableListOf() }.add(place)
        photosByPlace.putIfAbsent(place.id, mutableListOf())
        markPending(place.id)
        return place
    }

    fun setTransport(placeId: String, mode: TransportMode?) {
        placesByTrip.values.forEach { list ->
            val idx = list.indexOfFirst { it.id == placeId }
            if (idx >= 0) {
                list[idx] = list[idx].copy(transportFromPrevious = mode?.name)
                markPending(placeId)
            }
        }
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

        sortPhotosInPlace(placeId)
        markPending(photo.id)
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
        markPending(target.id)
        persist()
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
            for ((_, list) in photosByPlace) {
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

    private fun tripToJson(t: TripResponse) = JSONObject().apply {
        put("id", t.id)
        put("userId", t.userId)
        put("title", t.title)
        put("description", t.description)
        put("startDate", t.startDate)
        put("endDate", t.endDate)
        put("status", t.status.name)
        put("createdAt", t.createdAt)
        put("updatedAt", t.updatedAt)
    }

    private fun tripFromJson(o: JSONObject) = TripResponse(
        id = o.getString("id"),
        userId = o.getString("userId"),
        title = o.getString("title"),
        description = o.optString("description", null).takeIf { it.isNotBlank() },
        startDate = o.optString("startDate", null).takeIf { it.isNotBlank() },
        endDate = o.optString("endDate", null).takeIf { it.isNotBlank() },
        status = runCatching { TripStatus.valueOf(o.optString("status", "PLANNED")) }.getOrDefault(TripStatus.PLANNED),
        createdAt = o.optString("createdAt", ""),
        updatedAt = o.optString("updatedAt", ""),
        placesCount = null,
        photosCount = null
    )

    private fun placeToJson(p: PlaceWithPhotos) = JSONObject().apply {
        put("id", p.id)
        put("tripId", p.tripId)
        put("name", p.name)
        put("description", p.description)
        put("latitude", p.latitude)
        put("longitude", p.longitude)
        put("address", p.address)
        put("visitDate", p.visitDate)
        put("orderIndex", p.orderIndex)
        put("transportFromPrevious", p.transportFromPrevious)
        put("createdAt", p.createdAt)
    }

    private fun placeFromJson(o: JSONObject) = PlaceWithPhotos(
        id = o.getString("id"),
        tripId = o.getString("tripId"),
        name = o.getString("name"),
        description = o.optString("description", null).takeIf { it.isNotBlank() },
        latitude = if (o.isNull("latitude")) null else o.optDouble("latitude"),
        longitude = if (o.isNull("longitude")) null else o.optDouble("longitude"),
        address = o.optString("address", null).takeIf { it.isNotBlank() },
        visitDate = o.optString("visitDate", null).takeIf { it.isNotBlank() },
        orderIndex = o.optInt("orderIndex", 0),
        transportFromPrevious = o.optString("transportFromPrevious", null).takeIf { it.isNotBlank() },
        createdAt = o.optString("createdAt", ""),
        photos = emptyList()
    )

    private fun photoToJson(p: PhotoResponse) = JSONObject().apply {
        put("id", p.id)
        put("placeId", p.placeId)
        put("userId", p.userId)
        put("filePath", p.filePath)
        put("thumbnailPath", p.thumbnailPath)
        put("latitude", p.latitude)
        put("longitude", p.longitude)
        put("takenAt", p.takenAt)
        put("caption", p.caption)
        put("orderIndex", p.orderIndex)
        put("createdAt", p.createdAt)
    }

    private fun photoFromJson(o: JSONObject) = PhotoResponse(
        id = o.getString("id"),
        placeId = o.optString("placeId", null).takeIf { it.isNotBlank() },
        userId = o.getString("userId"),
        filePath = o.getString("filePath"),
        thumbnailPath = o.optString("thumbnailPath", o.getString("filePath")),
        latitude = if (o.isNull("latitude")) null else o.optDouble("latitude"),
        longitude = if (o.isNull("longitude")) null else o.optDouble("longitude"),
        takenAt = o.optString("takenAt", null).takeIf { it.isNotBlank() },
        caption = o.optString("caption", null).takeIf { it.isNotBlank() },
        orderIndex = o.optInt("orderIndex", 0),
        createdAt = o.optString("createdAt", ""),
        place = null
    )

    fun mergeBackendTimeline(timeline: TripTimeline) {
        val trip = timeline.trip
        trips[trip.id] = trip

        val newPlaces = mutableListOf<PlaceWithPhotos>()
        val allPhotos = mutableMapOf<String, MutableList<PhotoResponse>>()

        for (item in timeline.timeline) {
            for (place in item.places) {
                val p =
                    if (place.visitDate.isNullOrBlank()) place.copy(visitDate = item.date) else place
                newPlaces.add(p)
                allPhotos[p.id] = p.photos.toMutableList()
            }
        }

        placesByTrip[trip.id] = newPlaces
        for ((placeId, photos) in allPhotos) {
            photosByPlace[placeId] = photos
        }
        persist()
    }
}
