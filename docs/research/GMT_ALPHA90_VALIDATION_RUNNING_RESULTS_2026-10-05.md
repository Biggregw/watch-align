# GMT Alpha90 validation running results

Updated: 2026-10-05
Build under test: `1.3.0-alpha90-bezel-ray-proof-arm64`
Frozen source commit: `f66acee665a5afb4450fa08f61396c344be43627`

This file records observed results during the frozen validation round. Do not change Alpha90 in response to any individual case until the round is complete.

## Initial controls

- Clear RL Bruce Wayne control: provisional `PASS-RL`; 6 defect remained visibly different and 12 showed a smaller mismatch while the rest of the dial remained coherent.
- Borderline/acceptable GL control: first image `UNASSESSABLE` due insufficient opposing-pair evidence; clearer image provisional `PASS-GL`.
- Clean GL control: provisional `PASS-GL`; fixed master aligned closely without material false defects.

## Additional locked RL cases

### RL-1 — Clean Pepsi 126710BLRO, obvious CCW 6 baton
Thread: `1ktlzo1`
Result: **PASS-RL, strong**.
Observation: yellow genuine 6 box and white projected radial axis remain near the expected orientation while the candidate 6 baton visibly crosses/cants relative to them. Other dial features remain broadly coherent.

### RL-2 — Clean Pepsi 126710BLRO, rotated-left 6 baton
Thread: `1jawk9m`
Result: **PASS-RL, weak / visualisation-limited**.
Observation: candidate 6 shows a small angular disagreement relative to the projected genuine reference, but the bold 4 px overlay hides much of the difference at normal viewing size. Do not change line width during the frozen round.

### RL-3 — original and replacement examples
Original thread `1gme7m2`: `UNASSESSABLE` because source did not provide sufficient usable imagery.
Replacement thread `1ur0ic4`: repeated **UNASSESSABLE**. Alpha90 rejected the minute-derived fit because the projected dial no longer matched the independently detected physical edge. This is a photo/edge-gate limitation to diagnose after the frozen round, not a reason to loosen the gate during validation.

### RL-4 — VSF Pepsi 126710, 12 and 6 reported not centred
Thread: `1vxcmp9`
Static-screenshot result: **FAIL-FOLLOW, provisional**.
Observation from the accepted Alpha90 still image: the yellow fixed-master 12 triangle and 6 baton appeared to coincide extremely closely with the candidate features. The known reported 12/6 positional defects were not obvious in the static screenshot. Round markers and minute track were also broadly coherent, so this could not simply be dismissed as an obviously bad global fit.

Important follow-up observation from the user on the phone:

- manually turning the overlay on and off produced clearly visible apparent movement/difference in the borderline feature;
- therefore the static screenshot alone may be under-sensitive for small residuals;
- the existing bold overlay may still preserve the residual even when a still image makes it look nearly coincident;
- preserve the original provisional `FAIL-FOLLOW` label for auditability, but do not treat it as a confirmed architecture failure until the frozen round is re-reviewed using blink/close-up inspection and defect-class analysis.

Interpretation to carry forward without changing Alpha90:
- this remains the first case where a static screenshot conflicts with the independently reported RL defect;
- possible explanations include visualisation masking, the defect being defined relative to a reference not represented by the current master, master-geometry error, or pose absorption/contamination;
- the user's blink observation materially increases the likelihood that visualisation sensitivity is part of the issue;
- do not explain it away or retune the model until the remaining locked cases are run.

## Product learning captured during validation

The user's observation that small residuals can appear as movement when toggling the fixed overlay is now a planned product concept rather than an incidental behaviour. After the frozen round, the intended direction is:

- main full-watch overlay for overall coherence;
- enlarged close-ups below the main image for borderline/suspicious features;
- brief blink/toggle inspection on those close-ups;
- later deterministic edge-normal residual measurement that quantifies the same apparent movement without feeding it back into pose;
- future fixed-master text/coronet layers for placement checks such as floating `m` or tilted coronet.

The detailed post-validation implementation plan is `docs/research/GMT_POST_ALPHA90_IMPLEMENTATION_PLAN_2026-10-05.md`.

## Current status

The fixed-master concept has shown strong passes, weak/visualisation-limited passes, correct fail-closed behaviour, and one static-image conflict that now has a counter-observation from blink inspection. Continue the locked validation queue unchanged. The purpose of the frozen round is to discover exactly these limitations before any new fitting, visualisation or measurement logic is introduced.
