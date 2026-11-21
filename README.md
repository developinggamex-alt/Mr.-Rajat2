# AppNest — Native Android App & Game Catalog

AppNest is a native Kotlin and Jetpack Compose Android application featuring a user catalog and an admin publishing panel backed by Firebase Authentication (Google Sign-In via Jetpack Credential Manager) and Cloud Firestore.

## Features

- **Home Catalog**: Curated featured carousel, category filter chips, trending apps, and recent catalog updates synced in real time from Firestore (`/apps`).
- **Search & Discovery**: Instant search across titles, categories, developers, and descriptions with per-user persisted recent search history.
- **App Details & Native APK Downloader**:
  - Direct HTTPS APK streaming downloads with live byte/speed/percentage progress, pause, resume, and cancel controls.
  - ZIP/APK archive signature verification (`PK` header validation) and system APK installation via `FileProvider` (`REQUEST_INSTALL_PACKAGES`).
  - Google Drive share-link fallback handling (`drive.google.com`) and optional external `Open` URL support.
- **Downloads & Library**: Track active downloads, downloaded/installed apps, and download history (`/users/{uid}/trackedApps`).
- **App Updates**: Semantic/numeric version comparison between tracked installs and published listings with Wi-Fi-only download enforcement.
- **Profile & Settings**: Google Sign-In status, Wi-Fi-only toggle, auto-check updates toggle, notification preference, Privacy Policy, and Help & Support views.
- **Admin Panel**: Role-gated catalog management for verified admins (`/admins/{uid}` or bootstrapped verified admin email), supporting creating, editing, publishing to `/apps`, saving drafts to `/appDrafts`, and deleting listings.

## Tech Stack

- **Language & UI**: Kotlin, Jetpack Compose, Material Design 3
- **Navigation**: Type-safe Navigation Compose (`@Serializable` routes)
- **Authentication**: Firebase Auth + Jetpack Credential Manager (`GetSignInWithGoogleOption` / `GetGoogleIdOption`)
- **Database & Security**: Cloud Firestore (named database configured in `res/values/firebase_applet_config.xml`) with zero-trust `firestore.rules`
- **Image Loading**: Coil

## Build & Verify

Build the debug APK:

```bash
gradle assembleDebug
```

Run the Firestore security rules emulator tests:

```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

## Notes on Firebase & Distribution Links

- **Admin Access**: Bootstrapped verified admin email or UID documents under `/admins/{uid}` unlock the Admin Panel. Non-admin users are strictly limited to reading published listings and managing their own `/users/{uid}` and `/users/{uid}/trackedApps` documents.
- **Download Links**:
  - **Direct APK links** must be HTTPS URLs serving an APK binary.
  - **Google Drive links** should be shared as *Anyone with the link*; Drive links open through Android's browser/Drive handler when direct raw binary streaming is unavailable.
