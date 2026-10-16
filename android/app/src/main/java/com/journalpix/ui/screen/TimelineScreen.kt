package com.journalpix.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.journalpix.models.PhotoResponse
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.viewmodel.AppSession
import com.journalpix.viewmodel.AuthViewModel
import com.journalpix.viewmodel.PhotoViewModel
import com.journalpix.viewmodel.PlaceViewModel
import com.journalpix.viewmodel.TimelineViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(navController: NavController, tripId: String) {
    val context = LocalContext.current
    val timelineViewModel: TimelineViewModel = remember { TimelineViewModel() }
    val placeViewModel: PlaceViewModel = remember { PlaceViewModel() }
    val photoViewModel: PhotoViewModel = remember { PhotoViewModel() }

    var selectedPlace by remember { mutableStateOf<PlaceWithPhotos?>(null) }
    var showCreatePlaceDialog by remember { mutableStateOf(false) }
    var newPlaceName by remember { mutableStateOf("") }
    var uploadTargetPlaceId by remember { mutableStateOf<String?>(null) }

    val token = AppSession.currentUser.value?.token ?: AuthViewModel.DEMO_TOKEN
    val effectiveTripId = tripId.ifBlank { "demo-trip-1" }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val placeId = uploadTargetPlaceId
        if (uri != null && placeId != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* not all providers support persistable */ }

            photoViewModel.addPhotoOffline(context, placeId, uri) {
                timelineViewModel.refreshOffline(effectiveTripId)
                selectedPlace = timelineViewModel.timeline.value
                    ?.timeline
                    ?.flatMap { it.places }
                    ?.find { it.id == placeId }
            }
        }
        uploadTargetPlaceId = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // After permission result, open picker regardless (EXIF may still work via file copy)
        pickImageLauncher.launch("image/*")
    }

    fun startAddPhoto(placeId: String) {
        uploadTargetPlaceId = placeId
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // ACCESS_MEDIA_LOCATION is required to read GPS EXIF from MediaStore
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            pickImageLauncher.launch("image/*")
        }
    }

    LaunchedEffect(effectiveTripId) {
        timelineViewModel.getTimeline(token, effectiveTripId)
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
                    IconButton(onClick = { navController.navigate("map/$effectiveTripId") }) {
                        Icon(Icons.Default.Map, contentDescription = "View on map")
                    }
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
                if (timeline == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No timeline data")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = timeline.trip.title,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                            timeline.trip.description?.let { description ->
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "Photos offline. GPS from EXIF needs media-location permission. 📍 = address lookup (internet).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        timeline.timeline.forEach { timelineItem ->
                            stickyHeader {
                                TimelineDateHeader(date = timelineItem.date)
                            }
                            items(timelineItem.places, key = { it.id }) { place ->
                                PlaceCard(
                                    place = place,
                                    onClick = { selectedPlace = place },
                                    onAddPhoto = { startAddPhoto(place.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // EXIF diagnostic after add
    photoViewModel.lastExifNote.value?.let { note ->
        AlertDialog(
            onDismissRequest = { photoViewModel.lastExifNote.value = null },
            title = { Text("No GPS in photo") },
            text = {
                Text(
                    note + "\n\nTips:\n" +
                        "• Use a photo taken with location ON in the camera app\n" +
                        "• On Android 10+, allow \"Location\" access for photos when prompted\n" +
                        "• Some gallery apps strip GPS for privacy — try Files app / original file"
                )
            },
            confirmButton = {
                Button(onClick = { photoViewModel.lastExifNote.value = null }) { Text("OK") }
            }
        )
    }

    selectedPlace?.let { place ->
        AlertDialog(
            onDismissRequest = { selectedPlace = null },
            title = { Text(place.name) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(place.description ?: "No description")
                    Spacer(modifier = Modifier.height(8.dp))
                    place.latitude?.let { lat ->
                        place.longitude?.let { lon ->
                            Text(
                                text = "Place GPS: ${"%.5f".format(lat)}, ${"%.5f".format(lon)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Photos", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { startAddPhoto(place.id) }) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add photo")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val photos = place.photos.ifEmpty {
                        photoViewModel.photos.value.filter { it.placeId == place.id }
                    }
                    if (photos.isEmpty()) {
                        Text(
                            "No photos yet — tap Add photo (works offline).",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        photos.forEach { photo ->
                            PhotoItem(
                                photo = photo,
                                resolvedAddress = photoViewModel.resolvedAddresses[photo.id],
                                isGeocoding = photoViewModel.geocodeLoadingIds[photo.id] == true,
                                geocodeError = photoViewModel.geocodeErrors[photo.id],
                                onReverseGeocode = { photoViewModel.reverseGeocode(photo) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedPlace = null }) { Text("Close") }
            }
        )
    }

    if (showCreatePlaceDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaceDialog = false },
            title = { Text("Create New Place") },
            text = {
                OutlinedTextField(
                    value = newPlaceName,
                    onValueChange = { newPlaceName = it },
                    label = { Text("Place Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newPlaceName.isNotBlank()) {
                        val request = com.journalpix.models.CreatePlaceRequest(
                            tripId = effectiveTripId,
                            name = newPlaceName,
                            description = null,
                            latitude = null,
                            longitude = null,
                            address = null,
                            visitDate = LocalDate.now().toString(),
                            orderIndex = null
                        )
                        placeViewModel.createPlace(token, effectiveTripId, request) {
                            showCreatePlaceDialog = false
                            newPlaceName = ""
                            timelineViewModel.refreshOffline(effectiveTripId)
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaceDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun TimelineDateHeader(date: String) {
    val display = try {
        val localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
        localDate.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))
    } catch (_: Exception) {
        date
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = display,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun PlaceCard(
    place: PlaceWithPhotos,
    onClick: () -> Unit,
    onAddPhoto: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAddPhoto) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add photo")
                }
            }

            place.address?.let { address ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = address, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(4.dp))
            place.latitude?.let { lat ->
                place.longitude?.let { lon ->
                    Text(
                        text = "${"%.4f".format(lat)}, ${"%.4f".format(lon)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${place.photos.size} photos",
                style = MaterialTheme.typography.bodySmall
            )

            if (place.photos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                val firstPhoto = place.photos.first()
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(firstPhoto.thumbnailPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Place thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
            }
        }
    }
}

@Composable
fun PhotoItem(
    photo: PhotoResponse,
    resolvedAddress: String? = null,
    isGeocoding: Boolean = false,
    geocodeError: String? = null,
    onReverseGeocode: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photo.thumbnailPath.ifBlank { photo.filePath })
                    .crossfade(true)
                    .build(),
                contentDescription = photo.caption ?: "Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (photo.latitude != null && photo.longitude != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GPS: ${"%.5f".format(photo.latitude)}, ${"%.5f".format(photo.longitude)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        resolvedAddress?.let { addr ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = addr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        geocodeError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (isGeocoding) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onReverseGeocode) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = "Look up address (Nominatim)",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "No GPS in EXIF",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            photo.takenAt?.let { takenAt ->
                Text(
                    text = "Taken: $takenAt",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            photo.caption?.let { caption ->
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
