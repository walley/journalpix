package com.journalpix.ui.screen

import android.Manifest
import android.app.DatePickerDialog
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import com.journalpix.data.OfflineStore
import com.journalpix.models.PhotoResponse
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.models.TransportMode
import com.journalpix.viewmodel.AppSession
import com.journalpix.viewmodel.AuthViewModel
import com.journalpix.viewmodel.PhotoViewModel
import com.journalpix.viewmodel.PlaceViewModel
import com.journalpix.viewmodel.TimelineViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.journalpix.ui.theme.JournalPixTheme

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
    var showEditTitleDialog by remember { mutableStateOf(false) }
    var editTransportPlaceId by remember { mutableStateOf<String?>(null) }
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
                    IconButton(onClick = { showEditTitleDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit trip name")
                    }
                    IconButton(onClick = { navController.navigate("map/$effectiveTripId") }) {
                        Icon(Icons.Default.Map, contentDescription = "View on map")
                    }
                }
            )
        }
    ) { paddingValues ->
        val isRefreshing = timelineViewModel.isSyncing.value
        val pullToRefreshState = rememberPullToRefreshState()

        Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(paddingValues)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing, onRefresh = {
                    timelineViewModel.syncAndRefresh(token, effectiveTripId)
                }, state = pullToRefreshState, modifier = Modifier.fillMaxSize()
            ) {
                if (timelineViewModel.isLoading.value) {
                    Box(
                        Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                } else if (timeline == null) {
                    Box(
                        Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) { Text("No timeline data") }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                  .fillMaxWidth()
                                  .clickable { showEditDatesDialog = true }
                                  .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
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
                            val isBefore = dayKey == OfflineStore.BEFORE_KEY
                            val isAfter = dayKey == OfflineStore.AFTER_KEY
                            val isBeforeAfter = isBefore || isAfter
                            val expanded = expandedDays[dayKey] ?: true
                            val inRangeIndex = timeline.timeline.take(dayIndex)
                              .count { it.date != OfflineStore.BEFORE_KEY && it.date != OfflineStore.AFTER_KEY }
                            item(key = "day-header-$dayKey") {
                                DayHeader(
                                    dayNumber = if (isBeforeAfter) 0 else inRangeIndex + 1,
                                    date = dayItem.date,
                                    isBeforeAfter = isBeforeAfter,
                                    placeCount = dayItem.places.size,
                                    photoCount = dayItem.places.sumOf { it.photos.size },
                                    expanded = expanded,
                                    onToggle = { expandedDays[dayKey] = !expanded },
                                    onAddPhoto = {
                                        val newPlace = OfflineStore.addPlace(
                                            tripId = effectiveTripId,
                                            name = "Place on $dayKey",
                                            visitDate = if (isBeforeAfter) LocalDate.now()
                                              .toString() else dayKey
                                        )
                                        refresh()
                                        startAddPhoto(newPlace.id)
                                    }
                                )
                            }
                            if (expanded) {
                                itemsIndexed(
                                    items = dayItem.places,
                                    key = { _, place -> place.id }) { placeIndex, place ->
                                    if (placeIndex > 0) {
                                        TransportBetweenRow(
                                            place = place,
                                            onEdit = { editTransportPlaceId = place.id })
                                    }
                                    PlaceTimelineRow(
                                        number = placeIndex + 1,
                                        place = place,
                                        isLast = placeIndex == dayItem.places.lastIndex,
                                        onPlaceClick = { selectedPlace = place },
                                        onAddPhoto = { startAddPhoto(place.id) },
                                        resolvedAddresses = photoViewModel.resolvedAddresses,
                                        geocodeLoading = photoViewModel.geocodeLoadingIds,
                                        onReverseGeocode = { onReverseGeocode(it) })
                                }
                                if (!isBeforeAfter) {
                                    item(key = "add-place-$dayKey") {
                                        TextButton(
                                            onClick = {
                                                createPlaceDay = dayKey
                                                showCreatePlaceDialog = true
                                            },
                                            modifier = Modifier.padding(
                                                start = 48.dp,
                                                bottom = 8.dp
                                            )
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Add a place")
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            OutlinedButton(
                                onClick = {
                                    timelineViewModel.addNewDay(effectiveTripId)
                                },
                                modifier = Modifier
                                  .fillMaxWidth()
                                  .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add a new day")
                            }
                        }
                    }
                }
            }

            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                      .fillMaxWidth()
                      .align(Alignment.TopCenter)
                )
            }
        }
    }

    if (showEditTitleDialog && timeline != null) {
        var titleText by remember(timeline.trip.title) {
            mutableStateOf(timeline.trip.title)
        }
        AlertDialog(
            onDismissRequest = { showEditTitleDialog = false },
            title = { Text("Trip name") },
            text = {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (titleText.isNotBlank()) {
                        timelineViewModel.updateTripTitle(effectiveTripId, titleText.trim())
                        showEditTitleDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitleDialog = false }) { Text("Cancel") }
            }
        )
    }

    editTransportPlaceId?.let { placeId ->
        val current = timeline?.timeline
            ?.flatMap { it.places }
            ?.find { it.id == placeId }
        val selected = TransportMode.fromStored(current?.transportFromPrevious)
        AlertDialog(
            onDismissRequest = { editTransportPlaceId = null },
            title = { Text("How did you get here?") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TransportMode.entries.forEach { mode ->
                        val isSelected = selected == mode
                        Row(
                            modifier = Modifier
                              .fillMaxWidth()
                              .clickable {
                                  timelineViewModel.setTransport(placeId, mode, effectiveTripId)
                                  editTransportPlaceId = null
                              }
                              .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(mode.emoji, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                mode.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            timelineViewModel.setTransport(placeId, null, effectiveTripId)
                            editTransportPlaceId = null
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    ) { Text("Clear") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { editTransportPlaceId = null }) { Text("Cancel") }
            }
        )
    }

    fun showDatePicker(initialDateStr: String, onDateSelected: (LocalDate) -> Unit) {
        val cal = Calendar.getInstance()
        try {
            val parsed = LocalDate.parse(initialDateStr.trim())
            cal.set(parsed.year, parsed.monthValue - 1, parsed.dayOfMonth)
        } catch (_: Exception) { }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

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
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                showDatePicker(startText) { selectedDate ->
                                    startText = selectedDate.toString()
                                    dateError = null
                                }
                            }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select start date")
                            }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it; dateError = null },
                        label = { Text("To (yyyy-MM-dd)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                showDatePicker(endText) { selectedDate ->
                                    endText = selectedDate.toString()
                                    dateError = null
                                }
                            }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select end date")
                            }
                        }
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
                        timelineViewModel.updateTripDates(
                            effectiveTripId,
                            s.toString(),
                            e.toString()
                        )
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
        var placeDateText by remember(createPlaceDay) {
            mutableStateOf(createPlaceDay ?: LocalDate.now().toString())
        }
        AlertDialog(
            onDismissRequest = {
                showCreatePlaceDialog = false
                newPlaceName = ""
                createPlaceDay = null
            },
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
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = placeDateText,
                        onValueChange = { placeDateText = it },
                        label = { Text("Date (yyyy-MM-dd)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                showDatePicker(placeDateText) { selectedDate ->
                                    placeDateText = selectedDate.toString()
                                }
                            }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select date")
                            }
                        }
                    )
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
                            visitDate = placeDateText.trim().ifBlank { LocalDate.now().toString() },
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
                TextButton(onClick = {
                    showCreatePlaceDialog = false
                    newPlaceName = ""
                    createPlaceDay = null
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DayHeader(
    dayNumber: Int,
    date: String,
    isBeforeAfter: Boolean = false,
    placeCount: Int,
    photoCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddPhoto: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val isBefore = date == OfflineStore.BEFORE_KEY
    val isAfter = date == OfflineStore.AFTER_KEY

    val display = when {
        isBefore -> "Photos before the trip dates"
        isAfter  -> "Photos after the trip dates"
        else     -> try {
            val d = LocalDate.parse(date)
            val weekday = d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            val md = d.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
            "$weekday, $md"
        } catch (_: Exception) {
            date
        }
    }

    val title = when {
        isBefore -> "Before Trip"
        isAfter  -> "After Trip"
        else     -> "Day $dayNumber"
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
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(display, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "$placeCount places · $photoCount photos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onAddPhoto) {
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = "Add photo to day",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TransportBetweenRow(
    place: PlaceWithPhotos,
    onEdit: () -> Unit
) {
    val mode = TransportMode.fromStored(place.transportFromPrevious)
    Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 48.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
          .clickable(onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier
              .weight(1f)
              .padding(end = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.clickable(onClick = onEdit)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (mode != null) "${mode.emoji} ${mode.label}" else "＋ Transport",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier
              .weight(1f)
              .padding(start = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TimelineScreenPreview() {
    JournalPixTheme {
        TimelineScreen(
            navController = rememberNavController(),
            tripId = "demo-trip-1"
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DayHeaderPreview() {
    JournalPixTheme {
        DayHeader(
            dayNumber = 1,
            date = "2025-05-15",
            isBeforeAfter = false,
            placeCount = 3,
            photoCount = 12,
            expanded = true, onToggle = {}, onAddPhoto = {}
        )
    }
}
