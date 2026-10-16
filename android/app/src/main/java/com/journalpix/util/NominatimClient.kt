package com.journalpix.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Reverse geocoding via OpenStreetMap Nominatim (requires internet).
 * Requests English names via Accept-Language + namedetails.
 */
object NominatimClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val url =
                    "https://nominatim.openstreetmap.org/reverse" +
                        "?lat=$latitude&lon=$longitude" +
                        "&format=json" +
                        "&zoom=18" +
                        "&addressdetails=1" +
                        "&namedetails=1" +
                        "&accept-language=en"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "JournalPix/1.0 (offline-demo; contact: demo@journalpix.app)")
                    .header("Accept", "application/json")
                    // Prefer English name tags (name:en) when available
                    .header("Accept-Language", "en")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("Nominatim HTTP ${response.code}")
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val address = pickEnglishName(json)
                    if (address.isBlank()) {
                        Result.failure(Exception("No address found"))
                    } else {
                        Result.success(address)
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Prefer English labels from namedetails / address, then fall back to display_name.
     */
    private fun pickEnglishName(json: JSONObject): String {
        // namedetails may include name:en, name, etc.
        val namedetails = json.optJSONObject("namedetails")
        if (namedetails != null) {
            val en = namedetails.optString("name:en", "").trim()
            if (en.isNotEmpty()) {
                // Combine with higher-level English place context from display_name when useful
                val display = json.optString("display_name", "").trim()
                return if (display.isNotEmpty() && !display.startsWith(en)) {
                    // Keep full reverse-geocode line (already language-biased via accept-language)
                    display
                } else {
                    en.ifEmpty { display }
                }
            }
            val localName = namedetails.optString("name", "").trim()
            if (localName.isNotEmpty()) {
                val display = json.optString("display_name", "").trim()
                if (display.isNotEmpty()) return display
                return localName
            }
        }

        // display_name is already biased toward English when Accept-Language: en is set
        val display = json.optString("display_name", "").trim()
        if (display.isNotEmpty()) return display

        // Last resort: build from address parts
        val address = json.optJSONObject("address") ?: return ""
        val parts = listOf(
            "attraction", "tourism", "building", "road", "neighbourhood",
            "suburb", "village", "town", "city", "municipality",
            "county", "state", "country"
        ).mapNotNull { key ->
            address.optString(key, "").trim().takeIf { it.isNotEmpty() }
        }
        return parts.distinct().joinToString(", ")
    }
}
