#!/usr/bin/env bash
# JVM parity harness: runs the real Android pose + Alpha94 measurement code on desktop OpenCV 4.9
# (org.openpnp:opencv:4.9.0-0) so it can be compared with the Python research pipeline.
# Usage: run.sh <image.png> <name> [<9 comma-separated H values: frozen research H for fitter/measurement parity>]
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../../.." && pwd)
JAR=${OPENCV_JAR:-$HOME/.m2/repository/org/openpnp/opencv/4.9.0-0/opencv-4.9.0-0.jar}
[ -f "$JAR" ] || mvn -q dependency:get -Dartifact=org.openpnp:opencv:4.9.0-0
WORK=$(mktemp -d); mkdir -p "$WORK/src/com/watchalign/mobile"
SRC="$ROOT/android/app/src/main/java/com/watchalign/mobile"
# Android-free production classes, unchanged
for f in "$SRC"/*.java; do grep -q "^import android\.\|extends Activity" "$f" || cp "$f" "$WORK/src/com/watchalign/mobile/"; done
rm -f "$WORK"/src/com/watchalign/mobile/{Sub124060*,GmtHumanSummary,GmtTwelveRecoveryAnalyzer}.java
cp "$SRC/Alpha94MarkerMeasurement.java" "$WORK/src/com/watchalign/mobile/"
# AutomaticDialOverlay with only the outline-bitmap rendering stubbed (pose path unchanged)
python3 - "$SRC/AutomaticDialOverlay.java" "$WORK/src/com/watchalign/mobile/AutomaticDialOverlay.java" <<'PY'
import sys
s=open(sys.argv[1]).read()
s=s.replace('Bitmap overlay=warpOutline(input.getWidth(),input.getHeight(),h);','Bitmap overlay=new Bitmap(1,1);')
i=s.index('    private static Bitmap warpOutline('); k=i; depth=0; started=False
while True:
    c=s[k]
    if c=='{': depth+=1; started=True
    elif c=='}':
        depth-=1
        if started and depth==0: break
    k+=1
open(sys.argv[2],'w').write(s[:i]+s[k+1:])
PY
cp -r "$HERE/stubs/." "$WORK/src/"; cp "$HERE/HarnessMain.java" "$WORK/src/com/watchalign/mobile/"
javac -nowarn -d "$WORK/out" -cp "$JAR" $(find "$WORK/src" -name "*.java")
java -cp "$WORK/out:$JAR" com.watchalign.mobile.HarnessMain "$@"
