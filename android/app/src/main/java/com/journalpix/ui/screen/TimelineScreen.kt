package com.journalpix.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(navController: NavController, tripId: String) {
    val context = LocalContext.current
    val timelineViewModel: TimelineViewModel = remember { TimelineViewModel() }
    val placeViewModel: PlaceViewModel = remember { PlaceViewModel() }
    val photoViewModel: PhotoViewModel = remember { PhotoViewModel() }

    var selectedPlace by remember { mutableStateOf<PlaceWithPhotos?>(null) }
    var showCreatePlaceDialog by remember { mutableStateOf(false) }
    var showEditDatesDialog by remember { mutableStateOf(false) }
    var createPlaceDay by remember { mutableStateOf<String?>(null) }
    var newPlaceName by remember { mutableStateOf("") }
    var uploadTargetPlaceId by remember { mutableStateOf<String?>(null) }
    val expandedDays = remember { mutableStateMapOf<String, Boolean>() }

    val token = AppSession.currentUser.value?.token ?: AuthViewModel.DEMO_TOKEN
    val effectiveTripId = tripId.ifBlank { "demo-trip-1" }

    fun refresh() {
        timelineViewModel.refreshOffline(effectiveTripId)
    }

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
            } catch (_: Exception) { }

            photoViewModel.addPhotoOffline(context, placeId, uri) {
                // Photo EXIF day moves the place into the correct day bucket
                refresh()
                selectedPlace = timelineViewModel.timeline.value
                    ?.timeline
                    ?.flatMap { it.places }
                    ?.find { p -> p.photos.any { ph -> ph.id == it.id } || p.id == placeId }
            }
        }
        uploadTargetPlaceId = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> pickImageLauncher.launch("image/*") }

    fun startAddPhoto(placeId: String) {
        uploadTargetPlaceId = placeId
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        else pickImageLauncher.launch("image/*")
    }

    fun onReverseGeocode(photo: PhotoResponse) {
        photoViewModel.reverseGeocode(effectiveTripId, photo) {
            refresh()
            selectedPlace = timelineViewModel.timeline.value
                ?.timeline
                ?.flatMap { it.places }
                ?.find { p -> p.id == it.id }
        }
    }

    LaunchedEffect(effectiveTripId) {
        timelineViewModel.getTimeline(token, effectiveTripId)
    }

    val timeline = timelineViewModel.timeline.value

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            timeline?.trip?.title ?: "Trip Timeline",
                            fontWeight = FontWeight.SemiBold
                        )
                        val range = listOfNotNull(
                            timeline?.trip?.startDate,
                            timeline?.trip?.endDate
                        ).joinToString(" – ")
                        if (range.isNotBlank()) {
                            Text(
                                range,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDatesDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit trip dates")
                    }
                    IconButton(onClick = { navController.navigate("map/$effectiveTripId") }) {
                        Icon(Icons.Default.Map, contentDescription = "View on map")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (timelineViewModel.isLoading.value) {
            Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else if (timeline == null) {
            Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { Text("No timeline data") }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    // Trip date range (tappable to edit)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showEditDatesDialog = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trip itinerary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val from = timeline.trip.startDate ?: "—"
                            val to = timeline.trip.endDate ?: "—"
                            Text(
                                text = "From $from  →  $to",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit dates",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                timeline.timeline.forEachIndexed { dayIndex, dayItem ->
                    val dayKey = dayItem.date
                    val expanded = expandedDays[dayKey] ?: true
                    item(key = "day-header-$dayKey") {
                        DayHeader(
                            dayNumber = dayIndex + 1,
                            date = dayItem.date,
                            placeCount = dayItem.places.size,
                            photoCount = dayItem.places.sumOf { it.photos.size },
                            expanded = expanded,
                            onToggle = { expandedDays[dayKey] = !expanded }
                        )
                    }
                    if (expanded) {
                        itemsIndexed(
                            items = dayItem.places,
                            key = { _, place -> place.id }
                        ) { placeIndex, place ->
                            PlaceTimelineRow(
                                number = placeIndex + 1,
                                place = place,
                                isLast = placeIndex == dayItem.places.lastIndex,
                                onPlaceClick = { selectedPlace = place },
                                onAddPhoto = { startAddPhoto(place.id) },
                                resolvedAddresses = photoViewModel.resolvedAddresses,
                                geocodeLoading = photoViewModel.geocodeLoadingIds,
                                onReverseGeocode = { onReverseGeocode(it) }
                            )
                        }
                        item(key = "add-place-$dayKey") {
                            TextButton(
                                onClick = {
                                    createPlaceDay = dayKey
                                    showCreatePlaceDialog = true
                                },
                                modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add a place")
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            createPlaceDay = LocalDate.now().toString()
                            showCreatePlaceDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add a place")
                    }
                }
            }
        }
    }

    // Edit from / to dates
    if (showEditDatesDialog && timeline != null) {
        var startText by remember(timeline.trip.startDate) {
            mutableStateOf(timeline.trip.startDate ?: LocalDate.now().toString())
        }
        var endText by remember(timeline.trip.endDate) {
            mutableStateOf(timeline.trip.endDate ?: LocalDate.now().toString())
        }
        var dateError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showEditDatesDialog = false },
            title = { Text("Trip dates") },
            text = {
                Column {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it; dateError = null },
                        label = { Text("From (yyyy-MM-dd)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it; dateError = null },
                        label = { Text("To (yyyy-MM-dd)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    dateError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        val s = LocalDate.parse(startText.trim())
                        val e = LocalDate.parse(endText.trim())
                        if (e.isBefore(s)) {
                            dateError = "End date must be on or after start date"
                            return@Button
                        }
                        timelineViewModel.updateTripDates(effectiveTripId, s.toString(), e.toString())
                        showEditDatesDialog = false
                    } catch (_: Exception) {
                        dateError = "Use format yyyy-MM-dd"
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDatesDialog = false }) { Text("Cancel") }
            }
        )
    }

    photoViewModel.lastExifNote.value?.let { note ->
        AlertDialog(
            onDismissRequest = { photoViewModel.lastExifNote.value = null },
            title = { Text("No GPS in photo") },
            text = { Text(note) },
            confirmButton = {
                Button(onClick = { photoViewModel.lastExifNote.value = null }) { Text("OK") }
            }
        )
    }

    selectedPlace?.let { place ->
        // Re-resolve place from latest timeline so photos stay sorted after geocode
        val live = timelineViewModel.timeline.value
            ?.timeline
            ?.flatMap { it.places }
            ?.find { it.id == place.id }
            ?: place
        PlaceDetailSheet(
            place = live,
            photoViewModel = photoViewModel,
            onDismiss = { selectedPlace = null },
            onAddPhoto = { startAddPhoto(place.id) },
            onReverseGeocode = { onReverseGeocode(it) }
        )
    }

    if (showCreatePlaceDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaceDialog = false },
            title = { Text("Add a place") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPlaceName,
                        onValueChange = { newPlaceName = it },
                        label = { Text("Place name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    createPlaceDay?.let { day ->
                        Spacer(Modifier.height(8.dp))
                        Text("Day: $day", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPlaceName.isNotBlank()) {
                        val request = com.journalpix.models.CreatePlaceRequest(
                            tripId = effectiveTripId,
                            name = newPlaceName.trim(),
                            description = null,
                            latitude = null,
                            longitude = null,
                            address = null,
                            visitDate = createPlaceDay ?: LocalDate.now().toString(),
                            orderIndex = null
                        )
                        placeViewModel.createPlace(token, effectiveTripId, request) {
                            showCreatePlaceDialog = false
                            newPlaceName = ""
                            createPlaceDay = null
                            refresh()
                        }
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaceDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DayHeader(
    dayNumber: Int,
    date: String,
    placeCount: Int,
    photoCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val display = try {
        val d = LocalDate.parse(date)
        val weekday = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        val md = d.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        "$weekday, $md"
    } catch (_: Exception) {
        date
    }

    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Day $dayNumber", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(display, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "$placeCount places · $photoCount photos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaceTimelineRow(
    number: Int,
    place: PlaceWithPhotos,
    isLast: Boolean,
    onPlaceClick: () -> Unit,
    onAddPhoto: () -> Unit,
    resolvedAddresses: Map<String, String>,
    geocodeLoading: Map<String, Boolean>,
    onReverseGeocode: (PhotoResponse) -> Unit
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(if (place.photos.isEmpty()) 24.dp else 8.dp)
                        .background(lineColor)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, bottom = 12.dp)
                .clickable(onClick = onPlaceClick)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val subtitle = buildList {
                        place.address?.let { add(it) }
                        if (place.latitude != null && place.longitude != null) {
                            add("${"%.4f".format(place.latitude)}, ${"%.4f".format(place.longitude)}")
                        }
                    }.joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                IconButton(onClick = onAddPhoto) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add photo")
                }
            }

            if (place.photos.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Already sorted by time from OfflineStore
                    place.photos.forEach { photo ->
                        PhotoThumb(
                            photo = photo,
                            address = resolvedAddresses[photo.id] ?: place.address,
                            isGeocoding = geocodeLoading[photo.id] == true,
                            onReverseGeocode = { onReverseGeocode(photo) },
                            onClick = onPlaceClick
                        )
                    }
                }
            }

            if (!isLast) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(lineColor.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
private fun PhotoThumb(
    photo: PhotoResponse,
    address: String?,
    isGeocoding: Boolean,
    onReverseGeocode: () -> Unit,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.width(112.dp)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.thumbnailPath.ifBlank { photo.filePath })
                .crossfade(true)
                .build(),
            contentDescription = photo.caption ?: "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
        )
        if (photo.latitude != null && photo.longitude != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    "${"%.2f".format(photo.latitude)}, ${"%.2f".format(photo.longitude)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                if (isGeocoding) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
                } else {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = "Reverse geocode",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onReverseGeocode),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            address?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2
                )
            }
        }
        photo.takenAt?.let { t ->
            Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun PlaceDetailSheet(
    place: PlaceWithPhotos,
    photoViewModel: PhotoViewModel,
    onDismiss: () -> Unit,
    onAddPhoto: () -> Unit,
    onReverseGeocode: (PhotoResponse) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(place.name) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                place.address?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                }
                place.description?.let { Text(it) }
                if (place.latitude != null && place.longitude != null) {
                    Text(
                        "GPS: ${"%.5f".format(place.latitude)}, ${"%.5f".format(place.longitude)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Photos (by time)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onAddPhoto) {
                        Icon(Icons.Default.AddPhotoAlternate, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
                val photos = place.photos // already time-sorted
                if (photos.isEmpty()) {
                    Text("No photos yet", style = MaterialTheme.typography.bodySmall)
                } else {
                    photos.forEach { photo ->
                        PhotoItem(
                            photo = photo,
                            resolvedAddress = photoViewModel.resolvedAddresses[photo.id] ?: place.address,
                            isGeocoding = photoViewModel.geocodeLoadingIds[photo.id] == true,
                            geocodeError = photoViewModel.geocodeErrors[photo.id],
                            onReverseGeocode = { onReverseGeocode(photo) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun PhotoItem(
    photo: PhotoResponse,
    resolvedAddress: String? = null,
    isGeocoding: Boolean = false,
    geocodeError: String? = null,
    onReverseGeocode: () -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.height(8.dp))
            if (photo.latitude != null && photo.longitude != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "GPS: ${"%.5f".format(photo.latitude)}, ${"%.5f".format(photo.longitude)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        resolvedAddress?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        geocodeError?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (isGeocoding) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onReverseGeocode) {
                            Icon(Icons.Default.Place, "Look up address", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                Text("No GPS in EXIF", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            photo.takenAt?.let {
                Text("Taken: $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
