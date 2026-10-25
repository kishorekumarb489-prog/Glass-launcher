# Glass Launcher

A simple iOS-style launcher for Android.

## Features
- Rounded (squircle) icon style
- Glass-effect dock and search pill
- Droplet press and launch animation
- Dock with Phone, Messages, Camera, Browser (opens your phone's own apps)
- Search apps, long-press for App info / Uninstall
- Swipe down on home to open the notification panel
- Wallpaper blur on Android 12+ (if your device supports it)

## Build without a PC (free)
1. Create a free GitHub account and a new repository.
2. Upload every file in this folder (keep the folder structure, including `.github`).
3. Open the **Actions** tab -> **Build APK** -> **Run workflow**.
4. When it finishes, download `GlassLauncher-debug-apk`, unzip, install `app-debug.apk`.
5. Press Home and choose **Glass Launcher** -> **Always**.

## Build on a PC
Open this folder in Android Studio and press Run.

## Switching back
Settings -> Apps -> Default apps -> Home app -> choose your old launcher.

## Notes
- Apps ask for their own permissions the first time you use them. This is Android behaviour.
- Camera, calls and messages open your phone's installed apps.
- Not tested on a device yet. If the build fails, send me the error text.
