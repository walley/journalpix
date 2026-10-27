package com.journalpix.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.journalpix.models.PhotoResponse
import com.journalpix.models.PlaceWithPhotos
import com.journalpix.viewmodel.PhotoViewModel

@Composable
fun PlaceTimelineRow(
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(lineColor)
                )
            }
        }
        Card(
            onClick = onPlaceClick,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            place.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        place.address?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "${place.photos.size} photos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onAddPhoto) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add photo")
                    }
                }
                if (place.photos.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        place.photos.take(4).forEach { photo ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(photo.thumbnailPath.ifBlank { photo.filePath })
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        }
                        if (place.photos.size > 4) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text("+${place.photos.size - 4}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceDetailSheet(
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
            Column(modifier = Modifier.fillMaxWidth()) {
                place.address?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${place.photos.size} photos",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onAddPhoto) {
                        Icon(Icons.Default.AddPhotoAlternate, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
                val photos = place.photos
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
                            "GPS: ${\"%.5f\".format(photo.latitude)}, ${\"%.5f\".format(photo.longitude)}",
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
