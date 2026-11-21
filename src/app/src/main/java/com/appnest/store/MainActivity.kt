package com.appnest.store

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.provider.Settings
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var pendingApkFile: File? = null
    private var activeDownloadId: Long = -1L

    private val unknownAppsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val file = pendingApkFile
        if (file != null && canInstallUnknownApps()) {
            pendingApkFile = null
            launchApkInstaller(file)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (id != -1L && id == activeDownloadId) {
                val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(id)
                manager.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        if (statusIdx >= 0 && cursor.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL && uriIdx >= 0) {
                            val localUri = cursor.getString(uriIdx)
                            if (!localUri.isNullOrBlank()) {
                                val file = Uri.parse(localUri).path?.let { File(it) }
                                if (file != null && file.exists()) {
                                    installApkOrRequestPermission(file)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        ViewCompat.setOnApplyWindowInsetsListener(swipeRefresh) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(downloadReceiver, filter)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = userAgentString.replace("; wv", "")
        }

        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
        webView.isNestedScrollingEnabled = true
        webView.addJavascriptInterface(AndroidBridge(), "AppNestAndroid")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                val host = url.host?.lowercase() ?: ""
                if (url.scheme != "https" && url.scheme != "http") {
                    return true
                }
                val isInternal = host.endsWith("run.app") ||
                    host.endsWith("firebaseapp.com") ||
                    host == "accounts.google.com" ||
                    host == "localhost" ||
                    host == "127.0.0.1"
                return if (isInternal) {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                swipeRefresh.isRefreshing = false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val popupWebView = WebView(this@MainActivity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = webView.settings.userAgentString
                }
                val dialog = Dialog(this@MainActivity, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
                dialog.setContentView(popupWebView)
                popupWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView?) {
                        dialog.dismiss()
                    }
                }
                popupWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                        return false
                    }
                }
                dialog.show()
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = popupWebView
                resultMsg?.sendToTarget()
                return true
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith("blob:")) return@setDownloadListener
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType("application/vnd.android.package-archive")
                addRequestHeader("User-Agent", userAgent)
                setTitle(fileName)
                setDescription("Downloading via AppNest")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            activeDownloadId = manager.enqueue(request)
            Toast.makeText(this, "Downloading $fileName…", Toast.LENGTH_SHORT).show()
        }

        swipeRefresh.setDistanceToTriggerSync(220)
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            webView.scrollY > 0 || webView.canScrollVertically(-1)
        }
        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        webView.loadUrl(BuildConfig.APP_URL)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('appnest:resume'))", null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun packagePrefs() = getSharedPreferences("appnest_packages", MODE_PRIVATE)

    private fun extractPackageName(file: File): String? {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            }
            info?.packageName
        } catch (_: Exception) {
            null
        }
    }

    private fun rememberApkForApp(appId: String, file: File) {
        val pkg = extractPackageName(file)
        packagePrefs().edit().apply {
            if (!pkg.isNullOrBlank()) putString("pkg_$appId", pkg)
            putString("file_$appId", file.name)
            apply()
        }
    }

    private fun canInstallUnknownApps(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun requestUnknownAppsPermission(apkFile: File? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || canInstallUnknownApps()) {
            if (apkFile != null) launchApkInstaller(apkFile)
            return
        }
        pendingApkFile = apkFile
        AlertDialog.Builder(this)
            .setTitle("Allow install unknown apps")
            .setMessage("To install downloaded APKs directly from AppNest, enable 'Allow from this source' in Android Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
                unknownAppsLauncher.launch(intent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                pendingApkFile = null
            }
            .show()
    }

    private fun installApkOrRequestPermission(file: File) {
        if (!canInstallUnknownApps()) {
            requestUnknownAppsPermission(file)
            return
        }
        launchApkInstaller(file)
    }

    private fun launchApkInstaller(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun canInstallUnknownApps(): Boolean = this@MainActivity.canInstallUnknownApps()

        @JavascriptInterface
        fun openUnknownAppsSettings() {
            runOnUiThread { this@MainActivity.requestUnknownAppsPermission(null) }
        }

        @JavascriptInterface
        fun setDarkMode(enabled: Boolean) {
            runOnUiThread {
                val bg = Color.parseColor(if (enabled) "#0F111C" else "#FFFFFF")
                window.statusBarColor = bg
                window.navigationBarColor = bg
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !enabled
                    isAppearanceLightNavigationBars = !enabled
                }
            }
        }

        @JavascriptInterface
        fun isAppInstalled(appId: String): Boolean {
            val pkg = packagePrefs().getString("pkg_$appId", null) ?: return false
            return packageManager.getLaunchIntentForPackage(pkg) != null
        }

        @JavascriptInterface
        fun openAppById(appId: String, rawFileName: String, fallbackUrl: String): Boolean {
            val prefs = packagePrefs()
            val pkg = prefs.getString("pkg_$appId", null)
            if (!pkg.isNullOrBlank()) {
                val launch = packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runOnUiThread { startActivity(launch) }
                    return true
                }
            }
            val savedName = prefs.getString("file_$appId", null) ?: rawFileName
            val safeName = savedName.replace(Regex("[^a-zA-Z0-9._-]+"), "-").ifBlank { "app.apk" }
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
            val file = File(dir, safeName)
            if (file.exists()) {
                runOnUiThread { installApkOrRequestPermission(file) }
                return true
            }
            if (fallbackUrl.startsWith("https://")) {
                runOnUiThread {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
                }
                return true
            }
            return false
        }

        @JavascriptInterface
        fun installApkBase64ForApp(appId: String, base64Data: String, rawFileName: String) {
            try {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Invalid APK file.", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                val safeName = rawFileName.replace(Regex("[^a-zA-Z0-9._-]+"), "-")
                    .ifBlank { "app.apk" }
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                val outFile = File(dir, safeName)
                FileOutputStream(outFile).use { it.write(bytes) }
                rememberApkForApp(appId, outFile)
                runOnUiThread { installApkOrRequestPermission(outFile) }
            } catch (_: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Could not prepare APK installer.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun installApkBase64(base64Data: String, rawFileName: String) {
            installApkBase64ForApp(rawFileName, base64Data, rawFileName)
        }
    }
}
