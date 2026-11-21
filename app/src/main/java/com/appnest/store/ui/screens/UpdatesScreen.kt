package com.appnest.store.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.appnest.store.R
import com.appnest.store.model.ActiveDownloadTask
import com.appnest.store.model.AppListing
import com.appnest.store.ui.components.AppListingCard
import com.appnest.store.ui.components.EmptyStateCard

@Composable
fun UpdatesScreen(
    updates: List<AppListing>,
    activeDownloads: Map<String, ActiveDownloadTask>,
    onOpenAppDetail: (String) -> Unit,
    onDownloadUpdate: (AppListing) -> Unit
) {
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
                    text = stringResource(R.string.updates_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            item {
                Text(
                    text = "Newer catalog versions for your tracked apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (updates.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No newer versions available",
                        detail = "All tracked downloads and installed apps match the latest catalog version."
                    )
                }
            } else {
                items(updates, key = { it.id }) { app ->
                    AppListingCard(
                        app = app,
                        isDownloading = activeDownloads.containsKey(app.id),
                        onClick = { onOpenAppDetail(app.id) },
                        onDownloadClick = { onDownloadUpdate(app) }
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
