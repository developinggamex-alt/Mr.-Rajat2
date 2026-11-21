package com.appnest.store.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.net.URI
import java.util.Locale

@Keep
@IgnoreExtraProperties
data class AppListing(
    @DocumentId val id: String = "",
    val name: String = "",
    val category: String = "",
    val developer: String = "",
    val version: String = "",
    val tagline: String = "",
    val description: String = "",
    val driveUrl: String = "",
    val downloadUrl: String = "",
    val iconUrl: String = "",
    val bannerUrl: String = "",
    val openUrl: String = "",
    val screenshots: List<String> = emptyList(),
    val sizeBytes: Long = 0L,
    val rating: Double = 0.0,
    val reviewCount: Long = 0L,
    val downloadCount: Long = 0L,
    val featured: Boolean = false,
    val published: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun validDirectApkUrl(): String? {
        val candidate = downloadUrl.trim()
        if (!AppUrlUtils.isValidHttpsUrl(candidate)) return null
        if (AppUrlUtils.extractDriveInfo(candidate) != null) return null
        return candidate
    }

    fun validDriveLink(): String? {
        return AppUrlUtils.normalizeDriveDownloadUrl(driveUrl)
            ?: AppUrlUtils.normalizeDriveDownloadUrl(downloadUrl)
    }

    fun isDownloadable(): Boolean = validDirectApkUrl() != null || validDriveLink() != null

    fun validIconUrl(): String? = iconUrl.trim().takeIf { AppUrlUtils.isValidHttpsUrl(it) }

    fun validBannerUrl(): String? = bannerUrl.trim().takeIf { AppUrlUtils.isValidHttpsUrl(it) }

    fun validOpenUrl(): String? = openUrl.trim().takeIf { AppUrlUtils.isValidHttpsUrl(it) }

    fun validScreenshots(): List<String> = screenshots.mapNotNull { item ->
        item.trim().takeIf { AppUrlUtils.isValidHttpsUrl(it) }
    }

    fun formattedSize(): String = AppUrlUtils.formatBytes(sizeBytes)

    fun summarySubtitle(): String {
        val parts = buildList {
            if (category.isNotBlank()) add(category.trim())
            if (rating > 0.0) add("★ ${String.format(Locale.US, "%.1f", rating)}")
            if (sizeBytes > 0L) add(formattedSize())
        }
        return parts.joinToString(" · ")
    }
}

data class AppListingInput(
    val name: String = "",
    val category: String = "",
    val developer: String = "",
    val version: String = "",
    val tagline: String = "",
    val description: String = "",
    val driveUrl: String = "",
    val downloadUrl: String = "",
    val iconUrl: String = "",
    val bannerUrl: String = "",
    val openUrl: String = "",
    val screenshotsText: String = "",
    val sizeMbText: String = "",
    val ratingText: String = "",
    val reviewCountText: String = "",
    val downloadCountText: String = "",
    val featured: Boolean = false
) {
    fun toValidatedMap(
        createdBy: String,
        publish: Boolean,
        includeCreatedAt: Boolean
    ): Result<Map<String, Any>> {
        val cleanName = name.trim()
        val cleanCategory = category.trim()
        val cleanDescription = description.trim()

        if (cleanName.isEmpty() || cleanName.length > 100) {
            return Result.failure(IllegalArgumentException("App title must be 1–100 characters."))
        }
        if (cleanCategory.isEmpty() || cleanCategory.length > 60) {
            return Result.failure(IllegalArgumentException("Category must be 1–60 characters."))
        }
        if (cleanDescription.isEmpty() || cleanDescription.length > 12000) {
            return Result.failure(IllegalArgumentException("Description must be 1–12,000 characters."))
        }

        val cleanDev = developer.trim()
        if (cleanDev.length > 100) {
            return Result.failure(IllegalArgumentException("Developer name max length is 100 characters."))
        }
        val cleanVersion = version.trim()
        if (cleanVersion.length > 40) {
            return Result.failure(IllegalArgumentException("Version max length is 40 characters."))
        }
        val cleanTagline = tagline.trim()
        if (cleanTagline.length > 160) {
            return Result.failure(IllegalArgumentException("Tagline max length is 160 characters."))
        }

        val cleanDriveRaw = driveUrl.trim()
        val normalizedDrive = if (cleanDriveRaw.isNotEmpty()) {
            AppUrlUtils.normalizeDriveDownloadUrl(cleanDriveRaw)
                ?: return Result.failure(IllegalArgumentException("Enter a valid HTTPS Google Drive file link."))
        } else null

        val cleanDownload = downloadUrl.trim().takeIf { it.isNotEmpty() }
        if (cleanDownload != null && (!AppUrlUtils.isValidHttpsUrl(cleanDownload) || cleanDownload.length > 2000)) {
            return Result.failure(IllegalArgumentException("Direct APK link must use HTTPS (max 2000 chars)."))
        }

        val cleanIcon = iconUrl.trim().takeIf { it.isNotEmpty() }
        if (cleanIcon != null && (!AppUrlUtils.isValidHttpsUrl(cleanIcon) || cleanIcon.length > 2000)) {
            return Result.failure(IllegalArgumentException("Icon URL must use HTTPS."))
        }

        val cleanBanner = bannerUrl.trim().takeIf { it.isNotEmpty() }
        if (cleanBanner != null && (!AppUrlUtils.isValidHttpsUrl(cleanBanner) || cleanBanner.length > 2000)) {
            return Result.failure(IllegalArgumentException("Banner URL must use HTTPS."))
        }

        val cleanOpen = openUrl.trim().takeIf { it.isNotEmpty() }
        if (cleanOpen != null && (!AppUrlUtils.isValidHttpsUrl(cleanOpen) || cleanOpen.length > 2000)) {
            return Result.failure(IllegalArgumentException("App open link must use HTTPS."))
        }

        val screenshots = screenshotsText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (screenshots.size > 12 || screenshots.any { it.length > 2000 || !AppUrlUtils.isValidHttpsUrl(it) }) {
            return Result.failure(IllegalArgumentException("Enter up to 12 valid HTTPS screenshot links."))
        }

        val sizeBytes: Long? = if (sizeMbText.trim().isNotEmpty()) {
            val mb = sizeMbText.trim().toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Enter a valid numeric APK size in MB."))
            if (mb < 0.0 || !mb.isFinite()) {
                return Result.failure(IllegalArgumentException("APK size must be 0 or greater."))
            }
            Math.round(mb * 1_048_576.0)
        } else null

        val rating: Double? = if (ratingText.trim().isNotEmpty()) {
            val r = ratingText.trim().toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Rating must be a number between 0 and 5."))
            if (!r.isFinite() || r < 0.0 || r > 5.0) {
                return Result.failure(IllegalArgumentException("Rating must be between 0.0 and 5.0."))
            }
            r
        } else null

        val reviewCount: Long? = if (reviewCountText.trim().isNotEmpty()) {
            val count = reviewCountText.trim().toLongOrNull()
                ?: return Result.failure(IllegalArgumentException("Review count must be a non-negative integer."))
            if (count < 0L) {
                return Result.failure(IllegalArgumentException("Review count cannot be negative."))
            }
            count
        } else null

        val downloadCount: Long? = if (downloadCountText.trim().isNotEmpty()) {
            val count = downloadCountText.trim().toLongOrNull()
                ?: return Result.failure(IllegalArgumentException("Download count must be a non-negative integer."))
            if (count < 0L) {
                return Result.failure(IllegalArgumentException("Download count cannot be negative."))
            }
            count
        } else null

        if (publish && cleanDownload == null && normalizedDrive == null) {
            return Result.failure(
                IllegalArgumentException("Add a Direct APK link or Google Drive link before publishing.")
            )
        }

        val map = linkedMapOf<String, Any?>(
            "name" to cleanName,
            "category" to cleanCategory,
            "developer" to cleanDev.takeIf { it.isNotEmpty() },
            "version" to cleanVersion.takeIf { it.isNotEmpty() },
            "tagline" to cleanTagline.takeIf { it.isNotEmpty() },
            "description" to cleanDescription,
            "driveUrl" to normalizedDrive,
            "downloadUrl" to cleanDownload,
            "iconUrl" to cleanIcon,
            "bannerUrl" to cleanBanner,
            "openUrl" to cleanOpen,
            "screenshots" to screenshots.takeIf { it.isNotEmpty() },
            "sizeBytes" to sizeBytes,
            "rating" to rating,
            "reviewCount" to reviewCount,
            "downloadCount" to downloadCount,
            "featured" to featured,
            "published" to publish,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (includeCreatedAt) {
            map["createdBy"] = createdBy
            map["createdAt"] = FieldValue.serverTimestamp()
        }

        @Suppress("UNCHECKED_CAST")
        return Result.success(map.filterValues { it != null } as Map<String, Any>)
    }
}

enum class TrackedStatus {
    DOWNLOADED,
    INSTALLED;

    companion object {
        fun fromRaw(raw: String?): TrackedStatus {
            return entries.firstOrNull { it.name == raw } ?: DOWNLOADED
        }
    }
}

@Keep
@IgnoreExtraProperties
data class TrackedApp(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val version: String = "",
    val bytes: Long = 0L,
    val status: String = TrackedStatus.DOWNLOADED.name,
    val localFileName: String = "",
    val packageName: String = "",
    val downloadedAt: Long = 0L,
    val installedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedStatus(): TrackedStatus = TrackedStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val autoCheckUpdates: Boolean = true,
    val wifiOnlyDownloads: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val recentSearches: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class UserPreferences(
    val autoCheckUpdates: Boolean = true,
    val wifiOnlyDownloads: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val recentSearches: List<String> = emptyList()
)

data class ActiveDownloadTask(
    val appId: String,
    val appName: String,
    val version: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val paused: Boolean = false,
    val statusText: String = "Connecting…"
) {
    val progressFraction: Float?
        get() = if (totalBytes > 0L) {
            (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class DriveFileInfo(
    val id: String,
    val resourceKey: String = ""
)

object AppUrlUtils {
    private val driveIdRegex = Regex("^[A-Za-z0-9_-]{10,128}$")
    private val fileIdPathRegex = Regex("^/file/d/([\\w-]+)(?:/|$)")
    private val idRegex = Regex("^[A-Za-z0-9_-]{1,128}$")
    private val numericVersionRegex = Regex("^\\d+(\\.\\d+)*$")

    fun isValidHttpsUrl(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return try {
            val uri = URI(raw.trim())
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null
        } catch (_: Exception) {
            false
        }
    }

    fun extractDriveInfo(rawUrl: String?): DriveFileInfo? {
        if (!isValidHttpsUrl(rawUrl)) return null
        return try {
            val uri = URI(rawUrl!!.trim())
            val host = uri.host?.lowercase(Locale.US) ?: return null
            if (host != "drive.google.com" &&
                host != "docs.google.com" &&
                host != "drive.usercontent.google.com"
            ) {
                return null
            }
            val queryParams = parseQueryParams(uri.rawQuery)
            val pathId = fileIdPathRegex.find(uri.path ?: "")?.groupValues?.getOrNull(1)
            val id = pathId ?: queryParams["id"]
            if (id.isNullOrBlank() || !driveIdRegex.matches(id)) return null
            val rk = queryParams["resourcekey"]?.takeIf { idRegex.matches(it) }.orEmpty()
            DriveFileInfo(id = id, resourceKey = rk)
        } catch (_: Exception) {
            null
        }
    }

    fun normalizeDriveDownloadUrl(rawUrl: String?): String? {
        val info = extractDriveInfo(rawUrl) ?: return null
        val rkPart = if (info.resourceKey.isNotEmpty()) "&resourcekey=${info.resourceKey}" else ""
        return "https://drive.google.com/uc?export=download&id=${info.id}$rkPart"
    }

    fun directDriveUserContentUrl(info: DriveFileInfo): String {
        val rkPart = if (info.resourceKey.isNotEmpty()) "&resourcekey=${info.resourceKey}" else ""
        return "https://drive.usercontent.google.com/download?id=${info.id}&export=download&confirm=t$rkPart"
    }

    fun sanitizeApkFileName(rawName: String?): String {
        val cleaned = (rawName ?: "")
            .replace(Regex("[^a-zA-Z0-9._-]+"), "-")
            .trim('.', '-')
        if (cleaned.isEmpty()) return "app.apk"
        return if (cleaned.endsWith(".apk", ignoreCase = true)) cleaned else "$cleaned.apk"
    }

    fun isNewerNumericVersion(next: String?, previous: String?): Boolean {
        val aStr = next?.trim().orEmpty()
        val bStr = previous?.trim().orEmpty()
        if (!numericVersionRegex.matches(aStr) || !numericVersionRegex.matches(bStr)) return false
        val a = aStr.split('.').mapNotNull { it.toIntOrNull() }
        val b = bStr.split('.').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(a.size, b.size)
        for (i in 0 until maxLen) {
            val diff = (a.getOrNull(i) ?: 0) - (b.getOrNull(i) ?: 0)
            if (diff != 0) return diff > 0
        }
        return false
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        return if (bytes >= 1_048_576L) {
            String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
        } else {
            String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        }
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (pair in rawQuery.split('&')) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = pair.substring(idx + 1)
                result[key] = value
            }
        }
        return result
    }
}
