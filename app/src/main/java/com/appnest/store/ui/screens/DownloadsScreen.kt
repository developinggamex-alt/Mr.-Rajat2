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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appnest.store.R
import com.appnest.store.model.ActiveDownloadTask
import com.appnest.store.model.AppListing
import com.appnest.store.model.AppUrlUtils
import com.appnest.store.model.TrackedApp
import com.appnest.store.model.TrackedStatus
import com.appnest.store.ui.components.EmptyStateCard
import com.appnest.store.ui.theme.NestBright
import com.appnest.store.ui.theme.NestChipActiveBg
import com.appnest.store.ui.theme.NestChipActiveBorder
import com.appnest.store.ui.theme.NestChipActiveText
import com.appnest.store.ui.theme.NestLine
import com.appnest.store.ui.theme.NestOnPrimary
import com.appnest.store.ui.theme.NestPanel
import java.text.DateFormat
import java.util.Date

private enum class DownloadsTab(val label: String) {
    ACTIVE("Downloading"),
    INSTALLED("Installed"),
    HISTORY("History")
}

@Composable
fun DownloadsScreen(
    activeDownloads: Map<String, ActiveDownloadTask>,
    trackedApps: List<TrackedApp>,
    catalogApps: List<AppListing>,
    hasLocalApk: (String?) -> Boolean,
    onTogglePause: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onInstallApk: (TrackedApp) -> Unit,
    onReinstallApp: (AppListing) -> Unit,
    onOpenApp: (AppListing?, TrackedApp?) -> Unit,
    onMarkInstalled: (TrackedApp) -> Unit,
    onUnmarkInstalled: (TrackedApp) -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(DownloadsTab.ACTIVE) }
    val catalogMap = remember(catalogApps) { catalogApps.associateBy { it.id } }
    val installedList = remember(trackedApps) {
        trackedApps.filter { it.resolvedStatus() == TrackedStatus.INSTALLED }
            .sortedByDescending { it.installedAt }
    }
    val historyList = remember(trackedApps) {
        trackedApps.filter { it.downloadedAt > 0L || it.bytes > 0L }
            .sortedByDescending { it.downloadedAt }
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
                    text = stringResource(R.string.downloads_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DownloadsTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        val count = when (tab) {
                            DownloadsTab.ACTIVE -> activeDownloads.size
                            DownloadsTab.INSTALLED -> installedList.size
                            DownloadsTab.HISTORY -> historyList.size
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { currentTab = tab },
                            label = {
                                Text(if (count > 0) "${tab.label} ($count)" else tab.label)
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = NestPanel,
                                labelColor = MaterialTheme.colorScheme.onBackground,
                                selectedContainerColor = NestChipActiveBg,
                                selectedLabelColor = NestChipActiveText
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = NestLine,
                                selectedBorderColor = NestChipActiveBorder
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("downloads_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            when (currentTab) {
                DownloadsTab.ACTIVE -> {
                    val activeList = activeDownloads.values.toList()
                    if (activeList.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No active downloads",
                                detail = "Direct APK downloads show live speed, pause, and resume here."
                            )
                        }
                    } else {
                        items(activeList, key = { it.appId }) { task ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = NestPanel),
                                border = BorderStroke(1.dp, NestLine),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = task.appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = task.statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val fraction = task.progressFraction
                                    if (fraction != null) {
                                        LinearProgressIndicator(
                                            progress = { fraction },
                                            color = NestBright,
                                            trackColor = NestLine,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            color = NestBright,
                                            trackColor = NestLine,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(
                                            onClick = { onTogglePause(task.appId) },
                                            shape = RoundedCornerShape(11.dp),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("pause_download_${task.appId}")
                                        ) {
                                            Text(if (task.paused) "Resume" else "Pause")
                                        }
                                        OutlinedButton(
                                            onClick = { onCancelDownload(task.appId) },
                                            shape = RoundedCornerShape(11.dp),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("cancel_download_${task.appId}")
                                        ) {
                                            Text("Cancel")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                DownloadsTab.INSTALLED -> {
                    if (installedList.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No apps marked installed",
                                detail = "After installing on Android, mark the app as installed from History or its details page."
                            )
                        }
                    } else {
                        items(installedList, key = { it.id }) { item ->
                            val catalogApp = catalogMap[item.id]
                            val hasUpdate = catalogApp != null &&
                                AppUrlUtils.isNewerNumericVersion(catalogApp.version, item.version) &&
                                catalogApp.isDownloadable()

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = NestPanel),
                                border = BorderStroke(1.dp, NestLine),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = if (item.version.isNotBlank()) "Version ${item.version}"
                                        else "Marked installed on this device",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (catalogApp?.validOpenUrl() != null || item.packageName.isNotBlank()) {
                                            OutlinedButton(
                                                onClick = { onOpenApp(catalogApp, item) },
                                                shape = RoundedCornerShape(11.dp),
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Text("Open")
                                            }
                                        }
                                        if (hasUpdate && catalogApp != null) {
                                            Button(
                                                onClick = { onReinstallApp(catalogApp) },
                                                shape = RoundedCornerShape(11.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = NestBright,
                                                    contentColor = NestOnPrimary
                                                ),
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Text("Update", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { onUnmarkInstalled(item) },
                                            shape = RoundedCornerShape(11.dp),
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text("Unmark")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                DownloadsTab.HISTORY -> {
                    if (historyList.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No completed in-app downloads",
                                detail = "Drive downloads opened in the browser appear in your device's system downloads."
                            )
                        }
                    } else {
                        items(historyList, key = { it.id }) { item ->
                            val catalogApp = catalogMap[item.id]
                            val cachedLocally = hasLocalApk(item.localFileName)
                            val dateStr = if (item.downloadedAt > 0L) {
                                DateFormat.getDateInstance(DateFormat.MEDIUM)
                                    .format(Date(item.downloadedAt))
                            } else ""

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = NestPanel),
                                border = BorderStroke(1.dp, NestLine),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            item.bytes.takeIf { it > 0L }?.let { AppUrlUtils.formatBytes(it) },
                                            item.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                                            dateStr.takeIf { it.isNotBlank() }
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (cachedLocally) {
                                            Button(
                                                onClick = { onInstallApk(item) },
                                                shape = RoundedCornerShape(11.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = NestBright,
                                                    contentColor = NestOnPrimary
                                                ),
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Text("Install APK", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        if (catalogApp != null && catalogApp.isDownloadable()) {
                                            OutlinedButton(
                                                onClick = { onReinstallApp(catalogApp) },
                                                shape = RoundedCornerShape(11.dp),
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Text("Reinstall")
                                            }
                                        }
                                        if (item.resolvedStatus() != TrackedStatus.INSTALLED) {
                                            OutlinedButton(
                                                onClick = { onMarkInstalled(item) },
                                                shape = RoundedCornerShape(11.dp),
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Text("Mark installed")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
