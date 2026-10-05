# GMT Alpha90 validation run log

Updated: 2026-10-05

This log records observed Alpha90 outcomes during the frozen validation round. The build under test is `1.3.0-alpha90-bezel-ray-proof-arm64`, frozen at source commit `f66acee665a5afb4450fa08f61396c344be43627`. Do not tune Alpha90 to individual cases during this round.

## RL-1 — Clean Pepsi 126710BLRO, known canted 6 baton

Source thread: `r/RepTimeQC/comments/1ktlzo1`

Independent ground truth selected before running Alpha90: multiple reviewers identify the 6 baton as clearly canted/CCW and recommend RL.

User-run Alpha90 screenshot observed on 2026-10-05.

Observed result:

- Alpha90 accepted the image and produced a coherent full-dial projection.
- The yellow fixed-master 6 baton remains essentially on the projected genuine radial orientation.
- The photographed white 6 baton visibly crosses that reference instead of being traced by it.
- Pixel inspection of the supplied screenshot indicates the real baton is roughly 2–3 degrees CCW relative to the fixed projected baton/axis. This is an image-level estimate, not a production measurement.
- The surrounding round markers and minute-track projection remain broadly coherent, arguing against a gross global pose error being the cause of the local 6 mismatch.

Status: **PASS-RL**.

Interpretation: this is a strong positive result for the fixed-master principle. A defect known independently before testing remains visible after perspective projection rather than being absorbed by the overlay.

Next locked case: RL-2, thread `r/RepTimeQC/comments/1jawk9m`, another independent rotated-left 6-baton example.
