# 124060 GMT-style UI parity correction — 2026-10-01

## Why this change exists

Alpha70 proved that the 124060 measurement and confidence path could run safely on a phone, but the first real device test exposed a product-level mistake: the Submariner had been allowed to grow a separate research-style presentation instead of starting from the mature GMT Watch Align experience.

The user's intended product model was simpler: copy the mature GMT experience first, then replace or disable only the pieces that genuinely fail for the new watch family.

Alpha71 corrects that direction without throwing away the measurement work already completed.

## Target

The 124060 should feel like the GMT check:

- whole dial marked at a glance;
- every hour position accounted for;
- found marker outlines visible on the uploaded photo;
- missing, hand-obscured or low-confidence markers visibly marked where they belong;
- detailed 12 close-up showing the local minute-track geometry, gap bracket and left/right spacing references;
- the same summary hierarchy and interaction flow.

The only deliberate presentation difference before calibration is the verdict state. GMT can use green / amber / red because it has model-specific QC limits. The 124060 uses cyan `M` for **measured / not yet judged** and a grey dash for **not judged on this photo**. This prevents presentation parity from being mistaken for calibration parity.

## Lessons reused

- Mature GMT whole-dial markup is the reference product experience.
- A marker should be represented even when it is not found or cannot be judged.
- Whole-dial view is for at-a-glance status; close-up is for measurement evidence.
- The 12 close-up should show the measured triangle, 59/60/01 tick evidence, track chord, gap bracket and side spacing references.
- Hand obstruction and confidence withholding should be visible in the overlay, not buried only in Full results.
- Full-resolution crop remains the preferred source for close-up detail.

## Deliberate divergences

- No GMT green/amber/red verdicts are copied. 124060 thresholds are not calibrated.
- The 124060 has batons at 3, 6 and 9, so the presentation accounts for all three rather than inheriting GMT date-side assumptions.
- The Sub 12 remains the `SubTwelveTriangle` detector and keeps dial-radial rotation as primary because repeatability evidence showed it is better on the current Sub corpus than the GMT local 59/01 chord reference.
- GMT marker-pose output remains diagnostic. The GMT 5-degree near-frontal threshold is not applied.

## New reusable lesson

For a new production family, reuse must begin at the **product contract**, not at the detector layer.

Preferred sequence:

1. Start from the mature end-to-end workflow and presentation.
2. Run the new family through that contract in a neutral `measured / not judged` mode.
3. Replace only model-specific layout, geometry, references and calibration that fail under evidence.
4. Keep mature confidence, failure handling, missing-marker presentation and interaction patterns unless a concrete model difference prevents them.
5. Do not let a research/debug UI become the user-test checkpoint merely because the measurement code was developed separately.

This lesson is now also recorded in `AGENTS.md` and `docs/ENGINEERING_LESSONS.md` so future agents should not repeat the alpha69-alpha70 path.

## Alpha71 scope

- Presentation/workflow parity only.
- No new 124060 QC limits.
- No authenticity classification.
- No GMT detector or GMT QC behaviour change.
- Existing alpha70 reliability gates remain in force.

## Acceptance for phone testing

A successful alpha71 test should show:

- cyan `M` badges and outlines on reliable measured Sub markers;
- grey dash badges and short reasons on obscured, missing or unusable markers;
- all 12 hour positions represented;
- a detailed 12 close-up with the same kind of evidence presentation as GMT;
- no green/amber/red Sub verdicts until family-specific calibration exists;
- no change to the GMT route.
