package com.journalpix

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
}
