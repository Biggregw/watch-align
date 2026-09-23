# Stage 3: hybrid-normalised, incidence-first, tilt-restricted GMT 12 profile

experiment/gmt-proportional-geometry-v1

Scope: narrowly the GMT 12 triangle only, exactly as instructed. No 6/9,
cyclops, bezel, or RepTimeQC positive-set work started. No validation
watch inspected. No hard defect threshold created. No PR, no merge, no
Android/production change.

## 0. Research-branch checkpoint (before freezing)

Re-fetched `origin/research/proportional-geometry-knowledge-base`
immediately before writing the frozen profile below: still at commit
`c86bbe0`, same 9 `docs/research/proportional-geometry-*.md` files,
unchanged since the phase-2 addendum. No new guidance to reconcile.

## 1. What changed from Stage 1 / Phase 2

- **Population**: the phase-2 expanded calibration set (10 watches
  overall), restricted here to the **9 watches with at least one image
  at <=10 degrees apparent tilt** (2.06-8.91deg observed range). This is
  the PRIMARY population for every frozen band below. >10deg images
  were summarised as a stress-test comparison only and never
  contributed to a median, MAD, range, or stability check.
- **Hybrid per-feature normalisation**: apex radius stays simple
  (affine-only); centre and base radius switch to the projective
  (perspective-corrected) form, which the <=10deg data now clearly
  supports for those two positions specifically (see section 3).
- **Incidence-first orientation**: a new feature,
  `axis_incidence_canonical`, tests whether the observed apex-to-base
  symmetry line, extended, passes through the independently established
  dial centre -- primarily sensitive to sideways translation of the
  whole marker, complementing the existing tangential-offset feature.
- **Two stability checks required before freezing anything**:
  leave-one-physical-watch-out (LOWO) and a hierarchical (watch-level)
  bootstrap, both physical-watch-independence-respecting.

## 2. Frozen population

9 independent physical watches, 20 images, all at <=10 degrees apparent
tilt (2.06-8.91deg):

`gen_1stdibs_j10266432, gen_1stdibs_j14542122, gen_1stdibs_j15020852, gen_1stdibs_j22086492, gen_1stdibs_j22349682, gen_1stdibs_j24782832, gen_1stdibs_j26718002, gen_wex_3KSuGhC, gen_wex_e99gXKb`

(`gen_1stdibs_j14512702` and `gen_1stdibs_j25758762` are part of phase
2's expanded set but contribute no <=10deg image, so they don't appear
in this primary population.) Centre/base-radial (projective) features
draw from 8 of these 9 -- one watch's round-marker corridor fit wasn't
sufficiently well-conditioned at <=10deg.

Provenance limitation carried forward unchanged from phase 2: 7 of
these 9 watches come from a single marketplace channel (1stDibs).

## 3. Stability results and the hybrid-normalisation choice, with real numbers

Full candidate set tested (both simple and projective forms of centre/
base, so "where supported" was a data decision, not an assumption):

| feature | n watches | median | MAD | LOWO max shift | bootstrap IQR | verdict |
|---|---|---|---|---|---|---|
| apex_r_simple | 9 | 0.5497 | 0.0016 | 0.0008 | 0.0026 | **kept (simple)** |
| centre_r_simple | 9 | 0.7028 | 0.0290 | 0.0106 | 0.0383 | secondary |
| centre_r_projective | 8 | 0.7382 | 0.0056 | 0.0005 | 0.0050 | **kept (projective, ~5x tighter than simple)** |
| base_r_simple | 9 | 0.7922 | 0.0270 | 0.0135 | 0.0506 | secondary |
| base_r_projective | 8 | 0.8066 | 0.0036 | 0.0022 | 0.0053 | **kept (projective, ~7.5x tighter than simple)** |
| axis_incidence_canonical | 9 | 0.0038 | 0.0064 | 0.0016 | 0.0130 | **kept (new)** |
| centroid_tangential_offset_canonical | 9 | 0.0012 | 0.0041 | 0.0018 | 0.0042 | **kept** |
| apex_tangential_offset_canonical | 9 | -0.0035 | 0.0068 | 0.0034 | 0.0083 | secondary (redundant w/ centroid) |
| base_tangential_offset_canonical | 9 | 0.0004 | 0.0054 | 0.0021 | 0.0041 | secondary (redundant w/ centroid) |
| symmetry_axis_angular_deviation_deg | 9 | 0.28deg | 0.63deg | 0.24deg | 1.04deg | secondary (superseded by incidence test) |
| base_width_over_height | 9 | 1.027 | 0.136 | 0.068 | 0.206 | secondary (shape, by design) |

**Stability bar used** (stated explicitly, not a manufacturing
tolerance): LOWO max median shift <= the feature's own population MAD.
Every candidate above passed this bar at n=8-9 -- nothing was rejected
for instability. Feature selection to the final five was therefore made
on parsimony/redundancy grounds (matching Stage 1's own precedent: keep
the smallest set that isn't duplicating information), not by discarding
an unstable candidate. Full per-candidate numbers, including every
non-selected candidate, are in `gmt_stage3_stability_analysis.json`.

`apex_r_simple`'s per-feature reasoning directly confirms phase 2's own
finding under the stricter <=10deg cut: simple stays tighter than
projective for apex specifically. `centre_r_projective` and
`base_r_projective` are unambiguous here -- 5x and 7.5x tighter than
their simple counterparts, and far more LOWO-stable -- confirming and
sharpening phase 2's centre/base result.

## 4. Frozen profile

`gmt_12_triangle_profile_stage3.json` -- five core features:

| name | role | normalisation | median | useful_central_range |
|---|---|---|---|---|
| apex_r_simple | apex_radial | simple | 0.5497 | [0.5465, 0.5529] |
| centre_r_projective | centre_radial | projective | 0.7382 | [0.7270, 0.7495] |
| base_r_projective | base_radial | projective | 0.8066 | [0.7993, 0.8138] |
| axis_incidence_canonical | orientation | canonical | 0.0038 | [-0.0091, 0.0167] |
| centroid_tangential_offset_canonical | orientation | canonical | 0.0012 | [-0.0071, 0.0095] |

`useful_central_range` = median +/- 2*MAD, the same convention Stage 1
used. Six further features are reported as secondary evidence only
(not part of the verdict set): `centre_r_simple`, `base_r_simple`,
`apex_tangential_offset_canonical`, `base_tangential_offset_canonical`,
`symmetry_axis_angular_deviation_deg`, `base_width_over_height`.

No feature was rejected outright (`rejected_features: []`) -- everything
tested passed the stated stability bar; the secondary/core split is
about profile parsimony, not measurement failure. Every field's
reasoning and known limitations are written directly into the profile
JSON (`why_simple_not_projective` / `why_projective_not_simple` per
feature).

**Honest caveat on `axis_incidence_canonical`**: it is the least mature
of the five -- first calibration exposure this phase, its bootstrap IQR
is ~2x its own MAD (the widest ratio of any retained feature), and its
p90 (0.084) is far from its median (0.004), driven by a small number of
watches at n=9. It passed the stated LOWO bar and is genuinely useful
(it is the only incidence-first, translation-sensitive orientation
signal in the profile), but its band should be read as the least settled
of the five.

## 5. Applied unchanged to official reference and user's QC photo

Same frozen profile, same code, no per-image tuning, exactly as Stage 1's
protocol.

### Official Rolex catalogue reference (tilt=9.53deg)

| feature | observed | median | range | signed diff | status |
|---|---|---|---|---|---|
| apex_r_simple | 0.5647 | 0.5497 | [0.5465,0.5529] | +0.0150 | outside the central calibration distribution |
| centre_r_projective | 0.7395 | 0.7382 | [0.7270,0.7495] | +0.0013 | consistent with the central calibration distribution |
| base_r_projective | 0.8125 | 0.8066 | [0.7993,0.8138] | +0.0059 | toward an edge of the central calibration distribution |
| axis_incidence_canonical | 0.0331 | 0.0038 | [-0.0091,0.0167] | +0.0293 | outside the central calibration distribution |
| centroid_tangential_offset_canonical | -0.0200 | 0.0012 | [-0.0071,0.0095] | -0.0212 | outside the central calibration distribution |

**Important, honestly-reported anomaly**: the official catalogue image
falls outside the central band on 3 of 5 features, most strikingly
`apex_r_simple` (this exact observed value, 0.5647, is unchanged from
Stage 1 -- what changed is that Stage 3's calibration band is roughly
10x tighter, MAD 0.0016 vs Stage 1's 0.0174, so the same observation now
falls well outside it). This is recorded as an open question, not
explained away: it may mean the tightened <=10deg, single-marketplace-
dominated band is narrower than real manufacturing/photographic variation
supports, or that Rolex's own studio/catalogue rendering process
(controlled lighting, possible retouching, CGI-adjacent production
pipeline) differs systematically from ordinary marketplace photography
on exactly the axes these features measure. Both are plausible; this
result does not by itself favour one explanation, and no attempt is
made here to adjust the band to fit the reference image (that would be
tuning against a known-genuine control, which this project's rules
prohibit).

### User's dealer QC photo (tilt=3.94deg)

| feature | observed | median | range | signed diff | status |
|---|---|---|---|---|---|
| apex_r_simple | 0.5812 | 0.5497 | [0.5465,0.5529] | +0.0315 | outside the central calibration distribution |
| centre_r_projective | 0.7509 | 0.7382 | [0.7270,0.7495] | +0.0127 | outside the central calibration distribution |
| base_r_projective | 0.8205 | 0.8066 | [0.7993,0.8138] | +0.0139 | outside the central calibration distribution |
| axis_incidence_canonical | -0.0053 | 0.0038 | [-0.0091,0.0167] | -0.0091 | toward an edge of the central calibration distribution |
| centroid_tangential_offset_canonical | -0.0023 | 0.0012 | [-0.0071,0.0095] | -0.0035 | consistent with the central calibration distribution |

**Descriptive pattern, not a conclusion**: all three radial features
(apex, centre, base) sit above their medians in the same direction
(outward), a common-mode-consistent pattern in the sense the reconciled
research notes describe, while both orientation features are within or
near the central band. This is reported as an observed geometric pattern
only. It is explicitly NOT an authenticity, GL/RL, or defect
determination -- see section 6.

## 6. What this is not

- Not an authenticity, genuine/replica, GL/RL, or defect determination.
  "Outside the central calibration distribution" describes only where an
  observation sits relative to a small (n=8-9), single-marketplace-
  dominated, provisional research population -- it is not evidence of a
  manufacturing defect, and it is explicitly not a stronger claim about
  the user's watch than about the official catalogue reference, which
  also falls outside the same band on 3 of 5 features.
- Not a hard threshold. No pass/fail boundary exists anywhere in this
  profile or this write-up.
- Not validation. No validation-split watch (genuine or replica) was
  read, measured, or used to derive any number here.
- Not 6/9/cyclops/bezel work, and not the RepTimeQC real-defect-positive
  holdout -- both explicitly out of scope for this phase per the task.

## 7. Deliverables

- `tools/watch_align_py/gmt_12_triangle_profile_stage3.json` (frozen
  profile; does not modify or replace Stage 1's profile)
- `tools/watch_align_py/gmt_stage3_stability_analysis.py` (LOWO +
  hierarchical bootstrap, primary-tilt restriction)
- `tools/watch_align_py/gmt_proportional_render_stage3.py` /
  `render_gmt_stage3.py` (hybrid-normalisation-aware overlay, driven by
  each feature's role/normalisation rather than hardcoded names)
- `tools/watch_align_py/projective_radial.py`:
  `apply_projective_1d` (new, additive helper -- the forward direction
  of the existing fit, needed only to render a projective feature's
  expected gate on one specific photo)
- `tools/watch_align_py/gmt_proportional_features.py`: three new
  additive features (`apex_tangential_offset_canonical`,
  `base_tangential_offset_canonical`, `axis_incidence_canonical`)
- `datasets/126710BLNR/results/gmt_stage3_primary_clean.csv`,
  `gmt_stage3_stability_analysis.json` (numeric only)
- `datasets/126710BLNR/results/gmt_stage3_official_reference_measurements.json`,
  `gmt_stage3_user_qc_measurements.json` (numeric only)
- Visual outputs (official reference and user QC photo, user_mode +
  diagnostic_mode + diagnostic_panel + 12-crop) sent directly to the
  user -- not committed to git, per this project's standing rule against
  committing third-party or user source-photo pixels.

Stopping here, as instructed, for review before any further stage.
