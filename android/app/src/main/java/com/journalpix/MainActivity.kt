package com.journalpix

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.journalpix.ui.screen.AboutScreen
import com.journalpix.ui.screen.AuthScreen
import com.journalpix.ui.screen.HomeScreen
import com.journalpix.ui.screen.TimelineScreen
import com.journalpix.ui.map.MapScreen
import com.journalpix.ui.theme.JournalPixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
                    val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                    uri?.let {
                        // Handle single image share
                        // You can pass this to your ViewModel or navigate to upload screen
                    }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val uris = intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                    uris?.let { list ->
                        // Handle multiple images share
                        list.forEach { uri ->
                            // Process each image URI
                        }
                    }
                }
            }
        }
    }
}
