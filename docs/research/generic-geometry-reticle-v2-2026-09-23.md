# Generic geometry reticle v2 -- construction report

**Branch:** `experiment/generic-geometry-reticle-v2` (from the clean v1
reticle-prototype commit `7d37994`, before any AI-reviewer batch work).
**Scope:** visual geometry only. This report states only facts about how
the overlay was constructed. It does not interpret marker 12, does not
compute or suggest a pass/fail for any marker, and does not propose a
defect threshold.

## What was built

`reticle_v2.py` draws three independently-sourced geometry families and
nothing else:

1. **Minute-track-anchored structure** -- centre target, minute-track
   channel, 12 hour axes (12/3/6/9 slightly stronger), all gapped through
   the marker band. Unchanged from the v1 prototype's already-validated
   affine ellipse+roll basis.
2. **Round-marker corridor** -- inner, centre, and outer conics fit *only*
   from the 8 round hour markers (1, 2, 4, 5, 7, 8, 10, 11), via
   `marker_consensus.fit_conic` / `marker_consensus_analysis.
   fit_full_round_conic`. Markers 6, 9 (batons), 12 (triangle), and the
   date area are never segmented by this module at all -- there is no
   code path by which they could contribute to the fit.
3. **Diagnostic-only source points** (round-marker centroid/inner/outer
   evidence, hour labels) -- diagnostic-mode renders only.

Two render modes: `user_mode()` (the four geometry elements only, no
points, no labels, no numbers, no warnings) and `diagnostic_mode()`
(adds the source points/labels; fit residuals are reported in a separate
text panel image, never drawn on the photo).

Failure behaviour: if fewer than 5 of the 8 round markers segment, the
corridor is omitted entirely (not approximated, not extrapolated from
whichever markers did segment) and the reason is recorded.

## Construction facts, both photographs

Identical code (same commit, same constants, no per-image tuning) was
run via one generic script, `render_reticle_v2.py`, on both photographs.
Parameters were established and this render produced before the user's
photograph was rendered; nothing was changed afterward.

| | Official reference (`official_rolex_2026`) | User QC photograph (`frame_01`) |
|---|---|---|
| Source | First-party Rolex catalogue photo (provenance=official, split=reference -- not a source-labelled `gen_candidate`) | User-supplied GMT QC photograph, already in this research's local corpus |
| Pipeline pose | accepted, automatic_accepted | accepted |
| Tilt | 10.21 deg | 11.92 deg |
| Dial radius (px, at 1600px-max-dim decode) | 192.8 | 292.1 |
| Round markers segmented | **8 / 8** -- hours [1,2,4,5,7,8,10,11] | **4 / 8** -- hours [7,8,10,11] |
| Corridor status | fit (not suppressed) | **suppressed** (4 < `MIN_PEERS_FOR_CONIC`=5) |
| Corridor fit residuals (in-sample, non-leave-one-out) | inner: n=8, median=0.606px, robust_std=0.738px<br>centre: n=8, median=0.401px, robust_std=0.353px<br>outer: n=8, median=1.176px, robust_std=1.214px | n/a -- corridor not fit |
| Same algorithm/settings on both | **Yes** -- identical commit, identical `render_reticle_v2.py` invocation pattern, no constant changed between runs | |

## What this does and does not show

- The reference photo's full 8/8 round-marker coverage and sub-1.2px
  in-sample residuals demonstrate the corridor-fitting mechanism itself
  is working as designed on a clean, frontal, high-resolution source.
- The user's photograph segmented only 4 of 8 round markers (a
  handheld, angled, lower-resolution photo), which is below this
  module's fixed 5-marker minimum -- the corridor was correctly withheld
  rather than fit from an under-determined set or extrapolated from
  the round markers that did segment. This is the same graceful-
  degradation behaviour the v1 prototype demonstrated on this same
  photograph in earlier research.
- Viewpoint: both photographs' tilt (10.2 deg reference vs. 11.9 deg
  user) is close, so viewpoint mismatch between the two is not
  substantial for this comparison. No pose-matching or perspective-
  correction algorithm was introduced for this experiment, per
  instruction.
- Nothing in this report or the renders states whether any marker,
  including 12, is correctly placed, defective, or within any
  tolerance. That interpretation is explicitly out of scope for this
  experiment.

## Deliverables

Code and this report are committed to `experiment/generic-geometry-
reticle-v2`. Rendered images (derived from third-party/first-party
source photographs) are not committed to git, per this project's
standing rule; they were sent to the user directly.

- `tools/watch_align_py/reticle_v2.py` -- geometry core
- `tools/watch_align_py/reticle_v2_render.py` -- user-mode/diagnostic-mode renderers, side-by-side composer
- `tools/watch_align_py/render_reticle_v2.py` -- generic per-image driver
- `tools/watch_align_py/dial_crop.py` -- deterministic dial crop (reused from `experiment/generic-qc-reticle`)

## Explicitly out of scope (not done here)

- No 12-triangle defect logic, no 12-marker pass/fail, no 12-specific
  expected-position geometry.
- No manufacturing tolerances.
- No AI review of any kind.
- No Android changes, no production QC changes, no validation inspection.
- Nothing merged, no PR opened.
