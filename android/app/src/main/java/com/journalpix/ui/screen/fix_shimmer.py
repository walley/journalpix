src = open('TimelineScreen.kt').read()

# 1) Replace unassignedPhotosLoading with pendingUnassignedCount
old1 = """// Loading state for unassigned photos pool
  var unassignedPhotosLoading by remember {mutableStateOf(false)}"""
new1 = """// Loading state for unassigned photos pool — holds pending placeholder count
  # that show as shimmering skeletons immediately while photos process
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
    # Show skeleton placeholders IMMEDIATELY for instant visual feedback
    pendingUnassignedCount = uris.size
    val counter = java.util.concurrent.atomic.AtomicInteger(uris.size)
    uris.forEach { uri ->
      runCatching {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      photoViewModel.addUnassignedPhotoOffline(context, uri) {
        # Each photo done: decrement pending count, the real photo is now in store
        if (counter.decrementAndGet() >= 0) {
          pendingUnassignedCount = counter.get()
        }
        if (counter.get() == 0) {
          # All done — force a refresh to sync with store
          refresh()
        }
      }
    }
  }"""
assert src.count(old2) == 1
src = src.replace(old2, new2)

# 3) Update the unassigned photos section to show placeholders + real photos
old3 = """            # Unassigned photos section
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
new3 = """            # Unassigned photos section
            val unassignedPhotos = OfflineStore.getUnassignedPhotos()
            # Combine real photos from store + pending skeleton placeholders
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
assert src.count(old3) == 1
src = src.replace(old3, new3)

# 4) Update the Box/AsyncImage to handle pending items with shimmer
old4 = """                          ) {
                            if (item is PhotoResponse) {
                              AsyncImage(
                                model = photoThumbRequest(LocalContext.current, (item as PhotoResponse), 240),
                                contentDescription = item.caption ?: "Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                              )
                            } else {
                              # Loading placeholder
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
new4 = """                          ) {
                            if (item is PhotoResponse) {
                              AsyncImage(
                                model = photoThumbRequest(LocalContext.current, item, 240),
                                contentDescription = item.caption ?: "Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                              )
                            } else {
                              # Shimmering skeleton placeholder — shows immediately while photo processes
                              androidx.compose.foundation.Shimmer(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(8.dp),
                                baseColor = MaterialTheme.colorScheme.surfaceVariant,
                                highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest
                              )
                            }
                          }"""
assert src.count(old4) == 1
src = src.replace(old4, new4)

open('TimelineScreen.kt','w').write(src)
print("ok")