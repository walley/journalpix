package com.journalpix.repository

import androidx.room.*
import com.journalpix.models.Trip
import com.journalpix.models.TripStatus

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: Trip)
    
    @Update
    suspend fun update(trip: Trip)
    
    @Delete
    suspend fun delete(trip: Trip)
    
    @Query("SELECT * FROM trips WHERE userId = :userId")
    suspend fun getByUserId(userId: String): List<Trip>
    
    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: String): Trip?
    
    @Query("SELECT * FROM trips WHERE status = :status AND userId = :userId")
    suspend fun getByStatus(userId: String, status: TripStatus): List<Trip>
    
    @Query("DELETE FROM trips WHERE userId = :userId")
    suspend fun deleteByUserId(userId: String)
    
    @Query("DELETE FROM trips")
    suspend fun deleteAll()
}

class TripRepository(private val tripDao: TripDao) {
    suspend fun insert(trip: Trip) = tripDao.insert(trip)
    suspend fun update(trip: Trip) = tripDao.update(trip)
    suspend fun delete(trip: Trip) = tripDao.delete(trip)
    suspend fun getByUserId(userId: String) = tripDao.getByUserId(userId)
    suspend fun getById(id: String) = tripDao.getById(id)
    suspend fun getByStatus(userId: String, status: TripStatus) = tripDao.getByStatus(userId, status)
    suspend fun deleteByUserId(userId: String) = tripDao.deleteByUserId(userId)
    suspend fun deleteAll() = tripDao.deleteAll()
}
