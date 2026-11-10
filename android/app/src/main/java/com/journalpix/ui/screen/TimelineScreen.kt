package com.journalpix.ui.screen

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.journalpix.api.ApiService
import com.journalpix.data.OfflineStore
import com.journalpix.models.PhotoResponse
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.models.ReorderPlacesRequest
import com.journalpix.models.TransportMode
import com.journalpix.models.ZonedTime
import com.journalpix.models.ZonedTimeInput
import com.journalpix.viewmodel.AppSession
import com.journalpix.viewmodel.AuthViewModel
import com.journalpix.viewmodel.PhotoViewModel
import com.journalpix.viewmodel.PlaceViewModel
import com.journalpix.viewmodel.TimelineViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.journalpix.ui.theme.JournalPixTheme
import de.afarber.openmapview.CameraPosition
import de.afarber.openmapview.LatLng
import de.afarber.openmapview.MapType
import de.afarber.openmapview.compose.MapProperties
import de.afarber.openmapview.compose.MapUiSettings
import de.afarber.openmapview.compose.MarkerComposable
import de.afarber.openmapview.compose.OpenMap
import de.afarber.openmapview.compose.rememberCameraPositionState
import de.afarber.openmapview.compose.rememberMarkerState

private const val TAG_TIMELINE = "JPXTS"

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(navController:NavController, tripId:String) {
  val context = LocalContext.current
  val timelineViewModel:TimelineViewModel = remember {TimelineViewModel()}
  val placeViewModel:PlaceViewModel = remember {PlaceViewModel()}
  val photoViewModel:PhotoViewModel = remember {PhotoViewModel()}

  var selectedPlace by remember {mutableStateOf<PlaceWithPhotos?>(null)}
  var showEditDatesDialog by remember {mutableStateOf(false)}
  var showEditTitleDialog by remember {mutableStateOf(false)}
  var showEditDescriptionDialog by remember {mutableStateOf(false)}
  var editTransportPlaceId by remember {mutableStateOf<String?>(null)}
  var uploadTargetPlaceId by remember {mutableStateOf<String?>(null)}
  // Unassigned-pool photo dialog + move-to-day picker
  var selectedUnassignedPhoto by remember {mutableStateOf<PhotoResponse?>(null)}
  var showMoveToDayPicker by remember {mutableStateOf(false)}
  // Day-pool photo dialog: detect existing place within 50m, or offer to create one
  var selectedDayPhoto by remember {mutableStateOf<PhotoResponse?>(null)}
  val expandedDays = remember {mutableStateMapOf<String, Boolean>()}

  // Drag-and-drop state for reordering places within a day
  var draggedPlaceId by remember {mutableStateOf<String?>(null)}
  var dragOffsetY by remember {mutableStateOf(0f)}
  var hoverTargetIndex by remember {mutableStateOf<Int?>(null)}

  // No token here — offline-first; SyncManager is the only place that uses the auth token
  // Blank tripId is invalid — validate and show error instead of falling back to a demo trip
  val effectiveTripId = AppSession.validateTripId(tripId) ?: ""

  fun refresh() {
    timelineViewModel.refreshOffline(effectiveTripId)
  }

  /**
   * Auto-pick the target day for an unassigned photo from its EXIF taken-at date:
   * exact timeline day if in range, "before" bucket if before trip start,
   * "after" bucket if after trip end. Null when there is no EXIF date.
   */
  fun autoDayForPhoto(photo: PhotoResponse): String? {
    val d = photo.takenAt?.localDate ?: return null
    val tl = timelineViewModel.timeline.value
    val days = tl?.timeline.orEmpty()
    days.firstOrNull { it.date != null && it.date != OfflineStore.BEFORE_KEY && it.date != OfflineStore.AFTER_KEY && it.date == d.toString() }
      ?.let { return it.date }
    val start = tl?.trip?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val end = tl?.trip?.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (start != null && d.isBefore(start)) return OfflineStore.BEFORE_KEY
    if (end != null && d.isAfter(end)) return OfflineStore.AFTER_KEY
    if (start != null && end != null && !d.isBefore(start) && !d.isAfter(end)) return d.toString()
    return null
  }

  fun moveUnassignedPhotoToDay(photo: PhotoResponse, dayKey: String) {
    Log.i(TAG_TIMELINE, "Moving photo ${photo.id} to day '$dayKey'")
    photoViewModel.assignPhotoToDay(effectiveTripId, photo.id, dayKey) {
      refresh()
    }
    selectedUnassignedPhoto = null
    showMoveToDayPicker = false
  }

  // Guarded navigation to the map — never navigate with a blank/invalid tripId
  fun navigateToMap(addPlaceMode: Boolean) {
    val valid = AppSession.validateTripId(effectiveTripId)
    if (valid != null) {
      // Pass the whole trip route (all place lat/lons as a GeoJSON polyline) to the big map
      AppSession.mapEditPlaceId.value = null
      AppSession.mapGeoJson.value = com.journalpix.geo.GeoJson.forTripRoute(
        com.journalpix.data.OfflineStore.placesForTrip(valid)
      )
      navController.navigate(if (addPlaceMode) "map/$valid?addPlaceMode=true" else "map/$valid")
    } else {
      Log.e(TAG_TIMELINE, "Map navigation blocked — tripId is blank/invalid")
      android.widget.Toast.makeText(context, AppSession.tripIdError.value ?: "Invalid trip", android.widget.Toast.LENGTH_LONG).show()
    }
  }

  fun reorderDayPlaces(dayPlaces:List<PlaceWithPhotos>, fromIndex:Int, toIndex:Int) {
    if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= dayPlaces.size || toIndex >= dayPlaces.size) {
      Log.w(TAG_TIMELINE, "reorderDayPlaces: invalid indices from=$fromIndex to=$toIndex size=${dayPlaces.size}")
      return
    }
    // Swap the two places
    val newOrder = dayPlaces.toMutableList()
    val dragged = newOrder[fromIndex]
    val target = newOrder[toIndex]
    newOrder[fromIndex] = target
    newOrder[toIndex] = dragged
    Log.i(TAG_TIMELINE, "reorderDayPlaces: swapping '${dragged.name}'(idx=$fromIndex) with '${target.name}'(idx=$toIndex)")
    // Verify swap in the list before sending
    val verifyDragged = newOrder[toIndex]
    val verifyTarget = newOrder[fromIndex]
    if (verifyDragged.id != dragged.id || verifyTarget.id != target.id) {
      Log.e(TAG_TIMELINE, "reorderDayPlaces: SWAP VERIFICATION FAILED in local list!")
    }
    placeViewModel.reorderPlaces(effectiveTripId, newOrder.map {it.id}) {
      refresh()
      // Verify after refresh - log all day orders
      val allDays = timelineViewModel.timeline.value?.timeline?.map {day ->
        "day${day.day}: ${day.places.map {it.name}}\""
      }
      Log.i(TAG_TIMELINE, "reorderDayPlaces: after refresh, timeline order = $allDays")
      if (timelineViewModel.timeline.value?.timeline?.flatMap {it.places}?.map {it.id} != newOrder.map {it.id}) {
        // Note: full trip order may include other days, just log for inspection
      }
    }
  }

  val pickImageLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) {uri:Uri? ->
    val placeId = uploadTargetPlaceId
    if (uri != null && placeId != null) {
      try {
        context.contentResolver.takePersistableUriPermission(
          uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      } catch (_:Exception) {
      }
      photoViewModel.addPhotoOffline(context, placeId, uri) {
        refresh()
        selectedPlace = timelineViewModel.timeline.value?.timeline?.flatMap {it.places}?.find {p -> p.photos.any {ph -> ph.id == it.id} || p.id == placeId}
      }
    }
    uploadTargetPlaceId = null
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) {_ -> pickImageLauncher.launch("image/*")}

  // Mass upload: multiple pictures at once → all land in the unassigned pool under the minimap
  val pickMultipleImagesLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetMultipleContents()
  ) { uris: List<Uri> ->
    Log.i(TAG_TIMELINE, "Mass upload: ${uris.size} picture(s) selected — adding to unassigned pool")
    uris.forEach { uri ->
      runCatching {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      photoViewModel.addUnassignedPhotoOffline(context, uri)
    }
    refresh()
  }

  val massUploadPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) {_ -> pickMultipleImagesLauncher.launch("image/*")}

  fun requiredMediaPermissions(): List<String> {
    val needed = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return needed
  }

  fun startMassUpload() {
    val needed = requiredMediaPermissions()
    if (needed.isNotEmpty()) massUploadPermissionLauncher.launch(needed.toTypedArray())
    else pickMultipleImagesLauncher.launch("image/*")
  }

  fun startAddPhoto(placeId:String) {
    uploadTargetPlaceId = placeId
    val needed = requiredMediaPermissions()
    if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    else pickImageLauncher.launch("image/*")
  }

  fun onReverseGeocode(photo:PhotoResponse) {
    photoViewModel.reverseGeocode(effectiveTripId, photo) {
      refresh()
      selectedPlace = timelineViewModel.timeline.value?.timeline?.flatMap {it.places}?.find {p -> p.id == it.id}
    }
  }

  LaunchedEffect(effectiveTripId) {
    // Always sync on trip selection so real server data replaces any
    // stale/demo data that may be persisted locally in store.json.
    timelineViewModel.syncAndRefresh(effectiveTripId)
  }

  // The timeline NEVER creates or edits places from map callbacks — a map pick opens the
  // place detail in create mode; this screen only reloads when the store changes:
  LaunchedEffect(AppSession.placesVersion.value) {
    Log.i(TAG_TIMELINE, "placesVersion=${AppSession.placesVersion.value} — reloading timeline from OfflineStore")
    refresh()
  }

  val timeline = timelineViewModel.timeline.value

  Scaffold(topBar = {
    CenterAlignedTopAppBar(title = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          timeline?.trip?.title ?: "Trip Timeline", fontWeight = FontWeight.SemiBold
        )
        val range = listOfNotNull(
          timeline?.trip?.startDate, timeline?.trip?.endDate
        ).joinToString(" – ")
        if (range.isNotBlank()) {
          Text(
            range, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }, navigationIcon = {
      IconButton(onClick = {navController.popBackStack()}) {
        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
      }
    }, actions = {
      IconButton(onClick = {showEditTitleDialog = true}) {
        Icon(Icons.Default.Edit, contentDescription = "Edit trip name")
      }
      IconButton(onClick = {
        Log.i(TAG_TIMELINE, "onClick: '+' TopBar button — mass upload of pictures to the unassigned pool for trip $effectiveTripId")
        startMassUpload()
      }) {
        Icon(Icons.Default.PhotoLibrary, contentDescription = "Upload photos")
      }
    })
  }, floatingActionButton = {
    Column(
      horizontalAlignment = Alignment.End
    ) {
      FloatingActionButton(
        onClick = {
          navigateToMap(addPlaceMode = false)
        }, modifier = Modifier.size(48.dp)
      ) {
        Icon(
          Icons.Default.Map, contentDescription = "View map", tint = MaterialTheme.colorScheme.onPrimary
        )
      }
      Spacer(Modifier.height(8.dp))
      FloatingActionButton(
        onClick = {
          // Pick image to add to unassigned photos
          pickImageLauncher.launch("image/*")
        }, modifier = Modifier.size(48.dp)
      ) {
        Icon(
          Icons.Default.AddPhotoAlternate, contentDescription = "Add photo to unassigned", tint = MaterialTheme.colorScheme.onPrimary
        )
      }
    }
  }) {paddingValues ->
    val isRefreshing = timelineViewModel.isSyncing.value
    val pullToRefreshState = rememberPullToRefreshState()

    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      PullToRefreshBox(
        isRefreshing = isRefreshing, onRefresh = {
          timelineViewModel.syncAndRefresh(effectiveTripId)
        }, state = pullToRefreshState, modifier = Modifier.fillMaxSize()
      ) {
        if (timelineViewModel.isLoading.value) {
          Box(
            Modifier.fillMaxSize(), contentAlignment = Alignment.Center
          ) {CircularProgressIndicator()}
        } else if (timeline == null) {
          Box(
            Modifier.fillMaxSize(), contentAlignment = Alignment.Center
          ) {Text("No timeline data")}
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)
          ) {
            // Trip description — 4th column under TopBar, scrolls with page
            item {
              val description = timeline.trip.description
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 8.dp)
                  .clickable {showEditDescriptionDialog = true}, colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    Icons.Default.Description, contentDescription = "Description", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary
                  )
                  Spacer(Modifier.width(8.dp))
                  Text(text = description?.takeIf {it.isNotBlank()} ?: "Add trip description…",
                       style = MaterialTheme.typography.bodyMedium,
                       color = if (description.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                       else MaterialTheme.colorScheme.onSurface,
                       modifier = Modifier.weight(1f))
                  Icon(
                    Icons.Default.Edit, contentDescription = "Edit description", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
            // Trip minimap — shows all known place positions, tap to open full map
            item {
              val allPlaces = timeline.timeline.flatMap { it.places }
              val positioned = allPlaces.filter { it.latitude != null && it.longitude != null }
              Log.d(TAG_TIMELINE, "Minimap: ${positioned.size} positioned places out of ${allPlaces.size}")
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(180.dp)
                  .padding(horizontal = 16.dp, vertical = 4.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant)
                  .clickable {
                    Log.i(TAG_TIMELINE, "Minimap tapped — opening full map for trip $effectiveTripId")
                    navigateToMap(addPlaceMode = false)
                  }
              ) {
                if (positioned.isNotEmpty()) {
                  val avgLat = positioned.map { it.latitude!! }.average()
                  val avgLon = positioned.map { it.longitude!! }.average()
                  val cameraState = rememberCameraPositionState {
                    position = CameraPosition(LatLng(avgLat, avgLon), 11f)
                  }
                  OpenMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraState,
                    properties = MapProperties(
                      mapType = MapType.STANDARD,
                      minZoomPreference = 3f,
                      maxZoomPreference = 20f
                    ),
                    uiSettings = MapUiSettings(
                      zoomControlsEnabled = false,
                      scrollGesturesEnabled = false,
                      zoomGesturesEnabled = false
                    )
                  ) {
                    positioned.forEachIndexed { index, place ->
                      MarkerComposable(
                        state = rememberMarkerState(
                          LatLng(place.latitude!!, place.longitude!!)
                        )
                      ) {
                        Box(
                          modifier = Modifier
                            .size(24.dp)
                            .background(
                              MaterialTheme.colorScheme.primary,
                              CircleShape
                            ),
                          contentAlignment = Alignment.Center
                        ) {
                          Text(
                            text = (index + 1).toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                          )
                        }
                      }
                    }
                  }
                } else {
                  Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      "No places on map yet",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
                // Tap hint overlay
                Surface(
                  modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
                  color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text(
                    "Tap to enlarge",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }
            // Unassigned photos section
            val unassignedPhotos = OfflineStore.getUnassignedPhotos()
            if (unassignedPhotos.isNotEmpty()) {
              item {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                  Text(
                    text = "Unassigned Photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                  )
                  Spacer(Modifier.height(8.dp))
                  LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    items(
                      items = unassignedPhotos, key = {it.id}) {photo ->
                      Column(
                        modifier = Modifier
                          .size(80.dp)
                          .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                          .clickable {
                            Log.i(TAG_TIMELINE, "Unassigned photo tapped: " + photo.id + " — showing EXIF dialog")
                            selectedUnassignedPhoto = photo
                          }
                      ) {
                        Box(
                          modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.1f))
                        ) {
                          AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(photoImageUrl(photo)).crossfade(true).build(),
                            contentDescription = photo.caption ?: "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                          )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                          text = photo.takenAt?.timeLabel() ?: "No date", style = MaterialTheme.typography.bodySmall, maxLines = 1
                        )
                      }
                    }
                  }
                }
                Spacer(Modifier.height(16.dp))
              }
            }
            item {
              Row(modifier = Modifier
                .fillMaxWidth()
                .clickable {showEditDatesDialog = true}
                .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Trip itinerary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                  )
                  val from = timeline.trip.startDate ?: "—"
                  val to = timeline.trip.endDate ?: "—"
                  Text(
                    text = "From $from  →  $to", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary
                  )
                }
                Icon(
                  Icons.Default.Edit, contentDescription = "Edit dates", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)
                )
              }
            }

            timeline.timeline.forEachIndexed {dayIndex, dayItem ->
              val rawDate = dayItem.date ?: ""
              val dayKey = dayItem.date ?: "day-${dayItem.day}-$dayIndex"
              val isBefore = rawDate == OfflineStore.BEFORE_KEY
              val isAfter = rawDate == OfflineStore.AFTER_KEY
              val isBeforeAfter = isBefore || isAfter
              val expanded = expandedDays[dayKey] ?: true
              val inRangeIndex = timeline.timeline.take(dayIndex).count {it.date != OfflineStore.BEFORE_KEY && it.date != OfflineStore.AFTER_KEY}
              item(key = "day-header-$dayKey") {
                DayHeader(
                  dayNumber = if (isBeforeAfter) 0 else inRangeIndex + 1,
                  date = rawDate,
                  isBeforeAfter = isBeforeAfter,
                  placeCount = dayItem.places.size,
                  photoCount = dayItem.places.sumOf {it.photos.size},
                  expanded = expanded,
                  onToggle = {
                    expandedDays[dayKey] = !(expandedDays[dayKey] ?: true)
                  },
                  onAddPhoto = {
                    val newPlace = OfflineStore.addPlace(
                      tripId = effectiveTripId,
                      name = if (rawDate.isNotBlank()) "Place on $rawDate" else "Place on day ${dayItem.day}",
                      visitDate = if (isBeforeAfter) ZonedTime.fromLocal(LocalDateTime.now(), ZoneId.systemDefault().id)
                      else ZonedTime.parseLegacy(rawDate)
                    )
                    refresh()
                    startAddPhoto(newPlace.id)
                  })
              }
              // Day photo pool — all photos of this day's places, right under the day title bar
              val dayPhotos = dayItem.places.flatMap { it.photos }
              if (dayPhotos.isNotEmpty()) {
                item(key = "day-photos-$dayKey") {
                  LazyRow(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    items(items = dayPhotos, key = { "dayphoto_" + it.id }) { photo ->
                      AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(photoImageUrl(photo)).crossfade(true).build(),
                        contentDescription = photo.caption ?: "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                          .size(56.dp)
                          .clip(RoundedCornerShape(8.dp))
                          .clickable {
                            Log.i(TAG_TIMELINE, "Day-pool photo tapped: ${photo.id} — checking for a place within 50m")
                            selectedDayPhoto = photo
                          }
                      )
                    }
                  }
                }
              }
              if (expanded) {
                itemsIndexed(
                  items = dayItem.places, key = {placeIndex, place -> "place_${dayKey}_${place.id}_$placeIndex"}) {placeIndex, place ->
                  if (placeIndex > 0) {
                    TransportBetweenRow(
                      place = place, onEdit = {editTransportPlaceId = place.id})
                  }
                  val isDragging = draggedPlaceId == place.id
                  val isHoverTarget = hoverTargetIndex == placeIndex && draggedPlaceId != null && !isDragging
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .zIndex(if (isDragging) 1f else 0f)
                      .background(
                        if (isHoverTarget) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else Color.Transparent, RoundedCornerShape(8.dp)
                      )
                      .graphicsLayer {
                        alpha = if (isDragging) 0.85f else 1f
                        translationY = if (isDragging) dragOffsetY else 0f
                      }, verticalAlignment = Alignment.CenterVertically
                  ) {
                    PlaceTimelineRow(
                      number = placeIndex + 1,
                                     place = place,
                                     isLast = placeIndex == dayItem.places.lastIndex,
                                     onPlaceClick = {selectedPlace = place},
                                     onAddPhoto = {startAddPhoto(place.id)},
                                     resolvedAddresses = photoViewModel.resolvedAddresses,
                                     geocodeLoading = photoViewModel.geocodeLoadingIds,
                                     onReverseGeocode = {onReverseGeocode(it)},
                                     modifier = Modifier.weight(1f),
                                     dragHandle = {
                                       Icon(
                                         Icons.Default.DragHandle, contentDescription = "Drag to reorder", tint = if (isDragging) MaterialTheme.colorScheme.primary
                                         else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier
                                           .size(28.dp)
                                           .pointerInput(place.id) {
                                             detectDragGesturesAfterLongPress(onDragStart = {
                                               draggedPlaceId = place.id
                                               dragOffsetY = 0f
                                               hoverTargetIndex = null
                                             }, onDragEnd = {
                                               val fromIdx = dayItem.places.indexOfFirst {it.id == place.id}
                                               val toIdx = hoverTargetIndex ?: run {
                                                 val rowHeightPx = 72.dp.toPx()
                                                 (fromIdx + (dragOffsetY / rowHeightPx).toInt()).coerceIn(0, dayItem.places.lastIndex)
                                               }
                                               draggedPlaceId = null
                                               dragOffsetY = 0f
                                               hoverTargetIndex = null
                                               if (fromIdx != toIdx && fromIdx >= 0 && toIdx >= 0) {
                                                 reorderDayPlaces(dayItem.places, fromIdx, toIdx)
                                               }
                                             }, onDragCancel = {
                                               draggedPlaceId = null
                                               dragOffsetY = 0f
                                               hoverTargetIndex = null
                                             }, onDrag = {change, dragAmount ->
                                               change.consume()
                                               dragOffsetY += dragAmount.y
                                               val rowHeightPx = 72.dp.toPx()
                                               val fromIdx = dayItem.places.indexOfFirst {it.id == place.id}
                                               if (fromIdx >= 0) {
                                                 val hoverIdx = (fromIdx + (dragOffsetY / rowHeightPx).toInt()).coerceIn(0, dayItem.places.lastIndex)
                                                 hoverTargetIndex = hoverIdx
                                               }
                                             })
                                           })
                                     })
                  }
                }
                if (!isBeforeAfter) {
                  item(key = "add-place-$dayKey") {
                    TextButton(
                      onClick = {
                        Log.i(TAG_TIMELINE, "onClick: 'Add a place' button for day=$rawDate — opening create place dialog")
                        // No dialog — hand off to placeview create mode, which asks for the name
                        val valid = AppSession.validateTripId(effectiveTripId)
                        if (valid != null) {
                          Log.i(TAG_TIMELINE, "onClick: 'Add a place' for day=$rawDate — navigating to place_new (placeview), no dialog")
                          navController.navigate("place_new?tripId=$valid&day=$rawDate")
                        } else {
                          android.widget.Toast.makeText(context, AppSession.tripIdError.value ?: "Invalid trip", android.widget.Toast.LENGTH_LONG).show()
                        }
                      }, modifier = Modifier.padding(
                        start = 48.dp, bottom = 8.dp
                      )
                    ) {
                      Icon(
                        Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)
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
                }, modifier = Modifier
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
      AlertDialog(onDismissRequest = {showEditTitleDialog = false}, title = {Text("Trip name")}, text = {
        OutlinedTextField(
          value = titleText, onValueChange = {titleText = it}, label = {Text("Name")}, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
      }, confirmButton = {
        Button(onClick = {
          if (titleText.isNotBlank()) {
            timelineViewModel.updateTripTitle(effectiveTripId, titleText.trim())
            showEditTitleDialog = false
          }
        }) {Text("Save")}
      }, dismissButton = {
        TextButton(onClick = {showEditTitleDialog = false}) {Text("Cancel")}
      })
    }

    if (showEditDescriptionDialog && timeline != null) {
      var descText by remember(timeline.trip.description) {
        mutableStateOf(timeline.trip.description ?: "")
      }
      AlertDialog(onDismissRequest = {showEditDescriptionDialog = false}, title = {Text("Trip description")}, text = {
        OutlinedTextField(
          value = descText, onValueChange = {descText = it}, label = {Text("Description")}, singleLine = false, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth()
        )
      }, confirmButton = {
        Button(onClick = {
          Log.i(TAG_TIMELINE, "Saving trip description: '${descText.trim()}'")
          timelineViewModel.updateTripDescription(effectiveTripId, descText)
          showEditDescriptionDialog = false
        }) {Text("Save")}
      }, dismissButton = {
        TextButton(onClick = {showEditDescriptionDialog = false}) {Text("Cancel")}
      })
    }

    editTransportPlaceId?.let {placeId ->
      val current = timeline?.timeline?.flatMap {it.places}?.find {it.id == placeId}
      val selected = TransportMode.fromStored(current?.transportFromPrevious)
      AlertDialog(onDismissRequest = {editTransportPlaceId = null}, title = {Text("How did you get here?")}, text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          TransportMode.entries.forEach {mode ->
            val isSelected = selected == mode
            Row(modifier = Modifier
              .fillMaxWidth()
              .clickable {
                timelineViewModel.setTransport(placeId, mode, effectiveTripId)
                editTransportPlaceId = null
              }
              .padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
              Text(mode.emoji, style = MaterialTheme.typography.titleMedium)
              Spacer(Modifier.width(12.dp))
              Text(
                mode.label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
              )
            }
          }
          TextButton(
            onClick = {
              timelineViewModel.setTransport(placeId, null, effectiveTripId)
              editTransportPlaceId = null
            }, modifier = Modifier.padding(top = 4.dp)
          ) {Text("Clear")}
        }
      }, confirmButton = {
        TextButton(onClick = {editTransportPlaceId = null}) {Text("OK")}
      }, dismissButton = {
        TextButton(onClick = {editTransportPlaceId = null}) {Text("Cancel")}
      })
    }

    fun showDatePicker(initialDateStr:String, onDateSelected:(LocalDate) -> Unit) {
      val cal = Calendar.getInstance()
      try {
        val parsed = LocalDate.parse(initialDateStr.trim())
        cal.set(parsed.year, parsed.monthValue - 1, parsed.dayOfMonth)
      } catch (_:Exception) {
      }

      DatePickerDialog(
        context, {_, year, month, dayOfMonth ->
          onDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
      ).show()
    }

    if (showEditDatesDialog && timeline != null) {
      var startText by remember(timeline.trip.startDate) {
        mutableStateOf(timeline.trip.startDate ?: LocalDate.now().toString())
      }
      var endText by remember(timeline.trip.endDate) {
        mutableStateOf(timeline.trip.endDate ?: LocalDate.now().toString())
      }
      var dateError by remember {mutableStateOf<String?>(null)}

      AlertDialog(onDismissRequest = {showEditDatesDialog = false}, title = {Text("Trip dates")}, text = {
        Column {
          OutlinedTextField(
            value = startText,
                            onValueChange = {startText = it; dateError = null},
                            label = {Text("From (yyyy-MM-dd)")},
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                              IconButton(onClick = {
                                showDatePicker(startText) {selectedDate ->
                                  startText = selectedDate.toString()
                                  dateError = null
                                }
                              }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select start date")
                              }
                            })
          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
            value = endText,
                            onValueChange = {endText = it; dateError = null},
                            label = {Text("To (yyyy-MM-dd)")},
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                              IconButton(onClick = {
                                showDatePicker(endText) {selectedDate ->
                                  endText = selectedDate.toString()
                                  dateError = null
                                }
                              }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select end date")
                              }
                            })
          dateError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
          }
        }
      }, confirmButton = {
        Button(onClick = {
          try {
            val s = LocalDate.parse(startText.trim())
            val e = LocalDate.parse(endText.trim())
            if (e.isBefore(s)) {
              dateError = "End date must be on or after start date"
              return@Button
            }
            timelineViewModel.updateTripDates(
              effectiveTripId, s.toString(), e.toString()
            )
            showEditDatesDialog = false
          } catch (_:Exception) {
            dateError = "Use format yyyy-MM-dd"
          }
        }) {Text("Save")}
      }, dismissButton = {
        TextButton(onClick = {showEditDatesDialog = false}) {Text("Cancel")}
      })
    }

    photoViewModel.lastExifNote.value?.let {note ->
      AlertDialog(onDismissRequest = {photoViewModel.lastExifNote.value = null}, title = {Text("No GPS in photo")}, text = {Text(note)}, confirmButton = {
        Button(onClick = {photoViewModel.lastExifNote.value = null}) {Text("OK")}
      })
    }

    selectedPlace?.let {place ->
      val live = timelineViewModel.timeline.value?.timeline?.flatMap {it.places}?.find {it.id == place.id} ?: place
      Log.i(TAG_TIMELINE, "Opening place detail screen: placeId=${place.id}, name='${place.name}'")
      if (place.id.isBlank()) {
        Log.e(TAG_TIMELINE, "Cannot open place detail — place id is BLANK")
        android.widget.Toast.makeText(context, "Invalid place", android.widget.Toast.LENGTH_LONG).show()
      } else {
        AppSession.openedPlaceSnapshot.value = live
        navController.navigate("place/${place.id}?tripId=$effectiveTripId")
      }
      selectedPlace = null
    }

    // Day-pool photo tapped → existing place within 50m? move there : ask to create a new place
    selectedDayPhoto?.let { photo ->
      val nearPlace = if (photo.latitude != null && photo.longitude != null) {
        OfflineStore.placeNear(effectiveTripId, photo.latitude!!, photo.longitude!!, 50.0, excludePlaceId = photo.placeId)
      } else null
      AlertDialog(
        onDismissRequest = { selectedDayPhoto = null },
        title = { Text(if (nearPlace != null) "Place nearby" else if (photo.latitude != null) "No place nearby" else "Photo") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Taken: " + (photo.takenLabel() ?: "unknown"), style = MaterialTheme.typography.bodySmall)
            Text(
              text = if (photo.latitude != null && photo.longitude != null)
                "GPS: %.6f, %.6f".format(photo.latitude, photo.longitude)
              else "No GPS in EXIF",
              style = MaterialTheme.typography.bodySmall
            )
            if (nearPlace != null) {
              Text("Place '${nearPlace.name}' is already added within 50 m.", style = MaterialTheme.typography.bodySmall)
            } else if (photo.latitude != null && photo.longitude != null) {
              Text("No place within 50 m of this photo.", style = MaterialTheme.typography.bodySmall)
            }
          }
        },
        confirmButton = {
          when {
            nearPlace != null -> Button(onClick = {
              Log.i(TAG_TIMELINE, "Moving photo ${photo.id} to existing nearby place '${nearPlace.name}' (${nearPlace.id})")
              OfflineStore.movePhotoToPlace(photo.id, nearPlace.id)
              refresh()
              selectedDayPhoto = null
            }) { Text("Move to '${nearPlace.name}'") }
            photo.latitude != null && photo.longitude != null -> Button(onClick = {
              Log.i(TAG_TIMELINE, "User confirmed: create a new place for photo ${photo.id} and move the photo to it")
              photoViewModel.createPlaceForPhoto(effectiveTripId, photo) {
                refresh()
                selectedDayPhoto = null
              }
            }) { Text("Create place") }
            else -> {}
          }
        },
        dismissButton = {
          TextButton(onClick = { selectedDayPhoto = null }) { Text("Cancel") }
        }
      )
    }

    // Unassigned photo tapped → EXIF info + Move to a day / Cancel
    selectedUnassignedPhoto?.let { photo ->
      val addr = OfflineStore.addresses[photo.id]
      AlertDialog(
        onDismissRequest = { selectedUnassignedPhoto = null },
        title = { Text("Photo") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AsyncImage(
              model = ImageRequest.Builder(LocalContext.current).data(photoImageUrl(photo)).crossfade(true).build(),
              contentDescription = photo.caption ?: "Photo",
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp))
            )
            Text("Taken: " + (photo.takenLabel() ?: "unknown"), style = MaterialTheme.typography.bodySmall)
            Text(
              text = if (photo.latitude != null && photo.longitude != null)
                "GPS: %.6f, %.6f".format(photo.latitude, photo.longitude)
              else "No GPS in EXIF",
              style = MaterialTheme.typography.bodySmall
            )
            if (!addr.isNullOrBlank()) {
              Text("Address: $addr", style = MaterialTheme.typography.bodySmall)
            }
          }
        },
        confirmButton = {
          Button(onClick = {
            val auto = autoDayForPhoto(photo)
            if (auto != null) {
              Log.i(TAG_TIMELINE, "Move to a day: EXIF date auto-picked day '$auto' for photo ${photo.id} — moving directly")
              moveUnassignedPhotoToDay(photo, auto)
            } else {
              Log.i(TAG_TIMELINE, "Move to a day tapped for photo ${photo.id} — no EXIF date, showing day picker")
              showMoveToDayPicker = true
            }
          }) { Text("Move to a day") }
        },
        dismissButton = {
          TextButton(onClick = { selectedUnassignedPhoto = null }) { Text("Cancel") }
        }
      )
    }

    // Day picker: choose the day whose photo pool receives the selected photo
    if (showMoveToDayPicker) {
      val photo = selectedUnassignedPhoto
      AlertDialog(
        onDismissRequest = { showMoveToDayPicker = false },
        title = { Text("Move to which day?") },
        text = {
          Column(Modifier.verticalScroll(rememberScrollState())) {
            val dayEntries = mutableListOf<Pair<String, String>>() // label to dayKey
            dayEntries.add("Before trip" to OfflineStore.BEFORE_KEY)
            timeline?.timeline?.forEach { d ->
              if (d.date != null && d.date != OfflineStore.BEFORE_KEY && d.date != OfflineStore.AFTER_KEY) {
                dayEntries.add((d.date ?: "Day ${d.day}") to d.date!!)
              } else if (d.date == null) {
                dayEntries.add("Day ${d.day}" to "")
              }
            }
            dayEntries.add("After trip" to OfflineStore.AFTER_KEY)
            dayEntries.forEach { (label, dayKey) ->
              TextButton(
                onClick = {
                  Log.i(TAG_TIMELINE, "Moving photo ${photo?.id} to '$label'")
                  if (photo != null) moveUnassignedPhotoToDay(photo, dayKey)
                },
                modifier = Modifier.fillMaxWidth()
              ) { Text(label, modifier = Modifier.fillMaxWidth()) }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showMoveToDayPicker = false }) { Text("Cancel") }
        }
      )
    }

  }
}

@Composable
private fun DayHeader(
  dayNumber:Int,
  date:String,
  isBeforeAfter:Boolean = false,
  placeCount:Int,
  photoCount:Int,
  expanded:Boolean,
  onToggle:() -> Unit,
  onAddPhoto:() -> Unit,
) {
  val locale = LocalLocale.current.platformLocale
  val isBefore = date == OfflineStore.BEFORE_KEY
  val isAfter = date == OfflineStore.AFTER_KEY

  val display = when {
    isBefore -> "Photos before the trip dates"
    isAfter -> "Photos after the trip dates"
    else -> try {
      val d = LocalDate.parse(date)
      val weekday = d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
      val md = d.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
      "$weekday, $md"
    } catch (_:Exception) {
      date
    }
  }

  val title = when {
    isBefore -> "Before Trip"
    isAfter -> "After Trip"
    else -> "Day $dayNumber"
  }

  Surface(
    tonalElevation = 1.dp, modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onToggle)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null
      )
      Spacer(Modifier.width(8.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(display, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text(
        "$placeCount places · $photoCount photos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.width(8.dp))
      IconButton(onClick = onAddPhoto) {
        Icon(
          Icons.Default.AddPhotoAlternate, contentDescription = "Add photo to day", tint = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}

@Composable
private fun TransportBetweenRow(
  place:PlaceWithPhotos,
  onEdit:() -> Unit,
) {
  val mode = TransportMode.fromStored(place.transportFromPrevious)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 48.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
      .clickable(onClick = onEdit), verticalAlignment = Alignment.CenterVertically
  ) {
    HorizontalDivider(
      modifier = Modifier
        .weight(1f)
        .padding(end = 8.dp), color = MaterialTheme.colorScheme.outlineVariant
    )
    Surface(
      shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f), modifier = Modifier.clickable(onClick = onEdit)
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (mode != null) "${mode.emoji} ${mode.label}" else "＋ Transport", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer
        )
      }
    }
    HorizontalDivider(
      modifier = Modifier
        .weight(1f)
        .padding(start = 8.dp), color = MaterialTheme.colorScheme.outlineVariant
    )
  }
}

@Preview(showBackground = true)
@Composable
fun TimelineScreenPreview() {
  JournalPixTheme {
    TimelineScreen(
      navController = rememberNavController(), tripId = "demo-trip-1"
    )
  }
}

@Preview(showBackground = true)
@Composable
fun DayHeaderPreview() {
  JournalPixTheme {
    DayHeader(dayNumber = 1, date = "2025-05-15", isBeforeAfter = false, placeCount = 3, photoCount = 12, expanded = true, onToggle = {}, onAddPhoto = {})
  }
}
