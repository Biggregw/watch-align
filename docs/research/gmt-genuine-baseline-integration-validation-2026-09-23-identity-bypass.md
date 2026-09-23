# GMT identity-veto diagnosis + first blind replica-control measurement — 2026-09-23

**Branch:** `feature/gmt-genuine-baseline-integration`
**Status:** first real blind measurement obtained (1 replica control, `rep-local-001`). This is evidence a small set of metrics may be promising -- **it is not proof of general discrimination** and nothing is promoted to Final QC on this basis (see F/G/H).

This report is additive to, and does not revise, `docs/research/gmt-genuine-baseline-integration-validation-2026-09-23.md` (which documented that all 9 Reddit-sourced controls were blocked). This phase adds one repository-local, human-reviewed fixture and a research-only mechanism to measure it, then reports what it actually showed.

## 1. What the identity veto protects against, and why it fired here

`identity_gate.py` (a port of `MinuteTrackIdentityGate.java`) is an *independent* check that runs only when the accepted minute-track pose looks geometrically suspicious. It never searches for, fits, moves, resizes, or rotates a pose -- it only samples the already-resolved pose-projected region and asks: does this actually look like a real dial (dark, textured interior; a plausible bright blob at 12, 6, and 9)? It exists specifically to catch the failure mode where the minute-track math converges confidently onto the *wrong* target (a bezel, a reflection, some other circular feature) despite passing its own internal numeric gates.

It only runs when `identity_verification_required`: `automatic_accepted` (tilt/top-phase/minute-track-holdout all already passed) **and** the accepted pose's centre disagrees with an older, cruder "legacy seed" circle detector by more than 22% of dial radius (`SUSPICION_CENTER_DISPLACEMENT_FRACTION`). If required, `PASS` is needed to avoid a veto; `AMBIGUOUS` and `FAIL` both veto.

For `rep-local-001` (`android/app/src/androidTest/assets/debug/community-vsf-batgirl-crooked12-01.jpg`), decoded via this project's research-tooling path (PIL EXIF-transpose + LANCZOS resize to 1800px max dimension -- the same decode path used to build the genuine baseline):

| field | value |
|---|---:|
| automatic_accepted | True |
| centre displacement vs legacy seed | **80.4%** of dial radius (suspicion threshold 22%) |
| identity_required | True |
| identity verdict | **FAIL** |
| `dial_interior_median` | 25.0 (dark threshold 135 -- dark: **pass**) |
| `interior_edge_fraction` | 0.351 (min 0.02 -- textured: **pass**) |
| `marker_good(12/6/9)` | **0 of 3** |

Direct per-marker measurement (added this session, not part of the identity gate's own output) shows exactly why `marker_good` returns 0/3: at all three canonical positions, contrast (109-131) and bright_fraction (0.04-0.22) clear their thresholds comfortably, but each marker's peak brightness (p90 = 141-155) falls just short of the gate's fixed absolute threshold of 165/255:

| hour | bg | p90 (need >=165) | contrast (need >=35) | bright_fraction (need >=0.05) |
|---|---:|---:|---:|---:|
| 12 | 31.5 | 140.9 | 109.4 | 0.062 |
| 6 | 24.0 | 148.9 | 124.9 | 0.043 |
| 9 | 24.3 | 154.8 | 130.6 | 0.216 |

**This is not a new mystery.** `tools/watch_align_py/tests/fixtures_manifest.py` already documented, on 2026-09-21, that this exact photo's dim exposure defeated `marker_qc.py`'s own (then-fixed, 150) brightness threshold for isolating markers, fixed by switching to a per-marker Otsu threshold. `identity_gate.marker_good()` uses a *different*, still-fixed threshold (bright_threshold = max(150, bg+45), and separately p90 >= 165) that was never given the same treatment. The same underlying cause -- this specific community photo's exposure -- explains both.

**Why the centre displacement is 80% in the first place:** decoding the identical file via `image_io.decode_capped` (this project's *production*-fidelity decode: `cv2.imread` + bilinear resize to 1600px, matching `MainActivity.readBitmap`/the Android instrumented-test decode helper) gives a completely different picture:

| | research decode (PIL/LANCZOS/1800px) | production decode (cv2/bilinear/1600px) |
|---|---:|---:|
| centre displacement vs legacy seed | 80.4% | **0.6%** |
| identity_required | True | **False** |
| vetoed | True | **False** |
| accepted | False | **True** |
| confidence | 0.35 | **0.73** |
| tilt_deg | 3.37 | 4.93 |

The minute-track holdout validation itself is not in question either way -- 12/12 held-out ticks, median residual 0.60px against a 2.40px limit, p90 0.83px against a 5.13px limit, 100% inlier fraction, under the *research* decode path that also triggers the veto. That is about as strong an independent geometric confirmation as this pipeline produces that the accepted pose really is on the real dial; the production-fidelity decode's clean, unvetoed, high-confidence acceptance of the identical photo corroborates it further. The 80%/0.6% swing is best explained as the "legacy seed" Hough-circle detector (used only as a rough starting hint, never for final geometry) being sensitive to the resize/resampling choice on this specific two-tone ("batgirl") bezel -- not evidence that the resolved pose is wrong.

**Conclusion for item 2:** the identity veto fired here because of two compounding, independently-explainable effects of this one photo (dim exposure defeating a stale fixed-brightness sub-check; a resize-sensitive legacy-seed disagreement triggering the check at all) -- not because the resolved pose is actually on the wrong target. No change was made to confirm this beyond reading code and running diagnostics against the unmodified pipeline.

## 2. Why production code was not changed

Two plausible root-cause fixes exist (raise/adapt `identity_gate.marker_good()`'s brightness threshold the same way `marker_qc.py` was fixed on 2026-09-21; or make the research runner decode images the same way production does). Neither was made:

- `identity_gate.py` mirrors real Android production code (`MinuteTrackIdentityGate.java`) and is a safety-relevant gate; a threshold change there needs its own deliberate review against the full fixture/photo population, not a one-photo fix bundled into a research validation task.
- Switching the research runner's decode path to match production would very likely also fix this control's spurious veto, but the *empirical genuine baseline* was built using the current PIL/LANCZOS/1800px path; changing only the replica runner's decode would make every genuine-vs-replica comparison decode-inconsistent, which is a bigger and separately-justified methodological change, not a "smallest safe mechanism."

Per the task's own preferred principle, the actual mechanism implemented is additive and research-only (below), and both findings above are recorded here as explicit, independently-reviewable recommendations, not applied as code changes.

## 3. The research-only bypass mechanism

Implemented entirely in `tools/research/run_replica_control_set.py`; `identity_gate.py`, `pipeline.py`, and every other file under `tools/watch_align_py/` are unmodified.

```python
def research_identity_bypass_eligible(automatic_accepted: bool, vetoed: bool,
                                       provenance_verified: bool) -> bool:
    return automatic_accepted and vetoed and provenance_verified
```

- `automatic_accepted` -- every actual geometric pose gate (tilt/top-phase/minute-track holdout) already passed on the frozen pipeline's own terms. If False, this is always False: a fixture failing real pose validation is never rescued.
- `vetoed` -- together with `automatic_accepted=True`, this fully explains why `accepted` was False (`accepted := automatic_accepted and not vetoed`): the identity gate is the *only* reason.
- `provenance_verified` -- a new, explicit, per-control column in `docs/research/gmt-replica-ground-truth-control-set.csv`, defaulting to unset/false for every existing (Reddit-sourced) row. Set to `true` only for `rep-local-001`, meaning: this exact image's bytes are committed to the repository by the project maintainer, who vouches for its provenance -- as opposed to a runtime-fetched external image this project never controls. It is never inferred from a defect label or control_id.

The function takes no label, no control_id, and no image data -- it is structurally incapable of depending on the expected defect (locked in by `test_bypass_function_signature_is_label_blind`). It is applied generically in `main()`'s per-image loop (any future `provenance_verified` control gets the same treatment), and every use is recorded transparently: `research_identity_bypass_used` is a column in `pose_gate_diagnostics.csv`, `per_image_measurements.csv`, `control_status.csv`, and `per_control_medians.csv` -- a bypassed measurement is never presented as an ordinarily-accepted one.

This is never reachable from Android production code (that code path never invokes this research script), is not a global switch (it is per-control, opt-in, and off by default), and does not touch the identity gate's own PASS/AMBIGUOUS/FAIL logic in any way.

## 4. Tests

`tools/research/tests/test_research_identity_bypass.py` (6 tests, pure function, no image/pipeline dependency):
- ordinary unverified control still vetoed (default `provenance_verified=False`)
- provenance-verified control bypasses an identity-only veto
- provenance-verified control is still rejected if `automatic_accepted=False` (real pose-gate failure), both with `vetoed=False` (the realistic case) and `vetoed=True` (defence in depth)
- provenance-verified control not "bypassed" when it was never vetoed in the first place
- `is_provenance_verified` manifest-value parsing
- the bypass function's signature is locked to exactly the three label-blind booleans

`tools/watch_align_py/tests/test_identity_gate_veto_regression.py` (6 tests, new -- regression lock for the **unmodified** production-mirroring functions):
- identity not required when automatic acceptance already failed (any centre displacement)
- identity not required when centre displacement is below the suspicion threshold
- identity required when accepted and centre displacement exceeds it
- primary never vetoed when identity was not required, whatever the verdict
- primary vetoed unless verdict is PASS, when required
- `final_acceptance` matches `automatic_accepted and not vetoed` across all four cases

**Full suite result:** `cd tools/watch_align_py && pytest tests/` -- **18/18 passed** (12 pre-existing + 6 new regression-lock tests). `pytest tools/research/tests/` -- **6/6 passed**. No existing test was modified; `test_pipeline_fixtures.py`'s own pinned `expect_pose_accepted=True` for this same fixture (via the *production*-fidelity `decode_capped`) is untouched and still passes, consistent with section 1's finding that the veto is a research-decode-path-specific effect.

## 5. rep-local-001: full measured metric vector vs. the frozen genuine profile

Raw outputs (unmodified structure, new `research_identity_bypass_used`/`provenance_verified` columns only): `docs/research/gmt-replica-control-set-results/{pose_gate_diagnostics,control_status,per_image_measurements,per_control_medians}.csv`. Full per-metric join: `docs/research/gmt-replica-control-set-results/discrimination_analysis.csv` (built by the rewritten `tools/research/analyze_replica_control_set.py`, which also carries the exact, code-verified mapping from each genuine-profile metric key to its raw measured column -- see that file's `PROFILE_TO_MEASURED` table and docstring for the citations).

Pose/measurement confidence for this control: `pose_confidence=0.35`, `tilt_deg=3.37`, `n_markers_segmented=2` (h06, h09; h12 is only measured via the Stage-3 triangle path, not the generic round-marker segmenter). **Caveat, not a defect:** `0.35` confidence is depressed by the same centre-displacement effect discussed in section 1 (`_confidence()` includes a term that collapses as centre-err grows); the production-fidelity decode of the identical photo scores `0.73`. Treat this control's numbers as coming from a photo whose *research-decode* pose solve is unusually resize-sensitive, not as an ordinary high-confidence measurement.

Metric name: profile key (raw measured column). Deviation = observed − genuine median. MAD-multiple = deviation / genuine MAD (blank if genuine MAD ≤ 1e-6). Evidence strength mirrors `DefaultEvidenceClassifier.java` exactly (REJECT/degenerate-MAD/DIAGNOSTIC_ONLY all cap at WEAK).

### 12 o'clock (primary predeclared metric family: `h12.axis_residual_deg`, human label "clockwise/crooked")

| metric | observed | genuine median | MAD | p10&ndash;p90 | status | deviation | MAD-mult | strength | direction | agrees with "clockwise"? |
|---|---:|---:|---:|---|---|---:|---:|---|---|---|
| `h12.apex_radial` | 0.5470 | 0.5512 | 0.0026 | 0.5493&ndash;0.5643 | PROMISING | −0.00416 | −1.60 | MODERATE | low | n/a (radial, not a rotation direction) |
| `h12.centre_radial_projective` | *not computed* | 0.7389 | 0.0029 | | PROMISING | | | | | insufficient evidence (round-marker corridor/projective fit unavailable for this photo) |
| `h12.base_radial_projective` | *not computed* | 0.8141 | 0.0026 | | PROMISING | | | | | insufficient evidence (as above) |
| `h12.axis_incidence` | 0.01032 | 0.0006 | 0.0118 | −0.0182&ndash;0.0202 | DIAGNOSTIC_ONLY | +0.00972 | +0.82 | WEAK | clockwise | **agrees**, but WEAK/diagnostic-only |
| `h12.tangential_centroid_offset` | −0.00805 | −0.0015 | 0.0024 | −0.0102&ndash;0.0052 | PROMISING | −0.00655 | **−2.73** | MODERATE | **left** | **disagrees** (predicts counter-clockwise/left, not clockwise) |
| *(no baseline)* `h12.stage3_symmetry_axis_angular_deviation_deg` | +0.0400&deg; | &mdash; | &mdash; | &mdash; | no baseline | &mdash; | &mdash; | insufficient evidence | clockwise | agrees in sign, but the magnitude (0.04&deg;) is negligible next to this dataset's own noise floor (compare 6/9 axis-residual MADs of 0.4&ndash;0.6&deg;), and there is no genuine population to judge it against at all |

`h12.tangential_centroid_offset` is the only 12-marker metric that is both `PROMISING` and reaches MODERATE evidence strength on its own MAD-multiple -- and it points the **opposite** direction from the predeclared "clockwise" label. `h12.axis_incidence` (WEAK/diagnostic) and the no-baseline rotation-angle observation both point clockwise, but neither is strong evidence on its own terms. **The 12-marker rotation-family signal from this one control is mixed, not clean confirmation of the human label.**

### 9 o'clock (supporting predeclared metric: `h09.axis_residual_deg`; human label: unspecified "concern", no predicted direction)

| metric | observed | genuine median | MAD | status | deviation | MAD-mult | strength | direction |
|---|---:|---:|---:|---|---:|---:|---|---|
| `h09.axis_residual_deg` | −0.431&deg; | 0.520&deg; | 0.406&deg; | PROMISING | −0.951&deg; | **−2.34** | MODERATE | counter-clockwise |
| `h09.centre_tangential` | −0.01303 | −0.0009 | 0.0025 | PROMISING | −0.01213 | **−4.85** | STRONG | counter-clockwise |
| `h09.centre_radial` | 0.6313 | 0.6871 | 0.0086 | DIAGNOSTIC_ONLY | −0.0558 | −6.49 | WEAK (capped) | low |
| `h09.radial_span` | 0.1448 | 0.2388 | 0.0129 | DIAGNOSTIC_ONLY | −0.0940 | −7.29 | WEAK (capped) | smaller |

`h09.centre_tangential` shows the largest-magnitude signal in the whole set (STRONG, −4.85 MAD-multiples) but **was not the control's predeclared 9-marker metric** (`h09.axis_residual_deg` was); reporting it is required by this task's own instruction to inspect 6/9 measurements, but it must not be read as a predicted-in-advance discriminator -- it is an incidental finding needing independent replication. The human label specifies no direction for the 9-marker concern, so "agreement" cannot be assessed for either 9-marker metric; only that both point the same way (counter-clockwise).

### 6 o'clock (not part of this control's label; reported for completeness/context only)

| metric | observed | genuine median | MAD | status | deviation | MAD-mult | strength |
|---|---:|---:|---:|---|---:|---:|---|
| `h06.axis_residual_deg` | −1.721&deg; | 0.709&deg; | 0.595&deg; | DIAGNOSTIC_ONLY | −2.430&deg; | −4.09 | WEAK (capped) |
| `h06.centre_tangential` | −0.00904 | 0.0049 | 0.0024 | PROMISING | −0.01394 | **−5.81** | STRONG |
| `h06.centre_radial` | 0.6401 | 0.6875 | 0.0089 | DIAGNOSTIC_ONLY | −0.0474 | −5.33 | WEAK (capped) |
| `h06.radial_span` | 0.1693 | 0.2430 | 0.0132 | DIAGNOSTIC_ONLY | −0.0737 | −5.58 | WEAK (capped) |

### A pose-confound caveat that applies to every number above

Nearly every metric across all three markers -- including several this control's label never mentioned (6 o'clock entirely) -- is deviated in the **same (negative) direction**, several by large MAD-multiples. Nine simultaneous independent manufacturing defects on one watch is implausible; a single systematic pose/rectification effect touching every marker consistently is far more plausible, and section 1 already demonstrated this exact photo's pose solve is unusually sensitive to decode/resample choices (confidence 0.35 vs 0.73, centre-err 80% vs 0.6%, between two decode paths of the identical file). **This is exactly the "pose/detection confounded" caveat the task asks for**: the broad, consistent negative shift across unrelated markers is better explained as a shared measurement/rectification effect specific to this one difficult photo than as nine independent findings, and it should suppress confidence in *any* single number here, including the ones that happen to point the "right" way.

## 6. Discrimination classification (per metric, evidence-only -- never a verdict on the watch)

- **`h12.tangential_centroid_offset`** -- **weak-to-promising, direction-inconsistent on this control.** PROMISING baseline, MODERATE magnitude (2.73 MAD-multiples), but pointed opposite the predeclared "clockwise" label. One control cannot resolve whether this control's own direction is atypical for this defect family or whether the metric/label pairing itself needs revisiting; needs more controls, not a conclusion.
- **`h12.axis_incidence`** -- **weak.** DIAGNOSTIC_ONLY baseline caps it regardless of direction agreement.
- **`h12.stage3_symmetry_axis_angular_deviation_deg`** -- **insufficient evidence.** No genuine baseline exists; magnitude is negligible on this control regardless.
- **`h12.apex_radial`**, **`h06`/`h09.centre_radial`**, **`h06`/`h09.radial_span`** -- **pose/detection confounded** on this control (see the caveat above); `centre_radial`/`radial_span` are also DIAGNOSTIC_ONLY by the genuine baseline's own prior classification, independent of this finding.
- **`h09.axis_residual_deg`** -- **weak-to-promising, no direction to check.** PROMISING baseline, MODERATE magnitude, but the human label specifies no predicted direction for the 9-marker concern, so this control can only show *a* signal exists, not that it points the right way.
- **`h06`/`h09.centre_tangential`** -- **promising but unpredeclared for this control.** Both PROMISING-baseline and STRONG-magnitude, but neither was this control's predeclared metric (only `h12.centre_t`/`h09.axis_residual_deg` were). Worth tracking in future controls, not claimed as validated here.
- **`h12.centre_radial_projective`**, **`h12.base_radial_projective`** -- **insufficient evidence.** Not computed for this photo at all.

**No metric reaches `SUPPORTED`.** Promotion to `SUPPORTED` (per the profile's own status semantics) requires the labelled displacement to exceed genuine spread *and* ordinary measurement error by a practically meaningful margin, demonstrated across independent controls -- one control, with a pose-confound caveat over most of its own numbers and a direction-disagreeing result on its own strongest clean signal, does not meet that bar.

## 7. Answering the key research question

**Does the frozen geometry pipeline produce a signal for the known crooked/clockwise 12 marker that is meaningfully distinguishable from currently observed genuine variation?**

Partially and ambiguously, on this one control. `h12.tangential_centroid_offset` (the metric with the tightest genuine MAD and PROMISING status) does show a clearly non-trivial deviation (2.73 MAD-multiples) -- but in the direction opposite the predeclared "clockwise" label, not confirming it. `h12.axis_incidence` and the no-baseline rotation-angle observation point the predicted direction, but are weak/unsupported by baseline data. Given the pose-confound caveat over this same control's other markers, **this one measurement cannot answer the question either way** -- it demonstrates the pipeline *can* now be run blind against a known-defect control at all (the actual deliverable this phase unblocks), not that any metric discriminates this defect family.

## 8. Additional local fixtures search (task item 9)

`android/app/src/androidTest/assets/debug/` contains exactly two files, searched across the *entire* git history (all branches), not just this one: `community-tilted-126710blnr-01.jpg` (no documented human QC defect verdict or factory/reference provenance -- usable only as a general pose-regression fixture, already used that way) and `community-vsf-batgirl-crooked12-01.jpg` (`rep-local-001`, used here). **No additional usable known-defect local control exists.** `docs/research/reptimeqc-labelled-corpus-2026-09-13.csv` (qualitative multi-brand Reddit research, no image URLs, `source_url` column empty throughout) was re-checked and remains unusable for quantitative measurement, as previously established. Reddit itself remains blocked (confirmed again this run: `gallery-dl` returned `returncode_1` for all 9 Reddit-sourced controls -- a different failure than the previously-diagnosed `returncode_4`/"blocked by network security", not re-diagnosed further this session since the local-fixture path was this phase's actual deliverable and the prior report's blocker finding already stands).

## Summary answers (A&ndash;H format, extending the prior report)

- **A. What was tested:** the identity-veto mechanism itself (why it fires, whether the pose it blocks is actually wrong), a new research-only bypass for provenance-verified local fixtures, and the first real blind measurement of a known-defect replica control.
- **B. Genuine baseline:** unchanged, cited from `434b492d` (5 independent watches, `<=10°` pose gate) -- not re-run, not re-derived.
- **C. Known-defect controls:** **1 measured** (`rep-local-001`, repository-local, human-review-documented, provenance-verified). 9 Reddit-sourced controls remain blocked.
- **D/E. Which metrics separated/did not separate the defect:** see section 6 -- no metric reaches `SUPPORTED`; `h12.tangential_centroid_offset` and `h09.axis_residual_deg`/`h09.centre_tangential` show non-trivial magnitude but with a direction contradiction (12) or no predicted direction to check (9); several others are pose/detection-confounded or lack a baseline.
- **F. Safe to expose now:** nothing. Identical conclusion to the prior report, now on stronger grounds (an actual measured control, not just zero data).
- **G. Additional evidence needed:** more provenance-verified or otherwise-unblocked controls (see H); repeatability data (still `null` for every metric -- no genuine watch has more than one usable low-tilt image yet); resolution of whether this control's broad negative shift is a real multi-marker effect or the decode-path pose-sensitivity confound described in section 5 (a second photo of the *same* physical watch, if one exists, would directly test this).
- **H. Recommended next step:** do not treat `rep-local-001` as resolved evidence either way. Seek additional provenance-verifiable images (further repository-local fixtures with documented human review, or a credentialed Reddit fetch) before drawing any conclusion about `h12.tangential_centroid_offset`'s direction discrepancy or `h09.centre_tangential`'s unpredeclared STRONG signal. Independently of this task: consider (separately, deliberately, outside a research-validation task) whether `identity_gate.marker_good()`'s fixed brightness threshold should receive the same Otsu-based treatment `marker_qc.py` got on 2026-09-21.

## Files

- `docs/research/gmt-replica-ground-truth-control-set.csv` -- new `provenance_verified` column (`true` only for `rep-local-001`).
- `tools/research/run_replica_control_set.py` -- new `research_identity_bypass_eligible`/`is_provenance_verified`, wired into the acceptance gate; new `research_identity_bypass_used`/`provenance_verified` output columns.
- `tools/research/analyze_replica_control_set.py` -- rewritten: code-verified `PROFILE_TO_MEASURED` mapping (replaces the prior phase's placeholder mapping, which never matched any real measured column), per-metric long-format output, `direction_word` mirroring `DirectionWording.java`.
- `tools/research/tests/test_research_identity_bypass.py` -- new, 6 tests.
- `tools/watch_align_py/tests/test_identity_gate_veto_regression.py` -- new, 6 tests, regression-locks the unmodified `pipeline.py` veto functions.
- `tools/watch_align_py/tests/fixtures_manifest.py` -- additive addendum to the existing `rep-local-001`/batgirl fixture entry documenting this session's identity-gate finding.
- `docs/research/gmt-replica-control-set-results/{pose_gate_diagnostics,control_status,per_image_measurements,per_control_medians,discrimination_analysis}.csv` -- regenerated raw outputs.
