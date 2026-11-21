package com.appnest.store.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.appnest.store.R
import com.appnest.store.model.UserPreferences
import com.appnest.store.ui.theme.NestAccent
import com.appnest.store.ui.theme.NestLine
import com.appnest.store.ui.theme.NestPanel
import com.appnest.store.ui.theme.NestSuccess

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    canInstallUnknownApps: Boolean,
    onOpenDownloads: () -> Unit,
    onOpenUpdates: () -> Unit,
    onToggleAutoUpdates: (Boolean) -> Unit,
    onToggleWifiOnly: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onOpenUnknownAppsSettings: () -> Unit,
    onClearApkCache: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenHelp: () -> Unit,
    onSignOut: () -> Unit
) {
    var showStorageDetails by remember { mutableStateOf(false) }
    var showAboutDetails by remember { mutableStateOf(false) }

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
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Text(
                    text = "Downloads & Updates",
                    style = MaterialTheme.typography.headlineMedium,
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
                    Column {
                        SettingNavRow(
                            title = "My Downloads",
                            onClick = onOpenDownloads,
                            testTag = "settings_downloads_row"
                        )
                        HorizontalDivider(color = NestLine)
                        SettingNavRow(
                            title = "App Updates",
                            onClick = onOpenUpdates,
                            testTag = "settings_updates_row"
                        )
                        HorizontalDivider(color = NestLine)
                        SettingToggleRow(
                            title = "Check for app updates automatically",
                            subtitle = "Compare your downloaded and installed versions against the catalog.",
                            checked = preferences.autoCheckUpdates,
                            onCheckedChange = onToggleAutoUpdates,
                            testTag = "settings_auto_updates_switch"
                        )
                        HorizontalDivider(color = NestLine)
                        SettingToggleRow(
                            title = "Download over Wi-Fi only",
                            subtitle = "Block APK downloads when device is not connected to Wi-Fi.",
                            checked = preferences.wifiOnlyDownloads,
                            onCheckedChange = onToggleWifiOnly,
                            testTag = "settings_wifi_only_switch"
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.headlineMedium,
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
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable(onClick = onOpenUnknownAppsSettings)
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("settings_unknown_apps_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Install unknown apps",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Manage in Android system settings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (canInstallUnknownApps) "Allowed" else "Configure",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (canInstallUnknownApps) NestSuccess else NestAccent
                            )
                        }
                        HorizontalDivider(color = NestLine)
                        SettingToggleRow(
                            title = "Notifications",
                            subtitle = "Show download completion and update alerts.",
                            checked = preferences.notificationsEnabled,
                            onCheckedChange = onToggleNotifications,
                            testTag = "settings_notifications_switch"
                        )
                        HorizontalDivider(color = NestLine)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showStorageDetails = !showStorageDetails }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Storage & location",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (showStorageDetails) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "APK files are stored in AppNest's app-private Downloads directory and opened through FileProvider. AppNest does not request device location.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onClearApkCache,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Clear cached APK files")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.headlineMedium,
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
                    Column {
                        SettingNavRow(
                            title = "Privacy Policy",
                            onClick = onOpenPrivacy,
                            testTag = "settings_privacy_row"
                        )
                        HorizontalDivider(color = NestLine)
                        SettingNavRow(
                            title = "Help & Support",
                            onClick = onOpenHelp,
                            testTag = "settings_help_row"
                        )
                        HorizontalDivider(color = NestLine)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAboutDetails = !showAboutDetails }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "About AppNest",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "v2.0",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (showAboutDetails) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "AppNest is an app and game catalog powered by Firebase.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("settings_sign_out_button")
                ) {
                    Text("Sign out")
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SettingNavRow(
    title: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
