# Watch Align Android

Watch Align Android is a standalone on-device QC and comparison app. It does not require a hosted Watch Align backend: watch analysis, marker geometry, QC annotations and reference registration run locally with OpenCV Android. Internet access is used only to discover/download an exact-model official manufacturer reference when that model has a verified source configured and the user has not supplied a reference photo; downloaded references are cached locally.

Current working version: **1.3.0-alpha15**

Alpha15 adds a model catalog covering the recurring families seen in RepTimeQC-style submissions: Rolex GMT-Master II, Submariner/Date, Daytona, Datejust, Day-Date, Yacht-Master, Explorer and Oyster Perpetual; Omega Seamaster Diver 300M, Aqua Terra and Planet Ocean; Audemars Piguet Royal Oak; Patek Philippe Nautilus/Aquanaut; Tudor Black Bay/Pelagos; Vacheron Constantin Overseas; and Cartier Santos/Tank. Exact references/variants are represented by individual profiles where practical.

Round/indexed models use the common geometry engine when the photograph actually supports it. Cartier Santos/Tank profiles deliberately use a visual-only QC mode rather than pretending circular marker geometry applies. Photos/dial variants that fail safe geometry detection are refused rather than forced through the engine.

Current QC includes dial detection, perspective suitability, roll-corrected hour-marker geometry, marker-to-minute-track checks, shaped-marker body rotation, model-aware bezel/pip inspection, date-wheel/numeral centring, generic date-aperture alignment to the local clock/minute-track axis, generic cyclops lens alignment/rotation where the lens boundary can genuinely be isolated, Rolex rehaut inspection, dial-print quality regions, model-aware SEL dark-gap evidence, and perspective-gated advisory wording. A 6° date/cyclops angular offset corresponds to one minute-track division. If the cyclops lens itself cannot be isolated reliably the app does not draw a lens box or invent a cyclops verdict.

Automatic official-reference discovery is currently enabled only for exact models whose manufacturer source has been verified in the app (currently 126710BLNR and 124060). Other catalog models still receive local QC and can use a user-supplied genuine/reference image for comparison. This avoids silently substituting a similar or wrong reference.

Reference comparison uses deterministic geometry-first registration. The app refuses an overlay when geometry cannot be validated rather than lowering the safety threshold. Official reference images are not stored in the repository.

The Android project lives alongside the Windows application so the two implementations can continue converging toward a shared cross-platform core. Android remains an alpha and is not presented as proof of authenticity.

Build locally with Gradle 8.10.x and Android SDK 35:

    gradle :app:testDebugUnitTest
    gradle :app:assembleDebug

APK output:

    app/build/outputs/apk/debug/app-debug.apk
