package com.appnest.store.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.appnest.store.R
import com.appnest.store.model.ActiveDownloadTask
import com.appnest.store.model.AppListing
import com.appnest.store.model.AppUrlUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ApkDownloadManager(
    context: Context
) {
    private val appContext = context.applicationContext

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _activeTasks = MutableStateFlow<Map<String, ActiveDownloadTask>>(emptyMap())
    val activeTasks: StateFlow<Map<String, ActiveDownloadTask>> = _activeTasks.asStateFlow()

    private val runningJobs = ConcurrentHashMap<String, Job>()
    private val pauseFlags = ConcurrentHashMap<String, AtomicBoolean>()

    fun downloadsDir(): File {
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(appContext.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isWifiConnected(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun canInstallUnknownApps(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun unknownAppsSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isPackageInstalled(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return appContext.packageManager.getLaunchIntentForPackage(packageName) != null
    }

    fun launchInstalledPackage(packageName: String): Boolean {
        val intent = appContext.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        return true
    }

    fun extractApkPackageName(file: File): String? {
        if (!file.exists()) return null
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            }
            info?.packageName
        } catch (_: Exception) {
            null
        }
    }

    fun findLocalApk(fileName: String?): File? {
        if (fileName.isNullOrBlank()) return null
        val file = File(downloadsDir(), AppUrlUtils.sanitizeApkFileName(fileName))
        return file.takeIf { it.exists() && it.length() > 4 }
    }

    fun createInstallApkIntent(file: File): Intent {
        val authority = "${appContext.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(appContext, authority, file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun clearDownloadedApks(): Int {
        var count = 0
        downloadsDir().listFiles()?.forEach { file ->
            if (file.isFile && file.delete()) count++
        }
        return count
    }

    fun startDirectOrDriveDownload(
        scope: CoroutineScope,
        app: AppListing,
        forceDrive: Boolean = false,
        wifiOnly: Boolean,
        notificationsEnabled: Boolean,
        onCompleted: suspend (bytes: Long, fileName: String, packageName: String?, apkFile: File) -> Unit,
        onFallbackDriveUrl: (String) -> Unit,
        onMessage: (String) -> Unit
    ) {
        if (wifiOnly && !isWifiConnected()) {
            onMessage("Wi-Fi-only downloads are enabled. Connect to Wi-Fi to continue.")
            return
        }

        if (runningJobs.containsKey(app.id)) {
            onMessage("${app.name} is already downloading.")
            return
        }

        val directUrl = if (!forceDrive) app.validDirectApkUrl() else null
        val driveUrl = app.validDriveLink()

        if (directUrl == null && driveUrl == null) {
            onMessage("No valid HTTPS APK link found for ${app.name}.")
            return
        }

        val pauseFlag = AtomicBoolean(false)
        pauseFlags[app.id] = pauseFlag

        _activeTasks.update { current ->
            current + (app.id to ActiveDownloadTask(
                appId = app.id,
                appName = app.name,
                version = app.version,
                statusText = "Connecting…"
            ))
        }

        val job = scope.launch(Dispatchers.IO) {
            val fileName = AppUrlUtils.sanitizeApkFileName(app.name)
            val partFile = File(downloadsDir(), "$fileName.part")
            val finalFile = File(downloadsDir(), fileName)

            try {
                val downloadUrl = if (directUrl != null) {
                    directUrl
                } else {
                    val info = AppUrlUtils.extractDriveInfo(driveUrl)
                        ?: throw DriveFallbackException(driveUrl!!)
                    AppUrlUtils.directDriveUserContentUrl(info)
                }

                streamApkToFile(
                    appId = app.id,
                    url = downloadUrl,
                    partFile = partFile,
                    pauseFlag = pauseFlag,
                    isDrive = directUrl == null,
                    driveFallbackUrl = driveUrl
                )

                if (!isValidApkZipHeader(partFile)) {
                    partFile.delete()
                    if (driveUrl != null && directUrl == null) {
                        throw DriveFallbackException(driveUrl)
                    }
                    throw IllegalStateException("The source did not return a valid APK archive.")
                }

                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                val totalBytes = finalFile.length()
                val pkgName = extractApkPackageName(finalFile)

                onCompleted(totalBytes, fileName, pkgName, finalFile)
                if (notificationsEnabled) {
                    postDownloadNotification(
                        title = "${app.name} downloaded",
                        body = "Tap Install in AppNest to install ${app.name}."
                    )
                }
            } catch (e: CancellationException) {
                partFile.delete()
                throw e
            } catch (e: DriveFallbackException) {
                partFile.delete()
                withContext(Dispatchers.Main) {
                    onFallbackDriveUrl(e.url)
                }
            } catch (e: Exception) {
                partFile.delete()
                withContext(Dispatchers.Main) {
                    if (driveUrl != null && directUrl != null) {
                        onMessage("Direct download failed. Try 'Download via Drive' in app details.")
                    } else if (driveUrl != null) {
                        onFallbackDriveUrl(driveUrl)
                    } else {
                        onMessage(e.localizedMessage ?: "Download failed.")
                    }
                }
            } finally {
                runningJobs.remove(app.id)
                pauseFlags.remove(app.id)
                _activeTasks.update { it - app.id }
            }
        }
        runningJobs[app.id] = job
    }

    fun togglePause(appId: String) {
        val flag = pauseFlags[appId] ?: return
        val newPaused = !flag.get()
        flag.set(newPaused)
        _activeTasks.update { tasks ->
            val task = tasks[appId] ?: return@update tasks
            val status = if (newPaused) {
                "Paused · ${AppUrlUtils.formatBytes(task.bytesDownloaded)}"
            } else {
                "Resuming…"
            }
            tasks + (appId to task.copy(paused = newPaused, statusText = status))
        }
    }

    fun cancelDownload(appId: String) {
        runningJobs[appId]?.cancel()
        runningJobs.remove(appId)
        pauseFlags.remove(appId)
        _activeTasks.update { it - appId }
    }

    fun postDownloadNotification(title: String, body: String) {
        if (!hasNotificationPermission()) return
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AppNest Downloads & Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        nm.notify(title.hashCode(), notification)
    }

    private suspend fun streamApkToFile(
        appId: String,
        url: String,
        partFile: File,
        pauseFlag: AtomicBoolean,
        isDrive: Boolean,
        driveFallbackUrl: String?
    ) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AppNest-Android/2.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (isDrive && driveFallbackUrl != null) {
                    throw DriveFallbackException(driveFallbackUrl)
                }
                throw IllegalStateException("Download failed (HTTP ${response.code}).")
            }

            val contentType = response.header("Content-Type")?.lowercase().orEmpty()
            if (contentType.contains("text/html") || contentType.contains("application/json")) {
                if (isDrive && driveFallbackUrl != null) {
                    val bodyText = response.body?.string().orEmpty()
                    val confirmed = parseDriveConfirmationUrl(bodyText, response.request.url.toString())
                    if (confirmed != null) {
                        streamApkToFile(
                            appId = appId,
                            url = confirmed,
                            partFile = partFile,
                            pauseFlag = pauseFlag,
                            isDrive = false,
                            driveFallbackUrl = driveFallbackUrl
                        )
                        return
                    }
                    throw DriveFallbackException(driveFallbackUrl)
                }
                throw IllegalStateException("The URL returned a webpage instead of an APK file.")
            }

            val body = response.body ?: throw IllegalStateException("Empty download response.")
            val totalBytes = body.contentLength().coerceAtLeast(0L)
            val input = body.byteStream()
            val buffer = ByteArray(16 * 1024)
            var downloaded = 0L
            val startedMs = System.currentTimeMillis()
            var lastUiUpdateMs = 0L

            FileOutputStream(partFile).use { output ->
                while (true) {
                    while (pauseFlag.get()) {
                        delay(150)
                    }
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read

                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdateMs >= 180L || downloaded == totalBytes) {
                        lastUiUpdateMs = now
                        val elapsedSec = ((now - startedMs).coerceAtLeast(500L)) / 1000.0
                        val speed = (downloaded / elapsedSec).toLong()
                        val pctPart = if (totalBytes > 0L) {
                            "${((downloaded * 100L) / totalBytes).coerceIn(0L, 100L)}% · "
                        } else ""
                        val totalPart = if (totalBytes > 0L) {
                            " / ${AppUrlUtils.formatBytes(totalBytes)}"
                        } else ""
                        val status = "$pctPart${AppUrlUtils.formatBytes(downloaded)}$totalPart · ${AppUrlUtils.formatBytes(speed)}/s"
                        _activeTasks.update { tasks ->
                            val current = tasks[appId] ?: return@update tasks
                            tasks + (appId to current.copy(
                                bytesDownloaded = downloaded,
                                totalBytes = totalBytes,
                                speedBytesPerSec = speed,
                                statusText = if (current.paused) {
                                    "Paused · ${AppUrlUtils.formatBytes(downloaded)}"
                                } else status
                            ))
                        }
                    }
                }
            }
            if (downloaded == 0L) {
                throw IllegalStateException("Downloaded APK file is empty.")
            }
        }
    }

    private fun parseDriveConfirmationUrl(html: String, baseUrl: String): String? {
        val hrefMatch = Regex("href=[\"']([^\"']*confirm=[^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("&amp;", "&")
        if (!hrefMatch.isNullOrBlank()) {
            return runCatching {
                Uri.parse(baseUrl).let { base ->
                    if (hrefMatch.startsWith("http")) hrefMatch
                    else "${base.scheme}://${base.host}$hrefMatch"
                }
            }.getOrNull()
        }
        return null
    }

    private fun isValidApkZipHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            FileInputStream(file).use { fis ->
                val sig = ByteArray(4)
                val read = fis.read(sig)
                read == 4 && sig[0] == 0x50.toByte() && sig[1] == 0x4B.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private class DriveFallbackException(val url: String) : Exception()

    companion object {
        private const val CHANNEL_ID = "appnest_downloads"
    }
}
