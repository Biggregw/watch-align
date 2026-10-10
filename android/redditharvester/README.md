# One-off Reddit QC collector for Watch Align

Standalone Android debug APK. Not part of Watch Align's analysis application. Uses research seed case records (51 observations in 49 unique Reddit threads) to retrieve original available Reddit gallery photographs and source JSON with comments.

1. Install the APK from the Reddit QC Collector build artifact.
2. On the phone, press **TEST FIRST REDDIT THREAD**.
3. Open Samsung My Files > Internal storage > Download > WatchAlign_Reddit_QC and check the model-specific ZIP plus TEST report ZIP.
4. If the test contains watch photos and manifest.csv, press **COLLECT ALL 49 THREADS**. Keep the phone awake and online until DONE.
5. Upload ZIP files here. Each model-specific ZIP part must stay under 28 MiB. The app records failures and external gallery links for follow-up.

This is a bounded **one-off** collector for the existing seed, not an unlimited Reddit scraper. It does not modify Watch Align calibration, genuine datasets or QC thresholds. It does **not** automatically certify photographs as near-frontal or measure watch geometry.

Caveats: Android HTTP access may differ from the Chrome browser. A browser opening Reddit images does not guarantee HttpURLConnection can download every one. Imgur album pages and Yupoo albums are logged as external rather than scraped automatically; direct i.imgur.com image links are supported. Reddit may rate limit or block automated requests. The app records HTTP failures rather than trying to bypass access restrictions. One-thread test is therefore mandatory.
