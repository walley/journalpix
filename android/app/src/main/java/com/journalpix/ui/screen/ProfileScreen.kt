package com.journalpix.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.journalpix.viewmodel.AppSession
import com.journalpix.viewmodel.AuthViewModel

private const val TAG_PROFILE = "JPXPS"

@Composable
fun ProfileScreen(navController: NavController) {
  Log.i(TAG_PROFILE, "ProfileScreen: rendering, isLoggedIn=${AppSession.isLoggedIn.value}, user=${AppSession.currentUser.value?.user?.email}")
  val user = AppSession.currentUser.value

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    if (user != null) {
      Text("Signed in as", style = MaterialTheme.typography.bodyMedium)
      Text(user.user.name, style = MaterialTheme.typography.titleLarge)
      Text(user.user.email, style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.height(24.dp))
      Button(onClick = {
        Log.i(TAG_PROFILE, "Logout clicked")
        AppSession.clear()
        AppSession.currentTripId.value = ""
        navController.navigate("auth") {
          popUpTo(0) { inclusive = true }
        }
      }) {
        Text("Log out")
      }
    } else {
      Text("Not signed in", style = MaterialTheme.typography.titleLarge)
      Spacer(Modifier.height(24.dp))
      Button(onClick = {
        Log.i(TAG_PROFILE, "Login button clicked — opening auth screen")
        navController.navigate("auth")
      }) {
        Text("Log in")
      }
    }
  }
}
