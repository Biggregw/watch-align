# Watch Align Android

Watch Align Android is a standalone on-device QC and comparison app. It does not require a hosted Watch Align backend: watch analysis, marker geometry, QC annotations and reference registration run locally with OpenCV Android. Internet access is used only to discover/download exact-model official manufacturer reference imagery when a user has not supplied a reference photo; downloaded references are cached locally.

Current working version: **1.3.0-alpha14**

Supported models:
- Rolex GMT-Master II 126710BLNR
- Rolex Submariner No-Date 124060

Current QC includes dial detection, perspective suitability, roll-corrected hour-marker geometry, marker-to-minute-track checks, body-rotation checks for shaped markers, bezel/pip alignment, GMT date-numeral centring, rehaut and dial-print inspection zones, SEL dark-gap evidence, and perspective-gated advisory wording. The report ranks the most significant detected observations first. Lume is not inferred from normal-light photos.

Reference comparison uses deterministic geometry-first registration. The app refuses an overlay when geometry cannot be validated rather than lowering the safety threshold. Official reference images are not stored in the repository.

The Android project lives alongside the Windows application so the two implementations can continue converging toward a shared cross-platform core. Android remains an alpha and is not presented as proof of authenticity.

Build locally with Gradle 8.10.x and Android SDK 35:

    gradle :app:testDebugUnitTest
    gradle :app:assembleDebug

APK output:

    app/build/outputs/apk/debug/app-debug.apk
