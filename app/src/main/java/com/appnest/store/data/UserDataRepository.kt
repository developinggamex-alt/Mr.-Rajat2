package com.appnest.store.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.appnest.store.model.AppListing
import com.appnest.store.model.TrackedApp
import com.appnest.store.model.TrackedStatus
import com.appnest.store.model.UserPreferences
import com.appnest.store.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

val Context.appNestDataStore: DataStore<Preferences> by preferencesDataStore(name = "appnest_prefs")

class UserDataRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val dataStore: DataStore<Preferences>
) {
    private object PrefKeys {
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches_json")
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeLocalPreferences(): Flow<UserPreferences> = dataStore.data.map { prefs ->
        val searchesJson = prefs[PrefKeys.RECENT_SEARCHES] ?: "[]"
        val searches = runCatching {
            val arr = JSONArray(searchesJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotEmpty()) add(item)
                }
            }
        }.getOrDefault(emptyList())

        UserPreferences(
            autoCheckUpdates = prefs[PrefKeys.AUTO_CHECK_UPDATES] ?: true,
            wifiOnlyDownloads = prefs[PrefKeys.WIFI_ONLY_DOWNLOADS] ?: false,
            notificationsEnabled = prefs[PrefKeys.NOTIFICATIONS_ENABLED] ?: true,
            recentSearches = searches
        )
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user profile read." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeTrackedApps(userId: String): Flow<List<TrackedApp>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user trackedApps read." }
        val path = "users/$uid/trackedApps"
        emitAll(
            db.collection("users").document(uid).collection("trackedApps")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TrackedApp::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(userId: String, defaultDisplayName: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeName = defaultDisplayName.trim().ifEmpty { "AppNest User" }.take(60)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "autoCheckUpdates" to true,
                    "wifiOnlyDownloads" to false,
                    "notificationsEnabled" to true,
                    "recentSearches" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateDisplayName(userId: String, displayName: String): Result<Unit> {
        val cleanName = displayName.trim()
        if (cleanName.isEmpty() || cleanName.length > 60) {
            return Result.failure(IllegalArgumentException("Display name must be 1–60 characters."))
        }
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "displayName" to cleanName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to cleanName,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            AuthRepository.updateAuthDisplayName(cleanName)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePreference(
        userId: String,
        autoCheckUpdates: Boolean? = null,
        wifiOnlyDownloads: Boolean? = null,
        notificationsEnabled: Boolean? = null
    ): Result<Unit> {
        dataStore.edit { prefs ->
            autoCheckUpdates?.let { prefs[PrefKeys.AUTO_CHECK_UPDATES] = it }
            wifiOnlyDownloads?.let { prefs[PrefKeys.WIFI_ONLY_DOWNLOADS] = it }
            notificationsEnabled?.let { prefs[PrefKeys.NOTIFICATIONS_ENABLED] = it }
        }
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            val docRef = db.collection("users").document(uid)
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            autoCheckUpdates?.let { updates["autoCheckUpdates"] = it }
            wifiOnlyDownloads?.let { updates["wifiOnlyDownloads"] = it }
            notificationsEnabled?.let { updates["notificationsEnabled"] = it }
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(updates).await()
            } else {
                val fallbackName = auth.currentUser?.displayName?.trim().orEmpty().ifEmpty { "AppNest User" }.take(60)
                docRef.set(
                    updates + mapOf(
                        "userId" to uid,
                        "displayName" to fallbackName,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveRecentSearch(userId: String, term: String, current: List<String>): Result<Unit> {
        val cleanTerm = term.trim().take(80)
        if (cleanTerm.isEmpty()) return Result.success(Unit)
        val updated = (listOf(cleanTerm) + current.filterNot { it.equals(cleanTerm, ignoreCase = true) }).take(6)

        dataStore.edit { prefs ->
            prefs[PrefKeys.RECENT_SEARCHES] = JSONArray(updated).toString()
        }

        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "recentSearches" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordDownload(
        userId: String,
        app: AppListing,
        bytes: Long,
        localFileName: String,
        packageName: String?
    ): Result<Unit> {
        val path = "users/$userId/trackedApps/${app.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(app.id.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("trackedApps").document(app.id)
            val snap = docRef.get().await()
            val nowMillis = System.currentTimeMillis()
            val safeName = app.name.trim().take(100).ifEmpty { "App" }
            val safeVersion = app.version.trim().take(40)
            val safeFile = localFileName.trim().take(200)
            val safePkg = packageName?.trim()?.take(200)

            if (snap.exists()) {
                val updates = mutableMapOf<String, Any?>(
                    "name" to safeName,
                    "version" to safeVersion.takeIf { it.isNotEmpty() },
                    "bytes" to bytes.coerceAtLeast(0L),
                    "localFileName" to safeFile.takeIf { it.isNotEmpty() },
                    "packageName" to safePkg?.takeIf { it.isNotEmpty() },
                    "downloadedAt" to nowMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                @Suppress("UNCHECKED_CAST")
                docRef.update(updates.filterValues { it != null } as Map<String, Any>).await()
            } else {
                val payload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "name" to safeName,
                    "version" to safeVersion.takeIf { it.isNotEmpty() },
                    "bytes" to bytes.coerceAtLeast(0L),
                    "status" to TrackedStatus.DOWNLOADED.name,
                    "localFileName" to safeFile.takeIf { it.isNotEmpty() },
                    "packageName" to safePkg?.takeIf { it.isNotEmpty() },
                    "downloadedAt" to nowMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                @Suppress("UNCHECKED_CAST")
                docRef.set(payload.filterValues { it != null } as Map<String, Any>).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun markInstalled(
        userId: String,
        appId: String,
        name: String,
        version: String,
        bytes: Long = 0L,
        localFileName: String? = null,
        packageName: String? = null
    ): Result<Unit> {
        val path = "users/$userId/trackedApps/$appId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(appId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("trackedApps").document(appId)
            val snap = docRef.get().await()
            val nowMillis = System.currentTimeMillis()
            val safeName = name.trim().take(100).ifEmpty { "App" }
            val safeVersion = version.trim().take(40)

            if (snap.exists()) {
                val updates = mutableMapOf<String, Any?>(
                    "name" to safeName,
                    "version" to safeVersion.takeIf { it.isNotEmpty() },
                    "status" to TrackedStatus.INSTALLED.name,
                    "installedAt" to nowMillis,
                    "localFileName" to localFileName?.trim()?.take(200)?.takeIf { it.isNotEmpty() },
                    "packageName" to packageName?.trim()?.take(200)?.takeIf { it.isNotEmpty() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                @Suppress("UNCHECKED_CAST")
                docRef.update(updates.filterValues { it != null } as Map<String, Any>).await()
            } else {
                val payload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "name" to safeName,
                    "version" to safeVersion.takeIf { it.isNotEmpty() },
                    "bytes" to bytes.coerceAtLeast(0L),
                    "status" to TrackedStatus.INSTALLED.name,
                    "localFileName" to localFileName?.trim()?.take(200)?.takeIf { it.isNotEmpty() },
                    "packageName" to packageName?.trim()?.take(200)?.takeIf { it.isNotEmpty() },
                    "installedAt" to nowMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                @Suppress("UNCHECKED_CAST")
                docRef.set(payload.filterValues { it != null } as Map<String, Any>).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun unmarkInstalled(
        userId: String,
        appId: String,
        hasDownloadHistory: Boolean
    ): Result<Unit> {
        val path = "users/$userId/trackedApps/$appId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(appId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("trackedApps").document(appId)
            if (hasDownloadHistory) {
                docRef.update(
                    mapOf(
                        "status" to TrackedStatus.DOWNLOADED.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
