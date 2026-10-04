# Watch Align Calibration Research Protocol

Status: **active governing research protocol**

Protocol version: **v0.1**

This document defines the reusable calibration method for Watch Align. It is deliberately independent of the Android production implementation.

The goal is to build a proven-genuine visual reference, measure how an individual watch differs from it, and validate that those differences correspond to real QC defects. It is not a genuine/fake classifier.

## Core rule

For every feature:

**nominal genuine master -> genuine variation -> photo/detector uncertainty -> frozen reference -> independent QC validation -> implementation choice**

Do not reverse that order.

## 1. Reuse evidence before searching

Before acquiring anything new:

- inspect existing project datasets, downloaded artifacts and prior research images;
- use already-proven genuine examples where suitable;
- do not harvest a larger corpus merely because a result is uncertain;
- search for new photos only when a named evidence gap remains.

Research should begin with the smallest sample capable of answering the question.

## 2. Work one feature at a time

Define the exact feature before measuring it. State:

- physical landmark(s);
- edge/centre definition;
- normalization reference;
- units;
- what would make the feature unassessable.

Do not let a change in edge definition silently change the feature. In particular, marker centre, lume boundary, white-gold surround and overall marker size are separate quantities.

## 3. Establish a nominal master first

Choose the best available proven-genuine image for the feature:

- high resolution;
- near frontal;
- landmarks unobstructed;
- clear dial boundary;
- strong provenance.

A clean loose genuine dial is ideal when available because the movement hole can provide a direct centre and there is no crystal/rehaut distortion, but do not delay useful work searching for one if existing images are sufficient.

The master defines the **zero point / nominal geometry**. One image does not define manufacturing tolerance.

## 4. Measure genuine variation around the master

Measure independent physical genuine watches against the nominal master.

Rules:

- one physical watch is one independent manufacturing sample;
- multiple photos of the same watch estimate photo/detector repeatability, not manufacturing variation;
- preserve provenance and source identity;
- retain raw measurements and rejection reasons;
- report median, spread and relevant percentiles rather than only a final tolerance.

Keep separate:

1. nominal geometry;
2. between-watch genuine variation;
3. within-watch/photo/detector variation.

## 5. Do not let perspective masquerade as manufacturing tolerance

Before an apparent genuine outlier widens the reference envelope, check related/opposing geometry.

For an opposing pair, first express each measurement as a residual from its own nominal value:

`dA = observedA - nominalA`

`dB = observedB - nominalB`

A coherent opposite-signed pattern can be evidence of pose. For example, if the 12-side relationship compresses while the 6-side relationship expands by a similar amount, treat that first as a photographic perspective signal rather than as two genuine manufacturing extremes.

Useful paired checks may include:

- 12 <-> 6;
- 3 <-> 9;
- 1 <-> 7;
- 2 <-> 8;
- 4 <-> 10;
- 5 <-> 11.

The exact relationship must be validated per feature; do not assume cancellation works merely because markers are opposite.

When pose contamination is supported, either:

- derive a validated paired/corrected quantity;
- exclude the contaminated raw measurement from tolerance estimation; or
- mark it unassessable.

Do **not** widen the genuine manufacturing envelope simply to include a bad photograph.

## 6. Prefer relational correction before complex pose reconstruction

Do not calculate camera tilt, homography or projective correction unless the feature requires it.

First test whether directly observed relationships around the dial explain the distortion sufficiently. A simple paired residual that restores the known genuine value is preferable to a more complex pose model that does not improve repeatability.

Complex perspective machinery must earn its place by improving held-out measurement accuracy.

## 7. Freeze the genuine reference before replica validation

Replica evidence must never define, tighten or widen the genuine envelope.

Once the genuine feature reference is frozen, test independent QC examples against it.

A replica that sits inside the genuine reference for the assessed feature is a valid result: **no detectable deviation for that feature**.

## 8. Validate against real QC defects

Use independent RepTimeQC or equivalent examples where reviewers identified a specific visible defect.

Where practical:

1. save the photo and the reported defect separately;
2. measure the photo without using the defect label to steer the measurement;
3. compare against the frozen genuine reference;
4. check whether the same defect is supported;
5. also test accepted/GL examples as negative controls.

Classify each outcome:

- reviewer defect found by measurement;
- reviewer defect missed;
- Watch Align flags an unreported deviation;
- both clear;
- image unassessable.

Investigate misses/false flags before changing thresholds.

## 9. Let evidence choose code vs AI

For each validated feature decide only after testing whether it belongs in:

- deterministic code;
- vision-AI call;
- hybrid code + AI;
- not reliable enough to ship.

A clean geometric rule should normally become deterministic code. A visually contextual feature that AI detects much more reliably may remain an AI feature. Do not force either solution in advance.

## 10. Golden-prompt development

This protocol is itself an experimental instrument.

When testing a protocol version against a feature whose established result is already known:

1. perform the new measurement without fitting to the known answer;
2. compare quantitatively afterwards;
3. diagnose the difference;
4. propose a precise protocol change;
5. test that change on fresh/held-out images;
6. promote the new version only if it improves repeatability/accuracy without introducing new failure modes.

Do not silently tune a measurement until it matches the answer.

### Required end-of-run self-critique

Every research run must end with a `LESSONS LEARNED` section containing:

- what produced the largest uncertainty or error;
- any ambiguous instruction in the protocol;
- any photo type that should be rejected earlier;
- any relationship that improved pose handling;
- any proposed wording/rule change;
- how that proposed change should be tested on fresh or held-out evidence.

**Do not automatically rewrite the governing protocol during the same run.** Self-critique proposes changes; promotion happens only after a separate validation step. This prevents prompt drift and overfitting to one unusual image.

Negative lessons must also be recorded, e.g. a correction that made results worse or an edge definition that proved unstable.

## 11. Standard research output

For each feature report:

- protocol version;
- feature definition;
- nominal-master identity and reason chosen;
- independent physical watches used;
- raw per-watch measurements;
- repeated-photo measurements where available;
- exclusions and exact reasons;
- pose/opposing-marker diagnostics;
- nominal result;
- genuine spread;
- measurement/repeatability spread;
- comparison with any previous established result;
- proposed reference envelope, if justified;
- `LESSONS LEARNED`;
- recommendation: continue / revise / hold.

## 12. Current control experiment

Do not start with Submariner expansion.

Use the **known-working GMT path** as the control:

1. choose one simple already-working GMT feature;
2. use existing proven-genuine GMT images first;
3. select the best genuine nominal candidate;
4. remeasure the feature on a small set of independent genuine watches;
5. apply related/opposing-marker checks for perspective contamination;
6. compare the independently derived result with the existing GMT calibration;
7. refine the protocol only through held-out testing;
8. expand to the next GMT feature only if the first control succeeds.

Production GMT code remains unchanged during this research stage.

## Success criterion

The protocol is successful when it repeatedly reproduces known genuine geometry and useful real-world QC findings with low false-flag rates, while abstaining when the photo cannot support the measurement.

Population-level genuine-vs-replica separation is **not** the success criterion.
