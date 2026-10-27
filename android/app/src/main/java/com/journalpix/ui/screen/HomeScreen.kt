package com.journalpix.ui.screen

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.journalpix.ui.theme.JournalPixTheme
import com.journalpix.models.TripResponse
import com.journalpix.models.TripStatus
import com.journalpix.viewmodel.AppSession
import com.journalpix.viewmodel.AuthViewModel
import com.journalpix.viewmodel.TripViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = remember { AuthViewModel() }
    val tripViewModel: TripViewModel = remember { TripViewModel() }
    var showCreateTripDialog by remember { mutableStateOf(false) }
    var newTripTitle by remember { mutableStateOf("") }
    var startDateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var endDateText by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }

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

    // Always read from shared session (not a per-screen ViewModel instance)
    val token = AppSession.currentUser.value?.token ?: AuthViewModel.DEMO_TOKEN

    LaunchedEffect(token) {
        // Demo token → offline sample trips; never shows network errors for demo
        tripViewModel.getTrips(token)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("JournalPix") },
                actions = {
                    IconButton(onClick = { navController.navigate("about") }) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                    IconButton(onClick = {
                        authViewModel.logout()
                        navController.navigate("auth") {
                            popUpTo(0) { inclusive = true }
                        }
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateTripDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Trip")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (tripViewModel.isLoading.value) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                if (tripViewModel.trips.value.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "No trips",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No trips yet")
                            Text("Create your first trip!")
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(tripViewModel.trips.value) { trip ->
                            TripCard(trip = trip, onClick = {
                                navController.navigate("timeline/${trip.id}")
                            })
                        }
                    }
                }
            }
        }
    }

    if (showCreateTripDialog) {
        AlertDialog(
            onDismissRequest = { showCreateTripDialog = false },
            title = { Text("Create New Trip") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTripTitle,
                        onValueChange = { newTripTitle = it },
                        label = { Text("Trip Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = startDateText,
                        onValueChange = { startDateText = it },
                        label = { Text("Start Date (yyyy-MM-dd)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                showDatePicker(startDateText) { selectedDate ->
                                    startDateText = selectedDate.toString()
                                }
                            }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select start date")
                            }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = endDateText,
                        onValueChange = { endDateText = it },
                        label = { Text("End Date (yyyy-MM-dd)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                showDatePicker(endDateText) { selectedDate ->
                                    endDateText = selectedDate.toString()
                                }
                            }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select end date")
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newTripTitle.isNotBlank()) {
                        val request = com.journalpix.models.CreateTripRequest(
                            title = newTripTitle.trim(),
                            description = null,
                            startDate = startDateText.ifBlank { null },
                            endDate = endDateText.ifBlank { null },
                            status = TripStatus.PLANNED
                        )
                        tripViewModel.createTrip(token, request) {
                            showCreateTripDialog = false
                            newTripTitle = ""
                            tripViewModel.getTrips(token)
                        }
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTripDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Only show non-demo network errors
    tripViewModel.errorMessage.value?.let { error ->
        if (!AuthViewModel.isDemoToken(token)) {
            AlertDialog(
                onDismissRequest = { tripViewModel.errorMessage.value = null },
                title = { Text("Error") },
                text = { Text(error) },
                confirmButton = {
                    Button(onClick = { tripViewModel.errorMessage.value = null }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
fun TripCard(trip: TripResponse, onClick: () -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    val startDate = trip.startDate?.let { LocalDate.parse(it) }
    val endDate = trip.endDate?.let { LocalDate.parse(it) }

    val statusContainerColor = when (trip.status) {
        TripStatus.PLANNED -> MaterialTheme.colorScheme.primaryContainer
        TripStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondaryContainer
        TripStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
        TripStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer
    }
    val statusContentColor = when (trip.status) {
        TripStatus.PLANNED -> MaterialTheme.colorScheme.onPrimaryContainer
        TripStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onSecondaryContainer
        TripStatus.COMPLETED -> MaterialTheme.colorScheme.onTertiaryContainer
        TripStatus.CANCELLED -> MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = trip.title,
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = trip.description ?: "No description",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                startDate?.let { start ->
                    Text(
                        text = start.format(formatter),
                        style = MaterialTheme.typography.bodySmall
                    )
                    endDate?.let { end ->
                        Text(" - ")
                        Text(
                            text = end.format(formatter),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                Text(
                    text = "${trip.placesCount ?: 0} places",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "${trip.photosCount ?: 0} photos",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = trip.status.name,
                style = MaterialTheme.typography.labelMedium,
                color = statusContentColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusContainerColor)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    JournalPixTheme {
        HomeScreen(navController = rememberNavController())
    }
}

@Preview(showBackground = true)
@Composable
fun TripCardPreview() {
    JournalPixTheme {
        TripCard(
            trip = TripResponse(
                id = "1",
                userId = "u1",
                title = "Tokyo & Kyoto Trip",
                description = "Exploring Japan in spring",
                startDate = "2025-04-01",
                endDate = "2025-04-14",
                status = TripStatus.IN_PROGRESS,
                placesCount = 8,
                photosCount = 42,
                createdAt = "",
                updatedAt = ""
            ),
            onClick = {}
        )
    }
}
