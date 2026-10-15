package com.journalpix.repository

import androidx.room.*
import com.journalpix.models.Photo

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: Photo)
    
    @Update
    suspend fun update(photo: Photo)
    
    @Delete
    suspend fun delete(photo: Photo)
    
    @Query("SELECT * FROM photos WHERE placeId = :placeId")
    suspend fun getByPlaceId(placeId: String): List<Photo>
    
    @Query("SELECT * FROM photos WHERE userId = :userId")
    suspend fun getByUserId(userId: String): List<Photo>
    
    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: String): Photo?
    
    @Query("SELECT * FROM photos WHERE placeId IS NULL AND userId = :userId")
    suspend fun getUnassignedByUserId(userId: String): List<Photo>
    
    @Query("DELETE FROM photos WHERE placeId = :placeId")
    suspend fun deleteByPlaceId(placeId: String)
    
    @Query("DELETE FROM photos WHERE userId = :userId")
    suspend fun deleteByUserId(userId: String)
    
    @Query("DELETE FROM photos")
    suspend fun deleteAll()
}

class PhotoRepository(private val photoDao: PhotoDao) {
    suspend fun insert(photo: Photo) = photoDao.insert(photo)
    suspend fun update(photo: Photo) = photoDao.update(photo)
    suspend fun delete(photo: Photo) = photoDao.delete(photo)
    suspend fun getByPlaceId(placeId: String) = photoDao.getByPlaceId(placeId)
    suspend fun getByUserId(userId: String) = photoDao.getByUserId(userId)
    suspend fun getById(id: String) = photoDao.getById(id)
    suspend fun getUnassignedByUserId(userId: String) = photoDao.getUnassignedByUserId(userId)
    suspend fun deleteByPlaceId(placeId: String) = photoDao.deleteByPlaceId(placeId)
    suspend fun deleteByUserId(userId: String) = photoDao.deleteByUserId(userId)
    suspend fun deleteAll() = photoDao.deleteAll()
}
