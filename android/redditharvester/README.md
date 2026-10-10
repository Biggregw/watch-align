# Reddit QC Collector 1.2.1: CSV-imported target lists

Standalone Android collector. This is **not** the Watch Align analysis app and does not change genuine references or calibration thresholds.

## Targeted collection

1. Install the Collector APK. If the Android signing key differs, uninstall the older **Collector** only. Leave Watch Align installed.
2. Tap **IMPORT THREAD LIST (.CSV)** and choose a research CSV from Android Downloads. It must contain `case_id`, `reference`, `reddit_url`, and `primary_feature` headers. Up to 1,000 rows / 2 MB are supported.
3. Confirm the number of unique posts. Multiple case rows for one Reddit thread are grouped.
4. Tap **TEST FIRST REDDIT THREAD**. A successful metadata fetch now unlocks Collect All even if the first post has no directly downloadable Reddit photos. If Reddit denies metadata access, the button remains disabled.
5. Tap **COLLECT ALL N THREADS**. Keep the phone awake, connected and in the app until the run completes.

Downloads are written to `Download/WatchAlign_Reddit_QC` and split below 28 MiB:

- Model-specific `Reddit_QC_<model>_partXX_ofYY.zip` with downloaded original photos, SHA-256, source manifest and selected post JSON.
- `Reddit_QC_Comments_partXX_ofYY.zip` with original accessible post/comment JSON **for all successfully retrieved threads, including those without photos**. For a one-thread test, `Reddit_QC_TEST_Comments_partXX_ofYY.zip`.
- `Reddit_QC_Collection_Report.zip` (or `Reddit_QC_TEST_Report.zip`) with the imported CSV, errors, source failures and counts.

The collector does not fetch private or inaccessible media, does not automatically scrape external Mega/Yupoo/Imgur album pages, and does not promise that each QC post has a Reddit-hosted photo. For those cases, links and failures are recorded so they can be handled separately.

Photo quality, watch geometry and reviewer labels remain provisional until externally validated. This app never sets Reddit-based QC tolerances.
