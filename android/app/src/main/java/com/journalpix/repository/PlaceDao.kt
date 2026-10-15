package com.journalpix.repository

import androidx.room.*
import com.journalpix.models.Place

@Dao
interface PlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: Place)
    
    @Update
    suspend fun update(place: Place)
    
    @Delete
    suspend fun delete(place: Place)
    
    @Query("SELECT * FROM places WHERE tripId = :tripId")
    suspend fun getByTripId(tripId: String): List<Place>
    
    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getById(id: String): Place?
    
    @Query("SELECT * FROM places WHERE tripId = :tripId ORDER BY orderIndex")
    suspend fun getByTripIdOrdered(tripId: String): List<Place>
    
    @Query("DELETE FROM places WHERE tripId = :tripId")
    suspend fun deleteByTripId(tripId: String)
    
    @Query("DELETE FROM places")
    suspend fun deleteAll()
}

class PlaceRepository(private val placeDao: PlaceDao) {
    suspend fun insert(place: Place) = placeDao.insert(place)
    suspend fun update(place: Place) = placeDao.update(place)
    suspend fun delete(place: Place) = placeDao.delete(place)
    suspend fun getByTripId(tripId: String) = placeDao.getByTripId(tripId)
    suspend fun getById(id: String) = placeDao.getById(id)
    suspend fun getByTripIdOrdered(tripId: String) = placeDao.getByTripIdOrdered(tripId)
    suspend fun deleteByTripId(tripId: String) = placeDao.deleteByTripId(tripId)
    suspend fun deleteAll() = placeDao.deleteAll()
}
