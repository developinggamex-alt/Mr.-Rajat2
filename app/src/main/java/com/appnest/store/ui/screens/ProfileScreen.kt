package com.appnest.store.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appnest.store.model.AppUrlUtils
import com.appnest.store.model.TrackedApp
import com.appnest.store.ui.components.AppThumbnail
import com.appnest.store.ui.theme.NestAccent
import com.appnest.store.ui.theme.NestBright
import com.appnest.store.ui.theme.NestChipActiveBg
import com.appnest.store.ui.theme.NestChipActiveBorder
import com.appnest.store.ui.theme.NestChipActiveText
import com.appnest.store.ui.theme.NestLine
import com.appnest.store.ui.theme.NestOnPrimary
import com.appnest.store.ui.theme.NestPanel

@Composable
fun ProfileScreen(
    displayName: String,
    email: String,
    isAdmin: Boolean,
    trackedApps: List<TrackedApp>,
    updateCount: Int,
    onSaveDisplayName: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdminPanel: () -> Unit
) {
    var nameInput by remember(displayName) { mutableStateOf(displayName) }
    LaunchedEffect(displayName) {
        if (nameInput.isBlank() && displayName.isNotBlank()) {
            nameInput = displayName
        }
    }

    val downloadedCount = remember(trackedApps) {
        trackedApps.count { it.downloadedAt > 0L || it.bytes > 0L }
    }
    val totalBytes = remember(trackedApps) {
        trackedApps.sumOf { it.bytes }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 880.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NestPanel),
                    border = BorderStroke(1.dp, NestLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AppThumbnail(
                                name = displayName.ifBlank { email.ifBlank { "AN" } },
                                iconUrl = null,
                                size = 56.dp,
                                cornerRadius = 16.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName.ifBlank { "Your account" },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (email.isNotBlank()) {
                                    Text(
                                        text = email,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { if (it.length <= 60) nameInput = it },
                            label = { Text("Display name") },
                            singleLine = true,
                            shape = RoundedCornerShape(13.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NestBright,
                                unfocusedBorderColor = NestLine
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_name_input")
                        )

                        Button(
                            onClick = { onSaveDisplayName(nameInput) },
                            enabled = nameInput.trim().isNotEmpty(),
                            shape = RoundedCornerShape(11.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NestBright,
                                contentColor = NestOnPrimary
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("profile_save_button")
                        ) {
                            Text("Save profile", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NestPanel),
                    border = BorderStroke(1.dp, NestLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "$downloadedCount downloaded",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Tracked on this account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = AppUrlUtils.formatBytes(totalBytes),
                                style = MaterialTheme.typography.titleMedium,
                                color = NestAccent
                            )
                            Text(
                                text = "Transferred",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isAdmin) {
                item {
                    Card(
                        onClick = onOpenAdminPanel,
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NestChipActiveBg),
                        border = BorderStroke(1.dp, NestChipActiveBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_admin_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AdminPanelSettings,
                                contentDescription = null,
                                tint = NestChipActiveText,
                                modifier = Modifier.size(26.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AppNest Admin Panel",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NestChipActiveText
                                )
                                Text(
                                    text = "Publish new apps, manage drafts, and edit catalog listings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NestChipActiveText.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenDownloads,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("profile_downloads_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Downloads")
                    }
                    OutlinedButton(
                        onClick = onOpenUpdates,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("profile_updates_button")
                    ) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (updateCount > 0) "Updates ($updateCount)" else "Updates")
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("profile_settings_button")
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Settings")
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
