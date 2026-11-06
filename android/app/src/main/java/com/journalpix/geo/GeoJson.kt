package com.journalpix.geo

import android.util.Log
import com.journalpix.models.PlaceWithPhotos
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal GeoJSON (RFC 7946) support used to pass spatial data from views to the big map.
 * Supports FeatureCollection with Point, LineString and Polygon geometries.
 */
object GeoJson {
    private const val TAG = "JPXGJ"

    sealed class Geometry {
        data class Point(val longitude: Double, val latitude: Double) : Geometry()
        data class LineString(val coordinates: List<Pair<Double, Double>>) : Geometry() // (lon, lat)
        data class Polygon(val rings: List<List<Pair<Double, Double>>>) : Geometry()
    }

    data class Feature(
        val geometry: Geometry,
        val properties: Map<String, String> = emptyMap()
    )

    data class FeatureCollection(val features: List<Feature>) {
        fun toJson(): String = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray().also { arr ->
                features.forEach { f -> arr.put(featureToJson(f)) }
            })
        }.toString()
    }

    /** GeoJSON of a single place as a Point feature (properties carry id/name). */
    fun forPlace(place: PlaceWithPhotos): String {
        Log.i(TAG, "forPlace: placeId=${place.id}, name='${place.name}', lat=${place.latitude}, lon=${place.longitude}")
        val features = mutableListOf<Feature>()
        if (place.latitude != null && place.longitude != null) {
            features.add(
                Feature(
                    geometry = Geometry.Point(place.longitude!!, place.latitude!!),
                    properties = mapOf(
                        "id" to place.id,
                        "name" to place.name,
                        "kind" to "place"
                    )
                )
            )
        }
        return FeatureCollection(features).toJson()
    }

    /** GeoJSON of a trip route: one LineString through all places that have coordinates, in order. */
    fun forTripRoute(places: List<PlaceWithPhotos>): String {
        Log.i(TAG, "forTripRoute: ${places.size} places")
        val coords = places
            .filter { it.latitude != null && it.longitude != null }
            .map { Pair(it.longitude!!, it.latitude!!) } // GeoJSON order: (lon, lat)
        val features = if (coords.size >= 2) {
            listOf(
                Feature(
                    geometry = Geometry.LineString(coords),
                    properties = mapOf("kind" to "tripRoute")
                )
            )
        } else emptyList()
        return FeatureCollection(features).toJson()
    }

    fun parse(json: String): FeatureCollection? {
        return try {
            val root = JSONObject(json)
            if (root.optString("type") != "FeatureCollection") {
                Log.e(TAG, "parse: not a FeatureCollection")
                return null
            }
            val arr = root.optJSONArray("features") ?: return FeatureCollection(emptyList())
            val features = (0 until arr.length()).mapNotNull { i ->
                runCatching { featureFromJson(arr.getJSONObject(i)) }.getOrNull()
            }
            Log.i(TAG, "parse: ${features.size} features")
            FeatureCollection(features)
        } catch (e: Exception) {
            Log.e(TAG, "parse: failed: ${e.message}", e)
            null
        }
    }

    private fun featureToJson(f: Feature): JSONObject = JSONObject().apply {
        put("type", "Feature")
        put("geometry", geometryToJson(f.geometry))
        put("properties", JSONObject().also { p ->
            f.properties.forEach { (k, v) -> p.put(k, v) }
        })
    }

    private fun geometryToJson(g: Geometry): JSONObject = when (g) {
        is Geometry.Point -> JSONObject().apply {
            put("type", "Point")
            put("coordinates", JSONArray().put(g.longitude).put(g.latitude))
        }
        is Geometry.LineString -> JSONObject().apply {
            put("type", "LineString")
            put("coordinates", coordsToJson(g.coordinates))
        }
        is Geometry.Polygon -> JSONObject().apply {
            put("type", "Polygon")
            put("coordinates", JSONArray().also { arr ->
                g.rings.forEach { ring -> arr.put(coordsToJson(ring)) }
            })
        }
    }

    private fun coordsToJson(coords: List<Pair<Double, Double>>): JSONArray =
        JSONArray().also { arr -> coords.forEach { (lon, lat) -> arr.put(JSONArray().put(lon).put(lat)) } }

    private fun featureFromJson(o: JSONObject): Feature {
        val geom = o.getJSONObject("geometry")
        val geometry = when (geom.getString("type")) {
            "Point" -> {
                val c = geom.getJSONArray("coordinates")
                Geometry.Point(c.getDouble(0), c.getDouble(1))
            }
            "LineString" -> Geometry.LineString(coordsFromJson(geom.getJSONArray("coordinates")))
            "Polygon" -> {
                val ringsArr = geom.getJSONArray("coordinates")
                Geometry.Polygon((0 until ringsArr.length()).map { coordsFromJson(ringsArr.getJSONArray(it)) })
            }
            else -> throw IllegalArgumentException("Unsupported geometry: ${geom.getString("type")}")
        }
        val props = mutableMapOf<String, String>()
        o.optJSONObject("properties")?.let { p ->
            p.keys().forEach { k -> props[k] = p.optString(k, "") }
        }
        return Feature(geometry, props)
    }

    private fun coordsFromJson(arr: JSONArray): List<Pair<Double, Double>> =
        (0 until arr.length()).map { i ->
            val c = arr.getJSONArray(i)
            Pair(c.getDouble(0), c.getDouble(1))
        }
}
