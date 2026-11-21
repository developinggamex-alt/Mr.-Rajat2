package com.appnest.store.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.appnest.store.model.ActiveDownloadTask
import com.appnest.store.model.AppListing
import com.appnest.store.model.TrackedApp
import com.appnest.store.model.TrackedStatus
import com.appnest.store.model.UiState
import com.appnest.store.ui.components.AppThumbnail
import com.appnest.store.ui.components.EmptyStateCard
import com.appnest.store.ui.theme.NestBright
import com.appnest.store.ui.theme.NestLine
import com.appnest.store.ui.theme.NestOnPrimary
import com.appnest.store.ui.theme.NestPanel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppDetailScreen(
    appId: String,
    catalogState: UiState<List<AppListing>>,
    trackedApp: TrackedApp?,
    activeDownload: ActiveDownloadTask?,
    onDownloadDirect: (AppListing) -> Unit,
    onDownloadDrive: (AppListing) -> Unit,
    onOpenApp: (AppListing, TrackedApp?) -> Unit,
    onMarkInstalled: (AppListing) -> Unit
) {
    val apps = (catalogState as? UiState.Success)?.data.orEmpty()
    val app = remember(apps, appId) { apps.firstOrNull { it.id == appId } }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (app == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp)
                    .padding(18.dp)
            ) {
                EmptyStateCard(
                    title = if (catalogState is UiState.Loading) "Loading app…" else "App unavailable",
                    detail = if (catalogState is UiState.Error) catalogState.message else ""
                )
            }
            return@Box
        }

        val previews = remember(app) { app.validScreenshots() }
        val stats = remember(app) {
            val nf = NumberFormat.getIntegerInstance(Locale.US)
            buildList {
                if (app.rating > 0.0) add("★ ${String.format(Locale.US, "%.1f", app.rating)}")
                if (app.sizeBytes > 0L) add(app.formattedSize())
                if (app.reviewCount > 0L) add("${nf.format(app.reviewCount)} reviews")
                if (app.downloadCount > 0L) add("${nf.format(app.downloadCount)} downloads")
                if (app.version.isNotBlank()) add("Version ${app.version}")
            }
        }
        val isMarkedInstalled = trackedApp?.resolvedStatus() == TrackedStatus.INSTALLED

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 880.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    AppThumbnail(
                        name = app.name,
                        iconUrl = app.validIconUrl(),
                        size = 76.dp,
                        cornerRadius = 18.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (app.developer.isNotBlank()) {
                            Text(
                                text = app.developer,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (app.category.isNotBlank()) {
                            Text(
                                text = app.category,
                                style = MaterialTheme.typography.labelMedium,
                                color = NestBright
                            )
                        }
                    }
                }
            }

            if (stats.isNotEmpty()) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        stats.forEach { stat ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = NestPanel,
                                border = BorderStroke(1.dp, NestLine)
                            ) {
                                Text(
                                    text = stat,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (app.isDownloadable()) {
                        Button(
                            onClick = { onDownloadDirect(app) },
                            shape = RoundedCornerShape(11.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NestBright,
                                contentColor = NestOnPrimary
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("detail_download_button")
                        ) {
                            Text(
                                text = if (activeDownload != null) "Downloading…" else "Download APK",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = "Download unavailable",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (app.validDirectApkUrl() != null && app.validDriveLink() != null) {
                        OutlinedButton(
                            onClick = { onDownloadDrive(app) },
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("detail_drive_button")
                        ) {
                            Text("Download via Drive")
                        }
                    }

                    if (app.validOpenUrl() != null || trackedApp?.packageName?.isNotBlank() == true) {
                        OutlinedButton(
                            onClick = { onOpenApp(app, trackedApp) },
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("detail_open_button")
                        ) {
                            Text("Open")
                        }
                    }

                    OutlinedButton(
                        onClick = { onMarkInstalled(app) },
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_mark_installed_button")
                    ) {
                        Text(if (isMarkedInstalled) "Installed ✓" else "Mark installed")
                    }
                }
            }

            if (previews.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Previews",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(previews, key = { index, url -> "$index-$url" }) { idx, url ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, NestLine),
                                    color = NestPanel
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "${app.name} screenshot ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(190.dp)
                                            .height(300.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (app.description.isNotBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "About this app",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = app.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
