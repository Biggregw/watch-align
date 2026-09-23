# Addendum: research-branch refresh after phase 2 completion

Per the standing protocol ("refresh the research branch at checkpoints;
list and read all current `docs/research/proportional-geometry-*.md`
files; record conflicts/new follow-up ideas; never merge the research
branch into this implementation branch"), the branch was re-fetched
after phase 2's calibration-expansion work was committed.

**Nothing in Stage 1 or Phase 2** (the frozen profile, the calibration
CSVs, the expanded feature-distribution statistics, or any rendered
output already delivered) is changed by this addendum.

## What changed on the research branch

`origin/research/proportional-geometry-knowledge-base` moved from
`d19988a` to `c86bbe0` -- 6 new commits, all authored by the project
owner directly (not by this session), the last one ("interpret expanded
proportional calibration evidence") timestamped shortly after phase 2's
completion. The branch now carries 9 `proportional-geometry-*.md` files
(up from 3): the 3 already-read files, plus:

- `proportional-geometry-calibration-statistics-protocol.md` -- a
  detailed statistical-methodology guardrail document (physical-watch
  as the unit, equal-weight watch summaries, within/between-watch
  noise decomposition, leave-one-watch-out stability, small-sample
  language bands, duplicate protection, coverage-by-tilt-band
  reporting, provenance-diversity tracking).
- `proportional-geometry-incidence-and-common-mode-guardrails.md` --
  expands the earlier invariants note into a full incidence-first rule
  family (marker-on-hour-line, axis-through-dial-centre incidence,
  opposite-marker collinearity) plus explicit tangential/radial
  common-mode-vs-local-residual decomposition, generalised to 6/9
  batons.
- `proportional-geometry-practical-viewpoint-policy.md` -- defines
  three tilt-based operating modes (<=10deg primary, 10-15deg advisory,
  >15deg stress-only) and a product-weighted feature-ranking order that
  puts ordinary-view performance and coverage ahead of extreme-tilt
  invariance.
- `proportional-geometry-real-defect-positive-set-plan.md` -- a
  future holdout-testing plan using already-catalogued validation-split
  replica QC threads with independently human-described marker
  concerns (explicitly: plan-only, do not inspect or tune against these
  while calibration work is active).
- `proportional-geometry-reddit-qc-evidence-2026-09-23.md` -- a
  qualitative refresh of recurring RepTimeQC GMT concerns, producing a
  P0-P4 research-priority ordering (marker orientation first, then 12
  radial placement, then round-marker peer anomalies, then date/cyclops,
  then bezel).
- **`proportional-geometry-phase2-interpretation-2026-09-23.md`** -- a
  direct interpretation of this experiment's own phase 2 numbers.

## The phase2-interpretation file: verified against this branch's actual numbers

The interpretation file's stated facts (10 watches, 37 clean rows, 28
simple-feature images, 20 projective-feature images) match this
branch's committed `gmt_calibration_expanded_*` outputs exactly. Its
feature-specific readings are consistent with what phase 2 itself
reported:

- `apex_r_simple` singled out as the strongest low-tilt radial
  candidate, on the same evidence (median stable, MAD tightened
  sharply, simple beats projective for this feature specifically) that
  phase 2's own write-up already recorded.
- `centroid_tangential_offset_canonical`'s Stage-1 "possible left/right
  bias" read as more likely small-sample noise, matching phase 2
  section 4's own conclusion.
- `base_r_simple` and `base_width_over_height` flagged as still noisy /
  not sharply clustered, matching phase 2's numbers (their MAD did not
  tighten with more watches).
- `symmetry_axis_angular_deviation_deg` flagged as still the noisiest
  retained feature, matching Stage 1's original caveat and phase 2's
  unchanged MAD for it.

No factual disagreement was found between this branch's interpretation
and this experiment's own committed results. This is corroboration, not
new evidence -- the interpretation reasons from the same numbers phase 2
already published.

## New guidance beyond phase 2's own write-up (recorded, not applied)

- **A hybrid, per-feature normalisation policy** (simple for apex
  radial position; projective for centre/base radial position where
  peer geometry is well-conditioned; canonical tangential offset for
  placement; incidence residual rather than raw angle for orientation;
  shape kept as secondary evidence only). Phase 2 reported the
  simple-vs-projective split per feature but did not commit to a
  combined per-feature production policy -- this is a genuinely new,
  more prescriptive recommendation.
- **A <=10deg primary statistical cut**, recommended to be applied
  *before* any future feature-selection/profile freeze, with all-view
  statistics kept only as stress/robustness evidence. Phase 2's own
  tilt-dependence analysis (section 6) reported <=10/>10deg subgroup
  medians but did not restrict the primary population to <=10deg.
- **Leave-one-watch-out stability** and **hierarchical (watch-level)
  bootstrap** are recommended as required checks before any band is
  treated as more than exploratory. Phase 2 did not compute either.
- **Incidence-first residuals** (marker-on-predicted-hour-line,
  axis-through-dial-centre) and the **tangential/radial common-mode vs
  local-residual decomposition**, now generalised to 6/9 batons, remain
  unimplemented -- this was already flagged as a gap in the first
  addendum (2026-09-23, pre-phase-2) and still stands.
- **Coverage-by-tilt-band and rejected-vs-accepted comparison**
  (calibration-statistics-protocol.md section 9) -- phase 2's audit CSV
  already contains everything needed for this (tilt, blur, failure
  category, per-image), but the comparison itself was not run.

## Explicitly not a conflict

Nothing here contradicts a frozen decision. The hybrid-normalisation
policy sharpens, rather than overturns, phase 2's own "do not force
projective normalisation to win" finding. The <=10deg primary-cut
recommendation is a *future* profile-freeze input, not a retroactive
edit to the already-published expanded statistics (which correctly
report both subgroups without picking one as primary). The
calibration-statistics-protocol's `8-12 watches: useful early research
distribution... still not a validated manufacturing tolerance` framing
matches phase 2's own verdict (section 8) almost verbatim.

## Not acted on

Per the standing rule that a checkpoint read records follow-up ideas
without implementing a new phase unless asked: none of the "Recommended
next experiment" steps in `proportional-geometry-phase2-interpretation
-2026-09-23.md` (re-cut statistics at <=10deg, run feature selection,
retain 3-6 features, freeze a new profile, re-apply to the user's photo,
render the explanation overlay, then validation) have been started.
Nothing was written to the research branch itself by this session --
its refresh here was authored by the project owner directly.

This addendum is committed to `experiment/gmt-proportional-geometry-v1`.
