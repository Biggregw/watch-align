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
Result: **FAIL-FOLLOW, provisional and important**.
Observation from accepted Alpha90 overlay: the yellow fixed-master 12 triangle and 6 baton appear to coincide extremely closely with the candidate features. The known reported 12/6 positional defects are not clearly exposed. Round markers and minute track are also broadly coherent, so this cannot be dismissed simply as an obviously bad global fit.

Interpretation to carry forward without changing Alpha90:
- this is the first clear case where the validation outcome conflicts with the independently reported RL defect;
- possibilities include pose absorption/contamination, the defect being defined relative to a reference not represented by the current master, master-geometry error, or bold rendering masking a very small residual;
- do not explain it away or retune the model until the remaining locked cases are run.

## Current status

The fixed-master concept has shown both promising passes and now one meaningful failure. Continue the locked validation queue unchanged. The purpose of the frozen round is to discover exactly this kind of limitation before any new fitting or measurement logic is introduced.
