src = open('TimelineScreen.kt').read()

# 1) Add unassignedPhotosLoading state
old1 = """var selectedDayPhoto by remember {mutableStateOf<PhotoResponse?>(null)}
  val expandedDays = remember {mutableStateMapOf<String, Boolean>()}"""
new1 = """var selectedDayPhoto by remember {mutableStateOf<PhotoResponse?>(null)}
  val expandedDays = remember {mutableStateMapOf<String, Boolean>()}
  // Loading state for unassigned photos pool
  var unassignedPhotosLoading by remember {mutableStateOf(false)}"""
assert src.count(old1) == 1
src = src.replace(old1, new1)

# 2) Add LaunchedEffect for unassignedPhotosVersion
old2 = """  // The timeline NEVER creates or edits places from map callbacks — a map pick opens the
  // place detail in create mode; this screen only reloads when the store changes:
  LaunchedEffect(AppSession.placesVersion.value) {
    Log.i(TAG_TIMELINE, "placesVersion=${AppSession.placesVersion.value} \u2014 reloading timeline from OfflineStore")
    refresh()
  }

  val timeline = timelineViewModel.timeline.value"""
new2 = """  // The timeline NEVER creates or edits places from map callbacks — a map pick opens the
  // place detail in create mode; this screen only reloads when the store changes:
  LaunchedEffect(AppSession.placesVersion.value) {
    Log.i(TAG_TIMELINE, "placesVersion=${AppSession.placesVersion.value} \u2014 reloading timeline from OfflineStore")
    refresh()
  }
  // Also reload when unassigned photos change (mass upload adds photos to the pool)
  LaunchedEffect(AppSession.unassignedPhotosVersion.value) {
    Log.i(TAG_TIMELINE, "unassignedPhotosVersion=${AppSession.unassignedPhotosVersion.value} \u2014 refreshing for pool update")
    refresh()
  }

  val timeline = timelineViewModel.timeline.value"""
assert src.count(old2) == 1
src = src.replace(old2, new2)

# 3) Set loading state in startMassUpload
old3 = """fun startMassUpload() {
    val needed = requiredMediaPermissions()
    if (needed.isNotEmpty()) massUploadPermissionLauncher.launch(needed.toTypedArray())
    else pickMultipleImagesLauncher.launch("image/*")
  }"""
new3 = """fun startMassUpload() {
    unassignedPhotosLoading = true
    val needed = requiredMediaPermissions()
    if (needed.isNotEmpty()) massUploadPermissionLauncher.launch(needed.toTypedArray())
    else pickMultipleImagesLauncher.launch("image/*")
  }"""
assert src.count(old3) == 1
src = src.replace(old3, new3)

# 4) Clear loading in pickMultipleImagesLauncher
old4 = """    refresh()
  }

  val massUploadPermissionLauncher = rememberLauncherForActivityResult("""
new4 = """    refresh()
    unassignedPhotosLoading = false
  }

  val massUploadPermissionLauncher = rememberLauncherForActivityResult("""
assert src.count(old4) == 1
src = src.replace(old4, new4)

# 5) Update unassigned photos section - condition
old5 = """            // Unassigned photos section
            val unassignedPhotos = OfflineStore.getUnassignedPhotos()
            if (unassignedPhotos.isNotEmpty()) {"""
new5 = """            // Unassigned photos section
            val unassignedPhotos = OfflineStore.getUnassignedPhotos()
            val showUnassignedSection = unassignedPhotos.isNotEmpty() || unassignedPhotosLoading
            if (showUnassignedSection) {"""
assert src.count(old5) == 1
src = src.replace(old5, new5)

# 6) Update items - use a list that includes placeholders when loading
old6 = """                    items(
                      items = unassignedPhotos, key = {it.id}) {photo ->"""
new6 = """                    items(
                      items = if (unassignedPhotos.isNotEmpty()) unassignedPhotos else (1..4).map { "loading-" + it },
                      key = { it }) { item ->"""
assert src.count(old6) == 1
src = src.replace(old6, new6)

# 7) Update clickable - check if PhotoResponse
old7 = """                            Log.i(TAG_TIMELINE, "Unassigned photo tapped: " + photo.id + " \u2014 showing EXIF dialog")
                            selectedUnassignedPhoto = photo
                          }"""
new7 = """                            Log.i(TAG_TIMELINE, "Unassigned photo tapped: " + item.id + " \u2014 showing EXIF dialog")
                            selectedUnassignedPhoto = item
                          }"""
assert src.count(old7) == 1
src = src.replace(old7, new7)

# 8) Update Box - remove background and add skeleton for loading items
old8 = """                        Box(
                          modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.1f))
                        ) {
                          AsyncImage(
                            model = photoThumbRequest(LocalContext.current, photo, 240),
                            contentDescription = photo.caption ?: "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                          )
                        }"""
new8 = """                        Box(
                          modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                          ) {
                            if (item is PhotoResponse) {
                              AsyncImage(
                                model = photoThumbRequest(LocalContext.current, item, 240),
                                contentDescription = item.caption ?: "Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                              )
                            } else {
                              // Skeleton loading placeholder
                              androidx.compose.foundation.Skeleton(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(8.dp)
                              )
                            }
                          }"""
assert src.count(old8) == 1
src = src.replace(old8, new8)

# 9) Update Text - show date only for real photos
old9 = """                        Text(
                          text = photo.takenAt?.timeLabel() ?: "No date", style = MaterialTheme.typography.bodySmall, maxLines = 1
                        )"""
new9 = """                        Text(
                          text = if (item is PhotoResponse) item.takenAt?.timeLabel() ?: "No date" else "",
                          style = MaterialTheme.typography.bodySmall, maxLines = 1
                        )"""
assert src.count(old9) == 1
src = src.replace(old9, new9)

open('TimelineScreen.kt','w').write(src)
print("ok")