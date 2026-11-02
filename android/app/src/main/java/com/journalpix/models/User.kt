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
    /** IANA zone used as the default when a record carries none of its own. */
    val timezone: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// API Response models
data class UserResponse(
    val id: String,
    val email: String,
    val name: String,
    /** IANA zone name, or null when the user has not set one. */
    val timezone: String? = null,
    val createdAt: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val name: String,
    val password: String,
    /** Optional; falls back to UTC on the server when absent. */
    val timezone: String? = null
)

data class UpdateUserRequest(
    val name: String? = null,
    val email: String? = null,
    val password: String? = null,
    /** null clears the zone (back to the server default). */
    val timezone: String? = null
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
