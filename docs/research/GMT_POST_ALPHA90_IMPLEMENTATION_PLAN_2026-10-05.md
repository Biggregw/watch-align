# GMT post-Alpha90 implementation plan

Updated: 2026-10-05
Branch: `feature/android-gmt-perspective-overlay-poc`

## Purpose

Capture the product and technical lessons from the Alpha90 fixed-master validation work and define the implementation order after the frozen validation round finishes.

This document is a plan, not permission to modify the frozen Alpha90 build. Alpha90 remains frozen at source commit `f66acee665a5afb4450fa08f61396c344be43627` on `freeze/alpha90-validation` until the current validation round is reviewed.

## Core architecture that must remain invariant

The working model is:

`fixed genuine master -> candidate minute-track camera pose -> one frozen perspective transform -> inspection only`

Non-negotiable rules:

1. The canonical geometry is derived from proven genuine references and is immutable during candidate analysis.
2. Candidate minute-track evidence is used only to establish camera pose/perspective.
3. The same single accepted transform is applied to every element of the genuine master.
4. Once pose is accepted, it is frozen. No QC target may alter it.
5. Applied hour markers, 12 triangle, 6/9 batons, dial text, coronet, date/cyclops, hands and bezel are QC targets only and must never steer pose or post-fit alignment.
6. No marker-specific local nudge, re-fit, flex or warping is allowed.
7. If the image cannot support a trustworthy pose, the app must fail closed rather than draw a plausible-looking fallback.
8. Watch Align is not an authenticity classifier. A replica may legitimately show no detectable geometric deviation in the features being assessed.

## What Alpha90 has taught us so far

### 1. Fixed-master local defects can remain visible

Known canted 6-baton RL examples have shown visible disagreement between the real marker and the projected genuine reference while surrounding geometry remains coherent. This supports the central fixed-master principle.

### 2. Good and borderline watches can remain close

Clean and accepted/GL examples have generally aligned closely. Small differences can still be visible to the user, especially when the overlay is toggled on and off.

### 3. Static screenshots are not always sufficient

A key user observation is that a borderline difference can be easier to see as apparent movement when switching the overlay on and off than in a single static frame. The human visual system is highly sensitive to edge flicker/jump. This should become a deliberate inspection mechanism rather than an accidental side effect.

### 4. The bold overlay is useful but can hide tiny residuals in a still image

The current bold yellow geometry makes overall alignment easy to understand, but a 1-2 pixel or small angular residual can be visually masked when viewed as a static screenshot. This is not automatically a reason to reduce line width. Close-up and blink inspection may be a better solution.

### 5. Fail-closed behaviour is valuable product behaviour

Some otherwise reasonable-looking photos have been rejected because the minute-derived transform no longer matched the independently detected physical dial edge. The guardrail should not be loosened during the frozen round. Repeated edge-gate rejection is a diagnostic item for later investigation, not a reason to force an overlay.

### 6. Not every Reddit RL tests the same thing

A reported defect may be:

- a local applied-marker rotation;
- a local marker translation/centring issue;
- a whole-dial rotation/translation relative to case/rehaut/bezel;
- bezel alignment;
- text/printing placement;
- date/cyclops alignment;
- or another optical/assembly relationship.

The minute track can define the dial coordinate system, so a defect defined only relative to the case or bezel may require an independent second reference. Do not redesign the local-marker method merely because it does not solve a different defect class.

### 7. RL-4 remains deliberately unresolved

The accepted RL-4 static screenshot appeared extremely close at 12/6 and was provisionally logged as `FAIL-FOLLOW`. The user subsequently reported that toggling the overlay made a visible apparent movement/difference easy to see on the phone. Therefore the static classification must not be over-interpreted.

Preserve the original provisional log for auditability, but treat RL-4 as a case to re-review after the frozen round using blink/close-up inspection and defect-class analysis. Do not tune Alpha90 to it now.

## Defect classes for the finished product

### A. Local applied-marker geometry

Examples: crooked 6 baton, shifted 9 baton, rotated or displaced 12 triangle, displaced round marker.

Primary method:

- minute track establishes frozen pose;
- fixed genuine marker geometry is projected once;
- candidate marker is compared only after pose freeze.

This is the current strongest use case.

### B. Printed dial graphics and text

Examples: floating `m`, tilted coronet, text block too high/low, word spacing, `SWISS MADE` placement.

Because these features are printed on the same dial plane as the minute track, they can use the same frozen perspective transform.

Two levels must be separated:

1. **Placement QC**: position, angle, baseline, spacing between groups. This is the first target.
2. **Artwork QC**: glyph shape, kerning, stroke thickness and fine print morphology. This needs higher-resolution evidence and stronger genuine-reference calibration and comes later.

Text/coronet must never participate in pose fitting.

### C. Whole dial relative to case/rehaut

If the complete minute track/dial assembly is rotated or translated relative to the case, a minute-track-derived coordinate system can normalise that movement away. This requires a separate independent physical reference such as a validated case/rehaut/crown relationship.

Do not use that independent reference to alter local dial pose. Treat dial-to-case QC as a separate measurement layer.

### D. Bezel alignment

Current Alpha90 bezel rays are visual aids only. The bezel is on a different physical plane, so perspective/parallax can create real apparent displacement. A later bezel module must model this independently and must not feed back into dial pose.

### E. Date/cyclops

Date printing, date-window alignment and cyclops geometry are separate targets. The cyclops/lens introduces optics different from the flat dial plane. Do not assume the same residual model is sufficient.

## Product presentation learned from Alpha90

### Main result view

Keep the full-watch projected genuine overlay at the top. Its purpose is overall confidence: the user should be able to see whether the perspective solution is coherent around the dial.

The main image should remain static by default.

### Closer inspection section

Below the main image, automatically show enlarged crops for features that are borderline, suspicious or otherwise worth inspection.

Initial candidates:

- 12 triangle;
- 6 baton;
- 9 baton;
- any round marker with meaningful residual;
- later coronet and specific text regions.

Rules:

- every close-up is only a crop/enlargement of the same projected fixed master and candidate image;
- no close-up may run a local pose fit or move the overlay;
- clean features need not clutter the default screen, though an advanced view can expose all positions;
- early product wording should be descriptive rather than verdict-heavy, for example `Small deviation detected - inspect closely`.

### Blink inspection

Blinking should become a first-class inspection tool.

Proposed behaviour after validation:

1. Main full-watch image loads static with overlay on.
2. A suspicious/borderline close-up automatically blinks for approximately 3-4 cycles when first shown.
3. It then stops with the overlay on.
4. A clear `Blink overlay` / `Stop blinking` control allows the user to restart or halt it.
5. Press-and-hold can optionally hide the overlay and restore it on release.
6. Avoid indefinite full-screen blinking by default.

Exact cadence and animation timing are UX parameters to test later. Do not tune them during the frozen validation round.

## Turning perceived blink movement into measurements

The visual jump seen during overlay toggling suggests a deterministic residual measurement that naturally follows the current architecture.

After pose is frozen:

1. Project the genuine feature outline into candidate space.
2. Search only within a narrow band normal to that fixed reference edge for candidate edge evidence.
3. Measure signed perpendicular displacement from the fixed genuine edge to the candidate edge.
4. Never feed those residuals back into the pose transform.

Interpretation examples:

- similar signed displacement along the full feature -> translation/centring shift;
- displacement changes sign or has a linear slope from one end to the other -> rotation;
- circular marker residual biased to one side -> centre displacement and/or size difference;
- symmetric expansion/contraction around a circular reference -> potential size difference.

For a baton, a residual pattern such as `-2, -1, 0, +1, +2 px` across its length is the numerical equivalent of the apparent angular jump a user sees while blinking.

### Movement map concept

A later close-up may show small arrows or a subtle residual plot indicating where the candidate edge sits relative to the genuine reference. This must be an inspection display, not a fit mechanism.

## Genuine variation and thresholds

Reddit RL/GL examples validate whether the system exposes defects humans care about. They must never define the genuine tolerance envelope.

The eventual decision logic should be based on:

`genuine variation + detector repeatability + photo/perspective uncertainty`

Only after those are measured should residuals be categorised as normal, borderline or outside the validated genuine range.

Early residual implementation should therefore report measurements without automatic RL/GL verdicts.

## Implementation sequence

### Phase 0 - finish frozen Alpha90 validation

No code changes.

- Continue the locked RL and GL/borderline queue.
- Prefer alternating GL and RL where practical so false-positive behaviour is checked as aggressively as defect detection.
- For each accepted image, record both static-overlay observation and whether manual blink/toggle reveals a clearer local movement.
- Preserve `UNASSESSABLE` results rather than forcing a worse photo.
- Record the suspected defect class for each case.
- Do not alter line width, pose thresholds, master geometry, bezel rays or solver behaviour.

Exit gate: complete/review the intended validation set and decide whether the fixed-master principle is sufficiently reliable for post-pose inspection.

### Phase 1 - validation review by defect class

Offline analysis only unless a specific defect is proven.

For each problematic case determine whether it is primarily:

- local marker geometry;
- whole dial versus case/rehaut;
- bezel relationship;
- text/printing;
- photo quality/edge gating;
- genuine-master calibration;
- or possible pose contamination.

Specifically re-review RL-4 using the user's blink observation rather than relying only on a static screenshot.

For repeated physical-edge rejection, diagnose whether the independent boundary detector is selecting the intended dial edge or another nearby boundary before changing thresholds.

### Phase 2 - synthetic independence controls if ambiguity remains

Use a previously accepted clean/GL image and make controlled image-only modifications while leaving the minute track untouched.

Minimum controls:

- rotate only the 6 baton by a known amount;
- translate only the 6 baton sideways by a known amount;
- rotate/translate only the 12 triangle.

Expected invariant: the calculated projected genuine master remains effectively unchanged while the edited candidate feature moves away from it.

This is a proof of independence, not a source of production tolerances.

### Phase 3 - first post-Alpha90 product build: close-up/blink UX

Make no solver change unless Phase 1 proves one is required.

Proposed Alpha91 scope:

- retain the frozen accepted pose/master architecture;
- add `Closer inspection` below main image;
- generate fixed crops for 12/6/9 first;
- allow manual blink on each crop;
- optionally test a short automatic blink sequence;
- no automatic pass/fail judgement;
- no local re-fitting.

Success criterion: small known residuals that are hard to see on the full image become easy to inspect without changing geometry.

### Phase 4 - edge-normal residual prototype

Develop offline first, then expose as measured-only diagnostics.

Proposed Alpha92-type scope after validation:

- fixed projected feature outlines;
- narrow-band candidate edge sampling;
- signed residuals;
- baton rotation/translation estimates derived from residual pattern;
- circle centre/size residuals;
- confidence/repeatability reporting;
- no verdict thresholds initially.

Validate against:

- frozen GL controls;
- known RL controls;
- synthetic controls;
- resize/re-render repeatability;
- genuine reference images.

### Phase 5 - genuine residual calibration

Collect/measure only the evidence needed to define genuine variation for the residual metrics that survived Phase 4.

Freeze:

- nominal reference geometry;
- genuine variation envelope;
- detector/repeatability uncertainty;
- image-quality eligibility rules.

Only then introduce `within expected genuine variation`, `borderline` or `outside validated range` style language.

Do not convert these into authenticity claims.

### Phase 6 - printed text and coronet placement layers

Extend the canonical genuine master as independent optional layers:

- coronet/logo;
- `ROLEX` block;
- model designation text;
- lower certification text;
- `SWISS MADE` and model-specific lower printing.

Start with placement/angle/baseline residuals. Add fine artwork/glyph comparison only if image resolution and genuine calibration support it.

Use the same frozen minute-track pose. Text is inspection-only.

### Phase 7 - independent dial-to-case and bezel modules

Only after local dial QC is stable:

- establish an independent case/rehaut reference for whole-dial rotation/translation;
- keep it separate from local-marker pose;
- separately research bezel-plane/parallax modelling before any bezel verdict;
- keep current bezel rays as visual aids until that modelling is validated.

### Phase 8 - expand to Submariner

Do not port the new architecture to Submariner until GMT has passed the preceding gates. Once GMT is stable, reuse the architecture and replace only model-specific genuine geometry, detector profiles and validated tolerances.

## Implementation guardrails

Do not:

- change Alpha90 during the frozen round;
- use a candidate QC feature to improve its own alignment;
- locally fit a close-up;
- define genuine tolerances from replica/RL/GL examples;
- interpret a fail-closed result as a defect in the watch;
- interpret a no-deviation result as proof of authenticity;
- let bezel or case geometry silently move the dial master;
- add text/artwork comparison before placement geometry is validated;
- expand model scope while the GMT architecture is still unresolved.

## Decision gates

Proceed from one phase to the next only when the previous phase answers a named question.

1. **Alpha90 gate:** Does the fixed-master/minute-pose concept reliably preserve meaningful local residuals on usable photos?
2. **UX gate:** Do close-up/blink views make borderline residuals reliably visible without changing geometry?
3. **Residual gate:** Can deterministic edge-normal residuals reproduce the differences seen by human blink inspection with acceptable repeatability?
4. **Calibration gate:** Are the residuals stable enough on proven genuine watches to define a useful genuine envelope?
5. **Text gate:** Does the same frozen pose place genuine text/coronet references reproducibly enough for placement QC?
6. **Independent-reference gate:** Can whole-dial/case and bezel relationships be measured without contaminating local dial pose?

## Immediate action

Continue the exact frozen Alpha90 validation set. For the remaining accepted cases, preserve the static screenshot but also record whether manual overlay toggling produces a visible apparent movement at the suspected feature. Do not implement close-ups, automatic blinking, residual measurements or text layers until the frozen validation round has been reviewed.