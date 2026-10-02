# GMT genuine-envelope QC audit, 2026-10-02

## Product rule

Watch Align is replica QC, not a perfection grader and not an authenticity classifier. A reliable
measurement already observed on a genuine watch belongs inside the accepted genuine envelope,
even when the genuine watch has a small visible imperfection. A replica is highlighted only when a
reliable measurement is outside the supported genuine envelope and the existing visibility and
stability gates also support the call.

This is a deliberate tightening of the product contract. Earlier GMT work sometimes retained a
CHECK inside known genuine variation because the same level also caught community-noticed replica
faults. That is no longer sufficient. If genuine and replica values overlap, that measurement does
not distinguish the replica at that level and must stay clear.

## Existing GMT evidence versus production boundaries

| check | documented genuine evidence | old production warning | envelope decision |
| --- | ---: | ---: | --- |
| 12 gap, low side | stable genuine down to about 0.081 | below 0.070 | unchanged, already outside genuine |
| 12 corroborated turn | genuine photos about 1.1 to 1.7 deg absolute | effectively 1.0 deg plus visibility | **changed to 2.0 deg** |
| 12 level-top skew | genuine Phillips example +2.2 deg | 2.0 deg | **changed to 2.5 deg** |
| 12 off-centre spacing asymmetry | genuine maximum 0.080 | 0.10 | unchanged |
| 6 baton centring | genuine maximum 0.075 | 0.10 | unchanged |
| 6 baton rotation | genuine maximum 1.79 deg | 2.0 deg | unchanged |
| round-marker offset | genuine judged maximum 0.103 | 0.15 | unchanged |
| round-marker size difference | genuine judged maximum 0.072 | 0.12 | unchanged |

Evidence sources already in the repository:

- `docs/research/gmt_fix_list_2026-09-27.md`
- `docs/research/gmt_dial_crop_2026-09-28.md`
- `docs/research/gmt12_offcentre_recheck_2026-09-28.md`
- `docs/research/gmt_round_markers_2026-09-28.md`

## Why the 12 rotation change is required

The alpha61 research explicitly found that genuine and replica 12-marker rotation overlapped below
about 2 degrees. It nevertheless kept CHECK at 1 degree so that several replica concerns would
remain visible. Under the current product rule that is backwards: those concerns are not specific
to replicas if genuine watches can produce the same measurement.

The production rotation gate therefore now requires at least 2.0 degrees before the marker-axis
angle itself can support CHECK. A level-top skew is even less specific; because a genuine Phillips
GMT measured +2.2 degrees, that path now waits until 2.5 degrees.

STRONG rotation no longer comes from image-scale pixel rise at a 2-degree reading. It requires at
least 3 degrees or the independent side-spacing signal to be strong. This avoids promoting a value
near the genuine boundary merely because a high-resolution image makes the same angle span more
pixels.

## Regression guard

`tools/watch_calibrator/tests/test_gmt_genuine_envelope.py` locks each production CHECK boundary
outside the documented genuine evidence above. Android unit tests separately verify that genuine-
envelope turns/skews clear while values beyond the envelope still receive attention.

This audit does not claim the listed numbers are Rolex manufacturing tolerances. They are empirical
Watch Align QC boundaries derived from the genuine photographs measured by this project.
