# Calibration platform foundation plan

Status: post-baseline review. PR #44 calibrator run 44 completed successfully on commit `b87d337adaf84627a335be8bb48b6ffc106890aa`. The plan below incorporates findings from the complete evidence artifact rather than relying only on code inspection.

## Why this matters

The calibration layer is foundational to Watch Align. The goal is not merely to make the existing 124060 calibrator work for the remaining Submariner 12-series models. The goal is to establish a reusable calibration platform that can support future watch designs and families without repeatedly rebuilding acquisition, provenance, reliability, statistics, validation, and promotion logic.

The statistical core already has strong principles that must be preserved:

- Genuine watches define accepted genuine variation.
- Replica data never moves a genuine-derived boundary; it is stress/separation evidence only.
- Each physical watch is the independent unit; multiple photos of one watch do not create multiple watches.
- Every reliable genuine photo can contribute to the production envelope because the app judges individual uploaded photos.
- Only obvious photo-level measurement failures within the same physical watch may be excluded.
- Source diversity is enforced on acquired genuine physical watches, not raw image count or discovery hits.
- Development/validation/holdout remain diagnostics for generalisation, not a mechanism for discarding valid genuine observations.

## Baseline from calibrator run 44

Run 44 is the baseline that future platform refactoring must explain or reproduce.

- workflow run: `37108014158`
- artifact: `watch-calibrator-124060-37108014158`
- artifact id: `11268923648`
- artifact digest: `sha256:652c3c9b7f8486aab3edda984cab01083355d469285f23e626ca6bbc7889cbd5`
- state: `GENUINE_ENVELOPE_READY`
- 41 acquired genuine physical watches
- 5 qualifying genuine sources under the configured whole-dataset source-diversity policy
- dominant source share 29.27%, below the 45% cap
- 319 unique genuine photos entered measurement
- 73 of those 319 photos produced at least one production-gated metric
- all discovery, acquisition and measurement rows inspected in this run were model `124060`
- no measurement rows reported an exception from the model-routing change

Metric coverage in the baseline is deliberately retained because it exposes where reliability and evidence are strong or thin:

| metric | genuine photos | genuine watches | genuine sources |
|---|---:|---:|---:|
| `twelve.rotation_deg` | 60 | 29 | 5 |
| `twelve.gap_r` | 42 | 22 | 5 |
| `twelve.centring_w` | 60 | 30 | 5 |
| `round.ring_rho` | 61 | 29 | 5 |
| `round.spacing_rms_deg` | 61 | 29 | 5 |
| `baton.3_9_line_offset_r` | 29 | 18 | 4 |
| `axis.12_6_line_offset_r` | 29 | 18 | 3 |

The current replica stress set contains 6 watches and 55 images, split 3 Clean and 3 VSF. None of the seven calibrated metrics showed replica separation outside the current genuine clear envelope. That is not a calibration failure, but it confirms that `calibrated` and `useful discriminator` must remain separate states.

The acquisition layer also behaved correctly by recording inaccessible or stale media rather than bypassing source restrictions. Examples included expected 404 image URLs and robots-blocked media. These are source-health diagnostics, not reasons to weaken acquisition policy.

## What the completed run changes in the plan

The completed run confirms the general direction, but it adds four requirements that should now be treated as first-class foundation work.

### A. Preserve the measurement coverage funnel and rejection reasons

Only 73 of 319 genuine photos produced any usable metric. The current output shows that 132 photos reached an automatic dial fit, 74 had a reproducible dial result, and 73 produced at least one gated metric, but it does not preserve a complete reason chain for every metric that became blank.

Future calibration must retain a coverage funnel such as:

`acquired -> readable -> dial found -> dial reproducible -> layout valid -> raw metric measured -> reliability gate -> calibrated value`

Every transition that rejects or withholds a value should have a machine-readable reason. This is especially important for future families, where a low yield could otherwise be mistaken for normal model variation.

### B. Make per-metric evidence sufficiency explicit

The whole-dataset source-diversity gate passed with five qualifying sources, but individual metrics can have materially narrower support. The current engine defaults to a minimum of three sources for a metric unless explicitly configured. That is why `axis.12_6_line_offset_r` can be calibrated from 18 watches across only three sources while the overall acquisition policy requires five qualifying sources.

This should not be left to an implicit default in the long-term platform.

Each metric should have an explicit evidence contract covering, as appropriate:

- minimum independent physical watches
- minimum independent sources
- maximum single-source share for that metric
- minimum multi-photo watches for repeatability evidence
- whether a source must contribute more than one watch
- model/family applicability

The right threshold may differ by metric, but it must be explicit, reported, and testable.

### C. Separate live source discovery from deterministic calibration replay

The fresh run found one more European Watch Company watch than the previous successful calibration run. The evidence population moved from 40 to 41 acquired genuine watches. The resulting calibration stayed very stable, which is reassuring: most observed limits were identical and the `twelve.gap_r` repeatability guard changed only by about 0.0000013.

This stability is good evidence for the method, but it also proves that a fresh public-web discovery run is not byte-for-byte reproducible because public inventory changes.

The platform should therefore have two distinct concepts:

1. **Evidence acquisition/refresh**: discover and acquire a new candidate evidence snapshot.
2. **Calibration replay**: rerun measurement and calibration against an immutable frozen evidence manifest.

A production promotion package should always point at a frozen evidence snapshot so a future detector or calibration-engine change can be replayed against exactly the same input population.

### D. Distinguish calibration readiness from QC utility

All seven metrics reached a genuine-envelope calibration state, but the current replica stress set showed no separation for any of them.

The platform should report at least three independent dimensions rather than collapsing them into one status:

- **measurement reliability**: can the metric be measured consistently enough?
- **genuine-envelope sufficiency**: is there enough genuine evidence to define safe variation?
- **QC utility**: does the metric actually distinguish any meaningful replica defects on stress evidence?

A metric may be reliable and safely calibrated yet have little or no current QC utility. That should never be hidden.

## Architectural direction

The platform should have a stable generic centre:

`model/family config -> discovery/acquisition adapter -> frozen evidence snapshot -> measurement adapter -> generic calibration engine -> optional family comparison -> explicit production promotion`

The generic calibration engine must not know whether it is processing a Submariner, GMT, Daytona, Omega, Tudor, or another watch. Design-specific knowledge belongs in the measurement adapter and model/family configuration.

## Foundation hardening before adding 126610

### 1. Validate identity and config before doing any expensive work

The requested model must match every downstream representation:

- CLI/workflow requested model
- config filename and config `model`
- family membership
- discovered candidate model
- acquired image model
- measurement output model

No implicit fallback to another model is permitted.

Add strict config/schema validation as a preflight step. Unknown metric sidedness, missing required policy fields, duplicate metric names, unsupported adapter ids, impossible layout declarations, and inconsistent model/family relationships must fail before discovery or measurement.

Avoid hidden defaults for decisions that affect evidence sufficiency or production judgement.

### 2. Separate measurement from judgement

A new model must be measurable before it is production-supported.

Calibration must call a measured-only, versioned measurement adapter rather than requiring the model to be enabled in Android production QC.

For every metric, retain the complete state rather than collapsing unreliable observations to a blank value:

`raw value -> reliability state -> reason -> diagnostics -> usable calibrated value`

This is intended to make failures such as the current 3/9 baton issue diagnosable without losing the underlying geometry.

### 3. Introduce a versioned generic measurement contract

Every measurement adapter should return the same outer contract, including:

- exact model
- family
- expected layout
- observed layout and layout confidence
- photo quality and pose information
- named metrics
- units and metric semantics
- raw value
- reliability state per metric
- diagnostic reason per metric
- calibrated-eligibility state per metric
- adapter version
- reliability-policy version
- metric-definition version

The generic calibrator should not contain watch-specific concepts such as triangle, baton, cyclops, date aperture, or chronograph subdial logic.

### 4. Add a measurement fingerprint and immutable run manifest

Every calibration result must be tied to the code, config and evidence that produced it.

Each candidate calibration package should record at least:

- repository commit SHA
- exact requested model
- frozen model config and hash
- frozen family config and hash where applicable
- evidence manifest id/hash
- measurement-adapter id/version
- reliability-policy version
- metric-definition version
- calibration-engine version
- acquisition-adapter version
- detector/measurement fingerprint
- source provenance summary
- measurement coverage funnel

If the measurement fingerprint changes, an existing calibration should be explicitly marked as requiring revalidation rather than silently reused.

### 5. Enforce a strict model/layout contract

The model config must be more than descriptive metadata.

Examples:

- 124060 expects no date and a baton at 3.
- 126610LN/LV expect a date at 3 and no baton at 3.
- Future families can define entirely different layout contracts.

If claimed model and observed layout conflict, quarantine the image rather than allowing it to shape calibration. An unresolved layout may still allow metrics that are independent of the unresolved region, but this must be declared by the adapter rather than inferred by the generic engine.

### 6. Split family configuration from model configuration

Avoid copying the current large 124060 configuration for every new reference.

Target structure:

- family config: shared adapter ids, reusable metric definitions, family-level discovery strategy, common layout vocabulary
- model config: exact reference, expected layout, source overrides, applicable/excluded metrics, model-specific policy overrides

Example:

- `families/submariner_12.json`
- `models/124060.json`
- `models/126610LN.json`
- `models/126610LV.json`

Family config expresses shared architecture only. It must never imply that different references share one calibration envelope.

### 7. Generalise acquisition behind an adapter contract

The central calibrator currently knows about a Submariner-specific acquisition adapter. That should become a generic adapter registry/interface.

The reusable acquisition contract should output a common manifest containing physical-watch identity, exact model, family, class, source, listing identity, provenance, image identity, acquisition state and duplicate-cluster identity.

Source-specific resolvers can remain specialised, but the calibrator core should not import or depend on `SUBMARINER_12`.

### 8. Strengthen independence and duplicate handling

Exact SHA duplicate rejection should remain, but it is not enough because the same photo may be resized, recompressed, cropped slightly, or rehosted.

Add perceptual duplicate clustering so alternative renditions of one photograph cannot create artificial evidence or source diversity.

Do not delete source evidence. Record duplicate-cluster identity and exclude duplicate renditions from independent evidence counts. Physical-watch identity remains the primary statistical independence unit.

### 9. Harden the generic calibration engine

The statistical engine should remain config-driven, but its contract should be stricter.

Required improvements:

- explicit supported metric sidedness/types rather than treating unknown values as two-sided
- explicit per-metric evidence sufficiency
- per-metric source dominance reporting
- explicit measurement-reliability state
- explicit genuine-envelope state
- explicit utility/separation state
- no silent cross-watch trimming
- within-watch outlier rejection remains conservative and fully auditable
- replica evidence remains unable to move a genuine-derived boundary
- pose sensitivity remains reported rather than silently filtered when the production route accepts the photo

### 10. Separate calibration from production promotion

A successful calibration run should produce a candidate calibration package, not automatically become production QC.

Production promotion should be a separate explicit step requiring evidence such as:

- model/config identity checks passed
- frozen evidence snapshot available
- genuine envelope sufficient for each promoted metric
- per-metric source diversity satisfactory
- measurement reliability satisfactory
- expected/observed layout behaviour validated
- known genuine regression fixtures clear
- replica utility/separation explicitly reported
- detector fingerprint current
- product-owner acceptance

Promotion should record exactly which candidate package and evidence snapshot were accepted.

## Family calibration rule

Never begin by pooling exact references.

For related models:

1. Calibrate each exact reference independently.
2. Compare genuine distributions metric by metric.
3. For each metric report:
   - exact-model envelopes
   - proposed family-union envelope
   - additional width introduced by sharing
   - source/watch counts
   - measurement coverage
   - pose/repeatability behaviour
   - replica utility before and after sharing
4. Share a family envelope only when evidence supports it.
5. If sharing makes a metric too broad to be useful, retain model-specific calibration or suppress the metric.

For Submariner 12-series this means separate 124060, 126610LN and 126610LV evidence first. The 3-to-9 baton metric is No-Date-only. Date-side metrics are Date-only. Other metrics may become family-shared only after comparison proves compatibility.

## Current known 124060 reliability issue

The current 3/9 problem should be fixed before expanded corpus work.

The analyser treats resize repeatability as diagnostic, but the calibration pair metric currently promotes the strict individual baton repeatability test into a hard measurement gate. This can make genuine watches show `reading not steady enough` even when both batons were found.

Do not simply remove the gate and reuse the old calibration limits. If the production measurement route changes, recalibrate the affected relational metrics with the same reliability route that production will use.

Preferred direction:

- judge relational metrics using pair-level repeatability of the actual relational quantity
- do not gate a relational measurement solely on unrelated one-pixel individual-edge movement
- rerun genuine calibration after changing the route
- keep hand/obscuration/wrong-place/outline-confidence gates intact

The run 44 evidence reinforces the need for this work because `baton.3_9_line_offset_r` is currently supported by only 29 photos from 18 genuine watches and 4 sources, while `axis.12_6_line_offset_r` has the same photo/watch count across only 3 sources.

## Revised implementation sequence

1. Preserve PR #44 run 44 and artifact `11268923648` as the known baseline.
2. Add strict model/config/family/schema validation and exact-model checks at every pipeline boundary.
3. Add a frozen run manifest, measurement fingerprint and evidence-snapshot identity.
4. Add deterministic calibration replay from a frozen evidence manifest, separate from live discovery/refresh.
5. Refactor measurement into a measured-only, versioned adapter contract while proving 124060 numerical equivalence against the run 44 baseline.
6. Preserve raw values, reliability reasons and the complete measurement coverage funnel.
7. Make per-metric sufficiency/diversity policies explicit and remove hidden evidence-policy defaults.
8. Generalise acquisition behind an adapter contract so the central calibrator no longer assumes `SUBMARINER_12`.
9. Add perceptual duplicate clustering and duplicate-cluster provenance.
10. Fix the 124060 baton relational reliability path and rerun against the frozen baseline evidence before refreshing sources.
11. Re-run a fresh 124060 evidence refresh as a separate test and explain any changes.
12. Add measured-only 126610LN and 126610LV configs/adapters without enabling Android production judgement.
13. Gather source-diverse genuine and replica stress evidence for both Date references.
14. Produce a 12-series family compatibility report metric by metric.
15. Adopt shared family envelopes only where supported; keep model-specific or unavailable checks elsewhere.
16. Only then generalise the Android Submariner production QC path.

## Acceptance criteria for the platform foundation

Before using the platform as the basis for future watch families, it should demonstrate all of the following:

- A new exact model cannot silently route through another model.
- Invalid or ambiguous config fails before expensive work starts.
- A claimed model/layout conflict is quarantined or explicitly scoped out of unaffected metrics.
- Measurements can run before production QC is enabled.
- Raw measurement and reliability diagnostics are preserved.
- A coverage funnel explains why photos/metrics were rejected or withheld.
- Adding a new family does not require editing the generic statistical engine.
- Adding a new acquisition family does not require hard-coding it into the runner.
- Config/model/measurement versions are frozen into every calibration artifact.
- A frozen evidence snapshot can be replayed deterministically.
- Detector or metric-definition changes invalidate/revalidate affected calibration explicitly.
- Physical-watch identity and source provenance survive every stage.
- Exact and perceptual duplicate evidence cannot inflate independence.
- Per-metric evidence sufficiency and source diversity are explicit.
- Replica data cannot move genuine-derived boundaries.
- Calibration readiness and replica-QC utility are separate reported states.
- Family pooling is a separate evidence-based decision rather than an assumption.
- Production promotion is explicit and auditable.
- The hardened platform reproduces the run 44 124060 baseline unless an intentional measurement-route change explains the difference.

## Scope guardrail

This foundation work exists to support the product roadmap, beginning with the remaining Submariner 12-series references. It should remain bounded and testable. Avoid open-ended framework work that does not directly improve calibration correctness, reproducibility, family expansion, or production safety.
