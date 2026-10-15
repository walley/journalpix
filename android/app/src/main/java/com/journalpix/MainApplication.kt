package com.journalpix

import android.app.Application
import com.journalpix.repository.AppDatabase
import com.journalpix.repository.PhotoRepository
import com.journalpix.repository.PlaceRepository
import com.journalpix.repository.TripRepository
import com.journalpix.repository.UserRepository

class MainApplication : Application() {
    
    val database by lazy { AppDatabase.getDatabase(this) }
    
    val userRepository by lazy { UserRepository(database.userDao()) }
    val tripRepository by lazy { TripRepository(database.tripDao()) }
    val placeRepository by lazy { PlaceRepository(database.placeDao()) }
    val photoRepository by lazy { PhotoRepository(database.photoDao()) }
    
    override fun onCreate() {
        super.onCreate()
        // Initialize any app-wide dependencies here
    }
}
