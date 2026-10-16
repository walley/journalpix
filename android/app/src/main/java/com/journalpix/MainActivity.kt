package com.journalpix

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.journalpix.ui.screen.AboutScreen
import com.journalpix.ui.screen.AuthScreen
import com.journalpix.ui.screen.HomeScreen
import com.journalpix.ui.screen.TimelineScreen
import com.journalpix.ui.map.MapScreen
import com.journalpix.ui.theme.JournalPixTheme
import com.journalpix.viewmodel.PhotoViewModel
import com.journalpix.viewmodel.TripViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream

class MainActivity : ComponentActivity() {
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
                    
                    NavHost(
                        navController = navController,
                        startDestination = "auth"
                    ) {
                        composable("auth") { AuthScreen(navController) }
                        composable("home") { HomeScreen(navController) }
                        composable("timeline/{tripId}") { backStackEntry ->
                            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
                            TimelineScreen(navController, tripId)
                        }
                        composable("map/{tripId}") { backStackEntry ->
                            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
                            MapScreen(navController, tripId)
                        }
                        composable("about") { AboutScreen(navController) }
                    }
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleShareIntent(it) }
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
                    // Get the current trip - for now use first trip or default
                    // In production, you might want to show a dialog to select which trip/place
                    // For now, we need a place - you might want to create a default place
                    // or let user select. Using empty string as placeholder.
                    // In a real app, you'd have proper auth and trip/place selection
                    // This is a simplified version - you should get the token from your auth state
                    val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    val token = sharedPrefs.getString("auth_token", null)
                    
                    if (token != null) {
                        tripViewModel.getTrips(token) { trips ->
                            if (trips.isNotEmpty()) {
                                val firstTrip = trips.first()
                                // For shared images, we need to associate them with a place
                                // For now, using the first place or creating a default one
                                // In production, show a dialog to select trip and place
                                photoViewModel.uploadPhoto(token, firstTrip.id, bmp) {
                                    runOnUiThread {
                                        // Show a toast or notification that photo was uploaded
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
