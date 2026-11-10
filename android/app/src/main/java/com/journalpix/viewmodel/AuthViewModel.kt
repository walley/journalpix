package com.journalpix.viewmodel

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.api.ApiService
import com.journalpix.models.LoginRequest
import com.journalpix.models.LoginResponse
import com.journalpix.models.RegisterRequest
import com.journalpix.models.UserResponse
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

/**
 * Simple process-wide session so every screen sees the same login state.
 * (Avoids losing the user when each screen creates its own AuthViewModel.)
 */
object AppSession {
    private const val TAG = "JPXAV"

    val currentUser = mutableStateOf<LoginResponse?>(null)
    val isLoggedIn = mutableStateOf(false)

    /** Error shown when currentTripId is blank (such a trip is invalid). */
    val tripIdError = mutableStateOf<String?>(null)

    /** Place pending creation from map selection: lat, lon, name. */
    val pendingMapPlace = mutableStateOf<Triple<Double, Double, String>?>(null)

    /**
     * Bumped by OfflineStore on every local mutation (persist). Screens that list data
     * (e.g. the timeline) observe this and reload — they never mutate places themselves.
     */
    val placesVersion = mutableStateOf(0)

    fun notifyDataChanged() {
        placesVersion.value = placesVersion.value + 1
    }

    /** Place pending coordinate/address update from map selection: lat, lon, name. */
    val pendingMapPlaceUpdate = mutableStateOf<Triple<Double, Double, String>?>(null)

    /** Place ID being edited on the map (set before navigating to map screen). */
    val mapEditPlaceId = mutableStateOf<String?>(null)

    /** GeoJSON FeatureCollection passed from a view to the big map (consumed by MapScreen). */
    val mapGeoJson = mutableStateOf<String?>(null)

    /** Home address (persisted in SharedPreferences), shown/editable on the Profile page. */
    val homeAddress = mutableStateOf<String?>(null)

    /** When set, the big map is in "pick home location" mode: checkmark returns lat/lon here. */
    val mapPickHome = mutableStateOf(false)

    /** Lat/lon picked on the big map for the home address. */
    val pendingHomeLocation = mutableStateOf<Pair<Double, Double>?>(null)

    /** A place named "Home"/"home" uses the saved home address. */
    fun isHomePlace(name: String?): Boolean =
        name?.trim()?.matches(Regex("[Hh]ome")) == true

    /** Address to display for a place: home address for Home places, otherwise the place's own. */
    fun effectiveAddress(placeName: String?, placeAddress: String?): String? =
        if (isHomePlace(placeName)) homeAddress.value?.takeIf { it.isNotBlank() } ?: placeAddress
        else placeAddress

    fun loadHomeAddress(context: android.content.Context) {
        homeAddress.value = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .getString("home_address", null)
    }

    fun saveHomeAddress(context: android.content.Context, address: String) {
        homeAddress.value = address
        context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("home_address", address).apply()
    }

    /** Currently open trip — used by the bottom nav bar for timeline/map destinations. */
    val currentTripId = mutableStateOf("")

    /** Snapshot of the place row that was clicked, used as offline fallback in place detail. */
    val openedPlaceSnapshot = mutableStateOf<com.journalpix.models.PlaceWithPhotos?>(null)

    fun setUser(user: LoginResponse?) {
        currentUser.value = user
        isLoggedIn.value = user != null
    }

    fun clear() {
        currentUser.value = null
        isLoggedIn.value = false
    }

    fun clearPendingMapPlace() {
        pendingMapPlace.value = null
    }

    fun clearPendingMapPlaceUpdate() {
        pendingMapPlaceUpdate.value = null
    }

    /**
     * currentTripId must never be blank — a blank trip id means an invalid trip.
     * Returns the id when valid, or null (and sets [tripIdError]) when blank.
     */
    fun validateTripId(tripId: String): String? {
        Log.i(TAG, "validateTripId: called with tripId='$tripId'")
        return if (tripId.isBlank()) {
            Log.e(TAG, "validateTripId: currentTripId is BLANK — trip is invalid and must be destroyed")
            tripIdError.value = "No trip selected — open a trip from Home first"
            null
        } else {
            tripIdError.value = null
            tripId
        }
    }
}

class AuthViewModel : ViewModel() {
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    // Bound to shared session
    val isLoggedIn get() = AppSession.isLoggedIn
    val currentUser get() = AppSession.currentUser

    /** Prefer the backend's JSON error message over a raw Retrofit message. */
    private fun errorText(e: Exception, fallback: String): String {
        if (e is HttpException) {
            val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            val serverError = raw?.let {
                runCatching { JSONObject(it).optString("error") }.getOrNull()
            }
            return if (!serverError.isNullOrBlank()) serverError
            else "$fallback (HTTP ${e.code()})"
        }
        return "Network error: ${e.message ?: e.javaClass.simpleName}"
    }

    companion object {
        // Temporary dev/demo credentials — remove before production
        const val DEMO_EMAIL = "demo"
        const val DEMO_PASSWORD = "demo"
        const val DEMO_TOKEN = "demo-dev-token"

        fun isDemoCredentials(email: String, password: String): Boolean {
            val e = email.trim().lowercase()
            val p = password.trim()
            return (e == DEMO_EMAIL || e == "demo@demo.local" || e == "demo@journalpix.app") &&
                p == DEMO_PASSWORD
        }

        fun isDemoToken(token: String?): Boolean {
            if (token.isNullOrBlank()) return false
            val t = token.removePrefix("Bearer ").trim()
            return t == DEMO_TOKEN
        }

        fun demoLoginResponse(): LoginResponse {
            return LoginResponse(
                user = UserResponse(
                    id = "00000000-0000-0000-0000-000000000001",
                    email = "demo@journalpix.app",
                    name = "Demo User",
                    createdAt = "2024-01-01T00:00:00Z"
                ),
                token = DEMO_TOKEN
            )
        }
    }

    fun register(email: String, name: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                val request = RegisterRequest(email, name, password)
                val response = ApiService.apiClient.register(request)

                if (response.success) {
                    response.data?.let { loginResponse ->
                        AppSession.setUser(loginResponse)
                        onSuccess()
                    }
                } else {
                    errorMessage.value = response.error ?: "Registration failed"
                }
            } catch (e: Exception) {
                errorMessage.value = errorText(e, "Registration failed")
            } finally {
                isLoading.value = false
            }
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        errorMessage.value = null

        // Fully offline: resolve demo:demo BEFORE any coroutine or network call
        if (isDemoCredentials(email, password)) {
            isLoading.value = false
            AppSession.setUser(demoLoginResponse())
            onSuccess()
            return
        }

        loginWithBackend(email, password, onSuccess)
    }

    /**
     * Real login against the HTTPS backend. Unlike [login], this never takes
     * the offline demo shortcut, so the "Login via backend" button exercises
     * the real API even when demo credentials are still in the fields.
     */
    fun loginWithBackend(email: String, password: String, onSuccess: () -> Unit) {
        errorMessage.value = null

        if (email.isBlank() || password.isBlank()) {
            errorMessage.value = "Enter your email and password"
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            try {
                val request = LoginRequest(email.trim(), password)
                val response = ApiService.apiClient.login(request)

                if (response.success) {
                    val loginResponse = response.data
                    if (loginResponse != null) {
                        AppSession.setUser(loginResponse)
                        onSuccess()
                    } else {
                        errorMessage.value = "Login failed: empty response"
                    }
                } else {
                    errorMessage.value = response.error ?: "Login failed"
                }
            } catch (e: Exception) {
                errorMessage.value = errorText(e, "Login failed")
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loginAsDemo(onSuccess: () -> Unit) {
        // No network, no coroutine
        errorMessage.value = null
        isLoading.value = false
        AppSession.setUser(demoLoginResponse())
        onSuccess()
    }

    fun logout() {
        AppSession.clear()
    }
}
