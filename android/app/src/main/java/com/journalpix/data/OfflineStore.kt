package com.journalpix.data

import android.content.Context
import com.journalpix.models.PhotoResponse
import com.journalpix.models.PlaceResponse
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.models.TimelineItem
import com.journalpix.models.TransportMode
import com.journalpix.models.TripResponse
import com.journalpix.models.TripStatus
import com.journalpix.models.TripTimeline
import com.journalpix.models.ZonedTime
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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

    private const val TAG = "JPXOS"

    const val BEFORE_KEY = "before"
    const val AFTER_KEY = "after"

    private val photosByPlace = ConcurrentHashMap<String, MutableList<PhotoResponse>>()
    private val placesByTrip = ConcurrentHashMap<String, MutableList<PlaceWithPhotos>>()
    private val trips = ConcurrentHashMap<String, TripResponse>()
    /** Photos not assigned to any place */
    private val unassignedPhotos = ConcurrentHashMap<String, PhotoResponse>()
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
        android.util.Log.i(TAG, "loadFromDisk: storeFile=${file.absolutePath}, exists=${file.exists()}, photosDir=${photosDir(ctx).absolutePath}")
        if (!file.exists()) {
            android.util.Log.i(TAG, "loadFromDisk: no existing store.json — fresh start (data survives reinstalls in filesDir, wiped only on uninstall/clear-data)")
            return
        }
        try {
            // Parse into TEMP collections first — only swap in on full success so a
            // single bad entry can never wipe the in-memory (and later persisted) data
            val newTrips = HashMap<String, TripResponse>()
            val newPlaces = HashMap<String, MutableList<PlaceWithPhotos>>()
            val newPhotos = HashMap<String, MutableList<PhotoResponse>>()
            val newUnassigned = HashMap<String, PhotoResponse>()
            val newAddresses = HashMap<String, String>()
            val newPending = HashSet<String>()

            val root = JSONObject(file.readText())
            val tripsArr = root.optJSONArray("trips") ?: JSONArray()
            for (i in 0 until tripsArr.length()) {
                val o = tripsArr.getJSONObject(i)
                val t = tripFromJson(o)
                newTrips[t.id] = t
            }
            val placesArr = root.optJSONArray("places") ?: JSONArray()
            for (i in 0 until placesArr.length()) {
                val o = placesArr.getJSONObject(i)
                val p = placeFromJson(o)
                newPlaces.getOrPut(p.tripId) { mutableListOf() }.add(p)
            }
            val photosArr = root.optJSONArray("photos") ?: JSONArray()
            for (i in 0 until photosArr.length()) {
                val o = photosArr.getJSONObject(i)
                val ph = photoFromJson(o)
                if (ph.placeId == null || ph.placeId.isEmpty()) {
                    newUnassigned[ph.id] = ph
                } else {
                    newPhotos.getOrPut(ph.placeId) { mutableListOf() }.add(ph)
                }
            }
            val addr = root.optJSONObject("addresses")
            if (addr != null) {
                addr.keys().forEach { k -> newAddresses[k] = addr.getString(k) }
            }
            val pending = root.optJSONArray("syncPending")
            if (pending != null) {
                for (i in 0 until pending.length()) newPending.add(pending.getString(i))
            }

            trips.clear(); trips.putAll(newTrips)
            placesByTrip.clear(); placesByTrip.putAll(newPlaces)
            photosByPlace.clear(); photosByPlace.putAll(newPhotos)
            unassignedPhotos.clear(); unassignedPhotos.putAll(newUnassigned)
            addresses.clear(); addresses.putAll(newAddresses)
            syncPending.clear(); syncPending.addAll(newPending)
            cleanupDemoLeftovers()
            android.util.Log.i(TAG, "loadFromDisk: restored trips=${trips.size}, places=${placesByTrip.values.sumOf { it.size }}, photos=${photosByPlace.values.sumOf { it.size }}, pending=${syncPending.size}")
        } catch (e: Exception) {
            // Never clear live data on a parse failure — keep whatever is in memory
            android.util.Log.e(TAG, "loadFromDisk: failed to parse store.json, keeping in-memory data: ${e.message}", e)
        }
    }

    /** Remove leftover demo-testing artifacts (old "Demo Place", trip-title-named places, demo trips). */
    private fun cleanupDemoLeftovers() {
        val demoNamePattern = Regex("^Trip demo", RegexOption.IGNORE_CASE)
        var removed = 0
        // Drop whole demo trips and their places
        val demoTripIds = placesByTrip.keys.filter { it.startsWith("demo") }.toSet()
        demoTripIds.forEach { tripId ->
            removed += placesByTrip.remove(tripId)?.size ?: 0
        }
        trips.keys.filter { it.startsWith("demo") }.forEach { trips.remove(it) }
        placesByTrip.values.forEach { list ->
            val before = list.size
            list.removeAll { it.id == "demo-place-1" || it.name == "Demo Place" || demoNamePattern.containsMatchIn(it.name) }
            removed += before - list.size
        }
        photosByPlace.remove("demo-place-1")
        // Drop pending queue entries that no longer exist locally
        val knownIds = placesByTrip.values.flatten().map { it.id }.toSet() + trips.keys
        syncPending.removeAll { it !in knownIds }
        if (removed > 0 || demoTripIds.isNotEmpty()) {
            android.util.Log.i(TAG, "cleanupDemoLeftovers: removed $removed demo leftover places, demo trips: $demoTripIds")
            persist()
        }
    }

    /**
     * Destroy an invalid trip (a blank currentTripId would fall back to the local
     * "demo-trip-1" key): remove all its places, photos, trip entry and pending flags.
     */
    fun destroyDemoTripLeftovers() {
        android.util.Log.i(TAG, "destroyDemoTripLeftovers: destroying invalid (blank/demo) trip leftovers")
        val demoKeys = placesByTrip.keys.filter { it.startsWith("demo") }.toSet()
        var removed = 0
        demoKeys.forEach { key ->
            placesByTrip.remove(key)?.forEach { p ->
                photosByPlace.remove(p.id)
                removed++
            }
        }
        trips.keys.filter { it.startsWith("demo") }.forEach { trips.remove(it) }
        // Drop pending queue entries that no longer exist locally
        val knownIds = placesByTrip.values.flatten().map { it.id }.toSet() + trips.keys
        syncPending.removeAll { it !in knownIds }
        android.util.Log.i(TAG, "destroyDemoTripLeftovers: removed $removed places, demo trips: $demoKeys")
        persist()
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
            unassignedPhotos.values.forEach { photosArr.put(photoToJson(it)) }
            root.put("photos", photosArr)

            val addr = JSONObject()
            addresses.forEach { (k, v) -> addr.put(k, v) }
            root.put("addresses", addr)

            val pending = JSONArray()
            syncPending.forEach { pending.put(it) }
            root.put("syncPending", pending)

            // Atomic write: temp file + rename, so a crash mid-write can't corrupt the store
            val file = storeFile(ctx)
            val tmp = File(file.parentFile, "store.json.tmp")
            tmp.writeText(root.toString(2))
            if (!tmp.renameTo(file)) {
                // rename can fail across filesystems — fall back to direct copy
                file.writeText(root.toString(2))
                tmp.delete()
            }
            android.util.Log.i(TAG, "persist: wrote store.json (trips=${trips.size}, places=${placesByTrip.values.sumOf { it.size }}, photos=${photosByPlace.values.sumOf { it.size }})")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "persist: FAILED to write store.json: ${e.message}", e)
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

    fun updateTripServerId(localId: String, serverTrip: TripResponse) {
        if (trips.containsKey(localId)) {
            trips.remove(localId)
            trips[serverTrip.id] = serverTrip

            val places = placesByTrip.remove(localId)
            if (places != null) {
                placesByTrip[serverTrip.id] = places.map { it.copy(tripId = serverTrip.id) }
                  .toMutableList()
            }
            persist()
        }
    }

    fun updatePlaceServerId(localId: String, serverPlace: PlaceResponse) {
        for ((_, places) in placesByTrip) {
            val index = places.indexOfFirst { it.id == localId }
            if (index != -1) {
                val oldPlace = places[index]
                val updatedPlace = oldPlace.copy(
                    id = serverPlace.id,
                    name = serverPlace.name,
                    description = serverPlace.description,
                    latitude = serverPlace.latitude,
                    longitude = serverPlace.longitude,
                    address = serverPlace.address,
                    visitDate = serverPlace.visitDate ?: oldPlace.visitDate,
                    orderIndex = serverPlace.orderIndex
                )
                places[index] = updatedPlace

                val photos = photosByPlace.remove(localId)
                if (photos != null) {
                    photosByPlace[serverPlace.id] = photos
                    photos.forEachIndexed { pIdx, ph ->
                        photos[pIdx] = ph.copy(placeId = serverPlace.id)
                    }
                }
                persist()
                break
            }
        }
    }

    fun getTrip(id: String): TripResponse? = trips[id]

    fun findPlace(placeId: String): Pair<PlaceWithPhotos, String>? {
        for ((tripId, places) in placesByTrip) {
            val found = places.find { it.id == placeId }
            if (found != null) return Pair(found, tripId)
        }
        return null
    }

    /** Find a place with its photos attached. */
    fun findPlaceWithPhotos(placeId: String): PlaceWithPhotos? {
        val found = findPlace(placeId)?.first
        if (found == null) {
            val all = placesByTrip.entries.joinToString("; ") { (tripId, list) ->
                "trip=$tripId: ${list.map { "${it.id}('${it.name}')" }}"
            }
            android.util.Log.w(TAG, "findPlaceWithPhotos: NOT FOUND placeId=$placeId. Known places: $all")
            return null
        }
        val photos = photosByPlace[placeId].orEmpty()
            .sortedWith(compareBy({ photoSortKey(it) }, { it.orderIndex }))
        android.util.Log.i(TAG, "findPlaceWithPhotos: placeId=$placeId, photos=${photos.size}, paths=${photos.map { it.filePath }}")
        return found.copy(photos = photos)
    }

    fun findPhotoWithPlace(photoId: String): Pair<PhotoResponse, String>? {
        for ((placeId, photos) in photosByPlace) {
            val found = photos.find { it.id == photoId }
            if (found != null) return Pair(found, placeId)
        }
        return null
    }

    /** All locally stored trips (offline mode / not logged in). */
    fun allTrips(): List<TripResponse> = trips.values.sortedBy { it.createdAt }

    /** Create a trip purely locally (offline, not logged in); queued for sync once logged in. */
    fun createLocalTrip(title: String, startDate: String?, endDate: String?): TripResponse {
        val id = "trip-${UUID.randomUUID()}"
        android.util.Log.i(TAG, "createLocalTrip: id=$id, title='$title'")
        val trip = TripResponse(
            id = id,
            userId = "",
            title = title.ifBlank { "Trip $id" },
            description = null,
            startDate = startDate,
            startTimezone = null,
            endDate = endDate,
            endTimezone = null,
            status = TripStatus.PLANNED,
            createdAt = java.time.Instant.now().toString(),
            updatedAt = java.time.Instant.now().toString(),
            placesCount = 0,
            photosCount = 0
        )
        trips[id] = trip
        placesByTrip.putIfAbsent(id, mutableListOf())
        markPending(id)
        return trip
    }

    fun ensureDemoTrip(tripId: String): TripResponse {
        if (tripId.isBlank()) {
            // A blank trip id is invalid — destroy any demo leftovers, never create a trip
            android.util.Log.e(TAG, "ensureDemoTrip: tripId is BLANK — invalid trip, destroying demo leftovers")
            destroyDemoTripLeftovers()
            return TripResponse(
                id = "",
                userId = "",
                title = "",
                description = null,
                startDate = null,
                startTimezone = null,
                endDate = null,
                endTimezone = null,
                status = TripStatus.PLANNED,
                createdAt = "",
                updatedAt = "",
                placesCount = 0,
                photosCount = 0
            )
        }
        val id = tripId
        placesByTrip.putIfAbsent(id, mutableListOf())
        return trips.getOrPut(id) {
            TripResponse(
                id = id,
                // No server user id here — user identity lives only in synchronization
                userId = "",
                title = if (id.startsWith("demo")) "Demo Alps Weekend" else "Trip $id",
                description = null,
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
        if (current.id.isBlank()) return current
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
        if (current.id.isBlank()) return current
        val updated = current.copy(
            title = title.trim().ifBlank { current.title },
            updatedAt = java.time.Instant.now().toString()
        )
        trips[current.id] = updated
        markPending(current.id)
        return updated
    }

    fun updateTripDescription(tripId: String, description: String?): TripResponse {
        val current = ensureDemoTrip(tripId)
        if (current.id.isBlank()) return current
        val updated = current.copy(
            description = description?.trim()?.takeIf { it.isNotBlank() },
            updatedAt = java.time.Instant.now().toString()
        )
        trips[current.id] = updated
        markPending(current.id)
        return updated
    }

    fun timelineFor(tripId: String): TripTimeline {
        if (tripId.isBlank()) {
            android.util.Log.e(TAG, "timelineFor: tripId is BLANK — invalid trip, destroying demo leftovers")
            destroyDemoTripLeftovers()
        }
        val trip = trips[tripId]
        if (trip == null) {
            return TripTimeline(
                trip = TripResponse(
                    id = tripId,
                    userId = "",
                    title = "",
                    description = null,
                    startDate = null,
                    startTimezone = null,
                    endDate = null,
                    endTimezone = null,
                    status = TripStatus.PLANNED,
                    createdAt = "",
                    updatedAt = "",
                    placesCount = 0,
                    photosCount = 0
                ),
                timeline = emptyList()
            )
        }
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
            val visitLocalDate = p.visitDate?.localDate
            if (visitLocalDate != null && start != null && end != null) {
                if (visitLocalDate.isBefore(start)) {
                    beforePlaces.add(p)
                } else if (visitLocalDate.isAfter(end)) {
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
                compareBy({ it.orderIndex }, { placeSortKey(it) })
            )
            items.add(TimelineItem(day = 0, date = BEFORE_KEY, places = ordered))
        }

        // Group in-range places by trip day number.
        val grouped = inRange.groupBy { p ->
            p.visitDate?.localDate?.let { ld ->
                start?.let { s ->
                    java.time.temporal.ChronoUnit.DAYS.between(s, ld).toInt() + 1
                } ?: p.day
            } ?: p.day
        }

        if (start != null && end != null && !end.isBefore(start) && java.time.temporal.ChronoUnit.DAYS.between(
                start,
                end
            ) < 365
        ) {
          val totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, end)
            .toInt() + 1
            var curr = start
            while (!curr!!.isAfter(end)) {
                val dateStr = curr.toString()
                val dayNum = java.time.temporal.ChronoUnit.DAYS.between(start!!, curr).toInt() + 1
                val list = grouped[dayNum].orEmpty()
                  .sortedWith(
                      compareBy({ it.orderIndex }, { placeSortKey(it) })
                  )
                items.add(TimelineItem(day = dayNum, date = dateStr, places = list))
                curr = curr.plusDays(1)
            }
          grouped.entries.filter { it.key !in 1 .. totalDays }
            .sortedBy { it.key }
            .forEach { (day, list) ->
              val ordered = list.sortedWith(
                compareBy({ it.orderIndex }, { placeSortKey(it) })
              )
              items.add(TimelineItem(day = day, date = null, places = ordered))
            }
        } else {
            grouped.entries.sortedBy { it.key }
              .forEach { (day, list) ->
                  val ordered = list.sortedWith(
                      compareBy({ it.orderIndex }, { placeSortKey(it) })
                  )
                  items.add(TimelineItem(day = day, date = null, places = ordered))
              }
        }

        if (afterPlaces.isNotEmpty()) {
            val ordered = afterPlaces.sortedWith(
                compareBy({ it.orderIndex }, { placeSortKey(it) })
            )
            items.add(TimelineItem(day = -1, date = AFTER_KEY, places = ordered))
        }

        return TripTimeline(
            trip = trip.copy(
                placesCount = places.size,
                photosCount = places.sumOf { it.photos.size }
            ),
            timeline = items
        )
    }

    /** All places of a trip (offline view). */
    fun placesForTrip(tripId: String): List<PlaceWithPhotos> = placesByTrip[tripId].orEmpty()

    fun addPlace(
        tripId: String,
        name: String,
        visitDate: ZonedTime? = null,
        address: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        transportFromPrevious: String? = null
    ): PlaceWithPhotos {
        val trip = ensureDemoTrip(tripId)
        if (trip.id.isBlank()) {
            android.util.Log.e(TAG, "addPlace: tripId is BLANK — refusing to create a place in an invalid trip")
            return PlaceWithPhotos(
                id = "",
                tripId = "",
                name = name,
                description = null,
                latitude = latitude,
                longitude = longitude,
                address = address,
                visitDate = visitDate,
                orderIndex = 0,
                transportFromPrevious = transportFromPrevious,
                createdAt = java.time.Instant.now().toString(),
                photos = emptyList()
            )
        }
        val place = PlaceWithPhotos(
            id = "place-${UUID.randomUUID()}",
            tripId = trip.id,
            name = name,
            description = null,
            latitude = latitude,
            longitude = longitude,
            address = address,
            visitDate = visitDate,
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

    /**
     * Store a place that was created on the server, keeping the SERVER id
     * (no pending flag). Prevents duplicate local place-… copies.
     */
    fun addServerPlace(tripId: String, server: PlaceResponse): PlaceWithPhotos {
        val place = PlaceWithPhotos(
            id = server.id,
            tripId = tripId,
            name = server.name,
            description = server.description,
            latitude = server.latitude,
            longitude = server.longitude,
            address = server.address,
            visitDate = server.visitDate,
            orderIndex = server.orderIndex,
            transportFromPrevious = server.transportFromPrevious,
            createdAt = server.createdAt,
            photos = emptyList()
        )
        val list = placesByTrip.getOrPut(tripId) { mutableListOf() }
        val idx = list.indexOfFirst { it.id == place.id }
        if (idx >= 0) list[idx] = place else list.add(place)
        photosByPlace.putIfAbsent(place.id, mutableListOf())
        android.util.Log.i(TAG, "addServerPlace: stored server place ${place.id} '${place.name}' under trip $tripId")
        persist()
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

    /** Update a place's fields locally (e.g., lat/lon/address from map selection). */
    fun updatePlaceLocal(
        placeId: String,
        name: String? = null,
        description: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        address: String? = null,
        visitDate: ZonedTime? = null
    ) {
        android.util.Log.i(TAG, "updatePlaceLocal START: placeId=$placeId, lat=$latitude, lon=$longitude, addr=$address")
        placesByTrip.values.forEach { list ->
            val idx = list.indexOfFirst { it.id == placeId }
            if (idx >= 0) {
                val p = list[idx]
                android.util.Log.i(TAG, "updatePlaceLocal: found at idx=$idx, before lat=${p.latitude} lon=${p.longitude}")
                list[idx] = p.copy(
                    name = name ?: p.name,
                    description = description ?: p.description,
                    latitude = latitude ?: p.latitude,
                    longitude = longitude ?: p.longitude,
                    address = address ?: p.address,
                    visitDate = visitDate ?: p.visitDate
                )
                android.util.Log.i(TAG, "updatePlaceLocal: after lat=${list[idx].latitude} lon=${list[idx].longitude}")
                markPending(placeId)
            } else {
                android.util.Log.w(TAG, "updatePlaceLocal: place $placeId NOT FOUND in list of ${list.size}")
            }
        }
    }

    /** Reorder places within a trip by assigning new orderIndex values based on the given ID order. */
    /** Delete a trip locally with all its places/photos; queued for server deletion on next sync. */
    fun deleteTripLocal(tripId: String) {
        android.util.Log.i(TAG, "deleteTripLocal: tripId=$tripId")
        placesByTrip.remove(tripId)?.forEach { p -> photosByPlace.remove(p.id) }
        trips.remove(tripId)
        markPending(tripId)
        persist()
    }

    /** Delete a place locally (and its photos); queued for server deletion on next sync. */
    fun deletePlaceLocal(placeId: String) {
        android.util.Log.i(TAG, "deletePlaceLocal: placeId=$placeId")
        placesByTrip.values.forEach { list -> list.removeAll { it.id == placeId } }
        photosByPlace.remove(placeId)
        markPending(placeId)
        persist()
    }

    fun reorderPlaces(tripId: String, orderedPlaceIds: List<String>) {
        val list = placesByTrip.getOrPut(tripId) { mutableListOf() }
        val before = list.associate { it.id to it.orderIndex }
        orderedPlaceIds.forEachIndexed { index, id ->
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(orderIndex = index)
            } else {
                android.util.Log.e(TAG, "reorderPlaces: place id $id not found in trip $tripId")
            }
        }
        // Verify the swap actually took effect
        val after = list.associate { it.id to it.orderIndex }
        orderedPlaceIds.forEachIndexed { index, id ->
            val actual = after[id]
            if (actual != index) {
                android.util.Log.e(TAG, "reorderPlaces VERIFY FAILED: place $id expected orderIndex=$index but got $actual (was ${before[id]})")
            }
        }
        android.util.Log.i(TAG, "reorderPlaces: applied order ${orderedPlaceIds.map { "$it=${after[it]}" }}")
        // Persist
        list.forEach { markPending(it.id) }
    }

    /** Add a photo without assigning it to any place (unassigned). */
    fun addUnassignedPhoto(
        uriString: String,
        latitude: Double?,
        longitude: Double?,
        takenAt: ZonedTime? = null,
        caption: String? = null
    ): PhotoResponse {
        val photo = PhotoResponse(
            id = "photo-${UUID.randomUUID()}",
            placeId = null,
            userId = "",
            filePath = uriString,
            thumbnailPath = uriString,
            latitude = latitude,
            longitude = longitude,
            takenAt = takenAt,
            caption = caption,
            orderIndex = unassignedPhotos.size,
            createdAt = java.time.Instant.now().toString(),
            place = null
        )
        unassignedPhotos[photo.id] = photo
        markPending(photo.id)
        return photo
    }

    /** Get all unassigned photos (not assigned to any place). */
    fun getUnassignedPhotos(): List<PhotoResponse> {
        return unassignedPhotos.values.toList()
    }

    fun addPhoto(
        placeId: String,
        uriString: String,
        latitude: Double?,
        longitude: Double?,
        takenAt: ZonedTime? = null,
        caption: String? = null
    ): PhotoResponse {
        val photo = PhotoResponse(
            id = "photo-${UUID.randomUUID()}",
            placeId = placeId,
            userId = "",
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
                if (takenAt != null) {
                    p = p.copy(visitDate = takenAt)
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
        if (trip.id.isBlank()) {
            android.util.Log.e(TAG, "assignPhotoToGeocodedPlace: tripId is BLANK — invalid trip, skipping")
            return addPlace("", shortPlaceName(address), address = address, latitude = latitude, longitude = longitude)
        }
        addresses[photoId] = address

        val shortName = shortPlaceName(address)
        val photo = findPhoto(photoId)
            ?: return addPlace(trip.id, shortName, address = address, latitude = latitude, longitude = longitude)

        val visit = photo.takenAt
            ?: ZonedTime.fromInstant(Instant.now(), ZoneId.systemDefault().id)

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
            var updated = existing.copy(
                name = if (existing.name.isBlank() || existing.name.startsWith("Place")) shortName else existing.name,
                address = address,
                latitude = latitude ?: existing.latitude,
                longitude = longitude ?: existing.longitude,
                visitDate = visit
            )
            list[idx] = updated
            updated
        } else {
            addPlace(
                tripId = trip.id,
                name = shortName,
                visitDate = visit,
                address = address,
                latitude = latitude,
                longitude = longitude
            )
        }

        // Move photo to target place
        val oldPlaceId = photo.placeId
        if (oldPlaceId != null && oldPlaceId != target.id) {
            val oldList = photosByPlace[oldPlaceId]
            oldList?.removeAll { it.id == photoId }
        }
        val moved = photo.copy(placeId = target.id)
        val dest = photosByPlace.getOrPut(target.id) { mutableListOf() }
        if (dest.none { it.id == photoId }) dest.add(moved)
        else {
            val i = dest.indexOfFirst { it.id == photoId }
            if (i >= 0) dest[i] = moved
        }
        sortPhotosInPlace(target.id)
        persist()
        return target
    }

    /** Find a photo by id across all places and unassigned (offline view). */
    fun findPhotoPublic(photoId: String): PhotoResponse? = findPhoto(photoId)

    fun updatePhotoLocal(photoId: String, caption: String?) {
        android.util.Log.i(TAG, "updatePhotoLocal: photoId=$photoId")
        findPhoto(photoId)?.let { p ->
            val updated = p.copy(caption = caption)
            if (p.placeId != null) {
                photosByPlace[p.placeId]?.replaceAll { if (it.id == photoId) updated else it }
            } else {
                unassignedPhotos[photoId] = updated
            }
            markPending(photoId)
        }
    }

    fun deletePhotoLocal(photoId: String) {
        android.util.Log.i(TAG, "deletePhotoLocal: photoId=$photoId")
        photosByPlace.values.forEach { it.removeAll { p -> p.id == photoId } }
        unassignedPhotos.remove(photoId)
        markPending(photoId)
        persist()
    }

    private fun findPhoto(photoId: String): PhotoResponse? {
        for ((_, photos) in photosByPlace) {
            val found = photos.find { it.id == photoId }
            if (found != null) return found
        }
        return null
    }

    private fun shortPlaceName(address: String): String {
        val first = address.split(",").firstOrNull()?.trim().orEmpty()
        return first.ifBlank { address.take(40) }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    private fun placeSortKey(p: PlaceWithPhotos): String {
        val photos = photosByPlace[p.id].orEmpty()
        val times = photos.mapNotNull { it.takenAt?.sortKey }.sorted()
        return times.firstOrNull() ?: (p.visitDate?.sortKey ?: "")
    }

    private fun photoSortKey(p: PhotoResponse): String =
        p.takenAt?.sortKey ?: p.createdAt

    private fun parseExifDay(takenAt: String): String? {
        return try {
            val normalized = takenAt.replace("T", " ").replace("-", ":")
            val dt = LocalDateTime.parse(normalized, exifDateTime)
            dt.toLocalDate().toString()
        } catch (_: Exception) {
            try {
                if (takenAt.length >= 10) LocalDate.parse(takenAt.take(10)).toString() else null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun tripToJson(t: TripResponse) = JSONObject().apply {
        put("id", t.id)
        put("userId", t.userId)
        put("title", t.title)
        put("description", t.description)
        put("startDate", t.startDate)
        put("startTimezone", t.startTimezone)
        put("endDate", t.endDate)
        put("endTimezone", t.endTimezone)
        put("daysCount", t.daysCount)
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
        startTimezone = o.optString("startTimezone", null).takeIf { it.isNotBlank() },
        endDate = o.optString("endDate", null).takeIf { it.isNotBlank() },
        endTimezone = o.optString("endTimezone", null).takeIf { it.isNotBlank() },
        daysCount = o.optInt("daysCount", 0).takeIf { it > 0 },
        status = try {
            TripStatus.valueOf(o.optString("status", "PLANNED"))
        } catch (_: Exception) {
            TripStatus.PLANNED
        },
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
        p.visitDate?.let { zt ->
            put("visitDate", JSONObject().apply {
                zt.utc?.let { put("utc", it) }
                zt.timezone?.let { put("timezone", it) }
                zt.local?.let { put("local", it) }
                zt.localTimezone?.let { put("localTimezone", it) }
            })
        }
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
        visitDate = o.optJSONObject("visitDate")?.let {
            ZonedTime(
                utc = it.optString("utc", ""),
                timezone = it.optString("timezone", "UTC"),
                local = it.optString("local", ""),
                localTimezone = it.optString("localTimezone", null).takeIf { s -> !s.isNullOrBlank() }
            )
        } ?: o.optString("visitDate", null)
          .takeIf { it.isNotBlank() }
          ?.let { ZonedTime.parseLegacy(it) },
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
        p.takenAt?.let { zt ->
            put("takenAt", JSONObject().apply {
                zt.utc?.let { put("utc", it) }
                zt.timezone?.let { put("timezone", it) }
                zt.local?.let { put("local", it) }
                zt.localTimezone?.let { put("localTimezone", it) }
            })
        }
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
        takenAt = o.optJSONObject("takenAt")?.let {
            ZonedTime(
                utc = it.optString("utc", ""),
                timezone = it.optString("timezone", "UTC"),
                local = it.optString("local", ""),
                localTimezone = it.optString("localTimezone", null).takeIf { s -> !s.isNullOrBlank() }
            )
        } ?: o.optString("takenAt", null)
          .takeIf { it.isNotBlank() }
          ?.let { ZonedTime.parseLegacy(it) },
        caption = o.optString("caption", null).takeIf { it.isNotBlank() },
        orderIndex = o.optInt("orderIndex", 0),
        createdAt = o.optString("createdAt", ""),
        place = null
    )

    /** Re-queue an item for upload without triggering a sync (used during merge). */
    private fun requeuePending(id: String) {
        if (syncPending.add(id)) {
            android.util.Log.i(TAG, "requeuePending: $id re-queued for upload (deleted or missing on server)")
        }
    }

    /**
     * Device-first merge: the mobile device is the source of truth.
     * - Server places/photos are only ADDED when missing locally.
     * - Local items are never removed, even if absent on the server.
     * - Local items missing on the server are re-queued for upload.
     * - Local trip metadata wins while it has pending changes.
     */
    fun mergeBackendTimeline(timeline: TripTimeline) {
        val trip = timeline.trip

        // Device-first: keep local trip if it has unsynced local changes
        val localTrip = trips[trip.id]
        if (localTrip == null || !syncPending.contains(trip.id)) {
            trips[trip.id] = trip
        } else {
            android.util.Log.i(TAG, "mergeBackendTimeline: keeping local pending trip ${trip.id}")
        }

        // Start from the local place list — server data only fills gaps
        val merged = (placesByTrip[trip.id] ?: mutableListOf()).toMutableList()
        val serverPlaceIds = mutableSetOf<String>()
        // Local IDs that matched a server place by name — server already has them, don't re-upload
        val nameMatchedLocalIds = mutableSetOf<String>()

        for (item in timeline.timeline) {
            for (place in item.places) {
                val p =
                    if (place.visitDate == null) place.copy(visitDate = ZonedTime.parseLegacy(item.date)) else place
                serverPlaceIds.add(p.id)
                var localIdx = merged.indexOfFirst { it.id == p.id }
                if (localIdx == -1) {
                    // Same name already exists locally (e.g. our own earlier push created the
                    // server copy but the local ID rename didn't stick) → don't duplicate
                    localIdx = merged.indexOfFirst {
                        it.name.trim().equals(p.name.trim(), ignoreCase = true)
                    }
                    if (localIdx != -1) {
                        nameMatchedLocalIds.add(merged[localIdx].id)
                        android.util.Log.i(TAG, "mergeBackendTimeline: server place ${p.id} '${p.name}' matches local ${merged[localIdx].id} by name — keeping local, no duplicate")
                    }
                }
                if (localIdx == -1) {
                    // Not on device → download from server
                    merged.add(p)
                    android.util.Log.i(TAG, "mergeBackendTimeline: downloaded new place from server: ${p.id} '${p.name}'")
                } else {
                    // On both → device wins for place fields, but pull server photos we don't have
                    val local = merged[localIdx]
                    val localId = local.id
                    val localPhotos = photosByPlace[localId].orEmpty()
                    val missingPhotos = p.photos.filter { sp -> localPhotos.none { it.id == sp.id } }
                    if (missingPhotos.isNotEmpty()) {
                        photosByPlace[localId] = (localPhotos + missingPhotos).toMutableList()
                        android.util.Log.i(TAG, "mergeBackendTimeline: added ${missingPhotos.size} server photos to place $localId")
                    }
                    merged[localIdx] = local.copy(photos = photosByPlace[localId].orEmpty())
                }
            }
        }

        // Local places missing on the server → keep them and re-upload
        // (unless they matched a server place by name — the server already has them)
        val reuploaded = merged.filter { it.id !in serverPlaceIds && it.id !in nameMatchedLocalIds }
        reuploaded.forEach { requeuePending(it.id) }
        if (reuploaded.isNotEmpty()) {
            android.util.Log.i(TAG, "mergeBackendTimeline: ${reuploaded.size} local places missing on server, re-queued: ${reuploaded.map { it.id }}")
        }

        placesByTrip[trip.id] = merged
        android.util.Log.i(TAG, "mergeBackendTimeline: trip ${trip.id} now has ${merged.size} places: ${merged.map { "${it.id}('${it.name}')" }}")
        persist()
    }
}
