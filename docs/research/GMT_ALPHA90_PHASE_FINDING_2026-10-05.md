# Alpha90 absolute clock-phase finding

Date: 2026-10-05
Frozen Alpha90 source: `f66acee665a5afb4450fa08f61396c344be43627`

## Finding

Expanded unattended testing exposed a missing degree of freedom in Alpha90: absolute clock phase / in-plane watch orientation.

Several rotated photos passed the existing minute-pair solve and physical-edge guard, so Alpha90 returned `ACCEPTED`, while the projected master kept canonical 12 pointing image-up rather than following the physical watch's nominal 12 direction.

This makes those accepted overlays invalid for local marker QC even though the perspective/edge fit itself passed.

A clear automated example is `EXT_RL_BLRO_ARF_01`: the watch is materially rotated in the source photo but the Alpha90 master remains image-up.

## Why

The frozen source explicitly locks canonical 12 to image-up.

The 60 evenly spaced minute positions are cyclically symmetric. They can constrain projective shape, but without an independent phase cue they cannot identify which repeating minute position is absolute 12 o'clock on a freely rotated photograph.

If hour-position minute stubs are distinguishable, the symmetry is reduced but absolute 12 still is not uniquely identified.

This is a mathematical identifiability issue, not a tuning problem.

## Validation rule from now on

Do not change frozen Alpha90.

For every deterministic Alpha90 `ACCEPTED` image, perform a global-phase triage before reviewing local marker residuals:

- `PHASE OK`: projected nominal 12/6 direction is coherent with the watch, so local QC review may continue.
- `GLOBAL_PHASE_MISMATCH`: do not judge 12/6/9/round markers from that overlay.
- `PHASE INDETERMINATE`: photo does not support local overlay review.

`ACCEPTED` now means only that the minute/edge perspective solution passed. It is not proof that the master has the correct absolute clock phase.

## Post-frozen solution direction

Keep phase independent from QC targets.

Preferred architecture:

1. physical dial edge as coarse seed/guard;
2. minute-track/opposing-pair geometry for projective shape;
3. separate coarse phase anchor for nominal 12 direction;
4. freeze transform;
5. inspect candidate QC targets without feedback.

Preferred phase anchor to research first: **case winding-crown direction**.

- Standard 126710 BLNR/BLRO/GRNR: crown identifies nominal 3 o'clock.
- Sprite/VTNR: crown-at-9 architecture must be explicit and model-specific.
- Robust fallback: user indicates the nominal 12 side coarsely. This supplies phase only and must not trace or fit the 12 marker.

Do not solve phase by fine-fitting the candidate 12 marker, 6/9 markers, date/cyclops, bezel triangle or dial text, because those are QC targets.

## Related automation finding

The expanded external-source pool also exposed a provenance mismatch: an album attached to a source labelled as a VSF Pepsi visibly contained a green/black left-crown Sprite. That source was removed/replaced on the automation branch rather than silently retained.

The preserved visual-review prompt now requires global-phase verification before any local-marker verdict.

## Meaning for the core concept

This does **not** invalidate the fixed-genuine-master idea.

It refines the required pose architecture. Minute geometry can remain the metric perspective source, but an independent coarse orientation/phase cue is required before the genuine master can be placed correctly on arbitrarily rotated uploaded QC photographs.
