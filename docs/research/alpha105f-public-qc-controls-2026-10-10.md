# Alpha105f real-photo QC validation checkpoint, 2026-10-10

Baseline: feature/android-alpha102-submariner commit ab9cd4bc031a. Validation branch: feature/android-alpha105f-qc-validation. No genuine reference thresholds, detector, model specs, or production reporting logic changed.

CI real-photo runs:
- Five public Reddit photos, hash-verified: https://github.com/Biggregw/watch-align/actions/runs/38052807244
- Complete preview result screens: https://github.com/Biggregw/watch-align/actions/runs/38052912490
- Nine original public photos, including same-watch repeatability: https://github.com/Biggregw/watch-align/actions/runs/38053087129

## Primary markers: five of five desired review checks confirmed

| Reddit QC example | Feature | Actual Alpha105f |
|---|---|---|
| GMT 1s4cszq, strong reviewer concern | 6 | WORTH, rotated 1.2° clockwise, genuine max 0.7° |
| GMT 18vdgh0, generally accepted 6 | 6 | WITHIN |
| Sub 124060 1wgfy7k, rejected 12 | 12 | WORTH, rotated 1.6° clockwise, genuine max 1.2° |
| Sub 124060 1w848xb, mild 12 but accepted | 12 | WORTH, closer to minute track by 1.08% of dial, genuine max 0.40% |
| Sub 124060 1kqn8ub, accepted 12 | 12 | WITHIN |

The two WORTH/CLEAR types produce inspectable close-up tiles. A new JUnit regression test checks WORTH close-ups, CLEAR close-ups, MINOR non-escalation and NOT_ASSESSED withholding. Android build and tests pass.

## Further 9 o'clock results that require review

- GMT 18vdgh0 image 1: CLEAR 9 position, toward 10 by 0.65% dial, genuine max 0.33%.
- Same GMT image 2: 9 WITHIN, different frontal frame of the same physical watch.
- Same GMT image 3: 9 WORTH for 1.2° anticlockwise rotation (smaller dial, R=138 px).
- Sub 124060 1w848xb image 4: 9 CLEAR toward 10 by 0.74%, genuine max 0.24%.
- Same Sub image 5: 9 CLEAR toward 10 by 0.81%, genuine max 0.25%.
- Sub 124060 1kqn8ub image 1: 9 WORTH toward 10 by 0.68%, genuine max 0.24%; image 2 dial not found.

The accepted Sub's 9 position is repeatable and may genuinely be a small deviation. The accepted GMT's CLEAR result is not stable across its different photos, so it needs bounded pose, light and outline validation before production is called ready. Reviewers accepting a watch does not itself prove each marker is perfect.

## Release gate

Do not change genuine boundaries using Reddit replica results. Do not blindly cap every 9 marker finding or relabel all accepted watches as within. Diagnose the GMT 9 inconsistency with a controlled same-photo/pose comparison and held-out genuine validation. Do not merge this checkpoint to main while the issue remains. The user's personal Batgirl images were not run through GitHub CI, only public Reddit images were.

LESSONS LEARNED: Strictly flagging trustworthy visible borderline measurements works on these five examples without any calibration or reporting code modification. The remaining gap is single-photo 9 position reliability, not absence of a strict review status.
