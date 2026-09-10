# Watch Align Android

This is the first true-offline Android build of Watch Align. It has no INTERNET permission and performs watch-circle detection, perspective diagnostics, hour-marker geometry and circle-based genuine/reference overlay locally using OpenCV Android.

Version: 1.3.0-alpha1

The Android project lives alongside the Windows application so shared model geometry and regression fixtures can converge into a single cross-platform core. This alpha intentionally does not bundle copyrighted manufacturer reference photographs; a genuine/reference photo can be selected from the device and compared entirely offline.

Build locally with Gradle 8.10.x and Android SDK 35:

    gradle :app:assembleDebug

APK output:

    app/build/outputs/apk/debug/app-debug.apk
