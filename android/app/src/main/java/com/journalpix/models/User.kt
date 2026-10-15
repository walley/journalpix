package com.journalpix.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val email: String,
    val name: String,
    val token: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// API Response models
data class UserResponse(
    val id: String,
    val email: String,
    val name: String,
    val createdAt: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val name: String,
    val password: String
)

data class LoginResponse(
    val user: UserResponse,
    val token: String
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null
)
