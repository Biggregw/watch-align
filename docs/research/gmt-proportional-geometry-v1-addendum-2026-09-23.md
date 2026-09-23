# Addendum: research-branch expansion after Stage 1 was frozen

This records a protocol update and a subsequent knowledge-base read, per
the user's explicit instruction to preserve experimental chronology
rather than retroactively edit completed work. **Nothing in Stage 1**
(`gmt-proportional-geometry-v1-2026-09-23.md`, `gmt_12_triangle_profile.
json`, the calibration CSVs, or any rendered output already delivered)
has been changed as a result of this addendum.

## Protocol update

The research branch (`research/proportional-geometry-knowledge-base`)
now carries three files, and the refresh protocol now requires reading
all of them, not only the originally-named authoritative file:

- `docs/research/proportional-geometry-knowledge-base.md` (unchanged
  since the last Stage 1 checkpoint read, commit `d19988a`)
- `docs/research/proportional-geometry-defect-rule-roadmap.md`
  (unchanged since Stage 1, already noted as supplemental context there)
- `docs/research/proportional-geometry-invariants-and-defect-decomposition.md`
  (**new**, read in full for the first time here)

## What the new file adds, and how it relates to the frozen Stage 1 profile

`proportional-geometry-invariants-and-defect-decomposition.md` develops
several generic mathematical tools that were **not available while Stage
1's feature catalogue, ranking, or profile were being finalised**, and
therefore could not have been incorporated then. None of them invalidate
what was measured -- they are additional analysis the same raw
observations could support.

### Genuinely new, not yet implemented

- **Cross-ratio radial coordinate** (its section 1): an alternative way
  to obtain the same projective radial coordinate Stage 1 computed via
  `t(r) = a*r/(c*r+1)`, using the classical 1D projective cross-ratio
  against the same three round-marker corridor correspondences plus the
  dial centre. The note explicitly frames these as "mathematically
  related" and recommends computing both and checking agreement as a
  conditioning check. Stage 1 implemented only the direct fit.
- **Apex/base-centre signed tangential endpoint pattern** (section 3):
  Stage 1 computed a single centroid tangential offset, not separate
  apex and base tangential offsets, so the translation-vs-rotation sign
  pattern this section describes cannot be read off the existing CSV
  without a new pass over the (already-fetched) calibration images.
- **Radial common-mode / differential residual decomposition**
  (section 4): this operates on *residuals from an expected value*
  (`R_apex - r_apex` etc.), which requires a frozen profile to exist
  first -- it could not have been computed before Stage 1's profile
  freeze, but it *can* now be computed from Stage 1's already-frozen
  medians and already-measured observed values without re-measuring or
  re-selecting anything. Not yet done.
- **Tangential residual slope model** (section 5), **generic per-shape
  residual vector** (section 6), **displacement-vector overlay cue**
  (section 11): not implemented.

### A genuine tension with an already-frozen Stage 1 decision, recorded rather than silently resolved

Section 12 of the new file proposes a priority order for "the first GMT
12 experiment" that ranks **radial common-mode translation** (#4) and
**radial differential/span residual** (#5) as high-priority features,
essentially above tangential offset and orientation.

Stage 1 explicitly **rejected** `apex_to_base_span_simple` as an
independently-selected feature, on the grounds that it is a deterministic
transform of two already-tracked features (`base_r_simple - apex_r_simple`)
and therefore not independent statistical information (see the rejected-
features table in the Stage 1 write-up). That reasoning is still correct
as far as it goes -- span *is* deterministic given apex and base radius.
The new guidance's point is different: it is not proposing span as a
fourth independent radius, but as an **interpretive lens** (differential
residual vs. common-mode residual) for reading the *already-retained*
apex/base features, plus a **new, not-yet-computed common-mode feature**
(the average of the apex/centre/base residuals) that Stage 1 never
constructed as its own quantity at all.

**Resolution recorded, not applied retroactively:** Stage 1's rejection
of span as an independent *selected* feature stands as written -- it was
a reasonable, correctly-reasoned decision given what was known at the
time, and this addendum does not overturn it. But the newer guidance
identifies a real gap: Stage 1's profile has no common-mode radial
translation feature, and does not decompose the apex/base residuals into
common-mode vs. differential components for interpretation. This is
recorded as a specific, concrete follow-up (see below), not folded into
the existing frozen profile.

### Not in tension, already effectively satisfied

- Section 2 (compare marker orientation against the locally predicted
  hour line, never a raw global vertical) -- already the explicit design
  of `axis_coords.py`; Stage 1's angular features are computed in the
  canonical axis-aligned frame throughout.
- Section 8 (leave-one-out peer conics so a displaced marker cannot pull
  its own expected envelope toward itself) -- Stage 1's round-marker
  corridor is stricter than leave-one-out for the marker actually under
  test: the 12 triangle is never included in that fit at all under any
  circumstance, by construction (`gmt_proportional_features.py`,
  `triangle_measurement.py` never call `mc.segment_marker` for hour 12
  when building the corridor). This principle would matter more once
  this framework is extended to judge a *round* marker against its own
  peers, which Stage 1 did not attempt.
- Section 9 (do not turn confidence/residual evidence into a production
  z-score without real calibration evidence) -- Stage 1 already avoided
  this; diagnostics are reported, not scored.
- Section 7 (12/6 pairwise, opposite-marker relationships) and section 10
  (multi-photo same-watch sign-consistency) -- both explicitly outside
  the narrow 12-only, single-photo-per-watch scope Stage 1 was given;
  not a gap so much as unexercised scope.

## Recommended follow-up experiment (not started)

A well-defined **Stage 2 candidate**, should it be requested:

1. Recompute apex and base tangential offsets separately (not just
   centroid) from the already-fetched calibration images -- no new fetch
   needed, the 7 images are already local to the CI environment/branch
   history.
2. Compute the cross-ratio radial coordinate alongside the existing
   projective-fit coordinate and report their agreement as a new
   conditioning diagnostic.
3. Compute common-mode and differential residual decomposition from the
   already-frozen profile medians against each already-measured
   observation (official reference and user QC included) -- this is pure
   post-hoc analysis of numbers Stage 1 already produced, so it could be
   done without touching the frozen profile or re-running any image
   measurement.
4. Re-run feature selection with the expanded candidate set once (1)-(3)
   exist, still bound by the same n=2-physical-watch sample-size caveat
   that applied to Stage 1 until more calibration `gen_candidate` watches
   are added to the corpus.

This addendum is committed to `experiment/gmt-proportional-geometry-v1`.
The research branch itself was not merged or cherry-picked, per the
standing rule.
