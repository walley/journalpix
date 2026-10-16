package com.journalpix.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.api.ApiService
import com.journalpix.models.LoginRequest
import com.journalpix.models.LoginResponse
import com.journalpix.models.RegisterRequest
import com.journalpix.models.UserResponse
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val isLoggedIn = mutableStateOf(false)
    val currentUser = mutableStateOf<LoginResponse?>(null)

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
                        currentUser.value = loginResponse
                        isLoggedIn.value = true
                        onSuccess()
                    }
                } else {
                    errorMessage.value = response.error ?: "Registration failed"
                }
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        errorMessage.value = null

        // Fully offline: resolve demo:demo BEFORE any coroutine or network call
        if (isDemoCredentials(email, password)) {
            currentUser.value = demoLoginResponse()
            isLoggedIn.value = true
            isLoading.value = false
            onSuccess()
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            try {
                val request = LoginRequest(email, password)
                val response = ApiService.apiClient.login(request)

                if (response.success) {
                    response.data?.let { loginResponse ->
                        currentUser.value = loginResponse
                        isLoggedIn.value = true
                        onSuccess()
                    }
                } else {
                    errorMessage.value = response.error ?: "Login failed"
                }
            } catch (e: Exception) {
                errorMessage.value = "Network error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loginAsDemo(onSuccess: () -> Unit) {
        // No network, no coroutine
        errorMessage.value = null
        isLoading.value = false
        currentUser.value = demoLoginResponse()
        isLoggedIn.value = true
        onSuccess()
    }

    fun logout() {
        currentUser.value = null
        isLoggedIn.value = false
    }
}
