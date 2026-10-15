package com.journalpix.repository

import androidx.room.*
import com.journalpix.models.User

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)
    
    @Update
    suspend fun update(user: User)
    
    @Delete
    suspend fun delete(user: User)
    
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: String): User?
    
    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getByEmail(email: String): User?
    
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): User?
    
    @Query("DELETE FROM users")
    suspend fun deleteAll()
}

class UserRepository(private val userDao: UserDao) {
    suspend fun insert(user: User) = userDao.insert(user)
    suspend fun update(user: User) = userDao.update(user)
    suspend fun delete(user: User) = userDao.delete(user)
    suspend fun getById(id: String) = userDao.getById(id)
    suspend fun getByEmail(email: String) = userDao.getByEmail(email)
    suspend fun getCurrentUser() = userDao.getCurrentUser()
    suspend fun deleteAll() = userDao.deleteAll()
}
