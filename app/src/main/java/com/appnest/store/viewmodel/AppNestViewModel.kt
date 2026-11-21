package com.appnest.store.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appnest.store.data.ApkDownloadManager
import com.appnest.store.data.CatalogRepository
import com.appnest.store.data.UserDataRepository
import com.appnest.store.model.ActiveDownloadTask
import com.appnest.store.model.AppListing
import com.appnest.store.model.AppListingInput
import com.appnest.store.model.AppUrlUtils
import com.appnest.store.model.TrackedApp
import com.appnest.store.model.TrackedStatus
import com.appnest.store.model.UiState
import com.appnest.store.model.UserPreferences
import com.appnest.store.model.UserProfile
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface AppNestEffect {
    data class ShowMessage(val message: String) : AppNestEffect
    data class OpenExternalUrl(val url: String) : AppNestEffect
    data class InstallApkFile(val file: File) : AppNestEffect
    data object NavigateToDownloads : AppNestEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppNestViewModel(
    private val catalogRepository: CatalogRepository,
    private val userDataRepository: UserDataRepository,
    val downloadManager: ApkDownloadManager,
    private val currentUser: FirebaseUser
) : ViewModel() {

    private val uid: String = currentUser.uid

    private val _effects = MutableSharedFlow<AppNestEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<AppNestEffect> = _effects.asSharedFlow()

    private val _selectedCategory = MutableStateFlow("")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _adminFeedback = MutableStateFlow<String?>(null)
    val adminFeedback: StateFlow<String?> = _adminFeedback.asStateFlow()

    private val _isSavingAdmin = MutableStateFlow(false)
    val isSavingAdmin: StateFlow<Boolean> = _isSavingAdmin.asStateFlow()

    private var updateAlertShown = false

    val isAdmin: StateFlow<Boolean> = catalogRepository.observeIsAdmin(currentUser)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = currentUser.email.equals("developinggamex@gmail.com", ignoreCase = true) &&
                currentUser.isEmailVerified
        )

    val publishedAppsState: StateFlow<UiState<List<AppListing>>> =
        catalogRepository.observePublishedApps()
            .map<List<AppListing>, UiState<List<AppListing>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing published apps", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Couldn't load catalog."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val draftAppsState: StateFlow<UiState<List<AppListing>>> =
        isAdmin.flatMapLatest { admin ->
            if (!admin) {
                flowOf(UiState.Success(emptyList()))
            } else {
                catalogRepository.observeDraftApps()
                    .map<List<AppListing>, UiState<List<AppListing>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = unwrapFirestoreException(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing drafts", rootError)
                        emit(UiState.Error(rootError.localizedMessage ?: "Couldn't load drafts."))
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val userProfile: StateFlow<UserProfile?> =
        userDataRepository.observeUserProfile(uid)
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing user profile", firestoreError ?: error)
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = null
            )

    val trackedApps: StateFlow<List<TrackedApp>> =
        userDataRepository.observeTrackedApps(uid)
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing tracked apps", firestoreError ?: error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    val preferences: StateFlow<UserPreferences> = combine(
        userDataRepository.observeLocalPreferences(),
        userProfile
    ) { local, profile ->
        if (profile != null) {
            UserPreferences(
                autoCheckUpdates = profile.autoCheckUpdates,
                wifiOnlyDownloads = profile.wifiOnlyDownloads,
                notificationsEnabled = profile.notificationsEnabled,
                recentSearches = if (profile.recentSearches.isNotEmpty()) {
                    profile.recentSearches
                } else {
                    local.recentSearches
                }
            )
        } else {
            local
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UserPreferences()
    )

    val activeDownloads: StateFlow<Map<String, ActiveDownloadTask>> = downloadManager.activeTasks

    val availableUpdates: StateFlow<List<AppListing>> = combine(
        publishedAppsState,
        trackedApps
    ) { catalogState, tracked ->
        val apps = (catalogState as? UiState.Success)?.data ?: return@combine emptyList()
        val trackedMap = tracked.associateBy { it.id }
        apps.filter { app ->
            val current = trackedMap[app.id]?.version
            AppUrlUtils.isNewerNumericVersion(app.version, current)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            userDataRepository.ensureUserProfile(
                userId = uid,
                defaultDisplayName = currentUser.displayName ?: currentUser.email ?: "AppNest User"
            )
        }
        viewModelScope.launch {
            combine(preferences, availableUpdates) { prefs, updates ->
                prefs to updates
            }.collect { (prefs, updates) ->
                if (prefs.autoCheckUpdates && !updateAlertShown && updates.isNotEmpty()) {
                    updateAlertShown = true
                    _effects.tryEmit(
                        AppNestEffect.ShowMessage(
                            "${updates.size} app update${if (updates.size > 1) "s" else ""} available in Settings → App Updates."
                        )
                    )
                }
            }
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun submitSearch(term: String) {
        val clean = term.trim()
        _searchQuery.value = term
        if (clean.isEmpty()) return
        viewModelScope.launch {
            userDataRepository.saveRecentSearch(uid, clean, preferences.value.recentSearches)
        }
    }

    fun startDownload(app: AppListing, forceDrive: Boolean = false) {
        val prefs = preferences.value
        val alreadyRunning = activeDownloads.value.containsKey(app.id)
        if (alreadyRunning) {
            _effects.tryEmit(AppNestEffect.NavigateToDownloads)
            return
        }

        if (!forceDrive && app.validDirectApkUrl() != null) {
            _effects.tryEmit(AppNestEffect.NavigateToDownloads)
        }

        downloadManager.startDirectOrDriveDownload(
            scope = viewModelScope,
            app = app,
            forceDrive = forceDrive,
            wifiOnly = prefs.wifiOnlyDownloads,
            notificationsEnabled = prefs.notificationsEnabled,
            onCompleted = { bytes, fileName, packageName, apkFile ->
                userDataRepository.recordDownload(
                    userId = uid,
                    app = app,
                    bytes = bytes,
                    localFileName = fileName,
                    packageName = packageName
                )
                _effects.tryEmit(AppNestEffect.ShowMessage("${app.name} downloaded. Opening installer…"))
                _effects.tryEmit(AppNestEffect.InstallApkFile(apkFile))
            },
            onFallbackDriveUrl = { driveUrl ->
                _effects.tryEmit(AppNestEffect.ShowMessage("Opening Google Drive download…"))
                _effects.tryEmit(AppNestEffect.OpenExternalUrl(driveUrl))
            },
            onMessage = { msg ->
                _effects.tryEmit(AppNestEffect.ShowMessage(msg))
            }
        )
    }

    fun togglePauseDownload(appId: String) {
        downloadManager.togglePause(appId)
    }

    fun cancelDownload(appId: String) {
        downloadManager.cancelDownload(appId)
        _effects.tryEmit(AppNestEffect.ShowMessage("Download cancelled."))
    }

    fun installFromTracked(tracked: TrackedApp) {
        val file = downloadManager.findLocalApk(tracked.localFileName)
        if (file != null) {
            _effects.tryEmit(AppNestEffect.InstallApkFile(file))
        } else {
            _effects.tryEmit(AppNestEffect.ShowMessage("APK file not cached locally. Tap Reinstall."))
        }
    }

    fun openTrackedOrCatalogApp(app: AppListing?, tracked: TrackedApp?) {
        val pkg = tracked?.packageName?.takeIf { it.isNotBlank() }
        if (pkg != null && downloadManager.launchInstalledPackage(pkg)) {
            return
        }
        val localFile = downloadManager.findLocalApk(tracked?.localFileName)
        if (localFile != null) {
            _effects.tryEmit(AppNestEffect.InstallApkFile(localFile))
            return
        }
        val openUrl = app?.validOpenUrl()
        if (openUrl != null) {
            _effects.tryEmit(AppNestEffect.OpenExternalUrl(openUrl))
            return
        }
        _effects.tryEmit(AppNestEffect.ShowMessage("Install the APK first to launch ${tracked?.name ?: app?.name ?: "this app"}."))
    }

    fun markInstalled(app: AppListing) {
        viewModelScope.launch {
            val fileName = AppUrlUtils.sanitizeApkFileName(app.name)
            val localFile = downloadManager.findLocalApk(fileName)
            val pkg = localFile?.let { downloadManager.extractApkPackageName(it) }
            val result = userDataRepository.markInstalled(
                userId = uid,
                appId = app.id,
                name = app.name,
                version = app.version,
                bytes = localFile?.length() ?: app.sizeBytes,
                localFileName = localFile?.name,
                packageName = pkg
            )
            result.onSuccess {
                _effects.tryEmit(AppNestEffect.ShowMessage("Marked ${app.name} as installed on this device."))
            }.onFailure { err ->
                _effects.tryEmit(AppNestEffect.ShowMessage(err.localizedMessage ?: "Couldn't mark installed."))
            }
        }
    }

    fun markInstalledFromHistory(tracked: TrackedApp) {
        viewModelScope.launch {
            val result = userDataRepository.markInstalled(
                userId = uid,
                appId = tracked.id,
                name = tracked.name,
                version = tracked.version,
                bytes = tracked.bytes,
                localFileName = tracked.localFileName.takeIf { it.isNotBlank() },
                packageName = tracked.packageName.takeIf { it.isNotBlank() }
            )
            result.onSuccess {
                _effects.tryEmit(AppNestEffect.ShowMessage("Marked ${tracked.name} as installed."))
            }.onFailure { err ->
                _effects.tryEmit(AppNestEffect.ShowMessage(err.localizedMessage ?: "Couldn't update status."))
            }
        }
    }

    fun unmarkInstalled(tracked: TrackedApp) {
        viewModelScope.launch {
            val hasHistory = tracked.downloadedAt > 0L || tracked.bytes > 0L
            userDataRepository.unmarkInstalled(uid, tracked.id, hasHistory)
                .onSuccess {
                    _effects.tryEmit(AppNestEffect.ShowMessage("Removed ${tracked.name} from Installed."))
                }
                .onFailure { err ->
                    _effects.tryEmit(AppNestEffect.ShowMessage(err.localizedMessage ?: "Couldn't unmark."))
                }
        }
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            userDataRepository.updateDisplayName(uid, name)
                .onSuccess {
                    _effects.tryEmit(AppNestEffect.ShowMessage("Profile saved."))
                }
                .onFailure { err ->
                    _effects.tryEmit(AppNestEffect.ShowMessage(err.localizedMessage ?: "Couldn't save profile."))
                }
        }
    }

    fun updateAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            updateAlertShown = false
            userDataRepository.updatePreference(uid, autoCheckUpdates = enabled)
        }
    }

    fun updateWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            userDataRepository.updatePreference(uid, wifiOnlyDownloads = enabled)
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userDataRepository.updatePreference(uid, notificationsEnabled = enabled)
        }
    }

    fun clearApkCache() {
        val deleted = downloadManager.clearDownloadedApks()
        _effects.tryEmit(
            AppNestEffect.ShowMessage(
                if (deleted > 0) "Cleared $deleted cached APK file${if (deleted == 1) "" else "s"}."
                else "APK cache is already empty."
            )
        )
    }

    fun clearAdminFeedback() {
        _adminFeedback.value = null
    }

    fun saveAdminListing(
        existingId: String?,
        wasPublished: Boolean?,
        input: AppListingInput,
        publish: Boolean,
        onSaved: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSavingAdmin.value = true
            _adminFeedback.value = null
            val result = catalogRepository.saveListing(
                existingId = existingId,
                wasPublished = wasPublished,
                input = input,
                publish = publish
            )
            _isSavingAdmin.value = false
            result.onSuccess { id ->
                val message = if (publish) {
                    "Published to the user catalog."
                } else {
                    "Saved as a private draft and removed from the public catalog."
                }
                _adminFeedback.value = message
                _effects.tryEmit(AppNestEffect.ShowMessage(message))
                onSaved(id)
            }.onFailure { err ->
                val message = err.localizedMessage ?: "Couldn't save listing."
                _adminFeedback.value = message
                _effects.tryEmit(AppNestEffect.ShowMessage(message))
            }
        }
    }

    fun deleteAdminListing(
        id: String,
        published: Boolean,
        onDeleted: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSavingAdmin.value = true
            _adminFeedback.value = null
            val result = catalogRepository.deleteListing(id, published)
            _isSavingAdmin.value = false
            result.onSuccess {
                _adminFeedback.value = "Listing deleted."
                _effects.tryEmit(AppNestEffect.ShowMessage("Listing deleted."))
                onDeleted()
            }.onFailure { err ->
                val message = err.localizedMessage ?: "Couldn't delete listing."
                _adminFeedback.value = message
                _effects.tryEmit(AppNestEffect.ShowMessage(message))
            }
        }
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private companion object {
        const val TAG = "AppNestVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
