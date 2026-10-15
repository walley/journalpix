package com.journalpix.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalpix.api.ApiService
import com.journalpix.models.ApiResponse
import com.journalpix.models.LoginRequest
import com.journalpix.models.LoginResponse
import com.journalpix.models.RegisterRequest
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val isLoggedIn = mutableStateOf(false)
    val currentUser = mutableStateOf<LoginResponse?>(null)
    
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
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            
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
    
    fun logout() {
        currentUser.value = null
        isLoggedIn.value = false
    }
}
