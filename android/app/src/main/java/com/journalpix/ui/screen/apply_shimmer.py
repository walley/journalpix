src = open('TimelineScreen.kt').read()

# 1) Replace unassignedPhotosLoading with pendingUnassignedCount
old1 = """// Loading state for unassigned photos pool
  var unassignedPhotosLoading by remember {mutableStateOf(false)}"""
new1 = """// Loading state for unassigned photos pool — holds pending placeholder count
  // that show as shimmering skeletons immediately while photos process
  var pendingUnassignedCount by remember {mutableStateOf(0)}"""
assert src.count(old1) == 1
src = src.replace(old1, new1)

# 2) In mass upload callback: add placeholders immediately, then replace as done
old2 = """  ) { uris: List<Uri> ->
    Log.i(TAG_TIMELINE, "Mass upload: ${uris.size} picture(s) selected — adding to unassigned pool")
    val counter = java.util.concurrent.atomic.AtomicInteger(uris.size)
    uris.forEach { uri ->
      runCatching {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      photoViewModel.addUnassignedPhotoOffline(context, uri) {
        if (counter.decrementAndGet() == 0) {
          // Last photo done — refresh on main thread
          refresh()
          unassignedPhotosLoading = false
        }
      }
    }
  }"""
new2 = """  ) { uris: List<Uri> ->
    Log.i(TAG_TIMELINE, "Mass upload: ${uris.size} picture(s) selected — adding to unassigned pool")
    // Show skeleton placeholders IMMEDIATELY for instant visual feedback
    pendingUnassignedCount = uris.size
    val counter = java.util.concurrent.atomic.AtomicInteger(uris.size)
    uris.forEach { uri ->
      runCatching {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      photoViewModel.addUnassignedPhotoOffline(context, uri) {
        // Each photo done: decrement pending count, the real photo is now in store
        if (counter.decrementAndGet() >= 0) {
          pendingUnassignedCount = counter.get()
        }
        if (counter.get() == 0) {
          // All done — force a refresh to sync with store
          refresh()
        }
      }
    }
  }"""
assert src.count(old2) == 1
src = src.replace(old2, new2)

# 3) Update startMassUpload
old3 = """fun startMassUpload() {
    val needed = requiredMediaPermissions()
    if (needed.isNotEmpty()) massUploadPermissionLauncher.launch(needed.toTypedArray())
    else pickMultipleImagesLauncher.launch("image/*")
  }"""
new3 = """fun startMassUpload() {
    pendingUnassignedCount = 0  // will be set in callback after picker returns
    val needed = requiredMediaPermissions()
    if (needed.isNotEmpty()) massUploadPermissionLauncher.launch(needed.toTypedArray())
    else pickMultipleImagesLauncher.launch("image/*")
  }"""
assert src.count(old3) == 1
src = src.replace(old3, new3)

# 4) Update the unassigned photos section to show placeholders + real photos
old4 = """            // Unassigned photos section
            val unassignedPhotos = OfflineStore.getUnassignedPhotos()
            val showUnassignedSection = unassignedPhotos.isNotEmpty() || unassignedPhotosLoading
            if (showUnassignedSection) {
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
                      items = if (unassignedPhotos.isNotEmpty()) unassignedPhotos else (1..4).map { "loading-" + it },
                      key = { (it as Any).let { if (it is PhotoResponse) it.id else it.toString() } }) { item: Any ->"""
new4 = """            // Unassigned photos section
            val unassignedPhotos = OfflineStore.getUnassignedPhotos()
            // Combine real photos from store + pending skeleton placeholders
            val showUnassignedSection = unassignedPhotos.isNotEmpty() || pendingUnassignedCount > 0
            if (showUnassignedSection) {
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
                      items = unassignedPhotos + (1..pendingUnassignedCount).map { "pending-" + it },
                      key = { (it as Any).let { if (it is PhotoResponse) it.id else it.toString() } }) { item: Any ->"""
assert src.count(old4) == 1
src = src.replace(old4, new4)

# 5) Update the Box/AsyncImage to handle pending items with shimmer placeholder
old5 = """                          ) {
                            if (item is PhotoResponse) {
                              AsyncImage(
                                model = photoThumbRequest(LocalContext.current, (item as PhotoResponse), 240),
                                contentDescription = item.caption ?: "Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                              )
                            } else {
                              // Loading placeholder
                              Box(
                                modifier = Modifier
                                  .fillMaxSize()
                                  .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                  )
                              )
                            }
                          }"""
new5 = """                          ) {
                            if (item is PhotoResponse) {
                              AsyncImage(
                                model = photoThumbRequest(LocalContext.current, item, 240),
                                contentDescription = item.caption ?: "Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                              )
                            } else {
                              // Shimmering skeleton placeholder — shows immediately while photo processes
                              ShimmerPlaceholder()
                            }
                          }"""
assert src.count(old5) == 1
src = src.replace(old5, new5)

# 6) Add ShimmerPlaceholder composable at the end of the file
shimmer_composable = """

@Composable
fun ShimmerPlaceholder() {
    // Subtle pulse animation for skeleton loading effect
    val alpha = androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animationSpec = androidx.compose.animation.core.tween(1000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        initialValue = 0.4f
    ).value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .graphicsLayer { this.alpha = alpha }
    )
}
"""
if 'fun ShimmerPlaceholder()' not in src:
    src = src.rstrip() + shimmer_composable

open('TimelineScreen.kt','w').write(src)
print("ok")