package com.journalpix.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Reverse geocoding via OpenStreetMap Nominatim (requires internet).
 * Policy: identify the app with a User-Agent and keep requests modest.
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
                        "?lat=$latitude&lon=$longitude&format=json&zoom=18&addressdetails=0"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "JournalPix/1.0 (offline-demo; contact: demo@journalpix.app)")
                    .header("Accept", "application/json")
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
                    val display = json.optString("display_name", "")
                    if (display.isBlank()) {
                        Result.failure(Exception("No address found"))
                    } else {
                        Result.success(display)
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
