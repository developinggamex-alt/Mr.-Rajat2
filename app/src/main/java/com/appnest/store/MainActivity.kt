package com.appnest.store

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.appnest.store.data.ApkDownloadManager
import com.appnest.store.data.AuthRepository
import com.appnest.store.data.authStateFlow
import com.appnest.store.data.CatalogRepository
import com.appnest.store.data.UserDataRepository
import com.appnest.store.data.appNestDataStore
import com.appnest.store.model.UiState
import com.appnest.store.ui.components.AppNestTopBar
import com.appnest.store.ui.navigation.AppRoute
import com.appnest.store.ui.screens.AdminScreen
import com.appnest.store.ui.screens.AppDetailScreen
import com.appnest.store.ui.screens.AuthScreen
import com.appnest.store.ui.screens.DownloadsScreen
import com.appnest.store.ui.screens.HelpScreen
import com.appnest.store.ui.screens.HomeScreen
import com.appnest.store.ui.screens.PrivacyScreen
import com.appnest.store.ui.screens.ProfileScreen
import com.appnest.store.ui.screens.SearchScreen
import com.appnest.store.ui.screens.SettingsScreen
import com.appnest.store.ui.screens.UpdatesScreen
import com.appnest.store.ui.theme.AppNestTheme
import com.appnest.store.ui.theme.NestBg
import com.appnest.store.ui.theme.NestMuted
import com.appnest.store.ui.theme.NestNavActiveBg
import com.appnest.store.ui.theme.NestNavActiveText
import com.appnest.store.ui.theme.NestSurface
import com.appnest.store.viewmodel.AppNestEffect
import com.appnest.store.viewmodel.AppNestViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

class MainActivity : ComponentActivity() {

    private var pendingApkFile: File? = null
    private lateinit var downloadManager: ApkDownloadManager

    private val unknownAppsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val file = pendingApkFile
        if (file != null && downloadManager.canInstallUnknownApps()) {
            pendingApkFile = null
            startActivity(downloadManager.createInstallApkIntent(file))
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(NestBg.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(NestSurface.toArgb())
        )
        downloadManager = ApkDownloadManager(applicationContext)

        setContent {
            AppNestTheme {
                AppContent(
                    downloadManager = downloadManager,
                    onRequestInstallApk = { file, showPermissionPrompt ->
                        if (downloadManager.canInstallUnknownApps()) {
                            startActivity(downloadManager.createInstallApkIntent(file))
                        } else {
                            pendingApkFile = file
                            showPermissionPrompt()
                        }
                    },
                    onOpenUnknownAppsSettings = {
                        unknownAppsLauncher.launch(downloadManager.unknownAppsSettingsIntent())
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !downloadManager.hasNotificationPermission()
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenExternalUrl = { url ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                )
            }
        }
    }
}

@Composable
private fun AppContent(
    downloadManager: ApkDownloadManager,
    onRequestInstallApk: (File, showPermissionPrompt: () -> Unit) -> Unit,
    onOpenUnknownAppsSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenExternalUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            AuthRepository.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedAppShell(
                currentUser = user,
                downloadManager = downloadManager,
                onSignOut = {
                    AuthRepository.signOut(
                        credentialManager = credentialManager,
                        scope = scope
                    )
                },
                onRequestInstallApk = onRequestInstallApk,
                onOpenUnknownAppsSettings = onOpenUnknownAppsSettings,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenExternalUrl = onOpenExternalUrl
            )
        }
    }
}

private data class TopLevelNavItem(
    val route: AppRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
private fun AuthenticatedAppShell(
    currentUser: FirebaseUser,
    downloadManager: ApkDownloadManager,
    onSignOut: () -> Unit,
    onRequestInstallApk: (File, showPermissionPrompt: () -> Unit) -> Unit,
    onOpenUnknownAppsSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenExternalUrl: (String) -> Unit
) {
    val viewModel: AppNestViewModel = viewModel(
        key = currentUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val auth = Firebase.auth
                AppNestViewModel(
                    catalogRepository = CatalogRepository(db, auth),
                    userDataRepository = UserDataRepository(db, auth, app.appNestDataStore),
                    downloadManager = downloadManager,
                    currentUser = currentUser
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    var showUnknownAppsDialog by remember { mutableStateOf(false) }

    val publishedState by viewModel.publishedAppsState.collectAsStateWithLifecycle()
    val draftState by viewModel.draftAppsState.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val trackedApps by viewModel.trackedApps.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val availableUpdates by viewModel.availableUpdates.collectAsStateWithLifecycle()
    val adminFeedback by viewModel.adminFeedback.collectAsStateWithLifecycle()
    val isSavingAdmin by viewModel.isSavingAdmin.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AppNestEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is AppNestEffect.OpenExternalUrl -> onOpenExternalUrl(effect.url)
                is AppNestEffect.InstallApkFile -> {
                    onRequestInstallApk(effect.file) {
                        showUnknownAppsDialog = true
                    }
                }
                AppNestEffect.NavigateToDownloads -> {
                    navController.navigate(AppRoute.Downloads) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    }

    if (showUnknownAppsDialog) {
        AlertDialog(
            onDismissRequest = { showUnknownAppsDialog = false },
            title = { Text("Allow install unknown apps") },
            text = {
                Text("To install downloaded APKs directly from AppNest, enable 'Allow from this source' in Android Settings.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnknownAppsDialog = false
                        onOpenUnknownAppsSettings()
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnknownAppsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val topLevelItems = remember {
        listOf(
            TopLevelNavItem(AppRoute.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
            TopLevelNavItem(AppRoute.Search, R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search),
            TopLevelNavItem(AppRoute.Downloads, R.string.nav_downloads, Icons.Filled.Download, Icons.Outlined.Download),
            TopLevelNavItem(AppRoute.Profile, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person)
        )
    }

    val isTopLevel = destination?.let { dest ->
        dest.hasRoute<AppRoute.Home>() ||
            dest.hasRoute<AppRoute.Search>() ||
            dest.hasRoute<AppRoute.Downloads>() ||
            dest.hasRoute<AppRoute.Profile>()
    } ?: true

    val topBarTitle = when {
        destination?.hasRoute<AppRoute.AppDetail>() == true -> "App Details"
        destination?.hasRoute<AppRoute.Settings>() == true -> "Settings"
        destination?.hasRoute<AppRoute.Updates>() == true -> "App Updates"
        destination?.hasRoute<AppRoute.Privacy>() == true -> "Privacy Policy"
        destination?.hasRoute<AppRoute.Help>() == true -> "Help & Support"
        destination?.hasRoute<AppRoute.Admin>() == true -> "Admin Panel"
        else -> null
    }

    val catalogList = (publishedState as? UiState.Success)?.data.orEmpty()
    val effectiveDisplayName = userProfile?.displayName?.takeIf { it.isNotBlank() }
        ?: currentUser.displayName
        ?: ""

    fun navigateTopLevel(route: AppRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                AppNestTopBar(
                    showBack = !isTopLevel,
                    title = topBarTitle,
                    isAdmin = isAdmin,
                    onBackClick = { navController.popBackStack() },
                    onBrandClick = { navigateTopLevel(AppRoute.Home) },
                    onAdminClick = { navController.navigate(AppRoute.Admin) },
                    onSettingsClick = { navController.navigate(AppRoute.Settings) }
                )
            },
            bottomBar = {
                if (!useRail && isTopLevel) {
                    NavigationBar(
                        containerColor = NestSurface,
                        contentColor = NestMuted,
                        tonalElevation = 0.dp
                    ) {
                        topLevelItems.forEach { item ->
                            val selected = destination?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NestNavActiveText,
                                    selectedTextColor = NestNavActiveText,
                                    indicatorColor = NestNavActiveBg,
                                    unselectedIconColor = NestMuted,
                                    unselectedTextColor = NestMuted
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && isTopLevel) {
                    NavigationRail(
                        containerColor = NestSurface,
                        contentColor = NestMuted,
                        windowInsets = WindowInsets(0)
                    ) {
                        topLevelItems.forEach { item ->
                            val selected = destination?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = NestNavActiveText,
                                    selectedTextColor = NestNavActiveText,
                                    indicatorColor = NestNavActiveBg,
                                    unselectedIconColor = NestMuted,
                                    unselectedTextColor = NestMuted
                                )
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoute.Home,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<AppRoute.Home> {
                        HomeScreen(
                            displayName = effectiveDisplayName,
                            isAdmin = isAdmin,
                            catalogState = publishedState,
                            selectedCategory = selectedCategory,
                            activeDownloads = activeDownloads,
                            onSelectCategory = viewModel::selectCategory,
                            onOpenSearch = { navigateTopLevel(AppRoute.Search) },
                            onOpenAdminPanel = { navController.navigate(AppRoute.Admin) },
                            onOpenAppDetail = { appId ->
                                navController.navigate(AppRoute.AppDetail(appId))
                            },
                            onDownloadApp = { app -> viewModel.startDownload(app) }
                        )
                    }

                    composable<AppRoute.Search> {
                        SearchScreen(
                            query = searchQuery,
                            recentSearches = preferences.recentSearches,
                            selectedCategory = selectedCategory,
                            catalogState = publishedState,
                            activeDownloads = activeDownloads,
                            onQueryChange = viewModel::updateSearchQuery,
                            onSubmitSearch = viewModel::submitSearch,
                            onSelectCategory = viewModel::selectCategory,
                            onOpenAppDetail = { appId ->
                                navController.navigate(AppRoute.AppDetail(appId))
                            },
                            onDownloadApp = { app -> viewModel.startDownload(app) }
                        )
                    }

                    composable<AppRoute.Downloads> {
                        DownloadsScreen(
                            activeDownloads = activeDownloads,
                            trackedApps = trackedApps,
                            catalogApps = catalogList,
                            hasLocalApk = { fileName -> downloadManager.findLocalApk(fileName) != null },
                            onTogglePause = viewModel::togglePauseDownload,
                            onCancelDownload = viewModel::cancelDownload,
                            onInstallApk = viewModel::installFromTracked,
                            onReinstallApp = { app -> viewModel.startDownload(app) },
                            onOpenApp = viewModel::openTrackedOrCatalogApp,
                            onMarkInstalled = viewModel::markInstalledFromHistory,
                            onUnmarkInstalled = viewModel::unmarkInstalled
                        )
                    }

                    composable<AppRoute.Profile> {
                        ProfileScreen(
                            displayName = effectiveDisplayName,
                            email = currentUser.email.orEmpty(),
                            isAdmin = isAdmin,
                            trackedApps = trackedApps,
                            updateCount = availableUpdates.size,
                            onSaveDisplayName = viewModel::updateDisplayName,
                            onOpenDownloads = { navigateTopLevel(AppRoute.Downloads) },
                            onOpenUpdates = { navController.navigate(AppRoute.Updates) },
                            onOpenSettings = { navController.navigate(AppRoute.Settings) },
                            onOpenAdminPanel = { navController.navigate(AppRoute.Admin) }
                        )
                    }

                    composable<AppRoute.AppDetail> { entry ->
                        val route = entry.toRoute<AppRoute.AppDetail>()
                        val tracked = trackedApps.firstOrNull { it.id == route.appId }
                        AppDetailScreen(
                            appId = route.appId,
                            catalogState = publishedState,
                            trackedApp = tracked,
                            activeDownload = activeDownloads[route.appId],
                            onDownloadDirect = { app -> viewModel.startDownload(app, forceDrive = false) },
                            onDownloadDrive = { app -> viewModel.startDownload(app, forceDrive = true) },
                            onOpenApp = { app, trackedItem ->
                                viewModel.openTrackedOrCatalogApp(app, trackedItem)
                            },
                            onMarkInstalled = viewModel::markInstalled
                        )
                    }

                    composable<AppRoute.Settings> {
                        var canInstallUnknown by remember {
                            mutableStateOf(downloadManager.canInstallUnknownApps())
                        }
                        DisposableEffect(Unit) {
                            canInstallUnknown = downloadManager.canInstallUnknownApps()
                            onDispose { }
                        }
                        SettingsScreen(
                            preferences = preferences,
                            canInstallUnknownApps = canInstallUnknown,
                            onOpenDownloads = { navigateTopLevel(AppRoute.Downloads) },
                            onOpenUpdates = { navController.navigate(AppRoute.Updates) },
                            onToggleAutoUpdates = viewModel::updateAutoCheckUpdates,
                            onToggleWifiOnly = viewModel::updateWifiOnly,
                            onToggleNotifications = { enabled ->
                                if (enabled) onRequestNotificationPermission()
                                viewModel.updateNotificationsEnabled(enabled)
                            },
                            onOpenUnknownAppsSettings = onOpenUnknownAppsSettings,
                            onClearApkCache = viewModel::clearApkCache,
                            onOpenPrivacy = { navController.navigate(AppRoute.Privacy) },
                            onOpenHelp = { navController.navigate(AppRoute.Help) },
                            onSignOut = onSignOut
                        )
                    }

                    composable<AppRoute.Updates> {
                        UpdatesScreen(
                            updates = availableUpdates,
                            activeDownloads = activeDownloads,
                            onOpenAppDetail = { appId ->
                                navController.navigate(AppRoute.AppDetail(appId))
                            },
                            onDownloadUpdate = { app -> viewModel.startDownload(app) }
                        )
                    }

                    composable<AppRoute.Privacy> {
                        PrivacyScreen()
                    }

                    composable<AppRoute.Help> {
                        HelpScreen()
                    }

                    composable<AppRoute.Admin> {
                        AdminScreen(
                            isAdmin = isAdmin,
                            publishedState = publishedState,
                            draftState = draftState,
                            isSaving = isSavingAdmin,
                            feedback = adminFeedback,
                            onClearFeedback = viewModel::clearAdminFeedback,
                            onSaveListing = viewModel::saveAdminListing,
                            onDeleteListing = viewModel::deleteAdminListing
                        )
                    }
                }
            }
        }
    }
}
