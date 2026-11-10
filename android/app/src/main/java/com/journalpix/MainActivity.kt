package com.journalpix

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.journalpix.ui.map.MapGlScreen
import com.journalpix.ui.screen.AboutScreen
import com.journalpix.ui.screen.AuthScreen
import com.journalpix.ui.screen.HomeScreen
import com.journalpix.ui.screen.PlaceDetailScreen
import com.journalpix.ui.screen.TimelineScreen
import com.journalpix.ui.theme.JournalPixTheme
import com.journalpix.viewmodel.AppSession
import com.journalpix.viewmodel.PhotoViewModel
import com.journalpix.viewmodel.TripViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream

private const val TAG_MAIN = "JPXMA"

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private lateinit var photoViewModel: PhotoViewModel
    private lateinit var tripViewModel: TripViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        photoViewModel = ViewModelProvider(this)[PhotoViewModel::class.java]
        tripViewModel = ViewModelProvider(this)[TripViewModel::class.java]

        // Handle incoming share intent
        handleShareIntent(intent)

        setContent {
            JournalPixTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    // Track current trip for bottom nav destinations
                    if (currentRoute?.startsWith("timeline/") == true) {
                        AppSession.currentTripId.value =
                            navBackStackEntry?.arguments?.getString("tripId") ?: ""
                    }

                    Scaffold(
                        bottomBar = {
                            // Show nav bar only on real main destinations — currentRoute is null on first frame
                            if (currentRoute != null && currentRoute != "auth") {
                                // Floating rounded navigation bar near the very bottom
                                NavigationBar(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                        .clip(RoundedCornerShape(24.dp)),
                                    tonalElevation = 4.dp
                                ) {
                                    NavigationBarItem(
                                        selected = currentRoute == "home",
                                        onClick = {
                                            Log.i(TAG_MAIN, "Nav: home")
                                            navController.navigate("home") {
                                                popUpTo("home") { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        },
                                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                        label = { Text("Home") }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == "profile",
                                        onClick = {
                                            Log.i(TAG_MAIN, "Nav: profile (user) — screen not implemented yet")
                                            navController.navigate("profile")
                                        },
                                        icon = { Icon(Icons.Default.Person, contentDescription = "User") },
                                        label = { Text("User") }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == "settings",
                                        onClick = {
                                            Log.i(TAG_MAIN, "Nav: settings — screen not implemented yet")
                                            navController.navigate("settings")
                                        },
                                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                        label = { Text("Settings") }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == "about",
                                        onClick = {
                                            Log.i(TAG_MAIN, "Nav: about")
                                            navController.navigate("about")
                                        },
                                        icon = { Icon(Icons.Default.Info, contentDescription = "About") },
                                        label = { Text("About") }
                                    )
                                }
                            }
                        }
                    ) { paddingValues ->
                    NavHost(
                        navController = navController,
                        // App starts on trip selection (Home); login lives in the Profile tab
                        startDestination = "home",
                        modifier = Modifier.padding(paddingValues)
                    ) {
                        composable("auth") { AuthScreen(navController) }
                        composable("home") { HomeScreen(navController) }
                        composable("timeline/{tripId}") { backStackEntry ->
                            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
                            TimelineScreen(navController, tripId)
                        }
                        composable(
                            "map/{tripId}",
                            arguments = listOf(
                                navArgument("addPlaceMode") { defaultValue = false },
                                navArgument("placeIdForUpdate") { defaultValue = "" }
                            )
                        ) { backStackEntry ->
                            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
                            val addPlaceMode = backStackEntry.arguments?.getBoolean("addPlaceMode") ?: false
                            val placeIdForUpdate = backStackEntry.arguments?.getString("placeIdForUpdate") ?: ""
                            MapGlScreen(
                                navController,
                                tripId = tripId,
                                addPlaceMode = addPlaceMode,
                                placeIdForUpdate = placeIdForUpdate,
                                onPlaceAdded = { lat, lon, name ->
                                    Log.i(TAG_MAIN, "Map place added: tripId=$tripId, lat=$lat, lon=$lon, name='$name', placeIdForUpdate=$placeIdForUpdate")
                                    if (placeIdForUpdate.isNotBlank()) {
                                        AppSession.pendingMapPlaceUpdate.value = Triple(lat, lon, name)
                                    } else {
                                        AppSession.pendingMapPlace.value = Triple(lat, lon, name)
                                    }
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable(
                            "place/{placeId}",
                            arguments = listOf(
                                navArgument("tripId") { defaultValue = "" }
                            )
                        ) { backStackEntry ->
                            val placeId = backStackEntry.arguments?.getString("placeId") ?: ""
                            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
                            PlaceDetailScreen(
                                navController,
                                placeId = placeId,
                                tripId = tripId
                            )
                        }
                        composable("about") { AboutScreen(navController) }
                        composable("profile") {
                            Log.i(TAG_MAIN, "Profile screen")
                            com.journalpix.ui.screen.ProfileScreen(navController)
                        }
                        composable("settings") {
                            Log.i(TAG_MAIN, "Settings screen: native preferences")
                            com.journalpix.ui.screen.SettingsScreen(navController)
                        }
                    }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        if (Intent.ACTION_SEND == intent.action || Intent.ACTION_SEND_MULTIPLE == intent.action) {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    uri?.let { processImageUri(it) }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    uris?.let { list ->
                        list.forEach { uri ->
                            processImageUri(uri)
                        }
                    }
                }
            }
        }
    }

    private fun processImageUri(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                bitmap?.let { bmp ->
                    // Offline-first share: store the photo locally in the first trip.
                    // No token here — SyncManager handles any server upload.
                    val trips = com.journalpix.data.OfflineStore.allTrips()
                    if (trips.isNotEmpty()) {
                        val firstTrip = trips.first()
                        val photo = com.journalpix.data.OfflineStore.addUnassignedPhoto(
                            uriString = uri.toString(), latitude = null, longitude = null
                        )
                        Log.i(TAG_MAIN, "Share intent: stored photo ${photo.id} locally (trip ${firstTrip.id})")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Photo saved to journal", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
