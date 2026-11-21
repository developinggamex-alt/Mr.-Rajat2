# AppNest Android APK Source (`src/`)

This folder contains the native Android Studio (Kotlin + Gradle) source project to build the **AppNest** Android `.apk`.

## Features included
- Loads your live AppNest web app inside a hardware-accelerated, scrollbar-free `WebView` with pull-to-refresh and back-navigation history.
- Supports **Google (Gmail) Sign-In** popup windows and third-party auth cookies.
- Declares `REQUEST_INSTALL_PACKAGES` and prompts Android's **Install unknown apps** permission when needed.
- Bridges in-app APK downloads directly to `FileProvider` and the Android system APK package installer (no Google Drive chooser).
- Syncs Dark Mode status bar and navigation bar styling with AppNest Settings.

## Build the APK
1. Open the `src` folder in **Android Studio** (or install Android SDK + Gradle 8.9).
2. Optional: update `APP_URL` in `src/app/build.gradle.kts` if you deploy to a custom domain.
3. Build the debug or release APK:
   - From Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**
   - Or from terminal inside `src/`: `gradle assembleDebug`
   - Output APK path: `src/app/build/outputs/apk/debug/app-debug.apk`
