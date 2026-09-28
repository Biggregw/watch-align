# Scope: 6 & 9 o'clock applied baton markers

Status: scoping only, no implementation yet. Follows the sequence and
feature definitions already governing this in `docs/architecture/QC_PRINCIPLES.md`
("Applied hour markers" + "First principle: frontal measurement before
perspective"). This doc turns those into a concrete engineering plan and
records what's reusable from the 12-triangle work
(`docs/research/gmt12_session_findings_2026-09-26.md`) vs genuinely new.

## Why 6 and 9, and why not reuse the 12 code directly

Rolex GMT-style dials use a triangle at 12 and rectangular **baton** markers
elsewhere. QC_PRINCIPLES already separates the human-perceived defects for
batons into four *distinct* checks that must not be collapsed into one score:

1. tangential/centre alignment vs the corresponding minute-track tick;
2. local centring/symmetry within the surrounding minute-track pattern;
3. radial height/clearance vs the local minute-track line;
4. rotation/cant vs local minute-track orientation.

(3) and (4) are the direct analogues of what `gmt12_auto_landmarks.py` /
`human_qc_geometry.py` already do for the triangle. But three things do not
carry over mechanically:

- **No tip.** The 12 code derives orientation from triangle-corner-to-tip
  vectors (`_polygon_to_triangle_corners`, `triangle_tip`). A baton is a
  rectangle with no apex; orientation has to come from a long-axis fit
  (e.g. `cv2.minAreaRect` or PCA on the blob), not a corner-to-point vector.
- **ROI search direction is hardcoded to "top of dial."** `_triangle_candidate`'s
  ROI (`cy - k*r` margin) and `_minute_ticks`'s band assume the marker sits
  above the dial centre near tick 60. A baton at 6 sits *below* centre; at 9
  it sits to the *left*. The search geometry needs to be parameterized by
  angular position, not rewritten per marker.
- **Two independent markers, two independent baselines.** 6 and 9 are
  different physical parts with potentially different tolerances (and 9 sits
  under the crown/date side on many models, which can change lighting/glare).
  Per QC_PRINCIPLES, each gets its own measurement and, eventually, its own
  genuine baseline -- not a shared band.

## What's directly reusable

- `_dial_circle` / `_pick_dial_circle` (dial seed detection) -- unchanged, marker-agnostic.
- `_tick_masks` / `_ticks` (tick blob extraction) -- unchanged, already scale/rotation-agnostic per-angle.
- `_direct` / `_sequence` / `_robust_line` / `_circle_tangent_landmarks` (tick-triple -> regularized local reference line) -- reusable as-is *if* generalized to take a target angle instead of assuming "near the top." These already do the hard part (glare-robust local minute-track line fitting) that a baton check also needs.
- `_trim_bezel_band`, `_touches_roi_edge` -- reusable safety nets, marker-agnostic.
- The whole fetch/measure CI pattern built this session (`tools/research/validation/gmt12/fetch_direct.py`, `measure_manifest.py`, `.github/workflows/gmt12-fetch-*.yml`) -- directly reusable for baton genuine-photo acquisition once a sourced CSV exists, same as it was for 126710BLRO.
- The fail-closed `Status`/`Assessment` pattern in `gmt12_qc_assessment.py` -- reusable shape, new module.

## What's new

### 1. Landmark contract (new module, e.g. `human_qc_geometry.py` additions or a sibling `baton_geometry.py`)

```python
@dataclass(frozen=True)
class BatonGeometry:
    hour_position: int          # 6 or 9 -- which marker this is, never inferred
    baton_outer_left: Point     # outer edge corner nearest the tick to its "left" (counter-clockwise)
    baton_outer_right: Point    # outer edge corner nearest the tick to its "right" (clockwise)
    baton_centroid: Point       # long-axis midpoint / blob centroid
    baton_axis_angle_deg: float # long-axis orientation, from minAreaRect/PCA, image-relative
    minute_inner_left: Point    # observed inner end of the immediate CCW-neighbour tick
    minute_inner_right: Point   # observed inner end of the immediate CW-neighbour tick
    minute_marker_center: Point # observed centre of the tick this baton should align to
                                 # (the "30" tick for 6, "45" tick for 9) -- analogue of minute_60_center
```

`hour_position` is a required, explicit input (never inferred from geometry) --
this is what lets one generalized detector serve 6, 9, and later 3, without
guessing which marker it's looking at.

### 2. Measurements (new `BatonMeasurements`, mirrors `Gmt12Measurements`)

- `radial_clearance_over_baton_width` -- signed perpendicular gap, local
  minute-track line to baton outer edge, / baton width. Direct analogue of
  `top_clearance_over_triangle_width`.
- `tangential_offset_over_baton_width` -- lateral displacement of baton
  centroid vs `minute_marker_center`, / baton width. Analogue of
  `horizontal_offset_over_triangle_width`.
- `rotation_deg` -- angle between `baton_axis_angle_deg` and the local
  tangent-regularized minute-track line. Analogue of the triangle's rotation.
- `left_clearance_over_baton_width` / `right_clearance_over_baton_width` /
  asymmetry -- same side-clearance diagnostic pattern as the triangle.

### 3. Detector changes (`gmt12_auto_landmarks.py` or a generalized sibling)

- Parameterize the ROI/search-band construction by a target angle (degrees
  from 12, clockwise) instead of the current top-of-dial assumption, so the
  same function serves 12, 6, 9 (and later 3).
- Replace `_polygon_to_triangle_corners` with a baton-specific corner/axis
  extractor: threshold the bright blob in the angle-rotated ROI, take
  `cv2.minAreaRect`, derive outer-edge corners as the two rect corners
  furthest from the dial centre.
- `_direct`/`_sequence`/`_circle_tangent_landmarks` take the same `(cx, cy)`
  dial-centre but need the neighbour-tick search to happen at the target
  angle's position along the tick ring, not assumed-near-top -- likely a
  small refactor to accept a starting angular index rather than always
  looking for ticks near x≈cx, y≈top.

### 4. Genuine calibration data

Cannot reuse the 12-triangle band (`GEN_TOP_CLEARANCE_LOW/HIGH`) or its
photos -- different physical feature. Needs its own real, provenance-checked
photo set (auction-house/dealer, same standard as the 126710BLRO set),
processed through the new detector, visually *and* subpixel verified before
trusting any spread, exactly as this session did for 12. Expect this to be
the slowest part, same as it was for 12.

### 5. Tests

Mirror `test_gmt12_auto_landmarks.py` / `test_human_qc_geometry.py` /
`test_gmt12_qc_assessment.py`'s structure: synthetic geometric cases first,
then real-coordinate regression tests once real photos are processed. Reuse
the "never trust a number without a visual overlay check" discipline from
this session (it caught 3 real bugs in the 12 code and one false alarm that
turned out not to be a bug).

## Order of work (per QC_PRINCIPLES step order, applied to batons)

1. Generalize the ROI/search-angle parameterization (no new measurement yet --
   prove the *existing* tick-line-fitting machinery still works when pointed
   at 6 and 9 on synthetic images).
2. Add `BatonGeometry`/`BatonMeasurements` + the minAreaRect-based corner/axis
   extractor, with synthetic unit tests (mirrors current triangle tests).
3. Run on a handful of real photos (reuse fetch CI pattern), visually
   overlay-check every one before trusting a single number.
4. Collect a genuine baseline (own provenance-checked photo set) once step 3
   proves the detector works on real photos.
5. Only then define pass/fail bands -- same fail-closed philosophy
   (`UNASSESSABLE` over a fabricated number) as `gmt12_qc_assessment.py`.
6. Perspective correction stays out of scope until steps 1-5 are proven on
   frontal images, per QC_PRINCIPLES' explicit gate.

## Explicit non-goals for this scope

- No perspective/tilt correction.
- No rehaut, hands, date/cyclops, or bezel-pip checks (separate QC_PRINCIPLES sections).
- No 3 o'clock marker yet (not asked for; the generalization above should make it cheap later, but it's not being built now).
- No implementation in this pass -- this document is the plan to execute next.
