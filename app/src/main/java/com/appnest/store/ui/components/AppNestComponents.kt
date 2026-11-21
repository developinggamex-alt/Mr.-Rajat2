package com.appnest.store.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.appnest.store.R
import com.appnest.store.model.AppListing
import com.appnest.store.ui.theme.NestAccent
import com.appnest.store.ui.theme.NestBg
import com.appnest.store.ui.theme.NestBright
import com.appnest.store.ui.theme.NestChipActiveBg
import com.appnest.store.ui.theme.NestChipActiveBorder
import com.appnest.store.ui.theme.NestChipActiveText
import com.appnest.store.ui.theme.NestHeroEnd
import com.appnest.store.ui.theme.NestHeroMid
import com.appnest.store.ui.theme.NestHeroStart
import com.appnest.store.ui.theme.NestLine
import com.appnest.store.ui.theme.NestLineStrong
import com.appnest.store.ui.theme.NestOnPrimary
import com.appnest.store.ui.theme.NestPanel
import com.appnest.store.ui.theme.NestThumbBg
import com.appnest.store.ui.theme.NestThumbText

@Composable
fun AppNestTopBar(
    showBack: Boolean = false,
    title: String? = null,
    isAdmin: Boolean = false,
    onBackClick: () -> Unit = {},
    onBrandClick: () -> Unit = {},
    onAdminClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NestBg)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (showBack) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("top_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onBrandClick)
                            .padding(end = 8.dp)
                            .testTag("brand_logo_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF322951))
                                .border(1.dp, NestLineStrong, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Diamond,
                                contentDescription = stringResource(R.string.cd_appnest_logo),
                                tint = NestAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = buildAnnotatedString {
                                append("App")
                                withStyle(SpanStyle(color = NestAccent)) {
                                    append("Nest")
                                }
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isAdmin && !showBack) {
                    Surface(
                        onClick = onAdminClick,
                        shape = RoundedCornerShape(12.dp),
                        color = NestChipActiveBg,
                        border = BorderStroke(1.dp, NestChipActiveBorder),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("top_admin_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AdminPanelSettings,
                                contentDescription = stringResource(R.string.cd_admin_panel),
                                tint = NestChipActiveText,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Admin",
                                style = MaterialTheme.typography.labelMedium,
                                color = NestChipActiveText
                            )
                        }
                    }
                }

                if (!showBack) {
                    Surface(
                        onClick = onSettingsClick,
                        shape = RoundedCornerShape(12.dp),
                        color = NestPanel,
                        border = BorderStroke(1.dp, NestLine),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("top_settings_button")
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.cd_settings),
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = NestLine, thickness = 1.dp)
    }
}

@Composable
fun AppThumbnail(
    name: String,
    iconUrl: String?,
    size: Dp = 54.dp,
    cornerRadius: Dp = 14.dp
) {
    val initials = name.trim().take(2).uppercase().ifEmpty { "AN" }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(NestThumbBg)
            .border(1.dp, NestLineStrong.copy(alpha = 0.45f), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = "$name icon",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = NestThumbText
            )
        }
    }
}

@Composable
fun HeroFeaturedCard(
    featuredApp: AppListing?,
    onViewApp: (AppListing) -> Unit,
    onBrowseClick: () -> Unit
) {
    val bannerUrl = featuredApp?.validBannerUrl()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(216.dp)
            .clip(RoundedCornerShape(23.dp))
            .border(1.dp, Color(0xFF514471), RoundedCornerShape(23.dp))
            .background(
                Brush.linearGradient(
                    listOf(NestHeroStart, NestHeroMid, NestHeroEnd)
                )
            )
            .testTag("hero_featured_card")
    ) {
        if (bannerUrl != null) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = "${featuredApp.name} banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner),
                contentDescription = stringResource(R.string.cd_hero_banner),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xF0140E22),
                        0.60f to Color(0xCC171024),
                        1.0f to Color(0x4D171024)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            if (featuredApp != null) {
                Text(
                    text = "FEATURED",
                    style = MaterialTheme.typography.labelSmall,
                    color = NestAccent
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = featuredApp.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (featuredApp.tagline.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = featuredApp.tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onViewApp(featuredApp) },
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NestBright,
                        contentColor = NestOnPrimary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("hero_view_app_button")
                ) {
                    Text("View app", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "APPNEST STORE",
                    style = MaterialTheme.typography.labelSmall,
                    color = NestAccent
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.home_hero_default_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_hero_default_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onBrowseClick,
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NestBright,
                        contentColor = NestOnPrimary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("hero_browse_button")
                ) {
                    Text("Search catalog", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CategoryChipsRow(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    if (categories.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        val allItems = listOf("") + categories
        items(allItems, key = { it.ifEmpty { "__all__" } }) { label ->
            val selected = selectedCategory == label
            FilterChip(
                selected = selected,
                onClick = { onSelectCategory(label) },
                label = {
                    Text(
                        text = label.ifEmpty { "All" },
                        style = MaterialTheme.typography.labelMedium
                    )
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
                    .testTag("category_chip_${label.ifEmpty { "all" }}")
            )
        }
    }
}

@Composable
fun AppListingCard(
    app: AppListing,
    isDownloading: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NestPanel),
        border = BorderStroke(1.dp, NestLine),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_card_${app.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppThumbnail(name = app.name, iconUrl = app.validIconUrl())

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = app.summarySubtitle().ifBlank { app.developer }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (app.isDownloadable()) {
                Button(
                    onClick = onDownloadClick,
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDownloading) NestChipActiveBg else NestBright,
                        contentColor = if (isDownloading) NestChipActiveText else NestOnPrimary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("download_button_${app.id}")
                ) {
                    Text(
                        text = if (isDownloading) "Progress" else "Download",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    detail: String = "",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NestPanel),
        border = BorderStroke(1.dp, NestLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NestBright,
                        contentColor = NestOnPrimary
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
