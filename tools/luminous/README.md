# Bounded experimental luminous implementation

Base: PR52 593966433079d04816e98f78da5b66abd1f4553d. Opt-in only.

`LuminousMarkerDetector` uses the existing pose/locations and ring. For each marker
it locally estimates luminous-face/dial contrast, finds the centre-connected
component at three contrast levels, interpolates observed level crossings and
requires a closed dark separator, sufficient native pixels and stable boundaries.
No fixed metal inset, outer fitted edges or genuine luminous sizes enter detection.
Existing interference failures are conservatively withheld. Holes and deep
concavities are withheld rather than repaired into a precise convex area score.
The method currently supports closed, approximately convex luminous footprints;
chips, scratches, printing/hand overlap and merged bevels may be not assessed.

Experimental reliability checks (local contrast, separator coverage, native size,
contour stability/concavity) are detector eligibility checks, not replica quality
thresholds or established confidence intervals. They are documented in source and
must be evaluated visually on the phone. Area-moment orientation is a different
landmark from outer long-edge rotation and is labelled experimental.

Actions workflow `luminous-marker-experiment.yml` downloads ONLY curated artifact
38222239457, verifies 132 hashes, then runs twelve GMT / eight Submariner development plus four disjoint
held-out recorded groups per model. Metadata excludes existing shared-image and
source-model conflicts. Groups remain provisional physical-watch identities.
Sources and split are fixed before measurement; no score-based sample selection.
`derive_references.py` gives each recorded watch equal weight, using median
64-ray profiles of its actually measured inner contours. Each key needs at least
three contributing groups. Rounds share an inner reference within their model;
3/6/9 batons and triangles have separate polygons. References live ONLY under
`assets/experimental/luminous`, never the production reference directories.

Run locally with JDK17 and the committed desktop harness:

```
JAVA_TOOL_OPTIONS=-Dwatchalign.model=gmt_126710 bash tools/desktop-harness/run.sh LuminousValidation inputs.csv output score
```

Input columns: photo_id,physical_watch_id,split,path. The harness asserts that
opening the experimental measurement does not change existing QC output and
reruns the complete outer pipeline on the first photo as a regression control.

Android unit tests: `cd android && ./gradlew :app:testDebugUnitTest`.
APK: manual `luminous-test-apk.yml`, only after references and held-out validation
exist. It performs unit tests, checks the frozen production measurement files,
builds arm64 with the existing shared signing key and publishes an Actions
artifact. No APK assets contain the 132 original photographs.
