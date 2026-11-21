# AppNest — Admin and User Panels

A mobile-first JavaScript web app using your existing Firebase project, foodguide-fb333. No sample listings are included.

## Open the panels

Unzip and run this from the folder with this README:

    python -m http.server 8080 --directory dist

- User panel: http://localhost:8080/
- Admin panel: http://localhost:8080/admin.html

For hosting, deploy the contents of dist to an HTTPS static host. Firebase web configuration is in dist/firebase.mjs. Serve both pages from the same origin so Firebase sign-in state can be shared.

## First-time Firebase setup

1. In Firebase Console → Authentication → Sign-in method, enable Email/Password. Add your admin account in Authentication → Users and copy its UID.
2. In Firebase Console → Realtime Database → Data, create the key /admins/YOUR_ADMIN_UID with the Boolean value true (not a quoted string). Create this role only for trusted admin accounts.
3. In Realtime Database → Rules, merge the three sections from database.rules.json into your existing rules. Preserve rules for other paths. Do not leave broad parent-level read/write grants that override restrictions on admins or appDrafts. The supplied rules make /apps publicly readable and limit writes to trusted admins. Keep private records out of /apps.
4. For Firebase password reset and local/deployed use, verify the site origin and localhost (if testing locally) under Authentication → Settings → Authorized domains.
5. Open admin.html and sign in. Add a real listing and click Publish listing. The user panel listens for changes under /apps. Save as draft moves a listing to /appDrafts and removes it from the public catalog.

## App download and media links

- Drive APK link: set file access to Anyone with the link in Drive, then paste its share link. The APK download opens through Google Drive. Google can require confirmation or limit downloads. AppNest does not count a Drive click as a completed download.
- Direct APK link: paste an HTTPS raw APK URL with CORS allowing this site's origin. Direct links take priority. The user can pause, resume, cancel and see live bytes, speed, and percentage when the server sends a length. Pause/resume applies while the page is open. The user can use the Drive button on App Details as a fallback if both links are present.
- For icons, banners and screenshots, use HTTPS URLs that serve image content. Paste screenshot links one per line.
- Android installs APKs through the system installer. The Installed tab records apps the user marks manually. Optional Open links are HTTPS links supplied by the admin.
- Settings can automatically check for newer numeric versions while AppNest is open. Updating requires downloading and installing the APK. The Wi-Fi-only preference blocks downloads when the browser cannot confirm Wi-Fi. Browser notification permissions, OS permissions, storage location and installation are controlled by the device.
