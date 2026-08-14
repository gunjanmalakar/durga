# Get the APK without installing anything — GitHub builds it for you

This project includes a GitHub Actions workflow (`.github/workflows/android.yml`) that
compiles the app in the cloud and gives you a downloadable **app-debug.apk**. You only
need a free GitHub account and a web browser.

## Steps

1. **Make a free GitHub account** at https://github.com (skip if you have one).

2. **Create a repository**
   - Click the **+** (top-right) → **New repository**.
   - Name it e.g. `durga-app`. **Private** is fine. Click **Create repository**.

3. **Upload the project files**
   - Unzip `DurgaAndroid.zip` on your computer.
   - On the new repo page, click **“uploading an existing file”** (or **Add file → Upload files**).
   - Open the unzipped **DurgaAndroid** folder and drag **everything inside it** into the
     browser — the files `settings.gradle`, `gradlew`, and the folders `app`, `gradle`, and
     `.github` must end up at the **top level** of the repo (not inside another `DurgaAndroid` folder).
   - Scroll down and click **Commit changes**.

   > If the hidden `.github` folder doesn’t upload (some computers hide it): on the repo click
   > **Add file → Create new file**, type the name box exactly as
   > `.github/workflows/android.yml`, paste the contents of that file from the zip, and commit.

4. **Let it build**
   - Click the **Actions** tab. You’ll see **“Build Durga APK”** running (yellow dot).
     If it doesn’t start, click it → **Run workflow**.
   - Wait ~3–5 minutes for a green ✓.

5. **Download the APK**
   - Click the finished run → scroll to **Artifacts** → download **durga-debug-apk** (a .zip).
   - Unzip it to get **app-debug.apk**.

6. **Install on your phone**
   - Send `app-debug.apk` to your phone (Google Drive, email, USB…).
   - Tap it → allow **“Install unknown apps”** for the app you opened it from → **Install**.
   - Open Durga, allow **Camera, Location (Allow all the time), SMS, Notifications**, then
     log in (or register). Set the server box to `https://oles.co.in/durga/` if needed.

## Notes
- The build downloads Android tools the first time (that’s why it takes a few minutes).
- Re-running: any time you push a change or click **Run workflow**, a fresh APK is produced.
- This is a **debug** APK — perfect for installing and testing. For the Play Store you’d
  later create a **signed release** build (I can add that on request).
- Make sure the two backend files `api/app_login.php` and `api/app_register.php` are uploaded
  to your server (they’re in the web zip), and the site is HTTPS — the app needs them to log in.
