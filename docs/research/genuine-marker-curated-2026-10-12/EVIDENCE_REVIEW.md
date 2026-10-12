# Human review of the frozen genuine evidence

Measurement: [Actions 38235551437](https://github.com/Biggregw/watch-align/actions/runs/38235551437).
Provenance/table review: [Actions 38238576602](https://github.com/Biggregw/watch-align/actions/runs/38238576602).
Both succeeded. PR #52 remains draft at `593966433079d04816e98f78da5b66abd1f4553d`.
Production source/assets are identical to that commit. No APK, merge, harvesting,
new geometry, calibration or classification changes.

## What the study supports

All **132 unchanged original hashes** match the curated manifest and existing
catalogues. The 46 / 31 / 55 photographic counts become **44 / 31 / 55 recorded
watch groups** after retaining one original per recorded watch. Two repeat 124060
photos are listed separately, not additional watches. All **130 selected originals
pass the frozen pose**, yielding **1,474 individual marker rows**. No source is
quietly dropped because a marker is difficult.

The app's existing area gates clear **1,041 marker comparisons**; **433 are withheld**.
Existing shared-image warnings exclude 47 otherwise-cleared observations. Two
unresolved source-model conflicts exclude another 16. The reviewed distribution
therefore contains **978 marker observations** across **119 recorded groups with
at least one usable area**: GMT 26, 124060 38, 126610 55. These are listing groups,
not a serial-authenticated census; inferred and cross-vendor identities remain
provisional. Every individual percentage is retained; no watch quality average
is calculated.

The **1,329 available raw polygon comparisons**, including explicitly withheld
diagnostics, pass independent polygon-area and filled-denominator arithmetic
checks. Yellow/reference, cyan/fitted, orange/extra and magenta/missing in the
gallery use those exact saved polygons. Source crops are display resizes only;
they were never measurement inputs.

| Reviewed family / shape | Usable recorded groups | Area observations | Median total % | P90 % | P95 % | Maximum observed % |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| GMT 126710 baton | 26 | 41 | 3.059967 | 4.015634 | unavailable | 4.711614 |
| GMT 126710 round | 22 | 135 | 2.173007 | 2.724122 | unavailable | 3.222077 |
| 124060 baton | 37 | 106 | 2.746568 | 5.145011 | unavailable | 6.931386 |
| 124060 round | 36 | 229 | 1.582947 | 2.923736 | unavailable | 4.041513 |
| 126610 baton | 55 | 98 | 1.719510 | 2.603722 | 2.882384 | 3.603356 |
| 126610 round | 55 | 369 | 1.008873 | 1.706962 | 1.906305 | 3.018094 |

These are conditional photographic fit distributions, **not Rolex factory
tolerances, acceptable replica bands or confirmed physical manufacturing spread**.
Extra and missing distributions are separately exported. Their medians need not
sum to the median total; the agreed formula holds for every individual marker.
P90/P95 publication criteria concern sparse statistical tails, not QC thresholds.

## Identity and model qualifications

The 124060 placeholders were resolved by exact SHA against the existing catalogue.
The existing 8+-marker / 0.0005 R shared-dial diagnostic identifies two cross-listing
pairs: `39d163d48710cb5a` / `d04c99024b491471` and `63dc466faad01512` /
`a873cd649dee3d36`. Their maximum discrepancies are 0.000187 / 0.000337 R over
11 common markers. This supports reused-image or overlapping-identity concern;
it does not prove watch equality. Both pairs are excluded conservatively.
GMT `efaeab699876765e` also has a pre-existing shared-image flag.

`b0a1a6ff0bf6db97` and `7888550a54853ebb` are labelled 126710BLNR by the curated
manifest/catalogue but **both source URLs and image filenames say 116710**.
Their 126710-template comparisons remain unchanged and inspectable, but are
quarantined pending physical-reference confirmation. In particular, the initially
highest claimed-GMT baton result **5.149409%** is not included as genuine 126710
variation. The largest eligible GMT baton is instead **4.711614%**, photo
`708ff4c725496d44`, 9 o'clock. This exclusion is based on contradictory provenance,
not on the desired score. The original claimed-model aggregate is preserved.

## Selected outer-metal evidence

These observations are visual review of the published raw/rectified/outlined
crops, not independent metrology or acceptance decisions. Existing gates clearing
an area does not prove the fitted edge is the exact physical outer-metal boundary.

- [GMT 126710 9, `708ff4c725496d44`](outliers/708ff4c725496d44_9.png):
  **4.711614% = 4.711340% extra + 0.000274% missing**, R 434.631 px. The
  comparison is dominated by outward footprint differences. Bright metal has
  multiple visible facet transitions. Template dimensions, actual marker
  dimensions and edge localisation remain alternatives; no physical defect is
  established. Existing alignment QC is WITHIN.
- [124060 6, `855041528fc1ac79`](outliers/855041528fc1ac79_6.png):
  **6.931386%**, entirely extra to exported precision, R **199.318 px**.
  The raw boundary is visibly pixelated and the expected/fitted rectangles differ
  around the surround. At this scale, small edge movements can materially change
  area. A real dimensional difference versus the nominal is also possible;
  the photo does not separate these causes. Rotation/position QC remains WITHIN.
- [124060 round 11, `f27a64f84579f41b`](outliers/f27a64f84579f41b_11.png):
  **4.041513% = 0.060891% extra + 3.980622% missing**, R 679.531 px.
  Its bright face and broad darker lower bevel make localisation of one circular
  outer boundary uncertain even at higher resolution. Inward tracing is a
  plausible contributor, not independently proven. Alignment QC is WITHIN.
- [Withheld 124060 6, `5986c5a3258ae9ec`](outliers/5986c5a3258ae9ec_6.png):
  the seconds-hand shaft is visibly adjacent to/crossing the fitted region and
  the exploratory polygon absorbs part of it. Its **13.189180% diagnostic** is
  withheld by interference checks and never enters the genuine area distribution.
- [Withheld 124060 round 8, `855041528fc1ac79`](outliers/855041528fc1ac79_8.png):
  **15.887551% missing** is an offline diagnostic on the same low-resolution
  source. The apparent inward circle and poorly resolved rim warrant caution;
  the existing QC assessment is NOT_ASSESSED. It is not a confirmed physical size
  defect and is excluded from distributions.
- [126610 6, `821f211e705bfd5b`](outliers/821f211e705bfd5b_6.png):
  **3.603356% = 3.402685% extra + 0.200671% missing**, R 437.531 px,
  with bright outer bevels and smaller geometric discrepancies. Existing
  rotation/position QC is WITHIN; a maximum photograph score is not a severity
  threshold.

The other selected examples and all original source/image URLs are in
[the gallery](GALLERY.md) and [outlier index](outlier_index.csv). Model-conflicted
examples remain labelled diagnostics. Larger raw percentages withheld for hands,
edges or missing assessability must not be interpreted as physical defects.

## The 12 triangle remains unvalidated for area-defect scoring

All **130 triangle areas remain withheld**. There are 126 usable fitted diagnostic
polygons before provenance qualifications; none becomes an enabled area score.
Across the original curated claims, diagnostic median totals were **11.724509% GMT,
8.778687% 124060 and 9.380045% 126610**. These are diagnostic results, with two GMT
source identities now quarantined, not genuine-tolerance percentiles.

The fitted triangle footprints tend to be wider and taller than the expected
reference: original-claim median base-width / height differences are approximately
**+6.00% / +5.48% GMT**, **+4.83% / +4.04% 124060**, and **+4.92% / +4.18% 126610**.
Every fitted GMT triangle and most Submariner triangles are predominantly or
entirely extra area. Exact per-photo dimensions, percentages and provenance
qualifications are in [triangle diagnostics](triangle_diagnostics.csv).

The [GMT](outliers/7f94f08a2015c94e_12.png) and
[126610](outliers/5dd3527c3a74bc6e_12.png) close-ups show a consistent footprint
disagreement under curated near-frontal, higher-resolution conditions. Therefore
heavy auction perspective or low resolution alone cannot explain this entire
pattern. However, the bright outer facets, dark facets, rounded corners and
straight fitted-side intersections are not independently traced physical borders.
The evidence cannot yet separate nominal geometry, true physical size/shape,
bright-bevel/halo selection and corner extrapolation. **Keep triangle area withheld**
until the complete outer-metal definition is independently validated; do not
enlarge tolerances or resize the template to make the photographs pass.

Existing withholding reason text can be generic; it is not proof that the named
side actually lies outside the genuine range on a specific photo. The numerical
measure statuses are retained. No production wording or logic is changed here.

## Resolution, spacing and baseline sufficiency

Native dial radii span **433.65–834.25 px GMT**, **188.19–776.67 px 124060** and
**434.36–440.88 px 126610**. Only `c58f78ef6c1cd5b7` triggers the frozen working-size
rule: 3672-square original -> 3200-square working bitmap; the original hash remains
unchanged. Source and working dimensions are both exported. The
[native-resolution association table](native_resolution_associations.csv) compares
radius ranks descriptively, without treating resizes as extra watches or inventing
a resolution/QC cutoff. Different watches, sources and lighting confound these
associations; this study is not controlled photo-to-photo uncertainty calibration.

Minute-track position estimates, global ring corrections and marker-area mismatch
remain separate. **No directly observed metal-to-tick gap is available** from this
frozen pipeline. The base-to-nominal-track diagnostics are not direct gaps and
must not be used to flag a confirmed spacing defect. Nearby ticks in the images
assist human inspection but do not independently resolve polished metal boundaries.

All selected sources are established dealers. GMT comprises Bob's / SwissWatchExpo;
124060 has multiple dealer sources; **all 55 date Submariners are Bob's** with a
very narrow radius range. One-source lighting/editing can masquerade as physical
variation. The existing QC produces **no CLEAR or WORTH individual marker results**
on this selected cohort, but 10 MINOR descriptions and 257 NOT_ASSESSED rows remain;
that does not prove all physical shapes are perfect or visually acceptable.

The available photographs are sufficient to **begin provisional, model-specific
photographic baselines for assessable batons and rounds**. They are not sufficient
to establish independently separated manufacturing tolerances, generalise the
date model to all photographic styles, certify cross-vendor watch independence,
enable triangle area scoring, or choose replica acceptance bands. Human inspection
of the worst genuine examples is the next decision point; production remains frozen.

## LESSONS LEARNED

Byte integrity and face-on curation do not establish correct model identity,
independent watches or physical boundary accuracy. A contradictory image filename
can invalidate a population assignment despite a perfect SHA match. Reused-image
alerts and uncertain polished edges must remain visible, and current gate-cleared
outliers require review rather than automatic acceptance as factory tolerance.
The strongest unresolved pattern is triangle footprint disagreement across good
photographs. Its cause needs independently resolved outer-metal borders and
held-out source comparisons before changing geometry or detection. This study
does not promote any new tolerance, severity score or calibration protocol.
