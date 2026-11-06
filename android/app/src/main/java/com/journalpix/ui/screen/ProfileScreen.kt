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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.journalpix.viewmodel.AppSession
import com.journalpix.viewmodel.AuthViewModel

private const val TAG_PROFILE = "JPXPS"

@Composable
fun ProfileScreen(navController: NavController) {
  Log.i(TAG_PROFILE, "ProfileScreen: rendering, isLoggedIn=${AppSession.isLoggedIn.value}, user=${AppSession.currentUser.value?.user?.email}")
  val user = AppSession.currentUser.value
  val context = LocalContext.current

  val exportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip")
  ) { uri ->
    if (uri != null) {
      val ok = com.journalpix.data.ExportImport.exportToZip(context, uri)
      android.widget.Toast.makeText(context, if (ok) "Journal exported" else "Export failed", android.widget.Toast.LENGTH_LONG).show()
    }
  }
  val importLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      val ok = com.journalpix.data.ExportImport.importFromZip(context, uri)
      android.widget.Toast.makeText(context, if (ok) "Journal imported" else "Import failed", android.widget.Toast.LENGTH_LONG).show()
    }
  }

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
     Spacer(Modifier.height(32.dp))
     Button(onClick = {
       Log.i(TAG_PROFILE, "Export tapped — launching file picker")
       exportLauncher.launch("journalpix-export.zip")
     }) {
       Text("Export journal (ZIP)")
     }
     Spacer(Modifier.height(12.dp))
     Button(onClick = {
       Log.i(TAG_PROFILE, "Import tapped — launching file picker")
       importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
     }) {
       Text("Import journal (ZIP)")
     }
  }
}
