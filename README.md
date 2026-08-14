# Durga — native Android app

A Kotlin Android app for the Durga safety system. It talks to the **same PHP + MySQL
backend** as the web app (so all data lands in your `elearn_db` and shows up in the admin
panel and reports), and it adds real background operation and native SMS that a web page
can't do.

## What it does
- **Login / Register** against the backend (`api/app_login.php`, `api/app_register.php`).
- **Heart rate** via the rear camera + flash (photoplethysmography), stored every 5s.
- **Background guardian** — a foreground service that keeps running when the app is closed:
  it records **GPS location (~1 min)**, runs the **intelligent guardian check (~35s)**, syncs
  its **local SQLite queue**, and fires an **automatic SOS** on sustained distress.
- **SOS** — the backend emails/SMS-gateways your contacts *and* the phone sends a **real SMS**
  natively (`SmsManager`) to each contact, so alerts go out even without a paid SMS gateway.
- **Automatic touch sensing** — records touch behaviour and stores it (`api/save_touch.php`).
- **Restarts after reboot** (`BootReceiver`).
- Local **SQLite database** (`durga.db`) queues every reading offline, then syncs.

## Requirements
- **Android Studio** (Hedgehog or newer) with an internet connection the first time
  (it downloads the Android Gradle Plugin, AndroidX and CameraX from Google's Maven —
  which is why the APK must be built in Android Studio, not in a locked-down sandbox).
- Your Durga backend reachable over **HTTPS** (e.g. `https://oles.co.in/durga/`).
  The two new endpoints (`api/app_login.php`, `api/app_register.php`) are included in the
  latest web zip — upload them with the rest of `durga_web`.

## Build & install
1. Open the `DurgaAndroid` folder in **Android Studio** (File → Open).
2. Let Gradle sync (it fetches dependencies). If prompted, install the Android SDK 34.
3. Set your server URL if different from the default: edit `Api.kt` → `var BASE = "https://oles.co.in/durga/"`
   (or just type it into the “Advanced: server URL” box on the login screen).
4. Plug in an Android phone (USB debugging on) or use an emulator, then **Run ▶**.
   To get an installable file instead: **Build → Build Bundle(s)/APK(s) → Build APK(s)**;
   the debug APK appears under `app/build/outputs/apk/debug/app-debug.apk`. Sideload it
   (enable “Install unknown apps” on the phone).
5. For the Play Store, use **Build → Generate Signed Bundle/APK** to produce a signed `.aab`.

## Permissions (asked at first run)
Camera (heart rate), Location + **background location** (guardian), SMS (native SOS),
Notifications (the required foreground-service notice). Choose **“Allow all the time”** for
location so the guardian works with the app closed.

## Compatibility
- `minSdk 21` (Android 5.0) → `targetSdk 34` (Android 14), so it installs on essentially all
  in-use Android phones. Uses `LocationManager` (no Google Play Services dependency), so it
  also works on devices without Google services.

## Honest limits (unchanged from the web app)
- A **touchscreen cannot sense a pulse** — heart rate needs the camera + fingertip.
- **Background** here means a foreground service with a persistent notification (Android’s
  rule for always-on apps). It runs location + guardian + SOS with the screen off; the
  **camera heart-rate measurement runs only while you have the Measure screen open**
  (Android restricts camera use in the background).
- Battery optimisation: on some phones, tell the OS to **not** optimise Durga
  (Settings → Battery → Durga → Unrestricted) so the guardian isn’t killed.

## Where things are
```
app/src/main/java/in/co/oles/durga/
  Api.kt              OkHttp client + persistent session cookie
  LocalDb.kt          on-device SQLite queue
  LoginActivity.kt    RegisterActivity.kt   DashboardActivity.kt
  MeasureActivity.kt  CameraX PPG heart rate
  GuardianService.kt  foreground service: location + guardian + SOS + sync
  Sos.kt              backend alert + native SMS + WhatsApp-ready
  BootReceiver.kt     restart guardian after reboot
```
