# GMT Alpha90 validation results

Updated: 2026-10-05
Frozen build: `1.3.0-alpha90-bezel-ray-proof-arm64`
Frozen source commit: `f66acee665a5afb4450fa08f61396c344be43627`

This file records observed outcomes only. Do not change Alpha90 during the locked validation round.

## Initial controls

- Clear RL control, Bruce Wayne thread `1ua5hfh`: **PASS-RL, provisional**. Known local 6/12 problem remained visible while most other markers stayed coherent.
- Borderline GL, VSF Pepsi thread `1u6is5g`: first photo **UNASSESSABLE** because the solver correctly refused weak opposing-pair evidence; clearer photo **PASS-GL, provisional**.
- Clean GL control, Clean Pepsi V3 thread `1iud77l`: **PASS-GL, provisional**. Fixed master remained close throughout the dial.

## Locked RL cases

### RL-1 — Clean Pepsi 126710BLRO, thread `1ktlzo1`

Ground truth locked before testing: multiple reviewers independently identified a badly canted 6 baton and recommended RL.

Alpha90 result: **PASS-RL, strong**.

Observation: the projected genuine 6 reference remained straighter than the real baton and the candidate crossed the fixed reference rather than being absorbed by it. Other marker geometry remained broadly coherent, so the mismatch looked local rather than like a globally bad pose solve.

### RL-2 — Clean Pepsi 126710BLRO, thread `1jawk9m`

Ground truth locked before testing: crooked/rotated-left 6 baton; OP ultimately RL'd for the 6 marker.

Alpha90 result: **PASS-RL, weak / visualisation-limited**.

Observation from the user-run screenshot: the yellow box sits very close to the candidate baton and would be easy to read as a match at normal zoom. Pixel-level inspection of the screenshot shows the real white baton axis drifting by roughly 1 degree relative to the projected genuine box/axis, so the fixed master is not perfectly following the defect, but the current 4 px bold rendering masks much of that residual visually.

Interpretation: this is not a clean `FAIL-FOLLOW`, because a residual remains, but it exposes a real product limitation: small RL-level rotations can be difficult to see with the current bold proof rendering. Do not change rendering until the locked validation round is complete.
