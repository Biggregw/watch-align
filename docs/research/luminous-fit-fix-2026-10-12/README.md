# Frozen luminous fit regression

Same forty Alpha107 originals and frozen reference polygons. Every previous usable physical polygon and recorded outer value is asserted unchanged. No reference derivation, harvesting or acceptance thresholds. Listing identities remain provisional.

| Model | Before observed | After observed | Fitted geometry | Alignment only | Partial alignment supported | Newly measurable from previous withholding |
|---|---:|---:|---:|---:|---:|---:|
| gmt_126710 | 59 | 77 | 126 | 126 | 138 | 67 |
| submariner_124060 | 120 | 120 | 127 | 127 | 139 | 18 |
| submariner_126610 | 63 | 76 | 126 | 126 | 130 | 64 |

Fitted geometry reconstructs independently determined sides/intersections or circle centre/radius. Alignment-only rigidly transforms the unchanged expected template. Observed physical footprint is separate. All three use the same filled expected area denominator and saved polygons/regions; percentages are never added. Unsupported dimensions and percentages remain withheld.

## Recovered comparison contact sheets

![Raw / fitted / alignment-only / observed comparisons](gmt_126710_recoveries_1.jpg)
![Raw / fitted / alignment-only / observed comparisons](gmt_126710_recoveries_2.jpg)
![Raw / fitted / alignment-only / observed comparisons](gmt_126710_recoveries_3.jpg)
![Raw / fitted / alignment-only / observed comparisons](gmt_126710_recoveries_4.jpg)
![Raw / fitted / alignment-only / observed comparisons](gmt_126710_recoveries_5.jpg)
![Raw / fitted / alignment-only / observed comparisons](gmt_126710_recoveries_6.jpg)
![Raw / fitted / alignment-only / observed comparisons](gmt_126710_recoveries_7.jpg)
![Raw / fitted / alignment-only / observed comparisons](gmt_126710_recoveries_8.jpg)
![Raw / fitted / alignment-only / observed comparisons](gmt_126710_recoveries_9.jpg)
![Raw / fitted / alignment-only / observed comparisons](submariner_124060_recoveries_1.jpg)
![Raw / fitted / alignment-only / observed comparisons](submariner_124060_recoveries_2.jpg)
![Raw / fitted / alignment-only / observed comparisons](submariner_124060_recoveries_3.jpg)
![Raw / fitted / alignment-only / observed comparisons](submariner_126610_recoveries_1.jpg)
![Raw / fitted / alignment-only / observed comparisons](submariner_126610_recoveries_2.jpg)
![Raw / fitted / alignment-only / observed comparisons](submariner_126610_recoveries_3.jpg)
![Raw / fitted / alignment-only / observed comparisons](submariner_126610_recoveries_4.jpg)
![Raw / fitted / alignment-only / observed comparisons](submariner_126610_recoveries_5.jpg)
![Raw / fitted / alignment-only / observed comparisons](submariner_126610_recoveries_6.jpg)
![Raw / fitted / alignment-only / observed comparisons](submariner_126610_recoveries_7.jpg)
![Raw / fitted / alignment-only / observed comparisons](submariner_126610_recoveries_8.jpg)

Diagnostics distinguish seed rejection, crop leakage, enclosed dark pixels, convexity, contour instability and missing separator evidence. Enclosed pixels may be a separator shadow in a merged luminous/metal mask; they are not automatically material holes.

## Limitations and lessons learned

Independent edge fits can support alignment without proving every physical corner. Fitted percentages include idealised extrapolation and may disagree with physical footprint percentages. Fit residual and subset stability are not complete photographic confidence intervals. Human inspection of recovered polygons is required; this report does not claim universal absence of false detections or manufacturing tolerances.

## Validated test build

Replay: [38283629703](https://github.com/Biggregw/watch-align/actions/runs/38283629703), measurement commit `189313cb`. 387 unit tests, 18 native shape/resolution/hand checks, all 132 archive hashes verified; the exact previous 40-photo subset supplies 452 marker rows. These are photographic regressions, not 40 independently proven physical watches or manufacturing tolerances. Of previously withheld markers, 149 now have an observed or fitted area percentage; 31 additionally have observed full footprints. Fitted percentages are available for 379 markers, partial alignment for 407. All recorded prior usable physical polygons and available outer values are unchanged.

[Uploaded triangle, Batgirl and historical Pepsi comparisons](controls/README.md) include original hashes, exact before/after CSVs and six detailed comparison images. The suspected polished-rim GMT `709160117d0d8944` hour 1 is now withheld (zero independently evidenced metal-recovery samples). Fits with incomplete actual geometry still report supported direction/dimensions without inventing percentages.

Install the new **Watch Align Luminous Fit** (`com.watchalign.mobile.poc.edgefit.luminousfit`, Alpha107.2). It installs alongside earlier Alpha107 and PR #52 builds; a shared signing key was unavailable. Existing upload/model selection and outer QC remain the default. Open **Inspect luminous markers**, choose FITTED GEOMETRIC, FITTED alignment-only or Observed full footprint, then tap a marker. Each mode draws its own exact saved comparison polygons and extra/missing regions. Cyan in alignment-only is the expected template rigidly placed at the fitted pose, not an observed contour. No percentages are combined.

Test on the phone with the exact uploaded `37891.jpg` and Batgirl original. Inspect 12, 9 and rounds with/without outlines. One photograph is sufficient. Poor contrast, unresolved separator sections, short arcs, uncertain hand exclusion, unstable fits and missing independent ends still withhold the affected measurements. Fit residual/subset spans describe fit stability only; lighting, pose and provisional reference uncertainty can remain. Compilation and desktop replay do not demonstrate Android UI behavior or universally correct edge selection; phone visual review is still required.
