package com.appnest.store.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.appnest.store.R
import com.appnest.store.model.AppListing
import com.appnest.store.model.AppListingInput
import com.appnest.store.model.UiState
import com.appnest.store.ui.components.EmptyStateCard
import com.appnest.store.ui.theme.NestAccent
import com.appnest.store.ui.theme.NestBright
import com.appnest.store.ui.theme.NestChipActiveBg
import com.appnest.store.ui.theme.NestChipActiveBorder
import com.appnest.store.ui.theme.NestChipActiveText
import com.appnest.store.ui.theme.NestError
import com.appnest.store.ui.theme.NestLine
import com.appnest.store.ui.theme.NestOnPrimary
import com.appnest.store.ui.theme.NestPanel
import java.util.Locale

@Composable
fun AdminScreen(
    isAdmin: Boolean,
    publishedState: UiState<List<AppListing>>,
    draftState: UiState<List<AppListing>>,
    isSaving: Boolean,
    feedback: String?,
    onClearFeedback: () -> Unit,
    onSaveListing: (existingId: String?, wasPublished: Boolean?, input: AppListingInput, publish: Boolean, onSaved: (String) -> Unit) -> Unit,
    onDeleteListing: (id: String, published: Boolean, onDeleted: () -> Unit) -> Unit
) {
    if (!isAdmin) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(modifier = Modifier.widthIn(max = 600.dp)) {
                EmptyStateCard(
                    title = "Admin access required",
                    detail = "Sign in with a verified administrator account to manage AppNest listings."
                )
            }
        }
        return
    }

    val published = (publishedState as? UiState.Success)?.data.orEmpty()
    val drafts = (draftState as? UiState.Success)?.data.orEmpty()
    val allEntries = remember(published, drafts) {
        (published + drafts).sortedBy { it.name.lowercase(Locale.US) }
    }

    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedPublished by remember { mutableStateOf<Boolean?>(null) }
    var input by remember { mutableStateOf(AppListingInput()) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun loadNewListing() {
        selectedId = null
        selectedPublished = null
        input = AppListingInput()
        onClearFeedback()
    }

    fun selectListing(app: AppListing) {
        selectedId = app.id
        selectedPublished = app.published
        input = AppListingInput(
            name = app.name,
            category = app.category,
            developer = app.developer,
            version = app.version,
            tagline = app.tagline,
            description = app.description,
            driveUrl = app.driveUrl,
            downloadUrl = app.downloadUrl,
            iconUrl = app.iconUrl,
            bannerUrl = app.bannerUrl,
            openUrl = app.openUrl,
            screenshotsText = app.screenshots.joinToString("\n"),
            sizeMbText = if (app.sizeBytes > 0L) String.format(Locale.US, "%.2f", app.sizeBytes / 1_048_576.0) else "",
            ratingText = if (app.rating > 0.0) app.rating.toString() else "",
            reviewCountText = if (app.reviewCount > 0L) app.reviewCount.toString() else "",
            downloadCountText = if (app.downloadCount > 0L) app.downloadCount.toString() else "",
            featured = app.featured
        )
        onClearFeedback()
    }

    if (confirmDelete && selectedId != null && selectedPublished != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete listing?") },
            text = { Text("Remove ${input.name.ifBlank { "this listing" }} permanently from Firebase?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = selectedId!!
                        val pub = selectedPublished!!
                        confirmDelete = false
                        onDeleteListing(id, pub) { loadNewListing() }
                    }
                ) {
                    Text("Delete", color = NestError)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 760.dp

        if (wideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.width(260.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        AdminHeader(onNewListing = ::loadNewListing)
                    }
                    items(allEntries, key = { "${it.published}-${it.id}" }) { item ->
                        AdminCatalogRow(
                            app = item,
                            selected = item.id == selectedId,
                            onClick = { selectListing(item) }
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        AdminEditorCard(
                            isEditing = selectedId != null,
                            input = input,
                            isSaving = isSaving,
                            feedback = feedback,
                            onInputChange = { input = it },
                            onPublish = {
                                onSaveListing(selectedId, selectedPublished, input, true) { newId ->
                                    selectedId = newId
                                    selectedPublished = true
                                }
                            },
                            onSaveDraft = {
                                onSaveListing(selectedId, selectedPublished, input, false) { newId ->
                                    selectedId = newId
                                    selectedPublished = false
                                }
                            },
                            onDelete = if (selectedId != null) { { confirmDelete = true } } else null
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { AdminHeader(onNewListing = ::loadNewListing) }

                if (allEntries.isEmpty()) {
                    item {
                        Text(
                            text = "No listings yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(allEntries, key = { "${it.published}-${it.id}" }) { item ->
                        AdminCatalogRow(
                            app = item,
                            selected = item.id == selectedId,
                            onClick = { selectListing(item) }
                        )
                    }
                }

                item {
                    AdminEditorCard(
                        isEditing = selectedId != null,
                        input = input,
                        isSaving = isSaving,
                        feedback = feedback,
                        onInputChange = { input = it },
                        onPublish = {
                            onSaveListing(selectedId, selectedPublished, input, true) { newId ->
                                selectedId = newId
                                selectedPublished = true
                            }
                        },
                        onSaveDraft = {
                            onSaveListing(selectedId, selectedPublished, input, false) { newId ->
                                selectedId = newId
                                selectedPublished = false
                            }
                        },
                        onDelete = if (selectedId != null) { { confirmDelete = true } } else null
                    )
                }

                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun AdminHeader(onNewListing: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.admin_eyebrow),
            style = MaterialTheme.typography.labelSmall,
            color = NestAccent
        )
        Text(
            text = stringResource(R.string.admin_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Button(
            onClick = onNewListing,
            shape = RoundedCornerShape(11.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NestBright,
                contentColor = NestOnPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("admin_new_listing_button")
        ) {
            Text("New listing", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdminCatalogRow(
    app: AppListing,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) NestChipActiveBg else NestPanel,
        border = BorderStroke(1.dp, if (selected) NestChipActiveBorder else NestLine),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = app.name.ifBlank { "Untitled app" },
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) NestChipActiveText else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (app.published) "Published" else "Draft",
                style = MaterialTheme.typography.labelSmall,
                color = if (app.published) NestAccent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AdminEditorCard(
    isEditing: Boolean,
    input: AppListingInput,
    isSaving: Boolean,
    feedback: String?,
    onInputChange: (AppListingInput) -> Unit,
    onPublish: () -> Unit,
    onSaveDraft: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NestPanel),
        border = BorderStroke(1.dp, NestLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isEditing) "Edit listing" else "New listing",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            AdminField("App title *", input.name, { onInputChange(input.copy(name = it)) }, "admin_name_input")
            AdminField("Category *", input.category, { onInputChange(input.copy(category = it)) }, "admin_category_input")
            AdminField("Developer", input.developer, { onInputChange(input.copy(developer = it)) }, "admin_developer_input")
            AdminField("Version", input.version, { onInputChange(input.copy(version = it)) }, "admin_version_input", placeholder = "1.0.0")
            AdminField("Short tagline", input.tagline, { onInputChange(input.copy(tagline = it)) }, "admin_tagline_input")
            AdminField(
                label = "Description *",
                value = input.description,
                onValueChange = { onInputChange(input.copy(description = it)) },
                testTag = "admin_description_input",
                minLines = 4
            )
            AdminField(
                label = "Google Drive APK link",
                value = input.driveUrl,
                onValueChange = { onInputChange(input.copy(driveUrl = it)) },
                testTag = "admin_drive_url_input",
                placeholder = "https://drive.google.com/file/d/…"
            )
            AdminField(
                label = "Direct APK link",
                value = input.downloadUrl,
                onValueChange = { onInputChange(input.copy(downloadUrl = it)) },
                testTag = "admin_download_url_input",
                placeholder = "https://…"
            )
            AdminField("App icon URL", input.iconUrl, { onInputChange(input.copy(iconUrl = it)) }, "admin_icon_url_input")
            AdminField("Banner image URL", input.bannerUrl, { onInputChange(input.copy(bannerUrl = it)) }, "admin_banner_url_input")
            AdminField(
                label = "Screenshot URLs (one HTTPS link per line)",
                value = input.screenshotsText,
                onValueChange = { onInputChange(input.copy(screenshotsText = it)) },
                testTag = "admin_screenshots_input",
                minLines = 3
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    AdminField(
                        label = "Size (MB)",
                        value = input.sizeMbText,
                        onValueChange = { onInputChange(input.copy(sizeMbText = it)) },
                        testTag = "admin_size_input",
                        keyboardType = KeyboardType.Decimal
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AdminField(
                        label = "Rating (0–5)",
                        value = input.ratingText,
                        onValueChange = { onInputChange(input.copy(ratingText = it)) },
                        testTag = "admin_rating_input",
                        keyboardType = KeyboardType.Decimal
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    AdminField(
                        label = "Review count",
                        value = input.reviewCountText,
                        onValueChange = { onInputChange(input.copy(reviewCountText = it)) },
                        testTag = "admin_reviews_input",
                        keyboardType = KeyboardType.Number
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AdminField(
                        label = "Download count",
                        value = input.downloadCountText,
                        onValueChange = { onInputChange(input.copy(downloadCountText = it)) },
                        testTag = "admin_downloads_input",
                        keyboardType = KeyboardType.Number
                    )
                }
            }

            AdminField(
                label = "App open link",
                value = input.openUrl,
                onValueChange = { onInputChange(input.copy(openUrl = it)) },
                testTag = "admin_open_url_input",
                placeholder = "Optional HTTPS app link"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Feature on Home",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Switch(
                    checked = input.featured,
                    onCheckedChange = { onInputChange(input.copy(featured = it)) },
                    modifier = Modifier.testTag("admin_featured_switch")
                )
            }

            if (feedback != null) {
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NestAccent
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPublish,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NestBright,
                        contentColor = NestOnPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("admin_publish_button")
                ) {
                    Text("Publish", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onSaveDraft,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("admin_save_draft_button")
                ) {
                    Text("Save draft")
                }
            }

            if (onDelete != null) {
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("admin_delete_button")
                ) {
                    Text("Delete listing", color = NestError)
                }
            }
        }
    }
}

@Composable
private fun AdminField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    placeholder: String? = null,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        minLines = minLines,
        singleLine = minLines == 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NestBright,
            unfocusedBorderColor = NestLine
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}
