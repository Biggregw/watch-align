# Alpha107 luminous test build

Base: PR52 `593966433079d04816e98f78da5b66abd1f4553d`.
Separate branch: `feature/luminous-marker-experiment`. No merge or PR52 change.

The shared signing secret is unavailable in CI. This test APK therefore installs
as **Watch Align Luminous Test**, package `com.watchalign.mobile.poc.edgefit.luminous`,
alongside the existing PR52 app rather than replacing it. Both outer and inner
views are available in this experimental APK. APK size is approximately25 MB.

Upload one QC photograph and select the same model as usual. Existing results are
unchanged. Open **Inspect luminous markers** explicitly to run the experiment.
Tap a marker on the full dial (or its button) for the close-up. Toggle the outline
checkbox to compare the actual visible insert with the reference/detection.
Yellow is expected inner lume, cyan detected, orange extra, magenta missing.
The full dial and close-up use exactly the same saved polygons as the numbers.
All differences belong to that marker; no watch-wide average or quality verdict.

Available for GMT126710, Sub124060 and steel Sub Date126610: triangles, rounds,
6/9 batons, and the 124060 3 baton. Round rotation is not observable. Other shapes
use experimental area-moment orientation, which is a different landmark from the
outer detector's fitted-edge rotation. Right/down are positive displacement.
Size is area difference; major/minor spans use each footprint's own orientation.

## Validation completed before APK

- 132 original hashes verified; only 40 recorded genuine groups measured: GMT12
  development +4 held out; each Sub8 development +4 held out. Held-out groups did
  not define references. Identities remain provisional listing groups.
- All reference keys have >=3 genuine contributors. GMT9 has5; GMT6 has3; other
  keys have4–9 contributors. Each recorded watch has equal reference weight.
- Held-out assessed luminous areas: GMT12/44, 12406036/48, 12661023/44.
  Every failed marker and reason is retained. The high withholding rate is a
  limitation, not evidence that the watches are defective.
- 374 Android unit tests;12 native independent shape/resolution/hand checks;
  original repeat/control and polygon/area checks. Production files unchanged;
  complete outer reruns agree before/after the opt-in measurement.
- Original Batgirl9: luminous rotation -1.727018°, mismatch15.944486%
  (extra8.046560 +missing7.897926). Resized960 and JPEG85 copies: rotation
  -1.707688 /-1.731017°, mismatch15.987865 /16.157005%. Same original repeated
  exactly. These copies are repeatability checks of one watch, not new samples.
- Original Batgirl outer rotation remains -1.174605° and WORTH; position/outer
  area remain withheld. Pepsi outer mismatch remains2.421278% and WITHIN.
  This historical Pepsi's luminous areas are withheld because no stable isolated
  contour passed the checks; it was not added to the near-frontal reference.
- Batgirl luminous12 and6 are measured independently (6.883068% /6.218367%);
  no quality threshold or pass/fail judgement is assigned.

## What remains experimental

Inner contour segmentation, references, shape/size/area and area-moment rotation
all require phone visual review. Observed outline stability is not a calibrated
confidence interval. Lighting/texture, polished bevel connections, insufficient
source pixels, holes/concavities, failed ring placement and existing interference
checks can withhold individual measurements. Closed approximately convex inserts
are supported; chips/notches may be withheld rather than quantified. A nonzero
percentage is not a claim of unacceptable manufacture.

Please inspect the Batgirl9/12, genuine triangle and round boundaries against the
plain crop, then compare the unchanged outer view. The APK compiling and these
checks passing do not establish Samsung rendering, tap behaviour or practical
photographic reliability. Installation and screenshots are the next validation.

## Lessons learned

A single local cut yielded insufficient GMT9 references; a bounded contrast
ladder with the same stability/separator checks and four additional existing GMT
development groups supplied five contributors. No held-out result selected or
changed a reference. Bright surfaces still often fail isolation. Sparse references
and photographic localisation remain uncertainty sources, so no acceptance bands,
factory tolerances or production QC changes are justified by this build.
