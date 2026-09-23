# Generic QC alignment reticle -- visual-instrument prototype

**Branch:** `experiment/generic-qc-reticle` (built on top of the completed
`experiment/projective-marker-consensus` research). **Status:** prototype
for review. **Scope:** Python only. No Android changes. No production QC
changes. No PR to `main`. Not a defect classifier.

## 1. Product-philosophy shift

Every prior experiment in this research line (radial-drift diagnosis,
multi-radius homography, pose-library overlay, projective marker
consensus) tried to **automatically decide** whether a marker was
defective -- fit a reference, measure a residual, threshold it. That work
is done and is a real, separate deliverable (see
`projective-marker-consensus-2026-09-23.md`).

This prototype answers a different, narrower question: **can we build an
overlay that a human trusts, that makes geometric departures from normal
dial layout visually obvious, without pretending to reproduce the dial
itself or to auto-score anything?**

The product is: *automatic alignment + a trustworthy inspection reticle.*
Automated anomaly flagging is explicitly **not** required for this to be a
successful product, and is not attempted here.

## 2. Design principle: two geometric sources, kept separate

The reticle draws two geometrically different kinds of element, from two
different sources of truth, and never blends them into one fabricated
"exact perspective":

1. **Minute-track-anchored elements** (centre target, minute-track
   channel, 60 tick/gate references, 12 radial axes) use the existing,
   validated affine ellipse+roll basis (`geometry.map_point`, radii from
   `master.py`). This basis was repeatedly shown in this research line to
   be reliable for the minute track's own radius and for angle generally
   -- it is used here strictly within that validated regime.
2. **Marker envelope guides** (round-marker centre/outer/inner rings, the
   12-triangle's expected outer point) are fit directly from **this
   photo's own** detected round-marker points
   (`marker_consensus.fit_conic`), never extrapolated inward from the
   affine ellipse. If fewer than 5 round markers segment
   (`mc.MIN_PEERS_FOR_CONIC`), the envelope guide is omitted entirely --
   verified in practice on frame_03 (3/8 round markers), where no
   envelope is drawn.

Treating the minute-track's own affine basis as if it also told you
exactly where a marker ring sits is precisely the failed assumption this
research line spent weeks disproving (`radial-drift-root-cause-2026-09-22
.md` and the two rejected correction attempts that followed it). This
prototype deliberately does not repeat that mistake.

No element reproduces any Rolex-specific artwork -- no text, no
hour-marker shapes, no lume plots, no bezel graphics. Every element is a
plain geometric primitive (point, thin line, arc) that this project can
actually justify from measured geometry.

## 3. Three variants prototyped

All three share one `ReticleGeometry` (built once per photo by
`reticle.build_geometry`) and differ only in which elements they render
and how (`reticle_render.py`, `VARIANTS` dict).

| | A. Classic radial | B. Split-line / Vernier | C. Minimal + envelope |
|---|---|---|---|
| Minute channel | yes | yes | yes |
| Per-minute marks | short radial ticks on the tick position (all 60) | tangential gate brackets flanking each tick (all 60) | hour-only radial ticks (12) |
| 12 radial axes | yes, gapped around markers | yes, gapped around markers | yes, gapped around markers |
| Centre target | small bullseye | small bullseye | small bullseye |
| Marker envelope (round centre/outer/inner rings, triangle guide) | no | no | yes |
| Line count / density | highest | high | lowest |

Renders on all three real calibration frames are in the branch's working
tree under the scratchpad naming `reticle_frame_{01,02,03}_{variant}.jpg`
and were sent to the user directly (see Section 8); they are not committed
(source-photo-derived pixel content, per this project's standing rule of
never committing third-party photo content).

### A. Classic radial reticle
Short radial tick strokes sit directly over each minute-track division (a
slightly heavier stroke every 5th/hour position), plus the 12 gapped axes
and centre target. This is closest to a traditional reticle: every minute
position gets a mark, which reads as busy on the minute track itself but
requires no marker segmentation to render anything -- it always shows the
full structural reference.

### B. Split-line / Vernier-style reticle
Instead of a mark sitting on the tick, each minute position gets a small
open **gate**: two short tangential brackets, one just inside and one just
outside the channel wall. A correctly-aligned real tick should visually
sit centred in the gap between the two brackets; a scale or centring error
shows as the real tick drifting toward one bracket. This is the most
"precision-instrument" looking of the three, and directly visualises
scale error (not just presence/absence), but is the busiest under
inspection at full zoom -- 240 short strokes total across the ring.

### C. Minimal minute-channel + marker-envelope design
Drops the per-minute marks to hour-only (12 radial ticks), keeps the
channel, 12 axes and centre target, and adds the round-marker envelope
rings (centre/outer/inner, gold) and the triangle's outer-point guide when
enough round markers segment. This is the cleanest of the three by line
count, and is the only variant that visualises **marker-level** placement
directly (not just minute-track/angular alignment) -- at the cost of that
one extra guide silently disappearing on very poor-coverage photos.

## 4. What each element can and cannot safely show

| Element | Source | Can show | Cannot show |
|---|---|---|---|
| Centre target | ellipse centre (acquisition) | gross mis-centring of the whole automatic fit | anything about markers or minute track individually |
| Minute-track channel | affine ellipse + `MINUTE_TRACK_R` | scale error and centring error of the minute track ring as a whole (real track drifting outside the channel) | which specific minute is wrong; marker geometry |
| 60 tick/gate references | same affine basis, per-minute angle | angular position error of a specific minute tick, at minute resolution | radial (depth) error at the marker band; anything about markers |
| 12 radial axes | same affine basis, per-hour angle, gapped around markers | sideways/rotational marker displacement (marker not straddling its axis), 12/6 or 3/9 inconsistency (compare both ends of an axis pair against the same coherent basis) | nothing radial by itself -- axes are angle-only references |
| Round-marker envelope (centre/outer/inner) | conic fit to *this photo's* observed round-marker points | a round marker sitting noticeably off the ring fit to its own peers (radial displacement, size mismatch) | anything when fewer than 5 round markers segment (guide omitted, never fabricated); triangle/baton shape correctness (different shapes, not forced onto this ring -- see Section 2) |
| Triangle (12) outer-point guide | round-envelope's own mean radius, scaled by the validated triangle/round outer-radius ratio | a gross triangle displacement relative to where the round-marker ring itself says it should be | precise triangle orientation/shape; anything if the round envelope itself could not be fit |

## 5. Clutter / readability comparison

Subjective but consistent across all three real test frames (tilts 5.5°,
11.9°, 17.1°) and the synthetic-defect cases (Section 6):

- **A** reads clearly at a glance but the 60-tick ring is visually busy
  right on top of the real printed minute track -- acceptable, not ideal.
- **B** is the busiest (two strokes per minute = 120 short strokes on the
  ring) and needs the most zoom to read individually, but its gate
  structure is the most informative single element for scale/centring
  error specifically, because it shows *which side* the real tick has
  drifted to, not just that something is off.
- **C** is the cleanest by a clear margin (12 hour-only ticks vs. 60), and
  is the only variant that adds marker-level (not just minute-track-level)
  diagnostic content. It degrades gracefully: on frame_03 (3/8 round
  markers, below the 5-marker minimum), it silently renders without the
  gold envelope and still looks intentional, not broken.

## 6. Synthetic defect illustration cases

No real labelled-defective photos are available, so synthetic cases were
built by editing real calibration photos (never fabricated from nothing),
using `cv2.inpaint` to remove a marker from its true position and
`cv2.warpAffine`/`getRotationMatrix2D` plus a Gaussian-blurred circular
mask to paste a modified copy back, honestly labelled as synthetic
throughout.

- **Horizontally displaced marker (large):** a large synthetic
  displacement of a round marker is visually unambiguous, but pushes
  automatic round-marker segmentation from 6/8 down to 3/8 on that frame
  (below the 5-marker minimum for the envelope conic). This is a genuine,
  useful finding in its own right, reported honestly rather than hidden:
  **the reticle's structural elements (channel + 12 axes) stay useful even
  when marker-level segmentation itself fails** on a badly-displaced
  marker, because the axis is drawn from the minute-track basis, not from
  the marker being examined.
- **Horizontally displaced marker (moderate):** a smaller displacement
  keeps segmentation intact (6/8, envelope conic still fits) and is
  visibly closer to / crossing the gold envelope ring versus an
  undisplaced neighbour at equal zoom -- this is the clean demonstration
  of the envelope guide's actual diagnostic value.
- **Visibly tilted/rotated marker:** a baton marker rotated ~22° in place
  is visibly no longer parallel to its own radial axis under variant A's
  axis reference.
- **12/6 and 3/9 axis-consistency, dial/bezel alignment:** not built as
  separate synthetic warps. The mechanism that would expose these is the
  same one already demonstrated by the displacement case -- all 12 axes
  share one coherent centre/roll basis, so any true 12-vs-6 or 3-vs-9
  inconsistency in a real photographed watch shows the same way a single
  displaced marker does (one end sits on its axis, the other doesn't).
  Building dedicated synthetic warps for these would duplicate the same
  proof rather than add new evidence, so this is reported as a reasoned
  limitation of the test-case coverage, not silently skipped.

## 7. Manual correction prototype (Part 9)

Automatic placement (minute-track acquisition -> ellipse + roll) is the
default and primary path. `reticle_manual.py` prototypes the composable
manual adjustments a user could apply on top of it if needed: **drag**
(recentre), **scale** (pinch), **rotate**. All three are expressed as one
small `AdjustParams` struct applied directly to the same `RotatedRect` +
roll pair `reticle.build_geometry` already consumes -- no other code
needs to change to support manual correction, and gestures compose
(`reticle_manual.compose`).

Deliberately **not** prototyped: independent per-corner perspective
handles. The automatic basis is already one coherent affine model for the
whole reticle; four free corners would let a user bend it piecewise
without geometric justification -- exactly the class of mistake this
research line has repeatedly had to walk back. If real-world use later
shows genuine skew/keystone error that translate+scale+rotate cannot
correct, that would be evidence for a real projective control. It is not
assumed here just because document-scanner apps commonly ship one.

Demonstrated with a synthetic "imperfect automatic fit" (offset centre,
6% wrong scale, 4° wrong roll) on frame_02, then walked back to the exact
original geometry via the inverse `AdjustParams` gesture through the
identical code path -- rendered before/after, sent to the user directly
(Section 8).

## 8. Recommendation

**Recommended design: C (minimal minute-channel + marker-envelope).**

Rationale: it is the cleanest by line count (matters most for "does not
look like a colourful debug screen"), it is the only variant that adds
marker-level diagnostic content beyond minute-track/angular alignment, and
it degrades gracefully -- on low round-marker-coverage photos it silently
falls back to something structurally equivalent to variant A rather than
failing or fabricating. Variant B's Vernier gates are a genuinely more
precise way to show minute-track scale error specifically and are worth
keeping in reserve (e.g. as a "precision mode" toggle) if scale error
turns out in practice to be a common real defect, but they add clutter
this prototype does not think is justified as the default.

One clean full-resolution mockup of the recommended design (frame_02,
5.5° tilt, best round-marker coverage of the three real test frames, no
synthetic modification) was sent to the user directly as
`RECOMMENDED_final_mockup_C_minimal.jpg`.

## 9. Deliverables

- `tools/watch_align_py/reticle.py` -- geometry core (both sources, kept
  explicitly separate; committed).
- `tools/watch_align_py/reticle_render.py` -- three variant renderers
  (committed).
- `tools/watch_align_py/reticle_manual.py` -- manual-correction prototype
  (committed).
- Rendered examples on 3 real calibration frames x 3 variants, synthetic
  defect-illustration cases, and the manual-correction before/after --
  sent to the user directly via file transfer (not committed; derived
  from third-party source photos, per this project's standing rule of
  never committing that content to git).

## 10. Explicitly out of scope / not done here

- No Android changes.
- No change to production QC, master geometry, or any threshold.
- No automated defect detection, scoring, or classification of any kind.
- No PR to `main`, nothing merged.
- Validation split was not inspected.
