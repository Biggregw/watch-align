# Alpha90 validation: RL-3 replacement photo

Date: 2026-10-05
Build: `1.3.0-alpha90-bezel-ray-proof-arm64`
Frozen source commit: `f66acee665a5afb4450fa08f61396c344be43627`

## Source

r/RepTimeQC replacement RL-3 candidate, thread `1ur0ic4`.

Independent QC discussion reports multiple alignment concerns on the candidate watch, including 12, 9 and 6 marker placement/rotation.

## Alpha90 result

Status: **UNASSESSABLE**.

The selected photo was rejected by Alpha90 before any overlay was shown.

Exact user-visible rejection:

`Automatic fit failed: candidate photo not sufficient for fixed-master overlay: minute fit rejected: projected dial no longer matched physical edge`

## Interpretation

This is correct fail-closed behaviour under the frozen Alpha90 rules. The minute-track-derived projective solution disagreed with the independently detected physical dial boundary strongly enough that Alpha90 refused to draw a potentially misleading master overlay.

Do not loosen the physical-edge consistency gate to force this example through validation.

If another clearer/more frontal photo from the same QC set passes the unchanged Alpha90 gate, that photo may be assessed separately. Otherwise this case remains UNASSESSABLE and validation proceeds to the next pre-locked case.
