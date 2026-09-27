#!/usr/bin/env bash
# Runs the app's own analysis code on desktop Java with OpenCV 4.9 (no phone/emulator).
# Usage:
#   tools/desktop-harness/run.sh E2E  <image> <overlay_out.png>        # one photo: summary + report + overlay
#   tools/desktop-harness/run.sh Batch <resolved_images.csv> <dataset_root> <out.csv> <crops_dir> [nshards shard]
#   tools/desktop-harness/run.sh AxisViz <image> <out.png>             # 12-marker close-up, true-12 vs triangle axis
#   tools/desktop-harness/run.sh Apex <image>...                       # fitted apex angle / squareness per photo
# Needs JDK 17. Downloads org.openpnp:opencv (desktop OpenCV Java incl. native libs) on first run.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(cd "$HERE/../.." && pwd)"
JAR="$HERE/.cache/opencv-4.9.0-0.jar"; OUT="$HERE/.cache/classes"
mkdir -p "$HERE/.cache"
# A rate-limited Maven Central returns a tiny text body; treat anything under 1 MB as missing.
if [ ! -f "$JAR" ] || [ "$(stat -c %s "$JAR")" -lt 1000000 ]; then
  curl -fsSL -o "$JAR.part" https://repo1.maven.org/maven2/org/openpnp/opencv/4.9.0-0/opencv-4.9.0-0.jar && mv "$JAR.part" "$JAR" \
    || { echo "Could not download opencv-4.9.0-0.jar (Maven Central may be rate limiting; retry later or copy it to $JAR)" >&2; exit 1; }
fi
rm -rf "$OUT" && mkdir -p "$OUT"
javac -encoding UTF-8 -nowarn -cp "$JAR" -sourcepath "$HERE/shim:$ROOT/android/app/src/main/java:$HERE/drivers" -d "$OUT" "$HERE"/drivers/*.java
MAIN="$1"; shift
exec java -Xmx2g -Dfile.encoding=UTF-8 -cp "$JAR:$OUT" "com.watchalign.mobile.$MAIN" "$@"
