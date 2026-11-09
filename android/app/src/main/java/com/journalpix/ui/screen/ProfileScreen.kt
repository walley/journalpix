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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.journalpix.viewmodel.AppSession
import com.journalpix.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

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

  // Home address: picked on the big map → reverse-geocoded here
  var showHomeEditDialog by remember { mutableStateOf(false) }
  var homeEditText by remember { mutableStateOf("") }
  val scope = rememberCoroutineScope()

  LaunchedEffect(AppSession.pendingHomeLocation.value) {
    val loc = AppSession.pendingHomeLocation.value ?: return@LaunchedEffect
    Log.i(TAG_PROFILE, "Home location picked on map: lat=${loc.first} lon=${loc.second} — reverse geocoding")
    val result = com.journalpix.util.NominatimClient.reverseGeocode(loc.first, loc.second)
    val addr = result.getOrNull()
    Log.i(TAG_PROFILE, "Home reverse geocode result: success=${result.isSuccess}, addr='$addr', error=${result.exceptionOrNull()?.message}")
    // Clear only AFTER the geocode finished — clearing first would change the
    // LaunchedEffect key and cancel this very coroutine
    AppSession.pendingHomeLocation.value = null
    if (!addr.isNullOrBlank()) {
      // Transfer the resolved address into the edit input box for confirmation
      homeEditText = addr
      showHomeEditDialog = true
    } else {
      android.widget.Toast.makeText(context, "Could not resolve address — enter it manually", android.widget.Toast.LENGTH_LONG).show()
      homeEditText = ""
      showHomeEditDialog = true
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
     Spacer(Modifier.height(32.dp))
     // Home address: "🏠 Home: <address> (map)(pen)"
     Row(
       verticalAlignment = Alignment.CenterVertically,
       modifier = Modifier.fillMaxWidth()
     ) {
       Text(
         text = "🏠 Home: ${AppSession.homeAddress.value?.takeIf { it.isNotBlank() } ?: "Not set"}",
         style = MaterialTheme.typography.bodyLarge,
         modifier = Modifier.weight(1f)
       )
       IconButton(onClick = {
         Log.i(TAG_PROFILE, "Home map icon tapped — opening big map to pick home location")
         AppSession.mapPickHome.value = true
         AppSession.mapEditPlaceId.value = null
         AppSession.mapGeoJson.value = null
         navController.navigate("map/home")
       }) {
         Icon(Icons.Default.Map, contentDescription = "Pick home on map")
       }
       IconButton(onClick = {
         Log.i(TAG_PROFILE, "Home pen icon tapped — manual edit")
         homeEditText = AppSession.homeAddress.value ?: ""
         showHomeEditDialog = true
       }) {
         Icon(Icons.Default.Edit, contentDescription = "Edit home address")
       }
     }
  }

  if (showHomeEditDialog) {
    AlertDialog(
      onDismissRequest = { showHomeEditDialog = false },
      title = { Text("Home address") },
      text = {
        OutlinedTextField(
          value = homeEditText,
          onValueChange = { homeEditText = it },
          label = { Text("Address") },
          singleLine = false,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        TextButton(onClick = {
          AppSession.saveHomeAddress(context, homeEditText.trim())
          Log.i(TAG_PROFILE, "Home address edited manually: '${homeEditText.trim()}'")
          showHomeEditDialog = false
        }) { Text("Save") }
      },
      dismissButton = {
        TextButton(onClick = { showHomeEditDialog = false }) { Text("Cancel") }
      }
    )
  }
}
