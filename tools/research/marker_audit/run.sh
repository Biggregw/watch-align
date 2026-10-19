#!/usr/bin/env bash
# Intentionally fail through pipelines too: an incomplete audit must never be green.
set -euo pipefail
ROOT=$(git rev-parse --show-toplevel)
cd "$ROOT"
OUT=${1:?usage: run.sh OUTPUT_DIRECTORY IMAGE_ROOT}
IMAGES=${2:?usage: run.sh OUTPUT_DIRECTORY IMAGE_ROOT}
PYTHON=${PYTHON:-python3}
mkdir -p "$OUT/cls" "$OUT/trace-src"
"$PYTHON" tools/research/marker_audit/fetch_cases.py tools/research/sub126610/catalogue_126610.csv "$IMAGES" "$OUT"
JAR=tools/desktop-harness/.cache/opencv-4.9.0-0.jar
test -s "$JAR" || { echo "Missing desktop OpenCV 4.9.0-0 jar: $JAR" >&2; exit 1; }
"$PYTHON" tools/research/marker_audit/prepare_trace.py android/app/src/main/java/com/watchalign/mobile/MetalFit.java "$OUT/trace-src/TracedMetalFit.java"
javac -encoding UTF-8 -nowarn -cp "$JAR" -sourcepath tools/desktop-harness/shim:android/app/src/main/java \
    -d "$OUT/cls" tools/desktop-harness/drivers/*.java "$OUT/trace-src/TracedMetalFit.java" tools/research/marker_audit/MarkerAudit.java
java -Xmx2g -Dwatchalign.model=submariner_126610 -cp "$JAR:$OUT/cls" com.watchalign.mobile.MarkerAudit \
    "$OUT/cases.csv" "$IMAGES" "$OUT/trace" | tee "$OUT/audit.log"
"$PYTHON" tools/research/marker_audit/render.py "$OUT/trace"
