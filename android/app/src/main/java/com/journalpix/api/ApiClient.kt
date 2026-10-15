package com.journalpix.api

import com.journalpix.models.*
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

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
        
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        return retrofit.create(ApiClient::class.java)
    }
}

object ApiService {
    private const val BASE_URL = "http://10.0.2.2:8080/api/"
    
    val apiClient: ApiClient by lazy {
        ApiClientBuilder(BASE_URL).build()
    }
}
