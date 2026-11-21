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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.appnest.store.R
import com.appnest.store.model.ActiveDownloadTask
import com.appnest.store.model.AppListing
import com.appnest.store.model.UiState
import com.appnest.store.ui.components.AppListingCard
import com.appnest.store.ui.components.CategoryChipsRow
import com.appnest.store.ui.components.EmptyStateCard
import com.appnest.store.ui.components.HeroFeaturedCard
import com.appnest.store.ui.theme.NestBright
import com.appnest.store.ui.theme.NestLine
import com.appnest.store.ui.theme.NestPanel

@Composable
fun HomeScreen(
    displayName: String?,
    isAdmin: Boolean,
    catalogState: UiState<List<AppListing>>,
    selectedCategory: String,
    activeDownloads: Map<String, ActiveDownloadTask>,
    onSelectCategory: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onOpenAppDetail: (String) -> Unit,
    onDownloadApp: (AppListing) -> Unit
) {
    val apps = (catalogState as? UiState.Success)?.data.orEmpty()
    val categories = remember(apps) {
        apps.mapNotNull { it.category.trim().takeIf { c -> c.isNotEmpty() } }
            .distinct()
            .sorted()
    }
    val featured = remember(apps) { apps.firstOrNull { it.featured } }
    val popular = remember(apps, selectedCategory) {
        apps.filter { selectedCategory.isEmpty() || it.category == selectedCategory }
            .sortedByDescending { it.downloadCount }
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
                val greeting = if (!displayName.isNullOrBlank()) {
                    "Welcome, $displayName"
                } else {
                    stringResource(R.string.home_discover_title)
                }
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = NestPanel,
                    border = BorderStroke(1.dp, NestLine),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onOpenSearch)
                        .testTag("home_search_shortcut")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.home_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                HeroFeaturedCard(
                    featuredApp = featured,
                    onViewApp = { app -> onOpenAppDetail(app.id) },
                    onBrowseClick = onOpenSearch
                )
            }

            if (categories.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Categories",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        CategoryChipsRow(
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onSelectCategory = onSelectCategory
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.home_popular_apps),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            when (catalogState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NestBright)
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        EmptyStateCard(
                            title = "Couldn't load catalog",
                            detail = catalogState.message
                        )
                    }
                }
                is UiState.Success -> {
                    if (apps.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No apps published yet",
                                detail = if (isAdmin) {
                                    "Published apps and games in this Firebase Firestore catalog will appear here. Open Admin Panel to publish your first listing."
                                } else {
                                    "Published apps and games will appear here."
                                },
                                actionLabel = if (isAdmin) "Open Admin Panel" else null,
                                onAction = if (isAdmin) onOpenAdminPanel else null
                            )
                        }
                    } else if (popular.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No apps in this category",
                                detail = "Try selecting All to view every listing."
                            )
                        }
                    } else {
                        items(popular, key = { it.id }) { app ->
                            AppListingCard(
                                app = app,
                                isDownloading = activeDownloads.containsKey(app.id),
                                onClick = { onOpenAppDetail(app.id) },
                                onDownloadClick = { onDownloadApp(app) }
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
