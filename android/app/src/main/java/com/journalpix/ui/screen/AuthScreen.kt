package com.journalpix.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.journalpix.viewmodel.AppSession
import com.journalpix.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(navController: NavController) {
    val authViewModel: AuthViewModel = remember { AuthViewModel() }
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf(AuthViewModel.DEMO_EMAIL) }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf(AuthViewModel.DEMO_PASSWORD) }

    fun goHome() {
        navController.navigate("home") {
            popUpTo("auth") { inclusive = true }
        }
    }

    // Already logged in (e.g. demo session)
    LaunchedEffect(AppSession.isLoggedIn.value) {
        if (AppSession.isLoggedIn.value) {
            goHome()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isLogin) "Login" else "Register",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Dev build: demo / demo works offline",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!isLogin) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (authViewModel.isLoading.value) {
            CircularProgressIndicator()
        } else if (isLogin) {
            // Primary action: always offline demo — never touches the network
            Button(
                onClick = {
                    authViewModel.errorMessage.value = null
                    authViewModel.loginAsDemo { goHome() }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login (demo / offline)")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real login against https://walley.org/journalpix/api/.
            OutlinedButton(
                onClick = {
                    authViewModel.loginWithBackend(email, password) { goHome() }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login via backend")
            }
        } else {
            Button(
                onClick = {
                    authViewModel.register(email, name, password) { goHome() }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Register")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = { isLogin = !isLogin }
        ) {
            Text(if (isLogin) "Don't have an account? Register" else "Already have an account? Login")
        }

        authViewModel.errorMessage.value?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            AlertDialog(
                onDismissRequest = { authViewModel.errorMessage.value = null },
                title = { Text("Error") },
                text = { Text(error) },
                confirmButton = {
                    Button(onClick = { authViewModel.errorMessage.value = null }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
