package com.journalpix.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("About JournalPix") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "App Icon",
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Name
            Text(
                text = "JournalPix",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Version
            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Description
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Travel Vlog & Journal",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "JournalPix is your ultimate travel companion. " +
                              "Create beautiful travel journals with photos, places, " +
                              "and routes. Plan your trips in advance or document " +
                              "your adventures as you go.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Features:",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FeatureItem("🗺️ Interactive Maps", "View your travel route with places as markers")
                    FeatureItem("📸 Photo Journal", "Upload photos with automatic geolocation extraction")
                    FeatureItem("📅 Timeline View", "See your journey organized by date")
                    FeatureItem("✏️ Editable Everything", "Edit trips, places, and photos anytime")
                    FeatureItem("🔒 Offline Support", "Access your data even without internet")
                    FeatureItem("💾 Multiple Databases", "Choose between SQLite or PostgreSQL")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Technology Stack
            Text(
                text = "Built With",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TechBadge("Kotlin", Color(0xFF7F52FF))
                TechBadge("Compose", Color(0xFF03DAC6))
                TechBadge("Rust", Color(0xFFDEA584))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TechBadge("Actix", Color(0xFF000000))
                TechBadge("Room", Color(0xFF03DAC6))
                TechBadge("Retrofit", Color(0xFF4CAF50))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Links Section
            Text(
                text = "Links",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinkCard(
                icon = Icons.Default.Link,
                title = "GitHub Repository",
                description = "View source code and contribute",
                url = "https://github.com/walley/journalpix"
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinkCard(
                icon = Icons.Default.Language,
                title = "Documentation",
                description = "Read the full documentation",
                url = "https://github.com/walley/journalpix#readme"
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinkCard(
                icon = Icons.Default.PrivacyTip,
                title = "Privacy Policy",
                description = "How we handle your data",
                url = "https://github.com/walley/journalpix/blob/main/PRIVACY.md"
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinkCard(
                icon = Icons.Default.Email,
                title = "Contact Us",
                description = "Get in touch with the team",
                url = "mailto:journalpix@example.com"
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Footer
            Text(
                text = "© 2024 JournalPix. All rights reserved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Made with ❤️ for travelers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun FeatureItem(title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TechBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkCard(icon: ImageVector, title: String, description: String, url: String) {
    Card(
        onClick = { /* Handle click - would need navigation or intent */ },
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium
                    )
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.rotate(180f)
            )
        }
    }
}
