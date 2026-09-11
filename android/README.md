# Watch Align Android

Watch Align Android is a standalone on-device QC and comparison app. It does not require a hosted Watch Align backend: watch analysis, marker geometry, QC annotations and reference registration run locally with OpenCV Android. Internet access is used only to discover/download an exact-model official manufacturer reference when that model has a verified source configured and the user has not supplied a reference photo; downloaded references are cached locally.

Current working version: **1.3.0-alpha16**

Alpha16 keeps the broad model catalog introduced in alpha15 and improves date/cyclops QC. For watches with a date aperture, the app now attempts to detect the local minute-track marker at the date position (for example the 15-minute marker at 3 o'clock, or 45-minute marker at 9 o'clock) and measures the date aperture against that photographed local anchor rather than only a theoretical global dial axis. If the local hash cannot be isolated reliably, the report states that it has fallen back to the fitted dial axis.

Where a genuine/reference image is available, the app also compares the apparent date-numeral height normalized to dial radius and reports it as a percentage of the genuine reference (`100% = genuine`). This is an image-based apparent magnification comparison, not a laboratory measurement of the cyclops optical magnification factor. Perspective mismatch between candidate and reference causes the result to be marked advisory.

Cyclops position is now primarily described relative to the detected date aperture (tangential/radial centre separation plus lens rotation). Its apparent position against the minute track remains explicitly perspective-sensitive because the cyclops sits above the dial plane and is subject to parallax. If the lens boundary cannot be isolated reliably the app does not draw a cyclops box or invent a lens verdict.

The model catalog covers recurring RepTimeQC-style families: Rolex GMT-Master II, Submariner/Date, Daytona, Datejust, Day-Date, Yacht-Master, Explorer and Oyster Perpetual; Omega Seamaster Diver 300M, Aqua Terra and Planet Ocean; Audemars Piguet Royal Oak; Patek Philippe Nautilus/Aquanaut; Tudor Black Bay/Pelagos; Vacheron Constantin Overseas; and Cartier Santos/Tank. Round/indexed models use the common geometry engine when the photograph supports it. Cartier Santos/Tank profiles deliberately use a visual-only QC mode rather than pretending circular marker geometry applies.

Other QC includes dial detection, perspective suitability, roll-corrected hour-marker geometry, marker-to-minute-track checks, shaped-marker body rotation, model-aware bezel/pip inspection, date-wheel/numeral centring, Rolex rehaut inspection, dial-print quality regions, model-aware SEL dark-gap evidence, and perspective-gated advisory wording. Fine-grained outputs are withheld or qualified when geometry cannot support a strong verdict.

Automatic official-reference discovery is currently enabled only for exact models whose manufacturer source has been verified in the app (currently 126710BLNR and 124060). Other catalog models still receive local QC and can use a user-supplied genuine/reference image for comparison. This avoids silently substituting a similar or wrong reference.

Reference comparison uses deterministic geometry-first registration. The app refuses an overlay when geometry cannot be validated rather than lowering the safety threshold. Official reference images are not stored in the repository.

The Android project lives alongside the Windows application so the two implementations can continue converging toward a shared cross-platform core. Android remains an alpha and is not presented as proof of authenticity.

Build locally with Gradle 8.10.x and Android SDK 35:

    gradle :app:testDebugUnitTest
    gradle :app:assembleDebug

APK output:

    app/build/outputs/apk/debug/app-debug.apk
