package com.journalpix.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.journalpix.models.PhotoResponse
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.models.TimelineItem
import com.journalpix.viewmodel.PhotoViewModel
import com.journalpix.viewmodel.PlaceViewModel
import com.journalpix.viewmodel.TimelineViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(navController: NavController, tripId: String) {
    val timelineViewModel: TimelineViewModel = remember { TimelineViewModel() }
    val placeViewModel: PlaceViewModel = remember { PlaceViewModel() }
    val photoViewModel: PhotoViewModel = remember { PhotoViewModel() }
    
    var selectedPlace by remember { mutableStateOf<PlaceWithPhotos?>(null) }
    var showCreatePlaceDialog by remember { mutableStateOf(false) }
    var newPlaceName by remember { mutableStateOf("") }
    
    val token = "Bearer token" // Should come from auth state
    
    LaunchedEffect(tripId) {
        timelineViewModel.getTimeline(token, tripId)
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Trip Timeline") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreatePlaceDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Place")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            if (timelineViewModel.isLoading.value) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val timeline = timelineViewModel.timeline.value
                
                timeline?.let { tripTimeline ->
                    Column {
                        // Trip info
                        Text(
                            text = tripTimeline.trip.title,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                        
                        tripTimeline.trip.description?.let { description ->
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // Timeline
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            timeline.timeline.forEach { timelineItem ->
                                stickyHeader {
                                    TimelineDateHeader(date = timelineItem.date)
                                }
                                
                                items(timelineItem.places) { place ->
                                    PlaceCard(
                                        place = place,
                                        onClick = { selectedPlace = place }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Place detail dialog
    selectedPlace?.let { place ->
        AlertDialog(
            onDismissRequest = { selectedPlace = null },
            title = { Text(place.name) },
            text = {
                Column {
                    Text(place.description ?: "No description")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Photos:", style = MaterialTheme.typography.titleMedium)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Photo grid
                    LazyColumn {
                        items(place.photos) { photo ->
                            PhotoItem(photo = photo)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedPlace = null }) {
                    Text("Close")
                }
            }
        )
    }
    
    // Create place dialog
    if (showCreatePlaceDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaceDialog = false },
            title = { Text("Create New Place") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPlaceName,
                        onValueChange = { newPlaceName = it },
                        label = { Text("Place Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPlaceName.isNotBlank()) {
                        val request = com.journalpix.models.CreatePlaceRequest(
                            tripId = tripId,
                            name = newPlaceName,
                            description = null,
                            latitude = null,
                            longitude = null,
                            address = null,
                            visitDate = null,
                            orderIndex = null
                        )
                        placeViewModel.createPlace(token, tripId, request) {
                            showCreatePlaceDialog = false
                            newPlaceName = ""
                            timelineViewModel.getTimeline(token, tripId)
                        }
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TimelineDateHeader(date: String) {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val localDate = LocalDate.parse(date, formatter)
    val displayFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy")
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = localDate.format(displayFormatter),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun PlaceCard(place: PlaceWithPhotos, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .combinedClickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleMedium
            )
            
            place.address?.let { address ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row {
                place.latitude?.let { lat ->
                    place.longitude?.let { lon ->
                        Text(
                            text = "${"%.4f".format(lat)}, ${"%.4f".format(lon)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${place.photos.size} photos",
                style = MaterialTheme.typography.bodySmall
            )
            
            // First photo as thumbnail
            if (place.photos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                val firstPhoto = place.photos.first()
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(firstPhoto.thumbnailPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Place thumbnail",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
            }
        }
    }
}

@Composable
fun PhotoItem(photo: PhotoResponse) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photo.thumbnailPath)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.caption ?: "Photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            )
            
            photo.caption?.let { caption ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            photo.takenAt?.let { takenAt ->
                Text(
                    text = takenAt,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
