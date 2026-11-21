package com.appnest.store.data

import com.appnest.store.model.AppListing
import com.appnest.store.model.AppListingInput
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
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

class CatalogRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observePublishedApps(): Flow<List<AppListing>> = flow {
        requireUserId()
        emitAll(
            db.collection("apps")
                .whereEqualTo("published", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AppListing::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { it.name.isNotBlank() && it.published }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "apps")
                    }
                    throw error
                }
        )
    }

    fun observeDraftApps(): Flow<List<AppListing>> = flow {
        requireUserId()
        emitAll(
            db.collection("appDrafts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AppListing::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "appDrafts")
                    }
                    throw error
                }
        )
    }

    fun observeIsAdmin(user: FirebaseUser): Flow<Boolean> =
        db.collection("admins").document(user.uid)
            .snapshots()
            .map { snapshot ->
                val hasAdminDoc = snapshot.exists()
                val isBootstrapAdmin = user.email.equals(BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true) &&
                    user.isEmailVerified
                hasAdminDoc || isBootstrapAdmin
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError != null && auth.currentUser?.uid == user.uid) {
                    handleFirestoreError(firestoreError, OperationType.GET, "admins/${user.uid}")
                }
                val isBootstrapAdmin = user.email.equals(BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true) &&
                    user.isEmailVerified
                emit(isBootstrapAdmin)
            }

    suspend fun saveListing(
        existingId: String?,
        wasPublished: Boolean?,
        input: AppListingInput,
        publish: Boolean
    ): Result<String> {
        val targetCollection = if (publish) "apps" else "appDrafts"
        val docId = existingId?.takeIf { it.isNotBlank() }
            ?: db.collection("apps").document().id
        val targetPath = "$targetCollection/$docId"

        return try {
            val uid = requireUserId()
            val movingCollections = existingId != null && wasPublished != null && wasPublished != publish
            val isNewInTarget = existingId == null || movingCollections

            val payload = input.toValidatedMap(
                createdBy = uid,
                publish = publish,
                includeCreatedAt = isNewInTarget
            ).getOrElse { return Result.failure(it) }

            val targetRef = db.collection(targetCollection).document(docId)
            if (movingCollections) {
                val oldCollection = if (wasPublished == true) "apps" else "appDrafts"
                val oldRef = db.collection(oldCollection).document(docId)
                db.runBatch { batch ->
                    batch.set(targetRef, payload)
                    batch.delete(oldRef)
                }.await()
            } else if (existingId == null) {
                targetRef.set(payload).await()
            } else {
                targetRef.update(payload).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existingId == null) OperationType.CREATE else OperationType.UPDATE,
                targetPath
            )
            Result.failure(e)
        }
    }

    suspend fun deleteListing(id: String, published: Boolean): Result<Unit> {
        val collection = if (published) "apps" else "appDrafts"
        val path = "$collection/$id"
        return try {
            requireUserId()
            require(id.isNotBlank()) { "Listing ID cannot be blank." }
            db.collection(collection).document(id).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    companion object {
        private const val BOOTSTRAP_ADMIN_EMAIL = "developinggamex@gmail.com"
    }
}
