package com.journalpix.api

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.journalpix.models.*
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/** Public Apache/reverse-proxy location of the backend. */
const val PUBLIC_BASE_URL = "https://walley.org/journalpix"

interface ApiClient {
    // Authentication
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<LoginResponse>
    
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResponse>
    
    @GET("auth/me")
    suspend fun getCurrentUser(@Header("Authorization") token: String): ApiResponse<UserResponse>
    
    // Trips
    @GET("trips")
    suspend fun getTrips(@Header("Authorization") token: String): ApiResponse<List<TripResponse>>
    
    @POST("trips")
    suspend fun createTrip(
        @Header("Authorization") token: String,
        @Body request: CreateTripRequest
    ): ApiResponse<TripResponse>
    
    @GET("trips/{tripId}")
    suspend fun getTrip(
        @Header("Authorization") token: String,
        @Path("tripId") tripId: String
    ): ApiResponse<TripResponse>
    
    @PUT("trips/{tripId}")
    suspend fun updateTrip(
        @Header("Authorization") token: String,
        @Path("tripId") tripId: String,
        @Body request: UpdateTripRequest
    ): ApiResponse<TripResponse>
    
    @DELETE("trips/{tripId}")
    suspend fun deleteTrip(
        @Header("Authorization") token: String,
        @Path("tripId") tripId: String
    ): ApiResponse<String>
    
    // Places
    @GET("trips/{tripId}/places")
    suspend fun getPlaces(
        @Header("Authorization") token: String,
        @Path("tripId") tripId: String
    ): ApiResponse<List<PlaceResponse>>
    
    @POST("trips/{tripId}/places")
    suspend fun createPlace(
        @Header("Authorization") token: String,
        @Path("tripId") tripId: String,
        @Body request: CreatePlaceRequest
    ): ApiResponse<PlaceResponse>
    
    @GET("places/{placeId}")
    suspend fun getPlace(
        @Header("Authorization") token: String,
        @Path("placeId") placeId: String
    ): ApiResponse<PlaceWithPhotos>
    
    @PUT("places/{placeId}")
    suspend fun updatePlace(
        @Header("Authorization") token: String,
        @Path("placeId") placeId: String,
        @Body request: UpdatePlaceRequest
    ): ApiResponse<PlaceResponse>
    
    @DELETE("places/{placeId}")
    suspend fun deletePlace(
        @Header("Authorization") token: String,
        @Path("placeId") placeId: String
    ): ApiResponse<String>
    
    // Photos
    @GET("places/{placeId}/photos")
    suspend fun getPhotosForPlace(
        @Header("Authorization") token: String,
        @Path("placeId") placeId: String
    ): ApiResponse<List<PhotoResponse>>
    
    @Multipart
    @POST("places/{placeId}/photos")
    suspend fun uploadPhoto(
        @Header("Authorization") token: String,
        @Path("placeId") placeId: String,
        @Part file: MultipartBody.Part
    ): ApiResponse<PhotoResponse>
    
    @GET("photos/{photoId}")
    suspend fun getPhoto(
        @Header("Authorization") token: String,
        @Path("photoId") photoId: String
    ): ApiResponse<PhotoResponse>
    
    @PUT("photos/{photoId}")
    suspend fun updatePhoto(
        @Header("Authorization") token: String,
        @Path("photoId") photoId: String,
        @Body request: UpdatePhotoRequest
    ): ApiResponse<PhotoResponse>
    
    @DELETE("photos/{photoId}")
    suspend fun deletePhoto(
        @Header("Authorization") token: String,
        @Path("photoId") photoId: String
    ): ApiResponse<String>
    
    // Timeline
    @GET("trips/{tripId}/timeline")
    suspend fun getTimeline(
        @Header("Authorization") token: String,
        @Path("tripId") tripId: String
    ): ApiResponse<TripTimeline>
}

class ApiClientBuilder(private val baseUrl: String) {
    
    fun build(): ApiClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val gson = GsonBuilder()
            // Backend JSON uses snake_case (trip_id, created_at, ...), the
            // Kotlin models use camelCase.
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        return retrofit.create(ApiClient::class.java)
    }
}

object ApiService {
    // Public backend: Apache on walley.org reverse-proxies /journalpix to the
    // loopback-only Rust service.
    private const val BASE_URL = "$PUBLIC_BASE_URL/api/"
    
    val apiClient: ApiClient by lazy {
        ApiClientBuilder(BASE_URL).build()
    }

    /**
     * Backend responses contain server-side paths (e.g. "./thumbnails/x.jpg").
     * Turn those into public HTTPS URLs; leave already-absolute/offline URIs
     * untouched.
     */
    fun thumbnailUrl(path: String): String = publicFileUrl("thumbnails", path)

    fun uploadUrl(path: String): String = publicFileUrl("uploads", path)

    private fun publicFileUrl(kind: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://") ||
            path.startsWith("content://") || path.startsWith("file://")
        ) {
            return path
        }
        return "$PUBLIC_BASE_URL/$kind/${path.substringAfterLast('/')}"
    }
}
