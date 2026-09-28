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

1. **Done 2026-09-28.** Generalize the ROI/search-angle parameterization (no
   new measurement yet -- prove the *existing* tick-line-fitting machinery
   still works when pointed at 6 and 9 on synthetic images). Implemented as
   `tools/research/dial_rotation.py`: an exact, lossless 90-degree-multiple
   whole-image rotation (no interpolation, so the Otsu/tophat thresholds
   `_tick_masks` depends on see unchanged pixel data) that brings any
   cardinal marker (12=0, 3=90, 6=180, 9=270 clockwise degrees) to the "top"
   position, plus point-coordinate forward/inverse transforms for mapping
   landmarks back to the original frame. `_minute_ticks` itself is
   completely unmodified -- proven in
   `tools/research/tests/test_dial_rotation.py` (14 tests): point round-trip
   identity across all 4 angles, forward transform verified pixel-for-pixel
   against real `cv2.rotate` output (not just formula reasoning), and full
   synthetic end-to-end runs of the unmodified `_minute_ticks` at 6 o'clock
   (ticks below the marker, centre above -- physically backwards from what
   the un-rotated function assumes) and 9 o'clock (ticks wider-than-tall,
   arranged along y -- a shape the un-rotated function's filters would
   reject) both succeed once rotated, with results mapped back and checked
   against the original-frame ground truth. One real finding during this:
   180-degree rotation reverses left/right, so the rotated frame's "left"
   tick is the original frame's right-neighbour tick -- not a bug, but
   something step 2's baton corner-extraction needs to account for
   (left/right must be re-derived from the un-rotated x-order, not assumed
   to survive the round trip positionally).
2. **Done 2026-09-28.** Add `BatonGeometry`/`BatonMeasurements` + the
   minAreaRect-based corner/axis extractor, with synthetic unit tests.
   - `BatonGeometry`/`BatonMeasurements`/`measure_baton` added to
     `human_qc_geometry.py`, deliberately mirroring `Gmt12Geometry`'s shape
     (outer-edge-pair + one inner point; tick-pair + one tick centre) but
     written orientation-agnostically: `measure_gmt12`'s
     `_x_on_line_at_y`-based horizontal-offset calculation assumes a
     roughly-vertical axis (true at 12, also true at 6, but **not** at 9,
     where the long axis is roughly horizontal and `_x_on_line_at_y` hits
     its own degenerate-horizontal-line guard). `measure_baton` uses
     `_signed_point_line_distance` for both the radial and tangential
     measurements instead -- proven by
     `test_ideal_9oclock_matches_6oclock_numerically` in
     `tools/research/tests/test_baton_geometry.py`, which asserts a
     horizontal-axis case gives numerically identical results to the
     vertical-axis one, not just that it doesn't crash.
   - `tools/research/baton_auto_landmarks.py`: `_baton_candidate` (blob
     threshold + `cv2.minAreaRect` in the rotated frame, replacing
     `_polygon_to_triangle_corners`'s triangle-specific logic -- a baton has
     no apex to derive an axis from) and `_minute_ticks_at_angle` (thin
     rotate/call-unmodified-`_minute_ticks`/unrotate wrapper) and
     `detect_baton`, tying dial-seed detection + corner extraction + tick
     search + `measure_baton` together, mirroring `detect_gmt12`'s shape.
   - **Left/right resolved, correcting step 1's note:** step 1 observed
     that a 180-degree rotation reverses x-order and read that as
     "left/right gets reversed." Re-examined properly this step: a rotation
     (unlike a reflection) preserves orientation/handedness, so
     "smaller-x-in-the-rotated-frame = counter-clockwise-neighbour" (the
     existing 12 o'clock convention, where the 59-tick sits left of 60)
     actually survives unrotation correctly for *all four* supported
     angles -- confirmed against the true clockwise-tangent-direction
     formula (`position(theta)=(cx+r*sin(theta), cy-r*cos(theta))`,
     clockwise from 12) in
     `test_smaller_x_after_rotation_matches_true_counterclockwise_direction`,
     parametrized over all 4 angles. What step 1 actually observed was
     naive original-frame x-values swapping (e.g. x=230 vs x=170), not a
     break in the CCW/CW semantic labelling -- `_baton_candidate` relies on
     the labelling being correct and it is.
   - 20 new tests across `test_baton_geometry.py` and
     `test_baton_auto_landmarks.py` (synthetic point geometry + full
     pixel-level `_baton_candidate`/`_minute_ticks_at_angle` pipeline at
     both 6 and 9 o'clock, built via the same "construct in the easier
     rotated frame, unrotate to get the real picture" technique proven in
     step 1). One real debugging finding kept as a code comment: placing
     the tick band too close to the baton's outer edge let the centre
     tick's dilated bounding box merge with the baton blob and silently
     vanish (rejected by the tick-width filter) -- fixed by widening the
     synthetic gap, but worth remembering as a real failure mode once real
     photos are tried in step 3. 76/76 relevant repo tests pass.
   - Untested/placeholder: `_baton_candidate`'s ROI margins and size gates
     are carried over verbatim from `_triangle_candidate`'s real-photo-tuned
     values, not yet validated against an actual baton photo. `detect_baton`
     itself (the full entry point, dial-seed detection included) has no
     synthetic test, same as `detect_gmt12` -- `_dial_circle` needs a real
     Hough-detectable circle, which only a real or very carefully
     constructed photo provides.
3. Run on a handful of real photos (reuse fetch CI pattern), visually
   overlay-check every one before trusting a single number.
4. Collect a genuine baseline (own provenance-checked photo set) once step 3
   proves the detector works on real photos.
5. Only then define pass/fail bands -- same fail-closed philosophy
   (`UNASSESSABLE` over a fabricated number) as `gmt12_qc_assessment.py`.
6. Perspective correction stays out of scope until steps 1-5 are proven on
   frontal images, per QC_PRINCIPLES' explicit gate.

3. **In progress 2026-09-28 -- real bug found, not yet fixed.** Ran
   `detect_baton` on 26 real photos already available locally from earlier
   this session (4 verified-provenance 126710BLRO photos + 22 previously
   user-supplied genuine/replica GMT photos), both 6 and 9 o'clock, and
   visually overlay-checked results before trusting any number (script:
   `/tmp/.../scratchpad/run_baton_real_photos.py`, not committed -- ad hoc
   driver, see below for what should actually be committed).

   **9 o'clock: works correctly.** Visually verified 2/2 checked photos
   (blro_147798, img19) -- outer-edge corners land exactly on the baton,
   tick landmarks land exactly on the real chapter-ring ticks next to the
   engraved "ROLEX" text. Not exhaustively checked across all 26.

   **6 o'clock: systematic bug, confirmed on 2 independent real photos
   (blro_147798, img19 -- different crops/angles/sources).** The
   tick-search band lands on "SWISS ... MADE" (or equivalent) dial text
   instead of the true minute-track ticks. Root cause: `_minute_ticks`'
   search band (`top-.20*r` to `top+.035*r`, hardcoded inside that shared
   function) assumes the gap between the marker and its neighbouring ticks
   is blank dial -- true at 12, confirmed true at 9, but **not at 6**,
   where Rolex prints certification text in exactly that gap on this watch
   family. The real ticks sit further out, past the text.

   Concretely: on blro_147798, the found "tick" candidates in that band had
   heights of 5-22px with no consistent pitch (real ticks are much more
   uniform) -- almost certainly individual text glyphs, not ticks. This
   fooled not just the `_sequence` fallback (all 4 blro photos' 6 o'clock
   results went through `_sequence`, never `_direct`, unlike their clean
   9 o'clock `_direct` results) but on img19 it fooled `_direct` itself
   (confidence 0.993, "(direct)" reason, still landed on text) -- so
   confidence/reason alone do not reliably flag this failure.

   **Not fixed yet -- this needs a design decision, not a quick patch:**
   `_minute_ticks`' band margins are hardcoded inside that shared function,
   used by both the 12-triangle and baton paths. Options to widen the
   6 o'clock search past the text without risking the already-validated
   12/9 o'clock behaviour: (a) parameterize `_minute_ticks`' margin with a
   default matching its current hardcoded value, so `_minute_ticks_at_angle`
   can pass a wider one for 6 o'clock specifically; (b) build a baton-
   specific tick search from the lower-level primitives
   (`_tick_masks`/`_ticks`/`_direct`/`_sequence`/`_first_regularized`)
   directly, bypassing `_minute_ticks`'s own band construction entirely;
   (c) add a tick-candidate height-consistency filter strict enough to
   reject text glyphs (their heights varied 5-22px on the one photo
   measured; real ticks should be far more uniform) -- weakest option,
   since it patches a symptom rather than searching in the right place.
   (a) or (b) both need real measurement of how far out the true 6 o'clock
   ticks actually sit past the text, across more than one photo, before
   picking a margin -- not yet done.

   Also not yet done: the committed `_baton_candidate` ROI margins/size
   gates are still the untuned triangle-derived placeholders from step 2 --
   this run didn't surface a problem with them specifically (the baton
   blob itself was found correctly on every photo visually checked), but
   they haven't been independently stress-tested either.

### Update 2026-09-28: same-photo reference-pitch check -- partial fix, honestly bounded

Explored and rejected two other fixes first, with real evidence each time
(recorded for anyone revisiting this): (a) widening `_minute_ticks`' search
margin -- rejected once a wider raw photo crop showed the real ticks sit at
the SAME radius as "SWISS MADE" text, not further out, so radius alone
can't separate them; (b) a fixed geometric expected-pitch formula
(`r*2*pi/60`) -- rejected once pulled across the full real-photo set:
genuinely-correct 12-o'clock detections alone ranged ~0.65x-0.99x of that
formula's prediction with no clean separation from known-bad cases, so it
has no discriminating power. (Earlier notes above already flagged the
dial-centre-radial-alignment idea as not matching the intended definition
-- the marker must sit parallel to its immediate tick neighbours, not just
point at the dial centre -- so that one was dropped without implementing.)

**What was implemented instead:** `_first_regularized` (shared by the
12-triangle and baton code paths) now takes an optional `reference_pitch`
-- the tick pitch already measured on a DIFFERENT, successfully-detected
marker on the SAME photo. A candidate whose own fitted pitch falls outside
20% of that reference is skipped in favour of the next-best one, even a
lower-scored one. `detect_baton` sources this automatically by running
`detect_gmt12` on the same image first (12 o'clock has no known text-
contamination issue and is the most real-photo-tested marker), or accepts
a precomputed value via a new optional parameter for callers detecting
multiple positions on one image.

Real-photo evidence for the 20% tolerance: a corrected re-run (using the
actual `detect_gmt12`/`detect_baton` functions rather than a simplified
stand-in script -- the first attempt at this had a real bug, computing
pitch from x-differences only, which is wrong at 9 o'clock where the
tangential direction is image-y) showed measured 6-o'clock pitch, as a
ratio to the same photo's 12-o'clock pitch, cleanly split into two
clusters: ~0.87-1.04x (7 photos, genuine detections) and ~0.50-0.76x (4
photos: img13, img16, img20, img23 -- a likely half-pitch aliasing
failure, picking sub-features half a tick apart). The tolerance boundary
(reject below 0.80x) sits in the real gap between these clusters.

**Verified outcome, honestly mixed:** all 4 previously-bad photos now
measure closer to the reference pitch. Visual re-check of all 4:
- **img16: genuinely fixed.** Landmarks now land on the real tick dashes
  flanking "SWISS MADE", confirmed by eye.
- **img13: separately broken, unrelated to this fix.** It's a watermarked
  product-listing screenshot with UI chrome and a stray object in the
  background below the watch case; `_dial_circle` picked a wrong seed
  entirely for this image, so BOTH the 12 o'clock reference and the 6
  o'clock target inherit the same wrong scale -- a pitch-ratio check
  can't catch a same-photo-consistent wrong answer. Separate, pre-existing
  failure class, not something this fix was meant to address.
- **img20, img23: NOT fixed, despite passing the new check.** Visual
  re-check shows the landmarks are still sitting on "SWISS MADE" text, not
  the real ticks -- in these two cases the text's letter spacing happens
  to coincidentally land within 20% of the true pitch, so the filter lets
  it through. The pitch signal alone cannot fully distinguish real ticks
  from text that happens to be evenly spaced at a similar scale.

**Conclusion:** this is a real, verified, net-positive improvement (fixes
the half-pitch-aliasing failure mode outright) but is NOT a complete
solution to 6 o'clock's text-contamination problem. Some fraction of
photos will still silently report a wrong number. A fuller fix would need
an additional, independent signal (shape/solidity was tried in-session and
also didn't cleanly separate text from ticks on its own; cross-position
consistency using the ACTUAL measured 9 o'clock clearance as a validity
check on 6, per the "circle drawn through the outer edges should be the
same distance from the minute markers at every hour" idea, is the most
promising remaining direction but is unimplemented -- it needs 6 to be
reliable enough first to not just flag itself).

## Explicit non-goals for this scope

- No perspective/tilt correction.
- No rehaut, hands, date/cyclops, or bezel-pip checks (separate QC_PRINCIPLES sections).
- No 3 o'clock marker yet (not asked for; the generalization above should make it cheap later, but it's not being built now).
- No implementation in this pass -- this document is the plan to execute next.
