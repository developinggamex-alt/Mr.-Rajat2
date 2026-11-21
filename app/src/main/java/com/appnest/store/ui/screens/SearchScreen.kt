package com.appnest.store.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.appnest.store.model.ActiveDownloadTask
import com.appnest.store.model.AppListing
import com.appnest.store.model.UiState
import com.appnest.store.ui.components.AppListingCard
import com.appnest.store.ui.components.CategoryChipsRow
import com.appnest.store.ui.components.EmptyStateCard
import com.appnest.store.ui.theme.NestBright
import com.appnest.store.ui.theme.NestLine
import com.appnest.store.ui.theme.NestPanel

@Composable
fun SearchScreen(
    query: String,
    recentSearches: List<String>,
    selectedCategory: String,
    catalogState: UiState<List<AppListing>>,
    activeDownloads: Map<String, ActiveDownloadTask>,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onOpenAppDetail: (String) -> Unit,
    onDownloadApp: (AppListing) -> Unit
) {
    val apps = (catalogState as? UiState.Success)?.data.orEmpty()
    val categories = remember(apps) {
        apps.mapNotNull { it.category.trim().takeIf { c -> c.isNotEmpty() } }
            .distinct()
            .sorted()
    }
    val results = remember(apps, query, selectedCategory) {
        val term = query.trim().lowercase()
        apps.filter { app ->
            val matchesCat = selectedCategory.isEmpty() || app.category == selectedCategory
            val matchesTerm = term.isEmpty() || listOf(
                app.name,
                app.category,
                app.developer,
                app.tagline,
                app.description
            ).any { it.lowercase().contains(term) }
            matchesCat && matchesTerm
        }
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
                    text = "Search",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search apps and games") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(13.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitSearch(query) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NestPanel,
                        unfocusedContainerColor = NestPanel,
                        focusedBorderColor = NestBright,
                        unfocusedBorderColor = NestLine
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input")
                )
            }

            if (recentSearches.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Recent searches",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recentSearches, key = { it }) { term ->
                                AssistChip(
                                    onClick = { onQueryChange(term) },
                                    label = { Text(term) },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = NestPanel,
                                        labelColor = MaterialTheme.colorScheme.onBackground
                                    ),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                )
                            }
                        }
                    }
                }
            }

            if (categories.isNotEmpty()) {
                item {
                    CategoryChipsRow(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelectCategory = onSelectCategory
                    )
                }
            }

            item {
                Text(
                    text = "Results",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            when (catalogState) {
                is UiState.Loading -> {
                    item {
                        EmptyStateCard(
                            title = "Loading catalog",
                            detail = "Connecting to Firebase…"
                        )
                    }
                }
                is UiState.Error -> {
                    item {
                        EmptyStateCard(
                            title = "Catalog unavailable",
                            detail = catalogState.message
                        )
                    }
                }
                is UiState.Success -> {
                    if (results.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No matching apps",
                                detail = "Try another search term or clear the active category filter."
                            )
                        }
                    } else {
                        items(results, key = { it.id }) { app ->
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
