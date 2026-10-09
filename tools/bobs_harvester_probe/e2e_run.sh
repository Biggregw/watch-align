#!/usr/bin/env bash
# One bounded end-to-end run of the installed app on the emulator: clear data, launch with a keyword filter, wait for
# the harvest and the ZIP export to finish, then pull the app's run log, decision CSVs, accepted image list and the
# ZIPs from Downloads/WatchAlign_Bobs_Harvest into $OUT/<name>/.
set -u
NAME="$1"; FILTER="$2"; MAXP="$3"; OUT="${OUT:-e2e_out}"; PKG=com.watchalign.bobsharvester
D="$OUT/$NAME"; mkdir -p "$D/zips" "$D/files"
adb shell pm clear "$PKG" >/dev/null
adb shell rm -rf /sdcard/Download/WatchAlign_Bobs_Harvest
# adb shell joins its arguments into one device-shell command line, so a multi-word filter must be quoted for that shell
# (run 37858978707: "submariner 124060" arrived as "submariner" and the stray word dropped --ei max_products)
adb shell "am start -n $PKG/.MainActivity --es filter '$FILTER' --ei max_products $MAXP --ez auto_zip true" >/dev/null
BASE=/sdcard/Android/data/$PKG/files/Bobs_Rolex_Harvest
# Android 14 refuses shell reads of Android/data; the google_apis emulator allows adb root (set up by the workflow)
fetch(){ adb exec-out cat "$BASE/$1" 2>&1; }
# POLL: number of 10 s polls (default 72 = 12 min; the CI harvest job sets more)
for i in $(seq 1 ${POLL:-72}); do
  sleep 10
  fetch run_log.txt > "$D/run_log.txt"
  if grep -q "Export summary\|ZIP export: nothing to export" "$D/run_log.txt"; then break; fi
done
sleep 3
fetch run_log.txt > "$D/run_log.txt"; fetch image_decisions.csv > "$D/image_decisions.csv"; fetch accepted.csv > "$D/accepted.csv"
adb exec-out sh -c "cd $BASE && tar -cf - accepted_images" > "$D/accepted.tar"
tar -xf "$D/accepted.tar" -C "$D/files" 2>/dev/null; rm -f "$D/accepted.tar"
adb pull /sdcard/Download/WatchAlign_Bobs_Harvest/. "$D/zips/" >/dev/null 2>&1 || true
echo "=== $NAME: finished after ~$((i*10)) s of polling; run_log.txt:"; cat "$D/run_log.txt"
echo "=== $NAME: Downloads/WatchAlign_Bobs_Harvest:"; ls -la "$D/zips"
