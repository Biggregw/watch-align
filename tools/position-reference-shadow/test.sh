#!/usr/bin/env bash
# Local only. Use JDK 17 and pre-existing desktop OpenCV/JUnit/Hamcrest jars; no downloads/builds/CI.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
: "${SHADOW_TEST_CP:?Set SHADOW_TEST_CP to the desktop OpenCV 4.9.0-0, JUnit 4.13.2 and Hamcrest 1.3 jars}"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
SRC="$ROOT/android/app/src/main/java"
TEST="$ROOT/android/app/src/test/java"
PKG=com/watchalign/mobile
"$JAVA_HOME/bin/javac" --release 8 -Xlint:all,-options -d "$OUT" \
  "$SRC/$PKG/PositionReferenceFit.java" "$HERE/PositionReferenceFitTest.java"
cd "$HERE"
"$JAVA_HOME/bin/java" -cp "$OUT" com.watchalign.mobile.PositionReferenceFitTest
tests=(PositionReferenceShadowTest ClearlyOffTest WatchClassificationTest AlignmentDecisionTest MetalFitTest TriangleTopEdgeTest)
sources=(); classes=()
for test in "${tests[@]}"; do sources+=("$TEST/$PKG/$test.java"); classes+=("com.watchalign.mobile.$test"); done
"$JAVA_HOME/bin/javac" --release 17 -encoding UTF-8 -cp "$SHADOW_TEST_CP" \
  -sourcepath "$ROOT/tools/desktop-harness/shim:$SRC:$TEST" -d "$OUT" \
  "$SRC/$PKG/Alpha99Pipeline.java" "${sources[@]}" "$HERE/ShadowDesktopCheck.java"
"$JAVA_HOME/bin/java" -cp "$OUT:$SHADOW_TEST_CP" org.junit.runner.JUnitCore "${classes[@]}"
"$JAVA_HOME/bin/java" -cp "$OUT:$SHADOW_TEST_CP" com.watchalign.mobile.ShadowDesktopCheck
